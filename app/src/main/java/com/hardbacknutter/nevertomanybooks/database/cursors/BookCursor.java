/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.database.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;

import com.hardbacknutter.nevertomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.dbsync.Synchronizer;

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
