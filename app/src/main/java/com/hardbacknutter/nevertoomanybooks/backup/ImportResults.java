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

import java.util.ArrayList;

/**
 * Value class to report back what was imported.
 * <p>
 * Note: failed = processed - created - updated
 */
public class ImportResults
        implements Parcelable {

    public static final Creator<ImportResults> CREATOR = new Creator<ImportResults>() {
        @Override
        public ImportResults createFromParcel(@NonNull final Parcel in) {
            return new ImportResults(in);
        }

        @Override
        public ImportResults[] newArray(final int size) {
            return new ImportResults[size];
        }
    };
    /** Keeps track of failed import lines in the CSV file. */
    public final ArrayList<Integer> failedCsvLinesNr = new ArrayList<>();
    public final ArrayList<String> failedCsvLinesMessage = new ArrayList<>();
    /** The total #books that were present in the import data. */
    public int booksProcessed;
    /** #books we created. */
    public int booksCreated;
    /** #books we updated. */
    public int booksUpdated;
    /** The total #covers that were present in the import data. */
    public int coversProcessed;
    /** #covers we created. */
    public int coversCreated;
    /** #covers we updated. */
    public int coversUpdated;
    /** #styles we imported. */
    public int styles;

    public ImportResults() {
    }

    private ImportResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksCreated = in.readInt();
        booksUpdated = in.readInt();
        coversProcessed = in.readInt();
        coversCreated = in.readInt();
        coversUpdated = in.readInt();
        styles = in.readInt();

        in.readList(failedCsvLinesNr, getClass().getClassLoader());
        in.readList(failedCsvLinesMessage, getClass().getClassLoader());
    }

    public void add(@NonNull final ImportResults results) {
        booksProcessed += results.booksProcessed;
        booksCreated += results.booksCreated;
        booksUpdated += results.booksUpdated;

        coversProcessed += results.coversProcessed;
        coversCreated += results.coversCreated;
        coversUpdated += results.coversUpdated;

        styles += results.styles;

        failedCsvLinesNr.addAll(results.failedCsvLinesNr);
        failedCsvLinesMessage.addAll(results.failedCsvLinesMessage);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksCreated);
        dest.writeInt(booksUpdated);
        dest.writeInt(coversProcessed);
        dest.writeInt(coversCreated);
        dest.writeInt(coversUpdated);
        dest.writeInt(styles);

        dest.writeList(failedCsvLinesNr);
        dest.writeList(failedCsvLinesMessage);
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

               + ", coversSkipped=" + coversProcessed
               + ", coversCreated=" + coversCreated
               + ", coversUpdated=" + coversUpdated

               + ", styles=" + styles

               + ", failedCsvLinesNr=" + failedCsvLinesNr
               + ", failedCsvLinesMessage=" + failedCsvLinesMessage
               + '}';
    }
}
