package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Details of a database table.
 *
 * This used to be an inner class of {@link CatalogueDBAdapter}
 * Externalised ONLY because of the size of the class
 * ONLY used by {@link CatalogueDBAdapter} and {@link DatabaseHelper}
 *
 * @author Philip Warner
 */
public class TableInfo implements Iterable<TableInfo.ColumnInfo> {

    public static final int CLASS_INTEGER = 1;
    public static final int CLASS_TEXT = 2;
    public static final int CLASS_REAL = 3;

    static final String TYPE_INT = "int";
    static final String TYPE_INTEGER = "integer";
    public static final String TYPE_TEXT = "text";
    static final String TYPE_DATE = "date";
    static final String TYPE_FLOAT = "float";
    static final String TYPE_BLOB = "blob";
    static final String TYPE_BOOLEAN = "boolean";

    private final Map<String, ColumnInfo> mColumns;
    private final DbSync.SynchronizedDb mSyncedDb;

    TableInfo(DbSync.SynchronizedDb db, String tableName) {
        mSyncedDb = db;
        mColumns = describeTable(tableName);
    }

    public ColumnInfo getColumn(String name) {
        String lcName = name.toLowerCase();
        if (!mColumns.containsKey(lcName))
            return null;
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
    private Map<String, ColumnInfo> describeTable(@NonNull String tableName) {
        String sql = "PRAGMA table_info(" + tableName + ")";

        Map<String, ColumnInfo> allColumns = new HashMap<>();

        try (Cursor colCsr = mSyncedDb.rawQuery(sql, new String[]{})) {
            if (colCsr == null)
                throw new IllegalArgumentException();

            if (!colCsr.moveToFirst())
                throw new RuntimeException("Unable to get column details");

            while (true) {
                ColumnInfo col = new ColumnInfo();
                col.position = colCsr.getInt(0);
                col.name = colCsr.getString(1);
                col.typeName = colCsr.getString(2);
                col.allowNull = colCsr.getInt(3) == 0;
                col.defaultValue = colCsr.getString(4);
                col.isPrimaryKey = colCsr.getInt(5) == 1;
                String tName = col.typeName.toLowerCase();
                switch (tName) {
                    case TYPE_INT:
                    case TYPE_INTEGER:
                        col.typeClass = CLASS_INTEGER;
                        break;
                    case TYPE_TEXT:
                        col.typeClass = CLASS_TEXT;
                        break;
                    case TYPE_FLOAT:
                    case "real":
                    case "double":
                        col.typeClass = CLASS_REAL;
                        break;
                    case TYPE_DATE:
                    case "datetime":
                        col.typeClass = CLASS_TEXT;
                        break;
                    case TYPE_BOOLEAN:
                        col.typeClass = CLASS_INTEGER;
                        break;
                    default:
                        throw new RuntimeException("Unknown data type '" + tName + "'");
                }

                allColumns.put(col.name.toLowerCase(), col);
                if (colCsr.isLast())
                    break;
                colCsr.moveToNext();
            }
        }
        return allColumns;
    }


    /**
     * Column info support. This is useful for auto-building queries from maps that have
     * more columns than are in the table.
     *
     * @author Philip Warner
     */
    public static class ColumnInfo {
        /** bit flags, used for {@link DatabaseDefinitions#DOM_ANTHOLOGY_MASK} */
        public static final int ANTHOLOGY_NO = 0;
        public static final int ANTHOLOGY_IS_ANTHOLOGY = 1;
        public static final int ANTHOLOGY_MULTIPLE_AUTHORS = 2;

        public int position;
        public String name;
        public String typeName;
        public boolean allowNull;
        public boolean isPrimaryKey;
        public String defaultValue;
        public int typeClass;
    }
}
