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
package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Fragment wrapper for {@link TextFieldEditorDialog}
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment extends EditorDialogFragment {
    /* Dialog text/message */
    public static final String BKEY_TEXT = "text";
    public static final String BKEY_MULTI_LINE = "multiLine";
    /**
     * Object to handle changes
     */
    private final TextFieldEditorDialog.OnTextFieldEditorResultsListener mEditListener =
            new TextFieldEditorDialog.OnTextFieldEditorResultsListener() {
                @Override
                public void onTextFieldEditorSave(final @NonNull TextFieldEditorDialog dialog, final @NonNull String newText) {
                    dialog.dismiss();
                    ((OnTextFieldEditorResultsListener) getCallerFragment())
                            .onTextFieldEditorSave(TextFieldEditorDialogFragment.this,
                                    mDestinationFieldId, newText);
                }

                @Override
                public void onTextFieldEditorCancel(final @NonNull TextFieldEditorDialog dialog) {
                    dialog.dismiss();
                    ((OnTextFieldEditorResultsListener) getCallerFragment())
                            .onTextFieldEditorCancel(TextFieldEditorDialogFragment.this,
                                    mDestinationFieldId);
                }
            };

    /** Currently displayed; null if empty/invalid */
    @Nullable
    private String mText;
    private boolean mMultiLine = false;

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        initStandardArgs(savedInstanceState);

        if (savedInstanceState != null) {
            mText = savedInstanceState.getString(BKEY_TEXT, "");
            mMultiLine = savedInstanceState.getBoolean(BKEY_MULTI_LINE);
        } else {
            Bundle args = getArguments();
            //noinspection ConstantConditions
            mText = args.getString(BKEY_TEXT, "");
            mMultiLine = args.getBoolean(BKEY_MULTI_LINE);
        }

        // Create the dialog and listen (locally) for its events
        TextFieldEditorDialog editor = new TextFieldEditorDialog(requireActivity(), mMultiLine);
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        editor.setText(mText);
        editor.setResultsListener(mEditListener);
        return editor;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        if (mText != null) {
            outState.putString(BKEY_TEXT, mText);
        }
        outState.putBoolean(BKEY_MULTI_LINE, mMultiLine);

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
    public interface OnTextFieldEditorResultsListener {
        void onTextFieldEditorSave(final @NonNull TextFieldEditorDialogFragment dialog,
                                   final int destinationFieldId,
                                   final @NonNull String newText);

        void onTextFieldEditorCancel(final @NonNull TextFieldEditorDialogFragment dialog,
                                     final int destinationFieldId);
    }
}
