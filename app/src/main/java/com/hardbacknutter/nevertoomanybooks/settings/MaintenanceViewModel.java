/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;
import com.hardbacknutter.util.logger.LoggerFactory;

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

    @NonNull
    private final CalculateUsedSpaceTask calculateUsedSpaceTask = new CalculateUsedSpaceTask();
    @NonNull
    private final DebugFileWriterTask debugWriterTask = new DebugFileWriterTask();

    private final MutableLiveData<Boolean> allowPurgeFiles = new MutableLiveData<>();

    @NonNull
    private Collection<Integer> bugReportOptions = BUG_REPORT_OPTIONS_DEFAULT;

    private int debugClicks;
    @NonNull
    private Catastrophe catastrophe = Catastrophe.None;

    @Override
    protected void onCleared() {
        calculateUsedSpaceTask.cancel();
        debugWriterTask.cancel();
        super.onCleared();
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onCalculateUsedSpaceTaskFailure() {
        return calculateUsedSpaceTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<UsedSpaceResult>> onCalculateUsedSpaceTaskCancelled() {
        return calculateUsedSpaceTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<UsedSpaceResult>> onCalculateUsedSpaceTaskFinished() {
        return calculateUsedSpaceTask.onFinished();
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onWriteDebugFailure() {
        return debugWriterTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onWriteDebugCancelled() {
        return debugWriterTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onWriteDebugFinished() {
        return debugWriterTask.onFinished();
    }

    void init(@Nullable final Bundle args) {
        // If we're not (yet) in catastrophe mode, but we have been asked to do so...
        if (catastrophe == Catastrophe.None
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
            setCatastrophe(Catastrophe.Entered);
        }

        // If we're currently in catastrophe mode
        if (catastrophe != Catastrophe.None) {
            // Prevent the user removing any files we might need.
            // We cannot prevent the user doing this when they get in this fragment a second
            // time, but heck...
            allowPurgeFiles.setValue(false);
        }
    }

    @NonNull
    LiveData<Boolean> onAllowPurgeFiles() {
        return allowPurgeFiles;
    }

    @NonNull
    Catastrophe isCatastrophe() {
        return catastrophe;
    }

    void setCatastrophe(@NonNull final Catastrophe catastrophe) {
        this.catastrophe = catastrophe;
        if (catastrophe.isOver()) {
            LoggerFactory.getLogger().w(TAG, "Catastrophe was: " + catastrophe);
        }
    }

    @NonNull
    Collection<Integer> getBugReportOptions() {
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

    /**
     * Start the task to calculate the used space.
     */
    void calculateUsedSpace() {
        calculateUsedSpaceTask.start();
    }

    /**
     * Start the debug report task.
     *
     * @param uri to write to
     */
    void writeDebugFile(@NonNull final Uri uri) {
        debugWriterTask.start(uri, bugReportOptions);
    }

    enum Catastrophe {
        /** Initial/None. */
        None,
        /** We read the arguments and there is a catastrophe to report. */
        Entered,
        /** We're telling the user. */
        Dialog,
        /** The user has created a bugreport. */
        Finished,
        /** The user cancelled the bugreport. */
        Ignored;

        boolean isOver() {
            return this == Finished || this == Ignored;
        }
    }

    private static class CalculateUsedSpaceTask
            extends MTask<UsedSpaceResult> {

        private static final String TAG = "CalculateUsedSpaceTask";

        /** The length of a UUID string. */
        private static final int UUID_LEN = 32;

        CalculateUsedSpaceTask() {
            super(R.id.TASK_ID_USED_SPACE, TAG);
        }

        void start() {
            execute();
        }

        @NonNull
        @Override
        protected UsedSpaceResult doWork()
                throws CancellationException, CoverStorageException {
            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            final CoverStorage coverStorage = serviceLocator.getCoverStorage();

            final FileFilter coverFilter = createCoverFilter();

            final long bytes = FileUtils.getUsedSpace(serviceLocator.getLogDir(), null)
                               + FileUtils.getUsedSpace(serviceLocator.getUpgradesDir(), null)
                               + FileUtils.getUsedSpace(coverStorage.getTempDir(), null)
                               + FileUtils.getUsedSpace(coverStorage.getDir(), coverFilter);
            return new UsedSpaceResult(coverFilter, bytes);
        }

        @NonNull
        private FileFilter createCoverFilter() {
            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            final List<String> bookUuidList = serviceLocator.getBookDao().getBookUuidList();
            final List<String> authorUuidList = serviceLocator.getAuthorDao().getImageUuidList();

            // Filter to check for orphaned cover files
            // Book cover files:
            // 000092d32d7eb79c959821d26ab3efed.jpg
            // 000092d32d7eb79c959821d26ab3efed_1.jpg
            // Other images:
            // 27a69be8-8a85-4c1a-a019-f2825cc98d7c.jpg
            // 27a69be8-8a85-4c1a-a019-f2825cc98d7c_1.jpg
            // not in the list? then we can purge it; i.e. return 'true'
            // not a uuid base filename ? be careful and leave it; i.e. return 'false'
            // Also see the docs in the DBCleaner
            return file -> {
                final int flen = file.getName().length();
                // 4: ".jpg"; 2: "_1"
                if (flen == (UUID_LEN + 4) || flen == (UUID_LEN + 4 + 2)) {
                    // not in the list? then we can purge it
                    return !bookUuidList.contains(file.getName().substring(0, UUID_LEN));
                }
                // extra 4: "-" in between
                if (flen == (UUID_LEN + 4 + 4) || flen == (UUID_LEN + 4 + 4 + 2)) {
                    return !authorUuidList.contains(file.getName().substring(0, UUID_LEN + 4));
                }
                // not a uuid base filename ? be careful and leave it.
                return false;
            };
        }
    }

    /**
     * Value class with the reusable cover filter, and the number of bytes used by purgeable files.
     */
    static class UsedSpaceResult {
        @NonNull
        private final FileFilter coverFilter;
        private final long bytes;

        UsedSpaceResult(@NonNull final FileFilter coverFilter,
                        final long bytes) {
            this.coverFilter = coverFilter;
            this.bytes = bytes;
        }

        @NonNull
        public FileFilter getCoverFilter() {
            return coverFilter;
        }

        public long getBytes() {
            return bytes;
        }
    }

    /**
     * Returns a boolean, always {@code true} as we can't return a {@code Void/null}.
     */
    private static class DebugFileWriterTask
            extends MTask<Boolean> {

        private static final String TAG = "DebugFileWriterTask";
        private Uri uri;
        private Collection<Integer> items;

        DebugFileWriterTask() {
            super(R.id.TASK_ID_EXPORT, TAG);
        }

        void start(@NonNull final Uri uri,
                   @NonNull final Collection<Integer> selectedItems) {
            this.uri = uri;
            this.items = selectedItems;

            execute();
        }

        @NonNull
        @Override
        protected Boolean doWork()
                throws IOException {
            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

            final DebugReport builder = new DebugReport(context)
                    .addDefaultMessage()
                    .addScreenParams();

            if (items.contains(DBG_SEND_DATABASE)) {
                builder.addDatabase();
            }
            if (items.contains(DBG_SEND_DATABASE_UPGRADE)) {
                builder.addDatabaseUpgrades(1);
            }
            if (items.contains(DBG_SEND_LOGFILES)) {
                builder.addLogs(10);
            }
            if (items.contains(DBG_SEND_PREFERENCES)) {
                builder.addPreferences();
            }
            builder.sendToFile(uri);

            return true;
        }
    }
}
