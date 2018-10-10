package com.eleybourn.bookcatalogue.taskqueue;

import android.database.Cursor;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.widgets.BindableItemCursorAdapter;

public interface BindableItemCursor extends Cursor {

    @NonNull
    BindableItemCursorAdapter.BindableItem getBindableItem();

    long getId();
}
