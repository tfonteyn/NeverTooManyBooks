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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupContainer;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.tararchive.TarBackupContainer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

/**
 * Encapsulates the actual container used for backup/restore.
 */
public final class BackupManager {

    /** Log tag. */
    private static final String TAG = "BackupManager";

    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /** Proposed extension for backup files. Not mandatory. */
    private static final String ARCHIVE_EXTENSION = ".ntmb";

    /** Constructor. */
    private BackupManager() {
    }

    /**
     * Create a BackupReader for the specified Uri.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @NonNull
    static BackupReader getReader(@NonNull final Context context,
                                  @NonNull final Uri uri)
            throws InvalidArchiveException, IOException {

        // We only support one backup format; so we use that.
        BackupContainer bkp = new TarBackupContainer(uri);
        // Each format should provide a validator of some kind
        bkp.validate(context);

        return bkp.newReader(context);
    }

    /**
     * Create a BackupWriter for the specified Uri.
     *
     * @param context Current context
     * @param uri     to write to
     *
     * @return a new writer
     *
     * @throws IOException on failure
     */
    @NonNull
    static BackupWriter getWriter(@NonNull final Context context,
                                  @NonNull final Uri uri)
            throws IOException {

        // We only support one backup format; so we use that.
        BackupContainer bkp = new TarBackupContainer(uri);

        return bkp.newWriter(context);
    }

    public static String getDefaultBackupFileName(@NonNull final Context context) {
        return context.getString(R.string.app_name) + '-'
               + DateUtils.localSqlDateForToday()
                          .replace(" ", "-")
                          .replace(":", "")
               + ARCHIVE_EXTENSION;
    }

    /**
     * read the info block and check if we have valid dates.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @return {@code true} if the archive has (or is supposed to have) valid dates
     */
    public static boolean archiveHasValidDates(@NonNull final Context context,
                                               @NonNull final Uri uri) {
        boolean hasValidDates;

        try (BackupReader reader = getReader(context, uri)) {
            BackupInfo info = reader.getInfo();
            reader.close();
            hasValidDates = info.hasValidDates();
        } catch (@NonNull final IOException | InvalidArchiveException e) {
            // InvalidArchiveException is irrelevant here, as we would not have gotten this far
            Logger.error(context, TAG, e);
            hasValidDates = false;
        }
        return hasValidDates;
    }

    /**
     * Store the date of the last full backup ('now') and reset the startup prompt-counter.
     *
     * @param context Current context
     */
    static void setLastFullBackupDate(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(PREF_LAST_FULL_BACKUP_DATE,
                                    DateUtils.utcSqlDateTimeForToday())
                         .putInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN,
                                 Prefs.STARTUP_BACKUP_COUNTDOWN)
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
    static Date getLastFullBackupDate(@NonNull final Context context) {
        String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateUtils.parseSqlDateTime(lastBackup);
        }

        return null;
    }
}
