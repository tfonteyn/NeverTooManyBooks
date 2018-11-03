package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Date;
import java.util.Objects;

public class ExportTypeSelectionDialogFragment extends DialogFragment {
    private int mCallerId;
    private File mFile;
    private final OnClickListener mRowClickListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            handleClick(v);
        }
    };

    /**
     * Constructor
     *
     * @param callerId ID passed by caller. Can be 0, will be passed back in event
     * @param file     the file
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportTypeSelectionDialogFragment newInstance(final int callerId, final @NonNull File file) {
        ExportTypeSelectionDialogFragment frag = new ExportTypeSelectionDialogFragment();
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

        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_export_type_selection, null);

        setOnClickListener(v, R.id.row_all_books);
        setOnClickListener(v, R.id.row_advanced_options);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(v)
                .setTitle(R.string.backup_to_archive)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void handleClick(final @NonNull View v) {
        if (v.getId() == R.id.row_advanced_options) {
            ExportAdvancedDialogFragment.newInstance(mCallerId, mFile)
                    .show(requireActivity().getSupportFragmentManager(), null);
        } else {
            ExportSettings settings = new ExportSettings();
            settings.file = mFile;
            settings.options = Exporter.EXPORT_ALL;

            OnExportTypeSelectionDialogResultsListener activity = (OnExportTypeSelectionDialogResultsListener) requireActivity();
            activity.onExportTypeSelectionDialogResult(this, mCallerId, settings);
        }
        dismiss();
    }

    /**
     * Utility routine to set the OnClickListener for a given view item.
     *
     * @param root root view
     * @param id   Sub-View ID
     */
    private void setOnClickListener(final @NonNull View root, final @IdRes int id) {
        View v = root.findViewById(id);
        v.setOnClickListener(mRowClickListener);
        v.setBackgroundResource(android.R.drawable.list_selector_background);
    }
    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnExportTypeSelectionDialogResultsListener {
        void onExportTypeSelectionDialogResult(final @NonNull DialogFragment dialog,
                                               final int callerId,
                                               final @NonNull ExportSettings settings);
    }

    public static class ExportSettings {
        public File file;
        public int options;
        @Nullable
        public Date dateFrom;
    }

}
