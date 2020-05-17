/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public final class StandardDialogs {

    /** Log tag. */
    private static final String TAG = "StandardDialogs";

    /**
     * The title to be used by generic Dialogs.
     * <p>
     * <br>type: {@code int} (stringId)
     */
    public static final String BKEY_DIALOG_TITLE = TAG + ":title";

    /**
     * The body message to be used by generic Dialogs.
     * <p>
     * <br>type: {@code String}
     */
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    private StandardDialogs() {
    }

    /**
     * Show a dialog asking if unsaved edits should be ignored.
     * <p>
     * To show the Save and/or Exit button, you must provide a Runnable, even an empty one.
     *
     * @param context   Current context
     * @param onSave    (optional) Runnable to execute if the user clicks the Save button.
     * @param onDiscard (optional) Runnable to execute if the user clicks the Discard button.
     */
    public static void unsavedEdits(@NonNull final Context context,
                                    @Nullable final Runnable onSave,
                                    @Nullable final Runnable onDiscard) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_details_have_changed)
                .setMessage(R.string.confirm_unsaved_edits)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setNeutralButton(R.string.action_edit, (d, w) -> d.dismiss());

        if (onDiscard != null) {
            builder.setNegativeButton(R.string.action_discard, (d, w) -> onDiscard.run());
        }
        if (onSave != null) {
            builder.setPositiveButton(R.string.action_save, (d, w) -> onSave.run());
        }

        builder.show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param series    Series we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteSeries(@NonNull final Context context,
                                    @NonNull final Series series,
                                    @NonNull final Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_delete_series)
                .setMessage(context.getString(R.string.confirm_delete_series,
                                              series.getLabel(context)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param tocEntry  TocEntry we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteTocEntry(@NonNull final Context context,
                                      @NonNull final TocEntry tocEntry,
                                      @NonNull final Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_delete_toc_entry)
                .setMessage(context.getString(R.string.confirm_delete_toc_entry,
                                              tocEntry.getLabel(context),
                                              tocEntry.getAuthor().getLabel(context)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context    Current context
     * @param title      Title of book we're about to delete
     * @param authorList Authors of book we're about to delete
     * @param onConfirm  Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteBook(@NonNull final Context context,
                                  @NonNull final String title,
                                  @NonNull final List<Author> authorList,
                                  @NonNull final Runnable onConfirm) {

        // Format the list of authors nicely
        StringBuilder authors = new StringBuilder();
        if (authorList.isEmpty()) {
            authors.append('<')
                   .append(context.getString(R.string.unknown)
                                  .toUpperCase(LocaleUtils.getUserLocale(context)))
                   .append('>');
        } else {
            // "a1, a2 and a3"
            authors.append(authorList.get(0).getLabel(context));
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getLabel(context));
            }

            if (authorList.size() > 1) {
                authors.append(' ').append(context.getString(R.string.list_and)).append(' ')
                       .append(authorList.get(authorList.size() - 1).getLabel(context));
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_delete_book)
                .setMessage(context.getString(R.string.confirm_delete_book, title, authors))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Show a registration request dialog.
     *
     * @param context   Current context
     * @param siteResId Site string resource ID
     * @param intent    Intent to start if the user wants more information.
     * @param required  Show third button allowing disabling message or not.
     * @param prefName  Preference name to use for disabling the message if requested
     */
    public static void registerOnSite(@NonNull final Context context,
                                      @StringRes final int siteResId,
                                      @NonNull final Intent intent,
                                      final boolean required,
                                      @NonNull final String prefName) {

        String site = context.getString(siteResId);
        String message;
        if (required) {
            message = context.getString(R.string.confirm_registration_required, site);
        } else {
            message = context.getString(R.string.confirm_registration_benefits, site,
                                        context.getString(R.string.lbl_credentials));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_registration)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.btn_tell_me_more,
                                   (d, w) -> context.startActivity(intent))
                .create();

        if (!required) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                             context.getString(R.string.btn_disable_message), (d, which) ->
                                     PreferenceManager.getDefaultSharedPreferences(context)
                                                      .edit().putBoolean(prefName, true).apply());
        }

        dialog.show();
    }

    /**
     * Purge {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} for the given entity.
     *
     * @param context   Current context
     * @param label     resource string id for the type of entity
     * @param entity    the entity (from which we'll display the label)
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void purgeBLNS(@NonNull final Context context,
                                 @StringRes final int label,
                                 @NonNull final Entity entity,
                                 @NonNull final Runnable onConfirm) {

        String msg = context.getString(R.string.info_purge_blns_item_name,
                                       context.getString(label),
                                       entity.getLabel(context));
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_purge_blns)
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    public static void showError(@NonNull final Context context,
                                 @StringRes final int msgId) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_error)
                .setMessage(msgId)
                .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }

    public static void showBadError(@NonNull final Context context,
                                    @StringRes final int msgId) {
        final String msg = createBadError(context, msgId);
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_error)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }

    public static String createBadError(@NonNull final Context context,
                                        @StringRes final int msgId) {
        return context.getString(msgId) + "\n"
               + context.getString(R.string.error_if_the_problem_persists,
                                   context.getString(R.string.lbl_send_debug));
    }


    /**
     * Show a popup info text.
     *
     * @param infoView the View from which we'll take the content-description as text to display
     *                 and anchor the popup to.
     */
    public static void infoPopup(@NonNull final View infoView) {
        infoPopup(infoView, infoView.getContentDescription());
    }

    /**
     * Show a popup info text. A tap outside of the popup will make it go away again.
     *
     * @param anchorView for the popup window
     * @param text       to display
     */
    @SuppressWarnings("WeakerAccess")
    public static void infoPopup(@NonNull final View anchorView,
                                 @NonNull final CharSequence text) {
        final Context context = anchorView.getContext();
        @SuppressLint("InflateParams")
        final View root = LayoutInflater.from(context).inflate(R.layout.popup_info, null);
        final TextView infoView = root.findViewById(R.id.info);
        infoView.setText(text);

        final PopupWindow popup = new PopupWindow(context);
        popup.setContentView(root);
        // make the rounded corners transparent
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        // finally show it
        popup.showAsDropDown(anchorView);
    }
}
