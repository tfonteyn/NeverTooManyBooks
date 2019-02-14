package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;

import java.net.SocketTimeoutException;
import java.util.List;

public class ISFDBGetEditionsTask
        extends AsyncTask<Void, Void, List<String>> {

    @NonNull
    private final String mIsbn;
    @NonNull
    private final ISFDBResultsListener mCallback;

    /**
     * Constructor.
     *
     * @param isbn     to search for
     * @param callback to send results to
     */
    public ISFDBGetEditionsTask(@NonNull final String isbn,
                                @NonNull final ISFDBResultsListener callback) {
        mIsbn = isbn;
        mCallback = callback;
    }

    @Override
    @Nullable
    protected List<String> doInBackground(final Void... params) {
        Editions bookEditions = new Editions(mIsbn);
        try {
            return bookEditions.fetch();
        } catch (SocketTimeoutException e) {
            Logger.info(this, e.getLocalizedMessage());
            return null;
        }
    }

    @Override
    protected void onPostExecute(final List<String> result) {
        mCallback.onGotISFDBEditions(result);
    }
}
