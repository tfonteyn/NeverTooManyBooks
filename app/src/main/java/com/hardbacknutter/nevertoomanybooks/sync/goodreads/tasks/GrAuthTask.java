/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

/**
 * Before we can access Goodreads, we must authorize our application to do so.
 */
public class GrAuthTask
        extends VMTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.RequestAuthTask";

    /**
     * Prompt the user to register.
     *
     * @param context Current context
     */
    public void prompt(@NonNull final Context context) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_security_24)
                .setTitle(R.string.info_authorized_needed)
                .setMessage(R.string.gr_authorization_needed)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.btn_learn_more, (d, w) -> context.startActivity(
                        new Intent(context, GoodreadsRegistrationActivity.class)))
                .setPositiveButton(android.R.string.ok, (d, w) -> start())
                .create()
                .show();
    }

    @UiThread
    public void start() {
        execute(R.id.TASK_ID_GR_REQUEST_AUTH);
    }

    @NonNull
    @Override
    @WorkerThread
    protected GrStatus doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG);

        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return new GrStatus(GrStatus.FAILED_NETWORK_UNAVAILABLE);
            }

            final GoodreadsAuth grAuth = new GoodreadsAuth();
            if (grAuth.hasValidCredentials(context)) {
                return new GrStatus(GrStatus.SUCCESS_AUTHORIZATION_ALREADY_GRANTED);
            }

            // This step can take several seconds....
            final Uri authUri = grAuth.requestAuthorization();
            // Open the web page.
            final Intent intent = new Intent(Intent.ACTION_VIEW, authUri);
            // fix for running on Android 9+ for starting the new activity
            // from a non-activity context
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            return new GrStatus(GrStatus.SUCCESS_AUTHORIZATION_REQUESTED);

        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
            return new GrStatus(GrStatus.FAILED_IO_EXCEPTION, e);

        } catch (@NonNull final GoodreadsAuth.AuthorizationException e) {
            return new GrStatus(GrStatus.FAILED_AUTHORIZATION);
        }
    }
}
