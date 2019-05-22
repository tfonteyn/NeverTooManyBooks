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

package com.eleybourn.bookcatalogue.searches;

import android.content.res.Resources;
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

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.Series.SeriesDetails;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

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
    @StringRes
    private final int mProgressTitleResId;

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
     * @param manager           TaskHandler implementation
     * @param taskId            identifier
     * @param taskName          thread name, used for debug only really.
     * @param searchEngine the search site manager
     */
    SearchTask(@NonNull final TaskManager manager,
               final int taskId,
               @NonNull final String taskName,
               @NonNull final SearchEngine searchEngine) {
        super(manager, taskName);
        mTaskId = taskId;
        mSearchEngine = searchEngine;
        // cache the resId for convenience
        mProgressTitleResId = mSearchEngine.getSearchingResId();
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
            Logger.debugEnter(this, "runTask",
                              getResources().getString(mProgressTitleResId));
        }
        // keys? site up? etc...
        if (!mSearchEngine.isAvailable()) {
            setFinalError(R.string.error_not_available, mSearchEngine.getNameResId());
            return;
        }

        mTaskManager.sendProgress(this, mProgressTitleResId, 0);

        try {
            // SEARCH!
            // manager checks the arguments
            //ENHANCE/FIXME: its seems most (all?) implementations can return multiple book data bundles quite easily.
            mBookData = mSearchEngine.search(mIsbn, mAuthor, mTitle, mFetchThumbnail);
            if (!mBookData.isEmpty()) {
                // Look for series name in the book title and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }

        } catch (AuthorizationException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(e);

        } catch (SocketTimeoutException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_network_timeout);

        } catch (MalformedURLException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_search_configuration);

        } catch (UnknownHostException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_search_configuration);

        } catch (IOException e) {
            Logger.warn(this, "runTask", e.getLocalizedMessage());
            setFinalError(R.string.error_search_failed);

        } catch (RuntimeException e) {
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
    private void setFinalError(@StringRes final int error,
                               @NonNull final Object... args) {
        Resources res = getResources();
        mFinalMessage = res.getString(R.string.error_search_exception,
                                      res.getString(mProgressTitleResId),
                                      res.getString(error, args));
    }

    /**
     * Show an unexpected exception message after task finish.
     */
    private void setFinalError(@NonNull final Exception e) {
        Resources res = getResources();
        String s;
        try {
            if (e instanceof FormattedMessageException) {
                // we have a clean user message.
                s = ((FormattedMessageException) e).getFormattedMessage(res);
            } else {
                // best shot
                s = e.getLocalizedMessage();
            }
        } catch (RuntimeException e2) {
            s = e2.getClass().getCanonicalName();
        }
        mFinalMessage = res.getString(R.string.error_search_exception,
                                      res.getString(mProgressTitleResId), s);
    }

}
