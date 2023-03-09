/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNavigatorDao;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class ShowBookPagerViewModel
        extends ViewModel {

    private static final String TAG = "ShowBookPagerViewModel";

    /** Table name of the {@link Booklist} table. */
    public static final String BKEY_NAV_TABLE_NAME = TAG + ":LTName";
    /** The row id in the list table for the initial book to show. */
    public static final String BKEY_LIST_TABLE_ROW_ID = TAG + ":LTRow";

    private final MutableLiveData<Long> currentBookId = new MutableLiveData<>();

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private BooklistNavigatorDao navHelper;

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
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Bundle args) {
        if (initialBookId == 0) {
            initialBookId = args.getLong(DBKey.FK_BOOK, 0);
            if (initialBookId <= 0) {
                throw new IllegalArgumentException(DBKey.FK_BOOK);
            }

            // the list is optional
            // If present, the user can swipe to the next/previous book in the list.
            final String navTableName = args.getString(BKEY_NAV_TABLE_NAME);
            if (navTableName != null && !navTableName.isEmpty()) {
                final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                if (rowId <= 0) {
                    throw new IllegalArgumentException(BKEY_LIST_TABLE_ROW_ID);
                }
                final SynchronizedDb db = ServiceLocator.getInstance().getDb();
                navHelper = new BooklistNavigatorDao(db, navTableName);
                initialPagerPosition = navHelper.getRowNumber(rowId) - 1;
            } else {
                initialPagerPosition = 0;
            }
        }
    }

    /**
     * Get the initial position of the pager.
     * <strong>Use only to set {@link androidx.viewpager2.widget.ViewPager2#setCurrentItem}</strong>
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
            return navHelper.getBookIdAtRow(position + 1);
        }
        return initialBookId;
    }

    @NonNull
    MutableLiveData<Long> onCurrentBookUpdated() {
        return currentBookId;
    }

    /**
     * Called when the user swipes to the next or previous book.
     *
     * @param position new 'current' position
     */
    void setPageSelected(final int position) {
        if (navHelper != null) {
            currentBookId.setValue(navHelper.getBookIdAtRow(position + 1));
        } else {
            currentBookId.setValue(initialBookId);
        }
    }
}
