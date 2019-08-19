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
     * can't use getContext, because setupSuggestions() MUST be called from the constructor,
     * at which point getContext == null.
     * alternative is hardcoding the package name of course
     * <p>
     * Matches the Manifest entry:
     * <p>
     * android:authorities="${packageName}.SearchSuggestionProvider"
     */
    private static final String AUTHORITY = App.getAppPackageName()
                                            + ".SearchSuggestionProvider";

    /** Database Access. */
    @Nullable
    private DAO mDb;

    @Nullable
    private Cursor mSSCursor;

    public SearchSuggestionProvider() {
        setupSuggestions(AUTHORITY, DATABASE_MODE_QUERIES);
    }

    /**
     * <b>Note:</b> {@link ContentProvider#onCreate()} states that database connections
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
        mSSCursor = mDb.fetchSearchSuggestions(selectionArgs[0]);
        return mSSCursor;
    }

    /**
     * There does not seem to be a way to cleanup resources (here, our db)
     * in a {@link ContentProvider}.
     * Added/Leaving this method here as a reminder
     */
    public void close() {
        if (mSSCursor != null) {
            mSSCursor.close();
        }
        if (mDb != null) {
            mDb.close();
        }
    }
}
