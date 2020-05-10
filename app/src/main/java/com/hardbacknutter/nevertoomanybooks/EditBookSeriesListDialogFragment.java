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

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesListBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Series of a Book.
 *
 * <strong>Warning:</strong> By exception this DialogFragment uses the parents ViewModel directly.
 * This means that any observables in the ViewModel must be tested/used with care, as their
 * destination view might not be available at the moment of an update being triggered.
 */
public class EditBookSeriesListDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookSeriesListDlg";
    /** Database Access. */
    private DAO mDb;

    /** The book. Must be in the Activity scope. */
    private BookViewModel mBookViewModel;

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookViewModel.setDirty(true);
                }
            };
    /** View Binding. */
    private DialogEditBookSeriesListBinding mVb;
    /** the rows. */
    private ArrayList<Series> mList;
    /** The adapter for the list itself. */
    private RecyclerViewAdapterBase mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * No-arg constructor.
     * <p>
     * Always force full screen as this dialog is to large/complicated.
     */
    public EditBookSeriesListDialogFragment() {
        super(R.layout.dialog_edit_book_series_list, true);
    }

    /**
     * Constructor.
     *
     * @return instance
     */
    public static DialogFragment newInstance() {
        return new EditBookSeriesListDialogFragment();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookSeriesListBinding.bind(view);

        //noinspection ConstantConditions
        mBookViewModel = new ViewModelProvider(getActivity()).get(BookViewModel.class);
        //noinspection ConstantConditions
        mBookViewModel.init(getContext(), getArguments());

        mVb.toolbar.setSubtitle(mBookViewModel.getBook().getTitle());
        mVb.toolbar.setNavigationOnClickListener(v -> {
            if (saveChanges()) {
                dismiss();
            }
        });
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add) {
                onAdd();
                return true;
            }
            return false;
        });

        final DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                mDb.getSeriesTitles());
        mVb.seriesTitle.setAdapter(nameAdapter);

        // soft-keyboards 'done' button act as a shortcut to add the series
        mVb.seriesNum.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd();
                return true;
            }
            return false;
        });
        // set up the list view. The adapter is setup in onPopulateViews
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mVb.seriesList.setLayoutManager(layoutManager);
        mVb.seriesList.setHasFixedSize(true);

        mList = mBookViewModel.getBook().getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        mListAdapter = new SeriesListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.seriesList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.seriesList);
    }

    private boolean saveChanges() {
        if (!mVb.seriesTitle.getText().toString().isEmpty()) {
            // Discarding applies to the temp series edit box only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                mVb.seriesTitle.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        mBookViewModel.updateSeries(mList);
        return true;
    }

    private void onAdd() {
        // clear any previous error
        mVb.lblSeries.setError(null);

        final String name = mVb.seriesTitle.getText().toString().trim();
        if (name.isEmpty()) {
            mVb.lblSeries.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        //noinspection ConstantConditions
        final Locale bookLocale = mBookViewModel.getBook().getLocale(getContext());

        final Series newSeries = new Series(name);
        //noinspection ConstantConditions
        newSeries.setNumber(mVb.seriesNum.getText().toString().trim());

        // see if it already exists
        newSeries.fixId(getContext(), mDb, bookLocale);
        // and check it's not already in the list.
        if (mList.contains(newSeries)) {
            mVb.lblSeries.setError(getString(R.string.warning_series_already_in_list));
        } else {
            mList.add(newSeries);
            // clear the form for next entry and scroll to the new item
            mVb.seriesTitle.setText("");
            mVb.seriesNum.setText("");
            mVb.seriesTitle.requestFocus();
            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.seriesList.scrollToPosition(mListAdapter.getItemCount() - 1);
        }
    }

    /**
     * Process the modified (if any) data.
     *
     * @param series  the user was editing (with the original data)
     * @param tmpData the modifications the user made in a placeholder object.
     *                Non-modified data was copied here as well.
     *                The id==0 will not be used/updated.
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
                Series.pruneList(mList, getContext(), mDb, bookLocale, false);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Series was modified.
        // Check if it's used by any other books.
        if (mBookViewModel.isSingleUsage(getContext(), series, bookLocale)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            series.copyFrom(tmpData, true);
            //noinspection ConstantConditions
            Series.pruneList(mList, getContext(), mDb, bookLocale, false);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the object was modified and it's used in more than one place.
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
                    if (mDb.updateSeries(getContext(), series, bookLocale)) {
                        Series.pruneList(mList, getContext(), mDb, bookLocale, false);
                        mBookViewModel.refreshSeriesList(getContext());
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        Logger.warnWithStackTrace(getContext(), TAG, "Could not update",
                                                  "series=" + series,
                                                  "tmpSeries=" + tmpData);
                        StandardDialogs.showError(getContext(), R.string.error_unexpected_error);
                    }
                })
                .setPositiveButton(R.string.btn_this_book, (d, w) -> {
                    // treat the new data as a new Series; save it so we have a valid id.
                    // Note that if the user abandons the entire book edit,
                    // we will orphan this new Series. That's ok, it will get
                    // garbage collected from the database sooner or later.
                    mDb.updateOrInsertSeries(getContext(),
                                             bookLocale, tmpData);
                    // unlink the old one (and unmodified), and link with the new one
                    // book/series positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(series);
                    mList.add(tmpData);
                    Series.pruneList(mList, getContext(), mDb, bookLocale, false);
                    mListAdapter.notifyDataSetChanged();
                })
                .create()
                .show();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
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
     * Edit a single Series from the book's series list.
     * It could exist (i.e. have an ID) or could be a previously added/new one (ID==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditBookSeriesDialogFragment
            extends BaseDialogFragment {

        /** Fragment/Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        static final String TAG = "EditBookSeriesDialogFragment";

        /** Database Access. */
        private DAO mDb;
        @Nullable
        private String mBookTitle;
        /** View Binding. */
        private DialogEditBookSeriesBinding mVb;

        /** The Series we're editing. */
        private Series mSeries;

        /** Current edit. */
        private String mSeriesTitle;
        /** Current edit. */
        private boolean mSeriesIsComplete;
        /** Current edit. */
        private String mSeriesNumber;

        public EditBookSeriesDialogFragment() {
            super(R.layout.dialog_edit_book_series);
        }

        /**
         * Constructor.
         *
         * @param bookTitle displayed for info only
         * @param series    to edit
         *
         * @return instance
         */
        static DialogFragment newInstance(@NonNull final String bookTitle,
                                          @NonNull final Series series) {
            final DialogFragment frag = new EditBookSeriesDialogFragment();
            final Bundle args = new Bundle(2);
            args.putString(DBDefinitions.KEY_TITLE, bookTitle);
            args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mDb = new DAO(TAG);

            final Bundle args = requireArguments();
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

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
        public void onViewCreated(@NonNull final View view,
                                  @Nullable final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mVb = DialogEditBookSeriesBinding.bind(view);

            Objects.requireNonNull(getTargetFragment(), ErrorMsg.NO_TARGET_FRAGMENT_SET);

            mVb.toolbar.setSubtitle(mBookTitle);
            mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
            mVb.toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.MENU_SAVE) {
                    if (saveChanges()) {
                        dismiss();
                    }
                    return true;
                }
                return false;
            });

            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> seriesNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item, mDb.getSeriesTitles());

            mVb.seriesTitle.setText(mSeriesTitle);
            mVb.seriesTitle.setAdapter(seriesNameAdapter);

            mVb.cbxIsComplete.setChecked(mSeriesIsComplete);

            mVb.seriesNum.setText(mSeriesNumber);
        }

        private boolean saveChanges() {
            viewToModel();

            // basic check only, we're doing more extensive checks later on.
            if (mSeriesTitle.isEmpty()) {
                showError(mVb.lblSeriesTitle, R.string.vldt_non_blank_required);
                return false;
            }

            // Create a new Series as a holder for all changes.
            final Series tmpSeries = new Series(mSeriesTitle);
            tmpSeries.setComplete(mSeriesIsComplete);
            tmpSeries.setNumber(mSeriesNumber);

            //noinspection ConstantConditions
            ((EditBookSeriesListDialogFragment) getTargetFragment())
                    .processChanges(mSeries, tmpSeries);
            return true;
        }

        private void viewToModel() {
            mSeriesTitle = mVb.seriesTitle.getText().toString().trim();
            //noinspection ConstantConditions
            mSeriesNumber = mVb.seriesNum.getText().toString().trim();
            mSeriesIsComplete = mVb.cbxIsComplete.isChecked();
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
            viewToModel();
            super.onPause();
        }

        @Override
        public void onDestroy() {
            if (mDb != null) {
                mDb.close();
            }
            super.onDestroy();
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
                final DialogFragment frag = EditBookSeriesDialogFragment
                        .newInstance(mBookViewModel.getBook().getTitle(), series);
                frag.setTargetFragment(EditBookSeriesListDialogFragment.this, 0);
                frag.show(getParentFragmentManager(), EditBookSeriesDialogFragment.TAG);
            });
        }
    }
}
