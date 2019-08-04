package com.hardbacknutter.nevertomanybooks.backup.ui;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertomanybooks.backup.ui.BRBaseActivity.FileDetails;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Object to provide a FileListerFragmentTask specific to archive mFileDetails.
 */
public class FileListerTask
        extends TaskBase<ArrayList<FileDetails>> {

    /**
     * Perform case-insensitive sorting using system locale (i.e. mFileDetails are system objects).
     */
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR =
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
                   @NonNull final TaskListener<ArrayList<FileDetails>> taskListener) {
        super(R.id.TASK_ID_FILE_LISTER, taskListener);
        mRootDir = rootDir;
    }

    @Override
    @NonNull
    @WorkerThread
    protected ArrayList<FileDetails> doInBackground(final Void... params) {
        Thread.currentThread().setName("FileListerTask");

        //TODO: should be using a user context.
        Context context = App.getAppContext();

        ArrayList<FileDetails> fileDetails = new ArrayList<>();

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
            BackupFileDetails fd = new BackupFileDetails(file);
            fileDetails.add(fd);
            if (BackupManager.isArchive(file)) {
                try (BackupReader reader = BackupManager.getReader(context, file)) {
                    fd.setInfo(reader.getInfo());
                } catch (@NonNull final IOException e) {
                    Logger.error(this, e);
                }
            }
        }

        Collections.sort(fileDetails, FILE_DETAILS_COMPARATOR);
        return fileDetails;
    }
}
