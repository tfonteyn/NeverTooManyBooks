/*
 * @Copyright 2020 HardBackNutter
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

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

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

    /** Database Access. */
    private DAO mDb;
    /** View binding. */
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
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param series     to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey,
                                             @NonNull final Series series) {
        final DialogFragment frag = new EditSeriesDialogFragment();
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mRequestKey = args.getString(BKEY_REQUEST_KEY);
        mSeries = args.getParcelable(DBDefinitions.KEY_FK_SERIES);
        Objects.requireNonNull(mSeries, ErrorMsg.NULL_SERIES);

        if (savedInstanceState == null) {
            mTitle = mSeries.getTitle();
            mIsComplete = mSeries.isComplete();
        } else {
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
        final DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, mDb.getSeriesTitles());

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

        // There is no book involved here, so use the users Locale instead
        //noinspection ConstantConditions
        final Locale bookLocale = AppLocale.getInstance().getUserLocale(getContext());

        // store changes
        mSeries.setTitle(mTitle);
        mSeries.setComplete(mIsComplete);

        final boolean success;
        if (mSeries.getId() == 0) {
            success = mDb.insert(getContext(), mSeries, bookLocale) > 0;
        } else {
            success = mDb.update(getContext(), mSeries, bookLocale);
        }
        if (success) {
            BookChangedListener.sendResult(this, mRequestKey, BookChangedListener.SERIES);
            return true;
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

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
