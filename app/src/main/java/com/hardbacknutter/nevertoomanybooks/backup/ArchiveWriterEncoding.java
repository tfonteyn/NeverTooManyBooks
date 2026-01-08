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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Archive encoding (formats) (partially) supported.
 * <p>
 * This is the top level, i.e. the actual file we read/write.
 */
public enum ArchiveWriterEncoding
        implements Parcelable {
    /**
     * The default full backup/restore support.
     * Will contain all data as JSON files + all cover images.
     */
    Zip("zip",
        R.string.option_archive_type_backup_zip,
        R.string.option_info_lbl_archive_type_backup),

    /**
     * Will contain all data in a single JSON file. No images.
     * Full support for export/import.
     */
    Json("json", R.string.option_archive_type_json,
         R.string.option_info_archive_format_json),

    /** Database. */
    SqLiteDb("db", R.string.option_archive_type_db,
             R.string.option_info_archive_format_db);

    /** {@link Parcelable}. */
    public static final Creator<ArchiveWriterEncoding> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ArchiveWriterEncoding createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public ArchiveWriterEncoding[] newArray(final int size) {
            return new ArchiveWriterEncoding[size];
        }
    };

    /** Log tag. */
    private static final String TAG = "ArchiveEncoding";

    /** The <strong>proposed</strong> archive filename extension to write to. */
    @NonNull
    private final String fileExt;
    private final int selectorResId;
    private final int shortDescResId;

    /**
     * Constructor.
     *
     * @param fileExt        to use as the proposed archive filename extension
     * @param selectorResId  the string resource to show in the dropdown
     * @param shortDescResId the matching short description to show below the dropdown
     */
    ArchiveWriterEncoding(@NonNull final String fileExt,
                          final int selectorResId,
                          final int shortDescResId) {
        this.fileExt = fileExt;
        this.selectorResId = selectorResId;
        this.shortDescResId = shortDescResId;
    }

    /**
     * Get the <strong>proposed</strong> archive file extension for writing an output file.
     *
     * @return file name extension
     */
    @NonNull
    String getFileExt() {
        return fileExt;
    }

    @StringRes
    int getSelectorResId() {
        return selectorResId;
    }

    @StringRes
    int getShortDescResId() {
        return shortDescResId;
    }

    /**
     * Create an {@link DataWriter} based on the type.
     *
     * @param context       Current context
     * @param recordTypes   the record types to write
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     * @param destFile      {@link File} to write to
     *
     * @return a new writer
     *
     * @throws FileNotFoundException if the writer could not open a file
     * @throws IllegalStateException (debug) if there is no writer available
     */
    @WorkerThread
    @NonNull
    DataWriter<ExportResults> createWriter(@NonNull final Context context,
                                           @NonNull final Set<RecordType> recordTypes,
                                           @Nullable final LocalDateTime sinceDateTime,
                                           @NonNull final File destFile)
            throws FileNotFoundException {

        switch (this) {
            case Zip: {
                return new ZipArchiveWriter(recordTypes, sinceDateTime, destFile);
            }
            case SqLiteDb: {
                return new DbArchiveWriter(DBHelper.getDatabasePath(context), destFile);
            }
            case Json: {
                return new JsonArchiveWriter(recordTypes, sinceDateTime, destFile);
            }
            default:
                throw new IllegalStateException(DataWriter.ERROR_NO_WRITER_AVAILABLE);
        }
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
}
