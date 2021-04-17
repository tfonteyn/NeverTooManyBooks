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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

public final class Backup {

    /** Triggers prompting for a backup when the countdown reaches 0; then gets reset. */
    public static final String PK_BACKUP_COUNTDOWN = "startup.backupCountdown";

    /** Number of app startup's between offers to backup. */
    public static final int BACKUP_COUNTDOWN_DEFAULT = 5;

    private static final String TAG = "Backup";

    /** Last full BACKUP date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";

    private Backup() {
    }

    /**
     * Store the LocalDateTime(ZoneOffset.UTC) of the last full backup
     * and reset the startup prompt-counter.
     */
    public static void setLastFullBackupDate() {
        ServiceLocator.getGlobalPreferences()
                      .edit()
                      .putInt(PK_BACKUP_COUNTDOWN, BACKUP_COUNTDOWN_DEFAULT)
                      .putString(PREF_LAST_FULL_BACKUP_DATE,
                                 LocalDateTime.now(ZoneOffset.UTC)
                                              .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                      .apply();
    }

    /**
     * Get the last time we made a full backup.
     *
     * @return LocalDateTime(ZoneOffset.UTC), or {@code null} if not set
     */
    @Nullable
    public static LocalDateTime getLastFullBackupDate() {
        return getDate(PREF_LAST_FULL_BACKUP_DATE);
    }


    /**
     * Store the LocalDateTime(ZoneOffset.UTC) of the last full export in the given encoding.
     */
    public static void setLastFullExportDate(@NonNull final ArchiveEncoding encoding) {
        ServiceLocator.getGlobalPreferences()
                      .edit()
                      .putString(PREF_LAST_FULL_BACKUP_DATE + encoding.getFileExt(),
                                 LocalDateTime.now(ZoneOffset.UTC)
                                              .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                      .apply();
    }

    /**
     * Get the last time we made a full export in the given format.
     *
     * @return LocalDateTime(ZoneOffset.UTC), or {@code null} if not set
     */
    @Nullable
    public static LocalDateTime getLastFullExportDate(@NonNull final ArchiveEncoding encoding) {
        return getDate(PREF_LAST_FULL_BACKUP_DATE + encoding.getFileExt());
    }

    /**
     * Read the given date key from SharedPreferences.
     *
     * @param key to use
     *
     * @return LocalDateTime(ZoneOffset.UTC), or {@code null} if not set
     */
    @Nullable
    private static LocalDateTime getDate(@NonNull final String key) {
        final String lastBackup = ServiceLocator.getGlobalPreferences().getString(key, null);
        if (lastBackup != null && !lastBackup.isEmpty()) {
            return new ISODateParser().parse(lastBackup);
        }

        return null;
    }

    /**
     * Transform a failure into a user friendly report.
     * <p>
     * This method check exceptions for <strong>both reader and writers</strong>.
     *
     * @param e error exception
     *
     * @return report string
     */
    @NonNull
    static String createErrorReport(@NonNull final Context context,
                                    @Nullable final Exception e) {
        final String msg = ExMsg.map(context, TAG, e);
        if (msg != null) {
            return msg;
        }

        return ExMsg.ioExFallbackMsg(context, e, context.getString(
                R.string.error_storage_not_writable));
    }
}
