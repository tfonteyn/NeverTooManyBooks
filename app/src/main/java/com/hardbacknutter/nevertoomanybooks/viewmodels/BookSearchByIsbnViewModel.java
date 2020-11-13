/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class BookSearchByIsbnViewModel
        extends ViewModel
        implements ActivityResultViewModel {

    private static final String TAG = "BookSearchByIsbnViewModel";
    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    /** Database Access. */
    private DAO mDb;

    /**
     * Inherits the result from {@link com.hardbacknutter.nevertoomanybooks.EditBookActivity}.
     */
    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     */
    public void init() {
        if (mDb == null) {
            mDb = new DAO(TAG);
        }
    }

    @NonNull
    public ArrayList<Long> getBookIdsByIsbn(@NonNull final ISBN code) {
        return mDb.getBookIdsByIsbn(code);
    }
}
