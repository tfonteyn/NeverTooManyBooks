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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.BookFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;

/**
 * In addition to the {@link BookBaseFragmentModel}, this model holds the flattened booklist
 * for sweeping left/right.
 */
public class FlattenedBooklistModel
        extends ViewModel {

    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    @Override
    protected void onCleared() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
            mFlattenedBooklist.deleteData();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args   {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     * @param bookId The book this model will represent.
     */
    public void init(@NonNull final SynchronizedDb syncedDb,
                     @Nullable final Bundle args,
                     final long bookId) {
        if (mFlattenedBooklist == null) {

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
            mFlattenedBooklist = new FlattenedBooklist(syncedDb, listTableName);
            // Check to see it really exists. The underlying table disappeared once in testing
            // which is hard to explain; it theoretically should only happen if the app closes
            // the database (DAO#sSyncedDb)
            // or if the activity pauses with 'isFinishing()' returning true.
            //
            // Last seen: 2019-10-25. Solution: don't cache the table in the Builder,
            // but recreate on each call.
            if (!mFlattenedBooklist.exists()) {
                mFlattenedBooklist.close();
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
            }
        }
    }

    @Nullable
    public FlattenedBooklist getFlattenedBooklist() {
        return mFlattenedBooklist;
    }
}
