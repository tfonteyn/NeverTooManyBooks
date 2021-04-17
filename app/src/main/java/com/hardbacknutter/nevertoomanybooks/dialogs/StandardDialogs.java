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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

public final class StandardDialogs {

    private StandardDialogs() {
    }

    /**
     * Show a dialog asking if the indicated change should be applied to all books,
     * or just the current book.
     *
     * @param context    Current context
     * @param original   entity
     * @param modified   entity
     * @param onAllBooks Runnable to execute if the user 'all books''
     * @param onThisBook Runnable to execute if the user 'this book''
     */
    public static void confirmScopeForChange(@NonNull final Context context,
                                             @NonNull final Entity original,
                                             @NonNull final Entity modified,
                                             @NonNull final Runnable onAllBooks,
                                             @NonNull final Runnable onThisBook) {
        final String allBooks = context.getString(R.string.bookshelf_all_books);
        final String message = context.getString(R.string.confirm_scope_for_change,
                                                 original.getLabel(context),
                                                 modified.getLabel(context),
                                                 allBooks);
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, w) -> onAllBooks.run())
                .setPositiveButton(R.string.btn_this_book, (d, w) -> onThisBook.run())
                .create()
                .show();
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
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
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
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.action_delete)
                .setMessage(context.getString(R.string.confirm_delete_series,
                                              series.getLabel(context)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param publisher Publisher we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deletePublisher(@NonNull final Context context,
                                       @NonNull final Publisher publisher,
                                       @NonNull final Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.action_delete)
                .setMessage(context.getString(R.string.confirm_delete_publisher,
                                              publisher.getLabel(context)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param bookshelf Bookshelf we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteBookshelf(@NonNull final Context context,
                                       @NonNull final Bookshelf bookshelf,
                                       @NonNull final Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.action_delete)
                .setMessage(context.getString(R.string.confirm_delete_bookshelf,
                                              bookshelf.getLabel(context),
                                              context.getString(R.string.bookshelf_all_books)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param title     Title of the item we're about to delete
     * @param author    of the item we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteTocEntry(@NonNull final Context context,
                                      @NonNull final String title,
                                      @NonNull final Author author,
                                      @NonNull final Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.action_delete)
                .setMessage(context.getString(R.string.confirm_delete_toc_entry,
                                              title,
                                              author.getLabel(context)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
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
        final StringBuilder authors = new StringBuilder();
        if (authorList.isEmpty()) {
            authors.append('<').append(context.getString(R.string.unknown_author)).append('>');

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
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.action_delete)
                .setMessage(context.getString(R.string.confirm_delete_book, title, authors))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .create()
                .show();
    }


    /**
     * Purge {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} for the given entity.
     *
     * @param context     Current context
     * @param label       resource string id for the type of entity
     * @param entityLabel the label of the entity
     * @param onConfirm   Runnable to execute if the user clicks the confirm button.
     */
    public static void purgeBLNS(@NonNull final Context context,
                                 @StringRes final int label,
                                 @NonNull final String entityLabel,
                                 @NonNull final Runnable onConfirm) {

        final String msg = context.getString(R.string.info_purge_blns_item_name,
                                             context.getString(label),
                                             entityLabel);
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
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
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage(msgId)
                .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }

    public static void showError(@NonNull final Context context,
                                 @NonNull final CharSequence message) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }

    public static void showError(@NonNull final Context context,
                                 @NonNull final LocalizedException e) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage(e.getUserMessage(context))
                .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }

    public static String createBadError(@NonNull final Context context,
                                        @StringRes final int msgId) {
        return createBadError(context, context.getString(msgId));
    }

    public static String createBadError(@NonNull final Context context,
                                        @NonNull final String msg) {
        return msg + "\n" + context.getString(R.string.error_if_the_problem_persists,
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
