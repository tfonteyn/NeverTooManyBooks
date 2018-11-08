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
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

/**
 *  Dialog to edit a single bookshelf.
 *
 * Calling point is a List.
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

        final EditText nameView = root.findViewById(R.id.series);
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
                    StandardDialogs.showUserMessage(mContext, R.string.name_can_not_be_blank);
                    return;
                }
                Bookshelf newBookshelf = new Bookshelf(newName);
                dialog.dismiss();
                confirmEdit(bookshelf, newBookshelf);
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

    private void confirmEdit(final @NonNull Bookshelf from, final @NonNull Bookshelf to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }
        if (from.id == 0) {
            long id = mDb.insertBookshelf(to.name);
            if (id < 0) {
                Logger.error("insert failed?");
            }
        } else {
            mDb.updateBookshelf(from.id, to.name);
        }

        mOnChanged.run();
    }
}
