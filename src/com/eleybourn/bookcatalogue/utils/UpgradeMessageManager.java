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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;

/**
 * Class to manage the message that is displayed when the application is upgraded.
 * <p>
 * The app version is stored in preferences and when there are messages to display, the
 * {@link #getUpgradeMessage()} method returns a non-empty string. When the message has been
 * acknowledged by the user, the startup activity should call {@link #setUpgradeAcknowledged()}
 * to store the current app version in preferences and so prevent re-display of the messages.
 */
public final class UpgradeMessageManager {

    /**
     * List of version-specific messages.
     * 2019-01-18: messages up to 3.x deleted.
     * Message 4.0.0 to 4.1 moved to the resource file, and added here.
     */
    private static final int[][] UPGRADE_MESSAGES = {
            // 4.0.0 is from mar/apr 2012 and was database version 72/73
            {76, R.string.new_in_400},
            {78, R.string.new_in_401},
            {81, R.string.new_in_403},
            {84, R.string.new_in_404},
            {88, R.string.new_in_406},
            {92, R.string.new_in_410},
            {124, R.string.new_in_420},
            {125, R.string.new_in_421},
            {126, R.string.new_in_422},
            {128, R.string.new_in_423},
            {134, R.string.new_in_424},

            // 5.0.0 is from Feb/Mar 2013 and was database version 80
            {142, R.string.new_in_500},
            {145, R.string.new_in_502},
            {146, R.string.new_in_503},
            {147, R.string.new_in_504},
            {149, R.string.new_in_505},
            {152, R.string.new_in_508},
            {154, R.string.new_in_509},
            {162, R.string.new_in_510},
            {166, R.string.new_in_511},
            {171, R.string.new_in_520},
            {179, R.string.new_in_522},

            {200, R.string.new_in_600},
            };

    /** The message generated for this instance; will be set first time it is generated. */
    @Nullable
    private static String mMessage;

    private UpgradeMessageManager() {
    }

    /**
     * Get the upgrade message for the running app instance; caches the result for later use.
     *
     * @return Upgrade message (or blank string)
     */
    @NonNull
    public static String getUpgradeMessage() {
        // If cached version exists, return it
        if (mMessage != null) {
            return mMessage;
        }

        // Builder for message
        final StringBuilder message = new StringBuilder();

        // See if we have a saved version id; if it's 0, it's an upgrade from a pre-98 install.
        long lastVersion = Prefs.getPrefs().getLong(StartupActivity.PREF_STARTUP_LAST_VERSION, 0);

        boolean first = true;
        for (int[] msg : UPGRADE_MESSAGES) {
            if (msg[0] > lastVersion) {
                if (!first) {
                    message.append('\n');
                }
                first = false;
                message.append(BookCatalogueApp.getResString(msg[1]));
            }
        }

        mMessage = message.toString().replace("\n", "<br/>");
        return mMessage;
    }

    public static void setUpgradeAcknowledged() {
            Prefs.getPrefs()
                 .edit()
                 .putLong(StartupActivity.PREF_STARTUP_LAST_VERSION, BookCatalogueApp.getVersion())
                 .apply();
    }
}
