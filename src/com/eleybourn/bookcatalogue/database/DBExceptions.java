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
        NotFoundException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
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
        UpdateException(@Nullable final String msg) {
            super(msg);
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
//        DeleteException() {
//            super();
//        }
//        DeleteException(@Nullable final Exception inner) {
//            super(inner);
//        }
//        DeleteException(@Nullable final String msg, @Nullable final Exception inner) {
//            super(msg, inner);
//        }
//    }

    public static class ColumnNotPresent extends RuntimeException {
        /**
         * Constructs a new runtime exception with the specified detail message.
         * The cause is not initialized, and may subsequently be initialized by a
         * call to {@link #initCause}.
         *
         * @param columnName the detail message. The detail message is saved for
         *                later retrieval by the {@link #getMessage()} method.
         */
        public ColumnNotPresent(@NonNull final String columnName) {
            super("Column " + columnName + " not present in cursor");
        }

        /**
         * Constructs a new runtime exception with the specified detail message and
         * cause.  <p>Note that the detail message associated with
         * {@code cause} is <i>not</i> automatically incorporated in
         * this runtime exception's detail message.
         *
         * @param columnName the detail message (which is saved for later retrieval
         *                by the {@link #getMessage()} method).
         * @param cause   the cause (which is saved for later retrieval by the
         *                {@link #getCause()} method).  (A <tt>null</tt> value is
         *                permitted, and indicates that the cause is nonexistent or
         *                unknown.)
         *
         * @since 1.4
         */
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
        LockException(@Nullable final String msg, @Nullable final Exception inner) {
            super(msg, inner);
        }
    }
}
