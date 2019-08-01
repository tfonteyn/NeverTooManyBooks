package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.tasks.TaskListener.TaskFinishedMessage;

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
     * This method provides handling for these outcomes.
     *
     * @return a String to display to the user, or {@code null} when authorization is needed.
     */
    public static String handleResult(@NonNull final TaskFinishedMessage<Integer> message) {
        //Reminder:  'success' only means the call itself was successful.
        // It still depends on the 'result' code what the next step is.

        Context context = App.getAppContext();

        // if auth failed, either first or second time, complain and bail out.
        if (message.result == GR_RESULT_CODE_AUTHORIZATION_FAILED
                ||
                (message.result == GR_RESULT_CODE_AUTHORIZATION_NEEDED
                        && message.taskId == R.id.TASK_ID_GR_REQUEST_AUTH)) {
            return context.getString(R.string.error_site_authentication_failed,
                                     context.getString(R.string.goodreads));
        }


        if (message.result == GR_RESULT_CODE_AUTHORIZATION_NEEDED) {
            // caller should ask to register
            return null;

        } else if (message.success) {
            // authenticated fine, just show info results.
            return context.getString(message.result);

        } else {
            // some non-auth related error occurred.
            String msg = context.getString(message.result);
            if (message.exception instanceof FormattedMessageException) {
                msg += ' ' + ((FormattedMessageException) message.exception).getFormattedMessage(
                        context);
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

}
