/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.Navigator;
import com.hardbacknutter.nevertoomanybooks.booklist.NavigatorDao;
import com.hardbacknutter.nevertoomanybooks.booklist.NavigatorList;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class ShowBookPagerViewModel
        extends ViewModel {

    private static final String TAG = "ShowBookPagerViewModel";

    /** Table name of the {@link Booklist} navigator table. */
    public static final String BKEY_NAV_TABLE_NAME = TAG + ":tableName";
    /** The position (int) in the navigator list for the initial book to show. */
    public static final String BKEY_NAV_POSITION = TAG + ":pos";

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private Navigator navHelper;

    /**
     * The <strong>initial</strong> pager position being displayed.
     * This is {@code 0} based as it's the recycler view list position.
     */
    @IntRange(from = 0)
    private int initialPagerPosition;
    /** The <strong>initial</strong> book id to show. */
    private long initialBookId;

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @Override
    public void onCleared() {
        if (navHelper != null) {
            navHelper.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     *
     * @return {@code true} on success.
     *         {@code false} if we should abort, and go back to the previous Activity
     *         (normally the BoB)
     *
     * @throws IllegalArgumentException if there are missing mandatory arguments
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public boolean init(@NonNull final Bundle args) {
        if (initialBookId == 0) {
            initialBookId = args.getLong(DBKey.FK_BOOK, 0);
            if (initialBookId <= 0) {
                throw new IllegalArgumentException(DBKey.FK_BOOK);
            }

            initialPagerPosition = args.getInt(BKEY_NAV_POSITION, 0);

            // the navTable is optional
            // If present, the user can swipe to the next/previous book in the list.
            final String navTableName = args.getString(BKEY_NAV_TABLE_NAME, null);
            if (navTableName != null && !navTableName.isEmpty()) {
                // GitHub #90 + #140
                // When the app was displaying a book-detail, and the user switched to other apps,
                // it's possible that Android freezes us (or kills us, but that is of no concern).
                // In a "warm start", the 'SaveState' was preserved, i.e. the fact that we're
                // "here" with the arguments being available.
                // The temporary tables MAY have been deleted due to the db connection
                // having been closed. This last part is speculation as I can't find
                // explicit info on WHEN/WHERE the db would be closed.
                //
                // We cannot recreate the navTable here as it relies on the BoB list-table.
                // Solution: return 'false' and let the current Fragment, do a 'back'
                // to take the user to the BoB screen.
                final SynchronizedDb db = ServiceLocator.getInstance().getDb();
                if (db.tableExists(navTableName)) {
                    // we have a navTable, init and display as normal
                    navHelper = new NavigatorDao(db, navTableName);
                    return true;
                } else {
                    // navTable expected but not there; we must have done a "warm start".
                    // ABORT!
                    return false;
                }
            }

            if (args.containsKey(Book.BKEY_BOOK_ID_LIST)) {
                final List<Long> idList = Objects.requireNonNull(
                        ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST));
                if (!idList.isEmpty()) {
                    navHelper = new NavigatorList(idList);
                    return true;
                }
            }

            // no navTable given, and no explicit id list.
            // Just display the single book.
        }
        return true;
    }

    /**
     * Get the initial position of the pager.
     * <strong>Used only to set
     * {@link androidx.viewpager2.widget.ViewPager2#setCurrentItem}</strong>
     *
     * @return pager position
     */
    @IntRange(from = 0)
    int getInitialPagerPosition() {
        return initialPagerPosition;
    }

    /**
     * Provides the row count to be used in the ViewPager adapter.
     *
     * @return row count
     */
    @IntRange(from = 1)
    int getRowCount() {
        if (navHelper != null) {
            return navHelper.getRowCount();
        } else {
            return 1;
        }
    }

    /**
     * Translate the position to the book id at that position.
     *
     * @param position to look up
     *
     * @return the book id at that position
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    long getBookIdAtPosition(@IntRange(from = 0) final int position) {
        if (navHelper != null) {
            return navHelper.getBookId(position);
        }
        return initialBookId;
    }
}
