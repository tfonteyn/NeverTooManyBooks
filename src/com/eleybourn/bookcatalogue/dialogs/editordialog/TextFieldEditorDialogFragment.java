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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.datamanager.Fields;

/**
 * DialogFragment to edit a specific text field; optionally with AutoComplete adapter.
 * <p>
 * A layout needs to have:
 * - R.id.text
 * - R.id.confirm
 * - R.id.cancel
 *
 * @author pjw
 */
public class TextFieldEditorDialogFragment
        extends
        EditorDialogFragment<TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener> {

    /** Fragment manager tag. */
    public static final String TAG = TextFieldEditorDialogFragment.class.getSimpleName();

    /** Argument: Dialog text/message. */
    private static final String BKEY_TEXT = "text";
    /** Argument: allow multiline text. */
    private static final String BKEY_MULTI_LINE = "multiLine";

    /** Currently displayed; null if empty/invalid. */
    @Nullable
    private String mText;

    /**
     * Constructor.
     *
     * @param callerTag     tag of the calling fragment to send results back to.
     * @param field         the field whose content we want to edit
     * @param dialogTitleId titel resource id for the dialog
     * @param multiLine     <tt>true</tt> if the text box should allow multi-line
     *
     * @return the new instance
     */
    public static TextFieldEditorDialogFragment newInstance(@NonNull final String callerTag,
                                                            @NonNull final Fields.Field field,
                                                            @StringRes final int dialogTitleId,
                                                            final boolean multiLine) {
        TextFieldEditorDialogFragment frag = new TextFieldEditorDialogFragment();
        Bundle args = new Bundle();
        args.putString(UniqueId.BKEY_CALLER_TAG, callerTag);
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
        args.putString(TextFieldEditorDialogFragment.BKEY_TEXT, field.getValue().toString());
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

        boolean multiLine = requireArguments().getBoolean(BKEY_MULTI_LINE);
        // optional, use a simple text view by default.
        int dialogLayoutId = requireArguments().getInt(UniqueId.BKEY_LAYOUT_ID,
                                                       R.layout.dialog_edit_textfield);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mText = args.getString(BKEY_TEXT, "");

        // Create the dialog and listen (locally) for its events
        TextFieldEditorDialog editor = new TextFieldEditorDialog(requireActivity(),
                                                                 dialogLayoutId, multiLine);
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
         * @param context   the caller context
         * @param multiLine <tt>true</tt> to allow multi-line text.
         */
        TextFieldEditorDialog(@NonNull final Context context,
                              @LayoutRes final int dialogLayoutId,
                              final boolean multiLine) {
            super(context);
            // Get the layout
            @SuppressLint("InflateParams")
            View root = getLayoutInflater().inflate(dialogLayoutId, null);

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
                    v -> {
                        dismiss();
                        getFragmentListener().onTextFieldEditorSave(mDestinationFieldId, getText());
                    }
            );

            // Handle Cancel
            root.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());

            // Setup the layout
            setView(root);

            // Make sure the buttons moves if the keyboard appears
            //noinspection ConstantConditions
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                                 | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        public String getText() {
            return mTextView.getText().toString().trim();
        }

        /** @param text - the current text to set. */
        public void setText(@Nullable final String text) {
            mTextView.setText(text);
        }

        /**
         * If the underlying layout text field is an AutoCompleteTextView, set the adapter.
         *
         * @param list of strings for the adapter.
         */
        public void setAutoCompleteList(@NonNull final ArrayList<String> list) {
            if (mTextView instanceof AutoCompleteTextView) {
                ArrayAdapter<String> mAdapter =
                        new ArrayAdapter<>(requireActivity(),
                                           android.R.layout.simple_dropdown_item_1line, list);
                ((AutoCompleteTextView) mTextView).setAdapter(mAdapter);
            }
        }

    }
}
