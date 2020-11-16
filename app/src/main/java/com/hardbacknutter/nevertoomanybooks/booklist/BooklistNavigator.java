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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.sqlite.SQLiteStatement;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.Closeable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;

/**
 * Provide a simple interface to move around from book to book in the {@link Booklist} table.
 * Keeps track of current rowId and bookId.
 * <p>
 * Reminder: moveNext/Prev SQL concatenates/splits two columns.
 * This is to get around the limitation of {@link SQLiteStatement}
 * only able to return a single column.
 * Alternative is to use a raw query, but those cannot be re-used (pre-compiled).
 */
public class BooklistNavigator
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "BooklistNavigator";

    /** Table name of the {@link Booklist} table. */
    public static final String BKEY_LIST_TABLE_NAME = TAG + ":LTName";
    public static final String BKEY_LIST_TABLE_ROW_ID = TAG + ":LTRow";

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";

    /** Name for the 'next' statement. */
    private static final String STMT_NEXT = "next";
    /** Name for the 'prev' statement. */
    private static final String STMT_PREV = "prev";
    /** Name for the 'move-to' statement. */
    private static final String STMT_MOVE = "move";
    /** Collection of statements pre-compiled for this object. */
    @NonNull
    private final SqlStatementManager mSqlStatementManager;
    /** Underlying table definition. */
    @NonNull
    private final String mListTableName;

    /** Current position; used to navigate around with next/prev. */
    private long mRowId;
    /** Book id at the currently selected row. */
    @IntRange(from = 0)
    private long mBookId;
    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param db            Database Access
     * @param listTableName Name of underlying and <strong>existing</strong> table
     */
    public BooklistNavigator(@NonNull final SynchronizedDb db,
                             @NonNull final String listTableName) {
        mListTableName = listTableName;
        mSqlStatementManager = new SqlStatementManager(db, TAG + "|" + listTableName);
    }

    /**
     * Get the current book.
     *
     * @return bookId, or {@code 0} if the last move operation failed.
     */
    @IntRange(from = 0)
    public long getBookId() {
        return mBookId;
    }

    /**
     * Move to the specified book row, based on the row ID in the <strong>list table</strong>.
     * <p>
     * We can't use the book id, as a book can occur multiple times
     * in the list (depending on style settings)
     *
     * @param rowId to move to
     *
     * @return {@code true} on success
     */
    public boolean moveTo(final long rowId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(STMT_MOVE, () ->
                SELECT_ + DBDefinitions.KEY_PK_ID + "||'/'||" + DBDefinitions.KEY_FK_BOOK
                + _FROM_ + mListTableName + _WHERE_ + DBDefinitions.KEY_PK_ID + "=?");
        stmt.bindLong(1, rowId);
        return execAndParse(stmt);
    }

    /**
     * Move to the next book row.
     *
     * @return {@code true} on success
     */
    private boolean moveNext() {
        final SynchronizedStatement stmt = mSqlStatementManager.get(STMT_NEXT, () ->
                SELECT_ + DBDefinitions.KEY_PK_ID + "||'/'||" + DBDefinitions.KEY_FK_BOOK
                + _FROM_ + mListTableName + _WHERE_ + DBDefinitions.KEY_PK_ID + ">?"
                + _AND_ + DBDefinitions.KEY_FK_BOOK + "<>COALESCE(?,-1)"
                + _ORDER_BY_ + DBDefinitions.KEY_PK_ID + " ASC LIMIT 1");

        stmt.bindLong(1, mRowId);
        if (mBookId > 0) {
            stmt.bindLong(2, mBookId);
        } else {
            stmt.bindNull(2);
        }
        return execAndParse(stmt);
    }

    /**
     * Move to the previous book row.
     *
     * @return {@code true} on success
     */
    private boolean movePrev() {
        final SynchronizedStatement stmt = mSqlStatementManager.get(STMT_PREV, () ->
                SELECT_ + DBDefinitions.KEY_PK_ID + "||'/'||" + DBDefinitions.KEY_FK_BOOK
                + _FROM_ + mListTableName + _WHERE_ + DBDefinitions.KEY_PK_ID + "<?"
                + _AND_ + DBDefinitions.KEY_FK_BOOK + "<>COALESCE(?,-1)"
                + _ORDER_BY_ + DBDefinitions.KEY_PK_ID + " DESC LIMIT 1");

        stmt.bindLong(1, mRowId);
        if (mBookId > 0) {
            stmt.bindLong(2, mBookId);
        } else {
            stmt.bindNull(2);
        }
        return execAndParse(stmt);
    }

    public boolean move(@NonNull final Direction direction) {
        switch (direction) {
            case First:
                mRowId = 0;
                mBookId = 0;
                return moveNext();

            case Previous:
                return movePrev();

            case Next:
                return moveNext();

            case Last:
                mRowId = Long.MAX_VALUE;
                mBookId = 0;
                return movePrev();
        }

        throw new IllegalArgumentException(String.valueOf(direction));
    }

    /**
     * Execute the passed statement and update the current row details.
     *
     * @param stmt to execute
     *
     * @return {@code true} on success
     */
    private boolean execAndParse(@NonNull final SynchronizedStatement stmt) {
        final String result = stmt.simpleQueryForStringOrNull();
        if (result != null) {
            final String[] data = result.split("/");
            mRowId = Long.parseLong(data[0]);
            mBookId = Long.parseLong(data[1]);
            return true;

        } else {
            mRowId = 0;
            mBookId = 0;
            return false;
        }
    }

    @Override
    public void close() {
        mCloseWasCalled = true;
        mSqlStatementManager.close();
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

    public enum Direction {
        First, Previous, Next, Last
    }
}
