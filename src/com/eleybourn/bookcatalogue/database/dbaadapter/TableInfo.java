package com.eleybourn.bookcatalogue.database.dbaadapter;

import android.database.Cursor;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DbSync;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

/**
 * Details of a database table.
 *
 * @author Philip Warner
 */
public class TableInfo implements Iterable<ColumnInfo> {
    private final Map<String,ColumnInfo> mColumns;
    private final DbSync.SynchronizedDb mDb;

    public static final int CLASS_INTEGER = 1;
    public static final int CLASS_TEXT = 2;
    public static final int CLASS_REAL = 3;

    public TableInfo(DbSync.SynchronizedDb db, String tableName) {
        mDb = db;
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
     * @param tableName	Name of the database table to lookup
     *
     * @return	A collection of ColumnInfo objects.
     */
    private Map<String,ColumnInfo> describeTable(String tableName) {
        String sql = "PRAGMA table_info(" + tableName + ")";

        Map<String,ColumnInfo> cols = new Hashtable<>();

        Cursor colCsr = mDb.rawQuery(sql, new String[]{});
        try {
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
                    case "int":
                    case "integer":
                        col.typeClass = CLASS_INTEGER;
                        break;
                    case "text":
                        col.typeClass = CLASS_TEXT;
                        break;
                    case "float":
                    case "real":
                    case "double":
                        col.typeClass = CLASS_REAL;
                        break;
                    case "date":
                    case "datetime":
                        col.typeClass = CLASS_TEXT;
                        break;
                    case "boolean":
                        col.typeClass = CLASS_INTEGER;
                        break;
                    default:
                        throw new RuntimeException("Unknown data type '" + tName + "'");
                }

                cols.put(col.name.toLowerCase(),col);
                if (colCsr.isLast())
                    break;
                colCsr.moveToNext();
            }
        } finally {
            if (colCsr != null)
                colCsr.close();
        }
        return cols;
    }
}
