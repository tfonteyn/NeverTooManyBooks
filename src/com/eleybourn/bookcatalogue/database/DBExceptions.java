package com.eleybourn.bookcatalogue.database;

import android.support.annotation.Nullable;

class DBExceptions {

    public static class NotFoundException extends RuntimeException {
        NotFoundException(@Nullable final String msg) {
            super(msg);
        }
        NotFoundException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

    static class InsertFailedException extends RuntimeException {
        InsertFailedException() {
            super();
        }

        InsertFailedException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

    static class UpdateFailedException extends RuntimeException {
        UpdateFailedException() {
            super();
        }

        UpdateFailedException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

    static class TransactionException extends RuntimeException {
        TransactionException() {
            super();
        }
        TransactionException(@Nullable final String msg) {
            super(msg);
        }

        TransactionException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }
    static class LockException extends RuntimeException {
        LockException() {
            super();
        }
        LockException(@Nullable final String msg) {
            super(msg);
        }

        LockException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }
}
