/*
 * @Copyright 2020 HardBackNutter
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
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Series of a Book.
 */
public class EditBookSeriesFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    public static final String TAG = "EditBookSeriesFragment";

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookModel.setDirty(true);
                }
            };

    /** Series name field. */
    private AutoCompleteTextView mSeriesNameView;
    /** Main screen Series Number field. */
    private TextView mSeriesNumberView;
    /** The View for the list. */
    private RecyclerView mListView;
    /** the rows. */
    private ArrayList<Series> mList;
    /** The adapter for the list itself. */
    private RecyclerViewAdapterBase mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_list_series, container, false);
        mListView = view.findViewById(android.R.id.list);
        mSeriesNameView = view.findViewById(R.id.series);
        mSeriesNumberView = view.findViewById(R.id.series_num);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        if (!EditBookFragment.showAuthSeriesOnTabs(getContext())) {
            //noinspection ConstantConditions
            getActivity().findViewById(R.id.tab_panel).setVisibility(View.GONE);
        }

        DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line,
                mBookModel.getDb().getSeriesTitles());
        mSeriesNameView.setAdapter(nameAdapter);

        // set up the list view. The adapter is setup in onLoadFields
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(layoutManager);
        mListView.setHasFixedSize(true);

        // adding a new entry
        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_add).setOnClickListener(v -> onAdd());
    }

    @Override
    protected void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        mList = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new SeriesListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mListView.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        // The list is not a 'real' field. Hence the need to store it manually here.
        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mList);
    }

    @Override
    public boolean hasUnfinishedEdits() {
        // We only check the title field; disregarding the series number field.
        return !mSeriesNameView.getText().toString().isEmpty();
    }

    private void onAdd() {
        String name = mSeriesNameView.getText().toString().trim();
        if (name.isEmpty()) {
            Snackbar.make(mSeriesNameView, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        //noinspection ConstantConditions
        final Locale bookLocale = mBookModel.getBook().getLocale(getContext());

        Series newSeries = new Series(name);
        newSeries.setNumber(mSeriesNumberView.getText().toString().trim());

        // see if it already exists
        newSeries.fixId(getContext(), mBookModel.getDb(), bookLocale);
        // and check it's not already in the list.
        if (mList.contains(newSeries)) {
            Snackbar.make(mSeriesNameView, R.string.warning_series_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newSeries);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mSeriesNameView.setText("");
        mSeriesNumberView.setText("");
    }

    /**
     * Process the modified (if any) data.
     *
     * @param series  the user was editing (with the original data)
     * @param tmpData the modifications the user made in a placeholder object.
     *                Non-modified data was copied here as well.
     *                The id==0.
     */
    private void processChanges(@NonNull final Series series,
                                @NonNull final Series tmpData) {

        //noinspection ConstantConditions
        final Locale bookLocale = mBookModel.getBook().getLocale(getContext());

        // name not changed ?
        if (series.getTitle().equals(tmpData.getTitle())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            series.setComplete(tmpData.isComplete());

            // Number is not part of the Series table, but of the book_series table.
            if (!series.getNumber().equals(tmpData.getNumber())) {
                // so if the number is different, just update it
                series.setNumber(tmpData.getNumber());
                //noinspection ConstantConditions
                Series.pruneList(mList, getContext(), mBookModel.getDb(), bookLocale, false);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Series was modified.
        // Check if it's used by any other books.
        if (mBookModel.isSingleUsage(getContext(), series, bookLocale)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original Series object that the user was changing.
            series.copyFrom(tmpData, true);
            //noinspection ConstantConditions
            Series.pruneList(mList, getContext(), mBookModel.getDb(), bookLocale, false);
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
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setNeutralButton(allBooks, (dialog, which) -> {
                    // copy all new data
                    series.copyFrom(tmpData, true);
                    // This change is done in the database right NOW!
                    if (mBookModel.getDb().updateSeries(getContext(), series, bookLocale)) {
                        Series.pruneList(mList, getContext(), mBookModel.getDb(),
                                         bookLocale, false);
                        mBookModel.getBook().refreshSeriesList(getContext(), mBookModel.getDb());
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        Logger.warnWithStackTrace(getContext(), TAG, "Could not update",
                                                  "series=" + series,
                                                  "tmpSeries=" + tmpData);
                        new AlertDialog.Builder(getContext())
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
                    mBookModel.getDb().updateOrInsertSeries(getContext(), bookLocale, tmpData);
                    // unlink the old one (and unmodified), and link with the new one
                    // book/series positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(series);
                    mList.add(tmpData);
                    Series.pruneList(mList, getContext(), mBookModel.getDb(), bookLocale, false);
                    mListAdapter.notifyDataSetChanged();
                })
                .create()
                .show();
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

        /** Database Access. */
        private DAO mDb;

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDb = new DAO(TAG);

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

        @Override
        public void onDestroy() {
            if (mDb != null) {
                mDb.close();
            }
            super.onDestroy();
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

            Objects.requireNonNull(getTargetFragment(), "no target fragment set");

            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View root = layoutInflater.inflate(R.layout.dialog_edit_book_series, null);

            //noinspection ConstantConditions
            DiacriticArrayAdapter<String> seriesNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line,
                    mDb.getSeriesTitles());

            // the dialog fields != screen fields.
            mTitleView = root.findViewById(R.id.series);
            mTitleView.setText(mSeriesName);
            mTitleView.setAdapter(seriesNameAdapter);

            mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
            if (mIsCompleteView != null) {
                mIsCompleteView.setChecked(mSeriesIsComplete);
            }

            mNumberView = root.findViewById(R.id.series_num);
            if (mNumberView != null) {
                mNumberView.setText(mSeriesNumber);
            }

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

                        ((EditBookSeriesFragment) getTargetFragment())
                                .processChanges(mSeries, tmpSeries);
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
            holder.rowDetailsView.setOnClickListener(v -> {
                EditBookSeriesDialogFragment frag = new EditBookSeriesDialogFragment();
                Bundle args = new Bundle(1);
                args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);
                frag.setArguments(args);
                frag.setTargetFragment(EditBookSeriesFragment.this, 0);
                frag.show(getParentFragmentManager(), EditBookSeriesDialogFragment.TAG);
            });
        }
    }
}
