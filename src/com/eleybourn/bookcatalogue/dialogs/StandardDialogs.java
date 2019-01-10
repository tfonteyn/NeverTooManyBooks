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
import android.content.Intent;
import android.database.Cursor;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.ThemeUtils;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * ENHANCE: decide Toast/Snackbar ? it's a preference setting for now... but overkill (bigger app)
 * TODO: Snackbar: needs styling
 * TODO: Snackbar: uses getDecorView for now so it's always at the bottom of the screen.
 */
public final class StandardDialogs {

    private StandardDialogs() {
    }

    /**
     * Shielding the actual implementation of Toast/Snackbar or whatever is next.
     */
    public static void showUserMessage(@NonNull final Activity activity,
                                       @StringRes final int message) {
        if (0 == Prefs.getInt(BookCatalogueApp.PREF_APP_USER_MESSAGE, 0)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(activity.getWindow().getDecorView(), message,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Shielding the actual implementation of Toast/Snackbar or whatever is next.
     */
    public static void showUserMessage(@NonNull final Activity activity,
                                       @NonNull final String message) {
        if (0 == Prefs.getInt(BookCatalogueApp.PREF_APP_USER_MESSAGE, 0)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(activity.getWindow().getDecorView(), message,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Problem child: called from a task (thread) which has no activity/context at all.
     * Hardwired to use the application context.
     * *will* only be called from a UI thread.
     * <p>
     * Does mean we can't use SnackBar
     */
    public static void showUserMessage(@NonNull final String message) {
        Toast.makeText(BookCatalogueApp.getAppContext(), message, Toast.LENGTH_LONG).show();
    }

    public static void showUserMessage(@StringRes final int message) {
        Toast.makeText(BookCatalogueApp.getAppContext(), message, Toast.LENGTH_LONG).show();
    }
    /* ========================================================================================== */

    /**
     * Show a dialog asking if unsaved edits should be ignored. Finish activity if so.
     */
    public static void showConfirmUnsavedEditsDialog(@NonNull final Activity activity,
                                                     @Nullable final Runnable onConfirm) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.lbl_details_have_changed)
                .setMessage(R.string.warning_unsaved_changes)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         activity.getString(R.string.btn_confirm_exit),
                         new AlertDialog.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 if (onConfirm != null) {
                                     onConfirm.run();
                                 } else {
                                     activity.setResult(Activity.RESULT_CANCELED);
                                     activity.finish();
                                 }
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         activity.getString(R.string.btn_continue_editing),
                         new AlertDialog.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        dialog.show();
    }

    public static void deleteSeriesAlert(@NonNull final Context context,
                                         @NonNull final CatalogueDBAdapter db,
                                         @NonNull final Series series,
                                         @NonNull final Runnable onDeleted) {

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(String.format(context.getString(R.string.warning_really_delete_series),
                                          series.name))
                .setTitle(R.string.title_delete_series)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 db.deleteSeries(series.id);
                                 dialog.dismiss();
                                 onDeleted.run();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        dialog.show();
    }

    /**
     * @return the resource id for a string in case of error, 0 for ok
     */
    @StringRes
    public static int deleteBookAlert(@NonNull final Context context,
                                      @NonNull final CatalogueDBAdapter db,
                                      final long bookId,
                                      @NonNull final Runnable onDeleted) {

        String UNKNOWN = '<' + BookCatalogueApp.getResourceString(R.string.unknown_uc) + '>';
        List<Author> authorList = db.getBookAuthorList(bookId);

        // get the book title
        String title;
        try (Cursor cursor = db.fetchBookById(bookId)) {
            if (!cursor.moveToFirst()) {
                return R.string.warning_unable_to_find_book;
            }

            title = cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_TITLE.name));
            if (title == null || title.isEmpty()) {
                title = UNKNOWN;
            }
        }

        // Format the list of authors nicely
        StringBuilder authors = new StringBuilder();
        if (authorList.size() == 0) {
            authors.append(UNKNOWN);
        } else {
            authors.append(authorList.get(0).getDisplayName());
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getDisplayName());
            }
            if (authorList.size() > 1) {
                authors.append(' ').append(context.getString(R.string.list_and)).append(' ').append(
                        authorList.get(authorList.size() - 1).getDisplayName());
            }
        }

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(
                        (context.getString(R.string.warning_really_delete_book, title, authors)))
                .setTitle(R.string.menu_delete_book)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 db.deleteBook(bookId);
                                 dialog.dismiss();
                                 onDeleted.run();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        dialog.show();
        return 0;
    }

    /**
     * Display a dialog warning the user that Goodreads authentication is required.
     * Gives the options: 'request now', 'more info' or 'cancel'.
     */
    public static void goodreadsAuthAlert(@NonNull final FragmentActivity context) {
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gr_title_auth_access)
                .setMessage(R.string.gr_action_cannot_be_completed)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 GoodreadsRegisterActivity.requestAuthorizationInBackground(
                                         context);
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         context.getString(R.string.btn_tell_me_more),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 Intent i = new Intent(context, GoodreadsRegisterActivity.class);
                                 context.startActivity(i);
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        dialog.show();

    }

    public static void confirmSaveDuplicateBook(@NonNull final Context context,
                                                @NonNull final AlertDialogAction nextStep) {
        /*
         * If it exists, show a dialog and use it to perform the
         * next action, according to the users choice.
         */
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.title_duplicate_book)
                .setMessage(context.getString(R.string.warning_duplicate_book_message))
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                         context.getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(final DialogInterface dialog,
                                                 final int which) {
                                 nextStep.onPositive();
                             }
                         });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(final DialogInterface dialog,
                                                 final int which) {
                                 nextStep.onNegative();
                             }
                         });
        dialog.show();
    }

    public interface AlertDialogAction {

        void onPositive();

        void onNeutral();

        void onNegative();
    }



    /* ========================================================================================== */

    /**
     * This class exists temporarily so we use AppCompatDialog in ONE place (here)
     * Gradually replacing the use.
     */
    public static class BasicDialog
            extends AppCompatDialog {

        public BasicDialog(@NonNull final Context context) {
            this(context, ThemeUtils.getDialogThemeResId());
        }

        public BasicDialog(@NonNull final Context context,
                           @StyleRes final int theme) {
            super(context, theme);

        }
    }
}
