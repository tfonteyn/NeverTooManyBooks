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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public final class Backup {

    private static final String TAG = "Backup";

    /** Last full BACKUP date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";

    private Backup() {
    }

    /**
     * Store the date of the last full backup and reset the startup prompt-counter.
     */
    public static void setLastFullBackupDate(@Nullable final LocalDateTime dateTime) {
        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        if (dateTime == null) {
            global.edit()
                  .remove(PREF_LAST_FULL_BACKUP_DATE)
                  .putInt(StartupViewModel.PK_STARTUP_BACKUP_COUNTDOWN,
                          StartupViewModel.STARTUP_BACKUP_COUNTDOWN)
                  .apply();
        } else {
            global.edit()
                  .putString(PREF_LAST_FULL_BACKUP_DATE, dateTime
                          .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                  .putInt(StartupViewModel.PK_STARTUP_BACKUP_COUNTDOWN,
                          StartupViewModel.STARTUP_BACKUP_COUNTDOWN)
                  .apply();
        }
    }

    /**
     * Get the last time we made a full backup.
     *
     * @param context Current context
     *
     * @return Date in the UTC timezone.
     */
    @Nullable
    public static LocalDateTime getLastFullBackupDate(@NonNull final Context context) {
        final String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                                   .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateParser.getInstance(context).parseISO(lastBackup);
        }

        return null;
    }

    /**
     * Store the date of the last full export in the given format.
     */
    public static void setLastFullExportDate(@NonNull final ArchiveEncoding encoding,
                                             @Nullable final LocalDateTime dateTime) {

        final String key = PREF_LAST_FULL_BACKUP_DATE + encoding.getFileExt();

        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        if (dateTime == null) {
            global.edit().remove(key).apply();
        } else {
            global.edit()
                  .putString(key, dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                  .apply();
        }
    }

    /**
     * Get the last time we made a full export in the given format.
     *
     * @param context Current context
     *
     * @return Date in the UTC timezone.
     */
    @Nullable
    public static LocalDateTime getLastFullExportDate(@NonNull final Context context,
                                                      @NonNull final ArchiveEncoding encoding) {

        final String key = PREF_LAST_FULL_BACKUP_DATE + encoding.getFileExt();

        final String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                                   .getString(key, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateParser.getInstance(context).parseISO(lastBackup);
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
