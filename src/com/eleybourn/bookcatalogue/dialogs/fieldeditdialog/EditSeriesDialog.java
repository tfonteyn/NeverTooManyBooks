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
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.EditSeriesListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Series;

/**
 * Dialog to edit a single series.
 * <p>
 * Calling point is a List; see {@link EditSeriesListActivity} for book
 */
public class EditSeriesDialog {

    @NonNull
    private final Activity mContext;
    @NonNull
    private final ArrayAdapter<String> mSeriesAdapter;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    public EditSeriesDialog(@NonNull final Activity activity,
                            @NonNull final CatalogueDBAdapter db,
                            @NonNull final Runnable onChanged) {
        mDb = db;
        mContext = activity;
        mSeriesAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_dropdown_item_1line,
                                            mDb.getAllSeriesNames());
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Series series) {
        // Build the base dialog
        final View root = mContext.getLayoutInflater()
                                  .inflate(R.layout.dialog_edit_series, null);

        final AutoCompleteTextView seriesView = root.findViewById(R.id.name);
        //noinspection ConstantConditions
        seriesView.setText(series.name);
        seriesView.setAdapter(mSeriesAdapter);
        final Checkable isCompleteView = root.findViewById(R.id.is_complete);
        isCompleteView.setChecked(series.isComplete);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(root)
                .setTitle(R.string.title_edit_series)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = seriesView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mContext, R.string.warning_required_series);
                    return;
                }
                boolean isComplete = isCompleteView.isChecked();
                confirmEdit(series, new Series(newName, isComplete, ""));
                dialog.dismiss();
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

    private void confirmEdit(@NonNull final Series from,
                             @NonNull final Series to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's TODO: this call is not needed I think
        from.id = mDb.getSeriesId(from);
        to.id = mDb.getSeriesId(to);

        // Case: series is the same
        if (to.id == from.id) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            if (from.id == 0) {
                from.id = mDb.getSeriesId(from);
            }
            mDb.insertOrUpdateSeries(from);
        } else {
            mDb.globalReplaceSeries(from, to);
            from.copyFrom(to);
        }
        mOnChanged.run();
    }
}
