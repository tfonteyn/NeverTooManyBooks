package com.eleybourn.bookcatalogue.goodreads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

public class GoodreadsShelves {

    @NonNull
    private final Map<String, GoodreadsShelf> mList;

    public GoodreadsShelves(@NonNull final Map<String, GoodreadsShelf> list) {
        mList = list;
    }

    public boolean isExclusive(@Nullable final String name) {
        GoodreadsShelf shelf = mList.get(name);
        return shelf != null && shelf.isExclusive();
    }
}
