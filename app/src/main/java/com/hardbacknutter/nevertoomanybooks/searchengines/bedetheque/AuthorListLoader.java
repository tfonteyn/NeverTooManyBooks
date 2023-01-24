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

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class AuthorListLoader {

    @NonNull
    private final Context context;
    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale locale;

    AuthorListLoader(@NonNull final Context context,
                     @NonNull final BedethequeSearchEngine searchEngine) {
        this.context = context;
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);
    }

    /**
     * Take the first character from the given name and normalize it to [0A-Z]
     * for use with the other class methods.
     *
     * @param name to use
     *
     * @return [0A-Z] of the first character
     */
    char firstChar(@NonNull final CharSequence name) {
        final char c1 = ParseUtils.toAscii(String.valueOf(name.charAt(0)))
                                  .toUpperCase(locale)
                                  .charAt(0);
        return Character.isAlphabetic(c1) ? c1 : '0';
    }


    boolean isAuthorPageCached(final char c1) {
        final SynchronizedDb cacheDb = ServiceLocator.getInstance().getCacheDb();
        try (SynchronizedStatement stmt = cacheDb.compileStatement(
                "SELECT DISTINCT 1 FROM " + CacheDbHelper.TBL_BDT_AUTHORS
                + " WHERE " + CacheDbHelper.BDT_AUTHOR_NAME + " LIKE ?")) {
            stmt.bindString(1, c1 + "%");
            return stmt.simpleQueryForLongOrZero() != 0;
        }
    }

    /**
     * Fetch and parse the list of authors from the website for the given first
     * character of the name.
     * <p>
     * The site has 27 pages with name lists. The 26 [A-Z] + a '0' page with
     * all the names which don't start with an [A-Z].
     *
     * @param c1 first character of the name
     *
     * @return {@code true} on success
     */
    boolean fetch(final char c1)
            throws SearchException, CredentialsException {

        final String url = searchEngine.getHostUrl() + "/liste_auteurs_BD_" + c1 + ".html";
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

        final SynchronizedDb cacheDb = ServiceLocator.getInstance().getCacheDb();
        Synchronizer.SyncLock txLock = null;
        try {
            if (!cacheDb.inTransaction()) {
                txLock = cacheDb.beginTransaction(true);
            }

            final Elements all = document.select("ul.nav-liste > li > a");
            try (SynchronizedStatement stmt = cacheDb.compileStatement(BdtAuthor.INSERT)) {
                for (final Element a : all) {
                    final String url = a.attr("href");
                    final Element span = a.selectFirst("span.libelle");
                    if (span != null) {
                        final String name = span.text();
                        stmt.bindString(1, name);
                        stmt.bindString(2, SqlEncode.orderByColumn(name, locale));
                        stmt.bindString(3, url);
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
}
