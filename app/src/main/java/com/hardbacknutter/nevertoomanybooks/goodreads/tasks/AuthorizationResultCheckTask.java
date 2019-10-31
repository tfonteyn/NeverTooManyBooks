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
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.AuthorizationException;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;

/**
 * Simple class to run in background and verify Goodreads credentials then
 * display a notification based on the result.
 * <p>
 * This task is run as the last part of the Goodreads auth process.
 * <p>
 * Runs in background because it can take several seconds.
 */
public class AuthorizationResultCheckTask
        extends AsyncTask<Void, Void, Boolean> {

    @Nullable
    private Exception mException;

    @Override
    protected Boolean doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.AuthorizationResultCheckTask");

        GoodreadsManager grManager = new GoodreadsManager();
        try {
            grManager.handleAuthenticationAfterAuthorization();
            if (grManager.hasValidCredentials()) {
                return true;
            }
        } catch (@NonNull final AuthorizationException | IOException e) {
            mException = e;
        }
        return false;
    }

    @Override
    protected void onPostExecute(@NonNull final Boolean result) {
        Context context = App.getLocalizedAppContext();

        if (result) {
            App.showNotification(context.getString(R.string.info_authorized),
                                 context.getString(R.string.gr_auth_successful));

        } else {
            String msg;
            if (mException instanceof FormattedMessageException) {
                msg = ((FormattedMessageException) mException).getLocalizedMessage(context);

            } else if (mException != null) {
                msg = context.getString(R.string.gr_auth_error) + ' '
                      + context.getString(R.string.error_if_the_problem_persists,
                                          context.getString(R.string.lbl_send_debug_info));

            } else {
                msg = context.getString(R.string.error_site_authentication_failed,
                                        context.getString(R.string.goodreads));
            }
            App.showNotification(context.getString(R.string.info_not_authorized), msg);
        }
    }
}
