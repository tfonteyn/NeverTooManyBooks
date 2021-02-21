/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.io.Closeable;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleUtils;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public class ShowBookViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Log tag. */
    private static final String TAG = "ShowBookViewModel";

    /** Table name of the {@link Booklist} table. */
    public static final String BKEY_NAV_TABLE_NAME = TAG + ":LTName";
    /** The row id in the list table for the initial book to show. */
    public static final String BKEY_LIST_TABLE_ROW_ID = TAG + ":LTRow";

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();

    /** Database Access. */
    private BookDao mDb;

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private ListStyle mStyle;
    /** <strong>Optionally</strong> passed. */
    @Nullable
    private ShowBookNavigator mNavHelper;

    /**
     * The <strong>current</strong> BOOK being displayed.
     * The only time this can be {@code null}
     * is when this class is just initialized, or when the Book was just deleted.
     */
    private Book mCurrentBook;

    /**
     * The <strong>initial</strong> pager position being displayed.
     * This is {@code 0} based as it's the recycler view list position.
     */
    @IntRange(from = 0)
    private int mInitialPagerPosition;

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @Override
    public void onCleared() {
        if (mNavHelper != null) {
            mNavHelper.close();
        }
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * <ul>
     * <li>{@link DBDefinitions#KEY_PK_ID}  book id</li>
     * <li>{@link Entity#BKEY_DATA_MODIFIED}      boolean</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        // always set the *current* book id, so the BoB list can reposition correctly.
        if (mCurrentBook != null) {
            mResultIntent.putExtra(DBDefinitions.KEY_PK_ID, mCurrentBook.getId());
        }
        return mResultIntent;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new BookDao(context, TAG);

            final long bookId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
            SanityCheck.requirePositiveValue(bookId, "KEY_PK_ID");
            mCurrentBook = Book.from(bookId, mDb);

            final String styleUuid = args.getString(ListStyle.BKEY_STYLE_UUID);
            if (styleUuid != null) {
                mStyle = StyleUtils.getStyleOrDefault(context, styleUuid);
            }

            // the list is optional
            // If present, the user can swipe to the next/previous book in the list.
            final String navTableName = args.getString(BKEY_NAV_TABLE_NAME);
            if (navTableName != null && !navTableName.isEmpty()) {
                final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                SanityCheck.requirePositiveValue(rowId, "BKEY_LIST_TABLE_ROW_ID");
                mNavHelper = new ShowBookNavigator(context, navTableName);
                mInitialPagerPosition = mNavHelper.getRowNumber(rowId) - 1;
            } else {
                mInitialPagerPosition = 0;
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
    public int getInitialPagerPosition() {
        return mInitialPagerPosition;
    }

    @IntRange(from = 1)
    public int getRowCount() {
        if (mNavHelper != null) {
            return mNavHelper.getRowCount();
        } else {
            return 1;
        }
    }

    /**
     * Force reload the book at the given <strong>pager</strong> position.
     *
     * @param position pager position to get the book for
     *
     * @return book
     */
    @NonNull
    public Book reloadBookAtPosition(@IntRange(from = 0) final int position) {
        if (mNavHelper != null) {
            mCurrentBook = Book.from(mNavHelper.getBookIdAtRow(position + 1), mDb);
        } else {
            mCurrentBook = Book.from(mCurrentBook.getId(), mDb);
        }
        return mCurrentBook;
    }

    /**
     * Get the book at the given <strong>pager</strong> position.
     *
     * @param position pager position to get the book for
     *
     * @return book
     */
    @NonNull
    public Book getBookAtPosition(@IntRange(from = 0) final int position) {
        if (mNavHelper != null) {
            mCurrentBook = Book.from(mNavHelper.getBookIdAtRow(position + 1), mDb);
        }
        return mCurrentBook;
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @param position pager position to get the book for
     *
     * @return {@code true} if the book is available for lending.
     */
    public boolean isAvailable(@IntRange(from = 0) final int position) {
        return getBookAtPosition(position).getLoanee().isEmpty();
    }

    /**
     * The book was returned, remove the loanee.
     *
     * @param position pager position to get the book for
     */
    public void deleteLoan(@IntRange(from = 0) final int position) {
        final Book book = getBookAtPosition(position);
        book.remove(DBDefinitions.KEY_LOANEE);
        LoaneeDao.getInstance().setLoanee(book, null);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @param position pager position to get the book for
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean toggleRead(@IntRange(from = 0) final int position) {
        if (getBookAtPosition(position).toggleRead(mDb)) {
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the current book.
     *
     * @param context  Current context
     * @param position pager position to get the book for
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteBook(@NonNull final Context context,
                              @IntRange(from = 1) final int position) {
        final Book book = getBookAtPosition(position);

        if (mDb.delete(context, book)) {
            //noinspection ConstantConditions
            mCurrentBook = null;
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    public BookDao getDb() {
        return mDb;
    }

    @NonNull
    public List<Bookshelf> getAllBookshelves() {
        return BookshelfDao.getInstance().getAll();
    }

    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>The fragment has no access to the style -> return the global visibility</li>
     *     <li>The global style is set to HIDE -> {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param global Global preferences
     * @param cIdx   0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isCoverUsed(@NonNull final SharedPreferences global,
                               @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBDefinitions.isCoverUsed(global, cIdx)) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields().isShowCover(global, cIdx);
        }
    }

    /**
     * Provide a simple interface to move around from book to book in the {@link Booklist} table
     * using the navigation peer-table.
     * Keeps track of current position and bookId.
     */
    private static final class ShowBookNavigator {

        private static final String SELECT_ = "SELECT ";
        private static final String _FROM_ = " FROM ";
        private static final String _WHERE_ = " WHERE ";

        @NonNull
        private final SynchronizedStatement mGetBookStmt;
        private final int mRowCount;
        /** Database Access. */
        @NonNull
        private final SynchronizedDb mSyncDb;
        @NonNull
        private final String mListTableName;
        /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
        private boolean mCloseWasCalled;

        /**
         * Constructor.
         *
         * @param context       Current context
         * @param listTableName Name of underlying and <strong>existing</strong> table
         */
        private ShowBookNavigator(@NonNull final Context context,
                                  @NonNull final String listTableName) {

            mSyncDb = DBHelper.getSyncDb(context);
            mListTableName = listTableName;

            try (SynchronizedStatement stmt = mSyncDb.compileStatement(
                    "SELECT COUNT(*) FROM " + mListTableName)) {
                mRowCount = (int) stmt.simpleQueryForLongOrZero();
            }

            mGetBookStmt = mSyncDb.compileStatement(
                    SELECT_ + DBDefinitions.KEY_FK_BOOK
                    + _FROM_ + mListTableName + _WHERE_ + DBDefinitions.KEY_PK_ID + "=?");
        }

        /**
         * Get the total number of rows (i.e. books) in the navigation table.
         *
         * @return row count
         */
        @IntRange(from = 1)
        private int getRowCount() {
            return mRowCount;
        }

        /**
         * Get the row number in the navigation table for the given list table row id.
         * This is {@code 1} based.
         *
         * @param listTableRowId the Booklist table rowId to find
         *
         * @return row number
         */
        @IntRange(from = 1)
        private int getRowNumber(final long listTableRowId) {
            // This method is only called once to get the initial row number
            try (SynchronizedStatement stmt = mSyncDb.compileStatement(
                    SELECT_ + DBDefinitions.KEY_PK_ID + _FROM_ + mListTableName
                    + _WHERE_ + DBDefinitions.KEY_FK_BL_ROW_ID + "=?")) {
                stmt.bindLong(1, listTableRowId);
                return (int) stmt.simpleQueryForLongOrZero();
            }
        }

        /**
         * Reposition and get the book id to load
         *
         * @param rowNumber the ROW number in the table
         *
         * @return book id
         *
         * @throws SQLiteDoneException which should NEVER happen... flw
         */
        private long getBookIdAtRow(@IntRange(from = 1) final int rowNumber)
                throws SQLiteDoneException {
            mGetBookStmt.bindLong(1, rowNumber);
            return mGetBookStmt.simpleQueryForLong();
        }

        private void close() {
            mCloseWasCalled = true;
            mGetBookStmt.close();
        }

        /**
         * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
         */
        @SuppressWarnings("FinalizeDeclaration")
        @Override
        @CallSuper
        protected void finalize()
                throws Throwable {
            if (!mCloseWasCalled) {
                close();
            }
            super.finalize();
        }
    }
}
