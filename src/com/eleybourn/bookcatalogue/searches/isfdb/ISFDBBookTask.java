package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;

class ISFDBBookTask implements SimpleTaskQueue.SimpleTask {
    private final HandlesISFDB callback;
    private final String bookUrl;
    private final Bundle mBookData = new Bundle();
    private boolean fetchThumbnail;

    ISFDBBookTask(@NonNull final String bookUrl,
                  final boolean fetchThumbnail,
                  @NonNull final HandlesISFDB callback) {
        this.callback = callback;
        this.bookUrl = bookUrl;
        this.fetchThumbnail = fetchThumbnail;
    }

    @Override
    public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
        new ISFDBBook(bookUrl, mBookData, fetchThumbnail);
    }

    @Override
    public void onFinish(@Nullable final Exception e) {
        callback.onGotISFDBBook(mBookData);
    }
}
