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

package com.hardbacknutter.nevertoomanybooks.search;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public enum ScanMode
        implements Parcelable {

    /** Scanner is (set) offline. */
    Off,
    /** Scan, search and edit the found book. */
    Single,
    /** Same as 'Single' but start the scanner again after editing finished. */
    Continuous,
    /** Scan and queue the code, until scanning is cancelled. */
    Batch;

    /** {@link Parcelable}. */
    public static final Creator<ScanMode> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ScanMode createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public ScanMode[] newArray(final int size) {
            return new ScanMode[size];
        }
    };

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
