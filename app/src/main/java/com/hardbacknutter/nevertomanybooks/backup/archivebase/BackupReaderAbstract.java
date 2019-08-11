/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.archivebase;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.ImportException;
import com.hardbacknutter.nevertomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertomanybooks.backup.Importer;
import com.hardbacknutter.nevertomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic BackupReader methods using
 * only a limited set of methods from the base interface.
 */
public abstract class BackupReaderAbstract
        implements BackupReader {

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /** progress message. */
    private final String mProcessPreferences;
    /** progress message. */
    private final String mProcessCover;
    /** progress message. */
    private final String mProcessBooklistStyles;
    /** progress and cancellation listener. */
    private ProgressListener mProgressListener;

    /** what and how to import. */
    private ImportOptions mSettings;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    protected BackupReaderAbstract(@NonNull final Context context) {
        mDb = new DAO();
        mProcessPreferences = context.getString(R.string.progress_msg_process_preferences);
        mProcessCover = context.getString(R.string.progress_msg_process_cover);
        mProcessBooklistStyles = context.getString(R.string.progress_msg_process_booklist_style);
    }

    /**
     * Do a full restore, sending progress to the listener.
     *
     * @throws IOException on failure
     */
    @Override
    public void restore(@NonNull final ImportOptions settings,
                        @NonNull final ProgressListener listener)
            throws IOException, ImportException {

        Context userContext = App.getFakeUserContext();
        Locale userLocale = LocaleUtils.getPreferredLocale();

        mSettings = settings;
        mProgressListener = listener;

        // keep track of what we read from the archive
        int entitiesRead = ImportOptions.NOTHING;

        // progress counters
        int coverCount = 0;
        int estimatedSteps = 1;

        try {
            final BackupInfo info = getInfo();
            estimatedSteps += info.getBookCount();
            if (info.hasCoverCount()) {
                // we got a count
                estimatedSteps += info.getCoverCount();
            } else {
                // We don't have a count, so assume each book has a cover.
                estimatedSteps *= 2;
            }

            mProgressListener.setMax(estimatedSteps);

            // Get first entity (this will be the entity AFTER the INFO entities)
            ReaderEntity entity = nextEntity();

            // process each entry based on type, unless we are cancelled, as in Nikita
            while (entity != null && !mProgressListener.isCancelled()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                    Logger.debug(this, "restore", "entity=" + entity.getName());
                }
                switch (entity.getType()) {
                    case Cover:
                        if ((mSettings.what & ImportOptions.COVERS) != 0) {
                            restoreCover(entity);
                            coverCount++;
                            // entitiesRead set when all done
                        }
                        break;

                    case Books:
                        if ((mSettings.what & ImportOptions.BOOK_CSV) != 0) {
                            // a CSV file with all book data
                            mSettings.results = restoreBooks(userContext, userLocale, entity);
                            entitiesRead |= ImportOptions.BOOK_CSV;
                        }
                        break;

                    case Preferences:
                        // current format
                        if ((mSettings.what & ImportOptions.PREFERENCES) != 0) {
                            mProgressListener.onProgressStep(1, mProcessPreferences);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer
                                        .doPreferences(entity, mProgressListener,
                                                       mSettings.getPrefs());
                            }
                            entitiesRead |= ImportOptions.PREFERENCES;
                        }
                        break;

                    case BooklistStyles:
                        // current format
                        if ((mSettings.what & ImportOptions.BOOK_LIST_STYLES) != 0) {
                            mProgressListener.onProgressStep(1, mProcessBooklistStyles);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doEntity(entity, mProgressListener);
                            }
                            entitiesRead |= ImportOptions.BOOK_LIST_STYLES;
                        }
                        break;

                    case XML:
                        // skip, future extension
                        break;

                    case PreferencesPreV200:
                        // pre-v200 format
                        if ((mSettings.what & ImportOptions.PREFERENCES) != 0) {
                            mProgressListener.onProgressStep(1, mProcessPreferences);
                            // read them into the 'old' prefs. Migration is done at a later stage.
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doPreferences(entity, mProgressListener,
                                                       userContext.getSharedPreferences(
                                                               Prefs.PREF_LEGACY_BOOK_CATALOGUE,
                                                               Context.MODE_PRIVATE));
                            }
                            entitiesRead |= ImportOptions.PREFERENCES;
                        }
                        break;

                    case BooklistStylesPreV200:
                        // pre-v200 format
                        if ((mSettings.what & ImportOptions.BOOK_LIST_STYLES) != 0) {
                            restorePreV200Style(entity);
                            entitiesRead |= ImportOptions.BOOK_LIST_STYLES;
                        }
                        break;

                    case Database:
                        // don't restore from archive; we're using the CSV file
                        break;

                    case Info:
                        // skip, already handled
                        break;

                    default:
                        throw new IllegalArgumentException("" + entity.getType());
                }
                entity = nextEntity();
            }
        } finally {
            // report what we actually imported
            if (coverCount > 0) {
                entitiesRead |= ImportOptions.COVERS;
                // sanity check... we would not have covers unless we had books? or would we?
                if (mSettings.results == null) {
                    mSettings.results = new Importer.Results();
                }
                mSettings.results.coversImported = coverCount;
            }
            mSettings.what = entitiesRead;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Logger.debug(this, "restore", "imported covers#=" + coverCount);
            }
            try {
                close();
            } catch (@NonNull final IOException e) {
                Logger.error(this, e, "Failed to close reader");
            }
        }
    }

    /**
     * Actual reader should override and close their input.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        mDb.close();
    }

    /**
     * Restore the books from the export file.
     *
     * @param context Current context
     * @param entity  to restore
     *
     * @throws IOException on failure
     */
    private Importer.Results restoreBooks(@NonNull final Context context,
                                          @NonNull final Locale userLocale,
                                          @NonNull final ReaderEntity entity)
            throws IOException, ImportException {

        try (Importer importer = new CsvImporter(context, mSettings)) {
            return importer
                           .doBooks(context, userLocale, entity.getStream(), null,
                                    mProgressListener);
        }
    }

    /**
     * Restore a cover file.
     *
     * @param cover to restore
     *
     * @throws IOException on failure
     */
    private void restoreCover(@NonNull final ReaderEntity cover)
            throws IOException {
        mProgressListener.onProgressStep(1, mProcessCover);

        // see if we have this file already
        final File currentCover = StorageUtils.getRawCoverFile(cover.getName());
        final Date covDate = cover.getDateModified();

        if ((mSettings.what & ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
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
     * Restore a serialized (pre-v200 archive) booklist style.
     *
     * @param entity to restore
     *
     * @throws IOException on failure
     */
    private void restorePreV200Style(@NonNull final ReaderEntity entity)
            throws IOException {

        Logger.warn(this, "restorePreV200Style", "Skipping");
        mProgressListener.onProgressStep(1, mProcessBooklistStyles);
//        try {
//            // deserialization will take care of writing the v200+ SharedPreference file
//            BooklistStyle style = entity.getSerializable();
//            style.save(mDb);
//
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
//                Logger.debug(this, "restorePreV200Style", style);
//            }
//        } catch (@NonNull final DeserializationException e) {
//            Logger.error(this, e, "Unable to restore style");
//        }
    }
}
