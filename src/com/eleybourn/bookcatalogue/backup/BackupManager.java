/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.backup.ui.BackupFileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment.FragmentTaskAbstract;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

/**
 * Class for public static methods relating to backup/restore.
 *
 * @author pjw
 */
public final class BackupManager {

    /** Last full backup date. */
    public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path. */
    public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";

    private BackupManager() {
    }

    /**
     * Start a foreground task that backs up the entire catalogue.
     * <p>
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    public static void backup(@NonNull final FragmentActivity context,
                              final int taskId,
                              @NonNull final ExportSettings settings) {

        // sanity checks
        if ((settings.file == null) || ((settings.what & ExportSettings.MASK) == 0)) {
            throw new IllegalArgumentException("Options must be specified: " + settings);
        }

        // Ensure the file key extension is what we want
        if (!BackupFileDetails.isArchive(settings.file)) {
            settings.file = new File(
                    settings.file.getAbsoluteFile() + BackupFileDetails.ARCHIVE_EXTENSION);
        }

        // we write to the temp file, and rename it upon success
        final File tempFile = new File(settings.file.getAbsolutePath() + ".tmp");

        final FragmentTask task = new FragmentTaskAbstract() {
            private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();
            private boolean mSuccess;

            @Override
            public void run(@NonNull final TaskWithProgressDialogFragment fragment,
                            @NonNull final SimpleTaskContext taskContext
            )
                    throws Exception {

                BackupContainer bkp = new TarBackupContainer(tempFile);
                if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "backup", "starting|file=" + tempFile.getAbsolutePath());
                }
                try (BackupWriter wrt = bkp.newWriter()) {
                    try {
                        // do a cleanup first.
                        taskContext.getDb().purge();

                        // go go go...
                        wrt.backup(settings, new BackupWriter.BackupWriterListener() {

                            @Override
                            public void setMax(final int max) {
                                fragment.setMax(max);
                            }

                            @Override
                            public void onProgressStep(@Nullable final String message,
                                                       final int delta) {
                                fragment.onProgressStep(message, delta);
                            }

                            @Override
                            public boolean isCancelled() {
                                return fragment.isCancelled();
                            }
                        });
                    } catch (IOException | RuntimeException e) {
                        // add user-friendly message
                        throw new Exception(
                                BookCatalogueApp
                                        .getResString(R.string.export_error_backup_failed), e);
                    }

                    // All done. we handle the result here, still in the background.
                    if (fragment.isCancelled()) {
                        // cancelled
                        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                            Logger.info(this,
                                        "backup", "cancelling|file="
                                                + settings.file.getAbsolutePath());
                        }
                        StorageUtils.deleteFile(tempFile);
                    } else {
                        // success
                        StorageUtils.deleteFile(settings.file);
                        StorageUtils.renameFile(tempFile, settings.file);
                        mSuccess = true;

                        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                            Logger.info(this,
                                        "backup", "finished|file="
                                                + settings.file.getAbsolutePath()
                                                + ", size = " + settings.file.length());
                        }
                    }
                }
            }

            @Override
            @CallSuper
            public void onFinish(@NonNull final TaskWithProgressDialogFragment fragment,
                                 @Nullable final Exception e) {
                // show the user the exception if there was one, and clean up
                super.onFinish(fragment, e);
                if (e != null) {
                    StorageUtils.deleteFile(tempFile);
                }

                // report success and update the last backup date/file
                fragment.setSuccess(mSuccess);
                if (mSuccess) {
                    SharedPreferences.Editor ed = Prefs.getPrefs().edit();
                    // if the backup was a full one (not a 'since') remember that.
                    if ((settings.what & ExportSettings.ALL) != 0) {
                        ed.putString(PREF_LAST_BACKUP_DATE, mBackupDate);
                    }
                    ed.putString(PREF_LAST_BACKUP_FILE, settings.file.getAbsolutePath());
                    ed.apply();
                }
            }

        };
        // make sure our results get back to the caller
        task.setTag(settings);

        // show progress dialog and start the task
        TaskWithProgressDialogFragment frag = TaskWithProgressDialogFragment
                .newInstance(context, R.string.progress_msg_backing_up, task, false, taskId);
        frag.setNumberFormat(null);
    }

    /**
     * Start a foreground task that restores the entire catalogue.
     * <p>
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    public static void restore(@NonNull final FragmentActivity context,
                               final int taskId,
                               @NonNull final ImportSettings /* in/out */settings) {

        if ((settings.what & ImportSettings.MASK) == 0) {
            throw new IllegalArgumentException("Options must be specified");
        }

        final FragmentTask task = new FragmentTaskAbstract() {
            @Override
            public void run(@NonNull final TaskWithProgressDialogFragment fragment,
                            @NonNull final SimpleTaskContext taskContext
            )
                    throws Exception {
                if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "restore", "starting|file=" + settings.file.getAbsolutePath());
                }
                try (BackupReader reader = readFrom(settings.file)) {
                    Objects.requireNonNull(reader);
                    reader.restore(settings, new BackupReaderListener() {
                        @Override
                        public void setMax(final int max) {
                            fragment.setMax(max);
                        }

                        @Override
                        public void onProgressStep(@NonNull final String message,
                                                   final int delta) {
                            fragment.onProgressStep(message, delta);
                        }

                        @Override
                        public boolean isCancelled() {
                            return fragment.isCancelled();
                        }
                    });

                } catch (IOException e) {
                    // add user-friendly message
                    throw new Exception(
                            BookCatalogueApp.getResString(R.string.error_import_failed), e);
                }

                // all done. we handle the result here, still in the background.
                Logger.info(BackupManager.class,
                            "restore|finishing|file=" + settings.file.getAbsolutePath()
                                    + ", size = " + settings.file.length());
            }
        };

        // make sure our results get back to the caller
        task.setTag(settings);

        // show progress dialog and start the task
        TaskWithProgressDialogFragment frag =
                TaskWithProgressDialogFragment.newInstance(context,
                                                           R.string.progress_msg_importing,
                                                           task,
                                                           false, taskId);
        frag.setNumberFormat(null);
    }

    /**
     * Create a BackupReader for the specified file.
     *
     * @param file to read from
     *
     * @return a new reader or null when not a valid archive
     *
     * @throws IOException (inaccessible, invalid other errors)
     */
    @Nullable
    public static BackupReader readFrom(@NonNull final File file)
            throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Attempt to open non-existent backup file");
        }

        // We only support one backup format; so we use that. In future we would need to
        // explore the file to determine which format to use
        TarBackupContainer bkp = new TarBackupContainer(file);
        // Each format should provide a validator of some kind
        if (!bkp.isValid()) {
            return null;
        }

        return bkp.newReader();
    }

}
