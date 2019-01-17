package com.eleybourn.bookcatalogue.database.definitions;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Details of a database table.
 *
 * @author Philip Warner
 */
public class TableInfo
        implements Iterable<ColumnInfo> {


    /** columns of this table. */
    @NonNull
    private final Map<String, ColumnInfo> mColumns;

    /**
     * Constructor.
     *
     * @param db        the database
     * @param tableName name of table
     */
    public TableInfo(@NonNull final SynchronizedDb db,
                     @NonNull final String tableName) {
        mColumns = describeTable(db, tableName);
    }

    /**
     * Get the information about a column.
     *
     * @param name of column
     *
     * @return the info, or null if the column is not present
     */
    @Nullable
    public ColumnInfo getColumn(@NonNull final String name) {
        String lcName = name.toLowerCase();
        if (!mColumns.containsKey(lcName)) {
            return null;
        }
        return mColumns.get(lcName);
    }

    @NonNull
    @Override
    public Iterator<ColumnInfo> iterator() {
        return mColumns.values().iterator();
    }

    /**
     * Get the column details for the given table.
     *
     * @param db        the database
     * @param tableName Name of the database table to lookup
     *
     * @return A collection of ColumnInfo objects.
     */
    @NonNull
    private Map<String, ColumnInfo> describeTable(@NonNull final SynchronizedDb db,
                                                  @NonNull final String tableName) {
        Map<String, ColumnInfo> allColumns = new HashMap<>();

        try (Cursor colCsr = db.rawQuery(ColumnInfo.getSql(tableName), null)) {
            while (colCsr.moveToNext()) {
                ColumnInfo col = new ColumnInfo(colCsr);
                allColumns.put(col.name.toLowerCase(), col);
            }
        }
        if (allColumns.isEmpty()) {
            throw new IllegalStateException("Unable to get column details");
        }
        return allColumns;
    }
}
