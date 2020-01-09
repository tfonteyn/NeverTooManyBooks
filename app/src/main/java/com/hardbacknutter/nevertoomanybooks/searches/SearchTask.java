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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Searches a single {@link SearchEngine}.
 */
public class SearchTask
        extends TaskBase<Bundle> {

    /** Log tag. */
    private static final String TAG = "SearchTask";

    /** progress title. e.g. "Searching Amazon". */
    private final String mProgressTitle;

    @NonNull
    private final SearchEngine mSearchEngine;

    /** whether to fetch thumbnails. */
    @Nullable
    private boolean[] mFetchThumbnail;

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
     * Constructor. Will search according to passed parameters.
     * <ol>
     * <li>native id</li>
     * <li>valid ISBN</li>
     * <li>generic barcode</li>
     * <li>text</li>
     * </ol>
     *
     * @param context      Localized context
     * @param taskId       identifier
     * @param searchEngine the search site engine
     */
    SearchTask(@NonNull final Context context,
               final int taskId,
               @NonNull final SearchEngine searchEngine,
               @NonNull final TaskListener<Bundle> taskListener) {
        super(taskId, taskListener);
        mSearchEngine = searchEngine;

        String name = context.getString(mSearchEngine.getNameResId());
        mProgressTitle = context.getString(R.string.progress_msg_searching_site, name);
    }

    /**
     * @param nativeId to search for
     */
    void setNativeId(@Nullable final String nativeId) {
        mNativeId = nativeId;
    }

    /**
     * @param isbn to search for
     */
    void setIsbn(@NonNull final String isbn) {
        mIsbn = isbn;
    }

    /**
     * @param author to search for
     */
    void setAuthor(@NonNull final String author) {
        mAuthor = author;
    }

    /**
     * @param title to search for
     */
    void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    /**
     * @param publisher to search for
     */
    void setPublisher(@NonNull final String publisher) {
        mPublisher = publisher;
    }

    /**
     * Set flag.
     *
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     */
    void setFetchThumbnail(@Nullable final boolean[] fetchThumbnail) {
        if (fetchThumbnail == null || fetchThumbnail.length == 0) {
            mFetchThumbnail = new boolean[2];
        } else {
            mFetchThumbnail = fetchThumbnail;
        }
    }

    @Override
    @Nullable
    protected Bundle doInBackground(final Void... voids) {
        Context localizedAppContext = App.getLocalizedAppContext();
        Thread.currentThread()
              .setName(TAG + ' ' + localizedAppContext.getString(mSearchEngine.getNameResId()));

        publishProgress(new TaskListener.ProgressMessage(mTaskId, mProgressTitle));

        try {
            // can we reach the site ?
            NetworkUtils.poke(localizedAppContext,
                              mSearchEngine.getUrl(localizedAppContext),
                              mSearchEngine.getConnectTimeoutMs());

            Bundle bookData;

            // sanity/paranoia check, see #setFetchThumbnail
            if (mFetchThumbnail == null) {
                mFetchThumbnail = new boolean[2];
            }

            // if we have a native id, and the engine supports it, we can search.
            if (mNativeId != null && !mNativeId.isEmpty()
                && mSearchEngine instanceof SearchEngine.ByNativeId) {
                bookData = ((SearchEngine.ByNativeId) mSearchEngine)
                        .searchByNativeId(localizedAppContext, mNativeId, mFetchThumbnail);

                // If we have a valid ISBN, ...
            } else if (ISBN.isValidIsbn(mIsbn)
                       && mSearchEngine instanceof SearchEngine.ByIsbn) {
                bookData = ((SearchEngine.ByIsbn) mSearchEngine)
                        .searchByIsbn(localizedAppContext, mIsbn, mFetchThumbnail);

                // If we have a generic barcode, ...
            } else if (mIsbn != null && !mIsbn.isEmpty()
                       && mSearchEngine instanceof SearchEngine.ByBarcode) {
                bookData = ((SearchEngine.ByIsbn) mSearchEngine)
                        .searchByIsbn(localizedAppContext, mIsbn, mFetchThumbnail);

                // The implementation is supposed to check the data, so no more checks here.
            } else if (mSearchEngine instanceof SearchEngine.ByText) {
                bookData = ((SearchEngine.ByText) mSearchEngine)
                        .search(localizedAppContext, mIsbn, mAuthor, mTitle, mPublisher,
                                mFetchThumbnail);

            } else {
                String name = localizedAppContext.getString(mSearchEngine.getNameResId());
                throw new IllegalStateException("search engine " + name
                                                + " does not implement any search?");
            }

            if (!bookData.isEmpty()) {
                // Look for Series name in the book title and clean KEY_TITLE
                mSearchEngine.checkForSeriesNameInTitle(bookData);
            }
            return bookData;

        } catch (@NonNull final CredentialsException | IOException | RuntimeException e) {
            Logger.error(localizedAppContext, TAG, e);
            mException = e;
            return null;
        }
    }
}
