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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING</strong> {@link Series}.
 */
public class EditSeriesDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditSeriesDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** View Binding. */
    private DialogEditSeriesBinding mVb;

    /** The Series we're editing. */
    private Series mSeries;

    /** Current edit. */
    private Series mCurrentEdit;

    /**
     * No-arg constructor for OS use.
     */
    public EditSeriesDialogFragment() {
        super(R.layout.dialog_edit_series);
    }

    /**
     * Launch the dialog.
     *
     * @param fm     The FragmentManager this fragment will be added to.
     * @param series to edit.
     */
    public static void launch(@NonNull final FragmentManager fm,
                              @NonNull final Series series) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, RowChangedListener.REQUEST_KEY);
        args.putParcelable(DBKey.FK_SERIES, series);

        final DialogFragment frag = new EditSeriesDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        mSeries = Objects.requireNonNull(args.getParcelable(DBKey.FK_SERIES), DBKey.FK_SERIES);

        if (savedInstanceState == null) {
            mCurrentEdit = new Series(mSeries.getTitle(), mSeries.isComplete());
        } else {
            //noinspection ConstantConditions
            mCurrentEdit = savedInstanceState.getParcelable(DBKey.FK_SERIES);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditSeriesBinding.bind(view);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getSeriesDao().getNames());

        mVb.seriesTitle.setText(mCurrentEdit.getTitle());
        mVb.seriesTitle.setAdapter(titleAdapter);
        mVb.cbxIsComplete.setChecked(mCurrentEdit.isComplete());

        // don't requestFocus() as we have multiple fields.
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
        if (mCurrentEdit.getTitle().isEmpty()) {
            showError(mVb.lblSeriesTitle, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mSeries.getTitle().equals(mCurrentEdit.getTitle())
            && mSeries.isComplete() == mCurrentEdit.isComplete()) {
            return true;
        }

        // store changes
        mSeries.copyFrom(mCurrentEdit, false);

        final Context context = getContext();
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final SeriesDao seriesDao = serviceLocator.getSeriesDao();

        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        // check if it already exists (will be 0 if not)
        //noinspection ConstantConditions
        final long existingId = seriesDao.find(context, mSeries, true, bookLocale);

        if (existingId == 0) {
            final boolean success;
            if (mSeries.getId() == 0) {
                success = seriesDao.insert(context, mSeries, bookLocale) > 0;
            } else {
                success = seriesDao.update(context, mSeries, bookLocale);
            }
            if (success) {
                RowChangedListener.setResult(this, mRequestKey,
                                             DBKey.FK_SERIES, mSeries.getId());
                return true;
            }
        } else {
            // Merge the 2
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(mSeries.getLabel(context))
                    .setMessage(R.string.confirm_merge_series)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        dismiss();
                        // move all books from the one being edited to the existing one
                        try {
                            seriesDao.merge(context, mSeries, existingId);
                            RowChangedListener.setResult(
                                    this, mRequestKey,
                                    // return the series who 'lost' it's books
                                    DBKey.FK_SERIES, mSeries.getId());
                        } catch (@NonNull final DaoWriteException e) {
                            Logger.error(TAG, e);
                            StandardDialogs.showError(context, R.string.error_storage_not_writable);
                        }
                    })
                    .create()
                    .show();
        }
        return false;
    }

    private void viewToModel() {
        mCurrentEdit.setTitle(mVb.seriesTitle.getText().toString().trim());
        mCurrentEdit.setComplete(mVb.cbxIsComplete.isChecked());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_SERIES, mCurrentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
