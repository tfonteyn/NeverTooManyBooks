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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.Closeable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;

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
    private final SynchronizedStatement mGetBookStmt;
    private final int mRowCount;
    /** Database Access. */
    @NonNull
    private final SynchronizedDb mDb;
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
    public BooklistNavigatorDao(@NonNull final Context context,
                                @NonNull final String listTableName) {

        mDb = DBHelper.getDb(context);
        mListTableName = listTableName;

        try (SynchronizedStatement stmt = mDb.compileStatement(
                "SELECT COUNT(*) FROM " + mListTableName)) {
            mRowCount = (int) stmt.simpleQueryForLongOrZero();
        }

        mGetBookStmt = mDb.compileStatement(
                SELECT_ + DBDefinitions.KEY_FK_BOOK
                + _FROM_ + mListTableName + _WHERE_ + DBDefinitions.KEY_PK_ID + "=?");
    }

    /**
     * Get the total number of rows (i.e. books) in the navigation table.
     *
     * @return row count
     */
    @IntRange(from = 1)
    public int getRowCount() {
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
    public int getRowNumber(final long listTableRowId) {
        // This method is only called once to get the initial row number
        try (SynchronizedStatement stmt = mDb.compileStatement(
                SELECT_ + DBDefinitions.KEY_PK_ID + _FROM_ + mListTableName
                + _WHERE_ + DBDefinitions.KEY_FK_BL_ROW_ID + "=?")) {
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
        mGetBookStmt.bindLong(1, rowNumber);
        return mGetBookStmt.simpleQueryForLong();
    }

    public void close() {
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
