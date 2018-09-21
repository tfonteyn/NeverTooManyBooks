package com.eleybourn.bookcatalogue.taskqueue;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.widgets.BindableItemCursorAdapter;

public interface BindableItemCursor extends Cursor {

    BindableItemCursorAdapter.BindableItem getBindableItem();
    long getId();
}
