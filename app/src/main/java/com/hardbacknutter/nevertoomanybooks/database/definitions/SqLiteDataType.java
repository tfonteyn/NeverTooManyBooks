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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * <a href="https://sqlite.org/datatype3.html#storage_classes_and_datatypes">
 * storage classes and data types</a>
 */
public enum SqLiteDataType
        implements Parcelable {

    Text("text", Cursor.FIELD_TYPE_STRING),

    /** Stores both int and long values. */
    Integer("integer", Cursor.FIELD_TYPE_INTEGER),

    /**
     * boolean is the same as Integer(storing 0,1) , but kept for clarity.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#boolean_datatype">boolean</a>
     */
    Boolean("boolean", Cursor.FIELD_TYPE_INTEGER),

    /** Stores both float and double values. */
    Real("real", Cursor.FIELD_TYPE_FLOAT),

    Blob("blob", Cursor.FIELD_TYPE_BLOB),

    /**
     * Date and datetime are kept for clarity. We always use them as {@code String}.
     * <p>
     * <a href="https://sqlite.org/datatype3.html#date_and_time_datatype">date_and_time</a>
     */
    Date("date", Cursor.FIELD_TYPE_STRING),

    /** Stored as UTC, in SQL-ISO format. */
    DateTime("datetime", Cursor.FIELD_TYPE_STRING);

    public static final Creator<SqLiteDataType> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SqLiteDataType createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        public SqLiteDataType[] newArray(final int size) {
            return new SqLiteDataType[size];
        }
    };

    private static final Map<String, SqLiteDataType> MAP = Map.ofEntries(
            Map.entry("integer", Integer),
            Map.entry("int", Integer),
            Map.entry("boolean", Boolean),
            Map.entry("text", Text),
            Map.entry("clob", Text),
            Map.entry("char", Text),
            Map.entry("varchar", Text),
            Map.entry("date", Date),
            Map.entry("datetime", DateTime),
            Map.entry("real", Real),
            Map.entry("float", Real),
            Map.entry("double", Real),
            Map.entry("blob", Blob));

    @NonNull
    private final String typeName;

    private final int cursorFieldType;

    /**
     * Constructor.
     *
     * @param typeName        the string to use when creating columns in SQL
     * @param cursorFieldType one of the Cursor.FIELD_TYPE_* constants, used when reading
     */
    SqLiteDataType(@NonNull final String typeName,
                   final int cursorFieldType) {
        this.typeName = typeName;
        this.cursorFieldType = cursorFieldType;
    }

    /**
     * <a href="https://sqlite.org/datatype3.html#determination_of_column_affinity">
     * determination of column affinity</a>
     * <p>
     * We only lookup the more common types.
     */
    @NonNull
    static SqLiteDataType getInstance(@NonNull final String typeName) {
        // We MUST use the system locale here.
        final String lcName = typeName.toLowerCase(ServiceLocator.getInstance().getSystemLocale());
        final SqLiteDataType type = MAP.get(lcName);
        if (type != null) {
            return type;
        }

        throw new IllegalArgumentException(typeName);
    }

    @NonNull
    public String getName() {
        return typeName;
    }

    public int getCursorFieldType() {
        return cursorFieldType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(ordinal());
    }

    @Override
    @NonNull
    public String toString() {
        return "SqLiteDataType{"
               + "typeName=`" + typeName + '`'
               + ", cursorFieldType=" + cursorFieldType
               + '}';
    }
}
