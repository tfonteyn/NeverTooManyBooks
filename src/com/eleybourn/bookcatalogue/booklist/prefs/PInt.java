package com.eleybourn.bookcatalogue.booklist.prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Allows us to cast any PPref<Integer> as needed.
 */
public interface PInt {

    void set(@Nullable final Integer value);

    @NonNull
    Integer get();
}
