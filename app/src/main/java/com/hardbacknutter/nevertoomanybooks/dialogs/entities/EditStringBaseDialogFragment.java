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
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BookChangedListenerOwner;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

public abstract class EditStringBaseDialogFragment
        extends DialogFragment
        implements BookChangedListenerOwner {

    /** Fragment/Log tag. */
    private static final String TAG = "EditStringBaseDialog";
    /** Argument. */
    static final String BKEY_TEXT = TAG + ":text";

    /** Database Access. */
    @Nullable
    DAO mDb;
    /** Current edit. */
    @Nullable
    String mCurrentText;
    @Nullable
    String mOriginalText;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookChangedListener> mListener;
    @Nullable
    private AutoCompleteTextView mEditText;
    @Nullable
    private String mDialogTitle;
    private int mChangeFlags;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mDialogTitle = args.getString(StandardDialogs.BKEY_DIALOG_TITLE,
                                      getString(R.string.action_edit));

        mOriginalText = args.getString(BKEY_TEXT, "");

        if (savedInstanceState == null) {
            mCurrentText = mOriginalText;
        } else {
            mCurrentText = savedInstanceState.getString(BKEY_TEXT, "");
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        final Toolbar toolbar = getView().findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.setTitle(mDialogTitle);
        toolbar.inflateMenu(R.menu.toolbar_save);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        mEditText = getView().findViewById(R.id.name);
        mEditText.setText(mCurrentText);

        // soft-keyboards 'done' button act as a shortcut to confirming/saving the changes
        mEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                App.hideKeyboard(v);
                saveChanges();
                return true;
            }
            return false;
        });
    }

    /**
     * Init for the sub class.
     *
     * @param changeFlags what we are changing
     * @param objects     (optional) list of strings for the auto-complete.
     */
    void init(@BookChangedListener.Flags int changeFlags,
              @Nullable final List<String> objects) {
        mChangeFlags = changeFlags;

        if (objects != null) {
            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item, objects);
            //noinspection ConstantConditions
            mEditText.setAdapter(adapter);
        }
    }

    private boolean saveChanges() {
        //noinspection ConstantConditions
        mCurrentText = mEditText.getText().toString().trim();
        if (mCurrentText.isEmpty()) {
            Snackbar.make(mEditText, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        // anything actually changed ?
        if (mCurrentText.equals(mOriginalText)) {
            return true;
        }

        final Bundle data = onSave();
        if (mListener != null && mListener.get() != null) {
            mListener.get().onBookChanged(0, mChangeFlags, data);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onBookChanged|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }

        return true;
    }

    /**
     * Save data.
     *
     * @return the bundle to pass back to
     * {@link BookChangedListener#onBookChanged(long, int, Bundle)}
     */
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
