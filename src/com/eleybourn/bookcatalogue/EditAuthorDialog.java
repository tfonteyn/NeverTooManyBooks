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

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;

public class EditAuthorDialog {
    private final Activity mActivity;
    private final CatalogueDBAdapter mDb;
    private final Runnable mOnChanged;

    EditAuthorDialog(@NonNull final Activity activity, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        mDb = db;
        this.mActivity = activity;
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Author author) {
        final Dialog dialog = new StandardDialogs.BasicDialog(mActivity);
        dialog.setContentView(R.layout.dialog_edit_author);
        dialog.setTitle(R.string.edit_author_details);

        EditText familyView = dialog.findViewById(R.id.family_name);
        EditText givenView = dialog.findViewById(R.id.given_names);
        familyView.setText(author.familyName);
        givenView.setText(author.givenNames);

        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText familyView = dialog.findViewById(R.id.family_name);
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showQuickNotice(mActivity, R.string.author_is_blank);
                    return;
                }

                EditText givenView = dialog.findViewById(R.id.given_names);
                String newGiven = givenView.getText().toString().trim();

                Author newAuthor = new Author(newFamily, newGiven);
                dialog.dismiss();
                confirmEdit(author, newAuthor);
            }
        });

        dialog.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEdit(@NonNull final Author from, @NonNull final Author to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's
        from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames); //TODO: this call is not needed I think
        to.id = mDb.getAuthorIdByName(to.familyName, to.givenNames);

        // Case: author is the same, or is only used in this book
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
