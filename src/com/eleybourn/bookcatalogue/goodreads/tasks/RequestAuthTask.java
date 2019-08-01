package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.AuthorizationException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskBase;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Before we can access Goodreads, we must authorize our application to do so.
 */
public class RequestAuthTask
        extends TaskBase<Integer> {

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RequestAuthTask(@NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_GR_REQUEST_AUTH, taskListener);
    }

    /**
     * Prompt the user to register.
     *
     * @param context      Current context
     * @param taskListener for sending progress and finish messages to.
     */
    public static void needsRegistration(@NonNull final Context context,
                                         @NonNull final TaskListener<Integer> taskListener) {
        new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_security)
                .setTitle(R.string.gr_title_auth_access)
                .setMessage(R.string.gr_action_cannot_be_completed)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_tell_me_more, (d, which) -> {
                    Intent intent = new Intent(context, GoodreadsRegisterActivity.class);
                    context.startActivity(intent);
                })
                .setPositiveButton(android.R.string.ok, (d, which) ->
                        new RequestAuthTask(taskListener).execute())
                .create()
                .show();
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
                mException = e;
                return R.string.gr_access_error;
            } catch (@NonNull final AuthorizationException e) {
                mException = e;
                return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZATION_FAILED;
            }
        } else {
            return R.string.gr_auth_access_already_auth;
        }
        if (isCancelled()) {
            return R.string.progress_end_cancelled;
        }
        return R.string.info_authorized;
    }
}
