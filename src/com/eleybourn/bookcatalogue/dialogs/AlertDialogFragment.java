package com.eleybourn.bookcatalogue.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

public class AlertDialogFragment
        extends DialogFragment {

    /**
     * @param title   resource id
     * @param message resource id
     *
     * @return new instance
     */
    public static AlertDialogFragment newInstance(@StringRes final int title,
                                                  @StringRes final int message) {
        AlertDialogFragment frag = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, title);
        args.putInt(UniqueId.BKEY_DIALOG_MSG_ID, message);
        frag.setArguments(args);
        return frag;
    }

    /**
     * @param title   resource id
     * @param message string
     *
     * @return new instance
     */
    public static AlertDialogFragment newInstance(@StringRes final int title,
                                                  @NonNull final String message) {
        AlertDialogFragment frag = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, title);
        args.putString(UniqueId.BKEY_DIALOG_MSG, message);
        frag.setArguments(args);
        return frag;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        @SuppressWarnings("ConstantConditions")
        @NonNull
        AlertDialogListener listener = (AlertDialogListener) getActivity();

        @StringRes
        int titleId = 0;
        String message = null;

        Bundle args = getArguments();
        if (args != null) {
            titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE);
            if (args.containsKey(UniqueId.BKEY_DIALOG_MSG_ID)) {
                message = getString(args.getInt(UniqueId.BKEY_DIALOG_MSG_ID));
            } else {
                message = args.getString(UniqueId.BKEY_DIALOG_MSG);
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setIcon(R.drawable.ic_warning)
                .setNegativeButton(android.R.string.cancel, (d, w) -> listener.onNegativeButton())
                .setNeutralButton(android.R.string.untitled, (d, w) -> listener.onNeutralButton())
                .setPositiveButton(android.R.string.ok, (d, w) -> listener.onPositiveButton())
                .create();

        if (titleId != 0) {
            dialog.setTitle(titleId);
        }
        if (message != null && !message.isEmpty()) {
            dialog.setMessage(message);
        }
        return dialog;
    }

    /**
     * A simple interface to combine 3 callbacks in one easy to pass object with optional
     * negative/neutral implementations.
     */
    public interface AlertDialogListener {

        void onPositiveButton();

        default void onNeutralButton() {
            // do nothing
        }

        default void onNegativeButton() {
            // do nothing
        }
    }
}
