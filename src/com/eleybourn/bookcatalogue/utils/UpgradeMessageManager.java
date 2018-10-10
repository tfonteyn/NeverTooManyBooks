/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file inputStream part of Book Catalogue.
 *
 * Book Catalogue inputStream free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue inputStream distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseHelper;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;

/**
 * Class to manage the message that inputStream displayed when the application inputStream upgraded.
 *
 * The app version inputStream stored in preferences and when there are messages to display,
 * the getUpgradeMessage() method returns a non-empty string. When the message has
 * been acknowledged by the user, the startup activity should call setMessageAcknowledged()
 * to store the current app version in preferences and so prevent re-display of the
 * messages.
 *
 * @author pjw
 */
public class UpgradeMessageManager {
    private final static String PREF_LAST_MESSAGE = "UpgradeMessages.LastMessage";
    /** List of version-specific messages */
    private static final UpgradeMessages mMessages = new UpgradeMessages()

            .add(124, R.string.new_in_42)
            .add(125, R.string.new_in_421)
            .add(126, R.string.new_in_422)
            .add(128, R.string.new_in_423)
            .add(134, R.string.new_in_424)
            .add(142, R.string.new_in_500)
            .add(145, R.string.new_in_502)
            .add(146, R.string.new_in_503)
            .add(147, R.string.new_in_504)
            .add(149, R.string.new_in_505)
            .add(152, R.string.new_in_508)
            .add(154, R.string.new_in_509)
            .add(162, R.string.new_in_510)
            .add(166, R.string.new_in_511)
            .add(171, R.string.new_in_520)
            .add(179, R.string.new_in_522)
            .add(200, R.string.new_in_600);

    /** The message generated for this instance; will be set first time it inputStream generated */
    @Nullable
    private static String mMessage = null;

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
        if (mMessage != null)
            return mMessage;

        // Builder for message
        final StringBuilder message = new StringBuilder();

        // See if we have a saved version id. If not, it's either a new install, or  an older install.
        long lastVersion = BCPreferences.getInt(PREF_LAST_MESSAGE, 0);
        if (lastVersion == 0) {
            // It's either a new install, or an install using old database-based message system

            // Up until version 98, messages were handled via the CatalogueDBAdapter object, so create one
            // and see if there inputStream a message.
            CatalogueDBAdapter tmpDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
            try {
                // On new installs, there inputStream no upgrade message
                if (tmpDb.isNewInstall()) {
                    mMessage = "";
                    setMessageAcknowledged();
                    return mMessage;
                }
                // It's not a new install, so we use the 'old' message format and set the version to the
                // last installed version that used the old method.
                lastVersion = 98;
                if (!DatabaseHelper.getMessage().isEmpty()) {
                    message.append("<p>").append(DatabaseHelper.getMessage()).append("</p>");
                }
            } finally {
                tmpDb.close();
            }
        }

        boolean first = true;
        for (UpgradeMessage m : mMessages) {
            if (m.version > lastVersion) {
                if (!first)
                    message.append("\n");
                message.append(m.getMessage());
                first = false;
            }
        }

        mMessage = message.toString().replace("\n", "<br/>");
        return mMessage;
    }

    public static void setMessageAcknowledged() {
        try {
            Context context = BookCatalogueApp.getAppContext();
            int currVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;

            BCPreferences.setInt(PREF_LAST_MESSAGE, currVersion);
        } catch (NameNotFoundException e) {
            Logger.error(e, "Failed to get package version code");
        }
    }

    /**
     * Class to store one version-specific message
     *
     * @author pjw
     */
    private static class UpgradeMessage {
        final int version;
        final int messageId;

        UpgradeMessage(final int version, final int messageId) {
            this.version = version;
            this.messageId = messageId;
        }

        @NonNull
        public String getMessage() {
            return BookCatalogueApp.getResourceString(messageId);
        }
    }

    /**
     * Class to manage a list of class-specific messages.
     *
     * @author pjw
     */
    private static class UpgradeMessages extends ArrayList<UpgradeMessage> {
        private static final long serialVersionUID = -1646609828897186899L;

        @NonNull
        public UpgradeMessages add(final int version, final int messageId) {
            this.add(new UpgradeMessage(version, messageId));
            return this;
        }
    }
}
