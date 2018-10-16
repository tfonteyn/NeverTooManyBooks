package com.eleybourn.bookcatalogue.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class DBExceptions {

    /**
     * Should NOT be used when the a search MAY fail.
     * Only use when the Search MUST NOT fail as this would indicate an integrity failure
     */
    static class NotFoundException extends RuntimeException {
        NotFoundException(@Nullable final String msg) {
            super(msg);
        }
    }


    /** should only be used from INSIDE a transaction so the caller can rollback */
    static class InsertException extends RuntimeException {
        InsertException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }


    static class UpdateException extends RuntimeException {
        UpdateException() {
            super();
        }
        UpdateException(@Nullable final Exception inner) {
            super(inner);
        }
        UpdateException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

//    // left as a reminder: don't bother, just return a '0' for nothing deleted
//    static class DeleteException extends RuntimeException {
//    }

    public static class ColumnNotPresent extends RuntimeException {
        public ColumnNotPresent(@NonNull final String columnName) {
            super("Column " + columnName + " not present in cursor");
        }

        public ColumnNotPresent(final String columnName, final Throwable cause) {
            super("Column " + columnName + " not present in cursor", cause);
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
        LockException(@Nullable final String msg) {
            super(msg);
        }
        LockException(@SuppressWarnings("SameParameterValue") @Nullable final String msg,
                      @Nullable final Exception inner) {
            super(msg, inner);
        }
    }
}
