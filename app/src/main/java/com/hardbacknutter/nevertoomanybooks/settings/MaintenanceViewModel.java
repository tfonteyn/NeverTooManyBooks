/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
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
     * MUST be the same order as the labels list in
     * {@link MaintenanceFragment}#onCreateBugReport.
     */
    static final List<Integer> BUG_REPORT_OPTIONS_ALL = List.of(
            DBG_SEND_DATABASE,
            DBG_SEND_DATABASE_UPGRADE,
            DBG_SEND_LOGFILES,
            DBG_SEND_PREFERENCES);

    private static final Collection<Integer> BUG_REPORT_OPTIONS_DEFAULT = Set.of(
            DBG_SEND_LOGFILES,
            DBG_SEND_PREFERENCES);

    private static final String TAG = "MaintenanceViewModel";
    /** Trigger the bug-report dialog when started. */
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_CREATE_REPORT = TAG + ":bug";
    /**
     * After clicking the debug category header 3 times, we display the debug options.
     * SQLite shell updates are not allowed.
     */
    private static final int DEBUG_CLICKS = 3;
    /** After clicking the header 3 more times, the SQLite shell will allow updates. */
    private static final int DEBUG_CLICKS_ALLOW_SQL_UPDATES = 6;

    private final MutableLiveData<Boolean> allowPurgeFiles = new MutableLiveData<>();

    @NonNull
    private Collection<Integer> bugReportOptions = BUG_REPORT_OPTIONS_DEFAULT;

    private int debugClicks;

    @Nullable
    private Boolean catastrophe;

    void init(@Nullable final Bundle args) {

        // If we're not (yet) in catastrophe mode, but we have been asked to do so...
        if (catastrophe == null
            && args != null && args.containsKey(BKEY_CREATE_REPORT)) {
            // prep the requested options
            final Collection<Integer> options = args.getIntegerArrayList(
                    MaintenanceViewModel.BKEY_CREATE_REPORT);
            if (options != null) {
                bugReportOptions = options;
            } else {
                // We expect the worst has happened and want ALL the info we can get.
                // But as always, the user WILL be able to disable anything
                // they do want to send us of course.
                bugReportOptions = MaintenanceViewModel.BUG_REPORT_OPTIONS_ALL;
            }
            // and enter catastrophe mode
            catastrophe = true;
        }

        // If we're currently in catastrophe mode
        if (catastrophe != null && catastrophe) {
            // Prevent the user removing any files we might need.
            // We cannot prevent the user doing this when they get in this fragment a second
            // time, but heck...
            allowPurgeFiles.setValue(false);
        }
    }

    @NonNull
    MutableLiveData<Boolean> onAllowPurgeFiles() {
        return allowPurgeFiles;
    }

    public boolean isCatastrophe() {
        if (catastrophe == null) {
            return false;
        }
        // return true, but switch to false for subsequent calls
        final boolean tmp = catastrophe;
        catastrophe = false;
        return tmp;
    }

    @NonNull
    public Collection<Integer> getBugReportOptions() {
        return bugReportOptions;
    }

    void setBugReportOptions(@NonNull final Collection<Integer> selectedItems) {
        this.bugReportOptions = selectedItems;
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

        final DebugReport builder = new DebugReport(context)
                .addDefaultMessage()
                .addScreenParams();

        if (bugReportOptions.contains(DBG_SEND_DATABASE)) {
            builder.addDatabase();
        }
        if (bugReportOptions.contains(DBG_SEND_DATABASE_UPGRADE)) {
            builder.addDatabaseUpgrades(1);
        }
        if (bugReportOptions.contains(DBG_SEND_LOGFILES)) {
            builder.addLogs(10);
        }
        if (bugReportOptions.contains(DBG_SEND_PREFERENCES)) {
            builder.addPreferences();
        }
        builder.sendToFile(uri);
    }
}
