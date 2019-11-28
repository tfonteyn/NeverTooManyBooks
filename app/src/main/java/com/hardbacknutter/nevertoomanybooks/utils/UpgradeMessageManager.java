/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Class to manage the message that is displayed when the application is upgraded.
 * <p>
 * The app version is stored in preferences and when there are messages to display, the
 * {@link #getUpgradeMessage} method returns a non-empty string. When the message has been
 * acknowledged by the user, the startup activity should call {@link #setUpgradeAcknowledged}
 * to store the current app version in preferences and so prevent re-display of the messages.
 */
public final class UpgradeMessageManager {

    /**
     * List of version-specific messages.
     */
    private static final int[][] UPGRADE_MESSAGES = {
//            {2, R.string.new_in_101},
    };
    private static final Pattern CR_PATTERN = Pattern.compile("\n", Pattern.LITERAL);

    /** The message generated for this instance; will be set first time it is generated. */
    @Nullable
    private static String sMessage;

    private UpgradeMessageManager() {
    }

    /**
     * Get the upgrade message for the running app instance; caches the result for later use.
     *
     * @param context Current context
     *
     * @return Upgrade message (or blank string)
     */
    @NonNull
    public static String getUpgradeMessage(@NonNull final Context context) {
        // If cached version exists, return it
        if (sMessage != null) {
            return sMessage;
        }

        final StringBuilder message = new StringBuilder();

        long lastVersion = PreferenceManager.getDefaultSharedPreferences(context)
                                            .getLong(StartupViewModel.PREF_STARTUP_LAST_VERSION, 0);

        boolean first = true;
        for (int[] msg : UPGRADE_MESSAGES) {
            if (msg[0] > lastVersion) {
                if (first) {
                    first = false;
                } else {
                    message.append('\n');
                }
                message.append(context.getString(msg[1]));
            }
        }

        sMessage = CR_PATTERN.matcher(message.toString())
                             .replaceAll(Matcher.quoteReplacement("<br/>"));
        return sMessage;
    }

    /**
     * Should be called after the user acknowledged the upgrade dialog message.
     *
     * @param appContext Application context
     */
    public static void setUpgradeAcknowledged(@NonNull final Context appContext) {
        PreferenceManager.getDefaultSharedPreferences(appContext)
                         .edit()
                         .putLong(StartupViewModel.PREF_STARTUP_LAST_VERSION, App.getVersion())
                         .apply();
    }

}
