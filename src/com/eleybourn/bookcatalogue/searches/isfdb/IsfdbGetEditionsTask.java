package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

public class IsfdbGetEditionsTask
        extends AsyncTask<Void, Void, ArrayList<Editions.Edition>> {

    @NonNull
    private final String mIsbn;
    @NonNull
    private final WeakReference<IsfdbResultsListener> mTaskListener;

    /**
     * Constructor.
     *
     * @param isbn         to search for
     * @param taskListener to send results to
     */
    @UiThread
    public IsfdbGetEditionsTask(@NonNull final String isbn,
                                @NonNull final IsfdbResultsListener taskListener) {
        mIsbn = isbn;
        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @Nullable
    @WorkerThread
    protected ArrayList<Editions.Edition> doInBackground(final Void... params) {
        Thread.currentThread().setName("IsfdbGetEditionsTask " + mIsbn);
        try {
            return new Editions().fetch(mIsbn);
        } catch (@NonNull final SocketTimeoutException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.warn(this, "doInBackground", e.getLocalizedMessage());
            }
            return null;
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final ArrayList<Editions.Edition> result) {
        // always send result, even if empty
        if (mTaskListener.get() != null) {
            mTaskListener.get().onGotISFDBEditions(result);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
