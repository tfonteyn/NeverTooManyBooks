/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Value class to report back what was imported.
 * <p>
 * Used by {@link RecordReader} and accumulated in {@link DataReader}.
 */
@SuppressWarnings("WeakerAccess")
public class ImportResults
        extends ReaderResults {

    /** {@link Parcelable}. */
    public static final Creator<ImportResults> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ImportResults createFromParcel(@NonNull final Parcel in) {
            return new ImportResults(in);
        }

        @Override
        @NonNull
        public ImportResults[] newArray(final int size) {
            return new ImportResults[size];
        }
    };
    public static final int MAX_FAIL_LINES = 10;
    /** Log tag. */
    private static final String TAG = "ImportResults";
    /** Bundle key if we get passed around. */
    public static final String BKEY = TAG;

    /**
     * Keeps track of failed import lines in a text file.
     * Not strictly needed as row number should be part of the messages.
     * Keeping for possible future enhancements.
     */
    public final List<Integer> failedLinesNr = new ArrayList<>();
    /** Keeps track of failed import lines in a text file. */
    public final List<String> failedLinesMessage = new ArrayList<>();
    /** records we found, but did not understand; i.e. did not have a {@link RecordReader} for. */
    public int recordsSkipped;

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

        recordsSkipped = in.readInt();
        in.readList(failedLinesNr, getClass().getClassLoader());
        in.readList(failedLinesMessage, getClass().getClassLoader());
    }

    public void add(@NonNull final ImportResults results) {
        super.add(results);

        styles += results.styles;
        preferences += results.preferences;
        certificates += results.certificates;

        recordsSkipped += results.recordsSkipped;
        failedLinesNr.addAll(results.failedLinesNr);
        failedLinesMessage.addAll(results.failedLinesMessage);
    }

    public void handleRowException(@NonNull final Context context,
                                   final int row,
                                   @NonNull final Exception e,
                                   @Nullable final String msg) {
        final String message;
        if (msg != null) {
            message = msg;
        } else if (e instanceof LocalizedException) {
            message = ((LocalizedException) e).getUserMessage(context);
        } else {
            message = context.getString(R.string.error_import_csv_line, row);
        }

        failedLinesMessage.add(message);
        failedLinesNr.add(row);
        booksFailed++;

        final Logger logger = ServiceLocator.getInstance().getLogger();
        if (booksFailed <= MAX_FAIL_LINES) {
            logger.w(TAG, "Import failed for book " + row + "|e=" + e.getMessage());
        }
        if (BuildConfig.DEBUG /* always */) {
            if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS && booksFailed > MAX_FAIL_LINES) {
                logger.w(TAG, "Import failed for book " + row + "|e=" + e.getMessage());
            } else if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
                // logging with the full exception is VERY HEAVY
                logger.e(TAG, e, "Import failed for book " + row);
            }
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeInt(styles);
        dest.writeInt(preferences);
        dest.writeInt(certificates);

        dest.writeInt(recordsSkipped);
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

               + ", recordsSkipped=" + recordsSkipped
               + ", failedLinesNr=" + failedLinesNr
               + ", failedLinesMessage=" + failedLinesMessage
               + '}';
    }
}
