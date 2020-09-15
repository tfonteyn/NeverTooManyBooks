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

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNavigator;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class BookDetailsFragmentViewModel
        extends BookBaseFragmentViewModel {

    /** Log tag. */
    private static final String TAG = "BookDetailsFragmentViewModel";

    /** Table name of the {@link Booklist} table. */
    public static final String BKEY_LIST_TABLE_NAME = TAG + ":LTName";
    public static final String BKEY_LIST_TABLE_ROW_ID = TAG + ":LTRow";
    @Nullable
    private BooklistNavigator mNavHelper;

    @Override
    protected void onCleared() {
        if (mNavHelper != null) {
            mNavHelper.close();
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

        if (mNavHelper == null && args != null) {
            // got list ?
            final String listTableName = args.getString(BKEY_LIST_TABLE_NAME);
            if (listTableName != null && !listTableName.isEmpty()) {
                // ok, we have a list, get the rowId we need to be on.
                final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                if (rowId > 0) {
                    mNavHelper = new BooklistNavigator(mDb.getSyncDb(), listTableName);
                    // move to book.
                    if (!mNavHelper.moveTo(rowId)
                        // Paranoia: is it the book we wanted ?
                        || mNavHelper.getBookId() != book.getId()) {
                        // Should never happen... flw
                        mNavHelper = null;
                    }
                }
            }
        }
    }

    /**
     * Called after the user swipes back/forwards through the flattened booklist.
     *
     * @param book      Current book
     * @param direction to move
     *
     * @return {@code true} if we moved
     */
    public boolean move(@NonNull final Book book,
                        @NonNull final BooklistNavigator.Direction direction) {

        if (mNavHelper != null && mNavHelper.move(direction)) {
            final long bookId = mNavHelper.getBookId();
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
