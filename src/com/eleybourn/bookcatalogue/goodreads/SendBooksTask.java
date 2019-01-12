package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.GoodreadsTask;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.IOException;

/**
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class SendBooksTask
        extends GoodreadsTask {

    private static final long serialVersionUID = -8519158637447641604L;
    private static final int FIVE_MINUTES = 300;
    /** Number of books with no ISBN. */
    int mNoIsbn;
    /** Number of books that had ISBN but could not be found. */
    int mNotFound;
    /** Number of books successfully sent. */
    int mSent;

    SendBooksTask(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return <tt>false</tt> to requeue, <tt>true</tt> for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {
        boolean result = false;

        // ENHANCE: Work out a way of checking if GR site is up
        //if (!Utils.hostIsAvailable(context, "www.goodreads.com")) {
        //  throw new IOException();
        //}

        if (Utils.isNetworkAvailable(context)) {
            GoodreadsManager grManager = new GoodreadsManager();
            // Ensure we are allowed
            if (grManager.hasValidCredentials()) {
                result = send(queueManager, context, grManager);
            } else {
                Logger.error("no valid credentials");
            }
        } else {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
            Logger.error("network not available");
        }

        return result;
    }

    /**
     * @return <tt>false</tt> to requeue, <tt>true</tt> for success
     */
    protected abstract boolean send(@NonNull final QueueManager queueManager,
                                    @NonNull final Context context,
                                    @NonNull final GoodreadsManager grManager);

    /**
     * Try to export one book.
     *
     * @return <tt>false</tt> on failure, <tt>true</tt> on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsManager grManager,
                        @NonNull final DBA db,
                        final BookRowView bookCursorRow) {
        GoodreadsManager.ExportDisposition disposition;
        Exception exportException = null;
        try {
            disposition = grManager.sendOneBook(db, bookCursorRow);
        } catch (GoodreadsExceptions.BookNotFoundException
                | GoodreadsExceptions.NotAuthorizedException
                | IOException e) {
            disposition = GoodreadsManager.ExportDisposition.error;
            exportException = e;
        }

        // Handle the result
        switch (disposition) {
            case sent:
                // Record the change
                db.setGoodreadsSyncDate(bookCursorRow.getId());
                mSent++;
                break;

            case noIsbn:
                storeEvent(new SendBookEvents.GrNoIsbnEvent(context, bookCursorRow.getId()));
                mNoIsbn++;
                break;

            case notFound:
                storeEvent(new SendBookEvents.GrNoMatchEvent(context, bookCursorRow.getId()));
                mNotFound++;
                break;

            case error:
                this.setException(exportException);
                queueManager.updateTask(this);
                return false;

            case networkError:
                // Only wait 5 minutes on network errors.
                if (getRetryDelay() > FIVE_MINUTES) {
                    setRetryDelay(FIVE_MINUTES);
                }
                queueManager.updateTask(this);
                return false;
        }
        return true;
    }
}
