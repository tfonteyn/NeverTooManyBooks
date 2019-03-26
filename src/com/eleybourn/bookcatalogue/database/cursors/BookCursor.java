/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;

import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;

/**
 * Cursor implementation for book-related queries. The cursor wraps common
 * column lookups and reduces code clutter when accessing common columns by
 * providing a {@link BookCursorRow}
 * <p>
 * Note: why are the methods of BookCursorRow not simply here?
 * Answer: (I think) because this way we have two-way inheritance,
 * parts of the RowView can be re-used for different cursors.
 *
 * @author Philip Warner
 */
public class BookCursor
        extends TrackedCursor
        implements Closeable {

    /** column position for the id column. */
    private int mIdCol = -2;

    /** Cached RowView for this cursor. */
    @Nullable
    private BookCursorRow mBookCursorRow;

    /**
     * Constructor.
     *
     * @param driver    Part of standard cursor constructor.
     * @param editTable Part of standard cursor constructor.
     * @param query     Part of standard cursor constructor.
     * @param sync      Synchronizer object
     */
    public BookCursor(@NonNull final SQLiteCursorDriver driver,
                      @NonNull final String editTable,
                      @NonNull final SQLiteQuery query,
                      @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);
    }

    /**
     * Local implementation for when there is no CursorRow.
     *
     * @return the row ID
     */
    public final long getId() {
        if (mIdCol < 0) {
            mIdCol = getColumnIndex(DatabaseDefinitions.DOM_PK_ID.name);
            if (mIdCol < 0) {
                throw new ColumnNotPresentException(DatabaseDefinitions.DOM_PK_ID.name);
            }
        }
        return getLong(mIdCol);
    }

    /**
     * @return the row object
     */
    @NonNull
    public BookCursorRow getCursorRow() {
        if (mBookCursorRow == null) {
            mBookCursorRow = new BookCursorRow(this);
        }
        return mBookCursorRow;
    }

    @Override
    @CallSuper
    public void close() {
        super.close();
        mBookCursorRow = null;
    }
}
