/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public enum EditAction
        implements Parcelable {
    /**
     * Add a new item. New items <strong>are NOT</strong> stored in the database.
     * Return the new item.
     */
    Add,
    /**
     * Edit an item. Modifications <strong>are NOT</strong> stored in the database.
     * Return the original item + a separate copied/modified item.
     */
    Edit,
    /**
     * Edit an item in-place. Modifications <strong>ARE</strong> stored in the database.
     * Returns the modified item.
     */
    EditInPlace;

    public static final String BKEY = "EditAction";

    /** {@link Parcelable}. */
    public static final Creator<EditAction> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public EditAction createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public EditAction[] newArray(final int size) {
            return new EditAction[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(this.ordinal());
    }
}
