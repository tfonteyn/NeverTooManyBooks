/*
 * @Copyright 2019 HardBackNutter
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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Options on what to export/backup and some support functions.
 */
public class ExportHelper
        extends Options {

    /**
     * Options to indicate new books or books with more recent update_date
     * fields should be exported.
     * <p>
     * 0: all books
     * 1: books added/updated since {@link #mDateFrom}.
     * If the latter is {@code null}, then since last backup.
     */
    public static final int EXPORT_SINCE = 1 << 16;
    public static final Creator<ExportHelper> CREATOR = new Creator<ExportHelper>() {
        @Override
        public ExportHelper createFromParcel(@NonNull final Parcel source) {
            return new ExportHelper(source);
        }

        @Override
        public ExportHelper[] newArray(final int size) {
            return new ExportHelper[size];
        }
    };
    /**
     * Options value to indicate all things should be exported.
     * Note that XML_TABLES is NOT included as it's considered special interest.
     */
    public static final int ALL = BOOK_CSV
                                  | PREFERENCES
                                  | BOOK_LIST_STYLES
                                  | COVERS;
    /**
     * all defined flags.
     */
    private static final int MASK = BOOK_CSV
                                    | PREFERENCES
                                    | BOOK_LIST_STYLES
                                    | COVERS
                                    | XML_TABLES
                                    | EXPORT_SINCE;
    private static final String TEMP_FILE_NAME = "tmpExport.tmp";

    @NonNull
    public final Exporter.Results results = new Exporter.Results();

    /** EXPORT_SINCE. */
    @Nullable
    private Date mDateFrom;

    /**
     * Constructor.
     *
     * @param options to export
     */
    public ExportHelper(final int options,
                        @Nullable final Uri uri) {
        super(options, uri);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportHelper(@NonNull final Parcel in) {
        super(in);

        // date follows ?
        if (in.readInt() != 0) {
            mDateFrom = new Date(in.readLong());
        }
    }

    public static File getTempFile() {
        return new File(StorageUtils.getCacheDir(), TEMP_FILE_NAME);
    }

    public void addResults(@NonNull final Exporter.Results results) {
        this.results.booksProcessed += results.booksProcessed;
        this.results.booksExported += results.booksExported;

        this.results.coversProcessed += results.coversProcessed;
        this.results.coversExported += results.coversExported;
        this.results.coversMissing += results.coversMissing;
    }

    @NonNull
    public Exporter.Results getResults() {
        return results;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        if (mDateFrom != null) {
            // date follows
            dest.writeInt(1);
            dest.writeLong(mDateFrom.getTime());
        } else {
            // no date
            dest.writeInt(0);
        }
    }

    @Nullable
    public Date getDateFrom() {
        return mDateFrom;
    }

    public void setDateFrom(@Nullable final Date date) {
        mDateFrom = date;
    }

    /**
     * Convenience method to return the date-from as a time. Returns 0 if the date is not set.
     *
     * @return time
     */
    public long getTimeFrom() {
        if (mDateFrom != null && (options & EXPORT_SINCE) != 0) {
            return mDateFrom.getTime();
        } else {
            return 0;
        }
    }

    /**
     * Called by the export task before starting.
     */
    public void validate() {
        super.validate();

        if ((options & MASK) == 0) {
            throw new IllegalStateException("options not set");
        }

        // when doing a backup 'since' check/set the date field.
        if ((options & EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (mDateFrom == null) {
                String lastBackup = BackupManager.getLastFullBackupDate(App.getAppContext());
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    mDateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            // cannot have a mDateFrom when not asking for a time limited export
            mDateFrom = null;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + ", options=0b" + Integer.toBinaryString(options)
               + ", results=" + results
               + ", mDateFrom=" + mDateFrom
               + '}';
    }
}
