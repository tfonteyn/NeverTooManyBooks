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
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ImportTypeSelectionDialogFragment extends DialogFragment {
    private int mCallerId;
    private File mFile;
    private boolean mArchiveHasValidDates;
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
    public static ImportTypeSelectionDialogFragment newInstance(final int callerId, final @NonNull File file) {
        ImportTypeSelectionDialogFragment frag = new ImportTypeSelectionDialogFragment();
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
        if (!(context instanceof OnImportTypeSelectionDialogResultsListener))
            throw new RTE.MustImplementException(context, OnImportTypeSelectionDialogResultsListener.class);
    }

    /**
     * Utility routine to set the OnClickListener for a given view item.
     *
     * @param root root view
     * @param id   Sub-View ID
     */
    private void setOnClickListener(final @NonNull View root, final @IdRes int id) {
        View view = root.findViewById(id);
        view.setOnClickListener(mRowClickListener);
        view.setBackgroundResource(android.R.drawable.list_selector_background);
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

        try {
            BackupReader reader = BackupManager.readFrom(this.requireContext(), mFile);
            BackupInfo info = reader.getInfo();
            reader.close();
            mArchiveHasValidDates = info.getAppVersionCode() >= 152;
        } catch (IOException e) {
            Logger.error(e);
            mArchiveHasValidDates = false;
        }

        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_import_type_selection, null);
        setOnClickListener(view, R.id.row_all_books);
        if (mArchiveHasValidDates) {
            setOnClickListener(view, R.id.row_new_and_changed_books);
        } else {
            TextView blurb = view.findViewById(R.id.new_and_changed_books_blurb);
            blurb.setText(R.string.old_archive_blurb);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(view)
                .setTitle(R.string.import_from_archive)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void handleClick(final @NonNull View v) {
        if (!mArchiveHasValidDates && v.getId() == R.id.row_new_and_changed_books) {
            StandardDialogs.showUserMessage(requireActivity(), R.string.old_archive_blurb);
            //Snackbar.make(v, R.string.old_archive_blurb, Snackbar.LENGTH_LONG).show();
            return;
        }

        try {
            ImportTypeSelectionDialogFragment.ImportSettings settings = new ImportTypeSelectionDialogFragment.ImportSettings();
            settings.file = mFile;
            settings.options = Importer.IMPORT_ALL;

            OnImportTypeSelectionDialogResultsListener activity = (OnImportTypeSelectionDialogResultsListener) requireActivity();
            activity.onImportTypeSelectionDialogResult(this, mCallerId, settings);
        } catch (Exception e) {
            Logger.error(e);
        }
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnImportTypeSelectionDialogResultsListener {
        void onImportTypeSelectionDialogResult(final @NonNull DialogFragment dialog,
                                               final int callerId,
                                               final @NonNull ImportSettings settings);
    }

    public static class ImportSettings {
        public File file;
        public int options;
    }

}
