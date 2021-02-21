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
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING</strong> {@link Series}.
 */
public class EditSeriesDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditSeriesDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** View Binding. */
    private DialogEditSeriesBinding mVb;

    /** The Series we're editing. */
    private Series mSeries;

    /** Current edit. */
    private String mTitle;
    /** Current edit. */
    private boolean mIsComplete;

    /**
     * No-arg constructor for OS use.
     */
    public EditSeriesDialogFragment() {
        super(R.layout.dialog_edit_series);
    }

    /**
     * Launch the dialog.
     *
     * @param series to edit.
     */
    public static void launch(@NonNull final FragmentActivity activity,
                              @NonNull final Series series) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, BooksOnBookshelf.RowChangeListener.REQUEST_KEY);
        args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);

        final DialogFragment frag = new EditSeriesDialogFragment();
        frag.setArguments(args);
        frag.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mSeries = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_SERIES),
                                         "KEY_FK_SERIES");

        if (savedInstanceState == null) {
            mTitle = mSeries.getTitle();
            mIsComplete = mSeries.isComplete();
        } else {
            //noinspection ConstantConditions
            mTitle = savedInstanceState.getString(DBDefinitions.KEY_FK_SERIES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                                        false);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditSeriesBinding.bind(view);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> adapter = new ExtArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                ExtArrayAdapter.FilterType.Diacritic,
                SeriesDao.getInstance().getNames());

        mVb.seriesTitle.setText(mTitle);
        mVb.seriesTitle.setAdapter(adapter);
        mVb.cbxIsComplete.setChecked(mIsComplete);
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
        if (mTitle.isEmpty()) {
            showError(mVb.lblSeriesTitle, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mSeries.getTitle().equals(mTitle)
            && mSeries.isComplete() == mIsComplete) {
            return true;
        }

        // store changes
        mSeries.setTitle(mTitle);
        mSeries.setComplete(mIsComplete);

        final Context context = getContext();

        final SeriesDao seriesDao = SeriesDao.getInstance();

        // There is no book involved here, so use the users Locale instead
        //noinspection ConstantConditions
        final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

        // check if it already exists (will be 0 if not)
        final long existingId = seriesDao.find(context, mSeries, true, bookLocale);

        if (existingId == 0) {
            final boolean success;
            if (mSeries.getId() == 0) {
                success = seriesDao.insert(context, mSeries, bookLocale) > 0;
            } else {
                success = seriesDao.update(context, mSeries, bookLocale);
            }
            if (success) {
                BooksOnBookshelf.RowChangeListener
                        .setResult(this, mRequestKey,
                                   BooksOnBookshelf.RowChangeListener.SERIES, mSeries.getId());
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
                            BooksOnBookshelf.RowChangeListener.setResult(
                                    this, mRequestKey,
                                    // return the series who 'lost' it's books
                                    BooksOnBookshelf.RowChangeListener.SERIES, mSeries.getId());
                        } catch (@NonNull final DaoWriteException e) {
                            Logger.error(context, TAG, e);
                            StandardDialogs.showError(context, R.string.error_storage_not_writable);
                        }
                    })
                    .create()
                    .show();
        }
        return false;
    }

    private void viewToModel() {
        mTitle = mVb.seriesTitle.getText().toString().trim();
        mIsComplete = mVb.cbxIsComplete.isChecked();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_SERIES_TITLE, mTitle);
        outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mIsComplete);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
