package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.GoodreadsTask;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class SendBooksTask extends GoodreadsTask {

    private GoodreadsManager mGrManager;
    protected CatalogueDBAdapter mDb;

    /** Number of books with no ISBN */
    int mNoIsbn = 0;
    /** Number of books that had ISBN but could not be found */
    int mNotFound = 0;
    /** Number of books successfully sent */
    int mSent = 0;

    SendBooksTask(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return false to requeue, true for success
     */
    @Override
    public boolean run(final @NonNull QueueManager queueManager, final @NonNull Context context) {
        boolean result = false;
        try {
            // Use the app context; the calling activity may go away
            mDb = new CatalogueDBAdapter(context.getApplicationContext());

            // ENHANCE: Work out a way of checking if GR site is up
            //if (!Utils.hostIsAvailable(context, "www.goodreads.com")) {
            //	throw new GoodreadsManager.GoodreadsExceptions.NetworkException();
            //}

            if (!Utils.isNetworkAvailable(context)) {
                // Only wait 5 minutes max on network errors.
                if (getRetryDelay() > 300) {
                    setRetryDelay(300);
                }
                throw new GoodreadsExceptions.NetworkException();
            }

            mGrManager = new GoodreadsManager();
            // Ensure we are allowed
            if (!mGrManager.hasValidCredentials()) {
                throw new GoodreadsExceptions.NotAuthorizedException();
            }

            result = send(queueManager, context);

        } catch (GoodreadsExceptions.NetworkException | GoodreadsExceptions.NotAuthorizedException e) {
            Logger.error(e);
        } finally {
            if (mDb != null) {
                mDb.close();
            }
        }
        return result;
    }

    /**
     * @return false to requeue, true for success
     */
    abstract protected boolean send(final @NonNull QueueManager queueManager,
                                    final @NonNull Context context);

    /**
     * Try to export one book
     *
     * @return false on failure, true on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(final @NonNull QueueManager queueManager,
                        final @NonNull Context context,
                        final BookRowView bookCursorRow) {
        GoodreadsManager.ExportDisposition disposition;
        Exception exportException = null;
        try {
            disposition = mGrManager.sendOneBook(mDb, bookCursorRow);
        } catch (Exception e) {
            disposition = GoodreadsManager.ExportDisposition.error;
            exportException = e;
        }

        // Handle the result
        switch (disposition) {
            case sent:
                // Record the change
                mDb.setGoodreadsSyncDate(bookCursorRow.getId());
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
                if (getRetryDelay() > 300) {
                    setRetryDelay(300);
                }
                queueManager.updateTask(this);
                return false;
        }
        return true;
    }
}
