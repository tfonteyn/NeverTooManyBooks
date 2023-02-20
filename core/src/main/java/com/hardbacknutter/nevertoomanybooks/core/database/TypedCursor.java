/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Wrapper for {@link SynchronizedCursor}.
 * <p>
 * Adds <strong>real type</strong> identification.
 * <p>
 * {@link Cursor#getType} will return the type
 * <strong>based on the data currently in the column</strong>. This is not always the type of
 * the column as defined at database creation time.<br>
 * Example: a 'date' column with content "1980" will report {@link Cursor#FIELD_TYPE_INTEGER}.
 * <p>
 * To enforce using the actual type, call {@link #setDb(SynchronizedDb, TableDefinition)} before
 * accessing anything.<br>
 * <strong>IMPORTANT:</strong> only gets the types from the table passed to {@link #setDb}
 */
public class TypedCursor
        extends SynchronizedCursor {

    /** database reference so we can get table/column info. */
    @Nullable
    private SynchronizedDb db;
    /** The primary table. */
    @Nullable
    private TableDefinition tableDefinition;
    /** Populated on first use. */
    @Nullable
    private TableInfo tableInfo;

    /**
     * Constructor.
     *
     * @param driver    Part of standard cursor constructor.
     * @param editTable Part of standard cursor constructor.
     * @param query     Part of standard cursor constructor.
     * @param sync      Synchronizer object
     */
    TypedCursor(@NonNull final SQLiteCursorDriver driver,
                @NonNull final String editTable,
                @NonNull final SQLiteQuery query,
                @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);
    }

    /**
     * See class docs.
     *
     * @param db              Database Access
     * @param tableDefinition to read types from
     */
    public void setDb(@NonNull final SynchronizedDb db,
                      @SuppressWarnings("SameParameterValue")
                      @NonNull final TableDefinition tableDefinition) {
        this.db = db;
        this.tableDefinition = tableDefinition;
    }

    /**
     * See class docs.
     *
     * <br><br>{@inheritDoc}
     */
    @SuppressLint("WrongConstant")
    @Override
    public int getType(final int columnIndex) {
        // initialise once.
        if (db != null && tableDefinition != null && tableInfo == null) {
            tableInfo = db.getTableInfo(tableDefinition);
        }

        if (tableInfo != null) {
            final ColumnInfo columnInfo = tableInfo.getColumn(getColumnName(columnIndex));
            if (columnInfo != null) {
                return columnInfo.getCursorFieldType();
            }
        }

        // no db/table was set, something went wrong, or it was not in our own table.
        // Use the default call.
        return super.getType(columnIndex);
    }
}
