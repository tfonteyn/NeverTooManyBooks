package com.eleybourn.bookcatalogue.goodreads;

import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

/**
 * GoodreadsExceptions that may be thrown and used to wrap more varied inner exceptions
 */
public class GoodreadsExceptions {
    static class GeneralException extends Exception {
        private static final long serialVersionUID = 5762518476144652354L;

        GeneralException() {
        }

        GeneralException(final @Nullable String message) {
            super(message);

        }

        GeneralException(final String message, final Throwable inner) {
            super(message, inner);
        }
    }

    public static class NotAuthorizedException extends GeneralException {
        private static final long serialVersionUID = 5589234170614368111L;

        public NotAuthorizedException() {
            super(BookCatalogueApp.getResourceString(R.string.gr_auth_failed), null);
        }

        public NotAuthorizedException(final Throwable inner) {
            super(BookCatalogueApp.getResourceString(R.string.gr_auth_failed), inner);
        }
    }

    public static class BookNotFoundException extends GeneralException {
        private static final long serialVersionUID = 872113355903361212L;

        public BookNotFoundException() {
            super();
        }
    }

    public static class NetworkException extends GeneralException {
        private static final long serialVersionUID = -4233137984910957925L;

        public NetworkException() {
            super(BookCatalogueApp.getResourceString(R.string.gr_access_error));
        }

        public NetworkException(final @Nullable String message) {
            super(message);
        }

        public NetworkException(final @Nullable Throwable inner) {
            super(BookCatalogueApp.getResourceString(R.string.gr_access_error), inner);
        }


    }
}
