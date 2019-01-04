package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Filter {
    @NonNull
    String getExpression(@Nullable final String uuid);
}
