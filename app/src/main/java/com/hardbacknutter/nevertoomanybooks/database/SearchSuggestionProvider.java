/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.content.ContentProvider;
import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * <a href="https://developer.android.com/guide/topics/search/adding-custom-suggestions.html">
 * https://developer.android.com/guide/topics/search/adding-custom-suggestions.html</a>
 * <p>
 * This class is a bit of a hack as it override the SearchRecentSuggestionsProvider,
 * but then actually bypasses most of its functionality by overriding the query method.
 * <p>
 * A cleaner implementation would be to extend the {@link ContentProvider} class,
 * and move the FTS insert/update methods from the DAO here.
 */
public class SearchSuggestionProvider
        extends SearchRecentSuggestionsProvider {

    /**
     * The authorities value must match:
     * - AndroidManifest.xml/provider/android:authorities
     * - res/xml/searchable.xml/searchSuggestAuthority
     * - SearchSuggestionProvider.java/AUTHORITY
     */
    public static final String AUTHORITY = App.getAppPackageName() + ".SearchSuggestionProvider";

    /** Required. This mode bit configures the suggestions database to record recent queries. */
    public static final int MODE = DATABASE_MODE_QUERIES;

    private static final String TAG = "SearchSuggestionProv";

    /** Database Access. */
    @Nullable
    private DAO mDb;

    /**
     * Constructor.
     */
    public SearchSuggestionProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

    @Override
    public Cursor query(final Uri uri,
                        final String[] projection,
                        final String selection,
                        @NonNull final String[] selectionArgs,
                        final String sortOrder) {
        if (selectionArgs[0].isEmpty()) {
            return null;
        }
        if (mDb == null) {
            mDb = new DAO(TAG);
        }
        return mDb.fetchSearchSuggestions(selectionArgs[0]);
    }
}
