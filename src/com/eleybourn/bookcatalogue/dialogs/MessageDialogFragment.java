package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;

public class MessageDialogFragment extends DialogFragment {
    private static final String TITLE_ID = "titleId";
    private static final String MESSAGE = "message";
    private static final String BUTTON_POSITIVE_TEXT_ID = "buttonPositiveTextId";
    private static final String BUTTON_NEGATIVE_TEXT_ID = "buttonNegativeTextId";
    private static final String BUTTON_NEUTRAL_TEXT_ID = "buttonNeutralTextId";
    private int mDialogId;

    /**
     * A plain title/message/OK dialog
     *
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param titleId  Title to display
     * @param message Message string to display
     *
     * @return Created fragment
     */
    @NonNull
    public static MessageDialogFragment newInstance(final int dialogId,
                                                    @StringRes final int titleId,
                                                    @NonNull final String message) {
        return MessageDialogFragment.newInstance(dialogId, titleId, message, android.R.string.ok, 0, 0);
    }

    /**
     * A full title/message/OK/Cancel/Neutral dialog
     *
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param titleId  Title to display
     *
     * @return Created fragment
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    public static MessageDialogFragment newInstance(final int dialogId,
                                                    @StringRes final int titleId,
                                                    @NonNull final String message,
                                                    @StringRes final int buttonPositiveTextId,
                                                    @StringRes final int buttonNegativeTextId,
                                                    @StringRes final int buttonNeutralTextId) {
        MessageDialogFragment frag = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        args.putInt(TITLE_ID, titleId);
        args.putString(MESSAGE, message);
        args.putInt(BUTTON_POSITIVE_TEXT_ID, buttonPositiveTextId);
        args.putInt(BUTTON_NEGATIVE_TEXT_ID, buttonNegativeTextId);
        args.putInt(BUTTON_NEUTRAL_TEXT_ID, buttonNeutralTextId);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports event
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof OnMessageDialogResultListener))
            throw new RTE.MustImplementException(context, OnMessageDialogResultListener.class);
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        int title = getArguments().getInt(TITLE_ID);
        String msg = getArguments().getString(MESSAGE);
        int btnPos = getArguments().getInt(BUTTON_POSITIVE_TEXT_ID);
        int btnNeg = getArguments().getInt(BUTTON_NEGATIVE_TEXT_ID);
        int btnNeut = getArguments().getInt(BUTTON_NEUTRAL_TEXT_ID);

        AlertDialog dialog = new AlertDialog.Builder(getActivity()).setMessage(msg)
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
            OnMessageDialogResultListener a = (OnMessageDialogResultListener) getActivity();
            if (a != null)
                a.onMessageDialogResult(mDialogId, button);
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
    public interface OnMessageDialogResultListener {
        void onMessageDialogResult(final int dialogId, final int button);
    }
}
