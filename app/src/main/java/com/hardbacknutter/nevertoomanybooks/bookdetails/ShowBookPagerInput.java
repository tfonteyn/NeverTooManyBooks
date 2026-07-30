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

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class ShowBookPagerInput {

    private static final String TAG = "ShowBookPagerInput";

    /** Table name of the {@link Booklist} navigator table. */
    private static final String BKEY_NAV_TABLE_NAME = TAG + ":tableName";
    /** The position (int) in the navigator list for the initial book to show. */
    private static final String BKEY_NAV_POSITION = TAG + ":pos";

    @IntRange(from = 1)
    private final long bookId;
    @NonNull
    private final Bookshelf bookshelf;
    private final int position;

    @Nullable
    private final String navTableName;
    @Nullable
    private final List<Long> bookIdList;

    /**
     * Constructor.
     *
     * @param bookId    Initial book id to show.
     *                  Used by the pager.
     * @param bookshelf current Bookshelf displayed by the BoB
     *                  Used by the book-details.
     */
    public ShowBookPagerInput(@IntRange(from = 1) final long bookId,
                              @NonNull final Bookshelf bookshelf) {
        this(bookId, bookshelf, 0, null, null);
    }

    /**
     * Constructor.
     *
     * @param bookId     Initial book id to show.
     *                   Used by the pager.
     * @param bookshelf  current Bookshelf displayed by the BoB
     *                   Used by the book-details.
     * @param position   The position of the given book.
     *                   Keep in mind a book can occur multiple times,
     *                   so we need to pass the specific position.
     *                   Ignored if navTableName is {@code null}.
     *                   Used by the pager.
     * @param bookIdList The list of book ids to display.
     *                   Used by the pager.
     */
    public ShowBookPagerInput(@IntRange(from = 1) final long bookId,
                              @NonNull final Bookshelf bookshelf,
                              @IntRange(from = 0) final int position,
                              @NonNull final List<Long> bookIdList) {
        this(bookId, bookshelf, position, bookIdList, null);
    }

    /**
     * Constructor.
     *
     * @param bookId       Initial book id to show.
     *                     Used by the pager.
     * @param bookshelf    current Bookshelf displayed by the BoB
     *                     Used by the book-details.
     * @param position     The position of the given book.
     *                     Keep in mind a book can occur multiple times,
     *                     so we need to pass the specific position.
     *                     Used by the pager.
     * @param navTableName The name of the current list-navigation table.
     *                     Used by the pager.
     */
    public ShowBookPagerInput(@IntRange(from = 1) final long bookId,
                              @NonNull final Bookshelf bookshelf,
                              @IntRange(from = 0) final int position,
                              @NonNull final String navTableName) {
        this(bookId, bookshelf, position, null, navTableName);
    }

    private ShowBookPagerInput(final long bookId,
                               @NonNull final Bookshelf bookshelf,
                               final int position,
                               @Nullable final List<Long> bookIdList,
                               @Nullable final String navTableName) {
        this.bookId = bookId;
        this.bookshelf = bookshelf;
        this.position = position;
        this.navTableName = navTableName;
        this.bookIdList = bookIdList;
    }

    /**
     * Constructor.
     *
     * @param args to read
     *
     * @return instance
     */
    @NonNull
    static ShowBookPagerInput fromBundle(@NonNull final Bundle args) {
        // book-details page
        @SuppressWarnings("deprecation")
        final Bookshelf bookshelf = Objects.requireNonNull(
                args.getParcelable(DBKey.FK_BOOKSHELF), DBKey.FK_BOOKSHELF);

        // Pager
        final long bookId = args.getLong(DBKey.FK_BOOK, 0);
        final int position = args.getInt(BKEY_NAV_POSITION, 0);
        final String navTableName = args.getString(BKEY_NAV_TABLE_NAME);
        final List<Long> bookIdList = ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST);

        return new ShowBookPagerInput(bookId, bookshelf, position, bookIdList, navTableName);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle();
        // book-details page
        args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

        // Pager
        args.putLong(DBKey.FK_BOOK, bookId);
        args.putInt(BKEY_NAV_POSITION, position);
        if (navTableName != null) {
            args.putString(BKEY_NAV_TABLE_NAME, navTableName);
        }
        if (bookIdList != null && !bookIdList.isEmpty()) {
            args.putParcelable(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList));
        }
        return args;
    }

    long getBookId() {
        return bookId;
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    int getPosition() {
        return position;
    }

    @Nullable
    String getNavTableName() {
        return navTableName;
    }

    @Nullable
    List<Long> getBookIdList() {
        return bookIdList;
    }
}
