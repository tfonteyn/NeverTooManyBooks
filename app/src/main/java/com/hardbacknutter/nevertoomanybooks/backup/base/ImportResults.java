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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Value class to report back what was imported.
 * <p>
 * Used by {@link Importer} and accumulated in {@link ArchiveReader}.
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

    /**
     * The resulting {@link ImportHelper} options flags after an import.
     * <p>
     * <br>type: {@code int} (bitmask)
     * setResult
     */
    public static final String BKEY_IMPORT_RESULTS = "importResults";

    private static final String BULLET = "\n• ";
    /** Keeps track of failed import lines in a text file. */
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

               + ", coversSkipped=" + coversProcessed
               + ", coversCreated=" + coversCreated
               + ", coversUpdated=" + coversUpdated
               + ", coversSkipped=" + coversSkipped

               + ", styles=" + styles
               + ", preferences=" + preferences

               + ", failedLinesNr=" + failedLinesNr
               + ", failedLinesMessage=" + failedLinesMessage
               + '}';
    }

    /**
     * Transform the result data into a user friendly report.
     *
     * @param context Current context
     *
     * @return report string
     */
    public String createReport(@NonNull final Context context) {
        //
        final StringBuilder report = new StringBuilder();

        //TODO: RTL
        if (booksCreated > 0 || booksUpdated > 0 || booksSkipped > 0) {
            report.append(BULLET)
                  .append(context.getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                            context.getString(R.string.lbl_books),
                                            booksCreated,
                                            booksUpdated,
                                            booksSkipped));
        }
        if (coversCreated > 0 || coversUpdated > 0 || coversSkipped > 0) {
            report.append(BULLET)
                  .append(context.getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                            context.getString(R.string.lbl_covers),
                                            coversCreated,
                                            coversUpdated,
                                            coversSkipped));
        }
        if (styles > 0) {
            report.append(BULLET).append(context.getString(R.string.name_colon_value,
                                                           context.getString(R.string.lbl_styles),
                                                           String.valueOf(styles)));
        }
        if (preferences > 0) {
            report.append(BULLET).append(context.getString(R.string.lbl_settings));
        }

        int failed = failedLinesNr.size();
        if (failed > 0) {
            final int fs;
            final Collection<String> msgList = new ArrayList<>();

            if (failed > 10) {
                // keep it sensible, list maximum 10 lines.
                failed = 10;
                fs = R.string.warning_import_csv_failed_lines_lots;
            } else {
                fs = R.string.warning_import_csv_failed_lines_some;
            }

            for (int i = 0; i < failed; i++) {
                msgList.add(context.getString(R.string.a_bracket_b_bracket,
                                              String.valueOf(failedLinesNr.get(i)),
                                              failedLinesMessage.get(i)));
            }

            final String all = msgList.stream()
                                      .map(s -> context.getString(R.string.list_element, s))
                                      .collect(Collectors.joining("\n"));
            report.append("\n").append(context.getString(fs, all));
        }

        return report.toString();
    }
}
