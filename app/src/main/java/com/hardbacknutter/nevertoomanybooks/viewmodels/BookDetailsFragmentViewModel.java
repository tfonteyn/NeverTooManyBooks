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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class BookDetailsFragmentViewModel
        extends BookBaseFragmentViewModel {

    /** Log tag. */
    private static final String TAG = "BookDetailsFragmentViewModel";

    /** Table name of the {@link FlattenedBooklist}. */
    public static final String BKEY_NAV_TABLE = TAG + ":FBLTable";
    public static final String BKEY_NAV_ROW_ID = TAG + ":FBLRow";
    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    @Override
    protected void onCleared() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
        }
        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args,
                     @NonNull final Book book) {
        super.init();

        if (mFlattenedBooklist == null && args != null) {
            initFlattenedBooklist(args, book);
        }
    }

    private void initFlattenedBooklist(@NonNull final Bundle args,
                                       @NonNull final Book book) {
        // got list ?
        final String navTableName = args.getString(BKEY_NAV_TABLE);
        if (navTableName != null && !navTableName.isEmpty()) {
            // ok, we have a list, get the rowId we need to be on.
            final long rowId = args.getLong(BKEY_NAV_ROW_ID, 0);
            if (rowId > 0) {
                mFlattenedBooklist = new FlattenedBooklist(mDb.getSyncDb(), navTableName);
                // move to book.
                if (!mFlattenedBooklist.moveTo(rowId)
                    // Paranoia: is it the book we wanted ?
                    || mFlattenedBooklist.getBookId() != book.getId()) {
                    // Should never happen... flw
                    mFlattenedBooklist.closeAndDrop();
                    mFlattenedBooklist = null;
                }
            }
        }
    }

    /**
     * Called after the user swipes back/forwards through the flattened booklist.
     *
     * @param book    Current book
     * @param forward flag; move to the next or previous book relative to the passed book.
     *
     * @return {@code true} if we moved
     */
    public boolean move(@NonNull final Book book,
                        final boolean forward) {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.move(forward);
            final long bookId = mFlattenedBooklist.getBookId();
            // reload if it's a different book
            if (bookId != book.getId()) {
                book.load(bookId, mDb);
                return true;
            }
        }
        return false;
    }

    @NonNull
    public List<Pair<Long, String>> getBookTitles(@NonNull final TocEntry tocEntry) {
        return tocEntry.getBookTitles(mDb);
    }
}
