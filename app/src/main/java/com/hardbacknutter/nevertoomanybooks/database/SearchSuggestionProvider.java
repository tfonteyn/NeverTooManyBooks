/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;

public class SearchSuggestionProvider
        extends ContentProvider {

    /**
     * The authority value must match:
     * - AndroidManifest.xml/provider/android:authorities
     * - src/main/res/xml/searchable.xml/searchSuggestAuthority
     * - SearchSuggestionProvider.java/AUTHORITY
     */
    private static final String AUTHORITY = ".SearchSuggestionProvider";

    /** Standard Local-search. */
    private static final String SEARCH_SUGGESTIONS =
            // KEY_FTS_BOOKS_PK is the _id into the books table.
            "SELECT " + DBKey.KEY_FTS_BOOK_ID + " AS " + DBKey.PK_ID
            + ',' + (TBL_FTS_BOOKS.dot(DBKey.KEY_TITLE)
                     + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1)
            + ',' + (TBL_FTS_BOOKS.dot(DBKey.FTS_AUTHOR_NAME)
                     + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_2)
            + ',' + (TBL_FTS_BOOKS.dot(DBKey.KEY_TITLE)
                     + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA)
            + " FROM " + TBL_FTS_BOOKS.getName()
            + " WHERE " + TBL_FTS_BOOKS.getName() + " MATCH ?";

    /** Uri and query support. Arbitrary code to indicate a match. */
    private static final int URI_MATCH_SUGGEST = 1;
    /** Uri and query support. */
    private UriMatcher mUriMatcher;

    @Override
    public boolean onCreate() {
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        //noinspection ConstantConditions
        mUriMatcher.addURI(getContext().getPackageName() + AUTHORITY,
                           SearchManager.SUGGEST_URI_PATH_QUERY, URI_MATCH_SUGGEST);
        return true;
    }

    @Override
    @Nullable
    public Cursor query(@NonNull final Uri uri,
                        @Nullable final String[] projection,
                        @Nullable final String selection,
                        @Nullable final String[] selectionArgs,
                        @Nullable final String sortOrder) {

        if (mUriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            if (selectionArgs == null || selectionArgs[0] == null || selectionArgs[0].isEmpty()) {
                return null;
            }

            final String query = FtsDao.prepareSearchText(selectionArgs[0], null);
            // do we have anything to search for?
            if (!query.isEmpty()) {
                //noinspection UnnecessaryLocalVariable
                final Cursor cursor = ServiceLocator
                        .getDb().rawQuery(SEARCH_SUGGESTIONS, new String[]{query});

                //  if (cursor != null) {
                //      //noinspection ConstantConditions
                //     cursor.setNotificationUri(getContext().getContentResolver(), uri);
                //  }

                return cursor;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull final Uri uri) {
        if (mUriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            return SearchManager.SUGGEST_MIME_TYPE;
        }

        // not sure this is actually useful.
        final int length = uri.getPathSegments().size();
        if (length >= 1) {
            final String base = uri.getPathSegments().get(0);
            if ("suggestions".equals(base)) {
                if (length == 1) {
                    return "vnd.android.cursor.dir/suggestion";
                } else if (length == 2) {
                    return "vnd.android.cursor.item/suggestion";
                }
            }
        }

        throw new IllegalArgumentException(String.valueOf(uri));
    }

    @Nullable
    @Override
    public Uri insert(@NonNull final Uri uri,
                      @Nullable final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull final Uri uri,
                      @Nullable final String selection,
                      @Nullable final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull final Uri uri,
                      @Nullable final ContentValues values,
                      @Nullable final String selection,
                      @Nullable final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
