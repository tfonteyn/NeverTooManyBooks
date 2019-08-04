package com.hardbacknutter.nevertomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Filter<T> {

    /**
     * A Filter must implement this method. All others are optional.
     *
     * @return filter SQL expression, or {@code null} if not active.
     */
    @Nullable
    String getExpression();

    /**
     * @param context Current context for accessing string resources.
     *
     * @return a human readable label/name for this filter, or {@code null} if none.
     */
    @Nullable
    default String getLabel(@NonNull final Context context) {
        return null;
    }

    /**
     * Allow an implementation to override the definition of being active.
     *
     * @return {@code true} if this filter is active.
     */
    default boolean isActive() {
        return getExpression() != null;
    }

    default String getKey() {
        throw new UnsupportedOperationException();
    }

    default T get() {
        throw new UnsupportedOperationException();
    }
}
