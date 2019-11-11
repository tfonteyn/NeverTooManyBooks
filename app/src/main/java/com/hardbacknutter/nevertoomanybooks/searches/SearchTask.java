/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Searches a single {@link SearchEngine},
 * and send the results back to the {@link SearchCoordinator}.
 * <p>
 * (the 'results' being this while Task object first send to the {@link TaskManager} which
 * then routes it to our creator, the @link SearchCoordinator})
 */
public class SearchTask
        extends ManagedTask {

    private static final String TAG = "SearchTask";

    /** progress title. e.g. "Searching Amazon". */
    private final String mProgressTitle;

    @NonNull
    private final SearchEngine mSearchEngine;

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
    /** search criteria. */
    @Nullable
    private String mNativeId;

    /**
     * Accumulated book info.
     * <p>
     * NEWTHINGS: if you add a new Search task/site that adds non-string based data,
     * {@link SearchCoordinator} #accumulateAllData(int) must be able to handle it.
     */
    @NonNull
    private Bundle mBookData = new Bundle();

    /**
     * Constructor. Will search according to passed parameters.
     * <p>
     * 1. native id
     * 2. ISBN
     * 3. text
     * <p>
     *
     * @param taskManager  Associated task manager
     * @param taskId       identifier
     * @param taskName     thread name, used for debug only really.
     * @param searchEngine the search site manager
     */
    SearchTask(@NonNull final TaskManager taskManager,
               final int taskId,
               @NonNull final String taskName,
               @NonNull final SearchEngine searchEngine) {
        super(taskManager, taskId, taskName);
        mSearchEngine = searchEngine;

        Context context = App.getLocalizedAppContext();
        mProgressTitle = context.getString(R.string.progress_msg_searching_site,
                                           context.getString(mSearchEngine.getNameResId()));
    }

    /**
     * @param nativeId to search for
     */
    void setNativeId(@NonNull final String nativeId) {
        // trims might not be needed, but heck.
        mNativeId = nativeId.trim();
    }

    /**
     * @param isbn to search for
     */
    void setIsbn(@NonNull final String isbn) {
        // trims might not be needed, but heck.
        mIsbn = isbn.trim();
    }

    /**
     * @param author to search for
     */
    void setAuthor(@NonNull final String author) {
        // trims might not be needed, but heck.
        mAuthor = author.trim();
    }

    /**
     * @param title to search for
     */
    void setTitle(@NonNull final String title) {
        // trims might not be needed, but heck.
        mTitle = title.trim();
    }

    /**
     * @param publisher to search for
     */
    void setPublisher(@NonNull final String publisher) {
        // trims might not be needed, but heck.
        mPublisher = publisher.trim();
    }

    /**
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    void setFetchThumbnail(final boolean fetchThumbnail) {
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

    @Override
    @WorkerThread
    protected void runTask() {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Log.d(TAG, "ENTER|runTask|" + mProgressTitle);
        }

        Context context = App.getLocalizedAppContext();

        mTaskManager.sendProgress(this, mProgressTitle, 0);

        try {
            // can we reach the site ?
            if (!NetworkUtils.isAlive(mSearchEngine.getUrl(context))) {
                setFinalError(R.string.error_search_failed_network, null);
                return;
            }

            // SEARCH.
            if (mSearchEngine instanceof SearchEngine.ByNativeId
                && mNativeId != null && !mNativeId.isEmpty()) {
                mBookData = ((SearchEngine.ByNativeId) mSearchEngine)
                        .searchByNativeId(context, mNativeId, mFetchThumbnail);

            } else if (mSearchEngine instanceof SearchEngine.ByIsbn
                       && ISBN.isValid(mIsbn)) {
                mBookData = ((SearchEngine.ByIsbn) mSearchEngine)
                        .searchByIsbn(context, mIsbn, mFetchThumbnail);

            } else if (mSearchEngine instanceof SearchEngine.ByText) {
                mBookData = ((SearchEngine.ByText) mSearchEngine)
                        .search(context, mIsbn, mAuthor, mTitle, mPublisher, mFetchThumbnail);

            } else {
                throw new IllegalStateException("search engine does not implement any search?"
                                                + context.getString(mSearchEngine.getNameResId()));
            }

            if (!mBookData.isEmpty()) {
                // Look for Series name in the book title and clean KEY_TITLE
                mSearchEngine.checkForSeriesNameInTitle(mBookData);
            }

        } catch (@NonNull final CredentialsException e) {
            setFinalError(R.string.error_authentication_failed, e);

        } catch (@NonNull final SocketTimeoutException e) {
            setFinalError(R.string.error_network_timeout, e);

        } catch (@NonNull final MalformedURLException | UnknownHostException e) {
            setFinalError(R.string.error_search_failed_network, e);

        } catch (@NonNull final IOException e) {
            setFinalError(R.string.error_search_failed, e);

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            setFinalError(R.string.error_unknown, e);
        }
    }

    /**
     * Prepare an error message to show after the task finishes.
     *
     * @param error String resource id; without parameter place holders.
     * @param e     (optional) the exception
     */
    private void setFinalError(@StringRes final int error,
                               @Nullable final Exception e) {

        Context context = App.getLocalizedAppContext();
        String siteName = context.getString(mSearchEngine.getNameResId());
        String message = context.getString(error);

        if (e != null) {
            String eMsg;
            if (e instanceof FormattedMessageException) {
                eMsg = ((FormattedMessageException) e).getLocalizedMessage(context);
            } else {
                eMsg = e.getLocalizedMessage();
            }

            if (eMsg != null) {
                message += "\n\n" + eMsg;
            }

            Logger.warn(context, TAG, "setFinalError", "siteName=" + siteName, e);
        }

        mFinalMessage = context.getString(R.string.error_search_exception, siteName, message);
    }
}
