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

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public class ExportManager
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ExportManager> CREATOR = new Creator<ExportManager>() {
        @Override
        public ExportManager createFromParcel(@NonNull final Parcel source) {
            return new ExportManager(source);
        }

        @Override
        public ExportManager[] newArray(final int size) {
            return new ExportManager[size];
        }
    };

    /**
     * Options to indicate new books or books with more recent
     * update_date fields should be exported.
     * <p>
     * 0: all books
     * 1: books added/updated since last backup.
     */
    static final int EXPORT_SINCE_LAST_BACKUP = 1 << 16;
    /**
     * all defined flags.
     */
    private static final int MASK = Options.ENTITIES | EXPORT_SINCE_LAST_BACKUP;
    /** Log tag. */
    private static final String TAG = "ExportHelper";
    /** Write to this temp file first. */
    private static final String TEMP_FILE_NAME = TAG + ".tmp";
    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /** The maximum file size for an export file for which we'll offer to send it as an email. */
    private static final int MAX_FILE_SIZE_FOR_EMAIL = 5_000_000;
    /**
     * Bitmask.
     * Contains the user selected options before doing the import/export.
     * After the import/export, reflects the entities actually imported/exported.
     */
    private int mOptions;
    @Nullable
    private Uri mUri;
    @Nullable
    private ExportResults mResults;
    @Nullable
    private ArchiveContainer mArchiveContainer;
    /** EXPORT_SINCE_LAST_BACKUP. */
    @Nullable
    private LocalDateTime mFromUtcDateTime;

    /**
     * Constructor.
     *
     * @param options to export
     */
    public ExportManager(final int options) {
        mOptions = options;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportManager(@NonNull final Parcel in) {
        mOptions = in.readInt();
        long epochMilli = in.readLong();
        if (epochMilli != 0) {
            // parcel from the epoch
            mFromUtcDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli),
                                                       ZoneOffset.UTC);
        }
        mUri = in.readParcelable(getClass().getClassLoader());
        mResults = in.readParcelable(getClass().getClassLoader());
    }

    public static String createErrorReport(@NonNull final Context context,
                                           @Nullable final Exception e) {
        String msg = null;

        if (e instanceof IOException) {
            // see if we can find the exact cause
            if (e.getCause() instanceof ErrnoException) {
                final int errno = ((ErrnoException) e.getCause()).errno;
                // write failed: ENOSPC (No space left on device)
                if (errno == OsConstants.ENOSPC) {
                    msg = context.getString(R.string.error_storage_no_space_left);
                } else {
                    // write to logfile for future reporting enhancements.
                    Logger.warn(context, TAG, "onExportFailed|errno=" + errno);
                }
            }

            // generic IOException message
            if (msg == null) {
                msg = StandardDialogs.createBadError(context, R.string.error_storage_not_writable);
            }
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = context.getString(R.string.error_unexpected_error);
        }

        return msg;
    }

    /** Called from the dialog via its View listeners. */
    void setOption(final int optionBit,
                   final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }

    @NonNull
    public ArchiveContainer getArchiveContainer() {
        if (mArchiveContainer == null) {
            // use the default
            return ArchiveContainer.Zip;
        }
        return mArchiveContainer;
    }

    void setArchiveContainer(@NonNull final ArchiveContainer archiveContainer) {
        mArchiveContainer = archiveContainer;
    }

    /**
     * Get the Uri for the user location to write to.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        Objects.requireNonNull(mUri, ErrorMsg.NULL_URI);
        return mUri;
    }

    public void setUri(@NonNull final Uri uri) {
        mUri = uri;
    }

    /**
     * Get the date-since.
     *
     * @return date (UTC based), or {@code null} if not in use
     */
    @Nullable
    public LocalDateTime getUtcDateTimeSince() {
        if ((mOptions & EXPORT_SINCE_LAST_BACKUP) != 0) {
            return mFromUtcDateTime;
        } else {
            return null;
        }
    }

    /**
     * Convenience method to return the date-from as a time {@code long}.
     *
     * @return epochMilli, or {@code 0} if not in use
     */
    long getDateSinceAsEpochMilli() {
        if (mFromUtcDateTime != null && ((mOptions & EXPORT_SINCE_LAST_BACKUP) != 0)) {
            return mFromUtcDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        } else {
            return 0;
        }
    }

    /**
     * Create a BackupWriter for the specified Uri.
     *
     * @param context Current context
     *
     * @return a new writer
     *
     * @throws IOException on failure
     */
    @NonNull
    public ArchiveWriter getArchiveWriter(@NonNull final Context context)
            throws IOException {

        //noinspection EnumSwitchStatementWhichMissesCases
        switch (getArchiveContainer()) {
            case Xml:
                return new XmlArchiveWriter(context, this);

            case CsvBooks:
                return new CsvArchiveWriter(this);

            case Tar:
                return new TarArchiveWriter(context, this);

            case Zip:
            default:
                // the default
                return new ZipArchiveWriter(context, this);

        }
    }

    /**
     * Get the temporary File to write to.
     * When writing is done (success <strong>and</strong> failure),
     * {@link #onSuccess} / {@link #onCleanup} must be called as needed.
     *
     * @param context Current context
     *
     * @return File
     */
    @NonNull
    public File getTempOutputFile(@NonNull final Context context) {
        return AppDir.Cache.getFile(context, TEMP_FILE_NAME);
    }

    /**
     * Called by the export task before starting.
     *
     * @param context Current context
     */
    public void validate(@NonNull final Context context) {
        if ((mOptions & MASK) == 0) {
            throw new IllegalStateException(ErrorMsg.OPTIONS_NOT_SET);
        }
        if (mUri == null) {
            throw new IllegalStateException(ErrorMsg.NULL_URI);
        }
        if ((mOptions & EXPORT_SINCE_LAST_BACKUP) != 0) {
            mFromUtcDateTime = getLastFullBackupDate(context);
        } else {
            mFromUtcDateTime = null;
        }
    }

    @NonNull
    public ExportResults getResults() {
        Objects.requireNonNull(mResults);
        return mResults;
    }

    public void setResults(@NonNull final ExportResults results) {
        mResults = results;
    }

    /**
     * Should be called after a successful write.
     *
     * @param context Current context
     *
     * @throws IOException on failure to write to the destination Uri
     */
    public void onSuccess(@NonNull final Context context)
            throws IOException {
        FileUtils.copy(context, AppDir.Cache.getFile(context, TEMP_FILE_NAME), getUri());
        FileUtils.delete(AppDir.Cache.getFile(context, TEMP_FILE_NAME));

        // if the backup was a full one (not a 'since') remember that.
        if ((mOptions & ExportManager.EXPORT_SINCE_LAST_BACKUP) != 0) {
            setLastFullBackupDate(context);
        }
    }

    /**
     * Should be called after a failed write.
     *
     * @param context Current context
     */
    public void onCleanup(@NonNull final Context context) {
        FileUtils.delete(AppDir.Cache.getFile(context, TEMP_FILE_NAME));
    }

    /**
     * Store the date of the last full backup ('now', UTC based)
     * and reset the startup prompt-counter.
     *
     * @param context Current context
     */
    private void setLastFullBackupDate(@NonNull final Context context) {
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_LAST_FULL_BACKUP_DATE,
                           LocalDateTime.now(ZoneOffset.UTC)
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .putInt(StartupViewModel.PK_STARTUP_BACKUP_COUNTDOWN,
                        StartupViewModel.STARTUP_BACKUP_COUNTDOWN)
                .apply();
    }

    /**
     * Get the last time we made a full backup.
     *
     * @param context Current context
     *
     * @return Date in the UTC timezone.
     */
    @Nullable
    private LocalDateTime getLastFullBackupDate(@NonNull final Context context) {
        String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateParser.getInstance(context).parseISO(lastBackup);
        }

        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mOptions);
        if (mFromUtcDateTime != null) {
            // parcel to the epoch
            dest.writeLong(mFromUtcDateTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        } else {
            dest.writeInt(0);
        }
        dest.writeParcelable(mUri, flags);
        dest.writeParcelable(mResults, flags);
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + ", mOptions=0b" + Integer.toBinaryString(mOptions)
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveContainer
               + ", mDateFrom=" + mFromUtcDateTime
               + ", mResults=" + mResults
               + '}';
    }

    public boolean offerEmail(@Nullable final Pair<String, Long> uriInfo) {
        final long fileSize;
        if (uriInfo != null && uriInfo.second != null) {
            fileSize = uriInfo.second;
        } else {
            fileSize = 0;
        }

        return fileSize > 0 && fileSize < MAX_FILE_SIZE_FOR_EMAIL;
    }

    public boolean isSet(final int optionBit) {
        return (mOptions & optionBit) != 0;
    }

    public int getOptions() {
        return mOptions;
    }

    /** Called <strong>after</strong> the export/import to report back what was handled. */
    public void setOptions(final int options) {
        mOptions = options;
    }

    /**
     * Check if there any options set that will cause us to export anything.
     *
     * @return {@code true} if something will be exported
     */
    boolean hasEntityOption() {
        return (mOptions & Options.ENTITIES) != 0;
    }
}
