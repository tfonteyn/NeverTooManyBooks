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
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.lang.ref.WeakReference;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BookChangedListenerOwner;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

public abstract class EditStringBaseDialogFragment
        extends BaseDialogFragment
        implements BookChangedListenerOwner {

    /** Fragment/Log tag. */
    private static final String TAG = "EditStringBaseDialog";
    /** Argument. */
    static final String BKEY_TEXT = TAG + ":text";

    @StringRes
    private final int mDialogTitleId;
    @StringRes
    private final int mLabelId;
    @BookChangedListener.Flags
    private final int mChangeFlags;
    /** Database Access. */
    @Nullable
    DAO mDb;
    /** The text we're editing. */
    @Nullable
    String mOriginalText;
    /** Current edit. */
    @Nullable
    String mCurrentText;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookChangedListener> mListener;

    private DialogEditStringBinding mVb;

    /**
     * Constructor; only used by the child class no-args constructor.
     *
     * @param titleId     for the dialog (i.e. the toolbar)
     * @param label       to use for the 'hint' of the input field
     * @param changeFlags one of the {@link BookChangedListener.Flags} bits
     */
    EditStringBaseDialogFragment(@StringRes final int titleId,
                                 @StringRes final int label,
                                 @BookChangedListener.Flags final int changeFlags) {
        super(R.layout.dialog_edit_string);

        mDialogTitleId = titleId;
        mLabelId = label;
        mChangeFlags = changeFlags;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mOriginalText = args.getString(BKEY_TEXT, "");

        if (savedInstanceState == null) {
            mCurrentText = mOriginalText;
        } else {
            mCurrentText = savedInstanceState.getString(BKEY_TEXT, "");
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditStringBinding.bind(view);

        mVb.toolbar.setTitle(mDialogTitleId);
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

        mVb.lblEditString.setHint(getString(mLabelId));
        mVb.lblEditString.setErrorEnabled(true);

        mVb.editString.setText(mCurrentText);

        // soft-keyboards 'done' button act as a shortcut to confirming/saving the changes
        mVb.editString.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        List<String> objects = getList();
        if (objects != null) {
            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item, objects);
            mVb.editString.setAdapter(adapter);
        }
    }

    /**
     * @return (optional) list of strings for the auto-complete.
     */
    @Nullable
    protected List<String> getList() {
        return null;
    }

    private boolean saveChanges() {
        mCurrentText = mVb.editString.getText().toString().trim();
        if (mCurrentText.isEmpty()) {
            showError(mVb.lblEditString, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mCurrentText.equals(mOriginalText)) {
            return true;
        }

        final Bundle data = onSave();
        if (mListener != null && mListener.get() != null) {
            mListener.get().onChange(0, mChangeFlags, data);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onBookChanged|"
                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }

        return true;
    }

    /**
     * Save data.
     *
     * @return the bundle to pass back to
     * {@link BookChangedListener#onChange(long, int, Bundle)}
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
        mCurrentText = mVb.editString.getText().toString().trim();
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
