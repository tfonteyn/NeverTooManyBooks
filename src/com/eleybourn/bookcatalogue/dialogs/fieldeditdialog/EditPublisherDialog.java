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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Publisher;

/**
 * Dialog to edit an existing publisher.
 * <p>
 * Calling point is a List.
 */
public class EditPublisherDialog {

    @NonNull
    private final Activity mActivity;
    @NonNull
    private final DBA mDb;
    @Nullable
    private final Runnable mOnChanged;

    /** Adapter for the AutoCompleteTextView field. */
    private final ArrayAdapter<String> mAdapter;

    /**
     * Constructor.
     */
    public EditPublisherDialog(@NonNull final Activity activity,
                               @NonNull final DBA db,
                               @Nullable final Runnable onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
        mAdapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_dropdown_item_1line,
                                      mDb.getPublisherNames());
    }

    @CallSuper
    public void edit(@NonNull final Publisher source) {
        // Build the base dialog
        final View root = mActivity.getLayoutInflater()
                                   .inflate(R.layout.dialog_edit_publisher, null);

        final AutoCompleteTextView nameView = root.findViewById(R.id.name);
        nameView.setText(source.getName());
        nameView.setAdapter(mAdapter);

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.title_edit_publisher)
                .create();

        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.warning_required_name);
                    return;
                }
                dialog.dismiss();

                if (newName.equals(source.getName())) {
                    return;
                }
                saveChanges(source.getName(), newName);
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

    /**
     * @param from the publisher data before editing
     * @param to   the data after editing
     */
    private void saveChanges(@NonNull final String from,
                             @NonNull final String to) {

        mDb.updatePublisher(from, to);
    }
}
