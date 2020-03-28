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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Searches a single {@link SearchEngine}.
 */
public class SearchTask
        extends TaskBase<SearchTask.By, Bundle> {

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
    private String mNativeId;
    /** search criteria. */
    @Nullable
    private String mIsbnStr;
    /** search criteria. */
    @Nullable
    private String mAuthor;
    /** search criteria. */
    @Nullable
    private String mTitle;
    /** search criteria. */
    @Nullable
    private String mPublisher;

    /**
     * Constructor. Will search according to passed parameters.
     * <ol>
     * <li>native id</li>
     * <li>valid ISBN</li>
     * <li>valid barcode</li>
     * <li>text</li>
     * </ol>
     *
     * @param context      Localized context
     * @param taskId       identifier
     * @param searchEngine the search site engine
     * @param taskListener for the results
     */
    SearchTask(@NonNull final Context context,
               final int taskId,
               @NonNull final SearchEngine searchEngine,
               @NonNull final TaskListener<Bundle> taskListener) {
        super(taskId, taskListener);
        mSearchEngine = searchEngine;

        String name = mSearchEngine.getName(context);
        mProgressTitle = context.getString(R.string.progress_msg_searching_site, name);
    }

    /**
     * Set/reset the criteria.
     *
     * @param nativeId to search for
     */
    void setNativeId(@Nullable final String nativeId) {
        mNativeId = nativeId;
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
    protected Bundle doInBackground(@NonNull final SearchTask.By... by) {
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());
        Thread.currentThread().setName(TAG + ' ' + mSearchEngine.getName(context));

        publishProgress(new TaskListener.ProgressMessage(getTaskId(), mProgressTitle));

        try {
            // can we reach the site ?
            NetworkUtils.poke(context,
                              mSearchEngine.getUrl(context),
                              mSearchEngine.getConnectTimeoutMs());

            // sanity check, see #setFetchThumbnail
            if (mFetchThumbnail == null) {
                mFetchThumbnail = new boolean[2];
            }

            Bundle bookData;

            switch (by[0]) {
                case NativeId:
                    Objects.requireNonNull(mNativeId, ErrorMsg.NULL_NATIVE_ID);
                    bookData = ((SearchEngine.ByNativeId) mSearchEngine)
                            .searchByNativeId(context, mNativeId, mFetchThumbnail);
                    break;

                case ISBN:
                    Objects.requireNonNull(mIsbnStr, ErrorMsg.NULL_ISBN_STR);
                    bookData = ((SearchEngine.ByIsbn) mSearchEngine)
                            .searchByIsbn(context, mIsbnStr, mFetchThumbnail);
                    break;

                case Barcode:
                    Objects.requireNonNull(mIsbnStr, ErrorMsg.NULL_ISBN_STR);
                    bookData = ((SearchEngine.ByBarcode) mSearchEngine)
                            .searchByBarcode(context, mIsbnStr, mFetchThumbnail);
                    break;

                case Text:
                    bookData = ((SearchEngine.ByText) mSearchEngine)
                            .search(context, mIsbnStr, mAuthor, mTitle, mPublisher,
                                    mFetchThumbnail);
                    break;

                default:
                    // we should never get here...
                    String name = mSearchEngine.getName(context);
                    throw new IllegalStateException("SearchEngine " + name
                                                    + " does not implement By=" + by[0]);
            }

            if (!bookData.isEmpty()) {
                // Look for Series name in the book title and clean KEY_TITLE
                mSearchEngine.checkForSeriesNameInTitle(bookData);
            }
            return bookData;

        } catch (@NonNull final CredentialsException | IOException
                | SearchEngine.SearchException | RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return null;
        }
    }

    public enum By {
        NativeId, ISBN, Barcode, Text
    }
}
