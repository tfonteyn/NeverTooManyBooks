package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Checkable;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.debug.MustImplementException;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public class ExportOptionsDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    private static final String TAG = ExportOptionsDialogFragment.class.getSimpleName();

    private ExportSettings mExportSettings;

    /**
     * (syntax sugar for newInstance)
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final ExportSettings settings) {
        if (fm.findFragmentByTag(TAG) == null) {
            newInstance(settings).show(fm, TAG);
        }
    }

    /**
     * Constructor.
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportOptionsDialogFragment newInstance(@NonNull final ExportSettings settings) {
        ExportOptionsDialogFragment frag = new ExportOptionsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS, settings);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mExportSettings = args.getParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS);

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_options, null);

        //noinspection ConstantConditions
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .setTitle(R.string.export_options_dialog_title)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    updateOptions();
                    OnOptionsListener.onOptionsSet(this, mExportSettings);
                })
                .create();

        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void updateOptions() {
        Dialog dialog = getDialog();
        // what to export. All checked == ExportSettings.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.cbx_xml_tables)).isChecked()) {
            mExportSettings.what |= ExportSettings.XML_TABLES;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_books_csv)).isChecked()) {
            mExportSettings.what |= ExportSettings.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_covers)).isChecked()) {
            mExportSettings.what |= ExportSettings.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_preferences)).isChecked()) {
            mExportSettings.what |= ExportSettings.PREFERENCES | ExportSettings.BOOK_LIST_STYLES;
        }

        Checkable radioSinceLastBackup = dialog.findViewById(R.id.radioSinceLastBackup);
        Checkable radioSinceDate = dialog.findViewById(R.id.radioSinceDate);

        if (radioSinceLastBackup.isChecked()) {
            mExportSettings.what |= ExportSettings.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            mExportSettings.dateFrom = null;

        } else if (radioSinceDate.isChecked()) {
            EditText dateSinceView = dialog.findViewById(R.id.txtDate);
            try {
                mExportSettings.what |= ExportSettings.EXPORT_SINCE;
                mExportSettings.dateFrom =
                        DateUtils.parseDate(dateSinceView.getText().toString().trim());
            } catch (RuntimeException e) {
                UserMessage.showUserMessage(dateSinceView, R.string.warning_date_not_set);
                mExportSettings.what = ExportSettings.NOTHING;
            }
        }
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS, mExportSettings);
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface OnOptionsListener {

        /**
         *  BAD idea... to be removed
         *
         * Convenience method. Try in order:
         * <ul>
         * <li>getTargetFragment()</li>
         * <li>getParentFragment()</li>
         * <li>getActivity()</li>
         * </ul>
         */
        static void onOptionsSet(@NonNull final Fragment sourceFragment,
                                 @NonNull final ExportSettings settings) {

            if (sourceFragment.getTargetFragment() instanceof OnOptionsListener) {
                ((OnOptionsListener) sourceFragment.getTargetFragment())
                        .onOptionsSet(settings);
            } else if (sourceFragment.getParentFragment() instanceof OnOptionsListener) {
                ((OnOptionsListener) sourceFragment.getParentFragment())
                        .onOptionsSet(settings);
            } else if (sourceFragment.getActivity() instanceof OnOptionsListener) {
                ((OnOptionsListener) sourceFragment.getActivity())
                        .onOptionsSet(settings);
            } else {
                throw new MustImplementException(OnOptionsListener.class);
            }
        }

        void onOptionsSet(@NonNull ExportSettings settings);
    }
}
