package com.eleybourn.bookcatalogue.backup.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.ui.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Object to provide a FileListerFragmentTask specific to archive files.
 *
 * @author pjw
 */
public class FileListerTask
        extends TaskWithProgress<Object, FileChooserFragment.DirectoryContent> {

    /** Used by the {@link ProgressDialogFragment} for this task. */
    public static final String TAG = FileListerTask.class.getSimpleName();

    /**
     * Perform case-insensitive sorting using system locale (i.e. files are system objects).
     */
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR =
            (o1, o2) -> o1.getFile().getName().toLowerCase(LocaleUtils.getSystemLocale())
                          .compareTo(o2.getFile().getName().toLowerCase(
                                  LocaleUtils.getSystemLocale()));

    @NonNull
    private final FileChooserFragment.DirectoryContent mDir;

    /**
     * Constructor.
     *
     * @param progressDialog ProgressDialogFragment
     * @param root           folder to list
     */
    @UiThread
    FileListerTask(@NonNull final ProgressDialogFragment<Object, FileChooserFragment.DirectoryContent> progressDialog,
                   @NonNull final File root) {
        super(progressDialog);

        mDir = new FileChooserFragment.DirectoryContent(root);
    }

    protected int getId() {
        return R.id.TASK_ID_FILE_LISTER;
    }

    @Override
    @Nullable
    @WorkerThread
    protected FileChooserFragment.DirectoryContent doInBackground(final Void... params) {

        // Filter for directories and our own archive files.
        FileFilter fileFilter = pathname ->
                (pathname.isDirectory() && pathname.canWrite())
                        || (pathname.isFile() && BackupManager.isArchive(pathname));

        // Get a file list
        File[] files = mDir.root.listFiles(fileFilter);
        if (files == null) {
            return mDir;
        }

        for (File file : files) {
            BackupFileDetails fd = new BackupFileDetails(file);
            mDir.files.add(fd);
            if (BackupManager.isArchive(file)) {
                try (BackupReader reader = BackupManager.getReader(file)) {
                    fd.setInfo(reader.getInfo());
                } catch (IOException e) {
                    Logger.error(this, e);
                }
            }
        }

        Collections.sort(mDir.files, FILE_DETAILS_COMPARATOR);
        return mDir;
    }
}
