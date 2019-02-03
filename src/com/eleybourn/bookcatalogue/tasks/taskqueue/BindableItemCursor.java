package com.eleybourn.bookcatalogue.tasks.taskqueue;

import android.database.Cursor;

import androidx.annotation.NonNull;

public interface BindableItemCursor
        extends Cursor {

    @NonNull
    BindableItemCursorAdapter.BindableItem getBindableItem();

    long getId();
}
