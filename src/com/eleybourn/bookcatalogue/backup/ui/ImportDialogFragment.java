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
import android.widget.Checkable;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ImportDialogFragment extends DialogFragment {

    private final ImportSettings settings = new ImportSettings();

    /**
     * Constructor
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportDialogFragment newInstance(final @NonNull ImportSettings settings) {
        ImportDialogFragment frag = new ImportDialogFragment();
        Bundle args = new Bundle();
        args.putString(UniqueId.BKEY_FILE_SPEC, settings.file.getAbsolutePath());
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
        settings.file = new File(Objects.requireNonNull(args.getString(UniqueId.BKEY_FILE_SPEC)));

        View root = requireActivity().getLayoutInflater().inflate(R.layout.dialog_import_options, null);

        if (BuildConfig.DEBUG) {
            root.findViewById(R.id.row__xml_tables).setVisibility(View.VISIBLE);
        }

        root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                updateOptions();
                OnImportTypeSelectionDialogResultsListener listener =
                        (OnImportTypeSelectionDialogResultsListener) requireActivity();
                listener.onImportTypeSelectionDialogResult(settings);
                dismiss();
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        TextView coverBlurb = root.findViewById(R.id.cover_images_blurb);
        coverBlurb.setText(R.string.lbl_cover_images);

        if (!archiveHasValidDates()) {
            View radioNewAndUpdatedBooks = root.findViewById(R.id.radioNewAndUpdatedBooks);
            radioNewAndUpdatedBooks.setEnabled(false);
            TextView blurb = root.findViewById(R.id.radioNewAndUpdatedBooksInfo);
            blurb.setText(R.string.import_warning_old_archive);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .setTitle(R.string.import_from_archive)
                .setIcon(R.drawable.ic_warning)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void updateOptions() {
        Dialog dialog = this.getDialog();
        // what to import. All three checked == ImportSettings.IMPORT_ALL
        if (((Checkable) dialog.findViewById(R.id.books_check)).isChecked()) {
            settings.what |= ImportSettings.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.covers_check)).isChecked()) {
            settings.what |= ImportSettings.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.preferences_check)).isChecked()) {
            settings.what |= ImportSettings.PREFERENCES | ImportSettings.BOOK_LIST_STYLES;
        }

        Checkable radioNewAndUpdatedBooks = dialog.findViewById(R.id.radioNewAndUpdatedBooks);
        if (radioNewAndUpdatedBooks.isChecked()) {
            settings.what |= ImportSettings.IMPORT_ONLY_NEW_OR_UPDATED;
        }
    }

    /**
     * read the info block and check if we have valid dates
     */
    private boolean archiveHasValidDates() {
        boolean mArchiveHasValidDates;
        try {
            BackupReader reader = BackupManager.readFrom(settings.file);
            BackupInfo info = reader.getInfo();
            reader.close();
            mArchiveHasValidDates = info.getAppVersionCode() >= 152;
        } catch (IOException e) {
            Logger.error(e);
            mArchiveHasValidDates = false;
        }
        return mArchiveHasValidDates;
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnImportTypeSelectionDialogResultsListener {
        void onImportTypeSelectionDialogResult(final @NonNull ImportSettings settings);
    }

}
