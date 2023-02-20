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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public enum Sort
        implements Parcelable {
    Unsorted(""),
    // " ASC" is the default, so don't bother adding it
    Asc(""),
    Desc(" DESC");

    public static final Creator<Sort> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Sort createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        public Sort[] newArray(final int size) {
            return new Sort[size];
        }
    };
    @NonNull
    private final String expression;

    Sort(@NonNull final String expression) {
        this.expression = expression;
    }

    @NonNull
    public String getExpression() {
        return expression;
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
}
