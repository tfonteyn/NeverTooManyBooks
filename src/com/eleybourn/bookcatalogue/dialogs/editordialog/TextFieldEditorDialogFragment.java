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
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

/**
 * DialogFragment to edit a specific text field.
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment
        extends
        EditorDialogFragment<TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener> {

    /** Fragment manager tag. */
    public static final String TAG = TextFieldEditorDialogFragment.class.getSimpleName();

    /** Argument: Dialog text/message. */
    private static final String BKEY_TEXT = TAG + ":text";
    /** Argument: allow multiline text. */
    private static final String BKEY_MULTI_LINE = TAG + ":multiLine";

    /** Currently displayed; null if empty/invalid. */
    @Nullable
    private String mText;

    /** View which displays the text. */
    private EditText mTextView;

    /**
     * Constructor.
     *
     * @param callerTag     tag of the calling fragment to send results back to.
     * @param fieldId       the field whose content we want to edit
     * @param currentValue  the current value of the field
     * @param dialogTitleId titel resource id for the dialog
     * @param multiLine     {@code true} if the text box should allow multi-line
     *
     * @return the new instance
     */
    public static TextFieldEditorDialogFragment newInstance(@NonNull final String callerTag,
                                                            @IdRes final int fieldId,
                                                            @NonNull final String currentValue,
                                                            @StringRes final int dialogTitleId,
                                                            final boolean multiLine) {
        TextFieldEditorDialogFragment frag = new TextFieldEditorDialogFragment();
        Bundle args = new Bundle();
        args.putString(UniqueId.BKEY_CALLER_TAG, callerTag);
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, fieldId);
        args.putString(TextFieldEditorDialogFragment.BKEY_TEXT, currentValue);
        args.putBoolean(TextFieldEditorDialogFragment.BKEY_MULTI_LINE, multiLine);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        readBaseArgs(savedInstanceState);

        Bundle args = requireArguments();

        boolean multiLine = args.getBoolean(BKEY_MULTI_LINE);

        args = savedInstanceState == null ? args : savedInstanceState;
        mText = args.getString(BKEY_TEXT, "");


        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_edit_textfield, null);

        mTextView = root.findViewById(R.id.text);
        // (re)set the bit for multiple lines allowed
        int inputType = mTextView.getInputType();
        if (multiLine) {
            inputType = inputType | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        } else {
            inputType = inputType & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        mTextView.setInputType(inputType);

        //noinspection ConstantConditions
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, ((d, which) -> {
                    mText = mTextView.getText().toString().trim();
                    getFragmentListener().onTextFieldEditorSave(mDestinationFieldId, mText);
                }))
                .create();

        if (mTitleId != 0) {
            dialog.setTitle(mTitleId);
        }
        mTextView.setText(mText);

        return dialog;
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views.
     */
    @Override
    @CallSuper
    public void onPause() {
        mText = mTextView.getText().toString().trim();
        super.onPause();
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mText != null) {
            outState.putString(BKEY_TEXT, mText);
        }
    }

    /**
     * Listener interface to receive notifications when dialog is closed.
     */
    public interface OnTextFieldEditorResultsListener {

        /**
         * reports the results after this dialog was confirmed.
         *
         * @param destinationFieldId - the field this dialog is bound to
         * @param newText            - the text
         */
        void onTextFieldEditorSave(int destinationFieldId,
                                   @NonNull String newText);
    }
}
