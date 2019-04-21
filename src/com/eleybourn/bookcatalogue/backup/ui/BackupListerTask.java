package com.eleybourn.bookcatalogue.backup.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

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

    /** Fragment manager tag. */
    public static final String TAG = BackupListerTask.class.getSimpleName();

    /**
     * Construct a file filter to select only directories and archive files.
     */
    private final FileFilter mFilter = pathname ->
            (pathname.isDirectory() && pathname.canWrite())
                    || (pathname.isFile() && BackupFileDetails.isArchive(pathname));

    /**
     * Constructor.
     *
     * @param fragment ProgressDialogFragment
     * @param root     folder to list
     */
    @UiThread
    BackupListerTask(@NonNull final ProgressDialogFragment<ArrayList<FileDetails>> fragment,
                     @NonNull final File root) {
        super(fragment, root);
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
                //noinspection ConstantConditions
                try (BackupReader reader = BackupManager.readFrom(mFragment.getContext(), file)) {
                    if (reader != null) {
                        fd.setInfo(reader.getInfo());
                    }
                } catch (IOException e) {
                    Logger.error(this, e);
                }
            }
        }
        return dirs;
    }
}
