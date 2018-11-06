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
import com.eleybourn.bookcatalogue.entities.Publisher;

public class EditPublisherDialog {
    @NonNull
    private final Activity mActivity;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    EditPublisherDialog(final @NonNull Activity activity, final @NonNull CatalogueDBAdapter db, final @NonNull Runnable onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
    }

    public void edit(final @NonNull Publisher publisher) {
        final Dialog dialog = new StandardDialogs.BasicDialog(mActivity);
        dialog.setContentView(R.layout.dialog_edit_publisher);
        dialog.setTitle(R.string.dialog_title_edit_publisher);

        final EditText nameView = dialog.findViewById(R.id.name);
        //noinspection ConstantConditions
        nameView.setText(publisher.name);

        //noinspection ConstantConditions
        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.name_can_not_be_blank);
                    return;
                }
                Publisher newPublisher = new Publisher(newName);
                dialog.dismiss();
                confirmEdit(publisher, newPublisher);
            }
        });

        //noinspection ConstantConditions
        dialog.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
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
    private void confirmEdit(final @NonNull Publisher from, final @NonNull Publisher to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        mDb.globalReplacePublisher(from, to);
        from.copyFrom(to);

        mOnChanged.run();
    }
}
