/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.database.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;

import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;

/**
 * Cursor implementation for book-related queries.
 */
public class BookCursor
        extends TrackedCursor
        implements Closeable {

    /** Helper to fetch columns by name. */
    @Nullable
    private CursorMapper mMapper;

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
     * Convenience method.
     *
     * @return the row ID
     */
    public final long getId() {
        int col = getColumnIndex(DBDefinitions.KEY_PK_ID);
        if (col < 0) {
            throw new ColumnNotPresentException(DBDefinitions.KEY_PK_ID);
        }
        return getLong(col);
    }

    /**
     * Get a wrapper for the row data.
     *
     * @return the row object
     */
    @NonNull
    private CursorMapper getCursorMapper() {
        if (mMapper == null) {
            mMapper = new CursorMapper(this);
        }
        return mMapper;
    }

    @Override
    @CallSuper
    public void close() {
        super.close();
        mMapper = null;
    }

    public boolean contains(@NonNull final String key) {
        return getCursorMapper().contains(key);
    }

    @NonNull
    public String getString(@NonNull final String key)
            throws ColumnNotPresentException {
        return getCursorMapper().getString(key);
    }

    public boolean getBoolean(@NonNull final String key)
            throws ColumnNotPresentException {
        return getCursorMapper().getBoolean(key);
    }

    public int getInt(@NonNull final String key)
            throws ColumnNotPresentException {
        return getCursorMapper().getInt(key);
    }

    public long getLong(@NonNull final String key)
            throws ColumnNotPresentException {
        return getCursorMapper().getLong(key);
    }

    public double getDouble(@NonNull final String key)
            throws ColumnNotPresentException {
        return getCursorMapper().getDouble(key);
    }
}
