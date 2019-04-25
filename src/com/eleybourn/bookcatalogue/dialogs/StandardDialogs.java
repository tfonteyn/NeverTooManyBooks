/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;

/**
 * ENHANCE: decide Toast/Snackbar ? it's a preference setting for now... but overkill (bigger app)
 * TODO: Snackbar: when using the activity/getDecorView it's always at the bottom of the screen.
 */
public final class StandardDialogs {

    private StandardDialogs() {
    }

    /**
     * Show a dialog asking if unsaved edits should be ignored. Finish activity if so.
     */
    public static void showConfirmUnsavedEditsDialog(@NonNull final Activity activity,
                                                     @Nullable final Runnable onConfirm) {
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.lbl_details_have_changed)
                .setMessage(R.string.warning_unsaved_edits)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         activity.getString(R.string.btn_confirm_exit),
                         (d, which) -> {
                             d.dismiss();
                             if (onConfirm != null) {
                                 onConfirm.run();
                             } else {
                                 activity.setResult(Activity.RESULT_CANCELED);
                                 activity.finish();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         activity.getString(R.string.btn_continue_editing),
                         (d, which) -> d.dismiss());

        dialog.show();
    }

    public static void deleteSeriesAlert(@NonNull final Context context,
                                         @NonNull final DBA db,
                                         @NonNull final Series series,
                                         @NonNull final Runnable onDeleted) {

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.confirm_really_delete_series,
                                              series.getName()))
                .setTitle(R.string.title_delete_series)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         context.getString(android.R.string.ok),
                         (d, which) -> {
                             d.dismiss();
                             db.deleteSeries(series.getId());
                             onDeleted.run();
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         (d, which) -> d.dismiss());

        dialog.show();
    }

    public static void deleteBookAlert(@NonNull final Context context,
                                       @NonNull final DBA db,
                                       final long bookId,
                                       @NonNull final Runnable onDeleted) {

        String UNKNOWN = '<' + context.getString(R.string.unknown).toUpperCase() + '>';
        List<Author> authorList = db.getAuthorsByBookId(bookId);

        String title = db.getBookTitle(bookId);
        if (title == null || title.isEmpty()) {
            title = UNKNOWN;
        }

        // Format the list of authors nicely
        StringBuilder authors = new StringBuilder();
        if (authorList.isEmpty()) {
            authors.append(UNKNOWN);
        } else {
            authors.append(authorList.get(0).getLabel());
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getLabel());
            }
            if (authorList.size() > 1) {
                authors.append(' ').append(context.getString(R.string.list_and)).append(' ').append(
                        authorList.get(authorList.size() - 1).getLabel());
            }
        }

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(
                        (context.getString(R.string.confirm_really_delete_book, title, authors)))
                .setTitle(R.string.menu_delete_book)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                         (d, which) -> {
                             d.dismiss();
                             db.deleteBook(bookId);
                             onDeleted.run();
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         (d, which) -> d.dismiss());

        dialog.show();
    }

    public static void confirmSaveDuplicateBook(@NonNull final Context context,
                                                @NonNull final AlertDialogListener nextStep) {
        /*
         * If it exists, show a dialog and use it to perform the
         * next action, according to the users choice.
         */
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.title_duplicate_book)
                .setMessage(context.getString(R.string.confirm_duplicate_book_message))
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                         context.getString(android.R.string.ok),
                         (d, which) -> nextStep.onPositiveButton());
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         (d, which) -> nextStep.onNegativeButton());
        dialog.show();
    }
}
