package com.eleybourn.bookcatalogue.dialogs;

public interface AlertDialogListener {

    void onPositiveButton();

    /**
     * Optional
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    default void onNeutralButton() {
        // do nothing
    }

    void onNegativeButton();
}
