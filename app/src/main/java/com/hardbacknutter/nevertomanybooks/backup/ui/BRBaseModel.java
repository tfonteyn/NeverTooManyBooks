package com.hardbacknutter.nevertomanybooks.backup.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertomanybooks.backup.ui.BRBaseActivity;
import com.hardbacknutter.nevertomanybooks.backup.ui.BackupFileDetails;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

/**
 * Holds the list of {@link BRBaseActivity.FileDetails} for displaying the folder content.
 */
public class BRBaseModel
        extends ViewModel {

    @NonNull
    private final MutableLiveData<ArrayList<BRBaseActivity.FileDetails>>
            mFileDetails = new MutableLiveData<>();

    private final TaskListener<ArrayList<BRBaseActivity.FileDetails>> mFileListerTaskListener =
            new TaskListener<ArrayList<BRBaseActivity.FileDetails>>() {
                @Override
                public void onTaskFinished(@NonNull final TaskFinishedMessage<ArrayList<BRBaseActivity.FileDetails>> message) {
                    //noinspection SwitchStatementWithTooFewBranches
                    switch (message.taskId) {
                        case R.id.TASK_ID_FILE_LISTER:
                            mRootDirFiles = message.result;
                            mFileDetails.setValue(mRootDirFiles);
                            break;

                        default:
                            Logger.warnWithStackTrace(this, "Unknown taskId=" + message.taskId);
                            break;
                    }
                }
            };

    @Nullable
    private File mRootDir;
    @Nullable
    private ArrayList<BRBaseActivity.FileDetails> mRootDirFiles;

    public void init(@NonNull final Context context) {
        if (mRootDirFiles != null) {
            // got files?, then just repopulate
            mFileDetails.setValue(mRootDirFiles);

        } else {
            // use lastBackupFile as the root directory for the browser.
            String filename = PreferenceManager.getDefaultSharedPreferences(context)
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
    public File getRootDir() {
        return mRootDir;
    }

    @NonNull
    public MutableLiveData<ArrayList<BRBaseActivity.FileDetails>> getFileDetails() {
        return mFileDetails;
    }

    /**
     * A new root directory is selected.
     * <p>
     * Rebuild the file list in background.
     *
     * @param rootDir the new root
     */
    public void onPathChanged(@NonNull final File rootDir) {
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
         * Perform case-insensitive sorting using system locale (i.e. mFileDetails are system objects).
         */
        private static final Comparator<BRBaseActivity.FileDetails> FILE_DETAILS_COMPARATOR =
                (o1, o2) -> o1.getFile().getName().toLowerCase(App.getSystemLocale())
                              .compareTo(o2.getFile().getName()
                                           .toLowerCase(App.getSystemLocale()));

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
                       @NonNull final TaskListener<ArrayList<BRBaseActivity.FileDetails>> taskListener) {
            super(R.id.TASK_ID_FILE_LISTER, taskListener);
            mRootDir = rootDir;
        }

        @Override
        @NonNull
        @WorkerThread
        protected ArrayList<BRBaseActivity.FileDetails> doInBackground(final Void... params) {
            Thread.currentThread().setName("FileListerTask");

            //TODO: should be using a user context.
            Context context = App.getAppContext();

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
