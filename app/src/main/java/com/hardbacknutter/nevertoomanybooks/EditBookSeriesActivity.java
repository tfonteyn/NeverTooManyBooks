/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.EditObjectListActivity;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit a list of mSeries provided in an {@code ArrayList<Series>}
 * and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditSeriesDialogFragment} for list
 */
public class EditBookSeriesActivity
        extends EditObjectListActivity<Series> {

    /** Log tag. */
    private static final String TAG = "EditBookSeriesActivity";

    /** Main screen Series Number field. */
    private TextView mSeriesNumberView;

    /**
     * Constructor.
     */
    public EditBookSeriesActivity() {
        super(UniqueId.BKEY_SERIES_ARRAY);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_series;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_book_series);

        mAutoCompleteAdapter = new ArrayAdapter<>(this,
                                                  android.R.layout.simple_dropdown_item_1line,
                                                  mModel.getDb().getSeriesTitles());

        mAutoCompleteTextView = findViewById(R.id.series);
        mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);

        mSeriesNumberView = findViewById(R.id.series_num);
    }

    @Override
    protected RecyclerViewAdapterBase
    createListAdapter(@NonNull final ArrayList<Series> list,
                      @NonNull final StartDragListener dragStartListener) {
        return new SeriesListAdapter(this, list, dragStartListener);
    }

    @Override
    protected void onAdd(@NonNull final View target) {
        String name = mAutoCompleteTextView.getText().toString().trim();
        if (name.isEmpty()) {
            Snackbar.make(mAutoCompleteTextView, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        Series newSeries = new Series(name);
        newSeries.setNumber(mSeriesNumberView.getText().toString().trim());
        // see if it already exists
        newSeries.fixId(this, mModel.getDb(), mModel.getBookLocale());
        // and check it's not already in the list.
        if (mList.contains(newSeries)) {
            Snackbar.make(mAutoCompleteTextView, R.string.warning_series_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newSeries);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAutoCompleteTextView.setText("");
        mSeriesNumberView.setText("");
    }

    /**
     * Process the modified (if any) data.
     *
     * @param series    the user was editing (with the original data)
     * @param tmpData the modifications the user made in a placeholder object.
     *                  Non-modified data was copied here as well.
     *                  The id==0.
     */
    protected void processChanges(@NonNull final Series series,
                                  @NonNull final Series tmpData) {

        final Locale bookLocale = mModel.getBookLocale();

        // name not changed ?
        if (series.getTitle().equals(tmpData.getTitle())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            series.setComplete(tmpData.isComplete());

            // Number is not part of the Series table, but of the book_series table.
            if (!series.getNumber().equals(tmpData.getNumber())) {
                // so if the number is different, just update it
                series.setNumber(tmpData.getNumber());
                Series.pruneList(mList, this, mModel.getDb(), bookLocale, false);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Series was modified.
        // Check if it's used by any other books.
        long nrOfReferences = mModel.getDb().countBooksInSeries(this, series, bookLocale);
        // If it's not, we can simply modify the old object and we're done here.
        // There is no need to consult the user.
        if (mModel.isSingleUsage(nrOfReferences)) {
            // Copy the new data into the original Series object that the user was changing.
            series.copyFrom(tmpData, true);
            Series.pruneList(mList, this, mModel.getDb(), bookLocale, false);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the name of the Series was modified
        // and the it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);
        String message = getString(R.string.confirm_apply_series_changed,
                                   series.getTitle(),
                                   tmpData.getTitle(),
                                   allBooks);
        new AlertDialog.Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setNeutralButton(allBooks, (dialog, which) -> {
                    // copy all new data
                    series.copyFrom(tmpData, true);
                    // This change is done in the database right NOW!
                    int rowsAffected = mModel.getDb().updateSeries(this, series, bookLocale);
                    if (rowsAffected == 1) {
                        mModel.setGlobalReplacementsMade(true);
                        Series.pruneList(mList, this, mModel.getDb(), bookLocale, false);
                        mListAdapter.notifyDataSetChanged();
                    } else {
                        // eek?
                        Logger.warnWithStackTrace(this, TAG, "Could not update",
                                                  "series=" + series,
                                                  "tmpSeries=" + tmpData);
                        new AlertDialog.Builder(this)
                                .setIconAttribute(android.R.attr.alertDialogIcon)
                                .setMessage(R.string.error_unexpected_error)
                                .show();
                    }
                })
                .setPositiveButton(R.string.btn_this_book, (dialog, which) -> {
                    // treat the new data as a new Series; save it so we have a valid id.
                    // Note that if the user abandons the entire book edit,
                    // we will orphan this new Series. That's ok, it will get
                    // garbage collected from the database sooner or later.
                    mModel.getDb().updateOrInsertSeries(this, bookLocale, tmpData);
                    // unlink the old one (and unmodified), and link with the new one
                    // book/series positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(series);
                    mList.add(tmpData);
                    Series.pruneList(mList, this, mModel.getDb(), bookLocale, false);
                    mListAdapter.notifyDataSetChanged();
                })
                .create()
                .show();
    }

    /**
     * Edit a Series from the list.
     * It could exist (i.e. have an ID) or could be a previously added/new one (ID==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditBookSeriesDialogFragment
            extends DialogFragment {

        /** Log tag. */
        private static final String TAG = "EditBookSeriesDialogFragment";

        private EditBookSeriesActivity mHostActivity;

        private AutoCompleteTextView mTitleView;
        private Checkable mIsCompleteView;
        private EditText mNumberView;

        /** The Series we're editing. */
        private Series mSeries;
        /** Current edit. */
        private String mSeriesName;
        /** Current edit. */
        private boolean mSeriesIsComplete;
        /** Current edit. */
        private String mSeriesNumber;

        /**
         * Constructor.
         *
         * @param series to edit
         *
         * @return the instance
         */
        static EditBookSeriesDialogFragment newInstance(@NonNull final Series series) {
            EditBookSeriesDialogFragment frag = new EditBookSeriesDialogFragment();
            Bundle args = new Bundle(1);
            args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onAttach(@NonNull final Context context) {
            super.onAttach(context);
            mHostActivity = (EditBookSeriesActivity) context;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = requireArguments();
            mSeries = args.getParcelable(DBDefinitions.KEY_FK_SERIES);
            Objects.requireNonNull(mSeries, "Series must be passed in args");

            if (savedInstanceState == null) {
                mSeriesName = mSeries.getTitle();
                mSeriesIsComplete = mSeries.isComplete();
                mSeriesNumber = mSeries.getNumber();
            } else {
                mSeriesName = savedInstanceState.getString(DBDefinitions.KEY_FK_SERIES);
                mSeriesIsComplete = savedInstanceState
                        .getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, false);
                mSeriesNumber = savedInstanceState.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            }
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View root = layoutInflater.inflate(R.layout.dialog_edit_book_series, null);

            // the dialog fields != screen fields.
            mTitleView = root.findViewById(R.id.series);
            mTitleView.setText(mSeriesName);
            // we can re-use the activity adapter.
            mTitleView.setAdapter(mHostActivity.mAutoCompleteAdapter);

            mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
            if (mIsCompleteView != null) {
                mIsCompleteView.setChecked(mSeriesIsComplete);
            }

            mNumberView = root.findViewById(R.id.series_num);
            mNumberView.setText(mSeriesNumber);

            //noinspection ConstantConditions
            return new AlertDialog.Builder(getContext())
                    .setView(root)
                    .setTitle(R.string.title_edit_series)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> {
                        // don't check on anything else here,
                        // we're doing more extensive checks later on.
                        mSeriesName = mTitleView.getText().toString().trim();
                        if (mSeriesName.isEmpty()) {
                            Snackbar.make(mTitleView, R.string.warning_missing_name,
                                          Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        // Create a new Series as a holder for all changes.
                        Series tmpSeries = new Series(mSeriesName);

                        // allow for future layout(s) not displaying the isComplete checkbox
                        if (mIsCompleteView != null) {
                            mSeriesIsComplete = mIsCompleteView.isChecked();
                            tmpSeries.setComplete(mSeriesIsComplete);
                        } else {
                            tmpSeries.setComplete(mSeries.isComplete());
                        }

                        // allow for future layout(s) not displaying the number field
                        if (mNumberView != null) {
                            mSeriesNumber = mNumberView.getText().toString().trim();
                            tmpSeries.setNumber(mSeriesNumber);
                        } else {
                            tmpSeries.setNumber(mSeries.getNumber());
                        }

                        mHostActivity.processChanges(mSeries, tmpSeries);
                    })
                    .create();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_FK_SERIES, mSeriesName);
            outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mSeriesIsComplete);
            outState.putString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES, mSeriesNumber);
        }

        @Override
        public void onPause() {
            mSeriesName = mTitleView.getText().toString().trim();
            if (mIsCompleteView != null) {
                mSeriesIsComplete = mIsCompleteView.isChecked();
            }
            if (mNumberView != null) {
                mSeriesNumber = mNumberView.getText().toString().trim();
            }

            super.onPause();
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

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Series
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SeriesListAdapter(@NonNull final Context context,
                          @NonNull final List<Series> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View view = getLayoutInflater().inflate(R.layout.row_edit_series_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Context context = getContext();

            final Series series = getItem(position);
            final String seriesLabel = series.getLabel(context);
            holder.seriesView.setText(seriesLabel);

            if (!seriesLabel.equals(series.getSorting())) {
                holder.seriesSortView.setVisibility(View.VISIBLE);
                holder.seriesSortView.setText(series.getSorting());
            } else {
                holder.seriesSortView.setVisibility(View.GONE);
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> EditBookSeriesDialogFragment
                    .newInstance(series)
                    .show(getSupportFragmentManager(), EditBookSeriesDialogFragment.TAG));
        }
    }
}
