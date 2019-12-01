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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;

/**
 * Common utilities.
 */
public final class GoodreadsTasks {

    /** Task 'Results' code. A fake StringRes. */
    static final int GR_RESULT_CODE_AUTHORIZED = 0;
    /** Task 'Results' code. A fake StringRes. */
    static final int GR_RESULT_CODE_AUTHORIZATION_NEEDED = -1;
    /** Task 'Results' code. A fake StringRes. */
    static final int GR_RESULT_CODE_AUTHORIZATION_FAILED = -2;

    private GoodreadsTasks() {
    }

    /**
     * When a typical Goodreads AsyncTask finishes, the 'result' will be a {@code StringRes}
     * to display to the user (or an exception),
     * or a specific code indicating authorization issues .
     * <p>
     * This method provides handling for those outcomes.
     *
     * @param context Current context
     *
     * @return a String to display to the user, or {@code null} when authorization is needed.
     */
    public static String handleResult(@NonNull final Context context,
                                      @NonNull final TaskListener.FinishMessage<Integer> message) {
        //Reminder:  'success' only means the call itself was successful.
        // It still depends on the 'result' code what the next step is.

        // if auth failed, either first or second time, complain and bail out.
        if (message.result == GR_RESULT_CODE_AUTHORIZATION_FAILED
            || (message.result == GR_RESULT_CODE_AUTHORIZATION_NEEDED
                && message.taskId == R.id.TASK_ID_GR_REQUEST_AUTH)) {
            return context.getString(R.string.error_site_authentication_failed,
                                     context.getString(R.string.goodreads));
        }


        if (message.result == GR_RESULT_CODE_AUTHORIZATION_NEEDED) {
            // caller should ask to register
            return null;

        } else if (message.status == TaskListener.TaskStatus.Success) {
            // authenticated fine, just show info results.
            return context.getString(message.result);

        } else {
            // some non-auth related error occurred.
            String msg = context.getString(message.result);
            if (message.exception instanceof FormattedMessageException) {
                msg += ' ' + ((FormattedMessageException) message.exception)
                        .getLocalizedMessage(context);

            } else if (message.exception != null) {
                msg += ' ' + message.exception.getLocalizedMessage();
            }
            return msg;
        }
    }

    /**
     * Check the url for certain keywords that would indicate a cover is, or is not, present.
     *
     * @param url to check
     *
     * @return {@code true} if the url indicates there is an actual image.
     */
    public static boolean hasCover(@Nullable final String url) {
        if (url == null) {
            return false;
        }
        String name = url.toLowerCase(App.getSystemLocale());
        // these string can be part of an image 'name' indicating there is no cover image.
        return !name.contains("/nophoto/") && !name.contains("nocover");
    }

    public enum Status {
        AuthSuccess,
        AuthFailed,
        AuthNeeded,
        CredentialsError,

        TaskQueued,

        BookSent,
        NoIsbn,
        BookNotFound,

        Cancelled,

        NoInternet,
        IOError,
        UnexpectedError
    }

}
