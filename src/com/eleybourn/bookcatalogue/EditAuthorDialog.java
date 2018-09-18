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

import android.app.Dialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;

public class EditAuthorDialog {
	private final Context mContext;
	private final CatalogueDBAdapter mDb;
	private final Runnable mOnChanged;

	EditAuthorDialog(@NonNull final Context context, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
		mDb = db;
		mContext = context;
		mOnChanged = onChanged;
	}

	public void edit(@NonNull final Author author) {
		final Dialog dialog = new StandardDialogs.BasicDialog(mContext);
		dialog.setContentView(R.layout.dialog_edit_author);
		dialog.setTitle(R.string.edit_author_details);

		EditText familyView = dialog.findViewById(R.id.family_name);
		EditText givenView = dialog.findViewById(R.id.given_names);
		familyView.setText(author.familyName);
		givenView.setText(author.givenNames);

		Button saveButton = dialog.findViewById(R.id.confirm);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText familyView = dialog.findViewById(R.id.family_name);
				EditText givenView = dialog.findViewById(R.id.given_names);
				String newFamily = familyView.getText().toString().trim();
				if (newFamily.isEmpty()) {
					Toast.makeText(mContext, R.string.author_is_blank, Toast.LENGTH_LONG).show();
					return;
				}
				String newGiven = givenView.getText().toString();
				Author newAuthor = new Author(newFamily, newGiven);
				dialog.dismiss();
				confirmEdit(author, newAuthor);
			}
		});

		Button cancelButton = dialog.findViewById(R.id.cancel);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		
		dialog.show();
	}
	
	private void confirmEdit(@NonNull final Author from, @NonNull final Author to) {

		if (to.equals(from)) {
			return;
		}

		// Get the new author ID
		from.id = mDb.getAuthorIdByName(from);
		to.id = mDb.getAuthorIdByName(to);

		// Case: author is the same, or is only used in this book
		if (to.id == from.id) {
			// Just update with the most recent spelling and format
			from.copyFrom(to);
			mDb.updateOrInsertAuthorByName(from);
		} else {
			mDb.globalReplaceAuthor(from, to);
			from.copyFrom(to);
		}
		mOnChanged.run();
	}
}
