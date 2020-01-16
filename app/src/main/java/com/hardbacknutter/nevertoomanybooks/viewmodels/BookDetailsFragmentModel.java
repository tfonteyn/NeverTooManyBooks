/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;

/**
 * In addition to the {@link BookBaseFragmentModel}.
 */
public class BookDetailsFragmentModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BookDetailsFragModel";

    /** Table name of the {@link FlattenedBooklist}. */
    public static final String BKEY_FLAT_BOOKLIST_TABLE = TAG + ":FBL_Table";
    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    /** The fields collection. */
    @NonNull
    private final Fields mFields = new Fields();

    /**
     * Pseudo constructor.
     *
     * @param db     Database Access
     * @param args   {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     * @param bookId The book this model will represent.
     */
    public void init(@NonNull final DAO db,
                     @Nullable final Bundle args,
                     final long bookId) {

        if (mFlattenedBooklist == null) {
            // no arguments ? -> no list!
            if (args == null) {
                return;
            }
            // no list ?
            String navTableName = args.getString(BKEY_FLAT_BOOKLIST_TABLE);
            if (navTableName == null || navTableName.isEmpty()) {
                return;
            }

            mFlattenedBooklist = new FlattenedBooklist(db, navTableName);
            if (!mFlattenedBooklist.moveTo(bookId)) {
                // book not found ? eh? Destroy the table!
                mFlattenedBooklist.close();
                mFlattenedBooklist = null;
            }
        }
    }

    @NonNull
    public Fields getFields() {
        return mFields;
    }

    @Nullable
    public FlattenedBooklist getFlattenedBooklist() {
        return mFlattenedBooklist;
    }
}
