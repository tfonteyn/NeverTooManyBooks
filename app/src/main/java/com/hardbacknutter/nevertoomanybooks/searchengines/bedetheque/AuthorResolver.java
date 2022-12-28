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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
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
        // If not found, load/refresh the list-page on which the author should/could be
        if (bdtAuthor == null) {
            final char c1 = author.getFamilyName().charAt(0);
            final List<BdtAuthor> list = fetchAuthorListFromSite(context, c1);
            if (!list.isEmpty()) {
                storeAuthorListInCache(list);
            }
            bdtAuthor = findInCache(author);
        }

        // If the author is unknown on the site, we're done.
        if (bdtAuthor == null || searchEngine.isCancelled()) {
            return false;
        }

        if (bdtAuthor.getResolvedName().isBlank()) {
            // The resolved name is not in the cache, load the author page
            final Document document = searchEngine.loadDocument(context, bdtAuthor.getUrl());
            if (!searchEngine.isCancelled()) {
                if (parseAuthor(document, bdtAuthor)) {
                    storeAuthorInCache(bdtAuthor);
                }
            }
        }

        // If the author does not use a pen-name, we're done.
        if (bdtAuthor.getResolvedName().equals(bdtAuthor.getName())) {
            return false;
        }

        // Resolve and set the real-author
        final Author realAuthor = Author.from(bdtAuthor.getResolvedName());
        authorDao.refresh(context, realAuthor, false, seLocale);
        author.setRealAuthor(realAuthor);

        // Overwrite the pen-name; this will correct any diacritics
        final Author penAuthor = Author.from(bdtAuthor.getName());
        // sanity check; they should match
        if (penAuthor.hashCodeOfNameOnly() == author.hashCodeOfNameOnly()) {
            // Overwrite the name; this will correct any diacritics
            author.setName(penAuthor.getFamilyName(), penAuthor.getGivenNames());
        }

        return true;
    }

    /**
     * Parse the downloaded document and update the given BdtAuthor if possible.
     *
     * @param document  to parse
     * @param bdtAuthor to update
     *
     * @return {@code true} if the BdtAuthor was modified; {@code false} otherwise
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
            // TODO: another sanity check: test if the penName matches the bdtAuthor name


            // sanity check, we always should have the family name at the very least
            if (!familyName.isBlank()) {
                bdtAuthor.setResolvedName(familyName + (givenName.isBlank() ? ""
                                                                            : ", " + givenName));
                return true;
            }
        }
        return false;
    }

    /**
     * Fetch and store the list of author name/url from the website for the given first
     * character of the name.
     *
     * @param context Current context
     * @param c1      first character of the name
     *
     * @return list of entries
     */
    @NonNull
    private List<BdtAuthor> fetchAuthorListFromSite(@NonNull final Context context,
                                                    final char c1)
            throws SearchException, CredentialsException {

        // The site has 27 pages with name lists. The 26 [A-Z] + a '0' page with
        // all the names which don't start with an [A-Z].
        char c = ParseUtils.toAscii(String.valueOf(c1)).toUpperCase(seLocale).charAt(0);
        if (!Character.isAlphabetic(c)) {
            c = '0';
        }

        final String url = searchEngine.getHostUrl() + "/liste_auteurs_BD_" + c + ".html";
        final Document document = searchEngine.loadDocument(context, url);
        if (!searchEngine.isCancelled()) {
            return parseAuthorList(document);
        }
        return List.of();
    }

    /**
     * Parse the downloaded document into a list of name+url.
     *
     * @param document to parse
     *
     * @return list of name/url pairs
     */
    @VisibleForTesting
    @NonNull
    List<BdtAuthor> parseAuthorList(@NonNull final Document document) {
        final List<BdtAuthor> list = new ArrayList<>();

        final Elements all = document.select("ul.nav-liste li a");
        for (final Element a : all) {
            final String url = a.attr("href");
            final Element span = a.firstElementChild();
            if (span != null && span.hasClass("libelle")) {
                final String name = span.text();
                list.add(new BdtAuthor(name, url));
            }
        }

        return list;
    }

    private void storeAuthorInCache(@NonNull final BdtAuthor bdtAuthor) {
        try (SynchronizedStatement stmt = cacheDb.compileStatement(
                "UPDATE " + CacheDbHelper.TBL_BDT_AUTHORS + " SET "
                + CacheDbHelper.BDT_AUTHOR_NAME + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_URL + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + " WHERE " + CacheDbHelper.PK_ID + "=?")) {
            stmt.bindString(1, bdtAuthor.getName());
            stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(), seLocale));
            stmt.bindString(3, bdtAuthor.getUrl());
            stmt.bindString(1, bdtAuthor.getResolvedName());
            stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getResolvedName(), seLocale));
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Store the list of author/url pairs in the cache database.
     * <p>
     * If the author already exists in the cache, we update the url.
     *
     * @param list to insert/update
     */
    private void storeAuthorListInCache(@NonNull final List<BdtAuthor> list) {
        try (SynchronizedStatement stmt = cacheDb.compileStatement(
                "INSERT INTO " + CacheDbHelper.TBL_BDT_AUTHORS
                + "(" + CacheDbHelper.BDT_AUTHOR_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB
                + ',' + CacheDbHelper.BDT_AUTHOR_URL
                + ") VALUES(?,?,?) ON CONFLICT(" + CacheDbHelper.BDT_AUTHOR_NAME_OB
                + ") DO UPDATE SET " + CacheDbHelper.BDT_AUTHOR_URL
                + "=excluded." + CacheDbHelper.BDT_AUTHOR_URL)) {

            for (final BdtAuthor bdtAuthor : list) {
                stmt.bindString(1, bdtAuthor.getName());
                stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(), seLocale));
                stmt.bindString(3, bdtAuthor.getUrl());
                stmt.executeInsert();
            }
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
        final String nameOb = SqlEncode.orderByColumn(author.getFormattedName(false),
                                                      seLocale);

        try (Cursor cursor = cacheDb.rawQuery(
                "SELECT "
                + CacheDbHelper.PK_ID
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_URL
                + " FROM " + CacheDbHelper.TBL_BDT_AUTHORS
                + " WHERE " + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + " OR " + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?",
                new String[]{nameOb, nameOb})) {
            if (cursor.moveToFirst()) {
                final CursorRow data = new CursorRow(cursor);
                return new BdtAuthor(data.getLong(CacheDbHelper.PK_ID), data);
            }
        }
        return null;
    }

    @VisibleForTesting
    static class BdtAuthor {
        private final long id;
        @NonNull
        private final String name;
        @NonNull
        private final String url;
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
                  @NonNull final DataHolder data) {
            this.id = id;
            this.name = data.getString(CacheDbHelper.BDT_AUTHOR_NAME);
            this.resolvedName = data.getString(CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME);
            this.url = data.getString(CacheDbHelper.BDT_AUTHOR_URL);
        }

        public long getId() {
            return id;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        String getResolvedName() {
            return resolvedName;
        }

        void setResolvedName(@NonNull final String resolvedName) {
            this.resolvedName = resolvedName;
        }

        @NonNull
        public String getUrl() {
            return url;
        }
    }
}
