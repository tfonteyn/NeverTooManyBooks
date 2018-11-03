package com.eleybourn.bookcatalogue.database.definitions;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DbSync;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Details of a database table.
 *
 * @author Philip Warner
 */
public class TableInfo implements Iterable<TableInfo.ColumnInfo> {

    /** @see StorageClass */
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_REAL = "real";
    public static final String TYPE_BLOB = "blob";

    /** https://sqlite.org/datatype3.html#boolean_datatype */
    public static final String TYPE_BOOLEAN = "boolean"; // same as Integer(storing 0,1) , but kept for clarity.

    /** https://sqlite.org/datatype3.html#date_and_time_datatype */
    public static final String TYPE_DATE = "date";
    public static final String TYPE_DATETIME = "datetime";// kept for clarity.


    @NonNull
    private final Map<String, ColumnInfo> mColumns;
    @NonNull
    private final DbSync.SynchronizedDb mSyncedDb;

    public TableInfo(final @NonNull DbSync.SynchronizedDb db, final @NonNull String tableName) {
        mSyncedDb = db;
        mColumns = describeTable(tableName);
    }

    @Nullable
    public ColumnInfo getColumn(final @NonNull String name) {
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
     * @param tableName Name of the database table to lookup
     *
     * @return A collection of ColumnInfo objects.
     */
    @NonNull
    private Map<String, ColumnInfo> describeTable(final @NonNull String tableName) {
        String sql = "PRAGMA table_info(" + tableName + ")";

        Map<String, ColumnInfo> allColumns = new HashMap<>();

        try (Cursor colCsr = mSyncedDb.rawQuery(sql, new String[]{})) {
            if (!colCsr.moveToFirst()) {
                throw new IllegalStateException("Unable to get column details");
            }

            while (true) {
                ColumnInfo col = new ColumnInfo();
                col.position = colCsr.getInt(0);
                col.name = colCsr.getString(1);
                col.typeName = colCsr.getString(2);
                col.allowNull = colCsr.getInt(3) == 0;
                col.defaultValue = colCsr.getString(4);
                col.isPrimaryKey = colCsr.getInt(5) == 1;

                col.storageClass = StorageClass.newInstance(col.typeName);

                allColumns.put(col.name.toLowerCase(), col);
                if (colCsr.isLast()) {
                    break;
                }
                colCsr.moveToNext();
            }
        }
        return allColumns;
    }

    /** https://sqlite.org/datatype3.html#storage_classes_and_datatypes */
    public enum StorageClass {
        Integer, Real, Text, Blob;

        public static StorageClass newInstance(final @NonNull String columnType) {
            // hardcoded strings are for backwards compatibility
            switch (columnType.toLowerCase()) {
                case TYPE_INTEGER:
                case "int":
                    return StorageClass.Integer;
                case TYPE_TEXT:
                case "char":
                    return StorageClass.Text;
                case TYPE_REAL:
                case "float":
                case "double":
                    return StorageClass.Real;
                case TYPE_BLOB:
                    return StorageClass.Blob;

                case TYPE_BOOLEAN:
                    return StorageClass.Integer;

                case TYPE_DATE:
                case TYPE_DATETIME:
                    return StorageClass.Text;
                default:
                    throw new RTE.IllegalTypeException(columnType);
            }
        }
    }

    /**
     * Column info support. This is useful for auto-building queries from maps that have
     * more columns than are in the table.
     *
     * @author Philip Warner
     */
    public static class ColumnInfo {
        public int position;
        public String name;
        public String typeName;
        public boolean allowNull;
        public boolean isPrimaryKey;
        public String defaultValue;
        public StorageClass storageClass;
    }
}
