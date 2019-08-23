/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * Column info support. This is useful for auto-building queries from maps that have
 * more columns than are in the table.
 */
public class ColumnInfo {

    /**
     * Actual types in the database.
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
     * https://sqlite.org/datatype3.html#boolean_datatype</a>
     */
    public static final String TYPE_BOOLEAN = "boolean";
    /**
     * Date and datetime are kept for clarity.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#date_and_time_datatype">
     * https://sqlite.org/datatype3.html#date_and_time_datatype</a>
     */
    public static final String TYPE_DATE = "date";
    public static final String TYPE_DATETIME = "datetime";

    @NonNull
    public final String name;
    public final boolean isPrimaryKey;
    @NonNull
    public final StorageClass storageClass;
    @NonNull
    private final String typeName;
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
     * https://sqlite.org/datatype3.html#storage_classes_and_datatypes</a>
     */
    public enum StorageClass {
        Integer, Real, Text, Blob;

        static StorageClass newInstance(@NonNull final String columnType) {
            // hardcoded strings are for backwards compatibility
            switch (columnType.toLowerCase(App.getSystemLocale())) {
                case TYPE_INTEGER:
                case "int":
                case TYPE_BOOLEAN:
                    return StorageClass.Integer;

                case TYPE_TEXT:
                case "char":
                case TYPE_DATE:
                case TYPE_DATETIME:
                    return StorageClass.Text;

                case TYPE_REAL:
                case "float":
                case "double":
                    return StorageClass.Real;

                case TYPE_BLOB:
                    return StorageClass.Blob;

                default:
                    // note that "" (empty) type is treated as TEXT.
                    // But we really should not allow our columns to be defined without a type.
                    throw new IllegalStateException("columnType=`" + columnType + '`');
            }
        }
    }
}
