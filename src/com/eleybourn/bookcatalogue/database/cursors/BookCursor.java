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
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;

/**
 * Cursor implementation for book-related queries providing a {@link MappedCursorRow}.
 */
public class BookCursor
        extends TrackedCursor
        implements Closeable {

    /** column position for the id column. */
    private int mIdCol = -2;

    /** Cached CursorRow for this cursor. */
    @Nullable
    private MappedCursorRow mCursorRow;

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
            mIdCol = getColumnIndex(DBDefinitions.KEY_PK_ID);
            if (mIdCol < 0) {
                throw new ColumnNotPresentException(DBDefinitions.KEY_PK_ID);
            }
        }
        return getLong(mIdCol);
    }

    /**
     * Get a wrapper for the row data.
     *
     * @return the row object
     */
    @NonNull
    public MappedCursorRow getCursorRow() {
        if (mCursorRow == null) {
            mCursorRow = new MappedCursorRow(this
            );
        }
        return mCursorRow;
    }

    @Override
    @CallSuper
    public void close() {
        super.close();
        mCursorRow = null;
    }
}
