package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

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

    private Exception mException;

    @Override
    protected Boolean doInBackground(final Void... params) {
        GoodreadsManager grMgr = new GoodreadsManager();
        try {
            grMgr.handleAuthentication();
            if (grMgr.hasValidCredentials()) {
                return true;
            }
        } catch (AuthorizationException | IOException e) {
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
            Context c = App.getAppContext();
            String msg;
            if (mException instanceof FormattedMessageException) {
                msg = ((FormattedMessageException) mException)
                        .getFormattedMessage(c.getResources());

            } else if (mException != null) {
                msg = c.getString(R.string.gr_auth_error) + ' '
                        + c.getString(R.string.error_if_the_problem_persists);

            } else {
                msg = c.getString(R.string.error_authorization_failed,
                                        c.getString(R.string.goodreads));
            }
            App.showNotification(context, R.string.info_not_authorized, msg);
        }
    }
}
