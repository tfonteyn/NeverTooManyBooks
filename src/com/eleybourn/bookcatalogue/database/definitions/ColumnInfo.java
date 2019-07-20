package com.eleybourn.bookcatalogue.database.definitions;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Column info support. This is useful for auto-building queries from maps that have
 * more columns than are in the table.
 */
public class ColumnInfo {

    /**
     * 'actual' types in the database.
     *
     * @see StorageClass
     */
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_REAL = "real";
    public static final String TYPE_BLOB = "blob";
    /**
     * boolean is the same as Integer(storing 0,1) , but kept for clarity.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#boolean_datatype">
     *     https://sqlite.org/datatype3.html#boolean_datatype</a>
     */
    public static final String TYPE_BOOLEAN = "boolean";
    /**
     * Date and datetime are kept for clarity.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#date_and_time_datatype">
     *     https://sqlite.org/datatype3.html#date_and_time_datatype</a>
     */
    public static final String TYPE_DATE = "date";
    public static final String TYPE_DATETIME = "datetime";

    @NonNull
    public final String name;
    @NonNull
    private final String typeName;
    public final boolean isPrimaryKey;
    @NonNull
    public final StorageClass storageClass;

    @SuppressWarnings("unused")
    private final int position;
    @SuppressWarnings("unused")
    private final boolean allowNull;
    @SuppressWarnings("unused")
    @Nullable
    private final String defaultValue;


    /**
     * Constructor.
     *
     * @param cursor with the column information details.
     */
    ColumnInfo(@NonNull final Cursor cursor) {
        position = cursor.getInt(0);
        name = cursor.getString(1);
        typeName = cursor.getString(2);
        allowNull = cursor.getInt(3) == 0;

        // can be null
        defaultValue = cursor.getString(4);

        isPrimaryKey = cursor.getInt(5) == 1;

        // derived
        storageClass = StorageClass.newInstance(typeName);
    }

    /**
     * @param tableName to get
     *
     * @return the sql to create the cursor that this class represents.
     */
    static String getSql(@NonNull final String tableName) {
        return "PRAGMA table_info(" + tableName + ')';
    }

    @Override
    @NonNull
    public String toString() {
        return "\nColumnInfo{"
                + "name=`" + name + '`'
                + ", isPrimaryKey=" + isPrimaryKey
                + ", storageClass=" + storageClass
                + ", position=" + position
                + ", typeName=`" + typeName + '`'
                + ", allowNull=" + allowNull
                + ", defaultValue=`" + defaultValue + '`'
                + '}';
    }

    /**
     * Mapping types to storage classes.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#storage_classes_and_datatypes">
     *     https://sqlite.org/datatype3.html#storage_classes_and_datatypes</a>
     */
    public enum StorageClass {
        Integer, Real, Text, Blob;

        static StorageClass newInstance(@NonNull final String columnType) {
            // hardcoded strings are for backwards compatibility
            switch (columnType.toLowerCase(LocaleUtils.getSystemLocale())) {
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
                    // note that "" (empty) type is treated as TEXT.
                    // But we really should not allow our columns to be defined without a type.
                    throw new IllegalTypeException("columnType=`" + columnType + '`');
            }
        }
    }
}
