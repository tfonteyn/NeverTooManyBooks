package com.eleybourn.bookcatalogue.debug;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * This is really a debug message, but unfortunately one we cannot reliably
 * intercept in development.
 * (i.e. testing needs to be unit/automated!)
 */
public class MustImplementException
        extends IllegalStateException {

    private static final long serialVersionUID = 1254362943479705468L;

    public MustImplementException(@NonNull final Context context,
                                  @NonNull final Class clazz) {
        super("Class " + context.getClass().getCanonicalName() +
                      " must implement " + clazz.getCanonicalName());
    }
}
