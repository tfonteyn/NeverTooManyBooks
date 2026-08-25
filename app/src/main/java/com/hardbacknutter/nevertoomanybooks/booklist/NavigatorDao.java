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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Provide a simple API to move around from book to book in the {@link Booklist} table
 * using the navigation peer-table.
 */
public final class NavigatorDao
        implements Navigator {

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";

    @NonNull
    private final SynchronizedStatement bookStmt;
    private final int rowCount;

    /**
     * Constructor.
     *
     * @param db        Database Access
     * @param tableName Name of underlying and <strong>existing</strong> table
     */
    public NavigatorDao(@NonNull final SynchronizedDb db,
                        @NonNull final String tableName) {

        try (SynchronizedStatement stmt = db.compileStatement(
                "SELECT COUNT(*) FROM " + tableName)) {
            rowCount = (int) stmt.simpleQueryForLongOrZero();
        }

        bookStmt = db.compileStatement(
                SELECT_ + DBKey.FK_BOOK
                + _FROM_ + tableName + _WHERE_ + DBKey.PK_ID + "=?");
    }

    @Override
    @IntRange(from = 1)
    public int getRowCount() {
        return rowCount;
    }

    @Override
    @IntRange(from = 1)
    public long getBookId(@IntRange(from = 0) final int position)
            throws SQLiteDoneException, SQLException {
        // positions are 0-based, but the table row is 1-based
        bookStmt.bindLong(1, position + 1);
        return bookStmt.simpleQueryForLong();
    }

    @Override
    public void close() {
        bookStmt.close();
    }
}
