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
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import org.apache.commons.compress.archivers.ArchiveEntry;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public abstract class ArchiveReaderAbstract
        implements ArchiveReader {

    private static final String TAG = "BackupReaderAbstract";

    /**
     * we can still read archives from this version and up to our current version.
     *
     * @see ArchiveWriterAbstract
     * <p>
     * v1: original code, used serialized styles and flat xml files for info/prefs
     * <p>
     * v2: writes new xml format supporting lists of elements, styles are xml as wel now.
     * <p>
     * 2020-03-12: we write v2; and can still read v1 with the exception of the binary styles.
     */
    private static final int VERSION_READ = 1;

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

    /** import configuration. */
    @NonNull
    private final ImportHelper mHelper;

    @Nullable
    private Importer.Results mResults;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    protected ArchiveReaderAbstract(@NonNull final Context context,
                                    @NonNull final ImportHelper helper) {
        mDb = new DAO(TAG);
        mHelper = helper;

        mContentResolver = context.getContentResolver();

        mProcessPreferences = context.getString(R.string.progress_msg_process_preferences);
        mProcessBooklistStyles = context.getString(R.string.progress_msg_process_booklist_style);

        mCoversText = context.getString(R.string.lbl_covers);
        mProgress_covers_n_created_m_updated =
                context.getString(R.string.progress_msg_n_created_m_updated);
    }

    @Override
    public int canReadVersion() {
        return VERSION_READ;
    }

    protected Uri getUri() {
        return mHelper.getUri();
    }

    @Override
    public void read(@NonNull final Context context,
                     @NonNull final ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException {

        mResults = new Importer.Results();

        // keep track of what we read from the archive
        int entitiesRead = Options.NOTHING;

        // entities that only appear once
        boolean readStyles = mHelper.getOption(Options.BOOK_LIST_STYLES);
        boolean readPrefs = mHelper.getOption(Options.PREFERENCES);
        boolean readBooks = mHelper.getOption(Options.BOOK_CSV);
        boolean readCovers = mHelper.getOption(Options.COVERS);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // progress counters
        int estimatedSteps = 1;

        try {
            final ArchiveInfo info = getInfo();
            estimatedSteps += info.getBookCount();
            if (readCovers) {
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
            if (readStyles) {
                progressListener.onProgressStep(1, mProcessBooklistStyles);
                ReaderEntity entity = findEntity(ReaderEntity.Type.BooklistStyles);
                if (entity != null) {
                    try (XmlImporter importer = new XmlImporter(null)) {
                        mResults.styles += importer.doStyles(context, entity, progressListener);
                    }
                    entitiesRead |= Options.BOOK_LIST_STYLES;
                    readStyles = false;
                }
                reset();
            }

            // Seek the preferences entity next, so we can apply any prefs while reading data.
            if (readPrefs) {
                progressListener.onProgressStep(1, mProcessPreferences);
                ReaderEntity entity = findEntity(ReaderEntity.Type.Preferences);
                if (entity != null) {
                    try (XmlImporter importer = new XmlImporter(null)) {
                        importer.doPreferences(entity, prefs, progressListener);
                    }
                    entitiesRead |= Options.PREFERENCES;
                    readPrefs = false;
                }
                reset();
            }

            // Get first entity.
            ReaderEntity entity = nextEntity();

            // process each entry based on type, unless we are cancelled.
            while (entity != null && !progressListener.isCancelled()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                    Log.d(TAG, "read|entity=" + entity.getName());
                }
                switch (entity.getType()) {
                    case Cover: {
                        if (readCovers) {
                            String msg;
                            if (importCover(context, entity)) {
                                msg = String.format(mProgress_covers_n_created_m_updated,
                                                    mCoversText,
                                                    mResults.coversCreated,
                                                    mResults.coversUpdated);
                            } else {
                                msg = context.getString(R.string.progress_msg_skip_s,
                                                        context.getString(R.string.lbl_cover));
                            }
                            progressListener.onProgressStep(1, msg);
                            mResults.coversProcessed++;
                            // entitiesRead is set when all done
                        }
                        break;
                    }
                    case Books: {
                        if (readBooks) {
                            // a CSV file with all book data
                            try (Importer importer = new CsvImporter(context, mHelper)) {
                                mHelper.addResults(importer.doBooks(context,
                                                                    entity.getInputStream(),
                                                                    progressListener));
                            }
                            entitiesRead |= Options.BOOK_CSV;
                            readBooks = false;
                        }
                        break;
                    }
                    case Preferences: {
                        if (readPrefs) {
                            progressListener.onProgressStep(1, mProcessPreferences);
                            try (XmlImporter importer = new XmlImporter(null)) {
                                importer.doPreferences(entity, prefs, progressListener);
                            }
                            entitiesRead |= Options.PREFERENCES;
                            readPrefs = false;
                        }
                        break;
                    }
                    case BooklistStyles: {
                        if (readStyles) {
                            progressListener.onProgressStep(1, mProcessBooklistStyles);
                            try (XmlImporter importer = new XmlImporter(null)) {
                                mResults.styles += importer.doStyles(context, entity,
                                                                     progressListener);
                            }
                            entitiesRead |= Options.BOOK_LIST_STYLES;
                            readStyles = false;
                        }
                        break;
                    }
                    case XML:
                        // skip, future extension
                        break;

                    case Database:
                        // never restore from archive.
                        break;

                    case LegacyPreferences:
                    case LegacyBooklistStyles:
                        // BookCatalogue format. No longer supported.
                    case Info:
                        // skip, *should* already be handled.
                        break;

                    case Unknown:
                        Logger.warn(context, TAG, "read|type=" + entity.getType());
                        break;
                }
                entity = nextEntity();
            }
        } finally {
            // report what we actually imported
            if (mResults.coversProcessed > 0) {
                entitiesRead |= Options.COVERS;
            }

            mHelper.setOptions(entitiesRead);
            mHelper.addResults(mResults);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Log.d(TAG, "read"
                           + "|results=" + mResults
                           + "|mSettings=" + mHelper);
            }
            try {
                close();
            } catch (@NonNull final IOException ignore) {
                // ignore
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
     * Detect the type of the passed entry.
     *
     * @param entry to get the type of
     *
     * @return the entity type
     */
    @NonNull
    protected ReaderEntity.Type getBackupEntityType(@NonNull final ArchiveEntry entry) {
        String name = entry.getName().toLowerCase(LocaleUtils.getSystemLocale());

        if (name.endsWith(".jpg") || name.endsWith(".png")) {
            return ReaderEntity.Type.Cover;

        } else if (ArchiveManager.BOOKS_FILE.equalsIgnoreCase(name)
                   || ArchiveManager.BOOKS_PATTERN.matcher(name).find()) {
            return ReaderEntity.Type.Books;

        } else if (ArchiveManager.PREFERENCES.equalsIgnoreCase(name)) {
            return ReaderEntity.Type.Preferences;

        } else if (ArchiveManager.STYLES.equalsIgnoreCase(name)) {
            return ReaderEntity.Type.BooklistStyles;

        } else if (ArchiveManager.DB_FILE.equalsIgnoreCase(name)) {
            return ReaderEntity.Type.Database;

        } else if (name.endsWith(".xml")) {
            return ReaderEntity.Type.XML;

        } else {
            Logger.warn(App.getAppContext(), TAG,
                        "getBackupEntityType|Unknown file=" + entry.getName());
            return ReaderEntity.Type.Unknown;
        }
    }

    /**
     * Import a cover file.
     *
     * @param context Current context
     * @param cover   to import
     *
     * @return {@code true} if an import was done
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean importCover(@NonNull final Context context,
                                @NonNull final ReaderEntity cover) {
        try {
            Date coverDate = cover.getDateModified();

            // see if we have this file already
            File currentCover = AppDir.Covers.getFile(context, cover.getName());
            boolean exists = currentCover.exists();
            if (mHelper.getOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED)) {
                if (exists) {
                    Date currFileDate = new Date(currentCover.lastModified());
                    if (currFileDate.compareTo(coverDate) >= 0) {
                        return false;
                    }
                }
            }
            // save/overwrite
            cover.save(context);

            Objects.requireNonNull(mResults, "mResults");
            if (exists) {
                mResults.coversUpdated++;
            } else {
                mResults.coversCreated++;
            }
            return true;

        } catch (@NonNull final IOException ignore) {
            // we don't want to quit importing just because one fails.
            return false;
        }
    }
}
