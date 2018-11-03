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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.Objects;

/**
 * Fragment wrapper for {@link TextFieldEditorDialog}
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment extends DialogFragment {
    /* Dialog text/message */
    public static final String BKEY_TEXT = "text";
    public static final String BKEY_MULTI_LINE = "multiLine";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;

    /**
     * Object to handle changes
     */
    private final TextFieldEditorDialog.OnTextFieldEditorResultsListener mEditListener =
            new TextFieldEditorDialog.OnTextFieldEditorResultsListener() {
                @Override
                public void onTextFieldEditorSave(final @NonNull TextFieldEditorDialog dialog, final @NonNull String newText) {
                    dialog.dismiss();

                    ((OnTextFieldEditorResultsListener) requireActivity()).onTextFieldEditorSave(TextFieldEditorDialogFragment.this,
                            mDestinationFieldId, newText);
                }

                @Override
                public void onTextFieldEditorCancel(final @NonNull TextFieldEditorDialog dialog) {
                    dialog.dismiss();

                    ((OnTextFieldEditorResultsListener) requireActivity()).onTextFieldEditorCancel(TextFieldEditorDialogFragment.this,
                            mDestinationFieldId);
                }
            };
    /** Currently displayed text; null if empty/invalid */
    @Nullable
    private String mText;
    private boolean mMultiLine = false;

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof OnTextFieldEditorResultsListener)) {
            throw new RTE.MustImplementException(context, OnTextFieldEditorResultsListener.class);
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mTitleId = savedInstanceState.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            mText = savedInstanceState.getString(BKEY_TEXT, "");
            mMultiLine = savedInstanceState.getBoolean(BKEY_MULTI_LINE);
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            mTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
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
        outState.putInt(UniqueId.BKEY_DIALOG_TITLE, mTitleId);
        outState.putInt(UniqueId.BKEY_FIELD_ID, mDestinationFieldId);
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
                                   final int callerId,
                                   final @NonNull String newText);

        void onTextFieldEditorCancel(final @NonNull TextFieldEditorDialogFragment dialog,
                                     final int callerId);
    }
}
