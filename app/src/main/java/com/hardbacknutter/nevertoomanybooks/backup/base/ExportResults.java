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
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Value class to report back what was exported.
 * <p>
 * Used by {@link Exporter} and accumulated in {@link ArchiveWriter}.
 */
public class ExportResults
        implements Parcelable {

    /** {@link Parcelable}. */
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

    /** id's of books we exported. */
    private final List<Long> mBooksExported = new ArrayList<>();

    /** filenames of covers exported. */
    private final List<String> mCoversExported = new ArrayList<>();

    /** #styles we exported. */
    public int styles;
    /** #preferences we exported. */
    public int preferences;

    /** whether we exported the actual database. */
    public boolean database;

    public ExportResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportResults(@NonNull final Parcel in) {
        in.readList(mBooksExported, getClass().getClassLoader());
        in.readStringList(mCoversExported);

        styles = in.readInt();
        preferences = in.readInt();
        database = in.readByte() != 0;
    }

    /**
     * Add a set of results to the current set of results.
     *
     * @param results to add
     */
    public void add(@NonNull final ExportResults results) {
        mBooksExported.addAll(results.mBooksExported);
        mCoversExported.addAll(results.mCoversExported);

        styles += results.styles;
        preferences += results.preferences;
    }

    public void addBook(final long bookId) {
        mBooksExported.add(bookId);
    }

    public int getBookCount() {
        return mBooksExported.size();
    }


    public void addCover(@NonNull final String cover) {
        mCoversExported.add(cover);
    }

    public int getCoverCount() {
        return mCoversExported.size();
    }

    public List<String> getCoverFileNames() {
        return mCoversExported;
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

        if (!mBooksExported.isEmpty()) {
            msg.append(BULLET)
               .append(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_books),
                                         String.valueOf(mBooksExported.size())));
        }
        if (!mCoversExported.isEmpty()) {
            msg.append(BULLET)
               .append(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_covers),
                                         String.valueOf(mCoversExported.size())));
        }

        if (styles > 0) {
            msg.append(BULLET)
               .append(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_styles),
                                         String.valueOf(styles)));
        }
        if (preferences > 0) {
            msg.append(BULLET).append(context.getString(R.string.lbl_settings));
        }
        if (database) {
            msg.append(BULLET).append(context.getString(R.string.lbl_database));
        }

        // The below works, but we cannot get the folder name for the file.
        // FIXME: We need to change the descriptive string not to include the folder.
        if (uriInfo != null && uriInfo.first != null && uriInfo.second != null) {
            msg.append("\n\n")
               .append(context.getString(R.string.progress_end_export_success,
                                         "",
                                         uriInfo.first,
                                         FileUtils.formatFileSize(context, uriInfo.second)));
        }

        return msg.toString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeList(mBooksExported);
        dest.writeStringList(mCoversExported);

        dest.writeInt(styles);
        dest.writeInt(preferences);
        dest.writeByte((byte) (database ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + ", booksExported=" + mBooksExported
               + ", coversExported=" + mCoversExported
               + ", styles=" + styles
               + ", preferences=" + preferences
               + ", database=" + database
               + '}';
    }
}
