package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.goodreads.AuthorizationException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

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
        Context context = App.getAppContext();

        if (result) {
            App.showNotification(context, R.string.info_authorized,
                                 context.getString(R.string.gr_auth_successful));

        } else {
            String msg;
            if (mException instanceof FormattedMessageException) {
                msg = ((FormattedMessageException) mException).getFormattedMessage(context);

            } else if (mException != null) {
                msg = context.getString(R.string.gr_auth_error) + ' '
                        + context.getString(R.string.error_if_the_problem_persists);

            } else {
                msg = context.getString(R.string.error_site_authentication_failed,
                                        context.getString(R.string.goodreads));
            }
            App.showNotification(context, R.string.info_not_authorized, msg);
        }
    }
}
