package com.hardbacknutter.nevertomanybooks.backup.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.io.IOException;
import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertomanybooks.debug.Logger;

public class ImportOptionsDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "ImportOptionsDialogFragment";

    private ImportOptions mOptions;

    private WeakReference<OptionsListener> mListener;

    /**
     * Constructor.
     *
     * @param options import configuration
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportOptionsDialogFragment newInstance(@NonNull final ImportOptions options) {
        ImportOptionsDialogFragment frag = new ImportOptionsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS, options);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mOptions = args.getParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS);
    }

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_options, null);

        if (!archiveHasValidDates()) {
            View radioNewAndUpdatedBooks = root.findViewById(R.id.radioNewAndUpdatedBooks);
            radioNewAndUpdatedBooks.setEnabled(false);
            TextView blurb = root.findViewById(R.id.radioNewAndUpdatedBooksInfo);
            blurb.setText(R.string.import_warning_old_archive);
        }

        //noinspection ConstantConditions
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .setTitle(R.string.import_options_dialog_title)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    updateOptions();
                    if (mListener.get() != null) {
                        mListener.get().onOptionsSet(mOptions);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Logger.debug(this, "onOptionsSet",
                                         Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
                        }
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    public void setListener(@NonNull final OptionsListener listener) {
        mListener = new WeakReference<>(listener);
    }

    private void updateOptions() {
        Dialog dialog = getDialog();
        // what to import. All three checked == ImportOptions.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.cbx_books_csv)).isChecked()) {
            mOptions.what |= ImportOptions.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_covers)).isChecked()) {
            mOptions.what |= ImportOptions.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_preferences)).isChecked()) {
            mOptions.what |= ImportOptions.PREFERENCES | ImportOptions.BOOK_LIST_STYLES;
        }

        Checkable radioNewAndUpdatedBooks = dialog.findViewById(R.id.radioNewAndUpdatedBooks);
        if (radioNewAndUpdatedBooks.isChecked()) {
            mOptions.what |= ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED;
        }
    }

    /**
     * read the info block and check if we have valid dates.
     */
    private boolean archiveHasValidDates() {
        boolean hasValidDates;
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.getReader(getContext(), mOptions.file)) {
            BackupInfo info = reader.getInfo();
            reader.close();
            hasValidDates = info.getAppVersionCode() >= 152;
        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            hasValidDates = false;
        }
        return hasValidDates;
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS, mOptions);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OptionsListener {

        void onOptionsSet(@NonNull ImportOptions options);
    }
}
