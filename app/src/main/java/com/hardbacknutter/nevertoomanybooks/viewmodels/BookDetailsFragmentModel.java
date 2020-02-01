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
    public static final String BKEY_NAV_TABLE = TAG + ":FBLTable";
    public static final String BKEY_NAV_ROW_ID = TAG + ":FBLRow";
    /** The fields collection. */
    @NonNull
    private final Fields mFields = new Fields();
    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    @Override
    protected void onCleared() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param db   Database Access
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final DAO db,
                     @Nullable final Bundle args,
                     final long bookId) {

        if (mFlattenedBooklist == null) {
            // no arguments ? -> no list!
            if (args == null) {
                return;
            }

            // got list ?
            String navTableName = args.getString(BKEY_NAV_TABLE);
            if (navTableName != null && !navTableName.isEmpty()) {
                // ok, we have a list, get the rowId we need to be on.
                final long rowId = args.getLong(BKEY_NAV_ROW_ID, 0);
                if (rowId > 0) {
                    mFlattenedBooklist = new FlattenedBooklist(db, navTableName);
                    // move to book.
                    if (!mFlattenedBooklist.moveTo(rowId)
                        // Paranoia: is it the book we wanted ?
                        || mFlattenedBooklist.getBookId() != bookId) {
                        // Should never happen... flw
                        mFlattenedBooklist.closeAndDrop();
                        mFlattenedBooklist = null;
                    }
                }
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
