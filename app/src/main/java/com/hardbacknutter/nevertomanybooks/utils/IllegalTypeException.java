package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.NonNull;

/**
 * When a method receives an argument that is 'valid', but of the wrong type.
 * Often used in 'default:' in a switch as well.
 */
public class IllegalTypeException
        extends IllegalStateException {

    private static final long serialVersionUID = -6589992995979719380L;

    public IllegalTypeException(@NonNull final String message) {
        super(message);
    }
}
