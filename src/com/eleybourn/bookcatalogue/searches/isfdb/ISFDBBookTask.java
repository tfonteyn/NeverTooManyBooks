package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;

import java.net.SocketTimeoutException;

class ISFDBBookTask implements SimpleTaskQueue.SimpleTask {
    @NonNull
    private final HandlesISFDB callback;
    @NonNull
    private final String bookUrl;
    private final Bundle mBookData = new Bundle();
    private boolean fetchThumbnail;

    ISFDBBookTask(final @NonNull String bookUrl,
                  final boolean fetchThumbnail,
                  final @NonNull HandlesISFDB callback) {
        this.callback = callback;
        this.bookUrl = bookUrl;
        this.fetchThumbnail = fetchThumbnail;
    }

    @Override
    public void run(final @NonNull SimpleTaskQueue.SimpleTaskContext taskContext) throws SocketTimeoutException {
        ISFDBBook isfdbBook = new ISFDBBook(bookUrl);
        isfdbBook.fetch(mBookData, fetchThumbnail);
    }

    @Override
    public void onFinish(final @Nullable Exception e) {
        callback.onGotISFDBBook(mBookData);
    }
}
