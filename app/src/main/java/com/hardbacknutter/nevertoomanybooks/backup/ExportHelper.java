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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public class ExportHelper {

    /**
     * Do an incremental backup.
     * <ul>
     *     <li>0: all books</li>
     *     <li>1: books added/updated since last backup</li>
     * </ul>
     */
    static final int OPTION_INCREMENTAL = 1;

    /** Log tag. */
    private static final String TAG = "ExportHelper";
    /** Write to this temp file first. */
    private static final String TEMP_FILE_NAME = TAG + ".tmp";
    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /** What is going to be exported. */
    @NonNull
    private final Set<ArchiveWriterRecord.Type> mExportEntries;
    /** Picked by the user; where we write to. */
    @Nullable
    private Uri mUri;
    /** Set by the user; defaults to ZIP. */
    @NonNull
    private ArchiveType mArchiveType = ArchiveType.Zip;
    /** Bitmask. Contains extra options for the {@link RecordWriter}. */
    @Options
    private int mOptions;
    /** Incremental backup; the date of the last full backup. */
    @Nullable
    private LocalDateTime mFromUtcDateTime;


    /**
     * Constructor.
     */
    public ExportHelper() {
        mExportEntries = EnumSet.allOf(ArchiveWriterRecord.Type.class);
    }

    /**
     * Constructor for testing individual options.
     *
     * @param exportEntries to write
     */
    @VisibleForTesting
    public ExportHelper(@NonNull final ArchiveWriterRecord.Type... exportEntries) {
        mExportEntries = EnumSet.copyOf(Arrays.asList(exportEntries));
    }

    /**
     * Create the proposed name for the archive. The user can change it.
     *
     * @return archive name
     */
    @NonNull
    String getDefaultUriName() {
        return "ntmb-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
               + mArchiveType.getFileExt();
    }

    public void setArchiveType(@NonNull final ArchiveType archiveType) {
        mArchiveType = archiveType;
    }

    /**
     * Get the Uri for the user location to write to.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        return Objects.requireNonNull(mUri, "uri");
    }

    public void setUri(@NonNull final Uri uri) {
        mUri = uri;
    }


    /**
     * Create a BackupWriter for the specified Uri.
     *
     * @param context Current context
     *
     * @return a new writer
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     *                                 (this would be a bug)
     * @throws IOException             on failure
     */
    @NonNull
    public ArchiveWriter createArchiveWriter(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(mUri, "uri");
            if (mExportEntries.isEmpty()) {
                throw new IllegalStateException("mExportEntities is empty");
            }
        }

        if ((mOptions & OPTION_INCREMENTAL) != 0) {
            mFromUtcDateTime = getLastFullBackupDate(context);
        } else {
            mFromUtcDateTime = null;
        }

        return mArchiveType.createArchiveWriter(context, this);
    }

    /**
     * Get the temporary File to write to.
     * When writing is done (success <strong>and</strong> failure),
     * {@link #onSuccess} / {@link #onError} must be called as needed.
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
     * Should be called after a successful write.
     *
     * @param context Current context
     *
     * @throws IOException on failure to write to the destination Uri
     */
    public void onSuccess(@NonNull final Context context)
            throws IOException {
        // The output file is now properly closed, export it to the user Uri
        final File tmpOutput = AppDir.Cache.getFile(context, TEMP_FILE_NAME);
        FileUtils.copy(context, tmpOutput, getUri());

        // if the backup was a full backup remember that.
        if ((mOptions & OPTION_INCREMENTAL) != 0) {
            setLastFullBackupDate(context);
        }

        // cleanup
        FileUtils.delete(tmpOutput);
    }

    /**
     * Should be called after a failed write.
     *
     * @param context Current context
     */
    public void onError(@NonNull final Context context) {
        // cleanup
        FileUtils.delete(AppDir.Cache.getFile(context, TEMP_FILE_NAME));
    }


    /**
     * Get the date-since.
     *
     * @return date (UTC based), or {@code null} if not in use
     */
    @Nullable
    public LocalDateTime getUtcDateTimeSince() {
        if ((mOptions & OPTION_INCREMENTAL) != 0) {
            return mFromUtcDateTime;
        } else {
            return null;
        }
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
        final String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                                   .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateParser.getInstance(context).parseISO(lastBackup);
        }

        return null;
    }


    void setExportEntry(@NonNull final ArchiveWriterRecord.Type entry,
                        final boolean isSet) {
        if (isSet) {
            mExportEntries.add(entry);
        } else {
            mExportEntries.remove(entry);
        }
    }

    @NonNull
    public Set<ArchiveWriterRecord.Type> getExporterEntries() {
        return mExportEntries;
    }


    boolean isIncremental() {
        return (mOptions & OPTION_INCREMENTAL) != 0;
    }

    void setIncremental(final boolean isSet) {
        if (isSet) {
            mOptions |= OPTION_INCREMENTAL;
        } else {
            mOptions &= ~OPTION_INCREMENTAL;
        }
    }

    @Options
    public int getOptions() {
        return mOptions;
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner options = new StringJoiner(",", "[", "]");
        if ((mOptions & OPTION_INCREMENTAL) != 0) {
            options.add("INCREMENTAL");
        }

        return "ExportHelper{"
               + "mExportEntities=" + mExportEntries
               + ", mOptions=0b" + Integer.toBinaryString(mOptions) + ": " + options.toString()
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveType
               + ", mFromUtcDateTime=" + mFromUtcDateTime
               + '}';
    }

    @IntDef(flag = true, value = OPTION_INCREMENTAL)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Options {

    }
}
