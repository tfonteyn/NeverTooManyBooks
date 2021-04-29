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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

abstract class GrTaskBase
        extends LTask<GrStatus> {

    /**
     * Constructor.
     *
     * @param taskId       a unique task identifier, returned with each message
     * @param taskName     a (preferably unique) name used for identification of this task
     * @param taskListener for sending progress and finish messages to.
     */
    GrTaskBase(final int taskId,
               @NonNull final String taskName,
               @NonNull final TaskListener<GrStatus> taskListener) {
        super(taskId, taskName, taskListener);
    }

    /**
     * Check internet access, and credentials.
     * Can be called from {@link #doWork(Context)}.
     *
     * <pre>{@code
     *     final GoodreadsAuth grAuth = new GoodreadsAuth();
     *     if (!checkCredentials(context, grAuth)) {
     *         return new GrStatus(GrStatus.CREDENTIALS_MISSING);
     *     }
     * }</pre>
     *
     * @param context Current context
     *
     * @return {@code true} if the task can be fully started.
     *
     * @throws NetworkUnavailableException if the network is not enabled
     * @throws CredentialsException        if the credentials were invalid
     */
    boolean checkCredentials(@NonNull final Context context,
                             @NonNull final GoodreadsAuth grAuth)
            throws NetworkUnavailableException, CredentialsException {

        // Got internet?
        if (!NetworkUtils.isNetworkAvailable()) {
            throw new NetworkUnavailableException();
        }

        switch (grAuth.getCredentialStatus(context)) {
            case Valid:
                // The credentials were valid from before, no network access was made
                return true;

            case Missing:
                // If the credentials are missing, we just return {@code false}.
                return false;

            case Invalid:
                // The site was contacted, and we tried to authenticate.
                throw new CredentialsException(R.string.site_goodreads,
                                               GoodreadsAuth.CredentialStatus.Invalid.toString());

            default:
                throw new IllegalArgumentException();
        }
    }
}
