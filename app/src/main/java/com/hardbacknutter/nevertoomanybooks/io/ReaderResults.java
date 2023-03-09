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
package com.hardbacknutter.nevertoomanybooks.io;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Value class to report back what was read/imported.
 * Used by both backup and sync packages.
 * <p>
 * Backup import classes extend this class.
 * Sync import uses this class directly.
 */
public class ReaderResults
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ReaderResults> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ReaderResults createFromParcel(@NonNull final Parcel in) {
            return new ReaderResults(in);
        }

        @Override
        @NonNull
        public ReaderResults[] newArray(final int size) {
            return new ReaderResults[size];
        }
    };

    /** The total #books that were present in the import data. */
    public int booksProcessed;
    /** #books we created. */
    public int booksCreated;
    /** #books we updated. */
    public int booksUpdated;
    /** #books we skipped for NON-failure reasons. */
    public int booksSkipped;
    /** #books which explicitly failed. */
    public int booksFailed;

    /** The total #covers that were present in the import data. */
    public int coversProcessed;
    /** #covers we created. */
    public int coversCreated;
    /** #covers we updated. */
    public int coversUpdated;
    /** #covers we skipped for NON-failure reasons. */
    public int coversSkipped;
    /** # covers which explicitly failed. */
    public int coversFailed;

    /**
     * Constructor.
     */
    public ReaderResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    public ReaderResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksCreated = in.readInt();
        booksUpdated = in.readInt();
        booksSkipped = in.readInt();
        booksFailed = in.readInt();

        coversProcessed = in.readInt();
        coversCreated = in.readInt();
        coversUpdated = in.readInt();
        coversSkipped = in.readInt();
        coversFailed = in.readInt();
    }

    /**
     * Accumulate the results.
     *
     * @param results to add
     */
    public void add(@NonNull final ReaderResults results) {
        booksProcessed += results.booksProcessed;
        booksCreated += results.booksCreated;
        booksUpdated += results.booksUpdated;
        booksSkipped += results.booksSkipped;
        booksFailed += results.booksFailed;

        coversProcessed += results.coversProcessed;
        coversCreated += results.coversCreated;
        coversUpdated += results.coversUpdated;
        coversSkipped += results.coversSkipped;
        coversFailed += results.coversFailed;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksCreated);
        dest.writeInt(booksUpdated);
        dest.writeInt(booksSkipped);
        dest.writeInt(booksFailed);

        dest.writeInt(coversProcessed);
        dest.writeInt(coversCreated);
        dest.writeInt(coversUpdated);
        dest.writeInt(coversSkipped);
        dest.writeInt(coversFailed);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + "booksProcessed=" + booksProcessed
               + ", booksCreated=" + booksCreated
               + ", booksUpdated=" + booksUpdated
               + ", booksSkipped=" + booksSkipped
               + ", booksFailed=" + booksFailed

               + ", coversProcessed=" + coversProcessed
               + ", coversCreated=" + coversCreated
               + ", coversUpdated=" + coversUpdated
               + ", coversSkipped=" + coversSkipped
               + ", coversFailed=" + coversFailed
               + '}';
    }
}
