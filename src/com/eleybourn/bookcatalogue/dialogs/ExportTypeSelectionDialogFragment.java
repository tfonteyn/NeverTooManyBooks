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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Date;
import java.util.Objects;

/**
 * Layout clickable items:
 *
 * R.id.all_books_row
 * R.id.advanced_options_row
 */
public class ExportTypeSelectionDialogFragment extends DialogFragment {
    private int mDialogId;
    private File mFile;
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
    public static ExportTypeSelectionDialogFragment newInstance(int dialogId, File file) {
        ExportTypeSelectionDialogFragment frag = new ExportTypeSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        args.putString(UniqueId.BKEY_FILE_SPEC, file.getAbsolutePath());
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnExportTypeSelectionDialogResultListener))
            throw new RTE.MustImplementException(context, OnExportTypeSelectionDialogResultListener.class);
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

        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_export_type_selection, null);

        setOnClickListener(v, R.id.row_all_books);
        setOnClickListener(v, R.id.row_advanced_options);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(v)
                .setTitle(R.string.backup_to_archive)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void handleClick(@NonNull final View v) {
        if (v.getId() == R.id.row_advanced_options) {
            ExportAdvancedDialogFragment frag = ExportAdvancedDialogFragment.newInstance(1, mFile);
            frag.show(requireActivity().getSupportFragmentManager(), null);
        } else {
            OnExportTypeSelectionDialogResultListener activity = (OnExportTypeSelectionDialogResultListener) requireActivity();
            ExportSettings settings = new ExportSettings();
            settings.file = mFile;
            settings.options = Exporter.EXPORT_ALL;
            activity.onExportTypeSelectionDialogResult(mDialogId, this, settings);
        }
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnExportTypeSelectionDialogResultListener {
        void onExportTypeSelectionDialogResult(final int dialogId,
                                               @NonNull final DialogFragment dialog,
                                               @NonNull final ExportSettings settings);
    }

    public static class ExportSettings {
        public File file;
        public int options;
        @Nullable
        public Date dateFrom;
    }

}
