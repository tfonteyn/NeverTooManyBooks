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

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

/**
 * Class for public static methods relating to backup/restore
 *
 * @author pjw
 */
public class BackupManager {

    /**
     * Ensure the file name extension is what we want
     */
    @NonNull
    private static File cleanupFile(@NonNull final File requestedFile) {
        if (!requestedFile.getName().toUpperCase().endsWith(".BCBK")) {
            return new File(requestedFile.getAbsoluteFile() + ".bcbk");
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
    public static File backup(@NonNull final FragmentActivity context,
                              @NonNull final File requestedFile,
                              final int taskId,
                              final int backupFlags,
                              @Nullable final Date since) {
        final int flags = backupFlags & Exporter.EXPORT_MASK;
        if (flags == 0) {
            throw new IllegalArgumentException("Backup flags must be specified");
        }
        //if (flags == (Exporter.EXPORT_ALL | Exporter.EXPORT_NEW_OR_UPDATED) ) {
        //	throw new IllegalArgumentException("Illegal backup flag combination: ALL and NEW_OR_UPDATED");
        //}

        final File resultingFile = cleanupFile(requestedFile);
        final File tempFile = new File(resultingFile.getAbsolutePath() + ".tmp");

        final FragmentTask task = new FragmentTaskAbstract() {
            private final String mBackupDate = DateUtils.toSqlDateTime(new Date());
            private boolean mBackupOk = false;

            @Override
            public void run(@NonNull final SimpleTaskQueueProgressFragment fragment, @NonNull final SimpleTaskContext taskContext) {

                TarBackupContainer bkp = new TarBackupContainer(tempFile);
                if (BuildConfig.DEBUG) {
                    Logger.info("Starting " + tempFile.getAbsolutePath());
                }
                try (BackupWriter wrt = bkp.newWriter()) {

                    wrt.backup(new BackupWriterListener() {
                        private int mTotalBooks = 0;

                        @Override
                        public void setMax(final int max) {
                            fragment.setMax(max);
                        }

                        @Override
                        public void step(@Nullable final String message, final int delta) {
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
                    }, backupFlags, since);

                    if (fragment.isCancelled()) {
                        if (BuildConfig.DEBUG) {
                            Logger.info("Cancelled " + resultingFile.getAbsolutePath());
                        }
                        StorageUtils.deleteFile(tempFile);
                    } else {
                        StorageUtils.deleteFile(resultingFile);
                        StorageUtils.renameFile(tempFile, resultingFile);
                        mBackupOk = true;
                        if (BuildConfig.DEBUG) {
                            Logger.info("Finished " + resultingFile.getAbsolutePath() + ", size = " + resultingFile.length());
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
            public void onFinish(@NonNull final SimpleTaskQueueProgressFragment fragment, @Nullable final Exception e) {
                super.onFinish(fragment, e);
                if (e != null) {
                    StorageUtils.deleteFile(tempFile);
                }
                fragment.setSuccess(mBackupOk);
                if (mBackupOk) {
                    if ((backupFlags == Exporter.EXPORT_ALL)) {
                        BookCatalogueApp.Prefs.putString(BookCatalogueApp.PREF_LAST_BACKUP_DATE, mBackupDate);
                    }
                    BookCatalogueApp.Prefs.putString(BookCatalogueApp.PREF_LAST_BACKUP_FILE, resultingFile.getAbsolutePath());
                }
            }

        };
        SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment
                .runTaskWithProgress(context, R.string.backing_up_ellipsis, task, false, taskId);
        frag.setNumberFormat(null);
        return resultingFile;
    }

    /**
     * Start a foreground task that restores the entire catalogue.
     *
     * We use a FragmentTask so that long actions do not occur in the UI thread.
     */
    public static void restore(@NonNull final FragmentActivity context,
                               @NonNull final File inputFile,
                               final int taskId,
                               final int importFlags) {

        final FragmentTask task = new FragmentTaskAbstract() {
            @Override
            public void run(@NonNull final SimpleTaskQueueProgressFragment fragment, @NonNull final SimpleTaskContext taskContext) {
                try {
                    Logger.info("Importing " + inputFile.getAbsolutePath());

                    readFrom(inputFile).restore(new BackupReaderListener() {
                        @Override
                        public void setMax(int max) {
                            fragment.setMax(max);
                        }

                        @Override
                        public void step(@NonNull final String message, final int delta) {
                            fragment.step(message, delta);
                        }

                        @Override
                        public boolean isCancelled() {
                            return fragment.isCancelled();
                        }
                    }, importFlags);

                } catch (IOException e) {
                    Logger.error(e);
                    throw new RuntimeException("Error during restore", e);
                }
                Logger.info("Finished " + inputFile.getAbsolutePath() + ", size = " + inputFile.length());
            }
        };
        SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context,
                R.string.importing_ellipsis, task, false, taskId);
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
    public static BackupReader readFrom(@NonNull final File file) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Attempt to open non-existent backup file");
        }

        // We only support one backup format; so we use that. In future we would need to
        // explore the file to determine which format to use
        TarBackupContainer bkp = new TarBackupContainer(file);
        // Each format should provide a validator of some kind
        if (!bkp.isValid()) {
            throw new IOException("Not a valid backup file");
        }

        return bkp.newReader();
    }
}
