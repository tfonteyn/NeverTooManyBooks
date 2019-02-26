package com.eleybourn.bookcatalogue.backup.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.filechooser.FileListerAsyncTask;

/**
 * Object to provide a FileListerFragmentTask specific to archive files.
 *
 * @author pjw
 */
public class BackupListerTask
        extends FileListerAsyncTask {

    /**
     * Construct a file filter to select only directories and backup files.
     */
    private final FileFilter mFilter = new FileFilter() {
        @Override
        public boolean accept(@NonNull final File pathname) {
            return (pathname.isDirectory() && pathname.canWrite())
                    || (pathname.isFile() && BackupFileDetails.isArchive(pathname));
        }
    };

    /**
     * Constructor.
     *
     * @param taskId  a task identifier, will be returned in the task finished listener.
     * @param context the caller context
     * @param root    folder to list
     */
    @UiThread
    BackupListerTask(final int taskId,
                     @NonNull final FragmentActivity context,
                     @NonNull final File root) {
        super(taskId, context, root);
    }

    /**
     * @return the file filter we constructed.
     */
    @NonNull
    protected FileFilter getFilter() {
        return mFilter;
    }

    /**
     * Process an array of Files.
     *
     * @param files to process
     *
     * @return an ArrayList of BackupFileDetails.
     */
    @NonNull
    protected ArrayList<FileDetails> processList(@Nullable final File[] files) {
        ArrayList<FileDetails> dirs = new ArrayList<>();
        if (files == null) {
            return dirs;
        }
        for (File file : files) {
            BackupFileDetails fd = new BackupFileDetails(file);
            dirs.add(fd);
            if (BackupFileDetails.isArchive(file)) {
                try (BackupReader reader = BackupManager.readFrom(file)) {
                    if (reader != null) {
                        fd.setInfo(reader.getInfo());
                    }
                } catch (IOException e) {
                    Logger.error(e);
                }
            }
        }
        return dirs;
    }
}
