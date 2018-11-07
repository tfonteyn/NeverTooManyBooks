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

import com.eleybourn.bookcatalogue.EditAuthorListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;

/**
 *  Dialog to edit a single author.
 *
 * Calling point is a List; see {@link EditAuthorListActivity} for book
 */
public class EditAuthorDialog {
    @NonNull
    private final Activity mContext;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    public EditAuthorDialog(final @NonNull Activity activity,
                            final @NonNull CatalogueDBAdapter db,
                            final @NonNull Runnable onChanged) {
        mDb = db;
        this.mContext = activity;
        mOnChanged = onChanged;
    }

    public void edit(final @NonNull Author author) {
        // Build the base dialog
        final View root = mContext.getLayoutInflater().inflate(R.layout.dialog_edit_author, null);

        final EditText familyView = root.findViewById(R.id.family_name);
        //noinspection ConstantConditions
        familyView.setText(author.familyName);
        final EditText givenView = root.findViewById(R.id.given_names);
        //noinspection ConstantConditions
        givenView.setText(author.givenNames);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(root)
                .setTitle(R.string.dialog_title_edit_author)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showUserMessage(mContext, R.string.warning_blank_author);
                    return;
                }
                String newGiven = givenView.getText().toString().trim();

                Author newAuthor = new Author(newFamily, newGiven);
                dialog.dismiss();
                confirmEdit(author, newAuthor);
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

    private void confirmEdit(final @NonNull Author from, final @NonNull Author to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's
        from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames); //TODO: this call is not needed I think
        to.id = mDb.getAuthorIdByName(to.familyName, to.givenNames);

        // Case: author is the same
        if (to.id == from.id) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            if (from.id == 0) {
                from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames);
            }
            mDb.insertOrUpdateAuthor(from);
        } else {
            mDb.globalReplaceAuthor(from, to);
            from.copyFrom(to);
        }
        mOnChanged.run();
    }
}
