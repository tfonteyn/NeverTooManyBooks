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
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBHelper;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;

/**
 * Class to manage the message that is displayed when the application is upgraded.
 *
 * The app version is stored in preferences and when there are messages to display, the
 * {@link #getUpgradeMessage()} method returns a non-empty string. When the message has been
 * acknowledged by the user, the startup activity should call {@link #setUpgradeAcknowledged()}
 * to store the current app version in preferences and so prevent re-display of the messages.
 *
 * @author pjw
 */
public class UpgradeMessageManager {
    /**
     * the string is misleading (but backwards compatibility rules), this is actually the
     * 'LastVersion' e.g. the version which was installed before the current one.
     */
    private final static String PREF_PREVIOUS_VERSION = "UpgradeMessages.LastMessage";
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

    /** The message generated for this instance; will be set first time it is generated */
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

        // See if we have a saved version id. If not, it's an pre-98 install.
        long lastVersion = BookCatalogueApp.getIntPreference(PREF_PREVIOUS_VERSION, 0);
        if (lastVersion == 0) {
            // It was an old install using old database-based message system
            // set the version to the last installed version that used the old method.
            lastVersion = 98;
            if (!CatalogueDBHelper.getMessage().isEmpty()) {
                message.append("<p>").append(CatalogueDBHelper.getMessage()).append("</p>");
            }
        }

        boolean first = true;
        for (UpgradeMessage msg : mMessages) {
            if (msg.version > lastVersion) {
                if (!first)
                    message.append("\n");
                message.append(msg.getLocalizedMessage());
                first = false;
            }
        }

        mMessage = message.toString().replace("\n", "<br/>");
        return mMessage;
    }

    public static void setUpgradeAcknowledged() {
        try {
            Context context = BookCatalogueApp.getAppContext();
            int currVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;

            BookCatalogueApp.getSharedPreferences().edit().putInt(PREF_PREVIOUS_VERSION, currVersion).apply();
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
        public String getLocalizedMessage() {
            return BookCatalogueApp.getResourceString(messageId);
        }
    }

    /**
     * Class to manage a list of class-specific messages.
     *
     * @author pjw
     */
    private static class UpgradeMessages extends ArrayList<UpgradeMessage> {
        @NonNull
        public UpgradeMessages add(final int version, final int messageId) {
            this.add(new UpgradeMessage(version, messageId));
            return this;
        }
    }
}
