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
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.RTE.DeserializationException;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Basic implementation of format-agnostic BackupReader methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupReaderAbstract implements BackupReader {
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Context mContext;

    private final String processPreferences = BookCatalogueApp.getResourceString(R.string.progress_msg_process_preferences);
    private final String processCover = BookCatalogueApp.getResourceString(R.string.progress_msg_process_cover);
    private final String processBooklistStyle = BookCatalogueApp.getResourceString(R.string.progress_msg_process_booklist_style);

    /**
     * Constructor
     */
    protected BackupReaderAbstract(@NonNull final Context context) {
        mContext = context;
        mDb = new CatalogueDBAdapter(mContext);
    }

    /**
     * Do a full restore, sending progress to the listener
     */
    @Override
    public void restore(final @NonNull BackupReaderListener listener,
                        final int importFlags) throws IOException {
        // Just a stat for progress
        int coverCount = 0;

        // This is an estimate only; we actually don't know how many covers
        // there are in the backup.
        final BackupInfo info = getInfo();
        int maxSteps = info.getBookCount();
        if (info.hasCoverCount())
            maxSteps += info.getCoverCount();
        else
            maxSteps *= 2;
        maxSteps++;
        listener.setMax(maxSteps);

        // Get first entity (this will be the entity AFTER the INFO entities)
        ReaderEntity entity = nextEntity();
        // Reminder: type 'Cover' actually means *all* not recognised files !

        // While not at end, loop, processing each entry based on type
        while (entity != null && !listener.isCancelled()) {
            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, " Processing " + entity.getName());
            }
            switch (entity.getType()) {
                case Books: {
                    // a CSV file with all book data
                    restoreBooks(listener, entity, importFlags);
                    break;
                }
                case Cover: {
                    // This *might* not be a cover !
                    coverCount++;
                    restoreCover(listener, entity, importFlags);
                    break;
                }
                case Database:
                    // don't restore from archive; we're using the CSV file
                    break;

                case Preferences: {
                    restorePreferences(listener, entity);
                    break;
                }
                case BooklistStyle: {
                    restoreStyle(listener, entity);
                    break;
                }
                case XML: {
                    // skip, future extension
                    break;
                }

                case Info: {
                    // skip, already handled, should in fact not be seen.
                    break;
                }
                default:
                    throw new RTE.IllegalTypeException("" + entity.getType());
            }
            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, " Finished " + entity.getName());
            }
            entity = nextEntity();
        }
        close();

        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(this, " Restored " + coverCount + " covers");
        }
    }

    /**
     * Restore the books from the export file.
     */
    private void restoreBooks(final @NonNull BackupReaderListener listener,
                              final @NonNull ReaderEntity entity,
                              final int importFlags) throws IOException {
        // Make a listener for the 'export' function that just passes on the progress to out listener
        Importer.OnImporterListener importListener = new Importer.OnImporterListener() {
            private int mLastPos = 0;

            @Override
            public void onProgress(final @NonNull String message, final int position) {
                // The progress is sent periodically and has jumps, so we calculate deltas
                listener.step(message, position - mLastPos);
                mLastPos = position;
            }

            @Override
            public boolean isActive() {
                return !listener.isCancelled();
            }

            @Override
            public void setMax(final int max) {
                // Ignore; we know how many books there are
            }
        };

        // Now do the import
        InputStream in = entity.getStream();
        CsvImporter importer = new CsvImporter(mContext);
        importer.importBooks(in, null, importListener, importFlags);
    }


    /**
     * Restore a cover file.
     *
     * Note: there is no check if the file is *really* a cover image (jpg,png)
     * The file will simply be copied to the covers directory providing the import flags are met.
     */
    private void restoreCover(final @NonNull BackupReaderListener listener,
                              final @NonNull ReaderEntity cover,
                              final int flags) throws IOException {
        listener.step(processCover, 1);

        // see if we have this file already
        final File currentCover = StorageUtils.getRawCoverFile(cover.getName());
        final Date covDate = cover.getDateModified();

        if ((flags & Importer.IMPORT_NEW_OR_UPDATED) != 0) {
            if (currentCover.exists()) {
                Date currFileDate = new Date(currentCover.lastModified());
                if (currFileDate.compareTo(covDate) >= 0) {
                    return;
                }
            }
        }
        // save (and overwrite)
        cover.saveToDirectory(StorageUtils.getCoverStorage());
        //noinspection ResultOfMethodCallIgnored
        currentCover.setLastModified(covDate.getTime());
    }

    /**
     * Restore the app preferences
     */
    private void restorePreferences(final @NonNull BackupReaderListener listener,
                                    final @NonNull ReaderEntity entity) throws IOException {
        listener.step(processPreferences, 1);
        SharedPreferences prefs = BookCatalogueApp.getSharedPreferences();
        entity.getPreferences(prefs);
    }

    /**
     * Restore a booklist style
     */
    private void restoreStyle(final @NonNull BackupReaderListener listener,
                              final @NonNull ReaderEntity entity) throws IOException {
        listener.step(processBooklistStyle, 1);
        BooklistStyle booklistStyle = null;
        try {
            booklistStyle = entity.getSerializable();
        } catch (DeserializationException e) {
            Logger.error(e, "Unable to restore style");
        }

        if (booklistStyle != null) {
            mDb.insertOrUpdateBooklistStyle(booklistStyle);
        }
    }

    /**
     * Close the reader
     */
    @Override
    public void close() throws IOException {
        mDb.close();
    }
}
