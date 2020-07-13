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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

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
 * Handles <strong>ALL</strong> types of {@link ReaderEntity}.
 * <p>
 * i.o.w.: not split in two classes like the ArchiveWriter abstract and abstract-base.
 */
public abstract class ArchiveReaderAbstract
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "ArchiveReaderAbstract";

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** progress message. */
    private final String mProcessPreferences;
    /** progress message. */
    private final String mCoversText;
    /** progress message. */
    private final String mProgressMessage;
    /** progress message. */
    private final String mProcessBooklistStyles;

    /** import configuration. */
    @NonNull
    private final ImportManager mHelper;
    /** The accumulated results. */
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
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    /**
     * Get the Uri for the user location to read from.
     *
     * @return Uri
     */
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

        boolean readStyles = mHelper.isSet(Options.STYLES);
        boolean readPrefs = mHelper.isSet(Options.PREFS);
        final boolean readBooks = mHelper.isSet(Options.BOOKS);
        final boolean readCovers = mHelper.isSet(Options.COVERS);

        // progress counters
        int estimatedSteps = 1;

        try {
            // get the archive info; the helper will read it from the concrete archive.
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
            progressListener.setProgressMaxPos(estimatedSteps);

            // Seek the styles entity first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (readStyles) {
                progressListener.publishProgressStep(1, mProcessBooklistStyles);
                final ReaderEntity entity = seek(ArchiveContainerEntry.BooklistStylesXml);
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
                progressListener.publishProgressStep(1, mProcessPreferences);
                final ReaderEntity entity = seek(ArchiveContainerEntry.PreferencesXml);
                if (entity != null) {
                    try (Importer importer = new XmlImporter(context, Options.PREFS)) {
                        mResults.add(importer.read(context, entity, progressListener));
                    }
                    entitiesRead |= Options.PREFS;
                    readPrefs = false;
                }
                resetToStart();
            }

            // Get first entity.
            ReaderEntity entity = next();

            // process each entry based on type, unless we are cancelled.
            while (entity != null && !progressListener.isCancelled()) {
                switch (entity.getType()) {
                    case Cover: {
                        if (readCovers) {
                            String msg;
                            if (!readCover(context, entity)) {
                                mResults.coversSkipped++;
                            }
                            msg = String.format(mProgressMessage,
                                                mCoversText,
                                                mResults.coversCreated,
                                                mResults.coversUpdated,
                                                mResults.coversSkipped);
                            progressListener.publishProgressStep(1, msg);
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
                    case BooksXml: {
                        if (readBooks) {
                            try (Importer importer = new XmlImporter(context, Options.BOOKS)) {
                                mResults.add(importer.read(context, entity, progressListener));
                            }
                            entitiesRead |= Options.BOOKS;
                        }
                        break;
                    }
                    case PreferencesXml: {
                        // yes, we have already read them at the start.
                        // Leaving the code as we might support multiple entries in the future.
                        if (readPrefs) {
                            progressListener.publishProgressStep(1, mProcessPreferences);
                            try (Importer importer = new XmlImporter(context, Options.PREFS)) {
                                importer.read(context, entity, progressListener);
                            }
                            entitiesRead |= Options.PREFS;
                            readPrefs = false;
                        }
                        break;
                    }
                    case BooklistStylesXml: {
                        // yes, we have already read them at the start.
                        // Leaving the code as we might support multiple entries in the future.
                        if (readStyles) {
                            progressListener.publishProgressStep(1, mProcessBooklistStyles);
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
                    default: {
                        Logger.warn(context, TAG, "read|type=" + entity.getType());
                        break;
                    }
                }

                entity = next();
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

        return mResults;
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
            long coverDate = cover.getLastModifiedEpochMilli();

            // see if we have this file already
            File file = AppDir.Covers.getFile(context, cover.getName());
            final boolean exists = file.exists();
            if (mHelper.isSet(ImportManager.IMPORT_ONLY_NEW_OR_UPDATED)) {
                if (exists) {
                    if (file.lastModified() > coverDate) {
                        return false;
                    }
                }
            }

            file = FileUtils.copyInputStream(context, cover.getInputStream(),
                                             AppDir.Covers.getFile(context, cover.getName()));

            if (file != null && file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.setLastModified(cover.getLastModifiedEpochMilli());
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
     * Concrete reader should override and close its input.
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
     * Read the next {@link ReaderEntity} from the backup.
     *
     * @return The next entity, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @Nullable
    protected abstract ReaderEntity next()
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
    protected abstract ReaderEntity seek(@NonNull ArchiveContainerEntry type)
            throws IOException;

    /**
     * Reset the reader so {@link #next()} will get the first entity.
     *
     * @throws IOException on failure
     */
    protected abstract void resetToStart()
            throws IOException;
}
