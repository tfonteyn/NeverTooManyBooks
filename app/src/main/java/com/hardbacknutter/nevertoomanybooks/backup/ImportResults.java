/*
 * @Copyright 2018-2024 HardBackNutter
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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Value class to report back what was imported.
 * <p>
 * Used by {@link RecordReader} and accumulated in {@link DataReader}.
 */
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

    /**
     * The maximum lines with failure messages presented to the user.
     * Note that ALL failures will be reported in the logfile regardless.
     */
    static final int MAX_FAIL_LINES_REPORTED = 10;

    /** Log tag. */
    private static final String TAG = "ImportResults";

    /** Bundle key to pass this object around. */
    public static final String BKEY = TAG;

    /**
     * Keeps track of failed import lines in a text file.
     * Not strictly needed as row number should be part of the messages.
     * Keeping for possible future enhancements.
     */
    final List<Integer> failedLinesNr = new ArrayList<>();
    /** Keeps track of failed import lines in a text file. */
    final List<String> failedLinesMessage = new ArrayList<>();
    /** records we found, but did not understand; i.e. did not have a {@link RecordReader} for. */
    public int recordsSkipped;

    /** #styles we imported. */
    public int styles;
    /** #preferences we imported. */
    public int preferences;
    /** #certificates we imported. */
    public int certificates;

    /** #bookshelves (new only) we imported. */
    public int bookshelves;

    /** #deletedBook uuids we imported. */
    public int deletedBookRecords;

    /**
     * Constructor.
     */
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
        bookshelves = in.readInt();
        deletedBookRecords = in.readInt();

        recordsSkipped = in.readInt();
        in.readList(failedLinesNr, getClass().getClassLoader());
        in.readList(failedLinesMessage, getClass().getClassLoader());
    }

    /**
     * Accumulate the results.
     *
     * @param results to add
     */
    public void add(@NonNull final ImportResults results) {
        final int booksFailedBefore = booksFailed;

        super.add(results);

        styles += results.styles;
        preferences += results.preferences;
        certificates += results.certificates;
        bookshelves += results.bookshelves;
        deletedBookRecords += results.deletedBookRecords;

        recordsSkipped += results.recordsSkipped;

        // see comments in #handleRowException
        for (int i = 0; i < results.failedLinesNr.size()
                        && booksFailedBefore + i < MAX_FAIL_LINES_REPORTED;
             i++) {
            failedLinesNr.add(results.failedLinesNr.get(i));
            failedLinesMessage.add(results.failedLinesMessage.get(1));
        }
    }

    /**
     * Add the given exception data to the results.
     *
     * @param context          Current context
     * @param row              where the issue happened
     * @param e                the exception thrown
     * @param localizedMessage optional; a <strong>localized</strong> message which
     *                         <strong>will</strong> be shown to the user
     */
    public void handleRowException(@NonNull final Context context,
                                   final int row,
                                   @NonNull final Exception e,
                                   @Nullable final String localizedMessage) {
        final String message = Objects.requireNonNullElseGet(localizedMessage, () -> ExMsg
                .map(context, e)
                .orElseGet(() -> context.getString(R.string.error_import_csv_line, row)));

        // Limit the amount of reporting (to the user) so we don't
        // - overrun the Parceling max message size
        // - overwhelm the user with to many (maybe identical) messages.
        // but we DO log ALL message (see below)
        if (booksFailed < MAX_FAIL_LINES_REPORTED) {
            failedLinesMessage.add(message);
            failedLinesNr.add(row);
        }
        booksFailed++;

        final Logger logger = LoggerFactory.getLogger();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
            // logging with the full exception is VERY HEAVY
            logger.d(TAG, "handleRowException", e.getClass().getSimpleName(),
                     "row=" + row, e);
        } else {
            // NOT in debug; log just the message
            logger.w(TAG, e.getClass().getSimpleName(), "row=" + row, e.getMessage());
        }
    }

    @NonNull
    @Override
    public List<String> createReport(@NonNull final Context context) {
        final List<String> lines = super.createReport(context);

        if (styles > 0) {
            lines.add(context.getString(R.string.list_element, context.getString(
                    R.string.name_colon_value,
                    context.getString(R.string.lbl_styles),
                    // deduct built-in styles
                    String.valueOf(styles - BuiltinStyle.size()))));
        }
        if (preferences > 0) {
            lines.add(context.getString(R.string.list_element, context.getString(
                    R.string.lbl_settings)));
        }
        if (certificates > 0) {
            lines.add(context.getString(R.string.list_element, context.getString(
                    R.string.name_colon_value,
                    context.getString(R.string.lbl_certificates),
                    String.valueOf(certificates))));
        }

        if (bookshelves > 0) {
            lines.add(context.getString(R.string.list_element, context.getString(
                    R.string.name_colon_value,
                    context.getString(R.string.lbl_bookshelves),
                    String.valueOf(bookshelves))));
        }

        return lines;
    }

    @NonNull
    List<String> createFailuresReport(@NonNull final Context context) {
        final List<String> lines = new ArrayList<>();

        int failed = failedLinesNr.size();
        if (failed == 0) {
            return lines;
        }
        // sanity check, the amount of reporting should already be cut at error-time
        if (failed > MAX_FAIL_LINES_REPORTED) {
            failed = MAX_FAIL_LINES_REPORTED;
        }
        for (int i = 0; i < failed; i++) {
            lines.add(context.getString(R.string.list_element, context.getString(
                    R.string.a_bracket_b_bracket,
                    String.valueOf(failedLinesNr.get(i)),
                    failedLinesMessage.get(i))));
        }
        return lines;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeInt(styles);
        dest.writeInt(preferences);
        dest.writeInt(certificates);
        dest.writeInt(bookshelves);
        dest.writeInt(deletedBookRecords);

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
               + ", bookshelves=" + bookshelves
               + ", deletedBooks=" + deletedBookRecords

               + ", recordsSkipped=" + recordsSkipped
               + ", failedLinesNr=" + failedLinesNr
               + ", failedLinesMessage=" + failedLinesMessage
               + '}';
    }
}
