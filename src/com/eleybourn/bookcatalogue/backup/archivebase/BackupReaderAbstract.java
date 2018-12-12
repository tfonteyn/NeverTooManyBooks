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
package com.eleybourn.bookcatalogue.backup.archivebase;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvImporter;
import com.eleybourn.bookcatalogue.backup.csv.Importer;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.RTE.DeserializationException;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
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

    private String processPreferences;
    private String processCover;
    private String processBooklistStyles;

    /**
     * Constructor
     */
    protected BackupReaderAbstract() {
        Context context = BookCatalogueApp.getAppContext();
        mDb = new CatalogueDBAdapter(context);

        processPreferences = context.getString(R.string.progress_msg_process_preferences);
        processCover = context.getString(R.string.progress_msg_process_cover);
        processBooklistStyles = context.getString(R.string.progress_msg_process_booklist_style);
    }

    /**
     * Do a full restore, sending progress to the listener
     */
    @Override
    public void restore(final @NonNull ImportSettings settings,
                        final @NonNull BackupReaderListener listener) throws IOException {

        // keep track of what we read from the archive
        int entitiesRead = ImportSettings.NOTHING;

        // progress counters
        int coverCount = 0;
        int estimatedSteps = 1;

        try {
            final BackupInfo info = getInfo();
            estimatedSteps += info.getBookCount();
            if (info.hasCoverCount()) {
                estimatedSteps += info.getCoverCount();
            } else {
                // This is an estimate only; we actually don't know how many covers there are in the backup.
                estimatedSteps *= 2;
            }
            listener.setMax(estimatedSteps);

            // Get first entity (this will be the entity AFTER the INFO entities)
            ReaderEntity entity = nextEntity();
            // Reminder: type 'Cover' actually means *all* not recognised files !

            // process each entry based on type, unless we are cancelled, as in Nikita
            while (entity != null && !listener.isCancelled()) {
            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, "reading|entity=" + entity.getName());
            }
                switch (entity.getType()) {
                    case Books: {
                        if ((settings.what & ImportSettings.BOOK_CSV) != 0) {
                            // a CSV file with all book data
                            restoreBooks(listener, entity, settings);
                            entitiesRead |= ImportSettings.BOOK_CSV;
                        }
                        break;
                    }
                    case Cover: {
                        if ((settings.what & ImportSettings.BOOK_CSV) != 0) {
                            // This *might* not be a cover !
                            restoreCover(listener, entity, settings);
                            coverCount++;
                            // entitiesRead set when all done
                        }
                        break;
                    }
                    case Database:
                        // don't restore from archive; we're using the CSV file
                        break;

                    case Preferences: {
                        if ((settings.what & ImportSettings.PREFERENCES) != 0) {
                            restorePreferences(listener, entity);
                            entitiesRead |= ImportSettings.PREFERENCES;
                        }
                        break;
                    }
                    case BooklistStyle: {
                        if ((settings.what & ImportSettings.BOOK_LIST_STYLES) != 0) {
                            restoreStyle(listener, entity);
                            entitiesRead |= ImportSettings.BOOK_LIST_STYLES;
                        }
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
                    Logger.info(this, "restored|entity=" + entity.getName());
                }
                entity = nextEntity();
            }
        } finally {
            if (coverCount > 0) {
                entitiesRead |= ImportSettings.COVERS;
            }
            settings.what = entitiesRead;

            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, "imported covers#=" + coverCount);
            }
            try {
                close();
            } catch (Exception e) {
                Logger.error(e, "Failed to close reader");
            }
        }
    }

    /**
     * Restore the books from the export file.
     */
    private void restoreBooks(final @NonNull BackupReaderListener listener,
                              final @NonNull ReaderEntity entity,
                              final @NonNull ImportSettings settings) throws IOException {
        // Listener for the 'doExport' function that just passes on the progress to our own listener
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
        CsvImporter importer = new CsvImporter(settings);
        importer.doImport(entity.getStream(), null, importListener);
    }


    /**
     * Restore a cover file.
     *
     * Note: there is no check if the file is *really* a cover image (jpg,png)
     * The file will simply be copied to the covers directory providing the import flags are met.
     */
    private void restoreCover(final @NonNull BackupReaderListener listener,
                              final @NonNull ReaderEntity cover,
                              final @NonNull ImportSettings settings) throws IOException {
        listener.step(processCover, 1);

        // see if we have this file already
        final File currentCover = StorageUtils.getRawCoverFile(cover.getName());
        final Date covDate = cover.getDateModified();

        if ((settings.what & ImportSettings.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
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
        listener.step(processBooklistStyles, 1);
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
     * Actual reader should override and close their input
     */
    @Override
    @CallSuper
    public void close() throws IOException {
        mDb.close();
    }
}
