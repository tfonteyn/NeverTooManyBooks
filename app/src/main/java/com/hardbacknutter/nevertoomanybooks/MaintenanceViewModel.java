/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;

@SuppressWarnings("WeakerAccess")
public class MaintenanceViewModel
        extends ViewModel {

    static final int DBG_SEND_DATABASE = 0;
    static final int DBG_SEND_DATABASE_UPGRADE = 1;
    static final int DBG_SEND_LOGFILES = 2;
    static final int DBG_SEND_PREFERENCES = 3;

    /**
     * After clicking the debug category header 3 times, we display the debug options.
     * SQLite shell updates are not allowed.
     */
    private static final int DEBUG_CLICKS = 3;
    /** After clicking the header 3 more times, the SQLite shell will allow updates. */
    private static final int DEBUG_CLICKS_ALLOW_SQL_UPDATES = 6;

    @Nullable
    private Set<Integer> selectedItems;

    private int debugClicks;

    void setDebugSelection(@NonNull final Set<Integer> selectedItems) {
        this.selectedItems = selectedItems;
    }

    void incDebugClicks() {
        debugClicks++;
    }

    boolean isShowDbgOptions() {
        return debugClicks >= DEBUG_CLICKS;
    }

    boolean isDebugSqLiteAllowsUpdates() {
        return debugClicks >= DEBUG_CLICKS_ALLOW_SQL_UPDATES;
    }

    void sendDebug(@NonNull final Context context,
                   @NonNull final Uri uri)
            throws IOException {
        Objects.requireNonNull(selectedItems);

        final DebugReport builder = new DebugReport(context)
                .addDefaultMessage()
                .addScreenParams();

        if (selectedItems.contains(DBG_SEND_DATABASE)) {
            builder.addDatabase();
        }
        if (selectedItems.contains(DBG_SEND_DATABASE_UPGRADE)) {
            builder.addDatabaseUpgrades(1);
        }
        if (selectedItems.contains(DBG_SEND_LOGFILES)) {
            builder.addLogs(10);
        }
        if (selectedItems.contains(DBG_SEND_PREFERENCES)) {
            builder.addPreferences();
        }
        builder.sendToFile(uri);
    }
}
