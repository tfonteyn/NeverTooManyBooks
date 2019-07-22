package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.lang.ref.WeakReference;

import oauth.signpost.exception.OAuthNotAuthorizedException;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Before we can access Goodreads, we must authorize our application to do so.
 */
public class RequestAuthTask
        extends AsyncTask<Void, Object, Integer> {

    private final WeakReference<TaskListener<Object, Integer>> mTaskListener;

    @Nullable
    private Exception mException;

    /**
     * Constructor.
     */
    @UiThread
    public RequestAuthTask(@NonNull final TaskListener<Object, Integer> taskListener) {
        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.RequestAuthTask");

        if (!NetworkUtils.isNetworkAvailable()) {
            return R.string.error_no_internet_connection;
        }
        GoodreadsManager grManager = new GoodreadsManager();
        // should only happen if the developer forgot to add the Goodreads keys.... (me)
        if (!GoodreadsManager.hasKey()) {
            return R.string.gr_auth_error;
        }

        // This next step can take several seconds....
        if (!grManager.hasValidCredentials()) {
            try {
                grManager.requestAuthorization();
            } catch (@NonNull final IOException e) {
                Logger.error(this, e);
                return R.string.gr_access_error;
            } catch (@NonNull final OAuthNotAuthorizedException e) {
                return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZATION_FAILED;
            }
        } else {
            return R.string.gr_auth_access_already_auth;
        }
        if (isCancelled()) {
            // return value not used as onPostExecute is not called
            return R.string.progress_end_cancelled;
        }
        return R.string.info_authorized;
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Integer result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskFinished(R.id.TASK_ID_GR_REQUEST_AUTH, mException == null,
                                               result, mException);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
