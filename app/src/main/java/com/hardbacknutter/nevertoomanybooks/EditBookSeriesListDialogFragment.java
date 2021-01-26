/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesListBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Series of a Book.
 */
public class EditBookSeriesListDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookSeriesListDlg";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_SERIES = TAG + ":rk:" + EditSeriesForBookDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookFragmentViewModel mVm;
    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mVm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /** View Binding. */
    private DialogEditBookSeriesListBinding mVb;
    /** the rows. */
    private ArrayList<Series> mList;
    /** The adapter for the list itself. */
    private SeriesListAdapter mListAdapter;

    private final EditBookBaseFragment.EditItemLauncher<Series> mOnEditSeriesLauncher =
            new EditBookBaseFragment.EditItemLauncher<Series>() {
                @Override
                public void onResult(@NonNull final Series original,
                                     @NonNull final Series modified) {
                    processChanges(original, modified);
                }
            };

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookSeriesListDialogFragment() {
        super(R.layout.dialog_edit_book_series_list);
        setForceFullscreen();
    }

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookSeriesListDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOnEditSeriesLauncher.register(getChildFragmentManager(), this, RK_EDIT_SERIES);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookSeriesListBinding.bind(view);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

        mVb.toolbar.setSubtitle(mVm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getAllSeriesTitles());
        mVb.seriesTitle.setAdapter(nameAdapter);
        mVb.seriesTitle.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mVb.seriesTitle.setError(null);
            }
        });

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
        mVb.seriesList.setHasFixedSize(true);

        mList = mVm.getBook().getParcelableArrayList(Book.BKEY_SERIES_LIST);
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
    protected void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            onAdd();
            return true;
        }
        return false;
    }

    /**
     * Create a new entry.
     */
    private void onAdd() {
        // clear any previous error
        mVb.lblSeries.setError(null);

        final String title = mVb.seriesTitle.getText().toString().trim();
        if (title.isEmpty()) {
            mVb.lblSeries.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Series newSeries = new Series(title);
        //noinspection ConstantConditions
        newSeries.setNumber(mVb.seriesNum.getText().toString().trim());

        // see if it already exists
        //noinspection ConstantConditions
        mVm.fixId(getContext(), newSeries);
        // and check it's not already in the list.
        if (mList.contains(newSeries)) {
            mVb.lblSeries.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            mList.add(newSeries);
            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.seriesList.scrollToPosition(mListAdapter.getItemCount() - 1);

            // clear the form for next entry
            mVb.seriesTitle.setText("");
            mVb.seriesNum.setText("");
            mVb.seriesTitle.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!mVb.seriesTitle.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                mVb.seriesTitle.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        mVm.updateSeries(mList);
        return true;
    }

    /**
     * Process the modified (if any) data.
     *
     * @param original the original data the user was editing
     * @param modified the modifications the user made in a placeholder object.
     *                 Non-modified data was copied here as well.
     */
    private void processChanges(@NonNull final Series original,
                                @NonNull final Series modified) {

        // name not changed ?
        if (original.getTitle().equals(modified.getTitle())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            original.setComplete(modified.isComplete());

            // Number is not part of the Series table, but of the book_series table.
            if (!original.getNumber().equals(modified.getNumber())) {
                // so if the number is different, just update it
                original.setNumber(modified.getNumber());
                //noinspection ConstantConditions
                mVm.pruneSeries(getContext());
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name was modified. Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mVm.isSingleUsage(getContext(), original)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            original.copyFrom(modified, true);
            //noinspection ConstantConditions
            mVm.pruneSeries(getContext());
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the object was modified and it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        StandardDialogs.confirmScopeForChange(
                getContext(), original, modified,
                () -> changeForAllBooks(original, modified),
                () -> changeForThisBook(original, modified));
    }

    private void changeForAllBooks(@NonNull final Series original,
                                   @NonNull final Series modified) {
        // copy all new data
        original.copyFrom(modified, true);
        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (mVm.changeForAllBooks(getContext(), original)) {
            mListAdapter.notifyDataSetChanged();

        } else {
            Logger.error(getContext(), TAG, new Throwable(), "Could not update",
                         "original=" + original, "modified=" + modified);
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    private void changeForThisBook(@NonNull final Series original,
                                   @NonNull final Series modified) {
        // treat the new data as a new Series; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Series. That's ok, it will get
        // garbage collected from the database sooner or later.
        //noinspection ConstantConditions
        mVm.changeForThisBook(getContext(), original, modified);
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

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
    public static class EditSeriesForBookDialogFragment
            extends BaseDialogFragment {

        /** Fragment/Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "EditSeriesForBookDialog";
        private static final String BKEY_REQUEST_KEY = TAG + ":rk";

        /** FragmentResultListener request key to use for our response. */
        private String mRequestKey;

        @SuppressWarnings("FieldCanBeLocal")
        private EditBookFragmentViewModel mVm;

        /** Displayed for info only. */
        @Nullable
        private String mBookTitle;
        /** View Binding. */
        private DialogEditBookSeriesBinding mVb;

        /** The Series we're editing. */
        private Series mSeries;

        /** Current edit. */
        private String mTitle;
        /** Current edit. */
        private boolean mIsComplete;
        /** Current edit. */
        private String mNumber;

        /**
         * No-arg constructor for OS use.
         */
        public EditSeriesForBookDialogFragment() {
            super(R.layout.dialog_edit_book_series);
        }

        /**
         * Launch the dialog.
         *
         * @param fm         The FragmentManager this fragment will be added to.
         * @param requestKey for use with the FragmentResultListener
         * @param bookTitle  displayed for info only
         * @param series     to edit
         */
        static void launch(@NonNull final FragmentManager fm,
                           @SuppressWarnings("SameParameterValue")
                           @NonNull final String requestKey,
                           @NonNull final String bookTitle,
                           @NonNull final Series series) {
            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(DBDefinitions.KEY_TITLE, bookTitle);
            args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);

            final DialogFragment frag = new EditSeriesForBookDialogFragment();
            frag.setArguments(args);
            frag.show(fm, EditSeriesForBookDialogFragment.TAG);
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                 "BKEY_REQUEST_KEY");
            mSeries = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_SERIES),
                                             "KEY_FK_SERIES");

            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

            if (savedInstanceState == null) {
                mTitle = mSeries.getTitle();
                mIsComplete = mSeries.isComplete();
                mNumber = mSeries.getNumber();
            } else {
                //noinspection ConstantConditions
                mTitle = savedInstanceState.getString(DBDefinitions.KEY_FK_SERIES);
                mIsComplete = savedInstanceState
                        .getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, false);
                //noinspection ConstantConditions
                mNumber = savedInstanceState.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            }
        }

        @Override
        public void onViewCreated(@NonNull final View view,
                                  @Nullable final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mVb = DialogEditBookSeriesBinding.bind(view);

            mVb.toolbar.setSubtitle(mBookTitle);

            //noinspection ConstantConditions
            mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

            //noinspection ConstantConditions
            final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    ExtArrayAdapter.FilterType.Diacritic, mVm.getAllSeriesTitles());
            mVb.seriesTitle.setText(mTitle);
            mVb.seriesTitle.setAdapter(nameAdapter);

            mVb.cbxIsComplete.setChecked(mIsComplete);
            mVb.seriesNum.setText(mNumber);
        }

        @Override
        protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
            if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        }

        private boolean saveChanges() {
            viewToModel();

            // basic check only, we're doing more extensive checks later on.
            if (mTitle.isEmpty()) {
                showError(mVb.lblSeriesTitle, R.string.vldt_non_blank_required);
                return false;
            }

            // Create a new Series as a holder for all changes.
            final Series tmpSeries = new Series(mTitle);
            tmpSeries.setComplete(mIsComplete);
            tmpSeries.setNumber(mNumber);

            EditBookBaseFragment.EditItemLauncher
                    .sendResult(this, mRequestKey, mSeries, tmpSeries);
            return true;
        }

        private void viewToModel() {
            mTitle = mVb.seriesTitle.getText().toString().trim();
            //noinspection ConstantConditions
            mNumber = mVb.seriesNum.getText().toString().trim();
            mIsComplete = mVb.cbxIsComplete.isChecked();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_FK_SERIES, mTitle);
            outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mIsComplete);
            outState.putString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES, mNumber);
        }

        @Override
        public void onPause() {
            viewToModel();
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
            final Holder holder = new Holder(view);
            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> EditSeriesForBookDialogFragment
                    .launch(getChildFragmentManager(), RK_EDIT_SERIES,
                            mVm.getBook().getTitle(),
                            getItem(holder.getBindingAdapterPosition())));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Series series = getItem(position);
            holder.seriesView.setText(series.getLabel(getContext()));
        }
    }
}
