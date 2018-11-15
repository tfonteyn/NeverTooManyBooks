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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import com.eleybourn.bookcatalogue.EditSeriesListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Series;

/**
 *  Dialog to edit a single series.
 *
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

    public EditSeriesDialog(final @NonNull Activity activity, final @NonNull CatalogueDBAdapter db, final @NonNull Runnable onChanged) {
        mDb = db;
        mContext = activity;
        mSeriesAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_dropdown_item_1line, mDb.getAllSeries());
        mOnChanged = onChanged;
    }

    public void edit(final @NonNull Series series) {
        // Build the base dialog
        final View root = mContext.getLayoutInflater().inflate(R.layout.dialog_edit_series, null);

        final AutoCompleteTextView seriesView = root.findViewById(R.id.filename);
        //noinspection ConstantConditions
        seriesView.setText(series.name);
        seriesView.setAdapter(mSeriesAdapter);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setView(root)
                .setTitle(R.string.title_edit_series)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = seriesView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mContext, R.string.warning_required_series);
                    return;
                }
                confirmEdit(series, new Series(newName, ""));
                dialog.dismiss();
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

    private void confirmEdit(final @NonNull Series from, final @NonNull Series to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's
        from.id = mDb.getSeriesId(from); //TODO: this call is not needed I think
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
