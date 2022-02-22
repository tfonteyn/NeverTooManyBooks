/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Writes to a temporary file in the internal cache first.
 */
public class ExportHelper {

    /** Number of app startup's between offers to backup. */
    public static final int BACKUP_COUNTDOWN_DEFAULT = 5;
    /** Triggers prompting for a backup when the countdown reaches 0; then gets reset. */
    public static final String PK_BACKUP_COUNTDOWN = "startup.backupCountdown";
    /** Last full backup/export date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /** Log tag. */
    private static final String TAG = "ExportHelper";

    /** What is going to be exported. */
    @NonNull
    private final Set<RecordType> mRecordTypes;
    /** <strong>Where</strong> we write to. */
    @Nullable
    private Uri mUri;
    /** <strong>How</strong> to write to the Uri. */
    @NonNull
    private ArchiveEncoding mEncoding = ArchiveEncoding.Zip;


    /**
     * Do an incremental export. Definition of incremental depends on the writer.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated</li>
     * </ul>
     */
    private boolean mIncremental;

    /**
     * Constructor.
     */
    public ExportHelper() {
        mRecordTypes = EnumSet.allOf(RecordType.class);
    }

    /**
     * Constructor for testing individual options.
     *
     * @param recordTypes to write
     */
    @VisibleForTesting
    public ExportHelper(@NonNull final RecordType... recordTypes) {
        mRecordTypes = EnumSet.copyOf(Arrays.asList(recordTypes));
    }

    @NonNull
    public ArchiveEncoding getEncoding() {
        return mEncoding;
    }

    public void setEncoding(@NonNull final ArchiveEncoding encoding) {
        mEncoding = encoding;
    }

    /**
     * Is this a backup or an export.
     *
     * @return {@code true} when this is considered a backup,
     * {@code false} when it's considered an export.
     */
    public boolean isBackup() {
        return mEncoding == ArchiveEncoding.Zip;
    }

    @NonNull
    public Uri getUri() {
        return Objects.requireNonNull(mUri, "mUri");
    }

    public void setUri(@NonNull final Uri uri) {
        mUri = uri;
    }

    void setRecordType(@NonNull final RecordType recordType,
                       final boolean isSet) {
        if (isSet) {
            mRecordTypes.add(recordType);
        } else {
            mRecordTypes.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getRecordTypes() {
        return mRecordTypes;
    }


    public boolean isIncremental() {
        return mIncremental;
    }

    void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }

    /**
     * Get the last time we made a full export in the currently set encoding.
     *
     * @return LocalDateTime(ZoneOffset.UTC), or {@code null} if not set
     */
    @Nullable
    public LocalDateTime getLastDone() {
        if (mIncremental) {
            final String key;
            if (mEncoding == ArchiveEncoding.Zip) {
                // backwards compatibility
                key = PREF_LAST_FULL_BACKUP_DATE;
            } else {
                key = PREF_LAST_FULL_BACKUP_DATE + mEncoding.getFileExt();
            }

            final String lastDone = ServiceLocator.getGlobalPreferences().getString(key, null);
            if (lastDone != null && !lastDone.isEmpty()) {
                return new ISODateParser().parse(lastDone);
            }
        }
        return null;
    }

    /**
     * Store the LocalDateTime(ZoneOffset.UTC) of the last full backup/export
     * in the given encoding.
     */
    public void setLastDone() {
        if (!mIncremental) {
            final String date = LocalDateTime.now(ZoneOffset.UTC)
                                             .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            final SharedPreferences.Editor editor = ServiceLocator.getGlobalPreferences()
                                                                  .edit();
            if (mEncoding == ArchiveEncoding.Zip) {
                // backwards compatibility
                editor.putString(PREF_LAST_FULL_BACKUP_DATE, date)
                      // reset the startup prompt-counter.
                      .putInt(PK_BACKUP_COUNTDOWN, BACKUP_COUNTDOWN_DEFAULT);
            } else {
                editor.putString(PREF_LAST_FULL_BACKUP_DATE + mEncoding.getFileExt(), date);
            }
            editor.apply();
        }
    }

    @NonNull
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws ExportException, IOException, StorageException {

        Objects.requireNonNull(mUri, "mUri");
        SanityCheck.requireValue(mRecordTypes, "mExportEntries");

        final ExportResults results = new ExportResults();

        try (ArchiveWriter writer = mEncoding.createWriter(context, this)) {
            results.add(writer.write(context, progressListener));
        } catch (@NonNull final IOException e) {
            // The zip archiver (maybe others as well?) can throw an IOException
            // when the user cancels, so only throw when this is not the case
            if (!progressListener.isCancelled()) {
                FileUtils.delete(getTempFile(context));
                throw e;
            }
        }

        if (!progressListener.isCancelled()) {
            // The output file is now properly closed, export it to the user Uri
            final File tmpOutput = getTempFile(context);
            try (InputStream is = new FileInputStream(tmpOutput);
                 OutputStream os = context.getContentResolver().openOutputStream(mUri)) {
                if (os != null) {
                    FileUtils.copy(is, os);
                }
            }
        }

        FileUtils.delete(getTempFile(context));
        return results;
    }


    /**
     * Create/get the OutputStream to write to.
     *
     * @param context Current context
     *
     * @return FileOutputStream
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    public FileOutputStream createOutputStream(@NonNull final Context context)
            throws FileNotFoundException {
        return new FileOutputStream(getTempFile(context));
    }

    private File getTempFile(@NonNull final Context context) {
        return new File(context.getCacheDir(), TAG + ".tmp");
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + "mRecordTypes=" + mRecordTypes
               + ", mUri=" + mUri
               + ", mEncoding=" + mEncoding
               + ", mIncremental=" + mIncremental
               + '}';
    }
}
