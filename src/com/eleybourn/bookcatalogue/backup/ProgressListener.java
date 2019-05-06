package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Listener interface to get progress messages. Replaces all (5!) old backup related listeners.
 */
public interface ProgressListener {

    /**
     * @param max value (can be estimated) for the progress counter
     */
    void setMax(int max);

    /**
     * Advance progress by 'delta'.
     *
     * Optional to implement.
     */
    default void onProgressStep(int delta,
                        @Nullable String message) {
        throw new UnsupportedOperationException();
    }

    /**
     * Report progress in absolute position.
     *
     * @param absPosition absolute position for the progress counter
     * @param messageId   to display
     */
    void onProgress(int absPosition,
                    @StringRes int messageId);

    /**
     * Report progress in absolute position.
     *
     * @param absPosition absolute position for the progress counter
     * @param message     to display
     */
    void onProgress(int absPosition,
                    @Nullable String message);

    /**
     * @return {@code true} if operation is cancelled.
     */
    boolean isCancelled();
}
