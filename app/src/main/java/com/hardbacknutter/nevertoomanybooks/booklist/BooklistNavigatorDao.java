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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Provide a simple interface to move around from book to book in the {@link Booklist} table
 * using the navigation peer-table.
 * Keeps track of current position and bookId.
 */
public final class BooklistNavigatorDao {

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";

    @NonNull
    private final SynchronizedStatement bookStmt;
    private final int rowCount;
    @NonNull
    private final SynchronizedDb db;
    @NonNull
    private final String listTableName;

    /**
     * Constructor.
     *
     * @param db            Underlying database
     * @param listTableName Name of underlying and <strong>existing</strong> table
     */
    public BooklistNavigatorDao(@NonNull final SynchronizedDb db,
                                @NonNull final String listTableName) {
        this.db = db;

        this.listTableName = listTableName;

        try (SynchronizedStatement stmt = db.compileStatement(
                "SELECT COUNT(*) FROM " + this.listTableName)) {
            rowCount = (int) stmt.simpleQueryForLongOrZero();
        }

        bookStmt = db.compileStatement(
                SELECT_ + DBKey.FK_BOOK
                + _FROM_ + this.listTableName + _WHERE_ + DBKey.PK_ID + "=?");
    }

    /**
     * Get the total number of rows (i.e. books) in the navigation table.
     *
     * @return row count
     */
    @IntRange(from = 1)
    public int getRowCount() {
        return rowCount;
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
    public int getRowNumber(final long listTableRowId) {
        // This method is only called once to get the initial row number
        try (SynchronizedStatement stmt = db.compileStatement(
                SELECT_ + DBKey.PK_ID + _FROM_ + listTableName
                + _WHERE_ + BooklistBuilder.FK_BL_ROW_ID + "=?")) {
            stmt.bindLong(1, listTableRowId);
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Reposition and get the book id to load.
     *
     * @param rowNumber the ROW number in the table
     *
     * @return book id
     *
     * @throws SQLiteDoneException which should NEVER happen... flw
     */
    public long getBookIdAtRow(@IntRange(from = 1) final int rowNumber)
            throws SQLiteDoneException {
        bookStmt.bindLong(1, rowNumber);
        return bookStmt.simpleQueryForLong();
    }

    public void close() {
        bookStmt.close();
    }
}
