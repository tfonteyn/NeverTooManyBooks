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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Publisher;

/**
 *  Dialog to edit a single publisher.
 *
 * Calling point is a List.
 */
public class EditPublisherDialog {
    @NonNull
    private final Activity mContext;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    public EditPublisherDialog(@NonNull final Activity activity, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        mDb = db;
        mContext = activity;
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Publisher publisher) {
        // Build the base dialog
        final View root = mContext.getLayoutInflater().inflate(R.layout.dialog_edit_publisher, null);

        final EditText nameView = root.findViewById(R.id.name);
        //noinspection ConstantConditions
        nameView.setText(publisher.name);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(root)
                .setTitle(R.string.title_edit_publisher)
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
                Publisher newPublisher = new Publisher(newName);
                dialog.dismiss();
                confirmEdit(publisher, newPublisher);
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

    /**
     * ENHANCE: once {@link Publisher} use id's, use code from {@link EditSeriesDialog#confirmEdit}
     */
    private void confirmEdit(@NonNull final Publisher from, @NonNull final Publisher to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        mDb.globalReplacePublisher(from, to);
        from.copyFrom(to);

        mOnChanged.run();
    }
}
