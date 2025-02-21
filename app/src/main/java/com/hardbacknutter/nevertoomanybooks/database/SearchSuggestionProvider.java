/*
 * @Copyright 2018-2025 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;

/**
 * We're using the default search action view and the standard "SUGGEST" Uri and MIME types.
 * <p>
 * Our query returns book titles as the {@link SearchManager#SUGGEST_COLUMN_INTENT_DATA}.
 * <pre>
 * gradle.build
 * {@code
 *      android.buildTypes.[type].resValue("string", "searchSuggestAuthority", "[value]")
 * }
 *
 * AndroidManifest.xml
 * {@code
 * <provider
 *      android:name=".database.SearchSuggestionProvider"
 *      android:authorities="@string/searchSuggestAuthority"
 *      android:exported="false" />
 * }
 *
 * res/xml/searchable.xml
 * {@code
 *  <searchable
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:hint="@string/lbl_search_for_books"
 *     android:label="@string/lbl_search_for_books"
 *     android:searchSuggestAuthority="@string/searchSuggestAuthority"
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
 * @see <a href=
 *         "https://developer.android.com/guide/topics/providers/content-provider-creating#MIMETypes">
 *         MIMETypes</a>
 */
public class SearchSuggestionProvider
        extends ContentProvider {

    /** Uri and query support. Arbitrary code to indicate a match. */
    private static final int SUGGEST_URI_PATH_ID = 1;

    /** Uri and query support. */
    private UriMatcher uriMatcher;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        //noinspection DataFlowIssue
        uriMatcher.addURI(getContext().getString(R.string.searchSuggestAuthority),
                          SearchManager.SUGGEST_URI_PATH_QUERY,
                          SUGGEST_URI_PATH_ID);

        return true;
    }

    @Override
    @Nullable
    public Cursor query(@NonNull final Uri uri,
                        @Nullable final String[] projection,
                        @Nullable final String selection,
                        @Nullable final String[] selectionArgs,
                        @Nullable final String sortOrder) {

        if (uriMatcher.match(uri) != SUGGEST_URI_PATH_ID) {
            return null;
        }
        if (selectionArgs == null || selectionArgs[0] == null || selectionArgs[0].isEmpty()) {
            return null;
        }

        return ServiceLocator.getInstance().getFtsDao().querySearchSuggestions(selectionArgs[0]);
    }

    @Nullable
    @Override
    public String getType(@NonNull final Uri uri) {
        if (uriMatcher.match(uri) == SUGGEST_URI_PATH_ID) {
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
