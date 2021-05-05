/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.ReviewProcessor;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.Review;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ReviewsListApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * Import all a users 'reviews' from Goodreads; a users 'reviews' consists of all the books that
 * they have placed on bookshelves, irrespective of whether they have rated, reviewed
 * or marked the book as 'owned'.
 * <p>
 * Reminder on dates: parsing website dates, use {@link DateTimeFormatter#parse}.
 * Dates from the local database, use {@link FullDateParser}.
 */
public class ImportGrTQTask
        extends GrBaseTQTask {

    /** Log tag. */
    private static final String TAG = "GR.ImportGrTQTask";

    /**
     * Number of books to retrieve in one batch; we are encouraged to make fewer calls, so
     * setting this number high is good. 50 seems to take several seconds to retrieve, so it
     * was chosen.
     */
    private static final int BOOKS_PER_PAGE = 50;

    /**
     * Warning: 2021-05-04: class changed for the post-2.0 update; i.e. new serialVersionUID
     * which means any previously serialized task will be invalid.
     */
    private static final long serialVersionUID = 6943431006937739067L;


    /**
     * The date before which updates are irrelevant.
     * Can be {@code null}, which implies all dates are included.
     */
    @Nullable
    private final LocalDateTime mLastSyncDate;

    /** Flag indicating this job is a sync job: on completion, it will start an export. */
    private final boolean mIsSync;

    /** Date at which this job processed the first page successfully. */
    @Nullable
    private LocalDateTime mStartDate;

    /** Current position in entire list of reviews. */
    private int mPosition;

    /** Total number of reviews user has. */
    private int mTotalBooks;

    /**
     * Flag indicating this is the first time *this* object instance has been called.
     * Must NOT be serialized.
     */
    private transient boolean mFirstCall = true;

    /**
     * Where the actual work is done. Holds no state that needs serialising.
     * Always use {@link #getReviewProcessor(Context)}.
     */
    private transient ReviewProcessor mReviewProcessor;

    /**
     * Constructor.
     *
     * @param description for the task
     * @param isSync      Flag indicating this job is a sync job
     */
    public ImportGrTQTask(@NonNull final String description,
                          final boolean isSync) {
        super(description);

        mPosition = 0;
        mIsSync = isSync;
        // If it's a sync job, then find date of last successful sync and only apply
        // records from after that date. If no other job, then get all.
        if (mIsSync) {
            mLastSyncDate = GoodreadsManager.getLastSyncDate();
        } else {
            mLastSyncDate = null;
        }
    }

    private ReviewProcessor getReviewProcessor(@NonNull final Context context) {
        if (mReviewProcessor == null) {
            mReviewProcessor = new ReviewProcessor(context);
        }
        return mReviewProcessor;
    }

    @Override
    public TaskStatus doWork(@NonNull final Context context) {
        try {
            // Import part of the sync
            final TaskStatus status = importReviews(context);

            // If it's a sync job, start the export part as a new task and save the last syn date
            if (mIsSync) {
                final String desc = context.getString(R.string.gr_title_send_book);
                final TQTask task = new SendBooksGrTQTask(desc, false, true);
                QueueManager.getInstance().enqueueTask(QueueManager.Q_MAIN, task);

                GoodreadsManager.setLastSyncDate(mStartDate);
            }
            return status;

        } catch (@NonNull final DiskFullException | CoverStorageException
                | CredentialsException e) {
            setLastException(e);
            return TaskStatus.Failed;
        }
    }

    /**
     * Repeatedly request review pages until we are done.
     *
     * @param context Current context
     *
     * @return Status
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    private TaskStatus importReviews(@NonNull final Context context)
            throws DiskFullException, CoverStorageException, CredentialsException {

        final GoodreadsAuth grAuth = new GoodreadsAuth();
        final ReviewsListApiHandler api = new ReviewsListApiHandler(context, grAuth);
        final QueueManager queueManager = QueueManager.getInstance();

        // the result from the API call for a single page.
        Bundle pageData;
        // the reviews entry in the 'pageData' bundle.
        ArrayList<Bundle> reviewsFromPage;

        int page = mPosition / BOOKS_PER_PAGE;
        do {
            if (isCancelled()) {
                return TaskStatus.Cancelled;
            }

            // page numbers are 1.. based; start at 0 and increment at start of each loop
            page++;

            // set position to first in page
            mPosition = BOOKS_PER_PAGE * (page - 1);

            try {
                // If we have not started successfully yet, record the date at which
                // the run() was called. This date is used if the job is a sync job.
                final LocalDateTime startDate;
                if (mStartDate == null) {
                    startDate = LocalDateTime.now(ZoneOffset.UTC);
                } else {
                    startDate = null;
                }

                pageData = api.get(page, BOOKS_PER_PAGE);

                // If we succeeded, and this is the first time, save the date
                if (mStartDate == null) {
                    mStartDate = startDate;
                }
            } catch (@NonNull final IOException | SAXException e) {
                setLastException(e);
                return TaskStatus.Failed;
            }

            // Get the total, and if first call, update the task data
            mTotalBooks = (int) pageData.getLong(Review.TOTAL);
            if (mFirstCall) {
                // Update the task data, so the UI can reflect the status
                queueManager.updateTask(this);
                mFirstCall = false;
            }

            // Get the reviews array and process it
            reviewsFromPage = pageData.getParcelableArrayList(Review.REVIEWS);
            if (reviewsFromPage != null) {
                // Note that processing may involve a SLOW thumbnail download...
                // so we don't run the import in a transaction.
                for (final Bundle review : reviewsFromPage) {
                    if (isCancelled()) {
                        return TaskStatus.Cancelled;
                    }

                    if (isNeeded(review)) {
                        getReviewProcessor(context).process(review);
                        // Update after each book, so the UI can reflect the status
                        queueManager.updateTask(this);
                        mPosition++;
                    }
                }
            }
            // loop until no results, or last page
        } while (reviewsFromPage != null && reviewsFromPage.size() == BOOKS_PER_PAGE);

        return TaskStatus.Success;
    }

    /**
     * If we sync'd before, we make sure we only import changes.
     *
     * @param review to check
     *
     * @return {@code true} if this review should be imported
     */
    private boolean isNeeded(@NonNull final Bundle review) {
        if (mLastSyncDate != null) {
            final LocalDateTime reviewUpd = Review.parseDate(review.getString(Review.UPDATED));
            if (reviewUpd != null && reviewUpd.isBefore(mLastSyncDate)) {

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_IMPORT) {
                    Logger.d(TAG, "importReviews",
                             "skipping|grId=" + review.getLong(DBKey.SID_GOODREADS_BOOK));
                }
                // skip to the next review
                return false;
            }
        }
        return true;
    }

    @Override
    public int getCategory() {
        return GrBaseTQTask.CAT_IMPORT;
    }

    /**
     * Make a more informative description.
     *
     * @param context Current context
     */
    @Override
    @NonNull
    @CallSuper
    public String getDescription(@NonNull final Context context) {
        final String base = super.getDescription(context);
        if (mLastSyncDate == null) {
            return base + " (" + context.getString(R.string.x_of_y, mPosition, mTotalBooks) + ')';
        } else {
            return base + " (" + mPosition + ')';
        }
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @throws IOException on failure
     * @see Serializable
     */
    private void writeObject(@NonNull final ObjectOutputStream out)
            throws IOException {

        out.defaultWriteObject();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @throws IOException            on failure
     * @throws ClassNotFoundException on failure
     * @see Serializable
     */
    private void readObject(@NonNull final ObjectInputStream is)
            throws IOException, ClassNotFoundException {

        is.defaultReadObject();
        mFirstCall = true;
    }
}
