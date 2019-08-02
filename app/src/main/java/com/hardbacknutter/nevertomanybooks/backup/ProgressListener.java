package com.hardbacknutter.nevertomanybooks.backup;

import androidx.annotation.Nullable;

/**
 * Listener interface to get progress messages from the backup routines back to the caller.
 * Replaces all (5!) old backup related listeners.
 */
public interface ProgressListener {

    /**
     * Set the max value (can be estimated) for the progress counter.
     *
     * @param maxPosition value
     */
    void setMax(int maxPosition);

    /**
     * Increment the max value by 'delta'.
     *
     * @param delta value
     */
    void incMax(int delta);

    /**
     * Advance progress by 'delta'.
     * <p>
     * Optional to implement.
     *
     * @param delta   increment/decrement value for the progress counter
     * @param message to display, either a String or a StringRes
     */
    default void onProgressStep(int delta,
                                @Nullable Object message) {
        throw new UnsupportedOperationException();
    }

    /**
     * Report progress in absolute position.
     *
     * @param absPosition absolute position for the progress counter
     * @param message     to display, either a String or a StringRes
     */
    void onProgress(int absPosition,
                    @Nullable Object message);

    /**
     * @return {@code true} if operation is cancelled.
     */
    boolean isCancelled();
}
