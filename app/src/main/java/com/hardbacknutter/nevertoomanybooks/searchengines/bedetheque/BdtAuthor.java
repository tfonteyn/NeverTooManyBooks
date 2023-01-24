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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

class BdtAuthor {
    static final String INSERT =
            "INSERT INTO " + CacheDbHelper.TBL_BDT_AUTHORS
            + "(" + CacheDbHelper.BDT_AUTHOR_NAME
            + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB
            + ',' + CacheDbHelper.BDT_AUTHOR_URL
            + ") VALUES(?,?,?) ON CONFLICT(" + CacheDbHelper.BDT_AUTHOR_NAME_OB
            + ") DO UPDATE SET " + CacheDbHelper.BDT_AUTHOR_URL
            + "=excluded." + CacheDbHelper.BDT_AUTHOR_URL;
    private static final String FIND_BY_NAME =
            "SELECT "
            + CacheDbHelper.PK_ID
            + ',' + CacheDbHelper.BDT_AUTHOR_NAME
            + ',' + CacheDbHelper.BDT_AUTHOR_IS_RESOLVED
            + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME
            + ',' + CacheDbHelper.BDT_AUTHOR_URL
            + " FROM " + CacheDbHelper.TBL_BDT_AUTHORS
            + " WHERE " + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
            + " OR " + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?";
    private static final String UPDATE =
            "UPDATE " + CacheDbHelper.TBL_BDT_AUTHORS + " SET "
            + CacheDbHelper.BDT_AUTHOR_NAME + "=?"
            + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
            + ',' + CacheDbHelper.BDT_AUTHOR_URL + "=?"
            + ',' + CacheDbHelper.BDT_AUTHOR_IS_RESOLVED + "=?"
            + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME + "=?"
            + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?"
            + " WHERE " + CacheDbHelper.PK_ID + "=?";

    @NonNull
    private final Context context;
    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale locale;

    private long id;
    @NonNull
    private String name;
    @Nullable
    private String url;

    private boolean resolved;
    @Nullable
    private String resolvedName;

    BdtAuthor(@NonNull final Context context,
              @NonNull final BedethequeSearchEngine searchEngine,
              @NonNull final Author author) {
        this.context = context;
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);

        name = author.getFormattedName(false);
    }

    @VisibleForTesting
    BdtAuthor(@NonNull final Context context,
              @NonNull final BedethequeSearchEngine searchEngine,
              @NonNull final String name,
              @NonNull final String url) {
        this.context = context;
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);

        this.name = name;
        this.url = url;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * Get the resolved name.
     *
     * @return resolved name; or {@code null} if none or equal to the actual name
     */
    @Nullable
    String getResolvedName() {
        if (!resolved
            || resolvedName == null || resolvedName.isEmpty()
            || resolvedName.equals(name)) {
            return null;
        }
        return resolvedName;
    }

    /**
     * Try to find given author in the cache.
     *
     * @return {@link #isResolved()} status
     */
    boolean findInCache() {
        final String nameOb = SqlEncode.orderByColumn(name, locale);

        final SynchronizedDb cacheDb = ServiceLocator.getInstance().getCacheDb();
        try (Cursor cursor = cacheDb.rawQuery(FIND_BY_NAME, new String[]{nameOb, nameOb})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                id = rowData.getLong(CacheDbHelper.PK_ID);
                name = rowData.getString(CacheDbHelper.BDT_AUTHOR_NAME);
                resolved = rowData.getBoolean(CacheDbHelper.BDT_AUTHOR_IS_RESOLVED);
                resolvedName = rowData.getString(CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME, null);
                url = rowData.getString(CacheDbHelper.BDT_AUTHOR_URL);
            }
        }
        return resolved;
    }

    /**
     * Lookup the author on the website using the BdtAuthor url.
     *
     * @return {@link #isResolved()} status
     */
    boolean lookup()
            throws SearchException, CredentialsException {
        if (url == null || url.isEmpty()) {
            return false;
        }

        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            if (parseAuthor(document)) {
                updateCache();
            }
        }
        return resolved;
    }

    /**
     * Parse the downloaded document and update the given {@link BdtAuthor} if possible.
     * <p>
     * If successful, the 'resolved' flag for the given BdtAuthor will be set {@code true}
     * and the resolved name will have been updated if appropriate.
     *
     * @param document to parse
     *
     * @return {@link #isResolved()} status
     */
    @VisibleForTesting
    boolean parseAuthor(@NonNull final Document document) {
        resolved = false;
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

            resolvedName = familyName + (givenName.isBlank() ? "" : ", " + givenName);
            resolved = true;
        }

        return resolved;
    }

    private void updateCache() {
        final SynchronizedDb cacheDb = ServiceLocator.getInstance().getCacheDb();
        try (SynchronizedStatement stmt = cacheDb.compileStatement(UPDATE)) {
            stmt.bindString(1, name);
            stmt.bindString(2, SqlEncode.orderByColumn(name, locale));
            stmt.bindString(3, url);
            stmt.bindBoolean(4, resolved);
            if (resolvedName == null || resolvedName.equals(name)) {
                stmt.bindNull(5);
                stmt.bindNull(6);
            } else {
                stmt.bindString(5, resolvedName);
                stmt.bindString(6, SqlEncode.orderByColumn(resolvedName, locale));
            }
            stmt.bindLong(7, id);
            stmt.executeUpdateDelete();
        }
    }
}
