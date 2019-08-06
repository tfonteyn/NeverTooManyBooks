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

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Convenience class to pre-map the columns of a given table.
 */
public class MappedCursorRow {

    /** Associated cursor object. */
    @NonNull
    private final Cursor mCursor;
    /** The mapper helper. */
    @NonNull
    private final ColumnMapper mMapper;

    /**
     * Constructor.
     *
     * @param cursor the underlying cursor to use.
     */
    MappedCursorRow(@NonNull final Cursor cursor) {
        mCursor = cursor;
        mMapper = new ColumnMapper(cursor);
    }

    /**
     * Direct access to the cursor.
     *
     * @return the number of rows in the cursor.
     */
    public int getCount() {
        return mCursor.getCount();
    }

    /**
     * Direct access to the cursor.
     *
     * @return the position in the cursor.
     */
    public int getPosition() {
        return mCursor.getPosition();
    }

    /**
     * Direct access to the cursor.
     *
     * @param columnName to get
     *
     * @return the column index.
     */
    public int getColumnIndex(@NonNull final String columnName) {
        return mCursor.getColumnIndex(columnName);
    }

    /**
     * Direct access to the cursor.
     *
     * @param columnIndex to get
     *
     * @return a string from underlying cursor
     */
    @Nullable
    public String getString(final int columnIndex) {
        return mCursor.getString(columnIndex);
    }

    /**
     * Check if we have the given column available.
     *
     * @param columnName to check
     *
     * @return {@code true} if this column is present.
     */
    public boolean contains(@NonNull final String columnName) {
        return mMapper.contains(columnName);
    }

    /**
     * @param columnName to get
     *
     * @return a string from underlying mapper
     */
    @NonNull
    public String getString(@NonNull final String columnName) {
        return mMapper.getString(columnName);
    }

    /**
     * @param columnName to get
     *
     * @return a boolean from underlying mapper.
     */
    public boolean getBoolean(@NonNull final String columnName) {
        return mMapper.getBoolean(columnName);
    }

    /**
     * @param columnName to get
     *
     * @return an int from underlying mapper
     */
    public final int getInt(@NonNull final String columnName) {
        return mMapper.getInt(columnName);
    }

    /**
     * @param columnName to get
     *
     * @return a long from underlying mapper
     */
    public final long getLong(@NonNull final String columnName) {
        return mMapper.getLong(columnName);
    }

    /**
     * @param columnName to get
     *
     * @return a double from underlying mapper
     */
    public final double getDouble(@NonNull final String columnName) {
        return mMapper.getDouble(columnName);
    }
}
