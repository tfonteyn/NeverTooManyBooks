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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class LoaneeDaoImpl
        extends BaseDaoImpl
        implements LoaneeDao {

    /** Log tag. */
    private static final String TAG = "LoaneeDaoImpl";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public LoaneeDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    public boolean setLoanee(@NonNull final Book book) {
        final String loanee = book.getString(DBKey.LOANEE_NAME);
        if (loanee.isEmpty()) {
            return delete(book);
        } else {
            return insertOrUpdate(book.getId(), loanee);
        }
    }

    @Override
    public boolean setLoanee(@IntRange(from = 1) final long bookId,
                             @Nullable final String loanee) {
        if (loanee == null || loanee.isEmpty()) {
            return delete(bookId);
        } else {
            return insertOrUpdate(bookId, loanee);
        }
    }


    private boolean insertOrUpdate(@IntRange(from = 1) final long bookId,
                                   @NonNull final String loanee) {
        final String current = findLoaneeByBookId(bookId);
        if (current == null || current.isEmpty()) {
            return insert(bookId, loanee);

        } else if (!loanee.equals(current)) {
            // This is currently not reachable from the user-menu's
            // but leaving this in place for the future.
            return update(bookId, loanee);
        }
        return false;
    }

    private boolean insert(@IntRange(from = 1) final long bookId,
                           @NonNull final String loanee) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindLong(1, bookId);
            stmt.bindString(2, loanee);
            return stmt.executeInsert() > 0;
        }
    }

    private boolean update(@IntRange(from = 1) final long bookId,
                           @NonNull final String loanee) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, loanee);

            stmt.bindLong(2, bookId);
            return stmt.executeUpdateDelete() > 0;
        }
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        if (delete(book.getId())) {
            book.remove(DBKey.LOANEE_NAME);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(@IntRange(from = 1) final long bookId) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            rowsAffected = stmt.executeUpdateDelete();
        }
        return rowsAffected > 0;
    }

    @Override
    @Nullable
    public String findLoaneeByBookId(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    @Override
    @NonNull
    public List<String> getList() {
        return getColumnAsStringArrayList(Sql.SELECT_ALL);
    }

    private static final class Sql {

        /** Lend a book. */
        static final String INSERT =
                INSERT_INTO_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.LOANEE_NAME
                + ") VALUES(?,?)";

        static final String UPDATE =
                UPDATE_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
                + _SET_ + DBKey.LOANEE_NAME + "=?"
                + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** Delete the loan of a {@link Book}; i.e. 'return the book'. */
        static final String DELETE_BY_BOOK_ID =
                DELETE_FROM_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** Get the name of the loanee of a {@link Book} by its id. */
        static final String FIND_BY_BOOK_ID =
                SELECT_ + DBKey.LOANEE_NAME
                + _FROM_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** A list of the names of all people who have books lend out to. Ordered by name. */
        static final String SELECT_ALL =
                SELECT_DISTINCT_ + DBKey.LOANEE_NAME
                + _FROM_ + DBDefinitions.TBL_BOOK_LOANEE.getName()
                + _WHERE_ + DBKey.LOANEE_NAME + "<> ''"
                + _ORDER_BY_ + DBKey.LOANEE_NAME + _COLLATION;
    }
}
