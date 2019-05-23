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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
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
        new AlertDialog.Builder(activity)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.lbl_details_have_changed)
                .setMessage(R.string.warning_unsaved_edits)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_continue_editing, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_exit, (d, which) -> {
                    d.dismiss();
                    if (onConfirm != null) {
                        onConfirm.run();
                    } else {
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                    }
                })
                .create()
                .show();
    }

    public static void deleteSeriesAlert(@NonNull final Context context,
                                         @NonNull final Series series,
                                         @NonNull final Runnable onDoDelete) {

        new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_delete_series)
                .setMessage(context.getString(R.string.confirm_delete_series, series.getName()))
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    d.dismiss();
                    onDoDelete.run();
                })
                .create()
                .show();
    }

    public static void deleteBookAlert(@NonNull final Context context,
                                       @NonNull final String title,
                                       @NonNull final List<Author> authorList,
                                       @NonNull final Runnable onDoDelete) {

        // Format the list of authors nicely
        StringBuilder authors = new StringBuilder();
        if (authorList.isEmpty()) {
            authors.append('<').append(context.getString(R.string.unknown).toUpperCase())
                   .append('>');
        } else {
            // "a1, a2 and a3"
            authors.append(authorList.get(0).getLabel());
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getLabel());
            }

            if (authorList.size() > 1) {
                authors.append(' ').append(context.getString(R.string.list_and)).append(' ')
                       .append(authorList.get(authorList.size() - 1).getLabel());
            }
        }

        new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_delete_book)
                .setMessage(context.getString(R.string.confirm_delete_book, title, authors))
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    d.dismiss();
                    onDoDelete.run();
                })
                .create()
                .show();
    }
}
