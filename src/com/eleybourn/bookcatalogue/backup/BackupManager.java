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

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.filechooser.BackupFileDetails;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Class for public static methods relating to backup/restore
 *
 * @author pjw
 */
public class BackupManager {

    /** Last full backup date */
    public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path */
    public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";

    /**
     * Ensure the file name extension is what we want
     */
    @NonNull
    private static File cleanupExtension(final @NonNull File requestedFile) {
        if (!BackupFileDetails.isArchive(requestedFile)) {
            return new File(requestedFile.getAbsoluteFile() + BackupFileDetails.ARCHIVE_EXTENSION);
        } else {
            return requestedFile;
        }
    }

    /**
     * Start a foreground task that backs up the entire catalogue.
     *
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    @NonNull
    public static File backup(final @NonNull FragmentActivity context,
                              final int taskId,
                              final @NonNull ExportSettings settings) {

        if ((settings.options & ExportSettings.EXPORT_MASK) == 0) {
            throw new IllegalArgumentException("Backup flags must be specified");
        }
        final File destinationFile = cleanupExtension(settings.file);
        // we write to the temp file, and rename it upon success
        final File tempFile = new File(destinationFile.getAbsolutePath() + ".tmp");

        final FragmentTask task = new FragmentTaskAbstract() {
            private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();
            private boolean mBackupOk = false;

            @Override
            public void run(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                            final @NonNull SimpleTaskContext taskContext) {

                BackupContainer bkp = new TarBackupContainer(fragment.requireContext(), tempFile);
                if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                    Logger.info(this, "backup|starting|file=" + tempFile.getAbsolutePath());
                }
                try (BackupWriter wrt = bkp.newWriter()) {
                    // do it!
                    wrt.backup(settings, new BackupWriterListener() {
                        private int mTotalBooks = 0;

                        @Override
                        public void setMax(final int max) {
                            fragment.setMax(max);
                        }

                        @Override
                        public void step(final @Nullable String message, final int delta) {
                            fragment.step(message, delta);
                        }

                        @Override
                        public boolean isCancelled() {
                            return fragment.isCancelled();
                        }

                        @Override
                        public int getTotalBooks() {
                            return mTotalBooks;
                        }

                        @Override
                        public void setTotalBooks(final int books) {
                            mTotalBooks = books;
                        }
                    });

                    // all done. we handle the result here, still in the background.
                    if (fragment.isCancelled()) {
                        // cancelled
                        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                            Logger.info(this, "backup|cancelling|file=" + destinationFile.getAbsolutePath());
                        }
                        StorageUtils.deleteFile(tempFile);
                    } else {
                        // success
                        StorageUtils.deleteFile(destinationFile);
                        StorageUtils.renameFile(tempFile, destinationFile);
                        mBackupOk = true;
                        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                            Logger.info(this, "backup|finished|file=" + destinationFile.getAbsolutePath() + ", size = " + destinationFile.length());
                        }
                    }
                } catch (Exception e) {
                    Logger.error(e);
                    StorageUtils.deleteFile(tempFile);
                    throw new RuntimeException("Error during backup", e);
                }
            }

            @Override
            @CallSuper
            public void onFinish(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @Nullable Exception e) {
                super.onFinish(fragment, e);
                if (e != null) {
                    StorageUtils.deleteFile(tempFile);
                }

                fragment.setSuccess(mBackupOk);
                if (mBackupOk) {
                    SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
                    // if the backup was a full one (not a 'since') remember that.
                    if ((settings.options & ExportSettings.EXPORT_ALL) != 0) {
                        ed.putString(PREF_LAST_BACKUP_DATE, mBackupDate);
                    }
                    ed.putString(PREF_LAST_BACKUP_FILE, destinationFile.getAbsolutePath());
                    ed.apply();
                }
            }

        };
        SimpleTaskQueueProgressDialogFragment frag = SimpleTaskQueueProgressDialogFragment
                .newInstance(context, R.string.progress_msg_backing_up, task, false, taskId);
        frag.setNumberFormat(null);
        return destinationFile;
    }

    /**
     * Start a foreground task that restores the entire catalogue.
     *
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    public static void restore(final @NonNull FragmentActivity context,
                               final int taskId,
                               final @NonNull ImportSettings settings) {

        final FragmentTask task = new FragmentTaskAbstract() {
            @Override
            public void run(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                            final @NonNull SimpleTaskContext taskContext) {
                try {
                    if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                        Logger.info(this, "restore|starting|file=" + settings.file.getAbsolutePath());
                    }
                    readFrom(context, settings.file)
                            .restore(settings, new BackupReaderListener() {
                        @Override
                        public void setMax(int max) {
                            fragment.setMax(max);
                        }

                        @Override
                        public void step(final @NonNull String message, final int delta) {
                            fragment.step(message, delta);
                        }

                        @Override
                        public boolean isCancelled() {
                            return fragment.isCancelled();
                        }
                    });

                } catch (IOException e) {
                    Logger.error(e);
                    throw new RuntimeException("Error during restore", e);
                }
                Logger.info(BackupManager.class, "restore|finishing|file=" + settings.file.getAbsolutePath() + ", size = " + settings.file.length());
            }
        };
        SimpleTaskQueueProgressDialogFragment frag = SimpleTaskQueueProgressDialogFragment.newInstance(context,
                R.string.progress_msg_importing, task, false, taskId);
        frag.setNumberFormat(null);
    }

    /**
     * Create a BackupReader for the specified file.
     *
     * @param file to read from
     *
     * @return a new reader
     *
     * @throws IOException (inaccessible, invalid other other errors)
     */
    public static BackupReader readFrom(final @NonNull Context context,
                                        final @NonNull File file) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Attempt to open non-existent backup file");
        }

        // We only support one backup format; so we use that. In future we would need to
        // explore the file to determine which format to use
        TarBackupContainer bkp = new TarBackupContainer(context, file);
        // Each format should provide a validator of some kind
        if (!bkp.isValid()) {
            throw new IOException("Not a valid backup file");
        }

        return bkp.newReader();
    }
}
