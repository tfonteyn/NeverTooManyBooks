package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Checkable;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Objects;

public class ExportDialogFragment
        extends DialogFragment {

    private final ExportSettings settings = new ExportSettings();

    /**
     * Constructor.
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportDialogFragment newInstance(@NonNull final ExportSettings settings) {
        final ExportDialogFragment frag = new ExportDialogFragment();
        final Bundle args = new Bundle();
        //noinspection ConstantConditions
        args.putString(UniqueId.BKEY_FILE_SPEC, settings.file.getAbsolutePath());
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
        if (!(context instanceof OnExportTypeSelectionDialogResultsListener)) {
            throw new RTE.MustImplementException(context,
                                                 OnExportTypeSelectionDialogResultsListener.class);
        }
    }

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // savedInstanceState not used.
        Bundle args = getArguments();
        //noinspection ConstantConditions
        settings.file = new File(Objects.requireNonNull(args.getString(UniqueId.BKEY_FILE_SPEC)));

        View root = requireActivity().getLayoutInflater()
                                     .inflate(R.layout.dialog_export_options, null);

        root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                updateOptions();
                OnExportTypeSelectionDialogResultsListener listener =
                        (OnExportTypeSelectionDialogResultsListener) requireActivity();
                listener.onExportTypeSelectionDialogResult(settings);
                dismiss();
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dismiss();
            }
        });

        root.findViewById(R.id.xml_tables_check).setVisibility(View.VISIBLE);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .setTitle(R.string.lbl_backup)
                .setIcon(R.drawable.ic_warning)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void updateOptions() {
        Dialog dialog = getDialog();
        // what to export. All checked == ExportSettings.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.xml_tables_check)).isChecked()) {
            settings.what |= ExportSettings.XML_TABLES;
        }
        if (((Checkable) dialog.findViewById(R.id.books_check)).isChecked()) {
            settings.what |= ExportSettings.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.covers_check)).isChecked()) {
            settings.what |= ExportSettings.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.preferences_check)).isChecked()) {
            settings.what |= ExportSettings.PREFERENCES | ExportSettings.BOOK_LIST_STYLES;
        }

        Checkable radioSinceLastBackup = dialog.findViewById(R.id.radioSinceLastBackup);
        Checkable radioSinceDate = dialog.findViewById(R.id.radioSinceDate);

        if (radioSinceLastBackup.isChecked()) {
            settings.what |= ExportSettings.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            settings.dateFrom = null;

        } else if (radioSinceDate.isChecked()) {
            EditText dateSinceView = dialog.findViewById(R.id.txtDate);
            try {
                settings.what |= ExportSettings.EXPORT_SINCE;
                settings.dateFrom = DateUtils.parseDate(dateSinceView.getText().toString().trim());
            } catch (RuntimeException e) {
                //Snackbar.make(v, R.string.no_date, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showUserMessage(requireActivity(), R.string.warning_date_not_set);
                settings.what = ExportSettings.NOTHING;
            }
        }
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface OnExportTypeSelectionDialogResultsListener {

        void onExportTypeSelectionDialogResult(@NonNull ExportSettings settings);
    }
}
