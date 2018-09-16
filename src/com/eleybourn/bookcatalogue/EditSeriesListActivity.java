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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.*;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Utils;

import static com.eleybourn.bookcatalogue.UniqueId.BKEY_SERIES_ARRAY;

public class EditSeriesListActivity extends EditObjectListActivity<Series> {

    private ArrayAdapter<String> mSeriesAdapter;

    public EditSeriesListActivity() {
        super(BKEY_SERIES_ARRAY, R.layout.activity_edit_list_series, R.layout.row_edit_series_list);
    }

    @Override
    protected void onSetupView(@NonNull View target, @NonNull Series object, int position) {
        if (object != null) {
            TextView dt = target.findViewById(R.id.row_series);
            if (dt != null) {
                dt.setText(object.getDisplayName());
            }
            TextView st = target.findViewById(R.id.row_series_sort);
            if (st != null) {
                if (object.getDisplayName().equals(object.getSortName())) {
                    st.setVisibility(View.GONE);
                } else {
                    st.setVisibility(View.VISIBLE);
                    st.setText(object.getSortName());
                }
            }
            TextView et = target.findViewById(R.id.row_series_num);
            if (et != null) {
                et.setText(object.number);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle(mBookTitle);

        try {

            mSeriesAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line,
                    mDb.fetchAllSeriesArray());
            ((AutoCompleteTextView) this.findViewById(R.id.series)).setAdapter(mSeriesAdapter);

            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    @Override
    protected void onAdd(View v) {
        AutoCompleteTextView seriesField = EditSeriesListActivity.this.findViewById(R.id.series);
        String seriesTitle = seriesField.getText().toString().trim();
        if (!seriesTitle.isEmpty()) {
            EditText numberField = EditSeriesListActivity.this.findViewById(R.id.series_num);
            Series series = new Series(seriesTitle, numberField.getText().toString());
            series.id = mDb.getSeriesId(series);
            for (Series s : mList) {
                if (s.equals(series)) {
                    Toast.makeText(EditSeriesListActivity.this, getResources().getString(R.string.series_already_in_list), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            mList.add(series);
            mAdapter.notifyDataSetChanged();
            seriesField.setText("");
            numberField.setText("");
        } else {
            Toast.makeText(EditSeriesListActivity.this, getResources().getString(R.string.series_is_blank), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onRowClick(@NonNull View target, @NonNull final Series series, int position) {
        final Dialog dialog = new StandardDialogs.BasicDialog(this);
        dialog.setContentView(R.layout.dialog_edit_book_series);
        dialog.setTitle(R.string.edit_book_series);

        setTextOrHideView(R.id.booktitle, mBookTitle);

        AutoCompleteTextView seriesNameField = dialog.findViewById(R.id.series);
        seriesNameField.setText(series.name);
        seriesNameField.setAdapter(mSeriesAdapter);

        EditText seriesNumberField = dialog.findViewById(R.id.series_num);
        seriesNumberField.setText(series.number);

        Button saveButton = dialog.findViewById(R.id.confirm);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutoCompleteTextView seriesNameField = dialog.findViewById(R.id.series);
                String newName = seriesNameField.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(EditSeriesListActivity.this, R.string.series_is_blank, Toast.LENGTH_LONG).show();
                    return;
                }

                EditText seriesNumberField = dialog.findViewById(R.id.series_num);
                Series newSeries = new Series(newName, seriesNumberField.getText().toString());
                confirmEditSeries(series, newSeries);

                dialog.dismiss();
            }
        });
        Button cancelButton = dialog.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEditSeries(final Series oldSeries, final Series newSeries) {
        // First, deal with a some special cases...

        boolean nameIsSame = (newSeries.name.compareTo(oldSeries.name) == 0);
        // Case: Unchanged.
        if (nameIsSame && newSeries.number.compareTo(oldSeries.number) == 0) {
            // No change to anything; nothing to do
            return;
        }
        if (nameIsSame) {
            // Same name, different number... just update
            oldSeries.copyFrom(newSeries);
            Utils.pruneSeriesList(mList);
            Utils.pruneList(mDb, mList);
            mAdapter.notifyDataSetChanged();
            return;
        }

        // Get the new IDs
        oldSeries.id = mDb.getSeriesId(oldSeries);
        newSeries.id = mDb.getSeriesId(newSeries);

        // See if the old series is used in any other books.
        long nRefs = mDb.getSeriesBookCount(oldSeries);
        boolean oldHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // Case: series is the same (but different case), or is only used in this book
        if (newSeries.id == oldSeries.id || !oldHasOthers) {
            // Just update with the most recent spelling and format
            oldSeries.copyFrom(newSeries);
            Utils.pruneSeriesList(mList);
            Utils.pruneList(mDb, mList);
            mDb.sendSeries(oldSeries);
            mAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old series is used in more than one place.
        String format = getResources().getString(R.string.changed_series_how_apply);
        String allBooks = getResources().getString(R.string.all_books);
        String thisBook = getResources().getString(R.string.this_book);
        String message = String.format(format, oldSeries.name, newSeries.name, allBooks);

        final AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(message).create();

        alertDialog.setTitle(getResources().getString(R.string.scope_of_change));
        alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, thisBook, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                oldSeries.copyFrom(newSeries);
                Utils.pruneSeriesList(mList);
                Utils.pruneList(mDb, mList);
                mAdapter.notifyDataSetChanged();
                alertDialog.dismiss();
            }
        });

        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mDb.globalReplaceSeries(oldSeries, newSeries);
                oldSeries.copyFrom(newSeries);
                Utils.pruneSeriesList(mList);
                Utils.pruneList(mDb, mList);
                mAdapter.notifyDataSetChanged();
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    @Override
    protected boolean onSave(@NonNull Intent intent) {
        final AutoCompleteTextView t = EditSeriesListActivity.this.findViewById(R.id.series);
        Resources res = this.getResources();
        String s = t.getText().toString().trim();
        if (!s.isEmpty()) {
            final AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(res.getText(R.string.unsaved_edits)).create();

            alertDialog.setTitle(res.getText(R.string.unsaved_edits_title));
            alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, res.getText(R.string.yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    t.setText("");
                    findViewById(R.id.confirm).performClick();
                }
            });

            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, res.getText(R.string.no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //do nothing
                }
            });

            alertDialog.show();
            return false;
        } else {
            return true;
        }
    }
}
