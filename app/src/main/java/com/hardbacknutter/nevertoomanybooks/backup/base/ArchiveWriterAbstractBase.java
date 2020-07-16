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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Implementation of format-agnostic ArchiveWriter methods.
 * <p>
 * The {@link #write(Context, ProgressListener)} method executes the interface method flow.
 */
public abstract class ArchiveWriterAbstractBase
        implements ArchiveWriter {

    /** Lengthy steps should only send progress updates every 200ms. */
    protected static final int PROGRESS_UPDATE_INTERVAL_IN_MS = 200;
    /** Log tag. */
    private static final String TAG = "ArchWriterAbstractBase";
    /** export configuration. */
    @NonNull
    protected final ExportManager mHelper;
    /** The accumulated results. */
    @NonNull
    protected final ExportResults mResults = new ExportResults();
    /** Database Access. */
    @NonNull
    protected final DAO mDb;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    protected ArchiveWriterAbstractBase(@SuppressWarnings("unused") @NonNull final Context context,
                                        @NonNull final ExportManager helper) {
        mHelper = helper;
        mDb = new DAO(TAG);
    }

    /**
     * Do a full backup.
     * <ol>
     *     <li>{@link SupportsCovers#prepareCovers}</li>
     *     <li>{@link #prepareBooks}</li>
     *     <li>{@link #writeArchiveHeader}</li>
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
    @Override
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        // do a cleanup first
        mDb.purge();

        // keep track of what we wrote to the archive
        int entitiesWritten = Options.NOTHING;

        // All writers must support books.
        final boolean writeBooks = mHelper.isSet(Options.BOOKS);

        // these are optional.
        final boolean writeStyles = this instanceof SupportsStyles
                                    && mHelper.isSet(Options.STYLES);
        final boolean writePrefs = this instanceof SupportsPreferences
                                   && mHelper.isSet(Options.PREFS);
        final boolean writeCovers = this instanceof SupportsCovers
                                    && mHelper.isSet(Options.COVERS);

        try {
            // If we are doing covers, get the exact number by counting them.
            // We want to put that number into the INFO block.
            // Once you get beyond a 1000 covers this step is getting tedious/slow....
            if (!progressListener.isCancelled() && writeCovers) {
                // set the progress bar temporarily in indeterminate mode.
                progressListener.setProgressIsIndeterminate(true);
                progressListener
                        .publishProgress(0, context.getString(R.string.progress_msg_searching));
                ((SupportsCovers) this).prepareCovers(context, progressListener);
                // reset; won't take effect until the next onProgress.
                progressListener.setProgressIsIndeterminate(null);
                // set as temporary max, but keep in mind the position itself is still == 0
                progressListener.setProgressMaxPos(mResults.coversExported);
            }

            // If we are doing books, generate the file first, so we have the #books
            // which we want to put into the INFO block, and use with the progress listener.
            if (!progressListener.isCancelled() && writeBooks) {
                // This counts the books and updates progress.
                prepareBooks(context, progressListener);
            }

            // Calculate the new max value for the progress bar.
            final int max = mResults.booksExported + mResults.coversExported;
            // arbitrarily add 10 for the other entities we might do.
            progressListener.setProgressMaxPos(max + 10);

            // Start with the INFO
            if (!progressListener.isCancelled()) {
                final ArchiveInfo info = new ArchiveInfo(context, getVersion());
                if (mResults.booksExported > 0) {
                    info.setBookCount(mResults.booksExported);
                }
                if (mResults.coversExported > 0) {
                    info.setCoverCount(mResults.coversExported);
                }
                writeArchiveHeader(context, info);
            }

            // Write styles and prefs next. This will facilitate & speedup
            // importing as we'll be seeking in the input archive for them first.

            if (!progressListener.isCancelled() && writeStyles) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_styles));
                ((SupportsStyles) this).writeStyles(context, progressListener);
                progressListener.publishProgressStep(mResults.styles, null);
                entitiesWritten |= Options.STYLES;
            }

            if (!progressListener.isCancelled() && writePrefs) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_settings));
                ((SupportsPreferences) this).writePreferences(context, progressListener);
                progressListener.publishProgressStep(1, null);
                entitiesWritten |= Options.PREFS;
            }

            // Add the previously generated books file.
            if (!progressListener.isCancelled() && writeBooks) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_books));
                writeBooks(context, progressListener);
                progressListener.publishProgressStep(1, null);
                entitiesWritten |= Options.BOOKS;
            }

            // do covers last
            if (!progressListener.isCancelled() && writeCovers) {
                progressListener.publishProgressStep(0, context.getString(R.string.lbl_covers));
                ((SupportsCovers) this).writeCovers(context, progressListener);
                entitiesWritten |= Options.COVERS;
            }

        } finally {
            // Update the result options, setting what we actually exported.
            mHelper.setOptions(entitiesWritten);

            // closing a very large archive will take a while, so keep the progress dialog open
            progressListener.setProgressIsIndeterminate(true);
            progressListener
                    .publishProgress(0, context.getString(R.string.progress_msg_please_wait));
            // reset; won't take effect until the next onProgress.
            progressListener.setProgressIsIndeterminate(null);
        }

        return mResults;
    }

    /**
     * Prepare the books. This is step 1 of writing books.
     * Implementations should count the number of books and populate the archive header.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    protected abstract void prepareBooks(@NonNull Context context,
                                         @NonNull ProgressListener progressListener)
            throws IOException;

    /**
     * Write the books. This is step 2 of writing books.
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

    /**
     * Additional support for Styles.
     */
    public interface SupportsStyles {

        /**
         * Write the styles.
         * <p>
         * See {@link ArchiveWriterAbstract} for a default implementation.
         *
         * @param context          Current context
         * @param progressListener Listener to receive progress information.
         *
         * @throws IOException on failure
         */
        void writeStyles(@NonNull Context context,
                         @NonNull ProgressListener progressListener)
                throws IOException;
    }

    /**
     * Additional support for Preferences.
     */
    public interface SupportsPreferences {

        /**
         * Write the preference settings.
         * <p>
         * See {@link ArchiveWriterAbstract} for a default implementation.
         *
         * @param context          Current context
         * @param progressListener Listener to receive progress information.
         *
         * @throws IOException on failure
         */
        void writePreferences(@NonNull Context context,
                              @NonNull ProgressListener progressListener)
                throws IOException;
    }

    /**
     * Additional support for Covers.
     */
    public interface SupportsCovers {

        /**
         * Prepare the covers. This is step 1 of writing covers.
         * <p>
         * Implementations should count the number of covers and populate the archive header.
         * See {@link ArchiveWriterAbstract} for an implementation.
         *
         * @param context          Current context
         * @param progressListener Listener to receive progress information.
         *
         * @throws IOException on failure
         */
        @WorkerThread
        void prepareCovers(@NonNull Context context,
                           @NonNull ProgressListener progressListener)
                throws IOException;

        /**
         * Write the covers. This is step 2 of writing covers.
         * <p>
         * Implementations should write the files to the archive.
         * See {@link ArchiveWriterAbstract} for an implementation.
         *
         * @param context          Current context
         * @param progressListener Listener to receive progress information.
         *
         * @throws IOException on failure
         */
        @WorkerThread
        void writeCovers(@NonNull Context context,
                         @NonNull ProgressListener progressListener)
                throws IOException;
    }
}
