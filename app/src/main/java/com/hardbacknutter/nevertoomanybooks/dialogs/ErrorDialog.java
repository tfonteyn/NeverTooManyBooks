/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.sql.SQLException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageIOException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.acra.ACRA;

/**
 * All public methods are convenience methods which call {@link #showDialog}.
 * Done this way to ensure specific exceptions are ALWAYS showing the same message
 * and to allow {@link #showDialog} to recurse.
 */
public final class ErrorDialog {

    private static final String DOUBLE_LF = "\n\n";

    private ErrorDialog() {
    }

    /**
     * Show an error message after a {@link StorageException} was thrown.
     *
     * @param context Current context
     * @param e       the error
     */
    public static void storageError(@NonNull final Context context,
                                    @NonNull final StorageException e) {
        final String message;
        if (e instanceof ImageIOException) {
            message = context.getString(R.string.error_storage_not_writable);
        } else {
            // ImageStorageException
            message = context.getString(R.string.error_storage_not_accessible);
        }

        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.error_24px)
                .setTitle(R.string.lbl_images)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }

    private static boolean isSQLException(@NonNull final Throwable e) {
        return e instanceof SQLException || e.getCause() instanceof SQLException;
    }

    /**
     * Show an error message after a generic error was thrown.
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       the error
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final Throwable e) {
        if (isSQLException(e)) {
            ACRA.getErrorReporter().handleException(e, false);
            return;
        }

        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, null, null, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after a generic error was thrown.
     *
     * @param context       Current context
     * @param tag           log tag
     * @param e             the error
     * @param title         Dialog title
     * @param closingAction to use for the positive button
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final Throwable e,
                            @NonNull final CharSequence title,
                            @NonNull final DialogInterface.OnClickListener closingAction) {
        if (isSQLException(e)) {
            ACRA.getErrorReporter().handleException(e, false);
            return;
        }

        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, title, null, closingAction);
    }

    /**
     * Show an error message after a generic error was thrown.
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       the error
     * @param title   Dialog title
     * @param message The message to show
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final Throwable e,
                            @NonNull final CharSequence title,
                            @NonNull final CharSequence message) {
        if (isSQLException(e)) {
            ACRA.getErrorReporter().handleException(e, false);
            return;
        }

        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, title, message, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after a generic error was thrown.
     *
     * @param context       Current context
     * @param tag           log tag
     * @param e             the error
     * @param title         Dialog title
     * @param message       The message to show
     * @param closingAction to use for the positive button
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final Throwable e,
                            @NonNull final CharSequence title,
                            @NonNull final CharSequence message,
                            @NonNull final DialogInterface.OnClickListener closingAction) {
        if (isSQLException(e)) {
            ACRA.getErrorReporter().handleException(e, false);
            return;
        }

        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, title, message, closingAction);
    }

    /**
     * Show the dialog.
     * <p>
     * <strong>Called recursively!</strong>
     *
     * @param context       Current context
     * @param e             The error. SHOULD NOT be {@code null}.
     *                      But MUST be {@code null} when called recursively.
     * @param title         optional; Dialog title; use {@code null} for none
     * @param message       optional; The message to show;
     *                      when {@code null} a message will be derived from the exception
     * @param closingAction to use for the positive/close button
     */
    private static void showDialog(@NonNull final Context context,
                                   @Nullable final Throwable e,
                                   @Nullable final CharSequence title,
                                   @Nullable final CharSequence message,
                                   @NonNull final DialogInterface.OnClickListener closingAction) {

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.error_24px)
                .setTitle(title)
                .setPositiveButton(R.string.ok, closingAction);

        // Try to map the exception to a user friendly message,
        final Optional<String> mappedMsg = ExMsg.map(context, e);

        final String message2;
        if (message != null) {
            // A passed in message is always used
            message2 = message.toString();
        } else {
            // otherwise show the mapped message or freak-out message
            message2 = mappedMsg.orElseGet(() -> ExMsg.getUnexpectedErrorMessage(context));
        }

        // the exception MAY be absent
        if (e != null) {
            // If we have no mapped message, check the raw localised exception msg.
            final String eMessage = mappedMsg.orElseGet(e::getLocalizedMessage);

            // Show the "more" button if the current message
            // and the exception message are different.
            if (!message2.equals(eMessage)) {
                // The "more" button will replace the current dialog, with a new one
                // showing the message concatenated with the full exception message.
                builder.setNeutralButton(R.string.action_more_ellipsis, (d, w) ->
                        // Pass in a null exception to the recursive call to 'showDialog()'
                        // to make sure we don't loop.
                        showDialog(context, null,
                                   title, message2 + DOUBLE_LF + eMessage,
                                   closingAction)
                );
            }
        }
        builder.create()
               .show();
    }
}
