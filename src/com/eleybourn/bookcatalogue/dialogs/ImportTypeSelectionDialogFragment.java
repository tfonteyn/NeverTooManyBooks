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

/**
 * Layout clickable items:
 *
 * R.id.all_books_row
 * R.id.new_and_changed_books_row
 */
public class ImportTypeSelectionDialogFragment extends DialogFragment {
    private int mDialogId;
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
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param file     the file
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportTypeSelectionDialogFragment newInstance(final int dialogId, @NonNull final File file) {
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
    @CallSuper
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof OnImportTypeSelectionDialogResultListener))
            throw new RTE.MustImplementException(context, OnImportTypeSelectionDialogResultListener.class);

    }

    /**
     * Utility routine to set the OnClickListener for a given view item.
     *
     * @param root root view
     * @param id   Sub-View ID
     */
    private void setOnClickListener(@NonNull final View root, @IdRes final int id) {
        View v = root.findViewById(id);
        v.setOnClickListener(mRowClickListener);
        v.setBackgroundResource(android.R.drawable.list_selector_background);
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        //noinspection ConstantConditions
        mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        mFile = new File(Objects.requireNonNull(getArguments().getString(UniqueId.BKEY_FILE_SPEC)));

        try {
            BackupReader reader = BackupManager.readFrom(mFile);
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

    private void handleClick(@NonNull final View v) {
        if (!mArchiveHasValidDates && v.getId() == R.id.row_new_and_changed_books) {
            StandardDialogs.showBriefMessage(requireActivity(), R.string.old_archive_blurb);
            //Snackbar.make(v, R.string.old_archive_blurb, Snackbar.LENGTH_LONG).show();
            return;
        }

        try {
            OnImportTypeSelectionDialogResultListener a = (OnImportTypeSelectionDialogResultListener) requireActivity();
            ImportTypeSelectionDialogFragment.ImportSettings settings = new ImportTypeSelectionDialogFragment.ImportSettings();
            settings.file = mFile;
            settings.options = Importer.IMPORT_ALL;
            a.onImportTypeSelectionDialogResult(mDialogId, this, settings);
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
    public interface OnImportTypeSelectionDialogResultListener {
        void onImportTypeSelectionDialogResult(final int dialogId, @NonNull final DialogFragment dialog,
                                               @NonNull final ImportTypeSelectionDialogFragment.ImportSettings settings);
    }

    public static class ImportSettings {
        public File file;
        public int options;
    }

}
