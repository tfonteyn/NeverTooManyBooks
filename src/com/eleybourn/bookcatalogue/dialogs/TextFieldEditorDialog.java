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

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;

/**
 * Dialog to edit a specific text field.
 *
 * The constructors and interface are now protected because this really should
 * only be called as part of the fragment version.
 *
 * @author pjw
 */
class TextFieldEditorDialog extends AlertDialog {
    /** View which displays the text */
    private final EditText mTextView;
    /** Listener for dialog exit/save/onPartialDatePickerCancel */
    private OnTextFieldEditorResultsListener mListener;

    /**
     * Constructor
     *
     * @param context Calling context
     */
    TextFieldEditorDialog(final @NonNull Context context, final boolean multiLine) {
        super(context);

        // Get the layout
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
                        mListener.onTextFieldEditorSave(TextFieldEditorDialog.this, getText());
                    }
                }
        );


        // Handle Cancel
        root.findViewById(R.id.cancel).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View view) {
                        mListener.onTextFieldEditorCancel(TextFieldEditorDialog.this);
                    }
                }
        );

        // Handle Cancel by any means
        this.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(final DialogInterface dialog) {
                mListener.onTextFieldEditorCancel(TextFieldEditorDialog.this);
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
        void onTextFieldEditorSave(final @NonNull TextFieldEditorDialog dialog,
                                   final @NonNull String newText);

        void onTextFieldEditorCancel(final @NonNull TextFieldEditorDialog dialog);
    }

}
