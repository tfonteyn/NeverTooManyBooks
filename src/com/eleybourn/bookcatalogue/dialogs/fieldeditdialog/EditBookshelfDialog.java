/*
 * @copyright 2011 Philip Warner
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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

/**
 * Dialog to edit an existing or new bookshelf.
 * <p>
 * Calling point is a List.
 *
 * Don't move this to the {@link com.eleybourn.bookcatalogue.EditBookshelfListActivity}.
 * The intention is to allow editing 'on the fly' wherever a bookshelf is used.
 */
public class EditBookshelfDialog {

    @NonNull
    private final Activity mActivity;
    @NonNull
    private final DBA mDb;
    @Nullable
    private final OnChanged mOnChanged;

    /**
     *
     * @param activity  hosting activity (needed for user messages)
     * @param db        the database
     * @param onChanged (optional) class/method to run if something was changed
     */
    public EditBookshelfDialog(@NonNull final Activity activity,
                               @NonNull final DBA db,
                               @Nullable final OnChanged onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Bookshelf source) {
        // Build the base dialog
        final View root = mActivity.getLayoutInflater()
                                   .inflate(R.layout.dialog_edit_bookshelf, null);

        final EditText nameView = root.findViewById(R.id.name);
        //noinspection ConstantConditions
        nameView.setText(source.getName());

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.menu_edit_bookshelf)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.warning_required_name);
                    return;
                }

                // check if a shelf with this name already exists (null if not)
                Bookshelf existingShelf = mDb.getBookshelfByName(newName);

                // are we adding a new Bookshelf but trying to use an existing name?
                if ((source.getId() == 0) && (existingShelf != null)) {
                    StandardDialogs.showUserMessage(
                            mActivity,
                            mActivity.getString(R.string.warning_thing_already_exists,
                                                mActivity.getString(R.string.lbl_bookshelf)));
                    return;
                }

                dialog.dismiss();

                // check if there was something changed at all.
                if (source.getName().equals(newName)) {
                    return;
                }

                // At this point, we know changes were made.
                // Create a new Bookshelf as a holder for the changes.
                Bookshelf newBookshelf = new Bookshelf(newName, source.getStyle(mDb).getId());

                // copy new values
                source.copyFrom(newBookshelf);

                if (existingShelf != null) {
                    mergeShelves(source, existingShelf);
                } else {
                    if (mDb.updateOrInsertBookshelf(source)) {
                        if (mOnChanged != null) {
                            mOnChanged.onChanged(source.getId(), 0);
                        }
                    }
                }
            }
        });

        //noinspection ConstantConditions
        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void mergeShelves(@NonNull final Bookshelf source,
                              @NonNull final Bookshelf destination) {

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.menu_edit_bookshelf)
                .setMessage(R.string.confirm_merge_bookshelves)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, mActivity.getString(R.string.btn_merge),
                         new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();

                                 // move all books from bookshelf to existingShelf
                                 int booksMoved = mDb.mergeBookshelves(source.getId(),
                                                                       destination.getId());
                                 if (mOnChanged != null) {
                                     mOnChanged.onChanged(destination.getId(), booksMoved);
                                 }
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         mActivity.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });
        dialog.show();
    }

    public interface OnChanged {

        /**
         * Called after the user confirms a change.
         *
         * @param bookshelfId the id of the updated shelf, or of the newly inserted shelf.
         * @param booksMoved  if a merge took place, the amount of books moved (or 0).
         */
        void onChanged(long bookshelfId,
                       int booksMoved);
    }
}
