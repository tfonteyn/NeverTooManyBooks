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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.UniqueId;

/**
 * Fragment wrapper for the {@link TextFieldEditor} dialog
 * 
 * @author pjw
 */
public class TextFieldEditorFragment extends DialogFragment {
	private static final String BKEY_TITLE = "title";
	private static final String BKEY_TEXT = "text";
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnTextFieldEditorListener {
		void onTextFieldEditorSave(final int dialogId, @NonNull final TextFieldEditorFragment dialog, @NonNull final String newText);
		void onTextFieldEditorCancel(final int dialogId, @NonNull final TextFieldEditorFragment dialog);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 * @param text		Text to edit
	 *
	 * @return			Created fragment
	 */
	public static TextFieldEditorFragment newInstance(final int dialogId, final int titleId, @Nullable final String text) {
    	TextFieldEditorFragment frag = new TextFieldEditorFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_TEXT, text);
        args.putInt(BKEY_TITLE, titleId);
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(@NonNull final Context a) {
		super.onAttach(a);

		if (! (a instanceof OnTextFieldEditorListener))
			throw new IllegalStateException("Activity " + a.getClass().getSimpleName() + " must implement OnTextFieldEditorListener");
		
	}

	/**
	 * Create the underlying dialog
	 */
    @NonNull
	@Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        int title = getArguments().getInt(BKEY_TITLE);
        String text = getArguments().getString(BKEY_TEXT);

        TextFieldEditor editor = new TextFieldEditor(getActivity());
        editor.setText(text);
        editor.setTitle(title);
        editor.setOnEditListener(mEditListener);
        return editor;
    }
    
	/**
	 * Object to handle changes to a description field.
	 */
	private final TextFieldEditor.OnEditListener mEditListener = new TextFieldEditor.OnEditListener(){
		@Override
		public void onSaved(@NonNull final String newText) {
			((OnTextFieldEditorListener)getActivity()).onTextFieldEditorSave(mDialogId, TextFieldEditorFragment.this, newText);
		}
		@Override
		public void onCancel() {
			((OnTextFieldEditorListener)getActivity()).onTextFieldEditorCancel(mDialogId, TextFieldEditorFragment.this);
		}
	};
}
