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

    public enum TypeClass {Integer, Text, Real}

    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_BLOB = "blob";
    public static final String TYPE_CHAR = "char";
    public static final String TYPE_DATE = "date";
    public static final String TYPE_DATETIME = "datetime";
    public static final String TYPE_FLOAT = "float";
    public static final String TYPE_INT = "int";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_TEXT = "text";

    @NonNull
    private final Map<String, ColumnInfo> mColumns;
    @NonNull
    private final DbSync.SynchronizedDb mSyncedDb;

    public TableInfo(@NonNull final DbSync.SynchronizedDb db, @NonNull final String tableName) {
        mSyncedDb = db;
        mColumns = describeTable(tableName);
    }

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
     * @param tableName Name of the database table to lookup
     *
     * @return A collection of ColumnInfo objects.
     */
    @NonNull
    private Map<String, ColumnInfo> describeTable(@NonNull final String tableName) {
        String sql = "PRAGMA table_info(" + tableName + ")";

        Map<String, ColumnInfo> allColumns = new HashMap<>();

        try (Cursor colCsr = mSyncedDb.rawQuery(sql, new String[]{})) {
            if (!colCsr.moveToFirst()) {
                throw new RuntimeException("Unable to get column details");
            }

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
                        col.typeClass = TypeClass.Integer;
                        break;
                    case TYPE_TEXT:
                    case TYPE_CHAR:
                        col.typeClass = TypeClass.Text;
                        break;
                    case TYPE_FLOAT:
                    case "real":
                    case "double":
                        col.typeClass = TypeClass.Real;
                        break;
                    case TYPE_DATE:
                    case "datetime":
                        col.typeClass = TypeClass.Text;
                        break;
                    case TYPE_BOOLEAN:
                        col.typeClass = TypeClass.Integer;
                        break;
                    default:
                        throw new RTE.IllegalTypeException(tName);
                }

                allColumns.put(col.name.toLowerCase(), col);
                if (colCsr.isLast()) {
                    break;
                }
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
        public int position;
        public String name;
        public String typeName;
        public boolean allowNull;
        public boolean isPrimaryKey;
        public String defaultValue;
        public TypeClass typeClass;
    }
}
