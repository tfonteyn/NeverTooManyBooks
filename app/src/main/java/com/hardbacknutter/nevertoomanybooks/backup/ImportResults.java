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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordReader;
import com.hardbacknutter.nevertoomanybooks.utils.ReaderResults;

/**
 * Value class to report back what was imported.
 * <p>
 * Used by {@link RecordReader} and accumulated in {@link ArchiveReader}.
 */
public class ImportResults
        extends ReaderResults {

    /** {@link Parcelable}. */
    public static final Creator<ImportResults> CREATOR = new Creator<>() {
        @Override
        public ImportResults createFromParcel(@NonNull final Parcel in) {
            return new ImportResults(in);
        }

        @Override
        public ImportResults[] newArray(final int size) {
            return new ImportResults[size];
        }
    };

    /**
     * Keeps track of failed import lines in a text file.
     * Not strictly needed as row number should be part of the messages.
     * Keeping for possible future enhancements.
     */
    public final List<Integer> failedLinesNr = new ArrayList<>();
    /** Keeps track of failed import lines in a text file. */
    public final List<String> failedLinesMessage = new ArrayList<>();

    /** #styles we imported. */
    public int styles;
    /** #preferences we imported. */
    public int preferences;
    /** #certificates we imported. */
    public int certificates;

    public ImportResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportResults(@NonNull final Parcel in) {
        super(in);

        styles = in.readInt();
        preferences = in.readInt();
        certificates = in.readInt();

        in.readList(failedLinesNr, getClass().getClassLoader());
        in.readList(failedLinesMessage, getClass().getClassLoader());
    }

    public void add(@NonNull final ImportResults results) {
        super.add(results);

        styles += results.styles;
        preferences += results.preferences;
        certificates += results.certificates;

        failedLinesNr.addAll(results.failedLinesNr);
        failedLinesMessage.addAll(results.failedLinesMessage);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeInt(styles);
        dest.writeInt(preferences);
        dest.writeInt(certificates);

        dest.writeList(failedLinesNr);
        dest.writeList(failedLinesMessage);
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + super.toString()
               + ", styles=" + styles
               + ", preferences=" + preferences
               + ", certificates=" + certificates

               + ", failedLinesNr=" + failedLinesNr
               + ", failedLinesMessage=" + failedLinesMessage
               + '}';
    }
}
