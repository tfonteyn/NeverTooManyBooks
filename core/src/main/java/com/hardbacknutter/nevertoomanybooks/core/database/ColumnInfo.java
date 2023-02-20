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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ColumnInfo {

    private final int position;
    @NonNull
    private final String name;
    @NonNull
    private final SqLiteDataType sqLiteDataType;
    private final boolean nullable;
    @Nullable
    private final String defaultValue;
    private final boolean primaryKey;

    /**
     * Constructor.
     *
     * @param cursor with the column information details.
     */
    ColumnInfo(@NonNull final Cursor cursor) {
        position = cursor.getInt(0);
        name = cursor.getString(1);
        sqLiteDataType = SqLiteDataType.getInstance(cursor.getString(2));
        nullable = cursor.getInt(3) == 0;
        // can be null
        defaultValue = cursor.getString(4);
        primaryKey = cursor.getInt(5) == 1;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public int getCursorFieldType() {
        return sqLiteDataType.getCursorFieldType();
    }

    @Override
    @NonNull
    public String toString() {
        return "\nColumnInfo{"
               + "position=" + position
               + ", name=`" + name + '`'
               + ", sqLiteDataType=" + sqLiteDataType
               + ", nullable=" + nullable
               + ", defaultValue=`" + defaultValue + '`'
               + ", primaryKey=" + primaryKey
               + '}';
    }

}
