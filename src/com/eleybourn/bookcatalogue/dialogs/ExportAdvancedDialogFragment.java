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
import android.widget.Checkable;
import android.widget.RadioButton;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.File;
import java.util.Objects;

public class ExportAdvancedDialogFragment extends DialogFragment {
    private int mDialogId;
    private File mFile;

    /**
     * Constructor
     *
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param file     the file
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportAdvancedDialogFragment newInstance(final int dialogId, @NonNull final File file) {
        final ExportAdvancedDialogFragment frag = new ExportAdvancedDialogFragment();
        final Bundle args = new Bundle();
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
        if (!(context instanceof OnExportTypeSelectionDialogResultListener)) {
            throw new RTE.MustImplementException(context, OnExportTypeSelectionDialogResultListener.class);
        }
    }

    /**
     * Utility routine to set the OnClickListener for a given view to change a checkbox.
     *
     * @param cbId  Checkable view id
     * @param relId Related view id
     */
    private void setRelatedView(@NonNull final View root, @IdRes final int cbId, @IdRes final int relId) {
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
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        //noinspection ConstantConditions
        mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        mFile = new File(Objects.requireNonNull(getArguments().getString(UniqueId.BKEY_FILE_SPEC)));

        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_export_advanced_options, null);

        v.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        v.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v);
            }
        });

        setRelatedView(v, R.id.books_check, R.id.row_all_books);
        setRelatedView(v, R.id.covers_check, R.id.row_covers);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(v)
                .setTitle(R.string.advanced_options)
                .setIcon(R.drawable.ic_help_outline)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

//	private OnClickListener mRowClickListener = new OnClickListener() {
//		@Override
//		public void onClick(View v) {
//			handleClick(v);
//		}};
//
//	/**
//	 * Utility routine to set the OnClickListener for a given view item.
//	 * 
//	 * @param id		Sub-View ID
//	 */
//	private void setOnClickListener(View root, @IdRes int id) {
//		View v = root.findViewById(id);
//		v.setOnClickListener(mRowClickListener);
//		v.setBackgroundResource(android.R.drawable.list_selector_background);
//	}

    private void handleClick(@SuppressWarnings("unused") @NonNull final View view) {
        try {
            OnExportTypeSelectionDialogResultListener listenerActivity = (OnExportTypeSelectionDialogResultListener) getActivity();
            if (listenerActivity != null) {
                ExportSettings settings = createSettings();
                if (settings != null) {
                    listenerActivity.onExportTypeSelectionDialogResult(mDialogId, this, settings);
                    dismiss();
                }
            } else {
                dismiss();
            }
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    @Nullable
    private ExportSettings createSettings() {
        final ExportSettings settings = new ExportSettings();

        settings.file = mFile;
        settings.options = 0;
        settings.dateFrom = null;

        Dialog dialog = this.getDialog();
        if (((Checkable) dialog.findViewById(R.id.books_check)).isChecked()) {
            settings.options |= Exporter.EXPORT_DETAILS;
        }
        if (((Checkable) dialog.findViewById(R.id.covers_check)).isChecked()) {
            settings.options |= Exporter.EXPORT_COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.preferences_check)).isChecked()) {
            settings.options |= Exporter.EXPORT_PREFERENCES | Exporter.EXPORT_STYLES;
        }

        if (((RadioButton) dialog.findViewById(R.id.radioSinceLast)).isChecked()) {
            settings.options |= Exporter.EXPORT_SINCE;
            settings.dateFrom = null;
        } else if (((RadioButton) dialog.findViewById(R.id.radioSinceDate)).isChecked()) {
            View v = dialog.findViewById(R.id.txtDate);
            try {
                settings.options |= Exporter.EXPORT_SINCE;
                settings.dateFrom = DateUtils.parseDate(v.toString());
            } catch (Exception e) {
                //Snackbar.make(v, R.string.no_date, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showBriefMessage(requireActivity(), R.string.no_date);
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
    public interface OnExportAdvancedDialogResultListener {
        public void onExportAdvancedDialogResult(final int dialogId,
                                                 @NonNull final ExportAdvancedDialogFragment dialog,
                                                 final int rowId,
                                                 @NonNull final File file);
    }
}
