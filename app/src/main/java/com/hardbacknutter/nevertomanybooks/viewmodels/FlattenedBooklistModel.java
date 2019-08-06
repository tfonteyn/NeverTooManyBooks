/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.viewmodels;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertomanybooks.BookFragment;
import com.hardbacknutter.nevertomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertomanybooks.database.DAO;

/**
 * In addition to the {@link BookBaseFragmentModel}, this model holds the flattened book list
 * for sweeping left/right.
 */
public class FlattenedBooklistModel
        extends ViewModel {

    /** Database Access. */
    private DAO mDb;

    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    @Override
    protected void onCleared() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
            mFlattenedBooklist.deleteData();
        }

        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args   Bundle with arguments
     * @param bookId The book this model will represent.
     */
    public void init(@Nullable final Bundle args,
                     final long bookId) {
        if (mDb == null) {
            mDb = new DAO();

            // no arguments ? -> no list!
            if (args == null) {
                return;
            }
            // no list ?
            String listTableName = args.getString(BookFragment.BKEY_FLAT_BOOKLIST_TABLE);
            if (listTableName == null || listTableName.isEmpty()) {
                return;
            }

            // looks like we have a list, but...
            mFlattenedBooklist = new FlattenedBooklist(mDb, listTableName);
            // Check to see it really exists. The underlying table disappeared once in testing
            // which is hard to explain; it theoretically should only happen if the app closes
            // the database or if the activity pauses with 'isFinishing()' returning true.
            if (!mFlattenedBooklist.exists()) {
                mFlattenedBooklist.close();
                //mFlattenedBooklist.deleteData();
                mFlattenedBooklist = null;
                return;
            }

            // ok, we absolutely have a list, get the position we need to be on.
            int pos = args.getInt(BookFragment.BKEY_FLAT_BOOKLIST_POSITION, 0);

            mFlattenedBooklist.moveTo(pos);
            // the book might have moved around. So see if we can find it.
            while (mFlattenedBooklist.getBookId() != bookId) {
                if (!mFlattenedBooklist.moveNext()) {
                    break;
                }
            }

            if (mFlattenedBooklist.getBookId() != bookId) {
                // book not found ? eh? give up...
                mFlattenedBooklist.close();
                mFlattenedBooklist.deleteData();
                mFlattenedBooklist = null;
                return;
            }
        }
    }

    @Nullable
    public FlattenedBooklist getFlattenedBooklist() {
        return mFlattenedBooklist;
    }
}
