package com.eleybourn.bookcatalogue.database.definitions;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Details of a database table.
 *
 * @author Philip Warner
 */
public class TableInfo {

    /** columns of this table. */
    @NonNull
    private final Map<String, ColumnInfo> mColumns;
    /** only stored for debug purposes. */
    @NonNull
    private final String mTableName;

    /**
     * Constructor.
     *
     * @param db        the database
     * @param tableName name of table
     */
    public TableInfo(@NonNull final SynchronizedDb db,
                     @NonNull final String tableName) {
        mTableName = tableName;
        mColumns = describeTable(db, tableName);
    }

    /**
     * Get the information on all columns.
     *
     * @return the collection of column information
     */
    @NonNull
    public Collection<ColumnInfo> getColumns() {
        return mColumns.values();
    }

    /**
     * Get the information about a specific column.
     *
     * @param name of column
     *
     * @return the info, or {@code null} if not found
     */
    @Nullable
    public ColumnInfo getColumn(@NonNull final String name) {
        String lcName = name.toLowerCase(LocaleUtils.getSystemLocale());
        if (!mColumns.containsKey(lcName)) {
            return null;
        }
        return mColumns.get(lcName);
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
                allColumns.put(col.name.toLowerCase(LocaleUtils.getSystemLocale()), col);
            }
        }
        if (allColumns.isEmpty()) {
            throw new IllegalStateException("Unable to get column details");
        }
        return allColumns;
    }

    @Override
    @NonNull
    public String toString() {
        return "TableInfo{"
                + "mTableName=" + mTableName
                + "mColumns=" + mColumns.values()
                + '}';
    }
}
