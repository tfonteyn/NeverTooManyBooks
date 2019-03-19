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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditSeriesDialogFragment;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Activity to edit a list of series provided in an ArrayList<Series> and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditSeriesDialogFragment} for list
 *
 * @author Philip Warner
 */
public class EditSeriesListActivity
        extends EditObjectListActivity<Series> {

    /** Main screen Series name field. */
    private AutoCompleteTextView mSeriesNameView;
    /** Main screen Series Number field. */
    private TextView mSeriesNumberView;
    /** AutoCompleteTextView for mSeriesNameView and the EditView in the dialog box. */
    private ArrayAdapter<String> mSeriesAdapter;

    /** flag indicating global changes were made. Used in setResult. */
    private boolean mGlobalChangeMade;

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
        setTitle(mBookTitle);

        mSeriesAdapter = new ArrayAdapter<>(this,
                                            android.R.layout.simple_dropdown_item_1line,
                                            mDb.getAllSeriesNames());

        mSeriesNameView = findViewById(R.id.series);
        mSeriesNameView.setAdapter(mSeriesAdapter);
        mSeriesNumberView = findViewById(R.id.series_num);
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
            UserMessage.showUserMessage(this, R.string.warning_required_name);
            return;
        }

        Series newSeries = new Series(title);
        newSeries.setNumber(mSeriesNumberView.getText().toString().trim());

        // see if it already exists
        newSeries.fixupId(mDb);
        // and check it's not already in the list.
        for (Series series : mList) {
            if (series.equals(newSeries)) {
                UserMessage.showUserMessage(this, R.string.warning_series_already_in_list);
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

    @Override
    protected ArrayAdapter<Series> createListAdapter(@NonNull final ArrayList<Series> list) {
        return new SeriesListAdapter(this, list);
    }

    /**
     * Called from the editor dialog fragment after the user was done.
     */
    void processChanges(@NonNull final Series series,
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
                Utils.pruneList(mDb, mList);
                onListChanged();
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
            Utils.pruneList(mDb, mList);
            onListChanged();
            return;
        }

        // At this point, we know the names are genuinely different and the old series is used
        // in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_apply_series_changed,
                                      series.getSortName(), newSeries.getSortName(),
                                      allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         getString(R.string.btn_this_book),
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
     * Edit a Series from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     */
    public static class EditBookSeriesDialogFragment
            extends DialogFragment {

        public static final String TAG = EditBookSeriesDialogFragment.class.getSimpleName();

        private EditSeriesListActivity mActivity;

        private AutoCompleteTextView mNameView;
        private Checkable mIsCompleteView;
        private EditText mNumberView;

        private String mName;
        private boolean mIsComplete;
        private String mNumber;

        /**
         * Constructor.
         *
         * @param series to edit
         *
         * @return the instance
         */
        public static EditBookSeriesDialogFragment newInstance(@NonNull final Series series) {
            EditBookSeriesDialogFragment frag = new EditBookSeriesDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(UniqueId.KEY_SERIES, series);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            mActivity = (EditSeriesListActivity) requireActivity();

            final Series series = requireArguments().getParcelable(UniqueId.KEY_SERIES);
            if (savedInstanceState == null) {
                //noinspection ConstantConditions
                mName = series.getName();
                mIsComplete = series.isComplete();
                mNumber = series.getNumber();
            } else {
                mName = savedInstanceState.getString(UniqueId.KEY_SERIES);
                mIsComplete = savedInstanceState.getBoolean(UniqueId.KEY_SERIES_IS_COMPLETE);
                mNumber = savedInstanceState.getString(UniqueId.KEY_SERIES_NUM);
            }

            final View root = mActivity.getLayoutInflater()
                                       .inflate(R.layout.dialog_edit_book_series, null);

            // the dialog fields != screen fields.
            mNameView = root.findViewById(R.id.series);
            mNameView.setText(mName);
            mNameView.setAdapter(mActivity.mSeriesAdapter);

            mIsCompleteView = root.findViewById(R.id.is_complete);
            if (mIsCompleteView != null) {
                mIsCompleteView.setChecked(mIsComplete);
            }

            mNumberView = root.findViewById(R.id.series_num);
            mNumberView.setText(mNumber);

            root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    mName = mNameView.getText().toString().trim();
                    if (mName.isEmpty()) {
                        UserMessage.showUserMessage(mActivity,
                                                    R.string.warning_required_name);
                        return;
                    }
                    if (mIsCompleteView != null) {
                        mIsComplete = mIsCompleteView.isChecked();
                    }
                    mNumber = mNumberView.getText().toString().trim();
                    dismiss();

                    //noinspection ConstantConditions
                    mActivity.processChanges(series, mName, mIsComplete, mNumber);

                }
            });

            root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    dismiss();
                }
            });

            return new AlertDialog.Builder(mActivity)
                    .setView(root)
                    .setTitle(R.string.title_edit_book_series)
                    .create();
        }

        @Override
        public void onPause() {
            mName = mNameView.getText().toString().trim();
            if (mIsCompleteView != null) {
                mIsComplete = mIsCompleteView.isChecked();
            }
            mNumber = mNumberView.getText().toString().trim();

            super.onPause();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(UniqueId.KEY_SERIES, mName);
            outState.putBoolean(UniqueId.KEY_SERIES_IS_COMPLETE, mIsComplete);
            outState.putString(UniqueId.KEY_SERIES_NUM, mNumber);
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        @NonNull
        final TextView rowSeriesView;
        @NonNull
        final TextView rowSeriesSortView;

        Holder(@NonNull final View rowView) {
            rowSeriesView = rowView.findViewById(R.id.row_series);
            rowSeriesSortView = rowView.findViewById(R.id.row_series_sort);

            rowView.setTag(this);
        }
    }

    protected class SeriesListAdapter
            extends SimpleListAdapter<Series> {

        SeriesListAdapter(@NonNull final Context context,
                          @NonNull final ArrayList<Series> items) {
            super(context, R.layout.row_edit_series_list, items);
        }

        @Override
        public void onGetView(@NonNull final View convertView,
                              @NonNull final Series item) {
            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder(convertView);
            }
            // Setup the variant fields in the holder
            holder.rowSeriesView.setText(item.getDisplayName());

            if (item.getDisplayName().equals(item.getSortName())) {
                holder.rowSeriesSortView.setVisibility(View.GONE);
            } else {
                holder.rowSeriesSortView.setVisibility(View.VISIBLE);
                holder.rowSeriesSortView.setText(item.getSortName());
            }

        }

        /**
         * edit the item we clicked on.
         */
        @Override
        public void onRowClick(@NonNull final View target,
                               @NonNull final Series item,
                               final int position) {
            FragmentManager fm = getSupportFragmentManager();
            if (fm.findFragmentByTag(EditBookSeriesDialogFragment.TAG) == null) {
                EditBookSeriesDialogFragment.newInstance(item)
                                            .show(fm, EditBookSeriesDialogFragment.TAG);
            }
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
