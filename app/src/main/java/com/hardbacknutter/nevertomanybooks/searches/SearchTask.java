/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.backup.FormattedMessageException;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.entities.Series;
import com.hardbacknutter.nevertomanybooks.entities.Series.SeriesDetails;
import com.hardbacknutter.nevertomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;

/**
 * Searches a single {@link SearchEngine},
 * and send the results back to the {@link SearchCoordinator}.
 * <p>
 * (the 'results' being this while Task object first send to the {@link TaskManager} which
 * then routes it to our creator, the @link SearchCoordinator})
 */
public class SearchTask
        extends ManagedTask {

    /** progress title. e.g. "Searching Amazon". */
    private final String mProgressTitle;

    @NonNull
    private final SearchEngine mSearchEngine;

    /** identifier for this task. */
    private final int mTaskId;
    /** whether to fetch thumbnails. */
    private boolean mFetchThumbnail;
    /** search criteria. */
    @Nullable
    private String mAuthor;
    /** search criteria. */
    @Nullable
    private String mTitle;
    /** search criteria. */
    @Nullable
    private String mPublisher;
    /** search criteria. */
    @Nullable
    private String mIsbn;

    /**
     * Accumulated book info.
     * <p>
     * NEWKIND: if you add a new Search task/site that adds non-string based data,
     * {@link SearchCoordinator} #accumulateAllData(int) must be able to handle it.
     */
    @NonNull
    private Bundle mBookData = new Bundle();

    /**
     * Constructor. Will search according to passed parameters. If an ISBN
     * is provided that will be used to the exclusion of all others.
     *
     * @param manager      TaskHandler implementation
     * @param taskId       identifier
     * @param taskName     thread name, used for debug only really.
     * @param searchEngine the search site manager
     */
    SearchTask(@NonNull final TaskManager manager,
               final int taskId,
               @NonNull final String taskName,
               @NonNull final SearchEngine searchEngine) {
        super(manager, taskName);
        mTaskId = taskId;
        mSearchEngine = searchEngine;

        Context context = manager.getContext();
        mProgressTitle = context.getString(R.string.progress_msg_searching_site,
                                           context.getString(mSearchEngine.getNameResId()));
    }

    /**
     * @param isbn to search for
     */
    public void setIsbn(@NonNull final String isbn) {
        // trims might not be needed, but heck.
        mIsbn = isbn.trim();
    }

    /**
     * @param author to search for
     */
    public void setAuthor(@NonNull final String author) {
        // trims might not be needed, but heck.
        mAuthor = author.trim();
    }

    /**
     * @param title to search for
     */
    public void setTitle(@NonNull final String title) {
        // trims might not be needed, but heck.
        mTitle = title.trim();
    }

    /**
     * @param publisher to search for
     */
    public void setPublisher(@NonNull final String publisher) {
        // trims might not be needed, but heck.
        mPublisher = publisher.trim();
    }

    /**
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    public void setFetchThumbnail(final boolean fetchThumbnail) {
        mFetchThumbnail = fetchThumbnail;
    }

    /**
     * Accessor, so when thread has finished, data can be retrieved.
     * <p>
     *
     * @return a Bundle containing standard Book fields AND specific site fields.
     */
    @NonNull
    Bundle getBookData() {
        return mBookData;
    }

    /**
     * @return an identifier for this task.
     */
    int getTaskId() {
        return mTaskId;
    }

    @Override
    @WorkerThread
    protected void runTask() {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Logger.debugEnter(this, "runTask", mProgressTitle);
        }
        // keys? site up? etc...
        if (!mSearchEngine.isAvailable()) {
            setFinalError(R.string.error_not_available, mSearchEngine.getNameResId());
            return;
        }

        mTaskManager.sendProgress(this, mProgressTitle, 0);

        try {
            // SEARCH!
            // manager checks the arguments
            //ENHANCE: its seems most (all?) implementations can return multiple book data bundles quite easily.
            mBookData = mSearchEngine.search(mIsbn, mAuthor, mTitle, mPublisher, mFetchThumbnail);
            if (!mBookData.isEmpty()) {
                // Look for series name in the book title and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }

        } catch (@NonNull final CredentialsException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_authentication_failed);

        } catch (@NonNull final SocketTimeoutException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_network_timeout);

        } catch (@NonNull final MalformedURLException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_search_configuration);

        } catch (@NonNull final UnknownHostException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_search_configuration);

        } catch (@NonNull final IOException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_search_failed);

        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            setFinalError(e);
        }
    }

    @Override
    protected void onTaskFinish() {
        mTaskManager.sendProgress(this, R.string.done, 0);
    }

    /**
     * Look for a title; if present try to get a series name from it and clean the title.
     */
    private void checkForSeriesNameInTitle() {
        String bookTitle = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (bookTitle != null) {
            SeriesDetails details = Series.findSeriesFromBookTitle(bookTitle);
            if (details != null && !details.getName().isEmpty()) {
                ArrayList<Series> list =
                        mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                if (list == null) {
                    list = new ArrayList<>();
                }
                Series newSeries = new Series(details.getName());
                newSeries.setNumber(details.getPosition());
                list.add(newSeries);
                // store Series back
                mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
                // remove series info from the book title.
                bookTitle = bookTitle.substring(0, details.startChar - 1).trim();
                // and store title back
                mBookData.putString(DBDefinitions.KEY_TITLE, bookTitle);
            }
        }
    }

    /**
     * Show a 'known' error after task finish.
     */
    private void setFinalError(@StringRes final int error) {
        Context context = getContext();
        mFinalMessage = context.getString(R.string.error_search_exception, mProgressTitle,
                                          context.getString(error));
    }

    /**
     * Show a 'known' error after task finish.
     */
    private void setFinalError(@SuppressWarnings("SameParameterValue") @StringRes final int error,
                               @StringRes final int arg) {
        Context context = getContext();
        mFinalMessage = context.getString(R.string.error_search_exception, mProgressTitle,
                                          context.getString(error, context.getString(arg)));
    }

    /**
     * Show an unexpected exception message after task finish.
     */
    private void setFinalError(@NonNull final Exception e) {
        Context context = getContext();
        String s;
        try {
            if (e instanceof FormattedMessageException) {
                // we have a clean user message.
                s = ((FormattedMessageException) e).getFormattedMessage(context);
            } else {
                // best shot
                s = e.getLocalizedMessage();
            }
        } catch (@NonNull final RuntimeException e2) {
            s = e2.getClass().getCanonicalName();
        }
        mFinalMessage = context.getString(R.string.error_search_exception, mProgressTitle, s);
    }

}
