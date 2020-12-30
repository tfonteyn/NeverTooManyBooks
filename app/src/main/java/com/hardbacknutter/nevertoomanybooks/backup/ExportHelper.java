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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public class ExportHelper {

    /** Log tag. */
    private static final String TAG = "ExportHelper";
    /** Write to this temp file first. */
    private static final String TEMP_FILE_NAME = TAG + ".tmp";
    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /** What is going to be exported. */
    @NonNull
    private final Set<RecordType> mExportEntries;
    /** Picked by the user; where we write to. */
    @Nullable
    private Uri mUri;
    /** Set by the user; defaults to ZIP. */
    @NonNull
    private ArchiveEncoding mArchiveEncoding = ArchiveEncoding.Zip;
    /**
     * Do an incremental backup.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated since last backup</li>
     * </ul>
     */
    private boolean mIncremental;
    /** Incremental backup; the date of the last full backup. */
    @Nullable
    private LocalDateTime mFromUtcDateTime;

    /**
     * Constructor.
     */
    public ExportHelper() {
        mExportEntries = EnumSet.allOf(RecordType.class);
    }

    /**
     * Constructor for testing individual options.
     *
     * @param exportEntries to write
     */
    @VisibleForTesting
    public ExportHelper(@NonNull final RecordType... exportEntries) {
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
               + mArchiveEncoding.getFileExt();
    }

    public void setArchiveEncoding(@NonNull final ArchiveEncoding archiveEncoding) {
        mArchiveEncoding = archiveEncoding;
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
     * @throws FileNotFoundException on ...
     */
    @NonNull
    public ArchiveWriter createArchiveWriter(@NonNull final Context context)
            throws FileNotFoundException {

        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(mUri, "uri");
            if (mExportEntries.isEmpty()) {
                throw new IllegalStateException("mExportEntries.isEmpty()");
            }
        }

        // set the date before we pass control to the writer.
        if (mIncremental) {
            mFromUtcDateTime = getLastFullBackupDate(context);
        } else {
            mFromUtcDateTime = null;
        }

        return mArchiveEncoding.createWriter(context, this);
    }

    /**
     * Create/get the OutputStream to write to.
     * When writing is done (success <strong>and</strong> failure),
     * {@link #onSuccess} / {@link #onError} must be called as needed.
     *
     * @param context Current context
     *
     * @return OutputStream
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    public OutputStream createOutputStream(@NonNull final Context context)
            throws FileNotFoundException {
        return new FileOutputStream(AppDir.Cache.getFile(context, TEMP_FILE_NAME));
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

        try (InputStream is = new FileInputStream(tmpOutput);
             OutputStream os = context.getContentResolver().openOutputStream(getUri())) {
            if (os != null) {
                FileUtils.copy(is, os);
            }
        }

        // if the backup was a full backup remember that.
        if (!mIncremental) {
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
     * For an incremental backup: get the date of the last full backup.
     * Returns {@code null} if a full backup is requested.
     *
     * @return date (UTC based), or {@code null} if not in use
     */
    @Nullable
    public LocalDateTime getUtcDateTimeSince() {
        return mFromUtcDateTime;
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


    void setExportEntry(@NonNull final RecordType entry,
                        final boolean isSet) {
        if (isSet) {
            mExportEntries.add(entry);
        } else {
            mExportEntries.remove(entry);
        }
    }

    /**
     * Get the currently selected entry types that will be exported.
     *
     * @return set
     */
    @NonNull
    public Set<RecordType> getExporterEntries() {
        return mExportEntries;
    }


    boolean isIncremental() {
        return mIncremental;
    }

    void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + "mExportEntities=" + mExportEntries
               + ", mUri=" + mUri
               + ", mArchiveEncoding=" + mArchiveEncoding
               + ", mIncremental=" + mIncremental
               + ", mFromUtcDateTime=" + mFromUtcDateTime
               + '}';
    }
}
