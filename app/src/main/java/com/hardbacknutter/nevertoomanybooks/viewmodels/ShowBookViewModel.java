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
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public class ShowBookViewModel
        extends ViewModel
        implements ActivityResultViewModel {

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
    private DAO mDb;

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private BooklistStyle mStyle;
    /** <strong>Optionally</strong> passed. */
    @Nullable
    private ShowBookNavigator mNavHelper;

    /**
     * The <strong>current</strong> BOOK being displayed.
     * The only time this can be {@code null}
     * is when this class is just initialized, or when the Book was just deleted.
     */
    private Book mCurrentBook;

    /** The <strong>initial</strong> pager position being displayed. */
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
            mDb = new DAO(TAG);

            final long bookId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
            SanityCheck.requirePositiveValue(bookId, "KEY_PK_ID");
            mCurrentBook = Book.from(bookId, mDb);

            final String styleUuid = args.getString(BooklistStyle.BKEY_STYLE_UUID);
            if (styleUuid != null) {
                mStyle = StyleDAO.getStyleOrDefault(context, mDb, styleUuid);
            }

            // the list is optional
            // If present, the user can swipe to the next/previous book in the list.
            final String navTableName = args.getString(BKEY_NAV_TABLE_NAME);
            if (navTableName != null && !navTableName.isEmpty()) {
                final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                SanityCheck.requirePositiveValue(rowId, "BKEY_LIST_TABLE_ROW_ID");
                mNavHelper = new ShowBookNavigator(mDb, navTableName);
                mInitialPagerPosition = mNavHelper.getPositionOf(rowId);
            } else {
                mInitialPagerPosition = 1;
            }
        }
    }

    /**
     * Get the initial position of the pager.
     * <strong>Use only to set {@link androidx.viewpager2.widget.ViewPager2#setCurrentItem}</strong>
     *
     * @return nav position
     */
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
     * @param position to get
     *
     * @return book
     */
    @NonNull
    public Book reloadBookAt(@IntRange(from = 1) final int position) {
        if (mNavHelper != null) {
            mCurrentBook = mNavHelper.getBookAt(position);
        } else {
            mCurrentBook = Book.from(mCurrentBook.getId(), mDb);
        }
        return mCurrentBook;
    }

    /**
     * Get the book at the given <strong>pager</strong> position.
     *
     * @param position to get
     *
     * @return book
     */
    @NonNull
    public Book getBookAt(@IntRange(from = 1) final int position) {
        if (mNavHelper != null) {
            mCurrentBook = mNavHelper.getBookAt(position);
        }
        return mCurrentBook;
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @return {@code true} if the book is available for lending.
     */
    public boolean isAvailable(@IntRange(from = 1) final int position) {
        return getBookAt(position).getLoanee(mDb).isEmpty();
    }

    /**
     * The book was returned, remove the loanee.
     */
    public void deleteLoan(@IntRange(from = 1) final int position) {
        final Book book = getBookAt(position);
        book.remove(DBDefinitions.KEY_LOANEE);
        mDb.setLoanee(book, null, true);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean toggleRead(@IntRange(from = 1) final int position) {
        if (getBookAt(position).toggleRead(mDb)) {
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the current book.
     *
     * @param context Current context
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteBook(@NonNull final Context context,
                              @IntRange(from = 1) final int position) {
        final Book book = getBookAt(position);

        if (mDb.delete(context, book)) {
            mCurrentBook = null;
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    public DAO getDb() {
        return mDb;
    }

    @NonNull
    public List<Bookshelf> getAllBookshelves() {
        return mDb.getBookshelves();
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
     * @param context     current context
     * @param preferences Global preferences
     * @param cIdx        0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isCoverUsed(@NonNull final Context context,
                               @NonNull final SharedPreferences preferences,
                               @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBDefinitions.isCoverUsed(preferences, cIdx)) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields().isShowCover(context, preferences, cIdx);
        }
    }

    /**
     * Provide a simple interface to move around from book to book in the {@link Booklist} table
     * using the navigation peer-table.
     * Keeps track of current position and bookId.
     */
    private static class ShowBookNavigator {

        private static final String SELECT_ = "SELECT ";
        private static final String _FROM_ = " FROM ";
        private static final String _WHERE_ = " WHERE ";

        @NonNull
        private final SynchronizedStatement mGetBookStmt;
        private final int mRowCount;
        @NonNull
        private final DAO mDb;
        @NonNull
        private final String mListTableName;
        /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
        private boolean mCloseWasCalled;

        /**
         * Constructor.
         *
         * @param db            Database Access
         * @param listTableName Name of underlying and <strong>existing</strong> table
         */
        private ShowBookNavigator(@NonNull final DAO db,
                                  @NonNull final String listTableName) {

            mDb = db;
            mListTableName = listTableName;

            final SynchronizedDb syncDb = mDb.getSyncDb();

            try (SynchronizedStatement stmt = syncDb.compileStatement(
                    "SELECT COUNT(*) FROM " + listTableName)) {
                mRowCount = (int) stmt.simpleQueryForLongOrZero();
            }

            mGetBookStmt = syncDb.compileStatement(
                    SELECT_ + DBDefinitions.KEY_FK_BOOK
                    + _FROM_ + listTableName + _WHERE_ + DBDefinitions.KEY_PK_ID + "=?");
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
         * Get the position in the navigation table for the given list table row id.
         *
         * @param listTableRowId the Booklist table rowId to find
         *
         * @return position
         */
        @IntRange(from = 1)
        private int getPositionOf(final long listTableRowId) {
            try (SynchronizedStatement stmt = mDb.getSyncDb().compileStatement(
                    SELECT_ + DBDefinitions.KEY_PK_ID + _FROM_ + mListTableName
                    + _WHERE_ + DBDefinitions.KEY_FK_BL_ROW_ID + "=?")) {
                stmt.bindLong(1, listTableRowId);
                return (int) stmt.simpleQueryForLongOrZero();
            }
        }

        /**
         * Reposition and get the new Book.
         *
         * @return book
         *
         * @throws SQLiteDoneException which should NEVER happen... flw
         */
        private Book getBookAt(final int position)
                throws SQLiteDoneException {
            mGetBookStmt.bindLong(1, position);
            final long bookId = mGetBookStmt.simpleQueryForLong();
            return Book.from(bookId, mDb);
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
