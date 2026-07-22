package com.hardbacknutter.org.json;

/*
Public Domain.
 */

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; /**

 * The JSONException is thrown by the JSON.org classes when things are amiss.
 *
 * @author JSON.org
 * @version 2015-12-09
 */
@SuppressWarnings("ALL")
public class JSONException extends RuntimeException {
    /** Serialization ID */
    private static final long serialVersionUID = 0;

    /**
     * Constructs a JSONException with an explanatory message.
     *
     * @param message
     *            Detail about the reason for the exception.
     */
    public JSONException(@Nullable final String message) {
        super(message);
    }

    /**
     * Constructs a JSONException with an explanatory message and cause.
     * 
     * @param message
     *            Detail about the reason for the exception.
     * @param cause
     *            The cause.
     */
    public JSONException(@NonNull final String message, @Nullable final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new JSONException with the specified cause.
     * 
     * @param cause
     *            The cause.
     */
    public JSONException(@NonNull final Throwable cause) {
        super(cause.getMessage(), cause);
    }

}
