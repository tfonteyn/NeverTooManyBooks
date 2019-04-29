package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.ui.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Object to provide a FileListerFragmentTask specific to archive files.
 *
 * @author pjw
 */
public class FileListerTask
        extends AsyncTask<Void, Object, FileChooserFragment.DirectoryContent> {

    /** Fragment manager tag. */
    public static final String TAG = FileListerTask.class.getSimpleName();

    /**
     * Perform case-insensitive sorting using system locale (i.e. files are system objects).
     */
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR =
            (o1, o2) -> o1.getFile().getName().toLowerCase(LocaleUtils.getSystemLocale())
                          .compareTo(o2.getFile().getName().toLowerCase(
                                  LocaleUtils.getSystemLocale()));

    @NonNull
    protected final ProgressDialogFragment<FileChooserFragment.DirectoryContent> mFragment;

    @NonNull
    private final FileChooserFragment.DirectoryContent mDir;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param fragment ProgressDialogFragment
     * @param root     folder to list
     */
    @UiThread
    FileListerTask(@NonNull final ProgressDialogFragment<FileChooserFragment.DirectoryContent> fragment,
                   @NonNull final File root) {

        mFragment = fragment;
        mDir = new FileChooserFragment.DirectoryContent(root);
    }

    @Override
    @Nullable
    @WorkerThread
    protected FileChooserFragment.DirectoryContent doInBackground(final Void... params) {

        // Filter for directories and our own archive files.
        FileFilter fileFilter = pathname ->
                (pathname.isDirectory() && pathname.canWrite())
                        || (pathname.isFile() && BackupFileDetails.isArchive(pathname));

        // Get a file list
        File[] files = mDir.root.listFiles(fileFilter);
        if (files == null) {
            return mDir;
        }

        for (File file : files) {
            BackupFileDetails fd = new BackupFileDetails(file);
            mDir.files.add(fd);
            if (BackupFileDetails.isArchive(file)) {
                Context context = mFragment.getContext();
                if (context == null) {
                    // debugging... me bad.
                    Logger.warnWithStackTrace(this, "getContext() was NULL, using AppContext");
                    context = App.getAppContext();
                }

                try (BackupReader reader = BackupManager.readFrom(context, file)) {
                    fd.setInfo(reader.getInfo());
                } catch (IOException e) {
                    Logger.error(this, e);
                }
            }
        }

        Collections.sort(mDir.files, FILE_DETAILS_COMPARATOR);
        return mDir;
    }

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final FileChooserFragment.DirectoryContent result) {
        mFragment.onTaskFinished(mException == null, result);
    }
}
