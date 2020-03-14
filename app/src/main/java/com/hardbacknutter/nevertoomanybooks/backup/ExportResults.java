/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Value class to report back what was exported.
 * <p>
 * These could just as well be member variables of the helper itself, but this is cleaner.
 */
public class ExportResults
        implements Parcelable {

    public static final Creator<ExportResults> CREATOR = new Creator<ExportResults>() {
        @Override
        public ExportResults createFromParcel(@NonNull final Parcel in) {
            return new ExportResults(in);
        }

        @Override
        public ExportResults[] newArray(final int size) {
            return new ExportResults[size];
        }
    };
    /** #books that did not have a front-cover [0] / back-cover [1]. */
    public int[] coversMissing;
    /** The total #books that were considered for export. */
    public int booksProcessed;
    /** #books we exported. */
    public int booksExported;
    /** #covers exported. */
    public int coversExported;
    /** #covers that were skipped. */
    public int coversSkipped;
    /** #styles we exported. */
    public int styles;

    public ExportResults() {
        coversMissing = new int[2];
    }

    private ExportResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksExported = in.readInt();
        coversExported = in.readInt();
        coversSkipped = in.readInt();
        coversMissing = in.createIntArray();
        styles = in.readInt();
    }

    public void add(@NonNull final ExportResults results) {
        booksProcessed += results.booksProcessed;
        booksExported += results.booksExported;

        coversSkipped += results.coversSkipped;
        coversExported += results.coversExported;
        coversMissing[0] += results.coversMissing[0];
        coversMissing[1] += results.coversMissing[1];

        styles += results.styles;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksExported);
        dest.writeInt(coversExported);
        dest.writeInt(coversSkipped);
        dest.writeIntArray(coversMissing);
        dest.writeInt(styles);
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
               + ", booksExported=" + booksExported

               + ", coversSkipped=" + coversSkipped
               + ", coversExported=" + coversExported
               + ", coversMissing[0]=" + coversMissing[0]
               + ", coversMissing[1]=" + coversMissing[1]

               + ", styles=" + styles
               + '}';
    }
}
