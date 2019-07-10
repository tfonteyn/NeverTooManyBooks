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
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.dialogs.entities.EditSeriesDialogFragment;
import com.eleybourn.bookcatalogue.entities.ItemWithIdFixup;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit a list of series provided in an {@code ArrayList<Series>}
 * and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditSeriesDialogFragment} for list
 *
 * @author Philip Warner
 */
public class EditSeriesListActivity
        extends EditObjectListActivity<Series> {

    /** Main screen Series Number field. */
    private TextView mSeriesNumberView;

    /**
     * Constructor.
     */
    public EditSeriesListActivity() {
        super(UniqueId.BKEY_SERIES_ARRAY);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_series;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.title_edit_book_series);

        mAutoCompleteAdapter = new ArrayAdapter<>(this,
                                                  android.R.layout.simple_dropdown_item_1line,
                                                  mDb.getAllSeriesNames());

        mAutoCompleteTextView = findViewById(R.id.series);
        mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);

        mSeriesNumberView = findViewById(R.id.series_num);
    }

    @Override
    protected void onAdd(@NonNull final View target) {
        String name = mAutoCompleteTextView.getText().toString().trim();
        if (name.isEmpty()) {
            UserMessage.show(mAutoCompleteTextView, R.string.warning_required_name);
            return;
        }

        Series newSeries = new Series(name);
        newSeries.setNumber(mSeriesNumberView.getText().toString().trim());

        // see if it already exists
        newSeries.fixupId(mDb);
        // and check it's not already in the list.
        for (Series series : mList) {
            if (series.equals(newSeries)) {
                UserMessage.show(mAutoCompleteTextView, R.string.warning_series_already_in_list);
                return;
            }
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newSeries);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAutoCompleteTextView.setText("");
        mSeriesNumberView.setText("");
    }

    @Override
    protected RecyclerViewAdapterBase
    createListAdapter(@NonNull final ArrayList<Series> list,
                      @NonNull final StartDragListener dragStartListener) {
        return new SeriesListAdapter(this, list, dragStartListener);
    }

    /**
     * Called from the editor dialog fragment after the user was done.
     */
    private void processChanges(@NonNull final Series series,
                                @NonNull final String newName,
                                final boolean isComplete,
                                @NonNull final String newNumber) {

        // anything actually changed ?
        if (series.getName().equals(newName) && series.isComplete() == isComplete) {
            if (!series.getNumber().equals(newNumber)) {
                // Number is different.
                // Number is not part of the Series table, but of the book_series table.
                // so just update it and we're done here.
                series.setNumber(newNumber);
                Series.pruneSeriesList(mList);
                ItemWithIdFixup.pruneList(mDb, mList);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // At this point, we know changes were made.
        // Create a new Series as a holder for the changes.
        final Series newSeries = new Series(newName, isComplete);
        newSeries.setNumber(newNumber);

        //See if the old one is used by any other books.
        long nrOfReferences = mDb.countBooksInSeries(series);
        boolean usedByOthers = nrOfReferences > (mRowId == 0 ? 0 : 1);

        // if it's not, then we can simply re-use the old object.
        if (!usedByOthers) {
            // Use the original series, but update its fields
            series.copyFrom(newSeries);
            Series.pruneSeriesList(mList);
            ItemWithIdFixup.pruneList(mDb, mList);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the names are genuinely different and the old series is used
        // in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_apply_series_changed,
                                      series.getSortName(), newSeries.getSortName(),
                                      allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book),
                         (d, which) -> {
                             d.dismiss();

                             series.copyFrom(newSeries);
                             Series.pruneSeriesList(mList);
                             ItemWithIdFixup.pruneList(mDb, mList);
                             mListAdapter.notifyDataSetChanged();
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, (d, which) -> {
            d.dismiss();

            mGlobalReplacementsMade = mDb.globalReplaceSeries(series, newSeries,
                                                              LocaleUtils.getPreferredLocal());
            series.copyFrom(newSeries);
            Series.pruneSeriesList(mList);
            ItemWithIdFixup.pruneList(mDb, mList);
            mListAdapter.notifyDataSetChanged();
        });

        dialog.show();
    }

    /**
     * Edit a Series from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     */
    public static class EditBookSeriesDialogFragment
            extends DialogFragment {

        /** Fragment manager tag. */
        private static final String TAG = "EditBookSeriesDialogFragment";

        private AutoCompleteTextView mNameView;
        private Checkable mIsCompleteView;
        private EditText mNumberView;

        private String mSeriesName;
        private boolean mSeriesIsComplete;
        private String mSeriesNumber;

        private EditSeriesListActivity mActivity;

        /**
         * Constructor.
         *
         * @param series to edit
         *
         * @return the instance
         */
        static EditBookSeriesDialogFragment newInstance(@NonNull final Series series) {
            EditBookSeriesDialogFragment frag = new EditBookSeriesDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(DBDefinitions.KEY_SERIES_TITLE, series);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onAttach(@NonNull final Context context) {
            super.onAttach(context);
            mActivity = (EditSeriesListActivity) context;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            Bundle args = requireArguments();

            final Series series = args.getParcelable(DBDefinitions.KEY_SERIES_TITLE);
            if (savedInstanceState == null) {
                //noinspection ConstantConditions
                mSeriesName = series.getName();
                mSeriesIsComplete = series.isComplete();
                mSeriesNumber = series.getNumber();
            } else {
                mSeriesName = savedInstanceState.getString(DBDefinitions.KEY_SERIES_TITLE);
                mSeriesIsComplete = savedInstanceState.getBoolean(
                        DBDefinitions.KEY_SERIES_IS_COMPLETE);
                mSeriesNumber = savedInstanceState.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            }
            @SuppressWarnings("ConstantConditions")
            View root = getActivity().getLayoutInflater()
                                     .inflate(R.layout.dialog_edit_book_series, null);

            // the dialog fields != screen fields.
            mNameView = root.findViewById(R.id.series);
            mNameView.setText(mSeriesName);
            mNameView.setAdapter(mActivity.mAutoCompleteAdapter);

            mIsCompleteView = root.findViewById(R.id.is_complete);
            if (mIsCompleteView != null) {
                mIsCompleteView.setChecked(mSeriesIsComplete);
            }

            mNumberView = root.findViewById(R.id.series_num);
            mNumberView.setText(mSeriesNumber);

            //noinspection ConstantConditions
            return new AlertDialog.Builder(getContext())
                    .setView(root)
                    .setTitle(R.string.title_edit_book_series)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                        mSeriesName = mNameView.getText().toString().trim();
                        if (mSeriesName.isEmpty()) {
                            UserMessage.show(mNameView, R.string.warning_required_name);
                            return;
                        }
                        if (mIsCompleteView != null) {
                            mSeriesIsComplete = mIsCompleteView.isChecked();
                        }
                        mSeriesNumber = mNumberView.getText().toString().trim();
                        dismiss();

                        //noinspection ConstantConditions
                        mActivity.processChanges(series, mSeriesName, mSeriesIsComplete,
                                                 mSeriesNumber);
                    })
                    .create();
        }

        @Override
        public void onPause() {
            mSeriesName = mNameView.getText().toString().trim();
            if (mIsCompleteView != null) {
                mSeriesIsComplete = mIsCompleteView.isChecked();
            }
            mSeriesNumber = mNumberView.getText().toString().trim();

            super.onPause();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_SERIES_TITLE, mSeriesName);
            outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mSeriesIsComplete);
            outState.putString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES, mSeriesNumber);
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView seriesView;
        @NonNull
        final TextView seriesSortView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            seriesView = itemView.findViewById(R.id.row_series);
            seriesSortView = itemView.findViewById(R.id.row_series_sort);
        }
    }

    protected class SeriesListAdapter
            extends RecyclerViewAdapterBase<Series, Holder> {

        SeriesListAdapter(@NonNull final Context context,
                          @NonNull final ArrayList<Series> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_series_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            Series series = getItem(position);

            holder.seriesView.setText(series.getLabel());

            if (series.getLabel().equals(series.getSortName())) {
                holder.seriesSortView.setVisibility(View.GONE);
            } else {
                holder.seriesSortView.setVisibility(View.VISIBLE);
                holder.seriesSortView.setText(series.getSortName());
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.findFragmentByTag(EditBookSeriesDialogFragment.TAG) == null) {
                    EditBookSeriesDialogFragment.newInstance(series)
                                                .show(fm, EditBookSeriesDialogFragment.TAG);
                }
            });
        }
    }
}
