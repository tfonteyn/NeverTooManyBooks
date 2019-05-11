/*
 * @copyright 2011 evan
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches;

import android.content.ContentProvider;
import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.database.DAO;

/**
 * @author evan
 */
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

    private static final int MODE = DATABASE_MODE_QUERIES;

    /** Database access. */
    @Nullable
    private DAO mDb;

    @Nullable
    private Cursor mSSCursor;

    public SearchSuggestionProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

    /**
     * Note: {@link ContentProvider#onCreate()} states that database connections etc should be
     * deferred until needed. Hence creating it on the fly
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
