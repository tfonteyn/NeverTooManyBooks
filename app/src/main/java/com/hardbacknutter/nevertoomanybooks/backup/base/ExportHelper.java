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
import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public class ExportHelper {

    /**
     * all defined flags.
     */
    private static final int MASK = Options.ENTITIES | Options.IS_SYNC;
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
     * Contains the user selected options before doing the export.
     * After the export, reflects the entities actually exported.
     */
    @Options.Bits
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
    public ExportHelper(@Options.Bits final int options) {
        mOptions = options;
    }

    /** Called from the dialog via its View listeners. */
    public void setOption(@Options.Bits final int optionBit,
                          final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }

    @NonNull
    ArchiveContainer getArchiveContainer() {
        if (mArchiveContainer == null) {
            // use the default
            return ArchiveContainer.Zip;
        }
        return mArchiveContainer;
    }

    public void setArchiveContainer(@NonNull final ArchiveContainer archiveContainer) {
        mArchiveContainer = archiveContainer;
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
     * Get the date-since.
     *
     * @return date (UTC based), or {@code null} if not in use
     */
    @Nullable
    public LocalDateTime getUtcDateTimeSince() {
        if ((mOptions & Options.IS_SYNC) != 0) {
            return mFromUtcDateTime;
        } else {
            return null;
        }
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
    ArchiveWriter getArchiveWriter(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        SanityCheck.requirePositiveValue(mOptions & MASK, "mOptions");
        Objects.requireNonNull(mUri, "uri");

        if ((mOptions & Options.IS_SYNC) != 0) {
            mFromUtcDateTime = getLastFullBackupDate(context);
        } else {
            mFromUtcDateTime = null;
        }

        switch (getArchiveContainer()) {
            case Xml:
                return new XmlArchiveWriter(context, this);

            case CsvBooks:
                return new CsvArchiveWriter(this);

            case Tar:
                return new TarArchiveWriter(context, this);

            case SqLiteDb:
                return new DbArchiveWriter(this);

            case Unknown:
                throw new InvalidArchiveException(String.valueOf(getArchiveContainer()));

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

    @NonNull
    public ExportResults getResults() {
        return Objects.requireNonNull(mResults, "mResults");
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
        if ((mOptions & Options.IS_SYNC) != 0) {
            setLastFullBackupDate(context);
        }
    }

    /**
     * Should be called after a failed write.
     *
     * @param context Current context
     */
    void onCleanup(@NonNull final Context context) {
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
        final String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                                   .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateParser.getInstance(context).parseISO(lastBackup);
        }

        return null;
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


    public boolean isOptionSet(@Options.Bits final int optionBit) {
        return (mOptions & optionBit) != 0;
    }

    @Options.Bits
    public int getOptions() {
        return mOptions;
    }

    /**
     * Should be called <strong>after</strong> the export to set what was actually exported.
     *
     * @param options set
     */
    public void setOptions(@Options.Bits final int options) {
        mOptions = options;
    }

    /**
     * Check if there any options set that will cause us to export anything.
     *
     * @return {@code true} if something will be exported
     */
    public boolean hasEntityOption() {
        return (mOptions & Options.ENTITIES) != 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + ", mOptions=0b" + Integer.toBinaryString(mOptions)
               + ", mOptions=" + Options.toString(mOptions)
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveContainer
               + ", mFromUtcDateTime=" + mFromUtcDateTime
               + ", mResults=" + mResults
               + '}';
    }

    /**
     * Options as to what should be exported.
     * Not all implementations will support all options.
     * <p>
     * The bit numbers are not stored.
     */
    public static final class Options {

        public static final int NOTHING = 0;
        public static final int INFO = 1;

        public static final int BOOKS = 1 << 1;
        public static final int COVERS = 1 << 2;

        public static final int PREFS = 1 << 8;
        public static final int STYLES = 1 << 9;

        /**
         * All entity types which can be written/read.
         * This does not include INFO nor IS_SYNC.
         */
        public static final int ENTITIES = BOOKS | COVERS | PREFS | STYLES;

        /**
         * Do a sync.
         * <ul>
         *     <li>0: all books</li>
         *     <li>1: books added/updated since last backup</li>
         * </ul>
         */
        public static final int IS_SYNC = 1 << 16;

        /** DEBUG. */
        public static String toString(@Bits final int options) {
            final StringJoiner sj = new StringJoiner(",", "Options{", "}");
            if ((options & INFO) != 0) {
                sj.add("INFO");
            }
            if ((options & BOOKS) != 0) {
                sj.add("BOOKS");
            }
            if ((options & COVERS) != 0) {
                sj.add("COVERS");
            }
            if ((options & PREFS) != 0) {
                sj.add("PREFS");
            }
            if ((options & STYLES) != 0) {
                sj.add("STYLES");
            }
            if ((options & IS_SYNC) != 0) {
                sj.add("IS_SYNC");
            }
            return sj.toString();
        }

        @IntDef(flag = true, value = {INFO, BOOKS, COVERS, PREFS, STYLES, IS_SYNC})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Bits {

        }
    }
}
