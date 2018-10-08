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
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;

public class EditPublisherDialog {
    private final Activity mActivity;
    private final CatalogueDBAdapter mDb;
    private final Runnable mOnChanged;

    EditPublisherDialog(@NonNull final Activity activity, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Publisher publisher) {
        final Dialog dialog = new StandardDialogs.BasicDialog(mActivity);
        dialog.setContentView(R.layout.dialog_edit_publisher);
        dialog.setTitle(R.string.edit_publisher_details);

        EditText familyView = dialog.findViewById(R.id.name);
        familyView.setText(publisher.name);

        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText nameView = dialog.findViewById(R.id.name);
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showQuickNotice(mActivity, R.string.name_can_not_be_blank);
                    return;
                }
                Publisher newPublisher = new Publisher(newName);
                dialog.dismiss();
                confirmEdit(publisher, newPublisher);
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

    /**
     * ENHANCE: once Publisher use id's, use code from {@link EditSeriesDialog#confirmEdit}
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
