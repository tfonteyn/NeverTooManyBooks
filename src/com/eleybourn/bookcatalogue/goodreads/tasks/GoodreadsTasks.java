package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Common utilities.
 * <p>
 * Note that currently there is a bit of a round-about way of starting and handling results.
 * The caller starts the task, with the caller being the listener.
 * The task finishes and sends results to the listener.
 * The listener redirects to {@link GoodreadsTasks#handleResult}
 * <p>
 * Why? well... because this is 'clean' although obviously not efficient.
 * BUT... as the plan is to move to WorkManager instead of task-queue, this at least makes it
 * invisible/transparent to the caller.
 */
public final class GoodreadsTasks {

    /** can be part of an image 'name' from Goodreads indicating there is no cover image. */
    public static final String NO_COVER = "nocover";
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
     * or a specific code indicating issues authentication.
     * <p>
     * This method provides handling for these outcomes.
     *
     * @param view     to tie user messages to
     * @param listener used if authorization needs to be requested.
     *                 Handles a recursive "auth needed" safely.
     */
    public static void handleResult(final int taskId,
                                    final boolean success,
                                    @StringRes final Integer result,
                                    @Nullable final Exception e,
                                    @NonNull final View view,
                                    @NonNull final TaskListener<Object, Integer> listener) {

        //Reminder:  'success' only means the call itself was successful.
        // It still depends on the 'result' code what the next step is.

        Context context = view.getContext();

        // if auth failed, either first or second time, complain and bail out.
        if (result == GR_RESULT_CODE_AUTHORIZATION_FAILED
                || (result == GR_RESULT_CODE_AUTHORIZATION_NEEDED && taskId == R.id.TASK_ID_GR_REQUEST_AUTH)) {
            UserMessage.show(view, context.getString(R.string.error_authorization_failed,
                                                     context.getString(R.string.goodreads)));
            return;
        }

        // ask to register
        if (result == GR_RESULT_CODE_AUTHORIZATION_NEEDED) {
            new AlertDialog.Builder(context)
                    .setIcon(R.drawable.ic_security)
                    .setTitle(R.string.gr_title_auth_access)
                    .setMessage(R.string.gr_action_cannot_be_completed)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.btn_tell_me_more, (d, which) -> {
                        Intent intent = new Intent(context, GoodreadsRegisterActivity.class);
                        context.startActivity(intent);
                    })
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        UserMessage.show(view, R.string.progress_msg_connecting);
                        new RequestAuthTask(listener).execute();
                    })
                    .create()
                    .show();
        } else {
            // authenticated fine, just show info results.
            if (success) {
                UserMessage.show(view, result);

            } else {
                // some non-auth related error occurred.
                String msg = context.getString(result);
                if (e instanceof FormattedMessageException) {
                    msg += ' ' + ((FormattedMessageException) e)
                            .getFormattedMessage(context);
                } else if (e != null) {
                    msg += ' ' + e.getLocalizedMessage();
                }
                UserMessage.show(view, msg);
            }
        }
    }

}
