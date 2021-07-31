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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Value class to report back what was witten.
 * (this class is overkill, but we want to keep the same usage-structure as in the reader)
 */
public class SyncWriterResults
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SyncWriterResults> CREATOR = new Creator<SyncWriterResults>() {
        @Override
        public SyncWriterResults createFromParcel(@NonNull final Parcel in) {
            return new SyncWriterResults(in);
        }

        @Override
        public SyncWriterResults[] newArray(final int size) {
            return new SyncWriterResults[size];
        }
    };

    public int booksWritten;
    public int coversWritten;


    public SyncWriterResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    SyncWriterResults(@NonNull final Parcel in) {
        booksWritten = in.readInt();
        coversWritten = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksWritten);
        dest.writeInt(coversWritten);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + "booksWritten=" + booksWritten
               + ", coversWritten=" + coversWritten
               + '}';
    }
}
