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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.EditAuthorListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;

/**
 * Dialog to edit an existing single author.
 * <p>
 * Calling point is a List; see {@link EditAuthorListActivity} for book
 */
public class EditAuthorDialog {

    @NonNull
    private final Activity mActivity;
    @NonNull
    private final DBA mDb;
    @Nullable
    private final Runnable mOnChanged;
    @NonNull
    private final ArrayAdapter<String> mFamilyNameAdapter;
    @NonNull
    private final ArrayAdapter<String> mGivenNameAdapter;

    /**
     * Constructor.
     */
    public EditAuthorDialog(@NonNull final Activity activity,
                            @NonNull final DBA db,
                            @Nullable final Runnable onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
        mFamilyNameAdapter = new ArrayAdapter<>(mActivity,
                                                android.R.layout.simple_dropdown_item_1line,
                                                mDb.getAuthorsFamilyName());
        mGivenNameAdapter = new ArrayAdapter<>(mActivity,
                                               android.R.layout.simple_dropdown_item_1line,
                                               mDb.getAuthorsGivenNames());
    }

    public void edit(@NonNull final Author source) {
        // Build the base dialog
        final View root = mActivity.getLayoutInflater()
                                   .inflate(R.layout.dialog_edit_author, null);

        final AutoCompleteTextView familyView = root.findViewById(R.id.family_name);
        familyView.setText(source.getFamilyName());
        familyView.setAdapter(mFamilyNameAdapter);

        final AutoCompleteTextView givenView = root.findViewById(R.id.given_names);
        givenView.setText(source.getGivenNames());
        givenView.setAdapter(mGivenNameAdapter);

        final Checkable isCompleteView = root.findViewById(R.id.is_complete);
        isCompleteView.setChecked(source.isComplete());

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.title_edit_author)
                .create();

        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.warning_required_author);
                    return;
                }

                String newGiven = givenView.getText().toString().trim();
                boolean isComplete = isCompleteView.isChecked();
                dialog.dismiss();

                if (source.getFamilyName().equals(newFamily)
                        && source.getGivenNames().equals(newGiven)
                        && source.isComplete() == isComplete) {
                    return;
                }

                source.setName(newFamily, newGiven);
                source.setComplete(isComplete);
                mDb.updateOrInsertAuthor(source);

                if (mOnChanged != null) {
                    mOnChanged.run();
                }
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
