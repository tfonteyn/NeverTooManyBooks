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

import com.eleybourn.bookcatalogue.EditSeriesListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Series;

/**
 * Dialog to edit an existing single series.
 * <p>
 * Calling point is a List; see {@link EditSeriesListActivity} for book
 */
public class EditSeriesDialog {

    @NonNull
    private final Activity mActivity;
    @NonNull
    private final DBA mDb;
    @Nullable
    private final Runnable mOnChanged;
    @NonNull
    private final ArrayAdapter<String> mAdapter;

    public EditSeriesDialog(@NonNull final Activity activity,
                            @NonNull final DBA db,
                            @Nullable final Runnable onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
        mAdapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_dropdown_item_1line,
                                      mDb.getAllSeriesNames());
    }

    public void edit(@NonNull final Series source) {
        // Build the base dialog
        final View root = mActivity.getLayoutInflater()
                                   .inflate(R.layout.dialog_edit_series, null);

        final AutoCompleteTextView seriesView = root.findViewById(R.id.name);
        //noinspection ConstantConditions
        seriesView.setText(source.getName());
        seriesView.setAdapter(mAdapter);
        final Checkable isCompleteView = root.findViewById(R.id.is_complete);
        isCompleteView.setChecked(source.isComplete());

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.title_edit_series)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = seriesView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.warning_required_name);
                    return;
                }
                boolean isComplete = isCompleteView.isChecked();
                dialog.dismiss();

                if (source.getName().equals(newName)
                        && source.isComplete() == isComplete) {
                    return;
                }
                source.setName(newName);
                source.setComplete(isComplete);
                mDb.updateOrInsertSeries(source);
                if (mOnChanged != null) {
                    mOnChanged.run();
                }
            }
        });

        //noinspection ConstantConditions
        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
