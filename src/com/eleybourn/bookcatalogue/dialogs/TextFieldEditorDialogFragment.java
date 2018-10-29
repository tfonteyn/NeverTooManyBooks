/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.RTE;

/**
 * Fragment wrapper for {@link TextFieldEditorDialog}
 *
 * Calling Fragment does TextFieldEditorDialogFragment.newInstance
 * -> TextFieldEditorDialogFragment.onCreateDialog
 * -> TextFieldEditorDialog()
 * -> user
 * -> via listener/interface back to TextFieldEditorDialogFragment.OnTextFieldEditorListener
 * -> inner dialog is dismissed
 * -> via interface back to hosting Activity
 * -> Activity via interface back to Calling Fragment
 * - Calling Fragment uses data result + dismisses outer dialog
 * ---> if for some reason the Activity cannot forward, then outer dialog is closed by Activity
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment extends DialogFragment {
    /** Dialog title */
    private static final String BKEY_TITLE = "title";
    /* Dialog text/message */
    private static final String BKEY_TEXT = "text";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;

    /** Currently displayed text; null if empty/invalid */
    @Nullable
    private String mText;

    /**
     * Object to handle changes to a description field.
     */
    private final TextFieldEditorDialog.OnEditListener mEditListener = new TextFieldEditorDialog.OnEditListener() {
        @Override
        public void onSaved(@NonNull final TextFieldEditorDialog dialog, @NonNull final String newText) {
            dialog.dismiss();
            ((OnTextFieldEditorListener) requireActivity()).onTextFieldEditorSave(TextFieldEditorDialogFragment.this,
                    mDestinationFieldId, newText);
        }

        @Override
        public void onCancel(@NonNull final TextFieldEditorDialog dialog) {
            dialog.dismiss();
            ((OnTextFieldEditorListener) requireActivity()).onTextFieldEditorCancel(TextFieldEditorDialogFragment.this,
                    mDestinationFieldId);
        }
    };

    /**
     * Constructor
     *
     * @return Created fragment
     */
    @NonNull
    public static TextFieldEditorDialogFragment newInstance() {
        return new TextFieldEditorDialogFragment();
    }

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnTextFieldEditorListener))
            throw new RTE.MustImplementException(context, OnTextFieldEditorListener.class);
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Restore saved state info
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BKEY_TEXT)) {
                mText = savedInstanceState.getString(BKEY_TEXT);
            }
            mTitleId = savedInstanceState.getInt(BKEY_TITLE);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
        }

        // Create the dialog and listen (locally) for its events
        TextFieldEditorDialog editor = new TextFieldEditorDialog(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        editor.setText(mText);
        editor.setOnEditListener(mEditListener);
        return editor;
    }

    @NonNull
    public TextFieldEditorDialogFragment setDestinationFieldId(@IdRes final int id) {
        mDestinationFieldId = id;
        return this;
    }

    /**
     * Accessor. Update dialog if available.
     */
    @NonNull
    public TextFieldEditorDialogFragment setTitle(@StringRes final int title) {
        mTitleId = title;
        TextFieldEditorDialog d = (TextFieldEditorDialog) getDialog();
        if (d != null) {
            d.setTitle(mTitleId);
        }
        return this;
    }

    /**
     * Accessor. Update dialog if available.
     */
    @NonNull
    public TextFieldEditorDialogFragment setText(@NonNull final String text) {
        mText = text;
        TextFieldEditorDialog d = (TextFieldEditorDialog) getDialog();
        if (d != null) {
            d.setText(mText);
        }
        return this;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putInt(BKEY_TITLE, mTitleId);
        outState.putInt(UniqueId.BKEY_FIELD_ID, mDestinationFieldId);
        if (mText != null) {
            outState.putString(BKEY_TEXT, mText);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views
     */
    @Override
    @CallSuper
    public void onPause() {
        TextFieldEditorDialog dialog = (TextFieldEditorDialog) getDialog();
        if (dialog != null) {
            mText = dialog.getText();
        }
        super.onPause();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnTextFieldEditorListener {
        void onTextFieldEditorSave(@NonNull final TextFieldEditorDialogFragment dialog,
                                   final int callerId,
                                   @NonNull final String newText);

        void onTextFieldEditorCancel(@NonNull final TextFieldEditorDialogFragment dialog,
                                     final int callerId);
    }
}
