package com.eleybourn.bookcatalogue.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * a centralised collection of RunTimeExceptions so we can re-use them without re-inventing.
 * <p>
 * Well focused exceptions (e.g. database) should not be moved here.
 */
public class RTE {

    /**
     * Thrown after an ISBN was checked for validity where it *must* be valid.
     * e.g. f it wasn't, then we have a bug.
     */
    public static class IsbnInvalidException
            extends RuntimeException {

        private static final long serialVersionUID = 2652418388349622089L;

        public IsbnInvalidException(@Nullable final String message) {
            super(message);
        }

        public IsbnInvalidException(@Nullable final Exception inner) {
            super(inner);
        }
    }

    /**
     * When a method receives an argument that is 'valid', but of the wrong type.
     * Often used in 'default:' in a switch as well.
     */
    public static class IllegalTypeException
            extends IllegalStateException {

        private static final long serialVersionUID = -6589992995979719380L;

        public IllegalTypeException(@NonNull final String message) {
            super(message);
        }
    }

    /**
     * This is really a debug message, but unfortunately one we cannot reliably
     * intercept in development.
     * (e.g. testing needs to be unit/automated!)
     */
    public static class MustImplementException
            extends IllegalStateException {

        private static final long serialVersionUID = 1254362943479705468L;

        public MustImplementException(@NonNull final Context context,
                                      @NonNull final Class clazz) {
            super("Class " + context.getClass().getCanonicalName() +
                          " must implement " + clazz.getCanonicalName());
        }
    }

    /**
     * Catchall class for errors in serialization.
     */
    public static class DeserializationException
            extends Exception {

        private static final long serialVersionUID = -2040548134317746620L;

        DeserializationException(@Nullable final Exception e) {
            super();
            initCause(e);
        }
    }
}
