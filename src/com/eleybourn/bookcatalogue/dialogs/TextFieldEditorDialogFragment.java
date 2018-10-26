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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.RTE;

/**
 * Fragment wrapper for the {@link TextFieldEditorDialog} dialog
 * 
 * @author pjw
 */
public class TextFieldEditorDialogFragment extends DialogFragment {
	private static final String BKEY_TITLE = "title";
	private static final String BKEY_TEXT = "text";
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnTextFieldEditorListener {
		void onTextFieldEditorSave(final int dialogId, @NonNull final TextFieldEditorDialogFragment dialog, @NonNull final String newText);
		void onTextFieldEditorCancel(final int dialogId, @NonNull final TextFieldEditorDialogFragment dialog);
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
	@NonNull
    public static TextFieldEditorDialogFragment newInstance(final int dialogId, final int titleId, @Nullable final String text) {
    	TextFieldEditorDialogFragment frag = new TextFieldEditorDialogFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_TEXT, text);
        args.putInt(BKEY_TITLE, titleId);
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports interface
	 */
	@Override
	@CallSuper
	public void onAttach(@NonNull final Context context) {
		super.onAttach(context);
		if (! (context instanceof OnTextFieldEditorListener))
			throw new RTE.MustImplementException(context, OnTextFieldEditorListener.class);
	}

	/**
	 * Create the underlying dialog
	 */
    @NonNull
	@Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
		//noinspection ConstantConditions
		mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        int title = getArguments().getInt(BKEY_TITLE);
        String text = getArguments().getString(BKEY_TEXT);

        TextFieldEditorDialog editor = new TextFieldEditorDialog(requireActivity());
        editor.setText(text);
        editor.setTitle(title);
        editor.setOnEditListener(mEditListener);
        return editor;
    }
    
	/**
	 * Object to handle changes to a description field.
	 */
	private final TextFieldEditorDialog.OnEditListener mEditListener = new TextFieldEditorDialog.OnEditListener(){
		@Override
		public void onSaved(@NonNull final String newText) {
			((OnTextFieldEditorListener)requireActivity()).onTextFieldEditorSave(mDialogId, TextFieldEditorDialogFragment.this, newText);
		}
		@Override
		public void onCancel() {
			((OnTextFieldEditorListener)requireActivity()).onTextFieldEditorCancel(mDialogId, TextFieldEditorDialogFragment.this);
		}
	};
}
