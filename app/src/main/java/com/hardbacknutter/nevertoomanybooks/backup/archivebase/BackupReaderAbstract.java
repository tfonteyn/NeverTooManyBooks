/*
 * @Copyright 2020 HardBackNutter
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.LegacyPreferences;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic BackupReader methods using
 * only a limited set of methods from the base interface.
 */
public abstract class BackupReaderAbstract
        implements BackupReader {

    private static final String TAG = "BackupReaderAbstract";
    protected final ContentResolver mContentResolver;
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** progress message. */
    private final String mProcessPreferences;
    /** progress message. */
    private final String mCoversText;
    /** progress message. */
    private final String mProgress_covers_n_created_m_updated;
    /** progress message. */
    private final String mProcessBooklistStyles;

    /** what and how to import. */
    private ImportHelper mSettings;

    @Nullable
    private Importer.Results mResults;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    protected BackupReaderAbstract(@NonNull final Context context) {
        mDb = new DAO(TAG);

        mContentResolver = context.getContentResolver();

        mProcessPreferences = context.getString(R.string.progress_msg_process_preferences);
        mProcessBooklistStyles = context.getString(R.string.progress_msg_process_booklist_style);

        mCoversText = context.getString(R.string.lbl_covers);
        mProgress_covers_n_created_m_updated =
                context.getString(R.string.progress_msg_n_created_m_updated);
    }

    /**
     * Do a full restore, sending progress to the listener.
     *
     * @throws IOException on failure
     */
    @Override
    public void restore(@NonNull final Context context,
                        @NonNull final ImportHelper settings,
                        @NonNull final ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException {

        mSettings = settings;
        mResults = new Importer.Results();

        // keep track of what we read from the archive
        int entitiesRead = Options.NOTHING;

        // entities that only appear once
        boolean incStyles = (mSettings.options & Options.BOOK_LIST_STYLES) != 0;
        boolean incPrefs = (mSettings.options & Options.PREFERENCES) != 0;
        boolean incBooks = (mSettings.options & Options.BOOK_CSV) != 0;
        boolean incCovers = (mSettings.options & Options.COVERS) != 0;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // progress counters
        int estimatedSteps = 1;

        try {
            final BackupInfo info = getInfo();
            estimatedSteps += info.getBookCount();
            if (incCovers) {
                if (info.hasCoverCount()) {
                    estimatedSteps += info.getCoverCount();
                } else {
                    // We don't have a count, so assume each book has 1 cover.
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
                    try (XmlImporter importer = new XmlImporter(null)) {
                        mResults.styles += importer.doStyles(context, entity, progressListener);
                    }
                    entitiesRead |= Options.BOOK_LIST_STYLES;
                    incStyles = false;
                }
                reset();
            }

            // Seek the preferences entity next, so we can apply any prefs while reading data.
            if (incPrefs) {
                progressListener.onProgressStep(1, mProcessPreferences);
                ReaderEntity entity = findEntity(ReaderEntity.Type.Preferences);
                if (entity != null) {
                    try (XmlImporter importer = new XmlImporter(null)) {
                        importer.doPreferences(entity, prefs, progressListener);
                    }
                    entitiesRead |= Options.PREFERENCES;
                    incPrefs = false;
                }
                reset();
            }

            // Get first entity.
            ReaderEntity entity = nextEntity();

            // process each entry based on type, unless we are cancelled.
            while (entity != null && !progressListener.isCancelled()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                    Log.d(TAG, "restore|entity=" + entity.getName());
                }
                switch (entity.getType()) {
                    case Cover: {
                        if (incCovers) {
                            importCover(context, entity);
                            String msg = String.format(mProgress_covers_n_created_m_updated,
                                                       mCoversText,
                                                       mResults.coversCreated,
                                                       mResults.coversUpdated);
                            progressListener.onProgressStep(1, msg);
                            mResults.coversProcessed++;
                            // entitiesRead is set when all done
                        }
                        break;
                    }
                    case Books: {
                        if (incBooks) {
                            // a CSV file with all book data
                            try (Importer importer = new CsvImporter(context, mSettings)) {
                                mSettings.addResults(importer.doBooks(context,
                                                                      entity.getInputStream(),
                                                                      null,
                                                                      progressListener));
                            }
                            entitiesRead |= Options.BOOK_CSV;
                            incBooks = false;
                        }
                        break;
                    }
                    case Preferences: {
                        if (incPrefs) {
                            progressListener.onProgressStep(1, mProcessPreferences);
                            try (XmlImporter importer = new XmlImporter(null)) {
                                importer.doPreferences(entity, prefs, progressListener);
                            }
                            entitiesRead |= Options.PREFERENCES;
                            incPrefs = false;
                        }
                        break;
                    }
                    case BooklistStyles: {
                        if (incStyles) {
                            progressListener.onProgressStep(1, mProcessBooklistStyles);
                            try (XmlImporter importer = new XmlImporter(null)) {
                                mResults.styles += importer.doStyles(context, entity,
                                                                     progressListener);
                            }
                            entitiesRead |= Options.BOOK_LIST_STYLES;
                            incStyles = false;
                        }
                        break;
                    }
                    case XML:
                        // skip, future extension
                        break;

                    case Database:
                        // never restore from archive.
                        break;

                    case Info:
                        // skip, already handled
                        break;

                    case LegacyPreferences: {
                        // BookCatalogue format
                        if (incPrefs) {
                            progressListener.onProgressStep(1, mProcessPreferences);
                            // read them into the 'old' prefs. Migration is done at a later stage.
                            SharedPreferences legacyPrefs = LegacyPreferences.getPrefs(context);
                            try (XmlImporter importer = new XmlImporter(null)) {
                                importer.doPreferences(entity, legacyPrefs, progressListener);
                            }
                            entitiesRead |= Options.PREFERENCES;
                        }
                        break;
                    }
                    case LegacyBooklistStyles:
                        // BookCatalogue format was a serialized binary. No longer supported.
                        break;

                    case Unknown:
                        Logger.warn(context, TAG, "restore|type=" + entity.getType());
                        break;
                }
                entity = nextEntity();
            }
        } finally {
            // report what we actually imported
            if (mResults.coversProcessed > 0) {
                entitiesRead |= Options.COVERS;
            }

            mSettings.options = entitiesRead;
            mSettings.addResults(mResults);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Log.d(TAG, "restore"
                           + "|results=" + mResults
                           + "|mSettings=" + mSettings);
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
     * Import a cover file.
     *
     * @param context Current context
     * @param cover   to import
     *
     * @throws IOException on failure
     */
    private void importCover(@NonNull final Context context,
                             @NonNull final ReaderEntity cover)
            throws IOException {

        Date coverDate = cover.getDateModified();

        // see if we have this file already
        File currentCover = new File(StorageUtils.getCoverDir(context), cover.getName());
        boolean exists = currentCover.exists();
        if ((mSettings.options & ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
            if (exists) {
                Date currFileDate = new Date(currentCover.lastModified());
                if (currFileDate.compareTo(coverDate) >= 0) {
                    return;
                }
            }
        }
        // save (and overwrite)
        cover.save(context);
        //noinspection ResultOfMethodCallIgnored
        currentCover.setLastModified(coverDate.getTime());

        if (exists) {
            //noinspection ConstantConditions
            mResults.coversUpdated++;
        } else {
            //noinspection ConstantConditions
            mResults.coversCreated++;
        }
    }
}
