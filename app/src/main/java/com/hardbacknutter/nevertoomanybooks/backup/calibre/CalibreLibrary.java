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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class CalibreLibrary
        implements Parcelable {

    public static final Creator<CalibreLibrary> CREATOR = new Creator<CalibreLibrary>() {
        @Override
        public CalibreLibrary createFromParcel(@NonNull final Parcel in) {
            return new CalibreLibrary(in);
        }

        @Override
        public CalibreLibrary[] newArray(final int size) {
            return new CalibreLibrary[size];
        }
    };

    private final boolean mIsDefault;
    @NonNull
    private final String mId;
    @NonNull
    private final String mName;

    CalibreLibrary(@NonNull final String id,
                   @NonNull final String name,
                   final boolean isDefault) {
        this.mId = id;
        mName = name;
        this.mIsDefault = isDefault;
    }

    private CalibreLibrary(@NonNull final Parcel in) {
        mIsDefault = in.readByte() != 0;
        //noinspection ConstantConditions
        mId = in.readString();
        //noinspection ConstantConditions
        mName = in.readString();
    }

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public boolean isDefault() {
        return mIsDefault;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeByte((byte) (mIsDefault ? 1 : 0));
        dest.writeString(mId);
        dest.writeString(mName);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
