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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;

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

        Context context = getContext();
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
    protected void onTaskFinish() {
        mTaskManager.sendProgress(this, R.string.done, 0);
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
            //ENHANCE: it seems most implementations can return multiple book bundles quite easily.
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

        } catch (@NonNull final MalformedURLException | UnknownHostException e) {
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

    /**
     * Look for a title; if present try to get a series name from it and clean the title.
     */
    private void checkForSeriesNameInTitle() {
        String fullTitle = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (fullTitle != null) {
            //TEST: new regex logic
            Matcher matcher = Series.BOOK_SERIES_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                String bookTitle = matcher.group(1);
                String seriesTitleWithNumber = matcher.group(2);

                ArrayList<Series> seriesList =
                        mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                if (seriesList == null) {
                    seriesList = new ArrayList<>();
                }
                Series newSeries = Series.fromString(seriesTitleWithNumber);

                // add to the TOP of the list. This is based on translated books/comics
                // on Goodreads where the series is in the original language, but the
                // series name embedded in the title is in the same language as the title.
                seriesList.add(0, newSeries);

                // store Series back
                mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, seriesList);
                // and store cleaned book title back
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
        String s;
        Context context = getContext();
        try {
            if (e instanceof FormattedMessageException) {
                s = ((FormattedMessageException) e).getLocalizedMessage(context);
            } else {
                s = e.getLocalizedMessage();
            }
        } catch (@NonNull final RuntimeException e2) {
            s = e2.getClass().getCanonicalName();
        }
        mFinalMessage = context.getString(R.string.error_search_exception, mProgressTitle, s);
    }
}
