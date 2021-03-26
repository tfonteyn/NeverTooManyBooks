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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class LoaneeDaoImpl
        extends BaseDaoImpl
        implements LoaneeDao {

    /** Log tag. */
    private static final String TAG = "LoaneeDaoImpl";

    /** Get the name of the loanee of a {@link Book} by the Book id. */
    private static final String SELECT_BY_BOOK_ID =
            SELECT_ + DBKeys.KEY_LOANEE
            + _FROM_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
            + _WHERE_ + DBKeys.KEY_FK_BOOK + "=?";

    /** name only. */
    private static final String SELECT_ALL =
            SELECT_DISTINCT_ + DBKeys.KEY_LOANEE
            + _FROM_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
            + _WHERE_ + DBKeys.KEY_LOANEE + "<> ''"
            + _ORDER_BY_ + DBKeys.KEY_LOANEE + _COLLATION;

    /** Lend a book. */
    private static final String INSERT =
            INSERT_INTO_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
            + '(' + DBKeys.KEY_FK_BOOK
            + ',' + DBKeys.KEY_LOANEE
            + ") VALUES(?,?)";

    /** Delete the loan of a {@link Book}; i.e. 'return the book'. */
    private static final String DELETE_BY_BOOK_ID =
            DELETE_FROM_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
            + _WHERE_ + DBKeys.KEY_FK_BOOK + "=?";

    /**
     * Constructor.
     */
    public LoaneeDaoImpl() {
        super(TAG);
    }

    @Override
    public boolean setLoanee(@IntRange(from = 1) final long bookId,
                             @Nullable final String loanee) {
        final boolean success = setLoaneeInternal(bookId, loanee);
        if (success) {
            touchBook(bookId);
        }
        return success;
    }

    @Override
    public boolean setLoanee(@NonNull final Book book,
                             @Nullable final String loanee) {

        final boolean success = setLoaneeInternal(book.getId(), loanee);
        if (success) {
            touchBook(book);
        }
        return success;
    }

    /**
     * Lend out a book / return a book.
     * The book's {@link DBKeys#KEY_UTC_LAST_UPDATED} <strong>will NOT</strong> be updated.
     *
     * @param bookId book to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    private boolean setLoaneeInternal(@IntRange(from = 1) final long bookId,
                                      @Nullable final String loanee) {

        boolean success = false;

        if (loanee == null || loanee.isEmpty()) {
            try (SynchronizedStatement stmt = mDb
                    .compileStatement(DELETE_BY_BOOK_ID)) {
                stmt.bindLong(1, bookId);
                success = stmt.executeUpdateDelete() == 1;
            }
        } else {

            final String current = getLoaneeByBookId(bookId);
            if (current == null || current.isEmpty()) {
                try (SynchronizedStatement stmt = mDb
                        .compileStatement(INSERT)) {
                    stmt.bindLong(1, bookId);
                    stmt.bindString(2, loanee);
                    success = stmt.executeInsert() > 0;
                }

            } else if (!loanee.equals(current)) {
                final ContentValues cv = new ContentValues();
                cv.put(DBKeys.KEY_LOANEE, loanee);
                success = 0 < mDb.update(DBDefinitions.TBL_BOOK_LOANEE.getName(), cv,
                                         DBKeys.KEY_FK_BOOK + "=?",
                                         new String[]{String.valueOf(bookId)});
            }
        }
        return success;
    }

    @Override
    @Nullable
    public String getLoaneeByBookId(@IntRange(from = 1) final long bookId) {

        try (SynchronizedStatement stmt = mDb.compileStatement(SELECT_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getList() {
        return getColumnAsStringArrayList(SELECT_ALL);
    }
}
