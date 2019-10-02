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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.ContentProvider;
import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

public class SearchSuggestionProvider
        extends SearchRecentSuggestionsProvider {

    /**
     * The authorities value must match:
     * - AndroidManifest.xml/provider/android:authorities
     * - res/xml/searchable.xml/searchSuggestAuthority
     * - SearchSuggestionProvider.java/AUTHORITY
     */
    public static final String AUTHORITY = App.getAppPackageName() + ".SearchSuggestionProvider";

    /** This mode bit configures the database to record recent queries. */
    public final static int MODE = DATABASE_MODE_QUERIES;

    /** Database Access. */
    @Nullable
    private DAO mDb;

    public SearchSuggestionProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

    /**
     * The docs of the super state:
     * <strong>This method is provided for use by the ContentResolver.  Do not override, or directly
     * call from your own code.
     * </strong>
     * <br><br>
     * But the original BC code did override this regardless to use/provide a custom
     * query operating on the actual database instead of on the suggestions database.
     * <br><br>
     * <strong>Note:</strong> {@link ContentProvider#onCreate()} states that database connections
     * etc should be deferred until needed. Hence creating it on the fly.
     */
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
            mDb = new DAO();
        }
        return mDb.fetchSearchSuggestions(selectionArgs[0]);
    }
}
