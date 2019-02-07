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
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditSeriesDialog;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;

/**
 * Activity to edit a list of series provided in an ArrayList<Series> and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditSeriesDialog} for list
 *
 * @author Philip Warner
 */
public class EditSeriesListActivity
        extends EditObjectListActivity<Series> {

    /** Main screen Series name field. */
    private AutoCompleteTextView mSeriesNameView;
    /** Main screen Series Number field. */
    private TextView mSeriesNumberView;
    /** AutoCompleteTextView for mSeriesNameView. */
    private ArrayAdapter<String> mSeriesAdapter;

    /** flag indicating global changes were made. Used in setResult. */
    private boolean mGlobalChangeMade;

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditSeriesListActivity() {
        super(R.layout.activity_edit_list_series, R.layout.row_edit_series_list,
              UniqueId.BKEY_SERIES_ARRAY);
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        mSeriesAdapter = new ArrayAdapter<>(this,
                                            android.R.layout.simple_dropdown_item_1line,
                                            mDb.getAllSeriesNames());

        mSeriesNameView = findViewById(R.id.name);
        mSeriesNameView.setAdapter(mSeriesAdapter);
        mSeriesNumberView = findViewById(R.id.series_num);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    /**
     * The user entered new data in the edit field and clicked 'save'.
     *
     * @param target The view that was clicked ('add' button).
     */
    @Override
    protected void onAdd(@NonNull final View target) {
        String title = mSeriesNameView.getText().toString().trim();
        if (title.isEmpty()) {
            StandardDialogs.showUserMessage(this, R.string.warning_required_name);
            return;
        }

        Series newSeries = new Series(title);
        newSeries.setNumber(mSeriesNumberView.getText().toString().trim());

        // see if it already exists
        newSeries.fixupId(mDb);
        // and check it's not already in the list.
        for (Series series : mList) {
            if (series.equals(newSeries)) {
                StandardDialogs.showUserMessage(this, R.string.warning_series_already_in_list);
                return;
            }
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newSeries);
        onListChanged();

        // and clear for next entry.
        mSeriesNameView.setText("");
        mSeriesNumberView.setText("");
    }

    /**
     * Edit a Series from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     *
     * @param series to edit
     */
    private void edit(@NonNull final Series series) {

        // Build the base dialog
        final View root = getLayoutInflater().inflate(R.layout.dialog_edit_book_series, null);

        // the dialog fields != screen fields.
        final AutoCompleteTextView editNameView = root.findViewById(R.id.name);
        final EditText editNumberView = root.findViewById(R.id.series_num);

        //noinspection ConstantConditions
        editNameView.setText(series.getName());
        editNameView.setAdapter(mSeriesAdapter);
        //noinspection ConstantConditions
        editNumberView.setText(series.getNumber());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .setTitle(R.string.title_edit_book_series)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = editNameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(EditSeriesListActivity.this,
                                                    R.string.warning_required_name);
                    return;
                }
                String newNumber = editNumberView.getText().toString().trim();
                dialog.dismiss();

                // anything actually changed ?
                if (series.getName().equals(newName)) {
                    if (!series.getNumber().equals(newNumber)) {
                        // Name is the same. Number is different.
                        // Number is not part of the Series table, but of the book_series table.
                        // so just update it and we're done here.
                        series.setNumber(newNumber);
                        Series.pruneSeriesList(mList);
                        Utils.pruneList(mDb, mList);
                        onListChanged();
                    }
                    return;
                }

                // At this point, we know changes were made.
                // Create a new Series as a holder for the changes.
                Series newSeries = new Series(newName, series.isComplete());
                newSeries.setNumber(newNumber);

                processChanges(series, newSeries);
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

    private void processChanges(@NonNull final Series series,
                                @NonNull final Series newSeries) {

        //See if the old one is used by any other books.
        long nrOfReferences = mDb.countBooksInSeries(series);
        boolean usedByOthers = nrOfReferences > (mRowId == 0 ? 0 : 1);

        // if it's not, then we can simply re-use the old object.
        if (!usedByOthers) {
            // Use the original series, but update its fields
            series.copyFrom(newSeries);
            Series.pruneSeriesList(mList);
            Utils.pruneList(mDb, mList);
            onListChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old series
        // is used in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_apply_series_changed,
                                      series.getSortName(), newSeries.getSortName(), allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();

                                 series.copyFrom(newSeries);
                                 Series.pruneSeriesList(mList);
                                 Utils.pruneList(mDb, mList);
                                 onListChanged();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks,
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();

                                 mGlobalChangeMade = mDb.globalReplaceSeries(series, newSeries);

                                 series.copyFrom(newSeries);
                                 Series.pruneSeriesList(mList);
                                 Utils.pruneList(mDb, mList);
                                 onListChanged();
                             }
                         });

        dialog.show();
    }

    /**
     * Called when user clicks the 'Save' button.
     *
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt> if activity should exit, <tt>false</tt> to abort exit.
     */
    @Override
    protected boolean onSave(@NonNull final Intent data) {
        String name = mSeriesNameView.getText().toString().trim();
        if (name.isEmpty()) {
            // no current edit, so we're good to go. Add the global flag.
            data.putExtra(UniqueId.BKEY_GLOBAL_CHANGES_MADE, mGlobalChangeMade);
            return super.onSave(data);
        }

        StandardDialogs.showConfirmUnsavedEditsDialog(
                this,
                /* run when user clicks 'exit' */
                new Runnable() {
                    @Override
                    public void run() {
                        mSeriesNameView.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                });

        return false;
    }

    protected SimpleListAdapter<Series> createListAdapter(@LayoutRes final int rowLayoutId,
                                                          @NonNull final ArrayList<Series> list) {
        return new SeriesListAdapter(this, rowLayoutId, list);
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        TextView rowSeriesView;
        TextView rowSeriesSortView;
    }

    protected class SeriesListAdapter
            extends SimpleListAdapter<Series> {

        SeriesListAdapter(@NonNull final Context context,
                          @LayoutRes final int rowLayoutId,
                          @NonNull final ArrayList<Series> items) {
            super(context, rowLayoutId, items);
        }

        @Override
        public void onGetView(@NonNull final View convertView,
                              @NonNull final Series item) {
            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.rowSeriesView = convertView.findViewById(R.id.row_series);
                holder.rowSeriesSortView = convertView.findViewById(R.id.row_series_sort);

                convertView.setTag(holder);
            }
            // Setup the variant fields in the holder
            if (holder.rowSeriesView != null) {
                holder.rowSeriesView.setText(item.getDisplayName());
            }
            if (holder.rowSeriesSortView != null) {
                if (item.getDisplayName().equals(item.getSortName())) {
                    holder.rowSeriesSortView.setVisibility(View.GONE);
                } else {
                    holder.rowSeriesSortView.setVisibility(View.VISIBLE);
                    holder.rowSeriesSortView.setText(item.getSortName());
                }
            }
        }

        /**
         * edit the item we clicked on.
         */
        @Override
        public void onRowClick(@NonNull final View target,
                               @NonNull final Series item,
                               final int position) {
            edit(item);
        }

        /**
         * delegate to the ListView host.
         */
        @Override
        public void onListChanged() {
            EditSeriesListActivity.this.onListChanged();
        }
    }
}
