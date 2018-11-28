package com.eleybourn.bookcatalogue.backup.ui;

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
import android.widget.Checkable;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ui.ExportSettingsDialogFragment.OnExportTypeSelectionDialogResultsListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Objects;

/**
 * Generic advanced {@link ExportSettings} dialog.
 */
public class ExportAdvancedSettingsDialogFragment extends DialogFragment {
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
    public static ExportAdvancedSettingsDialogFragment newInstance(final int callerId,
                                                                   final @NonNull File file) {
        final ExportAdvancedSettingsDialogFragment frag = new ExportAdvancedSettingsDialogFragment();
        final Bundle args = new Bundle();
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
        if (!(context instanceof OnExportTypeSelectionDialogResultsListener)) {
            throw new RTE.MustImplementException(context, OnExportTypeSelectionDialogResultsListener.class);
        }
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

        View root = requireActivity().getLayoutInflater().inflate(R.layout.dialog_export_advanced_options, null);

        root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    OnExportTypeSelectionDialogResultsListener listener =
                            (OnExportTypeSelectionDialogResultsListener) requireActivity();
                    ExportSettings settings = createSettings();
                    if (settings != null) {
                        listener.onExportTypeSelectionDialogResult(ExportAdvancedSettingsDialogFragment.this,
                                mCallerId, settings);
                    }
                    dismiss();
                } catch (Exception e) {
                    Logger.error(e);
                }
            }
        });
        root.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        setRelatedView(root, R.id.books_check, R.id.row_all_books);
        setRelatedView(root, R.id.covers_check, R.id.row_covers);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .setTitle(R.string.lbl_advanced_options)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Utility routine to set the OnClickListener for a given view to change a checkbox.
     *
     * @param cbId  Checkable view id
     * @param relId Related view id
     */
    private void setRelatedView(final @NonNull View root, final @IdRes int cbId, final @IdRes int relId) {
        final Checkable cb = root.findViewById(cbId);
        final View rel = root.findViewById(relId);
        rel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cb.setChecked(!cb.isChecked());
            }
        });
    }

    /**
     *
     * @return the settings, or null if there was a conflict
     */
    @Nullable
    private ExportSettings createSettings() {
        final ExportSettings settings = new ExportSettings(mFile);

        Dialog dialog = this.getDialog();
        if (((Checkable) dialog.findViewById(R.id.books_check)).isChecked()) {
            settings.options |= ExportSettings.BOOK_DATA;
        }
        if (((Checkable) dialog.findViewById(R.id.covers_check)).isChecked()) {
            settings.options |= ExportSettings.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.preferences_check)).isChecked()) {
            settings.options |= ExportSettings.PREFERENCES | ExportSettings.BOOK_LIST_STYLES;
        }

        Checkable radioSinceLastBackup = dialog.findViewById(R.id.radioSinceLast);
        Checkable radioSinceDate = dialog.findViewById(R.id.radioSinceDate);

        if (radioSinceLastBackup.isChecked()) {
            settings.options |= ExportSettings.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            settings.dateFrom = null;

        } else if (radioSinceDate.isChecked()) {
            EditText v = dialog.findViewById(R.id.txtDate);
            try {
                settings.options |= ExportSettings.EXPORT_SINCE;
                settings.dateFrom = DateUtils.parseDate(v.getText().toString().trim());
            } catch (Exception e) {
                //Snackbar.make(v, R.string.no_date, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showUserMessage(requireActivity(), R.string.no_date);
                return null;
            }
        }

        return settings;
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    @SuppressWarnings("unused")
    public interface OnArchiveImportAdvancedOptionsDialogResultsListener {
        void onArchiveImportAdvancedOptionsDialogResults(final @NonNull ExportAdvancedSettingsDialogFragment dialog,
                                                         final int callerId,
                                                         final int rowId,
                                                         final @NonNull File file);
    }
}
