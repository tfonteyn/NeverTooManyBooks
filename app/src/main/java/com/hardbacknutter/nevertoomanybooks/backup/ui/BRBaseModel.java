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
package com.hardbacknutter.nevertoomanybooks.backup.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Holds the list of {@link BRBaseActivity.FileDetails} for displaying the folder content.
 */
public class BRBaseModel
        extends ViewModel {

    @NonNull
    private final MutableLiveData<ArrayList<BRBaseActivity.FileDetails>>
            mFileDetails = new MutableLiveData<>();
    @Nullable
    private File mRootDir;
    @Nullable
    private ArrayList<BRBaseActivity.FileDetails> mRootDirFiles;
    private final TaskListener<ArrayList<BRBaseActivity.FileDetails>> mFileListerTaskListener =
            new TaskListener<ArrayList<BRBaseActivity.FileDetails>>() {
                @Override
                public void onTaskFinished(
                        @NonNull
                        final TaskFinishedMessage<ArrayList<BRBaseActivity.FileDetails>> message) {
                    //noinspection SwitchStatementWithTooFewBranches
                    switch (message.taskId) {
                        case R.id.TASK_ID_FILE_LISTER:
                            mRootDirFiles = message.result;
                            mFileDetails.setValue(mRootDirFiles);
                            break;

                        default:
                            Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
                            break;
                    }
                }
            };

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        if (mRootDirFiles != null) {
            // got files?, then just repopulate
            mFileDetails.setValue(mRootDirFiles);

        } else {
            // use lastBackupFile as the root directory for the browser.
            String filename = PreferenceManager
                                      .getDefaultSharedPreferences(context)
                                      .getString(BackupManager.PREF_LAST_BACKUP_FILE,
                                                 StorageUtils.getSharedStorage().getAbsolutePath());
            File rootDir = new File(Objects.requireNonNull(filename));
            // Turn the File into a directory if needed
            if (rootDir.isDirectory()) {
                rootDir = new File(rootDir.getAbsolutePath());
            } else {
                rootDir = new File(rootDir.getParent());
            }
            if (!rootDir.exists()) {
                // fall back to default
                rootDir = StorageUtils.getSharedStorage();
            }

            // start the task to get the content
            onPathChanged(rootDir);
        }
    }

    @Nullable
    File getRootDir() {
        return mRootDir;
    }

    @NonNull
    MutableLiveData<ArrayList<BRBaseActivity.FileDetails>> getFileDetails() {
        return mFileDetails;
    }

    /**
     * A new root directory is selected.
     * <p>
     * Rebuild the file list in background.
     *
     * @param rootDir the new root
     */
    void onPathChanged(@NonNull final File rootDir) {
        if (rootDir.isDirectory()) {
            mRootDir = rootDir;
            new FileListerTask(mRootDir, mFileListerTaskListener).execute();
        }
    }

    /**
     * Object to provide a FileListerFragmentTask specific to archive mFileDetails.
     */
    private static class FileListerTask
            extends TaskBase<ArrayList<BRBaseActivity.FileDetails>> {

        /**
         * Perform case-insensitive sorting using system locale
         * (i.e. mFileDetails are system objects).
         */
        private static final Comparator<BRBaseActivity.FileDetails> FILE_DETAILS_COMPARATOR =
                (o1, o2) -> o1.getFile().getName().toLowerCase(App.getSystemLocale())
                              .compareTo(o2.getFile().getName().toLowerCase(App.getSystemLocale()));

        @NonNull
        private final File mRootDir;

        /**
         * Constructor.
         *
         * @param rootDir      folder to list
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        FileListerTask(@NonNull final File rootDir,
                       @NonNull
                       final TaskListener<ArrayList<BRBaseActivity.FileDetails>> taskListener) {
            super(R.id.TASK_ID_FILE_LISTER, taskListener);
            mRootDir = rootDir;
        }

        @Override
        @NonNull
        @WorkerThread
        protected ArrayList<BRBaseActivity.FileDetails> doInBackground(final Void... params) {
            Thread.currentThread().setName("FileListerTask");

            Context context = App.getLocalizedAppContext();

            ArrayList<BRBaseActivity.FileDetails> fileDetails = new ArrayList<>();

            // Filter for directories and our own archives.
            FileFilter fileFilter = file ->
                                            (file.isDirectory() && file.canWrite())
                                            || (file.isFile() && BackupManager.isArchive(file));

            // Get a file list
            File[] files = mRootDir.listFiles(fileFilter);
            if (files == null) {
                return fileDetails;
            }

            for (File file : files) {
                BackupFileDetails backupFileDetails = new BackupFileDetails(file);
                // for each backup archive found, read the info block.
                if (BackupManager.isArchive(file)) {
                    try (BackupReader reader = BackupManager.getReader(context, file)) {
                        backupFileDetails.setInfo(reader.getInfo());
                    } catch (@NonNull final IOException e) {
                        Logger.error(this, e);
                    }
                }
                fileDetails.add(backupFileDetails);
            }

            Collections.sort(fileDetails, FILE_DETAILS_COMPARATOR);
            return fileDetails;
        }
    }
}
