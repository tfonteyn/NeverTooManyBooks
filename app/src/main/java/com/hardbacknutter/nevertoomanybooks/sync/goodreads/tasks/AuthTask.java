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
import androidx.annotation.WorkerThread;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Before we can access Goodreads, we must authorize our application to do so.
 */
public class AuthTask
        extends LTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.AuthTask";

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    public AuthTask(@NonNull final TaskListener<GrStatus> taskListener) {
        super(R.id.TASK_ID_GR_REQUEST_AUTH, TAG, taskListener);
    }

    /**
     * Prompt the user to register / authorize access.
     *
     * @param context Current context
     */
    public void authorize(@NonNull final Context context) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_security_24)
                .setTitle(R.string.info_authorized_needed)
                .setMessage(R.string.gr_authorization_needed)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.btn_learn_more, (d, w) -> context.startActivity(
                        new Intent(context, GoodreadsRegistrationActivity.class)))
                .setPositiveButton(android.R.string.ok, (d, w) -> execute())
                .create()
                .show();
    }

    /**
     * Authenticate with the site.
     */
    public void authenticate() {
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected GrStatus doWork(@NonNull final Context context)
            throws IOException, CredentialsException {

        if (!NetworkUtils.isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        final GoodreadsAuth grAuth = new GoodreadsAuth();
        if (grAuth.getCredentialStatus(context) == GoodreadsAuth.CredentialStatus.Valid) {
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
    }
}
