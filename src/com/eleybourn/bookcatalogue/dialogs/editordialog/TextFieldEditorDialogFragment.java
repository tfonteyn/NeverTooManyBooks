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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;

/**
 * DialogFragment to edit a specific text field.
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment
        extends
        EditorDialogFragment<TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener> {

    /** Argument: Dialog text/message. */
    public static final String BKEY_TEXT = "text";
    /** Argument: allow multiline text. */
    public static final String BKEY_MULTI_LINE = "multiLine";

    /** Currently displayed; null if empty/invalid. */
    @Nullable
    private String mText;
    private boolean mMultiLine;

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

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
        return editor;
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views.
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

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        if (mText != null) {
            outState.putString(BKEY_TEXT, mText);
        }
        outState.putBoolean(BKEY_MULTI_LINE, mMultiLine);

        super.onSaveInstanceState(outState);
    }


    /**
     * The dialog calls this to report back the user input.
     *
     * @param result - the text
     */
    private void reportChanges(@NonNull final String result) {
        getFragmentListener()
                .onTextFieldEditorSave(TextFieldEditorDialogFragment.this,
                                       mDestinationFieldId, result);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnTextFieldEditorResultsListener {

        /**
         * reports the results after this dialog was confirmed.
         *
         * @param dialog             - the dialog
         * @param destinationFieldId - the field this dialog is bound to
         * @param newText            - the text
         */
        void onTextFieldEditorSave(@NonNull TextFieldEditorDialogFragment dialog,
                                   int destinationFieldId,
                                   @NonNull String newText);
    }


    /**
     * The custom dialog.
     */
    class TextFieldEditorDialog
            extends AlertDialog {

        /** View which displays the text. */
        private final EditText mTextView;

        /**
         * Constructor.
         *
         * @param context   - Calling context
         * @param multiLine - set to <tt>true</tt> to allow multi-line text.
         */
        TextFieldEditorDialog(@NonNull final Context context,
                              final boolean multiLine) {
            super(context);

            // Get the layout
            @SuppressLint("InflateParams")
            View root = this.getLayoutInflater().inflate(R.layout.dialog_edit_textfield, null);

            // get the text view
            mTextView = root.findViewById(R.id.text);

            // (re)set the bit for multiple lines allowed
            int inputType = mTextView.getInputType();
            if (multiLine) {
                inputType = inputType | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            } else {
                inputType = inputType & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            }
            mTextView.setInputType(inputType);

            // Handle OK
            root.findViewById(R.id.confirm).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            TextFieldEditorDialogFragment.this.reportChanges(getText());
                        }
                    }
            );


            // Handle Cancel
            root.findViewById(R.id.cancel).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            dismiss();
                        }
                    }
            );


            // Setup the layout
            setView(root);

            // Make sure the buttons moves if the keyboard appears
            //noinspection ConstantConditions
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        public String getText() {
            return mTextView.getText().toString().trim();
        }

        /** @param text - the current text to set. */
        public void setText(@Nullable final String text) {
            mTextView.setText(text);
        }

    }
}
