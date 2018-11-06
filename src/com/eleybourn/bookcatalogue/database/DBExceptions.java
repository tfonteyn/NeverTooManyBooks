package com.eleybourn.bookcatalogue.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class DBExceptions {

    /**
     * Should NOT be used when the a search MAY fail.
     * Only use when the Search MUST NOT fail as this would indicate an integrity error
     */
    static class NotFoundException extends RuntimeException {
        NotFoundException(final @Nullable String msg) {
            super(msg);
        }
    }


    /** should only be used from INSIDE a transaction so the caller can rollback */
    static class InsertException extends RuntimeException {
        InsertException(final @Nullable String msg, final @Nullable Exception inner) {
            super(msg, inner);
        }
    }


    static class UpdateException extends RuntimeException {
        UpdateException() {
            super();
        }
        UpdateException(final @Nullable Exception inner) {
            super(inner);
        }
        UpdateException(final @Nullable String msg, final @Nullable Exception inner) {
            super(msg, inner);
        }
    }

//    // left as a reminder: don't bother, just return a '0' for nothing deleted
//    static class DeleteException extends RuntimeException {
//    }

    public static class ColumnNotPresent extends RuntimeException {
        public ColumnNotPresent(final @NonNull String columnName) {
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
        TransactionException(final @Nullable String msg) {
            super(msg);
        }
        TransactionException(final @Nullable String msg, final @Nullable Exception inner) {
            super(msg, inner);
        }
    }
    static class LockException extends RuntimeException {
        LockException(final @Nullable String msg) {
            super(msg);
        }
        LockException(@SuppressWarnings("SameParameterValue") final @Nullable String msg,
                      final @Nullable Exception inner) {
            super(msg, inner);
        }
    }
}
