/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Value class to report back what was written.
 */
public class SyncWriterResults
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SyncWriterResults> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SyncWriterResults createFromParcel(@NonNull final Parcel in) {
            return new SyncWriterResults(in);
        }

        @Override
        @NonNull
        public SyncWriterResults[] newArray(final int size) {
            return new SyncWriterResults[size];
        }
    };

    public int booksProcessed;
    public int booksUpdated;
    public int imagesUpdated;

    /**
     * Constructor.
     */
    public SyncWriterResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SyncWriterResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksUpdated = in.readInt();
        imagesUpdated = in.readInt();
    }

    public int getBooksProcessed() {
        return booksProcessed;
    }

    public int getBooksUpdated() {
        return booksUpdated;
    }

    public int getImagesUpdated() {
        return imagesUpdated;
    }

    /**
     * Create a single String line with a report how many books were create/updated/...
     *
     * @param context Current context
     *
     * @return info or {@code ""} if none found
     */
    @NonNull
    public String createBooksSummaryLine(@NonNull final Context context) {
        final StringJoiner parts = new StringJoiner(", ");
        if (booksProcessed > 0) {
            parts.add(context.getString(R.string.name_colon_value,
                                        context.getString(R.string.lbl_books),
                                        String.valueOf(booksProcessed)));
        }
        if (booksUpdated > 0) {
            parts.add(context.getString(R.string.progress_msg_x_updated, booksUpdated));
        }
        if (imagesUpdated > 0) {
            parts.add(context.getString(R.string.name_colon_value,
                                        context.getString(R.string.lbl_images),
                                        String.valueOf(imagesUpdated)));
        }

        if (parts.length() > 0) {
            return parts.toString();
        } else {
            return "";
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksUpdated);
        dest.writeInt(imagesUpdated);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncWriterResults{"
               + "booksProcessed=" + booksProcessed
               + ", booksUpdated=" + booksUpdated
               + ", imagesUpdated=" + imagesUpdated
               + '}';
    }
}
