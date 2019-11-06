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

import javax.net.ssl.SSLProtocolException;

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
     * NEWTHINGS: if you add a new Search task/site that adds non-string based data,
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

        mTaskManager.sendProgress(this, mProgressTitle, 0);

        try {
            // can we reach the site ?
            if (!NetworkUtils.isAlive(mSearchEngine.getUrl(getContext()))) {
                setFinalError(R.string.error_search_failed_network, null);
                return;
            }

            // SEARCH!
            // manager checks the arguments
            //ENHANCE: it seems most implementations can return multiple book bundles quite easily.
            mBookData = mSearchEngine.search(getContext(),
                                             mIsbn, mAuthor, mTitle, mPublisher, mFetchThumbnail);
            if (!mBookData.isEmpty()) {
                // Look for Series name in the book title and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }

        } catch (@NonNull final CredentialsException e) {
            setFinalError(R.string.error_authentication_failed, e);

        } catch (@NonNull final SocketTimeoutException e) {
            setFinalError(R.string.error_network_timeout, e);

        } catch (@NonNull final MalformedURLException | UnknownHostException e) {
            setFinalError(R.string.error_search_failed_network, e);

        } catch (@NonNull final SSLProtocolException e) {
            // added catch to debug some day...
            setFinalError(R.string.error_unknown, e);
            // javax.net.ssl.SSLProtocolException: Read error: ssl=0xa33a2b40:
            // Failure in SSL library, usually a protocol error
            //  error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT
            //  (external/boringssl/src/crypto/cipher/e_aes.c:1143 0xa0d78e9f:0x00000000)
            //  error:1000008b:SSL routines:OPENSSL_internal:DECRYPTION_FAILED_OR_BAD_RECORD_MAC
            //  (external/boringssl/src/ssl/tls_record.c:277 0xa0d78e9f:0x00000000)
            //  at com.android.org.conscrypt.NativeCrypto.SSL_read(Native Method)
            //  at com.android.org.conscrypt.OpenSSLSocketImpl$SSLInputStream.read(OpenSSLSocketImpl.java:741)
            //  at com.android.okhttp.okio.Okio$2.read(Okio.java:136)
            //  at com.android.okhttp.okio.AsyncTimeout$2.read(AsyncTimeout.java:211)
            //  at com.android.okhttp.okio.RealBufferedSource.indexOf(RealBufferedSource.java:306)
            //  at com.android.okhttp.okio.RealBufferedSource.indexOf(RealBufferedSource.java:300)
            //  at com.android.okhttp.okio.RealBufferedSource.readUtf8LineStrict(RealBufferedSource.java:196)
            //  at com.android.okhttp.internal.http.Http1xStream.readResponse(Http1xStream.java:186)
            //  at com.android.okhttp.internal.http.Http1xStream.readResponseHeaders(Http1xStream.java:127)
            //  at com.android.okhttp.internal.http.HttpEngine.readNetworkResponse(HttpEngine.java:737)
            //  at com.android.okhttp.internal.http.HttpEngine.readResponse(HttpEngine.java:609)
            //  at com.android.okhttp.internal.huc.HttpURLConnectionImpl.execute(HttpURLConnectionImpl.java:471)
            //  at com.android.okhttp.internal.huc.HttpURLConnectionImpl.getResponse(HttpURLConnectionImpl.java:407)
            //  at com.android.okhttp.internal.huc.HttpURLConnectionImpl.getInputStream(HttpURLConnectionImpl.java:244)
            //  at com.android.okhttp.internal.huc.DelegatingHttpsURLConnection.getInputStream(DelegatingHttpsURLConnection.java:210)
            //  at com.android.okhttp.internal.huc.HttpsURLConnectionImpl.getInputStream(Unknown Source:0)
            //  at com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection.open(TerminatorConnection.java:190)

        } catch (@NonNull final IOException e) {
            setFinalError(R.string.error_search_failed, e);

        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            setFinalError(R.string.error_unknown, e);
        }
    }

    /**
     * Look for a title; if present try to get a Series name from it and clean the title.
     */
    private void checkForSeriesNameInTitle() {
        String fullTitle = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (fullTitle != null) {
            Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                String bookTitle = matcher.group(1);
                String seriesTitleWithNumber = matcher.group(2);
                if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                    ArrayList<Series> seriesList =
                            mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                    if (seriesList == null) {
                        seriesList = new ArrayList<>();
                    }
                    Series newSeries = Series.fromString(seriesTitleWithNumber);

                    // add to the TOP of the list. This is based on translated books/comics
                    // on Goodreads where the Series is in the original language, but the
                    // Series name embedded in the title is in the same language as the title.
                    seriesList.add(0, newSeries);

                    // store Series back
                    mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, seriesList);
                    // and store cleaned book title back
                    mBookData.putString(DBDefinitions.KEY_TITLE, bookTitle);
                }
            }
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

        Context context = getContext();
        String siteName = context.getString(mSearchEngine.getNameResId());
        String message = context.getString(error);

        if (e != null) {
            String eMsg;
            if (e instanceof FormattedMessageException) {
                eMsg = ((FormattedMessageException) e).getLocalizedMessage(getContext());
            } else {
                eMsg = e.getLocalizedMessage();
            }

            if (eMsg != null) {
                message += "\n\n" + eMsg;
            }

            Logger.warn(context, this, "setFinalError", "siteName=" + siteName, e);
        }

        mFinalMessage = context.getString(R.string.error_search_exception, siteName, message);
    }
}
