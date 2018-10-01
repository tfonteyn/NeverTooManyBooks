package com.eleybourn.bookcatalogue.database;

import android.support.annotation.Nullable;

class DBExceptions {

    /**
     * Should NOT be used when the a search MAY fail.
     * Only use when the Search MUST NOT fail
     */
    public static class NotFoundException extends RuntimeException {
        NotFoundException(@Nullable final String msg) {
            super(msg);
        }
        NotFoundException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }


    /** should only be used from INSIDE a transaction so the caller can rollback */
    static class InsertFailedException extends RuntimeException {
        InsertFailedException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }


    static class UpdateFailedException extends RuntimeException {
        UpdateFailedException() {
            super();
        }
        UpdateFailedException(@Nullable final String msg) {
            super(msg);
        }
        UpdateFailedException(@Nullable final Exception inner) {
            super(inner);
        }
        UpdateFailedException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

//    // left as a reminder: don't bother, just return a '0' for nothing deleted
//    static class DeleteFailedException extends RuntimeException {
//        DeleteFailedException() {
//            super();
//        }
//        DeleteFailedException(@Nullable final Exception inner) {
//            super(inner);
//        }
//        DeleteFailedException(@Nullable final String msg, @Nullable final Exception inner) {
//            super(msg, inner);
//        }
//    }

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

        LockException(@Nullable final String msg) {
            super(msg);
        }

        LockException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }
}
