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
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditAuthorDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditSeriesDialog;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;

/**
 * Activity to edit a list of series provided in an ArrayList<Series> and return an updated list.
 *
 * Calling point is a Book; see {@link EditAuthorDialog} for list
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
        super.onCreate(savedInstanceState);
        this.setTitle(mBookTitle);
        try {

            mSeriesAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, mDb.getAllSeries());
            ((AutoCompleteTextView) this.findViewById(R.id.name)).setAdapter(mSeriesAdapter);

            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        } catch (Exception e) {
            Logger.error(e);
        }
    }

    @Override
    protected void onAdd(final @NonNull View target) {
        AutoCompleteTextView seriesField = EditSeriesListActivity.this.findViewById(R.id.name);
        String seriesTitle = seriesField.getText().toString().trim();

        if (!seriesTitle.isEmpty()) {
            EditText numberField = EditSeriesListActivity.this.findViewById(R.id.series_num);
            Series newSeries = new Series(seriesTitle, numberField.getText().toString().trim());
            // see if we can find it based on the name
            newSeries.id = mDb.getSeriesId(newSeries.name);
            for (Series series : mList) {
                if (series.equals(newSeries)) {
                    StandardDialogs.showUserMessage(EditSeriesListActivity.this, R.string.warning_series_already_in_list);
                    return;
                }
            }
            mList.add(newSeries);
            mListAdapter.notifyDataSetChanged();
            seriesField.setText("");
            numberField.setText("");
        } else {
            StandardDialogs.showUserMessage(EditSeriesListActivity.this, R.string.warning_blank_series);
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
                .setTitle(R.string.dialog_title_edit_book_series)
                .create();

        //noinspection ConstantConditions
        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = seriesNameField.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(EditSeriesListActivity.this, R.string.warning_blank_series);
                    return;
                }

                Series newSeries = new Series(newName, seriesNumberField.getText().toString().trim());
                confirmEdit(series, newSeries);

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
            mListAdapter.notifyDataSetChanged();
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
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old series is used in more than one place.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.changed_series_how_apply, from.name, to.name, allBooks))
                .setTitle(R.string.scope_of_change)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.this_book), new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                from.copyFrom(to);
                Series.pruneSeriesList(mList);
                Utils.pruneList(mDb, mList);
                mListAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                mDb.globalReplaceSeries(from, to);
                from.copyFrom(to);
                Series.pruneSeriesList(mList);
                Utils.pruneList(mDb, mList);
                mListAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    @Override
    protected boolean onSave(final @NonNull Intent intent) {
        final AutoCompleteTextView view = findViewById(R.id.name);
        String s = view.getText().toString().trim();
        if (s.isEmpty()) {
            return true;
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.unsaved_edits))
                .setTitle(R.string.unsaved_edits_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        view.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.no),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        //do nothing
                    }
                });

        dialog.show();
        return false;

    }
    protected SimpleListAdapter<Series> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<Series> list) {
        return new SeriesListAdapter(this, rowViewId, list);
    }

    protected class SeriesListAdapter extends SimpleListAdapter<Series> implements SimpleListAdapterRowActionListener<Series> {
        SeriesListAdapter(final @NonNull Context context, final @LayoutRes int rowViewId, final @NonNull ArrayList<Series> items) {
            super(context, rowViewId, items);
        }

        @Override
        public void onGetView(final @NonNull View target, final @NonNull Series series) {
            TextView dt = target.findViewById(R.id.row_series);
            if (dt != null) {
                dt.setText(series.getDisplayName());
            }
            TextView st = target.findViewById(R.id.row_series_sort);
            if (st != null) {
                if (series.getDisplayName().equals(series.getSortName())) {
                    st.setVisibility(View.GONE);
                } else {
                    st.setVisibility(View.VISIBLE);
                    st.setText(series.getSortName());
                }
            }
        }

        @Override
        public void onRowClick(final @NonNull View target, final @NonNull Series series, final int position) {
            edit(series);
        }
    }

}
