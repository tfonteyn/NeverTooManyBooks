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

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public class ExportOptionsDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "ExportOptionsDialogFragment";

    private ExportOptions mOptions;

    private WeakReference<OptionsListener> mListener;

    /**
     * Constructor.
     *
     * @param options export configuration
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportOptionsDialogFragment newInstance(@NonNull final ExportOptions options) {
        ExportOptionsDialogFragment frag = new ExportOptionsDialogFragment();
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
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_options, null);

        //noinspection ConstantConditions
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .setTitle(R.string.export_options_dialog_title)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    updateOptions();
                    if (mListener.get() != null) {
                        mListener.get().onOptionsSet(mOptions);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Logger.debug(this, "onOptionsSet",
                                         "WeakReference to listener was dead");
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
        // what to export. All checked == ExportOptions.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.cbx_xml_tables)).isChecked()) {
            mOptions.what |= ExportOptions.XML_TABLES;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_books_csv)).isChecked()) {
            mOptions.what |= ExportOptions.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_covers)).isChecked()) {
            mOptions.what |= ExportOptions.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_preferences)).isChecked()) {
            mOptions.what |= ExportOptions.PREFERENCES | ExportOptions.BOOK_LIST_STYLES;
        }

        Checkable radioSinceLastBackup = dialog.findViewById(R.id.radioSinceLastBackup);
        Checkable radioSinceDate = dialog.findViewById(R.id.radioSinceDate);

        if (radioSinceLastBackup.isChecked()) {
            mOptions.what |= ExportOptions.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            mOptions.dateFrom = null;

        } else if (radioSinceDate.isChecked()) {
            EditText dateSinceView = dialog.findViewById(R.id.txtDate);
            try {
                mOptions.what |= ExportOptions.EXPORT_SINCE;
                mOptions.dateFrom =
                        DateUtils.parseDate(dateSinceView.getText().toString().trim());
            } catch (@NonNull final RuntimeException e) {
                UserMessage.show(dateSinceView, R.string.hint_date_not_set_with_brackets);
                mOptions.what = ExportOptions.NOTHING;
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
        outState.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS, mOptions);
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface OptionsListener {

        void onOptionsSet(@NonNull ExportOptions options);
    }
}
