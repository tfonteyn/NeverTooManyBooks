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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Implementation of <strong>format-agnostic</strong> {@link ArchiveWriter} methods.
 * <p>
 * The {@link #write(Context, ProgressListener)} method executes the interface method flow.
 */
public abstract class ArchiveWriterAbstractBase
        implements ArchiveWriter {

    /** Log tag. */
    private static final String TAG = "ArchWriterAbstractBase";
    /**
     * Arbitrary number of steps added to the progress max value.
     * This covers the styles/prefs/etc... and a small extra safety.
     */
    private static final int EXTRA_STEPS = 10;

    /** export configuration. */
    @NonNull
    protected final ExportHelper mHelper;
    /** Database Access. */
    @NonNull
    protected final DAO mDb;
    /** The accumulated results. */
    @NonNull
    final ExportResults mResults = new ExportResults();

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
    }

    /**
     * Do a full backup.
     * <ol>
     *     <li>{@link #prepareBooks}</li>
     *     <li>{@link SupportsArchiveHeader#writeHeader}</li>
     *     <li>{@link SupportsStyles#writeStyles}</li>
     *     <li>{@link SupportsPreferences#writePreferences}</li>
     *     <li>{@link #writeBooks}</li>
     *     <li>{@link SupportsCovers#writeCovers}</li>
     *
     * </ol>
     *
     * @param context          Current context
     * @param progressListener to send progress updates to
     *
     * @throws IOException on failure
     */
    @NonNull
    @Override
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        // do a cleanup before we start writing
        mDb.purge();

        final Set<ArchiveWriterRecord.Type> exportEntities = mHelper.getExporterEntries();

        final boolean writeBooks = exportEntities.contains(ArchiveWriterRecord.Type.Books);

        final boolean writeHeader = this instanceof SupportsArchiveHeader;

        final boolean writeStyles = this instanceof SupportsStyles
                                    && exportEntities.contains(
                ArchiveWriterRecord.Type.Styles);

        final boolean writePrefs = this instanceof SupportsPreferences
                                   && exportEntities.contains(
                ArchiveWriterRecord.Type.Preferences);

        final boolean writeCovers = this instanceof SupportsCovers
                                    && exportEntities.contains(
                ArchiveWriterRecord.Type.Cover);

        try {
            int steps = mDb.countBooksForExport(mHelper.getUtcDateTimeSince());
            if (writeCovers) {
                // assume 1 book == 1 cover
                steps = 2 * steps;
            }
            // set as an estimated max value
            progressListener.setMaxPos(steps + EXTRA_STEPS);

            // Prepare data/files we need information of BEFORE we can write the archive header
            if (!progressListener.isCancelled()) {
                mResults.add(prepareBooks(context, progressListener));
            }

            // Recalculate the progress max value using the exact number of books/covers
            // which will now include the back-covers.
            progressListener.setMaxPos(mResults.getBookCount()
                                       + mResults.getCoverCount()
                                       + EXTRA_STEPS);

            // Start with the archive header if required
            if (!progressListener.isCancelled() && writeHeader) {
                final ArchiveInfo archiveInfo = new ArchiveInfo(context, getVersion());
                if (mResults.getBookCount() > 0) {
                    archiveInfo.setBookCount(mResults.getBookCount());
                }
                if (mResults.getCoverCount() > 0) {
                    archiveInfo.setCoverCount(mResults.getCoverCount());
                }
                ((SupportsArchiveHeader) this).writeHeader(context, archiveInfo);
            }

            // Write styles first, and preferences next! This will facilitate & speedup
            // importing as we'll be seeking in the input archive for these.

            if (!progressListener.isCancelled() && writeStyles) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_styles));
                ((SupportsStyles) this).writeStyles(context, progressListener);
                progressListener.publishProgressStep(mResults.styles, null);
            }

            if (!progressListener.isCancelled() && writePrefs) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_settings));
                ((SupportsPreferences) this).writePreferences(context, progressListener);
                progressListener.publishProgressStep(1, null);
            }

            // Add the previously generated books file.
            if (!progressListener.isCancelled() && writeBooks) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_books));
                writeBooks(context, progressListener);
                progressListener.publishProgressStep(1, null);
            }

            // Always do the covers as the last step
            if (!progressListener.isCancelled() && writeCovers && mResults.getCoverCount() > 0) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_covers));
                ((SupportsCovers) this).writeCovers(context, progressListener);
            }

        } finally {

            // closing a very large archive may take a while, so keep the progress dialog open
            progressListener.setIndeterminate(true);
            progressListener.publishProgressStep(
                    0, context.getString(R.string.progress_msg_please_wait));
            // reset; won't take effect until the next publish call.
            progressListener.setIndeterminate(null);
        }

        return mResults;
    }

    /**
     * Prepare the Books and Covers.
     * <p>
     * For each book which will be exported, implementations should call:
     * {@link ExportResults#addBook(long)} and {@link ExportResults#addCover(String)} as needed.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @return ExportResults with the books and cover lists populated.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    protected abstract ExportResults prepareBooks(@NonNull Context context,
                                                  @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Write the books.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    protected abstract void writeBooks(@NonNull Context context,
                                       @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Concrete writer should override and close its output.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        mDb.close();
    }
}
