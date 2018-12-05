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
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Objects;

public class ExportDialogFragment extends DialogFragment {

    private final ExportSettings settings = new ExportSettings();

    /**
     * Constructor
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportDialogFragment newInstance(final @NonNull ExportSettings settings) {
        final ExportDialogFragment frag = new ExportDialogFragment();
        final Bundle args = new Bundle();
        //noinspection ConstantConditions
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
        settings.file = new File(Objects.requireNonNull(args.getString(UniqueId.BKEY_FILE_SPEC)));

        View root = requireActivity().getLayoutInflater().inflate(R.layout.dialog_export_options, null);

        root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOptions();
                OnExportTypeSelectionDialogResultsListener listener =
                        (OnExportTypeSelectionDialogResultsListener) requireActivity();
                listener.onExportTypeSelectionDialogResult(settings);
                dismiss();
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

//        setRelatedView(root, R.id.books_check, R.id.row_all_books);
//        setRelatedView(root, R.id.covers_check, R.id.row_covers);

        TextView coverBlurb = root.findViewById(R.id.cover_images_blurb);
        coverBlurb.setText(R.string.export_info_cover_images);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .setTitle(R.string.backup_to_archive)
                .setIcon(R.drawable.ic_warning)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void updateOptions() {
        Dialog dialog = this.getDialog();
        // what to export. All three checked == ExportSettings.EXPORT_ALL
        if (((Checkable) dialog.findViewById(R.id.books_check)).isChecked()) {
            settings.what |= ExportSettings.BOOK_DATA;
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
            EditText v = dialog.findViewById(R.id.txtDate);
            try {
                settings.what |= ExportSettings.EXPORT_SINCE;
                settings.dateFrom = DateUtils.parseDate(v.getText().toString().trim());
            } catch (Exception e) {
                //Snackbar.make(v, R.string.no_date, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showUserMessage(requireActivity(), R.string.no_date);
                settings.what = ExportSettings.NOTHING;
            }
        }
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
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnExportTypeSelectionDialogResultsListener {
        void onExportTypeSelectionDialogResult(final @NonNull ExportSettings settings);
    }
}
