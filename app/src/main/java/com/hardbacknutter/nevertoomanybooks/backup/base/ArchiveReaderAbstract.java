/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlImporter;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
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
    /** The amount of bits we'll shift the last-modified time. (== divide by 65536) */
    private static final int FILE_LM_PRECISION = 16;

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
    private final ImportHelper mHelper;
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
                                    @NonNull final ImportHelper helper) {
        mDb = new DAO(TAG);
        mHelper = helper;

        mProcessPreferences = context.getString(R.string.lbl_settings);
        mProcessBooklistStyles = context.getString(R.string.lbl_styles_long);

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
        @ImportHelper.Options
        int entitiesRead = ImportHelper.OPTIONS_NOTHING;

        boolean readStyles = mHelper.isOptionSet(ImportHelper.OPTIONS_STYLES);
        boolean readPrefs = mHelper.isOptionSet(ImportHelper.OPTIONS_PREFS);
        final boolean readBooks = mHelper.isOptionSet(ImportHelper.OPTIONS_BOOKS);
        final boolean readCovers = mHelper.isOptionSet(ImportHelper.OPTIONS_COVERS);

        // progress counters
        int estimatedSteps = 1;

        try {
            // get the archive info; the helper will read it from the concrete archive.
            final ArchiveInfo info = Objects
                    .requireNonNull(mHelper.getArchiveInfo(context), "info");
            estimatedSteps += info.getBookCount();
            if (readCovers) {
                if (info.hasCoverCount()) {
                    estimatedSteps += info.getCoverCount();
                } else {
                    // We don't have a count, so assume each book has 1 cover.
                    estimatedSteps *= 2;
                }
            }
            progressListener.setMaxPos(estimatedSteps);

            // Seek the styles entity first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (readStyles) {
                progressListener.publishProgressStep(1, mProcessBooklistStyles);
                final ReaderEntity entity = seek(ArchiveContainerEntry.BooklistStylesXml);
                if (entity != null) {
                    try (Importer importer = new XmlImporter(context, mHelper.getOptions())) {
                        mResults.add(importer.read(context, entity, progressListener));
                    }
                    entitiesRead |= ImportHelper.OPTIONS_STYLES;
                    readStyles = false;
                }
                resetToStart();
            }

            // Seek the preferences entity next, so we can apply any prefs while reading data.
            if (readPrefs) {
                progressListener.publishProgressStep(1, mProcessPreferences);
                final ReaderEntity entity = seek(ArchiveContainerEntry.PreferencesXml);
                if (entity != null) {
                    try (Importer importer = new XmlImporter(context, mHelper.getOptions())) {
                        mResults.add(importer.read(context, entity, progressListener));
                    }
                    entitiesRead |= ImportHelper.OPTIONS_PREFS;
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
                            readCover(context, entity);
                            mResults.coversProcessed++;
                            final String msg = String.format(mProgressMessage,
                                                             mCoversText,
                                                             mResults.coversCreated,
                                                             mResults.coversUpdated,
                                                             mResults.coversSkipped);
                            progressListener.publishProgressStep(1, msg);
                            // entitiesRead is set when all done
                        }
                        break;
                    }
                    case BooksCsv: {
                        if (readBooks) {
                            try (Importer importer = new CsvImporter(context,
                                                                     mHelper.getOptions())) {
                                mResults.add(importer.read(context, entity, progressListener));
                            }
                            entitiesRead |= ImportHelper.OPTIONS_BOOKS;
                        }
                        break;
                    }
                    case BooksXml: {
//                        // ENHANCE: XmlImporter does not currently support importing BooksXml
//                        if (readBooks) {
//                            try (Importer importer = new XmlImporter(context,
//                                                                     mHelper.getOptions())) {
//                                mResults.add(importer.read(context, entity, progressListener));
//                            }
//                            entitiesRead |= Options.BOOKS;
//                        }
                        break;
                    }
                    case PreferencesXml: {
                        // yes, we have already read them at the start.
                        // Leaving the code as we might support multiple entries in the future.
                        if (readPrefs) {
                            progressListener.publishProgressStep(1, mProcessPreferences);
                            try (Importer importer = new XmlImporter(context,
                                                                     mHelper.getOptions())) {
                                importer.read(context, entity, progressListener);
                            }
                            entitiesRead |= ImportHelper.OPTIONS_PREFS;
                            readPrefs = false;
                        }
                        break;
                    }
                    case BooklistStylesXml: {
                        // yes, we have already read them at the start.
                        // Leaving the code as we might support multiple entries in the future.
                        if (readStyles) {
                            progressListener.publishProgressStep(1, mProcessBooklistStyles);
                            try (Importer importer = new XmlImporter(context,
                                                                     mHelper.getOptions())) {
                                mResults.add(importer.read(context, entity, progressListener));
                            }
                            entitiesRead |= ImportHelper.OPTIONS_STYLES;
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
                entitiesRead |= ImportHelper.OPTIONS_COVERS;
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
     */
    private void readCover(@NonNull final Context context,
                           @NonNull final ReaderEntity cover) {

        try {
            // see if we have this file already
            File dstFile = AppDir.Covers.getFile(context, cover.getName());
            final boolean exists = dstFile.exists();

            if (exists) {
                // Check which is newer, the local file, or the imported file.
                if (mHelper.isOptionSet(ImportHelper.OPTIONS_UPDATED_BOOKS_SYNC)) {
                    // shift 16 bits to get to +- 1 minute precision.
                    // Using pure milliseconds will create far to many false positives
                    final long importFileDate =
                            cover.getLastModifiedEpochMilli() >> FILE_LM_PRECISION;
                    final long existingFileDate = dstFile.lastModified() >> FILE_LM_PRECISION;
                    if (existingFileDate > importFileDate) {
                        mResults.coversSkipped++;
                        return;
                    }
                }

                // Are we allowed to overwrite at all ?
                if (!mHelper.isOptionSet(ImportHelper.OPTIONS_UPDATED_BOOKS)) {
                    mResults.coversSkipped++;
                    return;
                }
            }

            dstFile = FileUtils.copyInputStream(context, cover.getInputStream(), dstFile);

            if (ImageUtils.isAcceptableSize(dstFile)) {
                //noinspection ResultOfMethodCallIgnored
                dstFile.setLastModified(cover.getLastModifiedEpochMilli());
                if (exists) {
                    mResults.coversUpdated++;
                } else {
                    mResults.coversCreated++;
                }
            }
        } catch (@NonNull final IOException ignore) {
            // we don't want to quit importing just because one fails.
            mResults.coversSkipped++;
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
