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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.EditSeriesListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Dialog to edit an existing single series.
 * <p>
 * Calling point is a List; see {@link EditSeriesListActivity} for book
 */
public class EditSeriesDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    private static final String TAG = EditAuthorDialogFragment.class.getSimpleName();

    private DBA mDb;

    private AutoCompleteTextView mNameView;
    private Checkable mIsCompleteView;

    private String mName;
    private boolean mIsComplete;

    /**
     * (syntax sugar for newInstance)
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final Series series) {
        if (fm.findFragmentByTag(TAG) == null) {
            newInstance(series).show(fm, TAG);
        }
    }

    /**
     * Constructor.
     */
    public static EditSeriesDialogFragment newInstance(@NonNull final Series series) {
        EditSeriesDialogFragment frag = new EditSeriesDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_SERIES, series);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        Context context = getContext();
        //noinspection ConstantConditions
        mDb = new DBA(context);

        Bundle args = requireArguments();

        final Series series = args.getParcelable(DBDefinitions.KEY_SERIES);
        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            mName = series.getName();
            mIsComplete = series.isComplete();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_SERIES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
        }

        View root = getLayoutInflater().inflate(R.layout.dialog_edit_series, null);

        ArrayAdapter<String> mAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                   mDb.getAllSeriesNames());

        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);
        mNameView.setAdapter(mAdapter);

        mIsCompleteView = root.findViewById(R.id.is_complete);
        mIsCompleteView.setChecked(mIsComplete);

        return new AlertDialog.Builder(context)
                .setView(root)
                .setTitle(R.string.lbl_series)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                    mName = mNameView.getText().toString().trim();
                    if (mName.isEmpty()) {
                        UserMessage.showUserMessage(mNameView, R.string.warning_required_name);
                        return;
                    }
                    mIsComplete = mIsCompleteView.isChecked();
                    dismiss();

                    //noinspection ConstantConditions
                    if (series.getName().equals(mName)
                            && series.isComplete() == mIsComplete) {
                        return;
                    }
                    series.setName(mName);
                    series.setComplete(mIsComplete);
                    mDb.updateOrInsertSeries(series);
                    BookChangedListener.onBookChanged(this, 0, BookChangedListener.SERIES, null);
                })
                .create();
    }

    @Override
    public void onPause() {
        mName = mNameView.getText().toString().trim();
        mIsComplete = mIsCompleteView.isChecked();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_SERIES, mName);
        outState.putBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE, mIsComplete);
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
