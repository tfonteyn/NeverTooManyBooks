package com.eleybourn.bookcatalogue.database.dbsync;

import androidx.annotation.Nullable;

public class TransactionException
        extends RuntimeException {

    private static final long serialVersionUID = -3659088149611399296L;

    public TransactionException() {
        super();
    }

    public TransactionException(@Nullable final String msg) {
        super(msg);
    }

    public TransactionException(@Nullable final String msg,
                                @Nullable final Exception inner) {
        super(msg, inner);
    }
}
