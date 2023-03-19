/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * {@link #show(Context, Throwable, String, String, DialogInterface.OnClickListener)}
 * <p>
 * All other methods are convenience methods which call the above.
 */
public final class ErrorDialog {
    private ErrorDialog() {
    }

    /**
     * Show an error message after an Exception was thrown.
     *
     * @param context Current context
     * @param e       The error
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e) {
        show(context, e, null, null, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after an Exception was thrown.
     *
     * @param context Current context
     * @param e       The error
     * @param title   Dialog title
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final String title) {
        show(context, e, title, null, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after an Exception was thrown.
     *
     * @param context       Current context
     * @param e             The error
     * @param title         Dialog title
     * @param closingAction to use for the positive button
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final String title,
                            @NonNull final DialogInterface.OnClickListener closingAction) {
        show(context, e, title, null, closingAction);
    }

    /**
     * Show an error message after an Exception was thrown.
     *
     * @param context Current context
     * @param e       The error
     * @param title   Dialog title
     * @param message The message to show
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final String title,
                            @NonNull final String message) {
        show(context, e, title, message, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after an Exception was thrown.
     *
     * @param context       Current context
     * @param e             The error.
     *                      Will be {@code null} when called recursively;
     *                      otherwise must NOT be {@code null}
     * @param title         optional; Dialog title string resource id; use {@code null} for none
     * @param message       optional; The message to show; use {@code null} for none
     * @param closingAction to use for the positive button
     */
    public static void show(@NonNull final Context context,
                            @Nullable final Throwable e,
                            @Nullable final String title,
                            @Nullable final String message,
                            @NonNull final DialogInterface.OnClickListener closingAction) {

        String tmpMsg;
        // Explicitly test on null, as we can call this method recursive (see neutral button)
        // If we don't we'd end up with a duplicate concatenated message.
        if (e != null) {
            tmpMsg = ExMsg.map(context, e)
                          .map(errorMsg -> message == null || message.isEmpty()
                                           ? errorMsg
                                           : errorMsg + "\n\n" + message)
                          .orElse(message);
        } else {
            tmpMsg = message;
        }

        if (tmpMsg == null || tmpMsg.isEmpty()) {
            // There is no message whatsoever, freak-out!
            tmpMsg = context.getString(R.string.error_unknown_long,
                                       context.getString(R.string.pt_maintenance));
        }

        final String detailedMessage = tmpMsg;

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setPositiveButton(android.R.string.ok, closingAction);

        if (title != null) {
            builder.setTitle(title);
        }
        builder.setMessage(detailedMessage);

        if (e != null) {
            builder.setNeutralButton(R.string.action_more_ellipsis, (d, w) ->
                    show(context,
                         null, title,
                         detailedMessage + "\n\n" + e.getLocalizedMessage(),
                         // Pass in null so we don't loop
                         closingAction)
            );
        }

        builder.create()
               .show();
    }
}
