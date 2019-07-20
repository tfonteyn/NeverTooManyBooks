package com.eleybourn.bookcatalogue.database.dbsync;

import androidx.annotation.Nullable;

public class TransactionException
        extends RuntimeException {

    private static final long serialVersionUID = -3659088149611399296L;

    public TransactionException() {
    }

    public TransactionException(@Nullable final String message) {
        super(message);
    }

    public TransactionException(@Nullable final String message,
                                @Nullable final Exception cause) {
        super(message, cause);
    }
}
