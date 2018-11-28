package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Objects;

/**
 * Generic {@link ExportSettings} dialog.
 */
public class ExportSettingsDialogFragment extends DialogFragment {
    private int mCallerId;
    private File mFile;

    /**
     * Constructor
     *
     * @param callerId ID passed by caller. Can be 0, will be passed back in event
     * @param file     the file
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportSettingsDialogFragment newInstance(final int callerId, final @NonNull File file) {
        ExportSettingsDialogFragment frag = new ExportSettingsDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_CALLER_ID, callerId);
        args.putString(UniqueId.BKEY_FILE_SPEC, file.getAbsolutePath());
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof OnExportTypeSelectionDialogResultsListener))
            throw new RTE.MustImplementException(context, OnExportTypeSelectionDialogResultsListener.class);
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        // savedInstanceState not used.
        Bundle args = getArguments();
        Objects.requireNonNull(args);
        mCallerId = args.getInt(UniqueId.BKEY_CALLER_ID);
        mFile = new File(Objects.requireNonNull(args.getString(UniqueId.BKEY_FILE_SPEC)));

        View root = requireActivity().getLayoutInflater().inflate(R.layout.dialog_export_type_selection, null);

        // option to backup ALL
        View importAll = root.findViewById(R.id.row_all_books);
        importAll.setBackgroundResource(android.R.drawable.list_selector_background);
        importAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                ExportSettings settings = new ExportSettings(mFile);
                settings.options = ExportSettings.EXPORT_ALL;
                OnExportTypeSelectionDialogResultsListener listener = (OnExportTypeSelectionDialogResultsListener) requireActivity();
                listener.onExportTypeSelectionDialogResult(ExportSettingsDialogFragment.this,
                        mCallerId, settings);
                dismiss();
            }
        });

        // take us to advanced settings on what to backup.
        View advancedOptions = root.findViewById(R.id.row_advanced_options);
        advancedOptions.setBackgroundResource(android.R.drawable.list_selector_background);
        advancedOptions.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                ExportAdvancedSettingsDialogFragment.newInstance(mCallerId, mFile)
                        .show(requireActivity().getSupportFragmentManager(), null);
                dismiss();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .setTitle(R.string.backup_to_archive)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnExportTypeSelectionDialogResultsListener {
        void onExportTypeSelectionDialogResult(final @NonNull DialogFragment dialog,
                                               final int callerId,
                                               final @NonNull ExportSettings settings);
    }

}
