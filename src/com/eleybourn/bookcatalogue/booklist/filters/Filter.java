package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.Nullable;

public interface Filter {

    @Nullable
    String getExpression(@Nullable String uuid);
}
