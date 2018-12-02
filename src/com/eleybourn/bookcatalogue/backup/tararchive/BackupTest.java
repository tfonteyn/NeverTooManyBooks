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
package com.eleybourn.bookcatalogue.backup.tararchive;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;

/**
 * Test module. DEBUG ONLY!
 *
 * @author pjw
 */
public class BackupTest {
    private static final String BACKUP_TAR = "backup.tar";

    public static void testBackupTar() {
        File f = StorageUtils.getFile(BACKUP_TAR);
        try {
            performBackupTar(BookCatalogueApp.getAppContext(), f);
        } catch (IOException e) {
            Logger.error(e);
        }
    }

    public static void testRestoreTar() {
        File f = StorageUtils.getFile(BACKUP_TAR);
        try {
            performRestoreTar(BookCatalogueApp.getAppContext(), f);
        } catch (IOException e) {
            Logger.error(e);
        }
    }

    private static void performBackupTar(final @NonNull Context context, @NonNull File file) throws IOException {
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(BackupTest.class,"Starting " + file.getAbsolutePath());
        }
        ExportSettings settings = new ExportSettings(file);
        settings.what = ExportSettings.EXPORT_ALL;
        new TarBackupContainer(context, file)
                .newWriter()
                .backup(settings, new BackupWriterListener() {
                    private final boolean mIsCancelled = false;
                    private long mMax;
                    @Nullable
                    private String mMessage = "";
                    private int mPosition = 0;
                    private int mTotalBooks;

                    @Override
                    public void setMax(int max) {
                        mMax = max;
                    }

                    @Override
                    public void step(final @Nullable String message, final int delta) {
                        if (message != null)
                            mMessage = message;
                        mPosition += delta;
                        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                            Logger.info(BackupTest.class,mMessage + " " + mPosition + " of " + mMax);
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return mIsCancelled;
                    }

                    @Override
                    public int getTotalBooks() {
                        return mTotalBooks;
                    }

                    @Override
                    public void setTotalBooks(int books) {
                        mTotalBooks = books;
                    }
                });
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(BackupTest.class,"Finished " + file.getAbsolutePath() + ", size = " + file.length());
        }
    }

    private static void performRestoreTar(final @NonNull Context context, @NonNull File file) throws IOException {
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(BackupTest.class,"Starting " + file.getAbsolutePath());
        }

        TarBackupContainer bkp = new TarBackupContainer(context, file);
        // Each format should provide a validator of some kind
        if (!bkp.isValid()) {
            throw new IOException("Not a valid backup file");
        }

        ImportSettings settings = new ImportSettings(file);
        settings.what = ImportSettings.IMPORT_ALL;
        bkp.newReader().restore(settings, new BackupReaderListener() {
            private final boolean mIsCancelled = false;
            private long mMax;
            @Nullable
            private String mMessage = "";
            private int mPosition = 0;

            @Override
            public void setMax(int max) {
                mMax = max;
            }

            @Override
            public void step(final @Nullable String message, final int delta) {
                if (message != null) {
                    mMessage = message;
                }
                mPosition += delta;
                if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                    Logger.info(BackupTest.class,mMessage + " " + mPosition + " of " + mMax);
                }
            }

            @Override
            public boolean isCancelled() {
                return mIsCancelled;
            }
        });

        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(BackupTest.class,"Finished " + file.getAbsolutePath() + ", size = " + file.length());
        }
    }
}
