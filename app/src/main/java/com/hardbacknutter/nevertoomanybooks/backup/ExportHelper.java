/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.preference.PreferenceManager;

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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

/**
 * The glue between the ViewModel and the underlying business logic to perform an export/backup.
 */
public class ExportHelper
        extends DataWriterHelperBase<ExportResults> {

    /** Number of app startup's between offers to backup. */
    public static final int BACKUP_COUNTDOWN_DEFAULT = 5;
    /** Triggers prompting for a backup when the countdown reaches 0; then gets reset. */
    public static final String PK_BACKUP_COUNTDOWN = "startup.backupCountdown";
    /** Last full backup/export date. */
    private static final String PK_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /** Log tag. */
    private static final String TAG = "ExportHelper";

    /** <strong>Where</strong> we write to. */
    @Nullable
    private Uri uri;

    /** <strong>How</strong> to write to the Uri. */
    @NonNull
    private ArchiveEncoding encoding;

    /**
     * Constructor.
     */
    public ExportHelper() {
        // set the default
        encoding = ArchiveEncoding.Zip;
        addRecordType(EnumSet.of(RecordType.Styles,
                                 RecordType.Preferences,
                                 RecordType.Certificates,
                                 RecordType.Books,
                                 RecordType.Cover));
    }

    /**
     * Constructor for testing individual options.
     *
     * @param encoding    of the archive we'll be exporting to
     * @param recordTypes to write
     */
    @VisibleForTesting
    public ExportHelper(@NonNull final ArchiveEncoding encoding,
                        @NonNull final Set<RecordType> recordTypes) {
        this.encoding = encoding;
        addRecordType(recordTypes);
    }

    /**
     * Get the type of archive (file) to write to.
     *
     * @return encoding
     */
    @NonNull
    public ArchiveEncoding getEncoding() {
        return encoding;
    }

    public void setEncoding(@NonNull final ArchiveEncoding encoding) {
        this.encoding = encoding;
    }

    @NonNull
    public Uri getUri() {
        return Objects.requireNonNull(uri, "uri");
    }

    public void setUri(@NonNull final Uri uri) {
        this.uri = uri;
    }

    /**
     * Is this a backup or an export.
     *
     * @return {@code true} when this is considered a backup,
     *         {@code false} when it's considered an export.
     */
    public boolean isBackup() {
        return encoding == ArchiveEncoding.Zip;
    }

    /**
     * Get the last time we made a full export in the currently set encoding.
     *
     * @param context Current context
     *
     * @return LocalDateTime(ZoneOffset.UTC), or {@code null} if not set
     */
    @Nullable
    public LocalDateTime getLastDone(@NonNull final Context context) {
        if (isIncremental()) {
            final String key;
            if (encoding == ArchiveEncoding.Zip) {
                // backwards compatibility
                key = PK_LAST_FULL_BACKUP_DATE;
            } else {
                key = PK_LAST_FULL_BACKUP_DATE + "." + encoding.getFileExt();
            }

            final String lastDone = PreferenceManager.getDefaultSharedPreferences(context)
                                                     .getString(key, null);
            if (lastDone != null && !lastDone.isEmpty()) {
                return new ISODateParser(
                        ServiceLocator.getInstance().getSystemLocale()).parse(lastDone);
            }
        }
        return null;
    }

    /**
     * Store the LocalDateTime(ZoneOffset.UTC) of the last full backup/export
     * in the given encoding.
     *
     * @param context Current context
     */
    public void setLastDone(@NonNull final Context context) {
        if (!isIncremental()) {
            final String date = LocalDateTime.now(ZoneOffset.UTC)
                                             .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            final SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .edit();
            if (encoding == ArchiveEncoding.Zip) {
                // backwards compatibility
                editor.putString(PK_LAST_FULL_BACKUP_DATE, date)
                      // reset the startup prompt-counter.
                      .putInt(PK_BACKUP_COUNTDOWN, BACKUP_COUNTDOWN_DEFAULT);
            } else {
                editor.putString(PK_LAST_FULL_BACKUP_DATE + "." + encoding.getFileExt(), date);
            }
            editor.apply();
        }
    }

    @WorkerThread
    @Override
    @NonNull
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   CredentialsException,
                   StorageException,
                   IOException {

        Objects.requireNonNull(uri, "uri");

        final ExportResults results = new ExportResults();

        try {
            dataWriter = encoding.createWriter(context, this);
            results.add(dataWriter.write(context, progressListener));

        } catch (@NonNull final IOException e) {
            // The zip archiver (maybe others as well?) can throw an IOException
            // when the user cancels, so only throw when this is not the case
            if (!progressListener.isCancelled()) {
                FileUtils.delete(getTempFile(context));
                throw e;
            }
        } finally {
            synchronized (this) {
                if (dataWriter != null) {
                    dataWriter.close();
                    dataWriter = null;
                }
            }
        }

        if (!progressListener.isCancelled()) {
            // The output file is now properly closed, export it to the user Uri
            final File tmpOutput = getTempFile(context);
            try (InputStream is = new FileInputStream(tmpOutput);
                 OutputStream os = context.getContentResolver().openOutputStream(uri)) {
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

    @NonNull
    private File getTempFile(@NonNull final Context context) {
        return new File(context.getCacheDir(), TAG + ".tmp");
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + super.toString()
               + ", uri=" + uri
               + ", encoding=" + encoding
               + '}';
    }
}
