package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.debug.Logger;

public class ISFDBGetEditionsTask
        extends AsyncTask<Void, Void, ArrayList<String>> {

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
    @UiThread
    public ISFDBGetEditionsTask(@NonNull final String isbn,
                                @NonNull final ISFDBResultsListener callback) {
        mIsbn = isbn;
        mCallback = callback;
    }

    @Override
    @Nullable
    @WorkerThread
    protected ArrayList<String> doInBackground(final Void... params) {
        Editions bookEditions = new Editions(mIsbn);
        try {
            return bookEditions.fetch();
        } catch (SocketTimeoutException e) {
            Logger.info(this, e.getLocalizedMessage());
            return null;
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(final ArrayList<String> result) {
        mCallback.onGotISFDBEditions(result);
    }
}
