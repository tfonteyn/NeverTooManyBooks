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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.AuthorizationException;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

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
                    Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
                    context.startActivity(intent);
                })
                .setPositiveButton(android.R.string.ok, (d, which) ->
                                                                new RequestAuthTask(taskListener)
                                                                        .execute())
                .create()
                .show();
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.RequestAuthTask");

        if (NetworkUtils.networkUnavailable()) {
            return R.string.error_network_no_connection;
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
