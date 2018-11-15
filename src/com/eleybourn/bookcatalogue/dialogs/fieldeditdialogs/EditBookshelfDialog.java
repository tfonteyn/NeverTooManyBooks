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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

import java.util.Objects;

/**
 *  Dialog to edit a single bookshelf.
 *
 * Calling point is a List.
 *
 * TEST new 'merge' behaviour
 */
public class EditBookshelfDialog {
    @NonNull
    private final Activity mContext;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    public EditBookshelfDialog(final @NonNull Activity activity,
                               final @NonNull CatalogueDBAdapter db,
                               final @NonNull Runnable onChanged) {
        mDb = db;
        mContext = activity;
        mOnChanged = onChanged;
    }

    public void edit(final @NonNull Bookshelf bookshelf) {
        // Build the base dialog
        final View root = mContext.getLayoutInflater().inflate(R.layout.dialog_edit_bookshelf, null);

        final EditText nameView = root.findViewById(R.id.filename);
        //noinspection ConstantConditions
        nameView.setText(bookshelf.name);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(root)
                .setTitle(R.string.menu_edit_bookshelf)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mContext, R.string.warning_required_name);
                    return;
                }

                // check if already exists (null if not)
                Bookshelf existingShelf = mDb.getBookshelfByName(newName);

                // adding a new Bookshelf, and trying to use an existing name?
                if ((bookshelf.id == 0) && (existingShelf != null)) {
                    StandardDialogs.showUserMessage(mContext,
                            mContext.getString(R.string.warning_thing_already_exists, mContext.getString(R.string.lbl_bookshelf)));
                    return;
                }

                dialog.dismiss();
                confirmEdit(bookshelf, existingShelf, newName);
            }
        });

        //noinspection ConstantConditions
        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEdit(final @NonNull Bookshelf from,
                             final @Nullable Bookshelf existingShelf,
                             final @NonNull String newName) {
        // case sensitive equality
        if (from.name.equals(newName)) {
            return;
        }

        // are we adding a new shelf?
        if (from.id == 0) {
            long id = mDb.insertBookshelf(newName);
            if (id < 0) {
                Logger.error("insert failed?");
            } else {
                mOnChanged.run();
            }
            return;
        }

        // we are renaming 'from' to 'newName' which already exists.
        // check if we should merge them.
        Objects.requireNonNull(existingShelf);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setMessage("The shelf you are renaming already exists. Would you like to merge them?")
                .setTitle(R.string.menu_edit_bookshelf)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, mContext.getString(R.string.btn_confirm_update), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();

                // just update
                mDb.updateBookshelf(from.id, newName);
                mOnChanged.run();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, mContext.getString(R.string.btn_merge), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();

                mDb.mergeBookshelves(from.id, existingShelf.id);
                mOnChanged.run();
            }
        });

        dialog.show();
    }
}
