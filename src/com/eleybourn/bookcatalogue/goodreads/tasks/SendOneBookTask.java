package com.eleybourn.bookcatalogue.goodreads.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskBase;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.tasks.TaskListener.TaskProgressMessage;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookLegacyTask} which is used internally by
 * {@link SendBooksLegacyTask}. The core of the task is (should be) identical.
 */
public class SendOneBookTask
        extends TaskBase<Integer> {

    private final long mBookId;

    /**
     * Constructor.
     *
     * @param bookId       the book to send
     * @param taskListener for sending progress and finish messages to.
     */
    public SendOneBookTask(final long bookId,
                           @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_GR_SEND_ONE_BOOK, taskListener);
        mBookId = bookId;
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.SendOneBookTask " + mBookId);

        GoodreadsManager.ExportResult result = null;
        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }

            GoodreadsManager grManager = new GoodreadsManager();
            if (!grManager.hasValidCredentials()) {
                return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZATION_NEEDED;
            }

            try (DAO db = new DAO();
                 BookCursor bookCursor = db.fetchBookForExportToGoodreads(mBookId)) {
                if (bookCursor.moveToFirst()) {
                    if (isCancelled()) {
                        return R.string.progress_end_cancelled;
                    }
                    publishProgress(new TaskProgressMessage(mTaskId,
                                                            R.string.progress_msg_sending));
                    result = grManager.sendOneBook(db, bookCursor.getCursorRow());
                    if (result == GoodreadsManager.ExportResult.sent) {
                        // Record the update
                        db.setGoodreadsSyncDate(mBookId);
                    }
                }
            }
        } catch (@NonNull final CredentialsException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.credentialsError;
            mException = e;
        } catch (@NonNull final BookNotFoundException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.notFound;
            mException = e;
        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.ioError;
            mException = e;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.error;
            mException = e;
        }

        if (result != null) {
            return result.getReasonStringId();
        }
        return R.string.error_unexpected_error;
    }
}
