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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class EditBookOutput
        implements Parcelable {

    /** tag used when parceling. */
    public static final String BKEY = "EditBookOutput";

    public static final Creator<EditBookOutput> CREATOR = new Creator<>() {
        @Override
        public EditBookOutput createFromParcel(@NonNull final Parcel in) {
            return new EditBookOutput(in);
        }

        @Override
        public EditBookOutput[] newArray(final int size) {
            return new EditBookOutput[size];
        }
    };

    /** The book id handled. This normally means the book that BoB should center on. */
    public final long bookId;

    /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
    public final boolean modified;

    private EditBookOutput(final long bookId,
                           final boolean modified) {
        this.bookId = bookId;
        this.modified = modified;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private EditBookOutput(@NonNull final Parcel in) {
        bookId = in.readLong();
        modified = in.readByte() != 0;
    }

    @NonNull
    public static Intent createResultIntent(final long bookId,
                                            final boolean modified) {
        final Parcelable output = new EditBookOutput(bookId, modified);
        return new Intent().putExtra(BKEY, output);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(bookId);
        dest.writeByte((byte) (modified ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
