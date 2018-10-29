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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Series;

public class EditSeriesDialog {
    @NonNull
    private final Activity mContext;
    @NonNull
    private final ArrayAdapter<String> mSeriesAdapter;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final Runnable mOnChanged;

    EditSeriesDialog(@NonNull final Activity activity, @NonNull final CatalogueDBAdapter db, @NonNull final  Runnable onChanged) {
        mDb = db;
        mContext = activity;
        mSeriesAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_dropdown_item_1line, mDb.getAllSeries());
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Series series) {
        final Dialog dialog = new StandardDialogs.BasicDialog(mContext);
        dialog.setContentView(R.layout.dialog_edit_series);
        dialog.setTitle(R.string.edit_series);

        final AutoCompleteTextView seriesView = dialog.findViewById(R.id.series);
        //noinspection ConstantConditions
        seriesView.setText(series.name);
        seriesView.setAdapter(mSeriesAdapter);

        //noinspection ConstantConditions
        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = seriesView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mContext, R.string.series_is_blank);
                    return;
                }
                confirmEdit(series, new Series(newName, ""));
                dialog.dismiss();
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

    private void confirmEdit(@NonNull final Series from, @NonNull final Series to) {
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
