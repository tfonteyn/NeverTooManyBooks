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
package com.hardbacknutter.nevertoomanybooks.database;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.view.Menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;

/**
 * We're using the default search action view and the standard "SUGGEST" Uri and MIME types.
 * <p>
 * Our query returns book titles as the {@link SearchManager#SUGGEST_COLUMN_INTENT_DATA}.
 * <p>
 * The {@link #AUTHORITY} must match the Manifest attribute {@code "android:authorities"}:
 * <pre>
 * {@code
 * <provider
 *      android:name=".database.SearchSuggestionProvider"
 *      android:authorities="com.hardbacknutter.nevertoomanybooks.SearchSuggestionProvider"
 *      android:exported="false" />
 * }
 *
 * res/xml/searchable.xml
 * {@code
 *  <searchable
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:hint="@string/lbl_search_for_books"
 *     android:label="@string/lbl_search_for_books"
 *     android:searchSuggestAuthority="com.hardbacknutter.nevertoomanybooks.SearchSuggestionProvider"
 *     android:searchSuggestIntentAction="android.intent.action.VIEW"
 *     android:searchSuggestThreshold="2"
 *     android:searchSuggestSelection=" ?"
 *     />
 * }
 * </pre>
 *
 * @see MenuUtils#setupSearchActionView(Activity, Menu)
 * @see <a href="https://developer.android.com/reference/android/content/ContentProvider.html">
 *         ContentProvider</a>
 * @see <a href="https://developer.android.com/guide/topics/providers/content-provider-creating#MIMETypes">
 *         MIMETypes</a>
 */
public class SearchSuggestionProvider
        extends ContentProvider {

    /**
     * The authority value must match:
     * - AndroidManifest.xml/provider/android:authorities
     * - src/main/res/xml/searchable.xml/searchSuggestAuthority
     * - SearchSuggestionProvider.java/AUTHORITY
     */
    private static final String AUTHORITY =
            "com.hardbacknutter.nevertoomanybooks.SearchSuggestionProvider";

    /** Standard Local-search. */
    private static final String SEARCH_SUGGESTIONS =
            // FTS_BOOK_ID is the _id into the books table.
            "SELECT " + DBKey.FTS_BOOK_ID + " AS " + DBKey.PK_ID
            + ',' + (TBL_FTS_BOOKS.dot(DBKey.TITLE)
                     + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1)
            + ',' + (TBL_FTS_BOOKS.dot(DBKey.FTS_AUTHOR_NAME)
                     + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_2)
            + ',' + (TBL_FTS_BOOKS.dot(DBKey.TITLE)
                     + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA)
            + " FROM " + TBL_FTS_BOOKS.getName()
            + " WHERE " + TBL_FTS_BOOKS.getName() + " MATCH ?";

    /** Uri and query support. Arbitrary code to indicate a match. */
    private static final int SUGGEST_URI_PATH_ID = 1;

    /** Uri and query support. */
    private static final UriMatcher URI_MATCHER;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SUGGEST_URI_PATH_ID);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    @Nullable
    public Cursor query(@NonNull final Uri uri,
                        @Nullable final String[] projection,
                        @Nullable final String selection,
                        @Nullable final String[] selectionArgs,
                        @Nullable final String sortOrder) {

        if (URI_MATCHER.match(uri) == SUGGEST_URI_PATH_ID) {
            if (selectionArgs == null || selectionArgs[0] == null || selectionArgs[0].isEmpty()) {
                return null;
            }

            final String query = FtsDao.prepareSearchText(selectionArgs[0], null);
            if (!query.isEmpty()) {
                return ServiceLocator.getInstance().getDb()
                                     .rawQuery(SEARCH_SUGGESTIONS, new String[]{query});
            }
        }

        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull final Uri uri) {
        if (URI_MATCHER.match(uri) == SUGGEST_URI_PATH_ID) {
            return SearchManager.SUGGEST_MIME_TYPE;
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
