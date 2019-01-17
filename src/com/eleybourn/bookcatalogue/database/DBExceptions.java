package com.eleybourn.bookcatalogue.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DBExceptions {

    /**
     * Should NOT be used when the a search MAY fail.
     * Only use when the Search MUST NOT fail as this would indicate an integrity error
     */
    static class NotFoundException
            extends RuntimeException {

        private static final long serialVersionUID = 7073473935257952862L;

        NotFoundException(@Nullable final String msg) {
            super(msg);
        }
    }


    /** should only be used from INSIDE a transaction so the caller can rollback. */
    static class InsertException
            extends RuntimeException {

        private static final long serialVersionUID = 7393004442420653364L;

        InsertException(@Nullable final String msg,
                        @Nullable final Exception inner) {
            super(msg, inner);
        }
    }


    public static class UpdateException
            extends RuntimeException {

        private static final long serialVersionUID = 4134182226987220133L;

        UpdateException() {
            super();
        }

        UpdateException(@Nullable final Exception inner) {
            super(inner);
        }

        UpdateException(@Nullable final String msg,
                        @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

//    // left as a reminder: don't bother, just return a '0' for nothing deleted.
//    static class DeleteException extends RuntimeException {
//    }

    public static class ColumnNotPresent
            extends RuntimeException {

        private static final long serialVersionUID = -5065796313450875326L;

        public ColumnNotPresent(@NonNull final String columnName) {
            super("Column " + columnName + " not present in cursor");
        }

        public ColumnNotPresent(@NonNull final String columnName,
                                @NonNull final Throwable cause) {
            super("Column " + columnName + " not present in cursor", cause);
        }
    }

    public static class TransactionException
            extends RuntimeException {

        private static final long serialVersionUID = -3659088149611399296L;

        TransactionException() {
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
}
