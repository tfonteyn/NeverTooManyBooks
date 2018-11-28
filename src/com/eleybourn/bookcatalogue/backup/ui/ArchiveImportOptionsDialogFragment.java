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
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ArchiveImportOptionsDialogFragment extends DialogFragment {

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
    public static ArchiveImportOptionsDialogFragment newInstance(final int callerId, final @NonNull File file) {
        ArchiveImportOptionsDialogFragment frag = new ArchiveImportOptionsDialogFragment();
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

        // read the info block first.
        boolean mArchiveHasValidDates;
        try {
            BackupReader reader = BackupManager.readFrom(this.requireContext(), mFile);
            BackupInfo info = reader.getInfo();
            reader.close();
            mArchiveHasValidDates = info.getAppVersionCode() >= 152;
        } catch (IOException e) {
            Logger.error(e);
            mArchiveHasValidDates = false;
        }

        View root = requireActivity().getLayoutInflater().inflate(R.layout.dialog_import_type_selection, null);

        View allBooks = root.findViewById(R.id.row_all_books);
        allBooks.setBackgroundResource(android.R.drawable.list_selector_background);
        allBooks.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                ImportSettings settings = new ImportSettings(mFile);
                settings.options = ImportSettings.IMPORT_ALL;

                OnImportTypeSelectionDialogResultsListener activity = (OnImportTypeSelectionDialogResultsListener) requireActivity();
                activity.onImportTypeSelectionDialogResult(mCallerId, settings);
                dismiss();
            }
        });

        if (mArchiveHasValidDates) {
            View changedAndNewBooks = root.findViewById(R.id.row_new_and_changed_books);
            changedAndNewBooks.setBackgroundResource(android.R.drawable.list_selector_background);
            changedAndNewBooks.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    ImportSettings settings = new ImportSettings(mFile);
                    settings.options = ImportSettings.IMPORT_ALL ^ ImportSettings.IMPORT_ONLY_NEW_OR_UPDATED;

                    OnImportTypeSelectionDialogResultsListener activity = (OnImportTypeSelectionDialogResultsListener) requireActivity();
                    activity.onImportTypeSelectionDialogResult(mCallerId, settings);
                    dismiss();
                }
            });

        } else {
            TextView blurb = root.findViewById(R.id.new_and_changed_books_blurb);
            blurb.setText(R.string.old_archive_blurb);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .setTitle(R.string.import_from_archive)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnImportTypeSelectionDialogResultsListener {
        void onImportTypeSelectionDialogResult(final int callerId,
                                               final @NonNull ImportSettings settings);
    }

}
