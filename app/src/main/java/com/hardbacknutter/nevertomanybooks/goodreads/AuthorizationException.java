package com.hardbacknutter.nevertomanybooks.goodreads;

import androidx.annotation.NonNull;

/**
 * A wrapper around the OAuth exception so we can keep the use of the original limited.
 */
public class AuthorizationException
        extends Exception {

    public AuthorizationException(@NonNull final String message) {
        super(message);
    }

    public AuthorizationException(@NonNull final Throwable cause) {
        super(cause);
    }

    public AuthorizationException(@NonNull final String message,
                                  @NonNull final Throwable cause) {
        super(message, cause);
    }
}
