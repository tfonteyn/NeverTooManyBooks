package com.eleybourn.bookcatalogue.booklist.filters;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Filter {

    /**
     *
     * @return a human readable label/name for this filter, or {@code null} if none.
     */
    @Nullable
    default String getLabel(@NonNull Resources resources) {
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

    /**
     *
     * @return filter SQL expression, or {@code null} if not active.
     */
    @Nullable
    String getExpression();
}
