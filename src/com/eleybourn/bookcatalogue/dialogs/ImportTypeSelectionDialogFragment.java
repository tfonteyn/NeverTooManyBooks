package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.File;
import java.io.IOException;

public class ImportTypeSelectionDialogFragment extends DialogFragment {
	private int mDialogId;
	private File mFile;
	private boolean mArchiveHasValidDates;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnImportTypeSelectionDialogResultListener {
		void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, File file);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param file      the file
	 *
	 * @return			Created fragment
	 */
	public static ImportTypeSelectionDialogFragment newInstance(int dialogId, File file) {
		ImportTypeSelectionDialogFragment frag = new ImportTypeSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
		args.putString(UniqueId.BKEY_FILE_SPEC, file.getAbsolutePath());
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		if (! (context instanceof OnImportTypeSelectionDialogResultListener))
			throw new RuntimeException("Activity " + context.getClass().getSimpleName() + " must implement OnImportTypeSelectionDialogResultListener");
		
	}

	private final OnClickListener mRowClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			handleClick(v);
		}};

	/**
	 * Utility routine to set the OnClickListener for a given view item.
	 * @param root      root view
	 * @param id		Sub-View ID
	 */
	private void setOnClickListener(View root, int id) {
		View v = root.findViewById(id);
		v.setOnClickListener(mRowClickListener);
		v.setBackgroundResource(android.R.drawable.list_selector_background);
	}

	/**
	 * Create the underlying dialog
	 */
    @NonNull
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
    	mFile = new File(getArguments().getString(UniqueId.BKEY_FILE_SPEC));

		try {
			BackupReader reader = BackupManager.readBackup(mFile);
			BackupInfo info = reader.getInfo();
			reader.close();
			mArchiveHasValidDates = info.getAppVersionCode() >= 152;
		} catch (IOException e) {
			Logger.logError(e);
			mArchiveHasValidDates = false;
		}

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_type_selection, null);
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.import_from_archive).create();
		alertDialog.setIcon(android.R.drawable.ic_menu_help);
		alertDialog.setCanceledOnTouchOutside(false);

		setOnClickListener(v, R.id.all_books);
		if (mArchiveHasValidDates) {
			setOnClickListener(v, R.id.new_and_changed_books);
		} else {
			TextView blurb = v.findViewById(R.id.new_and_changed_books_blurb);
			blurb.setText(R.string.old_archive_blurb);
		}

        return alertDialog;
    }
    
    private void handleClick(View v) {
    	if (!mArchiveHasValidDates && v.getId() == R.id.new_and_changed_books) {
    		Toast.makeText(getActivity(), R.string.old_archive_blurb, Toast.LENGTH_LONG).show();
    		return;
    	}

    	try {
    		OnImportTypeSelectionDialogResultListener a = (OnImportTypeSelectionDialogResultListener)getActivity();
    		if (a != null)
	        	a.onImportTypeSelectionDialogResult(mDialogId, this, v.getId(), mFile);
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    	dismiss();
    }

}
