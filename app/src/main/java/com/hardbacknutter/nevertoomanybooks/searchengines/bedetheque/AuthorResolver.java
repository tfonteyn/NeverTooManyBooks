/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.TestFlags;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * Talk to the Bedetheque website to resolve author pseudonyms.
 * <p>
 * Aside of Bedetheque itself, this class is also used by StripInfo and LastDodo.
 */
public class AuthorResolver {

    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale seLocale;
    @NonNull
    private final SynchronizedDb cacheDb;
    @NonNull
    private final AuthorDao authorDao;

    public AuthorResolver(@NonNull final Context context,
                          @Nullable final Cancellable caller) {
        searchEngine = (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine();
        searchEngine.setCaller(caller);
        seLocale = searchEngine.getLocale(context);

        // no database in junit mode ... so we need to cheat.
        if (BuildConfig.DEBUG && TestFlags.isJUnit) {
            //noinspection ConstantConditions
            cacheDb = null;
            //noinspection ConstantConditions
            authorDao = null;
            return;
        }

        cacheDb = ServiceLocator.getInstance().getCacheDb();
        authorDao = ServiceLocator.getInstance().getAuthorDao();
    }

    /**
     * Update the given Author with any missing diacritics and resolve pen-names.
     *
     * @param context Current context
     * @param author  to lookup
     *
     * @return {@code true} if the Author was modified; {@code false} otherwise
     */
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {

        // no database in junit mode ... so we need to cheat.
        if (BuildConfig.DEBUG && TestFlags.isJUnit) {
            return false;
        }

        // We SHOULD pass in the book-locale here...
        authorDao.refresh(context, author, false, seLocale);

        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        // Check if we have the author in the cache at all.
        BdtAuthor bdtAuthor = findInCache(author);
        if (bdtAuthor == null) {
            // If not found,
            final char c1 = author.getFamilyName().charAt(0);
            // and the list-page was never fetched before,
            if (!isAuthorPageCached(c1)) {
                // go fetch the the list-page on which the author should/could be
                if (fetchAuthorListFromSite(context, c1)) {
                    // If the author was on the list page, we should find it now.
                    bdtAuthor = findInCache(author);
                }
            }
        }

        // If the author is unknown on the site, we're done.
        if (bdtAuthor == null || searchEngine.isCancelled()) {
            return false;
        }

        // The author is on the site + in our cache.
        if (!bdtAuthor.isResolved()) {
            // but we have never resolved the name yet, so load the author details page
            final Document document = searchEngine.loadDocument(context, bdtAuthor.getUrl(),
                                                                null);
            if (!searchEngine.isCancelled()) {
                if (parseAuthor(document, bdtAuthor)) {
                    // Always update; this will correct any diacritics
                    // The 'resolved' flag will have been set to 'true'
                    // (and the resolved name if appropriate)
                    updateAuthorInCache(bdtAuthor);
                } else {
                    // parsing went wrong... we should never get here... flw
                    return false;
                }
            }
        }

        // If the author does not use a pen-name, we're done.
        if (bdtAuthor.getResolvedName().equals(bdtAuthor.getName())) {
            return false;
        }

        // The name was a pen-name and we have resolved it to their real name
        // Add it accordingly to the original Author object
        final Author realAuthor = Author.from(bdtAuthor.getResolvedName());
        authorDao.refresh(context, realAuthor, false, seLocale);
        author.setRealAuthor(realAuthor);

        // sanity check that the original name and the pen-name match
        final Author penAuthor = Author.from(bdtAuthor.getName());
        if (penAuthor.hashCodeOfNameOnly() == author.hashCodeOfNameOnly()) {
            // Always overwrite the name; this will correct any diacritics
            author.setName(penAuthor.getFamilyName(), penAuthor.getGivenNames());
        }

        return true;
    }

    /**
     * Parse the downloaded document and update the given {@link BdtAuthor} if possible.
     * <p>
     * If successful, the 'resolved' flag for the given BdtAuthor will be set {@code true}.
     *
     * @param document  to parse
     * @param bdtAuthor to update
     *
     * @return {@code true} if the {@link BdtAuthor} was resolved;
     * {@code false} otherwise, this should never be the case though
     */
    @VisibleForTesting
    boolean parseAuthor(@NonNull final Document document,
                        @NonNull final BdtAuthor bdtAuthor) {
        final Element info = document.selectFirst("div.auteur-infos ul.auteur-info");
        if (info != null) {
            final Elements labels = info.getElementsByTag("label");
            String familyName = "";
            String givenName = "";
            String penName = "";

            for (final Element label : labels) {
                //noinspection SwitchStatementWithoutDefaultBranch
                switch (label.text()) {
                    case "Nom :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            familyName = span.text();
                        }
                        break;
                    }
                    case "Prénom :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            givenName = span.text();
                        }
                        break;
                    }
                    case "Pseudo :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            penName = textNode.toString();
                        }
                        break;
                    }
                }
            }

            final String resolvedName = familyName + (givenName.isBlank() ? "" : ", " + givenName);
            bdtAuthor.setResolvedName(resolvedName);
            bdtAuthor.setResolved(true);

            return true;
        }
        return false;
    }

    /**
     * Fetch and parse the list of authors from the website for the given first
     * character of the name.
     *
     * @param context Current context
     * @param c1      first character of the name
     *
     * @return {@code true} on success
     */
    private boolean fetchAuthorListFromSite(@NonNull final Context context,
                                            final char c1)
            throws SearchException, CredentialsException {

        // The site has 27 pages with name lists. The 26 [A-Z] + a '0' page with
        // all the names which don't start with an [A-Z].
        char c = ParseUtils.toAscii(String.valueOf(c1)).toUpperCase(seLocale).charAt(0);
        if (!Character.isAlphabetic(c)) {
            c = '0';
        }

        final String url = searchEngine.getHostUrl() + "/liste_auteurs_BD_" + c + ".html";
        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            return parseAuthorList(document);
        }
        return false;
    }

    /**
     * Parse and store the list of author name/url.
     *
     * @param document to parse
     *
     * @return {@code true} on success
     */
    private boolean parseAuthorList(@NonNull final Document document) {
        boolean atLeastOneFound = false;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!cacheDb.inTransaction()) {
                txLock = cacheDb.beginTransaction(true);
            }

            try (SynchronizedStatement stmt = cacheDb.compileStatement(BdtAuthor.INSERT)) {
                final Elements all = document.select("ul.nav-liste > li > a");
                for (final Element a : all) {
                    final String url = a.attr("href");
                    final Element span = a.selectFirst("span.libelle");
                    if (span != null) {
                        final String name = span.text();
                        final BdtAuthor bdtAuthor = new BdtAuthor(name, url);

                        stmt.bindString(1, bdtAuthor.getName());
                        stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(),
                                                                   seLocale));
                        stmt.bindString(3, bdtAuthor.getUrl());
                        stmt.executeInsert();

                        atLeastOneFound = true;
                    }
                }
            }
            if (txLock != null) {
                cacheDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                cacheDb.endTransaction(txLock);
            }
        }
        return atLeastOneFound;
    }

    private void updateAuthorInCache(@NonNull final BdtAuthor bdtAuthor) {
        try (SynchronizedStatement stmt = cacheDb.compileStatement(BdtAuthor.UPDATE)) {
            stmt.bindString(1, bdtAuthor.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(), seLocale));
            stmt.bindString(3, bdtAuthor.getUrl());
            if (bdtAuthor.isResolved()) {
                stmt.bindBoolean(4, true);
                stmt.bindNull(5);
                stmt.bindNull(6);
            } else {
                stmt.bindBoolean(4, false);
                stmt.bindString(5, bdtAuthor.getResolvedName());
                stmt.bindString(6, SqlEncode.orderByColumn(bdtAuthor.getResolvedName(), seLocale));
            }
            stmt.bindLong(7, bdtAuthor.getId());
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Try to find given author in the cache.
     *
     * @param author to find
     *
     * @return url, or {@code null} if not found
     */
    @Nullable
    private BdtAuthor findInCache(@NonNull final Author author) {
        final String nameOb = SqlEncode
                .orderByColumn(author.getFormattedName(false), seLocale);

        try (Cursor cursor = cacheDb.rawQuery(
                "SELECT "
                + CacheDbHelper.PK_ID
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_IS_RESOLVED
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_URL
                + " FROM " + CacheDbHelper.TBL_BDT_AUTHORS
                + " WHERE " + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + " OR " + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?",
                new String[]{nameOb, nameOb})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return new BdtAuthor(rowData.getLong(CacheDbHelper.PK_ID), rowData);
            }
        }
        return null;
    }

    private boolean isAuthorPageCached(final char c1) {
        try (SynchronizedStatement stmt = cacheDb.compileStatement(
                "SELECT DISTINCT 1 FROM " + CacheDbHelper.TBL_BDT_AUTHORS
                + " WHERE " + CacheDbHelper.BDT_AUTHOR_NAME + " LIKE ?")) {
            stmt.bindString(1, c1 + "%");
            return stmt.simpleQueryForLongOrZero() != 0;
        }
    }

    @VisibleForTesting
    static class BdtAuthor {
        public static final String INSERT =
                "INSERT INTO " + CacheDbHelper.TBL_BDT_AUTHORS
                + "(" + CacheDbHelper.BDT_AUTHOR_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB
                + ',' + CacheDbHelper.BDT_AUTHOR_URL
                + ") VALUES(?,?,?) ON CONFLICT(" + CacheDbHelper.BDT_AUTHOR_NAME_OB
                + ") DO UPDATE SET " + CacheDbHelper.BDT_AUTHOR_URL
                + "=excluded." + CacheDbHelper.BDT_AUTHOR_URL;
        private static final String UPDATE =
                "UPDATE " + CacheDbHelper.TBL_BDT_AUTHORS + " SET "
                + CacheDbHelper.BDT_AUTHOR_NAME + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_URL + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_IS_RESOLVED + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?"
                + " WHERE " + CacheDbHelper.PK_ID + "=?";
        private final long id;
        @NonNull
        private final String name;
        @NonNull
        private final String url;

        private boolean resolved;
        @NonNull
        private String resolvedName;

        BdtAuthor(@NonNull final String name,
                  @NonNull final String url) {
            this.id = 0;
            this.name = name;
            this.resolvedName = "";
            this.url = url;
        }

        BdtAuthor(final long id,
                  @NonNull final DataHolder rowData) {
            this.id = id;
            this.name = rowData.getString(CacheDbHelper.BDT_AUTHOR_NAME);
            this.resolved = rowData.getBoolean(CacheDbHelper.BDT_AUTHOR_IS_RESOLVED);
            this.resolvedName = rowData.getString(CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME);
            this.url = rowData.getString(CacheDbHelper.BDT_AUTHOR_URL);
        }

        public long getId() {
            return id;
        }

        @NonNull
        public String getName() {
            return name;
        }

        public boolean isResolved() {
            return resolved;
        }

        public void setResolved(final boolean resolved) {
            this.resolved = resolved;
        }

        /**
         * Get the resolved name. <strong>Only</strong> valid
         * if {@link #isResolved()} returns {@code true}.
         * Undefined if {@link #isResolved()} returns {@code false}.
         *
         * @return resolved name
         */
        @NonNull
        String getResolvedName() {
            return resolvedName;
        }

        /**
         * Set the resolved name.
         *
         * @param resolvedName to set
         */
        void setResolvedName(@NonNull final String resolvedName) {
            this.resolvedName = resolvedName;
        }

        @NonNull
        public String getUrl() {
            return url;
        }
    }
}
