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
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

/**
 * Dialog to edit a single bookshelf.
 * <p>
 * Calling point is a List.
 */
public class EditBookshelfDialog {

    @NonNull
    private final Activity mContext;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    public EditBookshelfDialog(@NonNull final Activity activity,
                               @NonNull final CatalogueDBAdapter db,
                               @NonNull final Runnable onChanged) {
        mDb = db;
        mContext = activity;
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Bookshelf bookshelf) {
        // Build the base dialog
        final View root = mContext.getLayoutInflater()
                                  .inflate(R.layout.dialog_edit_bookshelf, null);

        final EditText nameView = root.findViewById(R.id.name);
        //noinspection ConstantConditions
        nameView.setText(bookshelf.name);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(root)
                .setTitle(R.string.menu_edit_bookshelf)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mContext, R.string.warning_required_name);
                    return;
                }

                // check if already exists (null if not)
                Bookshelf existingShelf = mDb.getBookshelfByName(newName);

                // adding a new Bookshelf, and trying to use an existing name?
                if ((bookshelf.id == 0) && (existingShelf != null)) {
                    StandardDialogs.showUserMessage(
                            mContext,
                            mContext.getString(R.string.warning_thing_already_exists,
                                               mContext.getString(
                                                       R.string.lbl_bookshelf)));
                    return;
                }

                dialog.dismiss();
                confirmEdit(bookshelf, newName, existingShelf);
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

    private void confirmEdit(@NonNull final Bookshelf bookshelf,
                             @NonNull final String newName,
                             @Nullable final Bookshelf existingShelf) {
        // case sensitive equality
        if (bookshelf.name.equals(newName)) {
            return;
        }

        // copy new values
        bookshelf.name = newName;
        // shelf did not exist, so go for it.
        if (existingShelf == null) {
            long id = mDb.insertOrUpdateBookshelf(bookshelf);
            mOnChanged.run();
            return;
        }

        // we are renaming 'from' to 'to' which already exists.
        // check if we should merge them.
        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.menu_edit_bookshelf)
                .setMessage(R.string.warning_merge_bookshelves)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, mContext.getString(R.string.btn_merge),
                         new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();

                                 // move all books from bookshelf to existingShelf
                                 mDb.mergeBookshelves(bookshelf.id, existingShelf.id);
                                 mOnChanged.run();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         mContext.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });
        dialog.show();
    }
}
