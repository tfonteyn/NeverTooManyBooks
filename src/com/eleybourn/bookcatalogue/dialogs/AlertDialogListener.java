package com.eleybourn.bookcatalogue.dialogs;

public interface AlertDialogListener {

    void onPositiveButton();

    @SuppressWarnings("unused")
    void onNeutralButton();

    void onNegativeButton();
}
