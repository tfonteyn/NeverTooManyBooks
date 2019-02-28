package com.eleybourn.bookcatalogue.backup.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.filechooser.FileListerAsyncTask;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

/**
 * Object to provide a FileListerFragmentTask specific to archive files.
 *
 * @author pjw
 */
public class BackupListerTask
        extends FileListerAsyncTask {

    public static final String TAG = BackupListerTask.class.getSimpleName();
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
     * @param root    folder to list
     */
    @UiThread
    BackupListerTask(@NonNull final ProgressDialogFragment<ArrayList<FileDetails>> frag,
                     @NonNull final File root) {
        super(frag, R.id.TASK_ID_FILE_LISTER, root);
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
