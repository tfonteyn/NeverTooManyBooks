package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;

public class MessageDialogFragment extends DialogFragment {
	private static final String TITLE_ID = "titleId";
	private static final String MESSAGE = "message";
	private static final String BUTTON_POSITIVE_TEXT_ID = "buttonPositiveTextId";
	private static final String BUTTON_NEGATIVE_TEXT_ID = "buttonNegativeTextId";
	private static final String BUTTON_NEUTRAL_TEXT_ID = "buttonNeutralTextId";
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnMessageDialogResultListener {
		void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static MessageDialogFragment newInstance(int dialogId, int titleId, int messageId, int buttonPositiveTextId, int buttonNegativeTextId, int buttonNeutralTextId) {
		String message = BookCatalogueApp.getResourceString(messageId);
        return MessageDialogFragment.newInstance(dialogId, titleId, message, buttonPositiveTextId, buttonNegativeTextId, buttonNeutralTextId);
    }

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static MessageDialogFragment newInstance(int dialogId, int titleId, String message, int buttonPositiveTextId, int buttonNegativeTextId, int buttonNeutralTextId) {
		MessageDialogFragment frag = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        args.putInt(TITLE_ID, titleId);
        args.putString(MESSAGE, message);
        args.putInt(BUTTON_POSITIVE_TEXT_ID, buttonPositiveTextId);
        args.putInt(BUTTON_NEGATIVE_TEXT_ID, buttonNegativeTextId);
        args.putInt(BUTTON_NEUTRAL_TEXT_ID, buttonNeutralTextId);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		if (! (context instanceof OnMessageDialogResultListener))
			throw new RuntimeException("Activity " + context.getClass().getSimpleName() + " must implement OnMessageDialogResultListener");
		
	}

	/**
	 * Create the underlying dialog
	 */
    @NonNull
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        int title = getArguments().getInt(TITLE_ID);
        String msg = getArguments().getString(MESSAGE);
        int btnPos = getArguments().getInt(BUTTON_POSITIVE_TEXT_ID);
        int btnNeg = getArguments().getInt(BUTTON_NEGATIVE_TEXT_ID);
        int btnNeut = getArguments().getInt(BUTTON_NEUTRAL_TEXT_ID);
        
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setMessage(msg).create();
		alertDialog.setTitle(title);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		
		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,
				getString(btnPos),
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				handleButton(AlertDialog.BUTTON_POSITIVE);
			}
		}); 
		
		if (btnNeg != 0) {
			alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
					getString(btnNeg),
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					handleButton(AlertDialog.BUTTON_NEGATIVE);
				}
			}); 			
		}
		if (btnNeut != 0) {
			alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL,
					getString(btnNeut),
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					handleButton(AlertDialog.BUTTON_NEUTRAL);
				}
			}); 			
		}

        return alertDialog;
    }
    
    private void handleButton(int button) {
    	try {
    		OnMessageDialogResultListener a = (OnMessageDialogResultListener)getActivity();
    		if (a != null)
	        	a.onMessageDialogResult(mDialogId, this, button);    		
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    	dismiss();
    }
}
