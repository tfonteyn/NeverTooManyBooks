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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
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
                    mBookViewModel.setDirty(true);
                }
            };
    /** View Binding. */
    private FragmentEditBookSeriesBinding mVb;
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
        mVb = FragmentEditBookSeriesBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        if (!Prefs.showEditBookTabAuthSeries(getContext())) {
            //noinspection ConstantConditions
            getActivity().findViewById(R.id.tab_panel).setVisibility(View.GONE);
        }

        final DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                mFragmentVM.getDb().getSeriesTitles());
        mVb.seriesTitle.setAdapter(nameAdapter);

        // set up the list view. The adapter is setup in onPopulateViews
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mVb.seriesList.setLayoutManager(layoutManager);
        mVb.seriesList.setHasFixedSize(true);

        // adding a new entry
        mVb.btnAdd.setOnClickListener(v -> onAdd());
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        mList = book.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new SeriesListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.seriesList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.seriesList);
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        // The list is not a 'real' field. Hence the need to store it manually here.
        // It requires no special validation.
        book.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mList);
    }

    @Override
    public boolean hasUnfinishedEdits() {
        // We only check the title field; disregarding the series number field.
        return !mVb.seriesTitle.getText().toString().isEmpty();
    }

    private void onAdd() {
        final String name = mVb.seriesTitle.getText().toString().trim();
        if (name.isEmpty()) {
            Snackbar.make(mVb.seriesTitle, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        //noinspection ConstantConditions
        final Locale bookLocale = mBookViewModel.getBook().getLocale(getContext());

        final Series newSeries = new Series(name);
        //noinspection ConstantConditions
        newSeries.setNumber(mVb.seriesNum.getText().toString().trim());

        // see if it already exists
        newSeries.fixId(getContext(), mFragmentVM.getDb(), bookLocale);
        // and check it's not already in the list.
        if (mList.contains(newSeries)) {
            Snackbar.make(mVb.seriesTitle, R.string.warning_series_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newSeries);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mVb.seriesTitle.setText("");
        mVb.seriesNum.setText("");
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
        final Locale bookLocale = mBookViewModel.getBook().getLocale(getContext());

        // name not changed ?
        if (series.getTitle().equals(tmpData.getTitle())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            series.setComplete(tmpData.isComplete());

            // Number is not part of the Series table, but of the book_series table.
            if (!series.getNumber().equals(tmpData.getNumber())) {
                // so if the number is different, just update it
                series.setNumber(tmpData.getNumber());
                //noinspection ConstantConditions
                Series.pruneList(mList, getContext(), mFragmentVM.getDb(), bookLocale, false);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Series was modified.
        // Check if it's used by any other books.
        if (mBookViewModel.isSingleUsage(getContext(), series, bookLocale)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original Series object that the user was changing.
            series.copyFrom(tmpData, true);
            //noinspection ConstantConditions
            Series.pruneList(mList, getContext(), mFragmentVM.getDb(), bookLocale, false);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the name of the Series was modified
        // and the it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        final String allBooks = getString(R.string.bookshelf_all_books);
        final String message = getString(R.string.confirm_apply_series_changed,
                                         series.getLabel(getContext()),
                                         tmpData.getLabel(getContext()),
                                         allBooks);
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, w) -> {
                    // copy all new data
                    series.copyFrom(tmpData, true);
                    // This change is done in the database right NOW!
                    if (mFragmentVM.getDb().updateSeries(getContext(), series, bookLocale)) {
                        Series.pruneList(mList, getContext(), mFragmentVM.getDb(),
                                         bookLocale, false);
                        mBookViewModel.refreshSeriesList(getContext());
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        Logger.warnWithStackTrace(getContext(), TAG, "Could not update",
                                                  "series=" + series,
                                                  "tmpSeries=" + tmpData);
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.ic_error)
                                .setMessage(R.string.error_unexpected_error)
                                .show();
                    }
                })
                .setPositiveButton(R.string.btn_this_book, (d, w) -> {
                    // treat the new data as a new Series; save it so we have a valid id.
                    // Note that if the user abandons the entire book edit,
                    // we will orphan this new Series. That's ok, it will get
                    // garbage collected from the database sooner or later.
                    mFragmentVM.getDb().updateOrInsertSeries(getContext(),
                                                             bookLocale, tmpData);
                    // unlink the old one (and unmodified), and link with the new one
                    // book/series positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(series);
                    mList.add(tmpData);
                    Series.pruneList(mList, getContext(), mFragmentVM.getDb(),
                                     bookLocale, false);
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

        Holder(@NonNull final View itemView) {
            super(itemView);
            seriesView = itemView.findViewById(R.id.row_series);
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
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "EditBookSeriesDialogFragment";

        /** The Series we're editing. */
        private Series mSeries;
        /** Current edit. */
        private String mSeriesTitle;
        /** Current edit. */
        private boolean mSeriesIsComplete;
        /** Current edit. */
        private String mSeriesNumber;

        /** Database Access. */
        private DAO mDb;
        /** View Binding. */
        private DialogEditBookSeriesBinding mVb;

        /**
         * Constructor.
         *
         * @param series to edit
         *
         * @return instance
         */
        static DialogFragment newInstance(@NonNull final Series series) {
            final DialogFragment frag = new EditBookSeriesDialogFragment();
            final Bundle args = new Bundle(1);
            args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDb = new DAO(TAG);

            final Bundle args = requireArguments();
            mSeries = args.getParcelable(DBDefinitions.KEY_FK_SERIES);
            Objects.requireNonNull(mSeries, ErrorMsg.ARGS_MISSING_SERIES);

            if (savedInstanceState == null) {
                mSeriesTitle = mSeries.getTitle();
                mSeriesIsComplete = mSeries.isComplete();
                mSeriesNumber = mSeries.getNumber();
            } else {
                mSeriesTitle = savedInstanceState.getString(DBDefinitions.KEY_FK_SERIES);
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

            Objects.requireNonNull(getTargetFragment(), ErrorMsg.NO_TARGET_FRAGMENT_SET);

            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            final LayoutInflater inflater = getActivity().getLayoutInflater();
            mVb = DialogEditBookSeriesBinding.inflate(inflater);

            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> seriesNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item, mDb.getSeriesTitles());

            // the dialog fields != screen fields.
            mVb.seriesTitle.setText(mSeriesTitle);
            mVb.seriesTitle.setAdapter(seriesNameAdapter);

            mVb.cbxIsComplete.setChecked(mSeriesIsComplete);

            mVb.seriesNum.setText(mSeriesNumber);

            return new MaterialAlertDialogBuilder(getContext())
                    .setView(mVb.getRoot())
                    .setIcon(R.drawable.ic_edit)
                    .setTitle(R.string.lbl_edit_series)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> dismiss())
                    .setPositiveButton(R.string.action_save, (d, w) -> {
                        // don't check on anything else here,
                        // we're doing more extensive checks later on.
                        mSeriesTitle = mVb.seriesTitle.getText().toString().trim();
                        if (mSeriesTitle.isEmpty()) {
                            Snackbar.make(mVb.seriesTitle, R.string.warning_missing_name,
                                          Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        // Create a new Series as a holder for all changes.
                        final Series tmpSeries = new Series(mSeriesTitle);

                        mSeriesIsComplete = mVb.cbxIsComplete.isChecked();
                        tmpSeries.setComplete(mSeriesIsComplete);

                        //noinspection ConstantConditions
                        mSeriesNumber = mVb.seriesNum.getText().toString().trim();
                        tmpSeries.setNumber(mSeriesNumber);

                        ((EditBookSeriesFragment) getTargetFragment())
                                .processChanges(mSeries, tmpSeries);
                    })
                    .create();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_FK_SERIES, mSeriesTitle);
            outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mSeriesIsComplete);
            outState.putString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES, mSeriesNumber);
        }

        @Override
        public void onPause() {
            mSeriesTitle = mVb.seriesTitle.getText().toString().trim();
            mSeriesIsComplete = mVb.cbxIsComplete.isChecked();
            //noinspection ConstantConditions
            mSeriesNumber = mVb.seriesNum.getText().toString().trim();

            super.onPause();
        }
    }

    private class SeriesListAdapter
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
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_series_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Series series = getItem(position);
            holder.seriesView.setText(series.getLabel(getContext()));

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> {
                final DialogFragment frag = EditBookSeriesDialogFragment.newInstance(series);
                frag.setTargetFragment(EditBookSeriesFragment.this, 0);
                frag.show(getParentFragmentManager(), EditBookSeriesDialogFragment.TAG);
            });
        }
    }
}
