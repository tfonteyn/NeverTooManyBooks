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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;

/**
 * Trivial Activity to handle the callback URI.
 */
public class GoodreadsAuthorizationActivity
        extends BaseActivity {

    private static final String TAG = "GoodreadsAuthActivity";

    private Handler mHandler;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Uri uri = getIntent().getData();
        if (uri != null) {
            mHandler = new Handler(Looper.getMainLooper());

            // Goodreads does not set the verifier...but we may as well check for it.
            // The verifier was added in version 1.0A, and Goodreads seems to implement 1.0.
            //String verifier = uri.getQueryParameter("oauth_verifier");

            // Simple task to verify Goodreads credentials and
            // display a notification with the result.
            // This task is run as the last part of the Goodreads auth process.
            ASyncExecutor.SERIAL.execute(() -> {
                Thread.currentThread().setName(TAG);
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                final Context context = ServiceLocator.getLocalizedAppContext();

                boolean res = false;
                Exception ex = null;
                try {
                    res = new GoodreadsAuth().handleAuthenticationAfterAuthorization(context);
                } catch (@NonNull final GoodreadsAuth.AuthorizationException | IOException e) {
                    ex = e;
                }

                // need 'final' to post()
                final boolean result = res;
                final Exception exception = ex;
                mHandler.post(() -> allDone(context, result, exception));
            });
        }

        // Bring our app back to the top
        startActivity(new Intent(this, BooksOnBookshelf.class));
        finish();
    }

    private void allDone(@NonNull final Context context,
                         final boolean result,
                         @Nullable final Exception exception) {

        final Notifier notifier = ServiceLocator.getInstance().getNotifier();
        final String siteName = context.getString(R.string.site_goodreads);

        if (result) {
            final String msg = context.getString(
                    R.string.info_site_authorization_successful, siteName);
            final Intent intent = new Intent(context, BooksOnBookshelf.class);
            notifier.sendInfo(context, Notifier.ID_GOODREADS, intent, false,
                              R.string.info_authorized, msg);

        } else if (exception != null) {
            final String msg = context.getString(
                    R.string.error_site_authorization_failed, siteName) + ' '
                               + context.getString(R.string.error_unknown_long,
                                                   context.getString(R.string.lbl_send_debug));

            final Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
            notifier.sendError(context, Notifier.ID_GOODREADS, intent, true,
                               R.string.info_not_authorized, msg);
        } else {
            final String msg = context.getString(
                    R.string.error_site_authentication_failed, siteName);
            final Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
            notifier.sendError(context, Notifier.ID_GOODREADS, intent, true,
                               R.string.info_not_authorized, msg);
        }
    }
}
