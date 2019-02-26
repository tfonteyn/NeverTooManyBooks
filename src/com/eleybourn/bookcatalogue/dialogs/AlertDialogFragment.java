package com.eleybourn.bookcatalogue.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

/**
 * Fragment wrapper for an AlertDialog with simple OK/Cancel.
 */
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
        Bundle args = requireArguments();
        final int title = args.getInt(UniqueId.BKEY_DIALOG_TITLE);
        String message;
        if (args.containsKey(UniqueId.BKEY_DIALOG_MSG_ID)) {
            message = requireActivity().getString(args.getInt(UniqueId.BKEY_DIALOG_MSG_ID));
        } else {
            message = args.getString(UniqueId.BKEY_DIALOG_MSG);
        }

        return new AlertDialog.Builder(getActivity())
                .setIcon(R.drawable.ic_warning)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok,
                                   new DialogInterface.OnClickListener() {
                                       public void onClick(@NonNull final DialogInterface dialog,
                                                           final int which) {
                                           ((AlertDialogListener) requireActivity())
                                                   .onPositiveButton();
                                       }
                                   }
                )
                .setNegativeButton(android.R.string.cancel,
                                   new DialogInterface.OnClickListener() {
                                       public void onClick(@NonNull final DialogInterface dialog,
                                                           final int which) {
                                           ((AlertDialogListener) requireActivity())
                                                   .onNegativeButton();
                                       }
                                   }
                )
                .create();
    }

}
