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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.options.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.options.Options;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

/**
 * Implementation of format-agnostic ArchiveWriter methods.
 */
public abstract class ArchiveWriterAbstractBase
        implements ArchiveWriter {

    /** archives are written in this version. */
    protected static final int VERSION = 2;
    /** Log tag. */
    private static final String TAG = "BackupWriterAbstract";
    /** While writing cover images, only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;
    /** export configuration. */
    @NonNull
    protected final ExportHelper mHelper;
    @NonNull
    protected final ExportResults mResults = new ExportResults();
    /** Database Access. */
    @NonNull
    protected final DAO mDb;
    /** progress message. */
    @NonNull
    private final String mProgress_msg_covers;
    /** progress message. */
    @NonNull
    private final String mProgress_msg_covers_skip;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    ArchiveWriterAbstractBase(@NonNull final Context context,
                              @NonNull final ExportHelper helper) {
        mHelper = helper;

        mDb = new DAO(TAG);
        mProgress_msg_covers = context.getString(
                R.string.progress_msg_n_covers_processed_m_missing);
        mProgress_msg_covers_skip = context.getString(
                R.string.progress_msg_n_covers_processed_m_missing_s_skipped);
    }

    /**
     * We always write the latest version archives (no backwards compatibility).
     */
    @Override
    public int getVersion() {
        return VERSION;
    }

    /**
     * Do a full backup.
     *
     * @param context          Current context
     * @param progressListener to send progress updates to
     */
    @Override
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        // do a cleanup first
        mDb.purge();

        // keep track of what we wrote to the archive
        int entitiesWritten = Options.NOTHING;

        boolean incBooks = mHelper.getOption(Options.BOOKS);
        boolean incStyles = mHelper.getOption(Options.STYLES);
        boolean incPrefs = mHelper.getOption(Options.PREFERENCES);

        boolean incCovers = mHelper.getOption(Options.COVERS) && this instanceof SupportsBinary;

        try {
            // If we are doing covers, get the exact number by counting them.
            // We want to put that number into the INFO block.
            // Once you get beyond a 1000 covers this step is getting tedious/slow....
            if (!progressListener.isCancelled() && incCovers) {
                // set the progress bar temporarily in indeterminate mode.
                progressListener.setIndeterminate(true);
                progressListener.onProgress(0, context.getString(R.string.progress_msg_searching));
                doCovers(context, true, progressListener);
                // reset; won't take effect until the next onProgress.
                progressListener.setIndeterminate(null);

                // set as temporary max, but keep in mind the position itself is still == 0
                progressListener.setMax(mResults.coversExported);
            }

            // If we are doing books, generate the file first, so we have the #books
            // which we want to put into the INFO block, and use with the progress listener.
            if (incBooks) {
                prepareBooks(context, progressListener);
            }

            // Calculate the new max value for the progress bar.
            int max = mResults.booksExported + mResults.coversExported;
            // arbitrarily add 10 for the other entities we might do.
            progressListener.setMax(max + 10);

            // Process each component of the Archive, unless we are cancelled.

            // Start with the INFO
            if (!progressListener.isCancelled()) {
                ArchiveInfo info = ArchiveInfo.newInstance(context, VERSION,
                                                           mResults.booksExported,
                                                           mResults.coversExported,
                                                           incStyles, incPrefs);
                writeArchiveHeader(info);
            }

            // Write styles and prefs next. This will facilitate & speedup
            // importing as we'll be seeking in the input archive for them first.

            if (!progressListener.isCancelled() && incStyles) {
                progressListener.onProgressStep(0, context.getString(R.string.lbl_styles));
                writeStyles(context, progressListener);
                progressListener.onProgressStep(mResults.styles, null);
                entitiesWritten |= Options.STYLES;
            }

            if (!progressListener.isCancelled() && incPrefs) {
                progressListener.onProgressStep(0, context.getString(R.string.lbl_settings));
                writePreferences(context, progressListener);
                progressListener.onProgressStep(1, null);
                entitiesWritten |= Options.PREFERENCES;
            }

            // Add the previously generated books file.
            if (!progressListener.isCancelled() && incBooks) {
                progressListener.onProgressStep(1, context.getString(R.string.lbl_books));
                writeBooks(context, progressListener);
                entitiesWritten |= Options.BOOKS;
            }

            // do covers last
            if (!progressListener.isCancelled() && incCovers) {
                doCovers(context, false, progressListener);
                entitiesWritten |= Options.COVERS;
            }

        } finally {
            mHelper.setOptions(entitiesWritten);
            // closing a very large archive will take a while, so keep the progress dialog open
            progressListener.setIndeterminate(true);
            progressListener
                    .onProgress(0, context.getString(R.string.progress_msg_please_wait));
            // reset; won't take effect until the next onProgress.
            progressListener.setIndeterminate(null);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "write"
                       + "|results=" + mResults
                       + "|mHelper=" + mHelper);
        }
        return mResults;
    }

    /**
     * Actual writer should override and close their output.
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
     * Write each cover file corresponding to a book to the archive.
     *
     * <strong>Note:</strong> We update the count during <strong>dryRun</strong> only.
     *
     * @param context Current context
     * @param dryRun  when {@code true}, no writing is done, we only count them.
     *                when {@code false}, we write.
     *
     * @throws IOException on failure
     */
    private void doCovers(@NonNull final Context context,
                          final boolean dryRun,
                          @NonNull final ProgressListener progressListener)
            throws IOException {

        long timeFrom = mHelper.getTimeFrom();

        int exported = 0;
        int skipped = 0;
        int[] missing = new int[2];
        long lastUpdate = 0;
        int delta = 0;

        try (Cursor cursor = mDb.fetchBookUuidList()) {
            final int uuidCol = cursor.getColumnIndex(DBDefinitions.KEY_BOOK_UUID);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                String uuid = cursor.getString(uuidCol);
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    File cover = AppDir.getCoverFile(context, uuid, cIdx);
                    if (cover.exists()) {
                        if (timeFrom < cover.lastModified()) {
                            if (!dryRun) {
                                putFile(cover.getName(), cover);
                            }
                            exported++;
                        } else {
                            skipped++;
                        }
                    } else {
                        missing[cIdx]++;
                    }
                }

                // progress messages only during real-run.
                if (!dryRun) {
                    String message;
                    if (skipped == 0) {
                        message = String.format(mProgress_msg_covers,
                                                exported, missing[0], missing[1]);
                    } else {
                        message = String.format(mProgress_msg_covers_skip,
                                                exported, missing[0], missing[1], skipped);
                    }
                    delta++;
                    long now = System.currentTimeMillis();
                    if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
                        progressListener.onProgressStep(delta, message);
                        lastUpdate = now;
                        delta = 0;
                    }

                }
            }
        }

        // results are collected during dry-run
        if (dryRun) {
            mResults.coversExported += exported;
            mResults.coversMissing[0] += missing[0];
            mResults.coversMissing[1] += missing[1];
            mResults.coversSkipped += skipped;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "doCovers"
                       + "|written=" + exported
                       + "|missing[0]=" + missing[0]
                       + "|missing[1]=" + missing[1]
                       + "|skipped=" + skipped);
        }
    }
}
