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
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * TODO: as the Dialog is now an inner class, remove the listener between DialogFragment and Dialog.
 *
 * DialogFragment to edit a specific text field.
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment extends EditorDialogFragment<TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener> {
    /* Dialog text/message */
    public static final String BKEY_TEXT = "text";
    public static final String BKEY_MULTI_LINE = "multiLine";

    /**
     * Object to handle changes
     */
    private final TextFieldEditorDialog.OnTextFieldEditorResultsListener mEditListener =
            new TextFieldEditorDialog.OnTextFieldEditorResultsListener() {
                @Override
                public void onTextFieldEditorSave(final @NonNull String newText) {
                     getFragmentListener()
                            .onTextFieldEditorSave(TextFieldEditorDialogFragment.this,
                                    mDestinationFieldId, newText);
                }

                @Override
                public void onTextFieldEditorCancel() {
                    getFragmentListener()
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
        Tracker.enterOnPause(this);
        TextFieldEditorDialog dialog = (TextFieldEditorDialog) getDialog();
        if (dialog != null) {
            mText = dialog.getText();
        }
        super.onPause();
        Tracker.exitOnPause(this);
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


    static class TextFieldEditorDialog extends AlertDialog {
        /** View which displays the text */
        private final EditText mTextView;
        /** Listener for dialog exit/save/cancel */
        private OnTextFieldEditorResultsListener mListener;

        /**
         * Constructor
         *
         * @param context Calling context
         */
        TextFieldEditorDialog(final @NonNull Context context, final boolean multiLine) {
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
                        public void onClick(final View view) {
                            mListener.onTextFieldEditorSave(getText());
                        }
                    }
            );


            // Handle Cancel
            root.findViewById(R.id.cancel).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            mListener.onTextFieldEditorCancel();
                        }
                    }
            );

            // Handle Cancel by any means
            this.setOnCancelListener(new OnCancelListener() {

                @Override
                public void onCancel(final DialogInterface dialog) {
                    mListener.onTextFieldEditorCancel();
                }
            });

            // Setup the layout
            setView(root);

            // Make sure the buttons moves if the keyboard appears
            //noinspection ConstantConditions
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        /** Set the listener */
        void setResultsListener(final @NonNull OnTextFieldEditorResultsListener listener) {
            mListener = listener;
        }

        /** Set the current text */
        public void setText(final @Nullable String text) {
            mTextView.setText(text);
        }

        public String getText() {
            return mTextView.getText().toString().trim();
        }

        /**
         * Listener to receive notifications when dialog is closed by any means.
         *
         * @author pjw
         */
        protected interface OnTextFieldEditorResultsListener {
            void onTextFieldEditorSave(final @NonNull String newText);

            void onTextFieldEditorCancel();
        }

    }
}
