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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNavigatorDao;
import com.hardbacknutter.nevertoomanybooks.booklist.style.DetailScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
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
    private BookDao mBookDao;

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private ListStyle mStyle;
    /** <strong>Optionally</strong> passed. */
    @Nullable
    private BooklistNavigatorDao mNavHelper;

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
    }

    /**
     * <ul>
     * <li>{@link DBKey#PK_ID}  book id</li>
     * <li>{@link Entity#BKEY_DATA_MODIFIED}      boolean</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        // always set the *current* book id, so the BoB list can reposition correctly.
        if (mCurrentBook != null) {
            mResultIntent.putExtra(DBKey.PK_ID, mCurrentBook.getId());
        }
        return mResultIntent;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mBookDao == null) {
            mBookDao = ServiceLocator.getInstance().getBookDao();

            final long bookId = args.getLong(DBKey.PK_ID, 0);
            SanityCheck.requirePositiveValue(bookId, "KEY_PK_ID");
            mCurrentBook = Book.from(bookId, mBookDao);

            final String styleUuid = args.getString(ListStyle.BKEY_STYLE_UUID);
            if (styleUuid != null) {
                mStyle = ServiceLocator.getInstance().getStyles()
                                       .getStyleOrDefault(context, styleUuid);
            }

            // the list is optional
            // If present, the user can swipe to the next/previous book in the list.
            final String navTableName = args.getString(BKEY_NAV_TABLE_NAME);
            if (navTableName != null && !navTableName.isEmpty()) {
                final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                SanityCheck.requirePositiveValue(rowId, "BKEY_LIST_TABLE_ROW_ID");
                mNavHelper = new BooklistNavigatorDao(navTableName);
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
    int getInitialPagerPosition() {
        return mInitialPagerPosition;
    }

    @IntRange(from = 1)
    int getRowCount() {
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
    Book reloadBookAtPosition(@IntRange(from = 0) final int position) {
        if (mNavHelper != null) {
            mCurrentBook = Book.from(mNavHelper.getBookIdAtRow(position + 1), mBookDao);
        } else {
            mCurrentBook = Book.from(mCurrentBook.getId(), mBookDao);
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
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Book getBookAtPosition(@IntRange(from = 0) final int position) {
        if (mNavHelper != null) {
            mCurrentBook = Book.from(mNavHelper.getBookIdAtRow(position + 1), mBookDao);
        }
        return mCurrentBook;
    }

    /**
     * Get the currently displayed book.
     *
     * @return current book
     */
    @NonNull
    public Book getCurrentBook() {
        // Sanity check
        if (mCurrentBook == null) {
            mCurrentBook = getBookAtPosition(0);
        }
        return Objects.requireNonNull(mCurrentBook);
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @param position pager position to get the book for
     *
     * @return {@code true} if the book is available for lending.
     */
    boolean isAvailable(@IntRange(from = 0) final int position) {
        return getBookAtPosition(position).getLoanee().isEmpty();
    }

    /**
     * The book was returned, remove the loanee.
     *
     * @param position pager position to get the book for
     */
    void deleteLoan(@IntRange(from = 0) final int position) {
        final Book book = getBookAtPosition(position);
        book.remove(DBKey.KEY_LOANEE);
        ServiceLocator.getInstance().getLoaneeDao().setLoanee(book, null);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @param position pager position to get the book for
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean toggleRead(@IntRange(from = 0) final int position) {
        if (getBookAtPosition(position).toggleRead()) {
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the current book.
     *
     * @param position pager position to get the book for
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook(@IntRange(from = 1) final int position) {
        final Book book = getBookAtPosition(position);

        if (mBookDao.delete(book)) {
            //noinspection ConstantConditions
            mCurrentBook = null;
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
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
    boolean isCoverUsed(@NonNull final SharedPreferences global,
                        @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx])) {
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

    boolean isShowTocByDefault(@NonNull final SharedPreferences global) {
        // Globally disabled overrules style setting
        if (!DBKey.isUsed(global, DBKey.BITMASK_TOC)) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields()
                         .isShowField(global, DetailScreenBookFields.PK_SHOW_TOC_BY_DEFAULT);
        }
    }
}
