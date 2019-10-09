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
import android.view.Menu;
import android.view.MenuItem;
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
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.EditObjectListActivity;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
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
            UserMessage.show(mAutoCompleteTextView, R.string.warning_missing_name);
            return;
        }

        Series newSeries = new Series(name);
        newSeries.setNumber(mSeriesNumberView.getText().toString().trim());
        // see if it already exists
        newSeries.fixId(this, mModel.getDb(), mModel.getBookLocale());
        // and check it's not already in the list.
        if (mList.contains(newSeries)) {
            UserMessage.show(mAutoCompleteTextView, R.string.warning_series_already_in_list);
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newSeries);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAutoCompleteTextView.setText("");
        mSeriesNumberView.setText("");
    }

    @Override
    protected void processChanges(@NonNull final Series series,
                                  @NonNull final Series newSeriesData) {

        final Locale bookLocale = mModel.getBookLocale();

        // anything other than the number changed ?
        if (series.getTitle().equals(newSeriesData.getTitle())
            && series.isComplete() == newSeriesData.isComplete()) {

            // Number is not part of the Series table, but of the book_series table.
            if (!series.getNumber().equals(newSeriesData.getNumber())) {
                // so if the number is different, just update it
                series.setNumber(newSeriesData.getNumber());
                Series.pruneList(mList, this, mModel.getDb(), bookLocale);
                mListAdapter.notifyDataSetChanged();
            }
            // nothing or only the number was different, so we're done here.
            return;
        }

        //See if the old one is used by any other books.
        long nrOfReferences = mModel.getDb().countBooksInSeries(this, series, bookLocale);

        // if it's not, we simply re-use the old object.
        if (mModel.isSingleUsage(nrOfReferences)) {
            /*
             * Use the original Series object, but update its fields
             *
             * see below and {@link DAO#insertBookDependents} where an *insert* will be done
             * The 'old' Series will be orphaned. TODO: simplify / don't orphan?
             */
            updateItem(series, newSeriesData, bookLocale);
            return;
        }

        // At this point, we know the names are genuinely different and the old Series is used
        // in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);
        String message = getString(R.string.confirm_apply_series_changed,
                                   series.getSorting(),
                                   newSeriesData.getSorting(),
                                   allBooks);
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, which) -> {
                    mModel.setGlobalReplacementsMade(
                            mModel.getDb().globalReplace(this, bookLocale, series, newSeriesData));
                    updateItem(series, newSeriesData, bookLocale);

                })
                .setPositiveButton(R.string.btn_this_book, (d, which) ->
                        updateItem(series, newSeriesData, bookLocale))
                .create()
                .show();
    }

    @Override
    protected void updateItem(@NonNull final Series series,
                              @NonNull final Series newSeriesData,
                              @NonNull final Locale fallbackLocale) {
        series.copyFrom(newSeriesData, true);
        Series.pruneList(mList, this, mModel.getDb(), fallbackLocale);
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD,
                 MenuHandler.ORDER_HIDE_KEYBOARD, R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_HIDE_KEYBOARD:
                App.hideKeyboard(getWindow().getDecorView());
                return true;

            default:
                return super.onOptionsItemSelected(item);
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

        /** Fragment manager tag. */
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
            Bundle args = new Bundle();
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
            Objects.requireNonNull(mSeries, "no Series passed");
            if (savedInstanceState == null) {
                mSeriesName = mSeries.getTitle();
                mSeriesIsComplete = mSeries.isComplete();
                mSeriesNumber = mSeries.getNumber();
            } else {
                mSeriesName = savedInstanceState.getString(DBDefinitions.KEY_FK_SERIES);
                mSeriesIsComplete = savedInstanceState
                        .getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                mSeriesNumber = savedInstanceState.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            }
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View root = layoutInflater.inflate(R.layout.dialog_edit_series_book, null);

            // the dialog fields != screen fields.
            mTitleView = root.findViewById(R.id.series);
            mTitleView.setText(mSeriesName);
            // we re-use the activity adapter.
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
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                        mSeriesName = mTitleView.getText().toString().trim();
                        if (mSeriesName.isEmpty()) {
                            UserMessage.show(mTitleView, R.string.warning_missing_name);
                            return;
                        }
                        if (mIsCompleteView != null) {
                            mSeriesIsComplete = mIsCompleteView.isChecked();
                        }
                        mSeriesNumber = mNumberView.getText().toString().trim();

                        // Create a new Series as a holder for the changes.
                        Series newSeriesData = new Series(mSeriesName, mSeriesIsComplete);
                        newSeriesData.setNumber(mSeriesNumber);
                        mHostActivity.processChanges(mSeries, newSeriesData);
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
            mSeriesNumber = mNumberView.getText().toString().trim();

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
                          @NonNull final ArrayList<Series> items,
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
            String seriesLabel = series.getLabel(context);
            holder.seriesView.setText(seriesLabel);

            if (!seriesLabel.equals(series.getSorting())) {
                holder.seriesSortView.setVisibility(View.VISIBLE);
                holder.seriesSortView.setText(series.getSorting());
            } else {
                holder.seriesSortView.setVisibility(View.GONE);
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
