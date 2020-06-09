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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Before we can access Goodreads, we must authorize our application to do so.
 */
public class RequestAuthTask
        extends TaskBase<Integer> {

    /** Log tag. */
    private static final String TAG = "GR.RequestAuthTask";

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
     * @param taskListener for RequestAuthTask to send progress and finish messages to.
     */
    public static void prompt(@NonNull final Context context,
                              @NonNull final TaskListener<Integer> taskListener) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_security)
                .setTitle(R.string.info_authorized_needed)
                .setMessage(R.string.gr_authorization_needed)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.btn_tell_me_more, (d, w) -> {
                    Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
                    context.startActivity(intent);
                })
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        new RequestAuthTask(taskListener).execute())
                .create()
                .show();
    }

    @Override
    @NonNull
    @WorkerThread
    @GrStatus.Status
    protected Integer doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);
        final Context context = App.getTaskContext();

        try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return GrStatus.FAILED_NETWORK_UNAVAILABLE;
            }

            final GoodreadsAuth grAuth = new GoodreadsAuth(context);
            if (grAuth.hasValidCredentials(context)) {
                return GrStatus.SUCCESS_AUTHORIZATION_ALREADY_GRANTED;
            }

            // This step can take several seconds....
            final Uri authUri = grAuth.requestAuthorization(context);
            // Open the web page. TODO: this should really be moved out of doInBackground
            final Intent intent = new Intent(Intent.ACTION_VIEW, authUri);
            // fix for running on Android 9+ for starting the new activity
            // from a non-activity context
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            return GrStatus.SUCCESS_AUTHORIZATION_REQUESTED;

        } catch (@NonNull final IOException e) {
            mException = e;
            Logger.error(context, TAG, e);
            return GrStatus.FAILED_IO_EXCEPTION;

        } catch (@NonNull final GoodreadsAuth.AuthorizationException e) {
            mException = e;
            return GrStatus.FAILED_AUTHORIZATION;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return GrStatus.FAILED_UNEXPECTED_EXCEPTION;
        }
    }
}
