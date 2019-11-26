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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.EditBookSeriesActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

/**
 * Dialog to edit an existing single Series.
 * <p>
 * Calling point is a List; see {@link EditBookSeriesActivity} for book
 */
public class EditSeriesDialogFragment
        extends DialogFragment {

    public static final String TAG = "EditSeriesDialogFrag";

    /** Database Access. */
    private DAO mDb;

    private WeakReference<BookChangedListener> mBookChangedListener;

    private AutoCompleteTextView mNameView;
    private Checkable mIsCompleteView;

    /** The Series we're editing. */
    private Series mSeries;
    /** Current edit. */
    private String mName;
    /** Current edit. */
    private boolean mIsComplete;

    /**
     * Constructor.
     *
     * @param series to edit.
     *
     * @return the instance
     */
    public static EditSeriesDialogFragment newInstance(@NonNull final Series series) {
        EditSeriesDialogFragment frag = new EditSeriesDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_FK_SERIES, series);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        mSeries = requireArguments().getParcelable(DBDefinitions.KEY_FK_SERIES);
        Objects.requireNonNull(mSeries, "Series must be passed in args");

        if (savedInstanceState == null) {
            mName = mSeries.getTitle();
            mIsComplete = mSeries.isComplete();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_FK_SERIES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_edit_series, null);

        Context context = getContext();

        @SuppressWarnings("ConstantConditions")
        ArrayAdapter<String> mAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                   mDb.getSeriesTitles());

        // the dialog fields != screen fields.
        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);
        mNameView.setAdapter(mAdapter);

        mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
        mIsCompleteView.setChecked(mIsComplete);

        return new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(R.string.title_edit_series)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> {
                    mName = mNameView.getText().toString().trim();
                    if (mName.isEmpty()) {
                        Snackbar.make(mNameView, R.string.warning_missing_name,
                                      Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    mIsComplete = mIsCompleteView.isChecked();

                    // anything actually changed ?
                    if (mSeries.getTitle().equals(mName)
                        && mSeries.isComplete() == mIsComplete) {
                        return;
                    }

                    // this is a global update, so just set and update.
                    mSeries.setTitle(mName);
                    mSeries.setComplete(mIsComplete);
                    // There is no book involved here, so use the users Locale instead
                    // and store the changes
                    mDb.updateOrInsertSeries(context, Locale.getDefault(), mSeries);

                    // and spread the news of the changes.
//                    Bundle data = new Bundle();
//                    data.putLong(DBDefinitions.KEY_SERIES_TITLE, mSeries.getId());
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener.get()
                                            .onBookChanged(0, BookChangedListener.SERIES, null);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Log.d(TAG, "onBookChanged|" + Logger.WEAK_REFERENCE_DEAD);
                        }
                    }
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_SERIES_TITLE, mName);
        outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mIsComplete);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mBookChangedListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        mName = mNameView.getText().toString().trim();
        mIsComplete = mIsCompleteView.isChecked();
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
