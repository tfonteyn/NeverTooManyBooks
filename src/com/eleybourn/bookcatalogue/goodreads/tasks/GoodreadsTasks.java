package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

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


    public static String handleResult(@NonNull final TaskFinishedMessage<Integer> message) {
        return handleResult(message.taskId, message.success, message.result, message.e);
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
    public static String handleResult(final int taskId,
                                      final boolean success,
                                      @StringRes final Integer result,
                                      @Nullable final Exception e) {

        //Reminder:  'success' only means the call itself was successful.
        // It still depends on the 'result' code what the next step is.

        Context context = App.getAppContext();

        // if auth failed, either first or second time, complain and bail out.
        if (result == GR_RESULT_CODE_AUTHORIZATION_FAILED
                || (result == GR_RESULT_CODE_AUTHORIZATION_NEEDED && taskId == R.id.TASK_ID_GR_REQUEST_AUTH)) {
            return context.getString(R.string.error_site_authentication_failed,
                                     context.getString(R.string.goodreads));
        }


        if (result == GR_RESULT_CODE_AUTHORIZATION_NEEDED) {
            // caller should ask to register
            return null;

        } else if (success) {
            // authenticated fine, just show info results.
            return context.getString(result);

        } else {
            // some non-auth related error occurred.
            String msg = context.getString(result);
            if (e instanceof FormattedMessageException) {
                msg += ' ' + ((FormattedMessageException) e).getFormattedMessage(context);
            } else if (e != null) {
                msg += ' ' + e.getLocalizedMessage();
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
