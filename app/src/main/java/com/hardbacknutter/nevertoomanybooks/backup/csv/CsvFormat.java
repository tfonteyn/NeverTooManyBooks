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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;

public enum CsvFormat
        implements Parcelable {
    /** A Goodreads export. */
    Goodreads(R.string.site_goodreads, null),

    Calibre(R.string.site_calibre, "timestamp"),

    /** The original BC format, or the extended but obsolete NTMB 1.x .. 3.x format. */
    BC(R.string.lbl_book_catalogue, DBKey.DATE_LAST_UPDATED__UTC),

    /** Anything not explicitly recognised. */
    Unknown(R.string.unknown, null);

    /** {@link Parcelable}. */
    public static final Creator<CsvFormat> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CsvFormat createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public CsvFormat[] newArray(final int size) {
            return new CsvFormat[size];
        }
    };
    private static final String TAG = "CsvFormat";
    /** Bundle key to pass this object around. */
    public static final String BKEY = TAG + ":bk";
    @StringRes
    private final int labelId;
    @Nullable
    private final String lastUpdateColumnName;

    CsvFormat(@StringRes final int labelId,
              @Nullable final String lastUpdateColumnName) {
        this.labelId = labelId;
        this.lastUpdateColumnName = lastUpdateColumnName;
    }

    /**
     * Parse the CSV file header line to guess the origin/format.
     *
     * @param context Current context
     * @param header  to parse
     *
     * @return format detected
     *
     * @throws DataReaderException on total failure to detect
     *                             or if there is a conflict found
     */
    @NonNull
    static CsvFormat guess(@NonNull final Context context,
                           @NonNull final String header)
            throws DataReaderException {

        if (header.startsWith("_id,author_details,title,isbn")
            || header.startsWith("\"_id\",\"author_details\",\"title\",\"isbn\"")) {
            // We have a pretty good match for original BC files
            return CsvFormat.BC;
        }

        if (header.startsWith("\"_id\",")) {
            // It's likely/hopefully a match for BC or NTMB 1-3 formats
            return CsvFormat.BC;
        }

        final Set<String> columnNames = Set.of(header.split(","));

        // RELEASE: check the latest Goodreads CSV export file header.
        // Check for some fairly distinct column names
        if (columnNames.contains("Book Id")
            && columnNames.contains("Author l-f")
            && columnNames.contains("Exclusive Shelf")) {

            return CsvFormat.Goodreads;
        }

        if (columnNames.contains("library_name")
            && columnNames.contains("author_sort")
            && columnNames.contains("title")) {

            // Calibre does not escape CR/LF in comment fields.
            // This breaks the format utterly.
            // If we find these columns, simply refuse to continue.
            // If other fields contain any CR/LF, we'll throw an error when we get there.
            if (columnNames.contains("comments") || columnNames.contains("#notes")) {
                throw new DataReaderException(
                        context.getString(R.string.error_import_csv_calibre));
            }

            // Hope for the best
            return CsvFormat.Calibre;
        }

        return CsvFormat.Unknown;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(ordinal());
    }

    /**
     * The user-displayable name for this format.
     *
     * @param context Current context
     *
     * @return label
     */
    @NonNull
    public CharSequence getLabel(@NonNull final Context context) {
        return context.getString(labelId);
    }

    @Nullable
    String getLastUpdateColumnName() {
        return lastUpdateColumnName;
    }
}
