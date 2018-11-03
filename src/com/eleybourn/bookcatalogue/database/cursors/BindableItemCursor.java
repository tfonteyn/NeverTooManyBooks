package com.eleybourn.bookcatalogue.database.cursors;

import android.database.Cursor;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;

public interface BindableItemCursor extends Cursor {

    @NonNull
    BindableItemCursorAdapter.BindableItem getBindableItem();

    long getId();
}
