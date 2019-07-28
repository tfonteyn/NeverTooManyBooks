/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;

/**
 * Class to manage the message that is displayed when the application is upgraded.
 * <p>
 * The app version is stored in preferences and when there are messages to display, the
 * {@link #getUpgradeMessage} method returns a non-empty string. When the message has been
 * acknowledged by the user, the startup activity should call {@link #setUpgradeAcknowledged()}
 * to store the current app version in preferences and so prevent re-display of the messages.
 */
public final class UpgradeMessageManager {

    /**
     * List of version-specific messages.
     */
    private static final int[][] UPGRADE_MESSAGES = {
            {200, R.string.new_in_600},
            };

    /** The message generated for this instance; will be set first time it is generated. */
    @Nullable
    private static String sMessage;

    private UpgradeMessageManager() {
    }

    /**
     * Get the upgrade message for the running app instance; caches the result for later use.
     *
     * @param context Current context for accessing resources.
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

        // See if we have a saved version id; if it's 0, it's an upgrade from a pre-98 install.
        long lastVersion = App.getPrefs().getLong(StartupActivity.PREF_STARTUP_LAST_VERSION, 0);

        boolean first = true;
        for (int[] msg : UPGRADE_MESSAGES) {
            if (msg[0] > lastVersion) {
                if (!first) {
                    message.append('\n');
                }
                first = false;
                message.append(context.getString(msg[1]));
            }
        }

        sMessage = message.toString().replace("\n", "<br/>");
        return sMessage;
    }

    /**
     * Should be called after the user acknowledged the upgrade dialog message.
     */
    public static void setUpgradeAcknowledged() {
        App.getPrefs()
           .edit()
           .putLong(StartupActivity.PREF_STARTUP_LAST_VERSION, getVersion())
           .apply();
    }

    /**
     * Reads the application version from the manifest.
     *
     * @return the versionCode.
     */
    private static long getVersion() {
        // versionCode deprecated and new method in API: 28, till then ignore...
        PackageInfo packageInfo = App.getPackageInfo(0);
        if (packageInfo != null) {
            return (long) packageInfo.versionCode;
        }
        return 0;
    }
}
