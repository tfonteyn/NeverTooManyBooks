/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.backup.archivebase;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

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
                        @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        Context userContext = App.getFakeUserContext();
        Locale userLocale = LocaleUtils.getPreferredLocale();

        mSettings = settings;

        // keep track of what we read from the archive
        int entitiesRead = ImportOptions.NOTHING;

        // entities that only appear once
        boolean incStyles = (mSettings.what & ImportOptions.BOOK_LIST_STYLES) != 0;
        boolean incPrefs = (mSettings.what & ImportOptions.PREFERENCES) != 0;
        boolean incBooks = (mSettings.what & ImportOptions.BOOK_CSV) != 0;
        boolean incCovers = (mSettings.what & ImportOptions.COVERS) != 0;

        // progress counters
        int coverCount = 0;
        int estimatedSteps = 1;

        try {
            final BackupInfo info = getInfo();
            estimatedSteps += info.getBookCount();
            if (incCovers) {
                if (info.hasCoverCount()) {
                    estimatedSteps += info.getCoverCount();
                } else {
                    // We don't have a count, so assume each book has a cover.
                    estimatedSteps *= 2;
                }
            }

            progressListener.setMax(estimatedSteps);

            // Seek the styles entity first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (incStyles) {
                progressListener.onProgressStep(1, mProcessBooklistStyles);
                ReaderEntity entity = findEntity(ReaderEntity.Type.BooklistStyles);
                if (entity != null) {
                    try (XmlImporter importer = new XmlImporter()) {
                        importer.doStyles(entity, progressListener);
                    }
                    entitiesRead |= ImportOptions.BOOK_LIST_STYLES;
                    incStyles = false;
                }
                reset();
            }

            // Seek the preferences entity next, so we can apply any prefs while reading data.
            if (incPrefs) {
                progressListener.onProgressStep(1, mProcessPreferences);
                ReaderEntity entity = findEntity(ReaderEntity.Type.Preferences);
                if (entity != null) {
                    try (XmlImporter importer = new XmlImporter()) {
                        importer.doPreferences(entity, mSettings.getPrefs(), progressListener);
                    }
                    entitiesRead |= ImportOptions.PREFERENCES;
                    incPrefs = false;
                }
                reset();
            }

            // Get first entity.
            ReaderEntity entity = nextEntity();

            // process each entry based on type, unless we are cancelled.
            while (entity != null && !progressListener.isCancelled()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                    Logger.debug(this, "restore", "entity=" + entity.getName());
                }
                switch (entity.getType()) {
                    case Cover:
                        if (incCovers) {
                            progressListener.onProgressStep(1, mProcessCover);
                            restoreCover(entity);
                            coverCount++;
                            // entitiesRead is set when all done
                        }
                        break;

                    case Books:
                        if (incBooks) {
                            // a CSV file with all book data
                            try (Importer importer = new CsvImporter(userContext, mSettings)) {
                                mSettings.results = importer.doBooks(userContext, userLocale,
                                                                     entity.getInputStream(), null,
                                                                     progressListener);
                            }
                            entitiesRead |= ImportOptions.BOOK_CSV;
                            incBooks = false;
                        }
                        break;

                    case Preferences:
                        if (incPrefs) {
                            progressListener.onProgressStep(1, mProcessPreferences);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doPreferences(entity, mSettings.getPrefs(),
                                                       progressListener);
                            }
                            entitiesRead |= ImportOptions.PREFERENCES;
                            incPrefs = false;
                        }
                        break;

                    case BooklistStyles:
                        if (incStyles) {
                            progressListener.onProgressStep(1, mProcessBooklistStyles);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doStyles(entity, progressListener);
                            }
                            entitiesRead |= ImportOptions.BOOK_LIST_STYLES;
                            incStyles = false;
                        }
                        break;

                    case XML:
                        // skip, future extension
                        break;

                    case Database:
                        // never restore from archive.
                        break;

                    case Info:
                        // skip, already handled
                        break;

                    case PreferencesPreV200:
                        // pre-v200 format
                        if (incPrefs) {
                            progressListener.onProgressStep(1, mProcessPreferences);
                            // read them into the 'old' prefs. Migration is done at a later stage.
                            SharedPreferences prefs = userContext.getSharedPreferences(
                                    Prefs.PREF_LEGACY_BOOK_CATALOGUE, Context.MODE_PRIVATE);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doPreferences(entity, prefs, progressListener);
                            }
                            entitiesRead |= ImportOptions.PREFERENCES;
                        }
                        break;

                    case BooklistStylesPreV200:
                        // pre-v200 format was a serialized binary. No longer supported.
                        break;

                    case Unknown:
                        Logger.warn(this, "restore", "type=" + entity.getType());
                        break;
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
            } catch (@NonNull final IOException ignore) {
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
     * Restore a cover file.
     *
     * @param cover to restore
     *
     * @throws IOException on failure
     */
    private void restoreCover(@NonNull final ReaderEntity cover)
            throws IOException {

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
}
