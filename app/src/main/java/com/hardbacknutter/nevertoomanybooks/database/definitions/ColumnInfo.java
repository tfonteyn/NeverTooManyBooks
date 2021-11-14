/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

public class ColumnInfo {

    /*
     * Actual types in the database.
     *
     * @see StorageClass
     */

    /** Stores both int and long values. */
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_TEXT = "text";
    /** Stores both float and double values. */
    public static final String TYPE_REAL = "real";
    public static final String TYPE_BLOB = "blob";

    /**
     * boolean is the same as Integer(storing 0,1) , but kept for clarity.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#boolean_datatype">boolean</a>
     */
    public static final String TYPE_BOOLEAN = "boolean";
    /**
     * Date and datetime are kept for clarity.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#date_and_time_datatype">date_and_time</a>
     * <p>
     * According to:
     * <a href="https://sqlite.org/datatype3.html#affinity_name_examples">affinity</a>
     * dates actually have an affinity with INT.
     * As we always use them as TEXT, {@link StorageClass}
     * puts them in the {@link StorageClass#Text} bucket.
     */
    public static final String TYPE_DATE = "date";
    /** Stored as UTC, in SQL-ISO format. */
    public static final String TYPE_DATETIME = "datetime";


    @NonNull
    public final String name;
    @NonNull
    public final StorageClass storageClass;
    private final boolean mIsPrimaryKey;
    private final boolean mNullable;
    @NonNull
    private final String typeName;
    @SuppressWarnings("unused")
    private final int position;
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
        mNullable = cursor.getInt(3) == 0;

        // can be null
        defaultValue = cursor.getString(4);

        mIsPrimaryKey = cursor.getInt(5) == 1;

        // derived
        storageClass = StorageClass.newInstance(typeName);
    }

    public boolean isNullable() {
        return mNullable;
    }

    public boolean isPrimaryKey() {
        return mIsPrimaryKey;
    }

    /**
     * Returns the type of the field for this column.
     * <p>
     * The returned field types are:
     * <ul>
     *      <li>{@link Cursor#FIELD_TYPE_INTEGER}</li>
     *      <li>{@link Cursor#FIELD_TYPE_FLOAT}</li>
     *      <li>{@link Cursor#FIELD_TYPE_STRING}</li>
     *      <li>{@link Cursor#FIELD_TYPE_BLOB}</li>
     * </ul>
     * <p>
     * If for whatever reason the type is not recognised,
     * {@link Cursor#FIELD_TYPE_STRING} is returned.
     *
     * @return The field type.
     */
    public int getType() {
        switch (storageClass) {
            case Integer:
                return Cursor.FIELD_TYPE_INTEGER;
            case Real:
                return Cursor.FIELD_TYPE_FLOAT;
            case Blob:
                return Cursor.FIELD_TYPE_BLOB;

            case Text:
            default:
                return Cursor.FIELD_TYPE_STRING;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "\nColumnInfo{"
               + "name=`" + name + '`'
               + ", mIsPrimaryKey=" + mIsPrimaryKey
               + ", storageClass=" + storageClass
               + ", position=" + position
               + ", typeName=`" + typeName + '`'
               + ", mNullable=" + mNullable
               + ", defaultValue=`" + defaultValue + '`'
               + '}';
    }

    /**
     * Mapping types to storage classes.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#storage_classes_and_datatypes">
     * storage classes and data types</a>
     */
    public enum StorageClass {
        Integer, Real, Text, Blob;

        static StorageClass newInstance(@NonNull final String columnType) {
            // Hardcoded strings are for backwards compatibility.
            // We MUST use the system locale here.
            switch (columnType.toLowerCase(ServiceLocator.getSystemLocale())) {
                case TYPE_INTEGER:
                case "int":
                case TYPE_BOOLEAN:
                    return Integer;

                case TYPE_TEXT:
                case "char":
                    // see definitions of TYPE_DATE/TYPE_DATETIME
                case TYPE_DATE:
                case TYPE_DATETIME:
                    return Text;

                case TYPE_REAL:
                case "float":
                case "double":
                    return Real;

                case TYPE_BLOB:
                    return Blob;

                default:
                    // note that "" (empty) type is treated as TEXT.
                    // But we really should not allow our columns to be defined without a type.
                    throw new IllegalArgumentException(columnType);
            }
        }
    }

    @StringDef({TYPE_INTEGER, TYPE_TEXT, TYPE_REAL, TYPE_BLOB,
                TYPE_BOOLEAN,
                TYPE_DATE, TYPE_DATETIME})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }
}
