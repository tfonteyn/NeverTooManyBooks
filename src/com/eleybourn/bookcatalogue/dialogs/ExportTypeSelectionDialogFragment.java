package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.File;
import java.util.Date;

public class ExportTypeSelectionDialogFragment extends DialogFragment {
    private int mDialogId;
    private File mFile;
    private final OnClickListener mRowClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            handleClick(v);
        }
    };

    /**
     * Constructor
     *
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param file     the file
     *
     * @return Created fragment
     */
    public static ExportTypeSelectionDialogFragment newInstance(int dialogId, File file) {
        ExportTypeSelectionDialogFragment frag = new ExportTypeSelectionDialogFragment();
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

        if (!(context instanceof OnExportTypeSelectionDialogResultListener))
            throw new RuntimeException("Activity " + context.getClass().getSimpleName() + " must implement OnExportTypeSelectionDialogResultListener");

    }

    /**
     * Utility routine to set the OnClickListener for a given view item.
     *
     * @param root root view
     * @param id   Sub-View ID
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

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_type_selection, null);
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.backup_to_archive).create();
        alertDialog.setIcon(android.R.drawable.ic_menu_help);
        alertDialog.setCanceledOnTouchOutside(false);

        setOnClickListener(v, R.id.all_books_row);
        setOnClickListener(v, R.id.advanced_options);

        return alertDialog;
    }

    private void handleClick(View v) {
        try {
            if (v.getId() == R.id.advanced_options) {
                ExportAdvancedDialogFragment frag = ExportAdvancedDialogFragment.newInstance(1, mFile);
                frag.show(getActivity().getSupportFragmentManager(), null);
            } else {
                OnExportTypeSelectionDialogResultListener a = (OnExportTypeSelectionDialogResultListener) getActivity();
                if (a != null) {
                    ExportSettings settings = new ExportSettings();
                    settings.file = mFile;
                    settings.options = Exporter.EXPORT_ALL;
                    a.onExportTypeSelectionDialogResult(mDialogId, this, settings);
                }
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnExportTypeSelectionDialogResultListener {
        void onExportTypeSelectionDialogResult(int dialogId, DialogFragment dialog, ExportSettings settings);
    }

    public static class ExportSettings {
        public File file;
        public int options;
        public Date dateFrom;
    }

}
