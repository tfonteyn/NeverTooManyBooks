package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;
import java.util.Objects;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.MustImplementException;

public class ImportDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = ImportDialogFragment.class.getSimpleName();

    private ImportSettings mImportSettings;

    private FragmentActivity mActivity;

    /**
     * Constructor.
     *
     * @param settings import configuration settings
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportDialogFragment newInstance(@NonNull final ImportSettings settings) {
        ImportDialogFragment frag = new ImportDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS, settings);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface.
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnImportTypeSelectionDialogResultsListener)) {
            throw new MustImplementException(context,
                                             OnImportTypeSelectionDialogResultsListener.class);
        }
    }

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mActivity = requireActivity();
        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mImportSettings = args.getParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS);

        View root = mActivity.getLayoutInflater().inflate(R.layout.dialog_import_options, null);

        root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                updateOptions();
                OnImportTypeSelectionDialogResultsListener listener =
                        (OnImportTypeSelectionDialogResultsListener) mActivity;
                listener.onImportTypeSelectionDialogResult(mImportSettings);
                dismiss();
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dismiss();
            }
        });

        if (!archiveHasValidDates()) {
            View radioNewAndUpdatedBooks = root.findViewById(R.id.radioNewAndUpdatedBooks);
            radioNewAndUpdatedBooks.setEnabled(false);
            TextView blurb = root.findViewById(R.id.radioNewAndUpdatedBooksInfo);
            blurb.setText(R.string.import_warning_old_archive);
        }

        AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.lbl_import_from_archive)
                .setIcon(R.drawable.ic_warning)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void updateOptions() {
        Dialog dialog = getDialog();
        // what to import. All three checked == ImportSettings.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.books_check)).isChecked()) {
            mImportSettings.what |= ImportSettings.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.covers_check)).isChecked()) {
            mImportSettings.what |= ImportSettings.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.preferences_check)).isChecked()) {
            mImportSettings.what |= ImportSettings.PREFERENCES | ImportSettings.BOOK_LIST_STYLES;
        }

        Checkable radioNewAndUpdatedBooks = dialog.findViewById(R.id.radioNewAndUpdatedBooks);
        if (radioNewAndUpdatedBooks.isChecked()) {
            mImportSettings.what |= ImportSettings.IMPORT_ONLY_NEW_OR_UPDATED;
        }
    }

    /**
     * read the info block and check if we have valid dates.
     */
    private boolean archiveHasValidDates() {
        boolean mArchiveHasValidDates;
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.readFrom(getContext(), mImportSettings.file)) {
            Objects.requireNonNull(reader);
            BackupInfo info = reader.getInfo();
            reader.close();
            mArchiveHasValidDates = info.getAppVersionCode() >= 152;
        } catch (IOException e) {
            Logger.error(e);
            mArchiveHasValidDates = false;
        }
        return mArchiveHasValidDates;
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS, mImportSettings);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnImportTypeSelectionDialogResultsListener {

        void onImportTypeSelectionDialogResult(@NonNull ImportSettings settings);
    }

}
