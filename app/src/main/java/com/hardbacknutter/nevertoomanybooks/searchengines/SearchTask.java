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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Searches a single {@link SearchEngine}.
 */
public class SearchTask
        extends LTask<Bundle> {

    static final int BY_EXTERNAL_ID = 0;
    static final int BY_ISBN = 1;
    static final int BY_BARCODE = 2;
    static final int BY_TEXT = 3;

    /** Log tag. */
    private static final String TAG = "SearchTask";

    @NonNull
    private final SearchEngine mSearchEngine;
    /** Whether to fetch covers. */
    @Nullable
    private boolean[] mFetchCovers;
    /** What criteria to search by. */
    @By
    private int mBy;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mExternalId;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mIsbnStr;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mAuthor;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mTitle;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mPublisher;

    /**
     * Constructor. Will search according to passed parameters.
     * <ol>
     *      <li>external id</li>
     *      <li>valid ISBN</li>
     *      <li>valid barcode</li>
     *      <li>text</li>
     * </ol>
     *
     * @param searchEngine the search site engine
     * @param taskListener for the results
     */
    SearchTask(@NonNull final SearchEngine searchEngine,
               @NonNull final TaskListener<Bundle> taskListener) {
        super(searchEngine.getId(),
              TAG + ' ' + searchEngine.getName(ServiceLocator.getAppContext()),
              taskListener);

        mSearchEngine = searchEngine;
        mSearchEngine.setCaller(this);
    }

    void setSearchBy(@By final int by) {
        mBy = by;
    }

    /**
     * Set/reset the criteria.
     *
     * @param externalId to search for
     */
    void setExternalId(@Nullable final String externalId) {
        mExternalId = externalId;
    }

    /**
     * Set/reset the criteria.
     *
     * @param isbnStr to search for
     */
    void setIsbn(@Nullable final String isbnStr) {
        mIsbnStr = isbnStr;
    }

    /**
     * Set/reset the criteria.
     *
     * @param author to search for
     */
    void setAuthor(@Nullable final String author) {
        mAuthor = author;
    }

    /**
     * Set/reset the criteria.
     *
     * @param title to search for
     */
    void setTitle(@Nullable final String title) {
        mTitle = title;
    }

    /**
     * Set/reset the criteria.
     *
     * @param publisher to search for
     */
    void setPublisher(@Nullable final String publisher) {
        mPublisher = publisher;
    }

    /**
     * Set/reset the criteria.
     *
     * @param fetchCovers Set to {@code true} if we want to get covers
     */
    void setFetchCovers(@Nullable final boolean[] fetchCovers) {
        if (fetchCovers == null || fetchCovers.length == 0) {
            mFetchCovers = new boolean[2];
        } else {
            mFetchCovers = fetchCovers;
        }
    }

    void startSearch() {
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Bundle doWork(@NonNull final Context context)
            throws StorageException, SearchException, CredentialsException, IOException {

        publishProgress(1, context.getString(R.string.progress_msg_searching_site,
                                             mSearchEngine.getName(context)));

        // Checking this each time a search starts is not needed...
        // But it makes error handling slightly easier and doing
        // it here offloads it from the UI thread.
        if (!NetworkUtils.isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        NetworkUtils.ping(mSearchEngine.getSiteUrl());

        // sanity check, see #setFetchCovers
        if (mFetchCovers == null) {
            mFetchCovers = new boolean[2];
        }

        final Bundle bookData;
        switch (mBy) {
            case BY_EXTERNAL_ID:
                SanityCheck.requireValue(mExternalId, "mExternalId");
                bookData = ((SearchEngine.ByExternalId) mSearchEngine)
                        .searchByExternalId(context, mExternalId, mFetchCovers);
                break;

            case BY_ISBN:
                SanityCheck.requireValue(mIsbnStr, "mIsbnStr");
                bookData = ((SearchEngine.ByIsbn) mSearchEngine)
                        .searchByIsbn(context, mIsbnStr, mFetchCovers);
                break;

            case BY_BARCODE:
                SanityCheck.requireValue(mIsbnStr, "mIsbnStr");
                bookData = ((SearchEngine.ByBarcode) mSearchEngine)
                        .searchByBarcode(context, mIsbnStr, mFetchCovers);
                break;

            case BY_TEXT:
                bookData = ((SearchEngine.ByText) mSearchEngine)
                        .search(context, mIsbnStr, mAuthor, mTitle, mPublisher, mFetchCovers);
                break;

            default:
                // we should never get here...
                throw new IllegalArgumentException("SearchEngine " + mSearchEngine.getName(context)
                                                   + " does not implement By=" + mBy);
        }

        return bookData;
    }

    @IntDef({BY_EXTERNAL_ID, BY_ISBN, BY_BARCODE, BY_TEXT})
    @Retention(RetentionPolicy.SOURCE)
    @interface By {

    }
}
