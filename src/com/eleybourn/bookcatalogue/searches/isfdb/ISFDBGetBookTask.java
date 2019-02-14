package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;

import java.net.SocketTimeoutException;
import java.util.List;

public class ISFDBGetBookTask
        extends AsyncTask<Void, Void, Bundle> {

    @NonNull
    private final ISFDBResultsListener mCallback;

    @NonNull
    private final List<String> mEditionUrls;
    private final boolean mFetchThumbnail;

    /**
     * Constructor.
     *
     * @param editionUrls    List of ISFDB url's
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     * @param callback       where to send the results to
     */
    public ISFDBGetBookTask(@NonNull final List<String> editionUrls,
                            final boolean fetchThumbnail,
                            @NonNull final ISFDBResultsListener callback) {
        mCallback = callback;
        mEditionUrls = editionUrls;
        mFetchThumbnail = fetchThumbnail;
    }

    @Override
    @Nullable
    protected Bundle doInBackground(final Void... params) {
        ISFDBBook isfdbBook = new ISFDBBook(mEditionUrls);
        try {
            return isfdbBook.fetch(new Bundle(), mFetchThumbnail);
        } catch (SocketTimeoutException e) {
            Logger.info(this, e.getLocalizedMessage());
            return null;
        }
    }

    @Override
    protected void onPostExecute(@Nullable final Bundle result) {
        mCallback.onGotISFDBBook(result);
    }
}
