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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Handled <strong>ALL</strong> types of {@link ReaderEntity}.
 * <p>
 * i.o.w.: not split in two classes like the ArchiveWriter abstract and abstract-base.
 */
public abstract class ArchiveReaderAbstract
        implements ArchiveReader {

    private static final String TAG = "ArchiveReaderAbstract";

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
    private final ImportManager mHelper;
    @NonNull
    private final ImportResults mResults = new ImportResults();

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    protected ArchiveReaderAbstract(@NonNull final Context context,
                                    @NonNull final ImportManager helper) {
        mDb = new DAO(TAG);
        mHelper = helper;

        mProcessPreferences = context.getString(R.string.progress_msg_process_preferences);
        mProcessBooklistStyles = context.getString(R.string.progress_msg_process_booklist_style);

        mCoversText = context.getString(R.string.lbl_covers);
        mProgress_covers_n_created_m_updated =
                context.getString(R.string.progress_msg_n_created_m_updated);
    }

    protected Uri getUri() {
        return mHelper.getUri();
    }

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException {

        // keep track of what we read from the archive
        int entitiesRead = Options.NOTHING;

        boolean readStyles = (mHelper.getOptions() & Options.STYLES) != 0;
        boolean readPrefs = (mHelper.getOptions() & Options.PREFERENCES) != 0;
        final boolean readBooks = (mHelper.getOptions() & Options.BOOKS) != 0;
        final boolean readCovers = (mHelper.getOptions() & Options.COVERS) != 0;

        // progress counters
        int estimatedSteps = 1;

        try {
            final ArchiveInfo info = mHelper.getInfo(context);
            Objects.requireNonNull(info);
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
                final ReaderEntity entity = findEntity(ArchiveContainerEntry.BooklistStylesXml);
                if (entity != null) {
                    try (Importer importer = new XmlImporter(context, Options.STYLES)) {
                        mResults.add(importer.read(context, entity, progressListener));
                    }
                    entitiesRead |= Options.STYLES;
                    readStyles = false;
                }
                resetToStart();
            }

            // Seek the preferences entity next, so we can apply any prefs while reading data.
            if (readPrefs) {
                progressListener.onProgressStep(1, mProcessPreferences);
                final ReaderEntity entity = findEntity(ArchiveContainerEntry.PreferencesXml);
                if (entity != null) {
                    try (Importer importer = new XmlImporter(context, Options.PREFERENCES)) {
                        mResults.add(importer.read(context, entity, progressListener));
                    }
                    entitiesRead |= Options.PREFERENCES;
                    readPrefs = false;
                }
                resetToStart();
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
                            if (readCover(context, entity)) {
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
                    case BooksCsv: {
                        if (readBooks) {
                            try (Importer importer = new CsvImporter(context, Options.BOOKS)) {
                                mResults.add(importer.read(context, entity, progressListener));
                            }
                            entitiesRead |= Options.BOOKS;
                        }
                        break;
                    }
                    case BooksXml:
                        if (readBooks) {
                            try (Importer importer = new XmlImporter(context, Options.BOOKS)) {
                                mResults.add(importer.read(context, entity, progressListener));
                            }
                            entitiesRead |= Options.BOOKS;
                        }
                        break;

                    case PreferencesXml: {
                        if (readPrefs) {
                            progressListener.onProgressStep(1, mProcessPreferences);
                            try (Importer importer = new XmlImporter(context,
                                                                     Options.PREFERENCES)) {
                                importer.read(context, entity, progressListener);
                            }
                            entitiesRead |= Options.PREFERENCES;
                            readPrefs = false;
                        }
                        break;
                    }
                    case BooklistStylesXml: {
                        if (readStyles) {
                            progressListener.onProgressStep(1, mProcessBooklistStyles);
                            try (Importer importer = new XmlImporter(context, Options.STYLES)) {
                                mResults.add(importer.read(context, entity, progressListener));
                            }
                            entitiesRead |= Options.STYLES;
                            readStyles = false;
                        }
                        break;
                    }
                    case XML:
                        // skip for now, future extension
                    case InfoHeaderXml:
                        // skip, already handled.
                    case Database:
                        // skip, never restore from archive.
                    case LegacyPreferences:
                        // BookCatalogue format. No longer supported.
                    case LegacyBooklistStyles:
                        // BookCatalogue format. No longer supported.
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

            try {
                close();
            } catch (@NonNull final IOException ignore) {
                // ignore
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "read"
                       + "|results=" + mResults
                       + "|mHelper=" + mHelper);
        }
        return mResults;
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
     * @return {@code true} if an import was done
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean readCover(@NonNull final Context context,
                              @NonNull final ReaderEntity cover) {
        try {
            Date coverDate = cover.getDateModified();

            // see if we have this file already
            final File currentCover = AppDir.Covers.getFile(context, cover.getName());
            final boolean exists = currentCover.exists();
            if ((mHelper.getOptions() & ImportManager.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
                if (exists) {
                    final Date currFileDate = new Date(currentCover.lastModified());
                    if (currFileDate.compareTo(coverDate) >= 0) {
                        return false;
                    }
                }
            }

            final File destFile = AppDir.Covers.getFile(context, cover.getName());
            FileUtils.copyInputStream(context, cover.getInputStream(), destFile);
            if (destFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destFile.setLastModified(cover.getDateModified().getTime());
            }

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

    /**
     * Read the next {@link ReaderEntity} from the backup.
     *
     * @return The next entity, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @Nullable
    protected abstract ReaderEntity nextEntity()
            throws IOException;

    /**
     * Scan the input for the desired entity type.
     * It's the responsibility of the caller to call {@link #resetToStart} as needed.
     *
     * @param type to get
     *
     * @return the entity if found, or {@code null} if not found.
     *
     * @throws IOException on failure
     */
    @Nullable
    protected abstract ReaderEntity findEntity(@NonNull ArchiveContainerEntry type)
            throws IOException;

    /**
     * Reset the reader so {@link #nextEntity()} will get the first entity.
     *
     * @throws IOException on failure
     */
    protected abstract void resetToStart()
            throws IOException;
}
