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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.dialogs.BasicDialog;

/**
 * TODO: unify with {@link EditAuthorDialog}
 */
public class EditLanguageDialog {
	private final Context mContext;
	private final CatalogueDBAdapter mDb;
	private final Runnable mOnChanged;

	EditLanguageDialog(Context context, CatalogueDBAdapter db, final Runnable onChanged) {
		mDb = db;
		mContext = context;
		mOnChanged = onChanged;
	}

	public void edit(final String language) {
		final Dialog dialog = new BasicDialog(mContext);
		dialog.setContentView(R.layout.dialog_edit_language);
		dialog.setTitle(R.string.edit_language_details);

		EditText familyView = dialog.findViewById(R.id.name);
		familyView.setText(language);

		Button saveButton = dialog.findViewById(R.id.confirm);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText nameView = dialog.findViewById(R.id.name);
				String newName = nameView.getText().toString().trim();
				if (newName.isEmpty()) {
					Toast.makeText(mContext, R.string.language_is_blank, Toast.LENGTH_LONG).show();
					return;
				}
				dialog.dismiss();
				confirmEdit(language, newName);
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
	
	private void confirmEdit(String oldName, final String newName) {
		// First, deal with a some special cases...
		
		// Case: Unchanged.
		if (newName.equals(oldName)) {
			return;
		}

		mDb.globalReplaceLanguage(oldName, newName);
		oldName = newName;

		mOnChanged.run();
	}
}
