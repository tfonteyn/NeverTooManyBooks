package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.Objects;

/**
 * A plain AlertDialog but nicer
 */
public class MessageDialogFragment extends DialogFragment {
    private static final String MESSAGE = "message";
    private static final String BUTTON_POSITIVE_TEXT_ID = "buttonPositiveTextId";
    private static final String BUTTON_NEGATIVE_TEXT_ID = "buttonNegativeTextId";
    private static final String BUTTON_NEUTRAL_TEXT_ID = "buttonNeutralTextId";
    private int mCallerId;

    /**
     * A plain title/message/OK dialog
     *
     * @param callerId ID passed by caller. Can be 0, will be passed back in event
     * @param titleId  Title string resource to display
     * @param message Message string to display
     *
     * @return Created fragment
     */
    @NonNull
    public static MessageDialogFragment newInstance(final int callerId,
                                                    final @StringRes int titleId,
                                                    final @NonNull String message) {
        return MessageDialogFragment.newInstance(callerId, titleId, message, android.R.string.ok, 0, 0);
    }

    /**
     * A full title/message/OK/Cancel/Neutral dialog
     *
     * @param callerId ID passed by caller. Can be 0, will be passed back in event
     * @param titleId  Title to display
     *
     * @return Created fragment
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    public static MessageDialogFragment newInstance(final int callerId,
                                                    final @StringRes int titleId,
                                                    final @NonNull String message,
                                                    final @StringRes int buttonPositiveTextId,
                                                    final @StringRes int buttonNegativeTextId,
                                                    final @StringRes int buttonNeutralTextId) {
        MessageDialogFragment frag = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_CALLER_ID, callerId);
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, titleId);
        args.putString(MESSAGE, message);
        args.putInt(BUTTON_POSITIVE_TEXT_ID, buttonPositiveTextId);
        args.putInt(BUTTON_NEGATIVE_TEXT_ID, buttonNegativeTextId);
        args.putInt(BUTTON_NEUTRAL_TEXT_ID, buttonNeutralTextId);
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
        if (!(context instanceof OnMessageDialogResultsListener))
            throw new RTE.MustImplementException(context, OnMessageDialogResultsListener.class);
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        Objects.requireNonNull(args);
        mCallerId = args.getInt(UniqueId.BKEY_CALLER_ID);
        @StringRes
        int title = args.getInt(UniqueId.BKEY_DIALOG_TITLE);
        @StringRes
        int btnPos = args.getInt(BUTTON_POSITIVE_TEXT_ID, android.R.string.ok);
        @StringRes
        int btnNeg = args.getInt(BUTTON_NEGATIVE_TEXT_ID);
        @StringRes
        int btnNeut = args.getInt(BUTTON_NEUTRAL_TEXT_ID);

        String msg = args.getString(MESSAGE);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity()).setMessage(msg)
                .setTitle(title)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getString(btnPos),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        handleButton(AlertDialog.BUTTON_POSITIVE);
                    }
                });

        if (btnNeg != 0) {
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                    getString(btnNeg),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            handleButton(AlertDialog.BUTTON_NEGATIVE);
                        }
                    });
        }
        if (btnNeut != 0) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                    getString(btnNeut),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            handleButton(AlertDialog.BUTTON_NEUTRAL);
                        }
                    });
        }

        return dialog;
    }

    private void handleButton(final int button) {
        try {
            OnMessageDialogResultsListener listenerActivity = (OnMessageDialogResultsListener) getActivity();
            if (listenerActivity != null)
                listenerActivity.onMessageDialogResult(mCallerId, button);
        } catch (Exception e) {
            Logger.error(e);
        }
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnMessageDialogResultsListener {
        void onMessageDialogResult(final int callerId, final int button);
    }
}
