package com.eleybourn.bookcatalogue.database.cursors;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

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
     * @param cursor  the underlying cursor to use.
     * @param table   the base table to map.
     * @param columns a list of column names (e.g. from other tables)
     */
    MappedCursorRow(@NonNull final Cursor cursor,
                    @Nullable final TableDefinition table,
                    @Nullable final String... columns) {
        mCursor = cursor;
        mMapper = new ColumnMapper(cursor, table);
        mMapper.addDomains(columns);
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
    public int getPosition(){
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
