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

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public final class Backup {

    private static final String TAG = "Backup";

    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";

    private Backup() {
    }

    /**
     * Store the date of the last full backup and reset the startup prompt-counter.
     *
     * @param context Current context
     */
    public static void setLastFullBackupDate(@NonNull final Context context,
                                             @Nullable final LocalDateTime dateTime) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        if (dateTime == null) {
            global.edit()
                  .remove(PREF_LAST_FULL_BACKUP_DATE)
                  .putInt(StartupViewModel.PK_STARTUP_BACKUP_COUNTDOWN,
                          StartupViewModel.STARTUP_BACKUP_COUNTDOWN).apply();
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
        String msg = ExMsg.map(context, TAG, e);

        if (msg == null) {
            // generic storage related IOException message
            if (e instanceof IOException) {
                if (BuildConfig.DEBUG /* always */) {
                    // in debug mode show the raw exception
                    msg = context.getString(R.string.error_unknown)
                          + "\n\n" + e.getLocalizedMessage();
                } else {
                    msg = StandardDialogs
                            .createBadError(context, R.string.error_storage_not_writable);
                }
            }

            // generic unknown message
            if (msg == null || msg.isEmpty()) {
                if (BuildConfig.DEBUG /* always */) {
                    // in debug mode show the raw exception
                    msg = context.getString(R.string.error_unknown);
                    if (e != null) {
                        msg += "\n\n" + e.getLocalizedMessage();
                    }
                } else {
                    // when not in debug, ask for feedback
                    msg = StandardDialogs.createBadError(context, R.string.error_unknown);
                }
            }
        }

        return msg;
    }

}
