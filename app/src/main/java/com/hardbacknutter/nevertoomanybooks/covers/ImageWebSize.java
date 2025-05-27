/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;

/**
 * Sizes of images downloaded by {@link SearchEngine} implementations.
 * These are open to interpretation (or not used at all) by individual sites.
 * <p>
 * The order must be from Small to Large so we can use {@link Enum#compareTo(Enum)}.
 */
public enum ImageWebSize
        implements Parcelable {
    Small,
    Medium,
    Large;

    /** {@link Parcelable}. */
    public static final Creator<ImageWebSize> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ImageWebSize createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public ImageWebSize[] newArray(final int size) {
            return new ImageWebSize[size];
        }
    };

    static final ImageWebSize[] SMALL_FIRST = {Small, Medium, Large};
    static final ImageWebSize[] LARGE_FIRST = {Large, Medium, Small};

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
