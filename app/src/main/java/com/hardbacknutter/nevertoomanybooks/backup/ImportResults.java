/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordReader;

/**
 * Value class to report back what was imported.
 * <p>
 * Used by {@link RecordReader} and accumulated in {@link ArchiveReader}.
 * <p>
 * Note: failed = processed - created - updated
 */
public class ImportResults
        implements Parcelable {

    /** {@link Parcelable}. */
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
    /** Log tag. */
    private static final String TAG = "ImportResults";
    /**
     * {@link ImportResults} after an import.
     */
    public static final String BKEY_IMPORT_RESULTS = TAG + ":results";
    /**
     * Keeps track of failed import lines in a text file.
     * Not strictly needed as row number should be part of the messages.
     * Keeping for possible future enhancements.
     */
    public final List<Integer> failedLinesNr = new ArrayList<>();
    /** Keeps track of failed import lines in a text file. */
    public final List<String> failedLinesMessage = new ArrayList<>();

    /** The total #books that were present in the import data. */
    public int booksProcessed;
    /** #books we created. */
    public int booksCreated;
    /** #books we updated. */
    public int booksUpdated;
    /** #books we skipped. (yes, we could use failedLinesNr.size()) */
    public int booksSkipped;

    /** The total #covers that were present in the import data. */
    public int coversProcessed;
    /** #covers we created. */
    public int coversCreated;
    /** #covers we updated. */
    public int coversUpdated;
    /** #covers we skipped. */
    public int coversSkipped;

    /** #styles we imported. */
    public int styles;
    /** #preferences we imported. */
    public int preferences;

    public ImportResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksCreated = in.readInt();
        booksUpdated = in.readInt();
        booksSkipped = in.readInt();

        coversProcessed = in.readInt();
        coversCreated = in.readInt();
        coversUpdated = in.readInt();
        coversSkipped = in.readInt();

        styles = in.readInt();
        preferences = in.readInt();

        in.readList(failedLinesNr, getClass().getClassLoader());
        in.readList(failedLinesMessage, getClass().getClassLoader());
    }

    public void add(@NonNull final ImportResults results) {
        booksProcessed += results.booksProcessed;
        booksCreated += results.booksCreated;
        booksUpdated += results.booksUpdated;
        booksSkipped += results.booksSkipped;

        coversProcessed += results.coversProcessed;
        coversCreated += results.coversCreated;
        coversUpdated += results.coversUpdated;
        coversSkipped += results.coversSkipped;

        styles += results.styles;
        preferences += results.preferences;

        failedLinesNr.addAll(results.failedLinesNr);
        failedLinesMessage.addAll(results.failedLinesMessage);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksCreated);
        dest.writeInt(booksUpdated);
        dest.writeInt(booksSkipped);

        dest.writeInt(coversProcessed);
        dest.writeInt(coversCreated);
        dest.writeInt(coversUpdated);
        dest.writeInt(coversSkipped);

        dest.writeInt(styles);
        dest.writeInt(preferences);

        dest.writeList(failedLinesNr);
        dest.writeList(failedLinesMessage);
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

               + ", coversProcessed=" + coversProcessed
               + ", coversCreated=" + coversCreated
               + ", coversUpdated=" + coversUpdated
               + ", coversSkipped=" + coversSkipped

               + ", styles=" + styles
               + ", preferences=" + preferences

               + ", failedLinesNr=" + failedLinesNr
               + ", failedLinesMessage=" + failedLinesMessage
               + '}';
    }
}
