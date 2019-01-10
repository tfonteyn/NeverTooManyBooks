package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

/**
 * GoodreadsExceptions that may be thrown and used to wrap more varied inner exceptions.
 */
public class GoodreadsExceptions {

    public static class BookNotFoundException
            extends Exception {

        private static final long serialVersionUID = 872113355903361212L;

        public BookNotFoundException() {
            super();
        }
    }

    public static class NotAuthorizedException
            extends Exception {

        private static final long serialVersionUID = 5589234170614368111L;

        public NotAuthorizedException() {
            super(BookCatalogueApp.getResourceString(R.string.gr_auth_failed), null);
        }

        public NotAuthorizedException(final Throwable inner) {
            super(BookCatalogueApp.getResourceString(R.string.gr_auth_failed), inner);
        }
    }
}
