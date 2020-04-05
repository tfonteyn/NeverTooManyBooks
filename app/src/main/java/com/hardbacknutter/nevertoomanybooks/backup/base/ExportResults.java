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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Value class to report back what was exported.
 * <p>
 * Used by {@link Exporter} and accumulated in {@link ArchiveWriter}.
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
    private static final String BULLET = "\n• ";

    /** #books that did not have a front-cover [0] / back-cover [1]. */
    @NonNull
    public final int[] coversMissing;

    /** #books we exported. */
    public int booksExported;
    /** #covers exported. */
    public int coversExported;
    /** #covers that were skipped. */
    public int coversSkipped;

    /** #styles we exported. */
    public int styles;
    /** #preferences we exported. */
    public int preferences;

    public ExportResults() {
        coversMissing = new int[2];
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportResults(@NonNull final Parcel in) {
        booksExported = in.readInt();
        coversExported = in.readInt();

        coversSkipped = in.readInt();
        //noinspection ConstantConditions
        coversMissing = in.createIntArray();

        styles = in.readInt();
        preferences = in.readInt();
    }

    public void add(@NonNull final ExportResults results) {
        booksExported += results.booksExported;
        coversExported += results.coversExported;

        coversSkipped += results.coversSkipped;
        coversMissing[0] += results.coversMissing[0];
        coversMissing[1] += results.coversMissing[1];

        styles += results.styles;
        preferences += results.preferences;
    }

    /**
     * Transform the result data into a user friendly report.
     *
     * @param context Current context
     *
     * @return report string
     */
    public String createReport(@NonNull final Context context,
                               @Nullable final Pair<String, Long> uriInfo) {
        // Transform the result data into a user friendly report.
        final StringBuilder msg = new StringBuilder();

        //TODO: RTL
        // slightly misleading. The text currently says "processed" but it's really "exported".
        if (booksExported > 0) {
            msg.append(BULLET)
               .append(context.getString(R.string.progress_end_export_result_n_books_processed,
                                         booksExported));
        }
        if (coversExported > 0
            || coversMissing[0] > 0
            || coversMissing[1] > 0) {
            msg.append(BULLET)
               .append(context.getString(
                       R.string.progress_end_export_result_n_covers_processed_m_missing,
                       coversExported,
                       coversMissing[0],
                       coversMissing[1]));
        }

        if (styles > 0) {
            msg.append(BULLET).append(context.getString(R.string.name_colon_value,
                                                        context.getString(R.string.lbl_styles),
                                                        String.valueOf(styles)));
        }
        if (preferences > 0) {
            msg.append(BULLET).append(context.getString(R.string.lbl_settings));
        }


        // The below works, but we cannot get the folder name for the file.
        // Disabling for now. We'd need to change the descriptive string not to include the folder.
        if (uriInfo != null && uriInfo.first != null && uriInfo.second != null) {
            msg.append("\n\n")
               .append(context.getString(R.string.X_export_info_success_archive_details,
                                         "",
                                         uriInfo.first,
                                         FileUtils.formatFileSize(context, uriInfo.second)));
        }

        return msg.toString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksExported);
        dest.writeInt(coversExported);
        dest.writeInt(coversSkipped);
        dest.writeIntArray(coversMissing);

        dest.writeInt(styles);
        dest.writeInt(preferences);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + ", booksExported=" + booksExported
               + ", coversExported=" + coversExported

               + ", coversSkipped=" + coversSkipped
               + ", coversMissing[0]=" + coversMissing[0]
               + ", coversMissing[1]=" + coversMissing[1]

               + ", styles=" + styles
               + ", preferences=" + preferences
               + '}';
    }
}
