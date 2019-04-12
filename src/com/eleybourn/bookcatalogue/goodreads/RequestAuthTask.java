package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

class RequestAuthTask
        extends AsyncTask<Void, Object, Integer> {

    /** Fragment manager t. */
    private static final String TAG = RequestAuthTask.class.getSimpleName();
    /** Generic identifier. */
    private static final int M_TASK_ID = R.id.TASK_ID_GR_REQUEST_AUTH;
    @NonNull
    private final ProgressDialogFragment<Integer> mFragment;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param fragment ProgressDialogFragment
     */
    @UiThread
    private RequestAuthTask(@NonNull ProgressDialogFragment<Integer> fragment) {
        mFragment = fragment;
    }

    /**
     * @param fm FragmentManager
     */
    @UiThread
    public static void start(@NonNull final FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) {
            ProgressDialogFragment<Integer> frag =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_connecting_to_web_site,
                                                       true, 0);
            RequestAuthTask task = new RequestAuthTask(frag);
            frag.setTask(M_TASK_ID, task);
            frag.show(fm, TAG);
            task.execute();
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Context context = mFragment.getContextWithHorribleClutch();

        GoodreadsManager grMgr = new GoodreadsManager();
        // should only happen if the developer forgot to add the Goodreads keys.... (me)
        if (grMgr.noKey()) {
            return R.string.gr_auth_error;
        }

        // This next step can take several seconds....
        if (!grMgr.hasValidCredentials()) {
            try {
                //noinspection ConstantConditions
                grMgr.requestAuthorization(context);
            } catch (IOException e) {
                Logger.error(this, e);
                return R.string.gr_access_error;
            } catch (AuthorizationException e) {
                return R.string.error_authorization_failed;
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
    protected void onPostExecute(@NonNull final Integer result) {
        mFragment.onTaskFinished(mException == null, result);
    }
}
