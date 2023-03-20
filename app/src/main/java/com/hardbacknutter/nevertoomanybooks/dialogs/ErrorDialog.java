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

import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * All public methods are convenience methods which call {@link #showDialog}.
 * Done this way to ensure specific exceptions are ALWAYS showing the same message
 * and to allow {@link #showDialog} to recurse.
 */
public final class ErrorDialog {
    private ErrorDialog() {
    }

    /**
     * Show an error message after an {@link IOException} was thrown.
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       The IOException
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final IOException e) {
        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, null, null, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after a {@link StorageException} was thrown.
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       The StorageException
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final StorageException e) {
        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, context.getString(R.string.error_storage_not_accessible), null,
                   (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after a {@link DaoWriteException} was thrown.
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       The DaoWriteException
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final DaoWriteException e) {
        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, context.getString(R.string.error_storage_not_writable), null,
                   (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after an {@link SecurityException} was thrown.
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       The SecurityException
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final SecurityException e) {
        LoggerFactory.getLogger().e(tag, e);
        showDialog(context, e, context.getString(R.string.error_unexpected), null,
                   (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after a generic Exception was thrown.
     *
     * @param context       Current context
     * @param e             The error
     * @param title         Dialog title
     * @param closingAction to use for the positive button
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final CharSequence title,
                            @NonNull final DialogInterface.OnClickListener closingAction) {
        showDialog(context, e, title, null, closingAction);
    }

    /**
     * Show an error message after a generic Exception was thrown.
     *
     * @param context Current context
     * @param e       The error
     * @param title   Dialog title
     * @param message The message to show
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final CharSequence title,
                            @NonNull final CharSequence message) {
        showDialog(context, e, title, message, (d, w) -> d.dismiss());
    }

    /**
     * Show an error message after a generic Exception was thrown.
     *
     * @param context       Current context
     * @param e             The error
     * @param title         Dialog title
     * @param message       The message to show
     * @param closingAction to use for the positive button
     */
    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final CharSequence title,
                            @NonNull final CharSequence message,
                            @NonNull final DialogInterface.OnClickListener closingAction) {
        showDialog(context, e, title, message, closingAction);
    }

    /**
     * Show the dialog.
     *
     * @param context       Current context
     * @param e             The error. SHOULD NOT be {@code null}.
     *                      But MUST be {@code null} when called recursively.
     * @param title         optional; Dialog title; use {@code null} for none
     * @param message       optional; The message to show; use {@code null} for none
     * @param closingAction to use for the positive button
     */
    private static void showDialog(@NonNull final Context context,
                                   @Nullable final Throwable e,
                                   @Nullable final CharSequence title,
                                   @Nullable final CharSequence message,
                                   @NonNull final DialogInterface.OnClickListener closingAction) {

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setPositiveButton(android.R.string.ok, closingAction);

        // both are set
        if (title != null && message != null) {
            builder.setTitle(title)
                   .setMessage(message);

            // the exception MAY be absent
            if (e != null) {
                // If an exception is available, the "more" button will
                // show the message concatenated with the actual exception message.
                // Pass in a null exception so we don't loop.
                builder.setNeutralButton(R.string.action_more_ellipsis, (d, w) ->
                        showDialog(context, null,
                                   title, message + "\n\n" + e.getLocalizedMessage(),
                                   closingAction)
                );
            }
            builder.create()
                   .show();
            return;
        }

        // We have a title, but no message
        if (title != null) {
            builder.setTitle(title);

            // Try to map the exception to a localized/simple message.
            final Optional<String> mappedMsg = ExMsg.map(context, e);
            // If we have no mapped message or the exception was null - freak-out!
            final String message2 = mappedMsg.orElseGet(() -> context.getString(
                    R.string.error_unexpected_long, context.getString(R.string.pt_maintenance)));
            builder.setMessage(message2);

            // The exception SHOULD be present but we're paranoid.
            if (e != null) {
                // If an exception is available, the "more" button will
                // show the derived message concatenated with the actual exception message.
                // Pass in a null exception so we don't loop.
                builder.setNeutralButton(R.string.action_more_ellipsis, (d, w) ->
                        showDialog(context, null,
                                   title, message2 + "\n\n" + e.getLocalizedMessage(),
                                   closingAction)
                );
            }
            builder.create()
                   .show();
            return;
        }


        // Worst case, we have no title and no message.
        // This would typically be an IOException

        // Try to map the exception to a localized/simple message.
        final Optional<String> mappedMsg = ExMsg.map(context, e);
        // If we have no mapped message or the exception was null - freak-out!
        final String title2 = mappedMsg.orElseGet(() -> context.getString(
                R.string.error_unexpected_long, context.getString(R.string.pt_maintenance)));
        // Set it as the title and leave the message itself blank.
        builder.setTitle(title2);

        // The exception SHOULD be present but we're paranoid.
        if (e != null) {
            // If an exception is available, the "more" button will
            // show the actual exception message.
            // Pass in a null exception so we don't loop.
            builder.setNeutralButton(R.string.action_more_ellipsis, (d, w) ->
                    showDialog(context, null,
                               title2, e.getLocalizedMessage(),
                               closingAction)
            );
        }

        builder.create()
               .show();
    }
}
