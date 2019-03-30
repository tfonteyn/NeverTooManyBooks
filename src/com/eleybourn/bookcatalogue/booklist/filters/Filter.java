package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.Nullable;

@FunctionalInterface
public interface Filter {

    @Nullable
    String getExpression();
}
