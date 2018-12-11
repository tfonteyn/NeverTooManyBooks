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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditSeriesDialog;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * Activity to edit a list of series provided in an ArrayList<Series> and return an updated list.
 *
 * Calling point is a Book; see {@link EditSeriesDialog} for list
 *
 * @author Philip Warner
 */
public class EditSeriesListActivity extends EditObjectListActivity<Series> {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_SERIES;

    /** AutoCompleteTextView */
    private ArrayAdapter<String> mSeriesAdapter;

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditSeriesListActivity() {
        super(R.layout.activity_edit_list_series, R.layout.row_edit_series_list, UniqueId.BKEY_SERIES_ARRAY);
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);
        mSeriesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, mDb.getAllSeriesNames());
        ((AutoCompleteTextView) this.findViewById(R.id.name)).setAdapter(mSeriesAdapter);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Tracker.exitOnCreate(this);
    }

    @Override
    protected void onAdd(final @NonNull View target) {
        AutoCompleteTextView seriesField = EditSeriesListActivity.this.findViewById(R.id.name);
        String seriesTitle = seriesField.getText().toString().trim();

        if (!seriesTitle.isEmpty()) {
            EditText numberField = EditSeriesListActivity.this.findViewById(R.id.series_num);
            Series newSeries = new Series(seriesTitle, numberField.getText().toString().trim());
            // see if we can find it based on the name
            newSeries.id = mDb.getSeriesIdByName(newSeries.name);
            for (Series series : mList) {
                if (series.equals(newSeries)) {
                    StandardDialogs.showUserMessage(EditSeriesListActivity.this, R.string.warning_series_already_in_list);
                    return;
                }
            }
            mList.add(newSeries);
            onListChanged();
            seriesField.setText("");
            numberField.setText("");
        } else {
            StandardDialogs.showUserMessage(EditSeriesListActivity.this, R.string.warning_required_series);
        }
    }

    /** TOMF: TODO: almost duplicate code in {@link EditSeriesDialog} */
    private void edit(final @NonNull Series series) {
        // Build the base dialog
        final View root = EditSeriesListActivity.this.getLayoutInflater().inflate(R.layout.dialog_edit_book_series, null);

        final AutoCompleteTextView seriesNameField = root.findViewById(R.id.name);
        //noinspection ConstantConditions
        seriesNameField.setText(series.name);
        seriesNameField.setAdapter(mSeriesAdapter);

        final EditText seriesNumberField = root.findViewById(R.id.series_num);
        //noinspection ConstantConditions
        seriesNumberField.setText(series.number);

        final AlertDialog dialog = new AlertDialog.Builder(EditSeriesListActivity.this)
                .setView(root)
                .setTitle(R.string.title_edit_book_series)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = seriesNameField.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(EditSeriesListActivity.this, R.string.warning_required_series);
                    return;
                }

                Series newSeries = new Series(newName, seriesNumberField.getText().toString().trim());
                confirmEdit(series, newSeries);

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

        // Same name? but different number... just update
        if (to.name.equals(from.name)) {
            from.copyFrom(to);
            Series.pruneSeriesList(mList);
            Utils.pruneList(mDb, mList);
            onListChanged();
            return;
        }

        // Get their id's
        from.id = mDb.getSeriesId(from); //TODO: this call is not needed I think
        to.id = mDb.getSeriesId(to);

        //See if the old series is used by any other books.; allows us to skip a global replace
        long nRefs = mDb.countSeriesBooks(from);
        boolean fromHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // series is the same (but maybe different case), or is only used in this book
        if (to.id == from.id || !fromHasOthers) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            Series.pruneSeriesList(mList);
            Utils.pruneList(mDb, mList);
            if (from.id == 0) {
                from.id = mDb.getSeriesId(from);
            }
            mDb.insertOrUpdateSeries(from);
            onListChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old series is used in more than one place.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.changed_series_how_apply, from.name, to.name, allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book), new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                from.copyFrom(to);
                Series.pruneSeriesList(mList);
                Utils.pruneList(mDb, mList);
                onListChanged();
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                mDb.globalReplaceSeries(from, to);
                from.copyFrom(to);
                Series.pruneSeriesList(mList);
                Utils.pruneList(mDb, mList);
                onListChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * Called when user clicks the 'Save' button.
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt>if activity should exit, false to abort exit.
     */
    @Override
    protected boolean onSave(final @NonNull Intent data) {
        final AutoCompleteTextView view = findViewById(R.id.name);
        String s = view.getText().toString().trim();
        if (s.isEmpty()) {
            // no current edit, so we're good to go
            return super.onSave(data);
        }

        StandardDialogs.showConfirmUnsavedEditsDialog(this,
                /* run when user clicks 'exit' */
                new Runnable() {
                    @Override
                    public void run() {
                        view.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                    /* if they click 'cancel', the dialog just closes without further actions */
                });

        return false;
    }

    protected SimpleListAdapter<Series> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<Series> list) {
        return new SeriesListAdapter(this, rowViewId, list);
    }

    protected class SeriesListAdapter extends SimpleListAdapter<Series> {

        SeriesListAdapter(final @NonNull Context context,
                          final @LayoutRes int rowViewId,
                          final @NonNull ArrayList<Series> items) {
            super(context, rowViewId, items);
        }

        @Override
        public void onGetView(final @NonNull View convertView, final @NonNull Series series) {
            Holder holder = ViewTagger.getTag(convertView);
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.row_series = convertView.findViewById(R.id.row_series);
                holder.row_series_sort = convertView.findViewById(R.id.row_series_sort);
                // Tag the parts that need it
                ViewTagger.setTag(convertView, holder);
            }
            // Setup the variant fields in the holder
            if (holder.row_series != null) {
                holder.row_series.setText(series.getDisplayName());
            }
            if (holder.row_series_sort != null) {
                if (series.getDisplayName().equals(series.getSortName())) {
                    holder.row_series_sort.setVisibility(View.GONE);
                } else {
                    holder.row_series_sort.setVisibility(View.VISIBLE);
                    holder.row_series_sort.setText(series.getSortName());
                }
            }
        }

        /**
         * edit the Series we clicked on
         */
        @Override
        public void onRowClick(final @NonNull View target,
                               final @NonNull Series series,
                               final int position) {
            edit(series);
        }

        /**
         * delegate to the ListView host
         */
        @Override
        public void onListChanged() {
            super.onListChanged();
            EditSeriesListActivity.this.onListChanged();
        }
    }

    /**
     * Holder pattern for each row.
     */
    private class Holder {
        TextView row_series;
        TextView row_series_sort;
    }
}
