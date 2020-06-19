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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BookChangedListenerOwner;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to edit an existing single Series.
 * <p>
 * Calling point is a List
 */
public class EditSeriesDialogFragment
        extends BaseDialogFragment
        implements BookChangedListenerOwner {

    /** Fragment/Log tag. */
    public static final String TAG = "EditSeriesDialogFrag";

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookChangedListener> mListener;
    /** View binding. */
    private DialogEditSeriesBinding mVb;

    /** The Series we're editing. */
    private Series mSeries;

    /** Current edit. */
    private String mTitle;
    /** Current edit. */
    private boolean mIsComplete;

    public EditSeriesDialogFragment() {
        super(R.layout.dialog_edit_series);
    }

    /**
     * Constructor.
     *
     * @param series to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Series series) {
        final DialogFragment frag = new EditSeriesDialogFragment();
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
        final DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, mDb.getSeriesTitles());

        mVb.seriesTitle.setText(mTitle);
        mVb.seriesTitle.setAdapter(adapter);

        mVb.cbxIsComplete.setChecked(mIsComplete);
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

        // this is a global update, so just set and update.
        mSeries.setTitle(mTitle);
        mSeries.setComplete(mIsComplete);
        // There is no book involved here, so use the users Locale instead
        //noinspection ConstantConditions
        final Locale bookLocale = LocaleUtils.getUserLocale(getContext());
        mDb.updateOrInsertSeries(getContext(), mSeries, bookLocale);

        if (mListener != null && mListener.get() != null) {
            mListener.get().onChange(0, BookChangedListener.SERIES, null);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onBookChanged|"
                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
        return true;
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

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    @Override
    public void setListener(@NonNull final BookChangedListener listener) {
        mListener = new WeakReference<>(listener);
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
