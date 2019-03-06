package com.eleybourn.bookcatalogue.dialogs;

public interface AlertDialogListener {

    void onPositiveButton();

    @SuppressWarnings({"unused", "EmptyMethod"})
    void onNeutralButton();

    void onNegativeButton();
}
