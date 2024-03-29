/*
 * @Copyright 2018-2023 HardBackNutter
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
    Unsorted("", '˗'),
    // " ASC" is the default, so don't bother adding it
    Asc("", '˄'),
    Desc(" DESC", '˅');

    /** {@link Parcelable}. */
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
    private final char symbol;

    /**
     * Constructor.
     *
     * @param expression the SQL expression to add to a column
     * @param symbol     a user-displayable symbol
     */
    Sort(@NonNull final String expression,
         final char symbol) {
        this.expression = expression;
        this.symbol = symbol;
    }

    /**
     * Cycle to the next value.
     *
     * @return the next value
     */
    @NonNull
    public Sort next() {
        int next = ordinal() + 1;
        if (next >= values().length) {
            next = 0;
        }
        return values()[next];
    }

    /**
     * Get the SQL clause.
     *
     * @return {@code ""} or {@code " DESC"}
     */
    @NonNull
    public String getExpression() {
        return expression;
    }

    public char getSymbol() {
        return symbol;
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
