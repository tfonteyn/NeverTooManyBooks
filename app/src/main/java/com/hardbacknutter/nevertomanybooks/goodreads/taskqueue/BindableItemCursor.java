package com.hardbacknutter.nevertomanybooks.goodreads.taskqueue;

import android.database.Cursor;

import androidx.annotation.NonNull;

public interface BindableItemCursor
        extends Cursor {

    @NonNull
    BindableItemCursorAdapter.BindableItem getBindableItem();

    long getId();
}
