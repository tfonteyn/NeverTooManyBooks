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
package com.hardbacknutter.nevertoomanybooks.dialogs.simplestring;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BookChangedListenerOwner;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

public abstract class EditStringBaseDialogFragment
        extends DialogFragment
        implements BookChangedListenerOwner {

    private static final String TAG = "EditStringBaseDialog";
    static final String BKEY_TEXT = TAG + ":text";

    /** Database Access. */
    @Nullable
    DAO mDb;
    @Nullable
    String mCurrentText;
    String mOriginalText;
    @Nullable
    private WeakReference<BookChangedListener> mListener;
    @Nullable
    private AutoCompleteTextView mEditText;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        mOriginalText = requireArguments().getString(BKEY_TEXT, "");

        if (savedInstanceState == null) {
            mCurrentText = mOriginalText;
        } else {
            mCurrentText = savedInstanceState.getString(BKEY_TEXT, "");
        }
    }

    /**
     * Create the dialog.
     *
     * @param layoutId    root view id
     * @param changeFlags what we are changing
     * @param objects     (optional) list of strings for the auto-complete.
     *
     * @return instance
     */
    Dialog createDialog(@LayoutRes int layoutId,
                        @BookChangedListener.Flags int changeFlags,
                        @Nullable final List<String> objects) {

        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View root = inflater.inflate(layoutId, null);

        mEditText = root.findViewById(R.id.name);
        mEditText.setText(mCurrentText);

        if (objects != null) {
            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item, objects);
            mEditText.setAdapter(adapter);
        }

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(root)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_save, (d, w) -> saveChanges(changeFlags))
                .create();
    }

    private void saveChanges(@BookChangedListener.Flags int changeFlags) {
        //noinspection ConstantConditions
        mCurrentText = mEditText.getText().toString().trim();
        if (mCurrentText.isEmpty()) {
            Snackbar.make(mEditText, R.string.warning_missing_name, Snackbar.LENGTH_LONG).show();
            return;
        }
        // if there are no differences, just bail out.
        if (mCurrentText.equals(mOriginalText)) {
            return;
        }
        Bundle data = onSave();

        if (mListener != null && mListener.get() != null) {
            mListener.get().onBookChanged(0, changeFlags, data);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onBookChanged|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    @Nullable
    abstract Bundle onSave();

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BKEY_TEXT, mCurrentText);
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
        //noinspection ConstantConditions
        mCurrentText = mEditText.getText().toString().trim();
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
