package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;

import java.net.SocketTimeoutException;
import java.util.List;

class ISFDBBookTask implements SimpleTaskQueue.SimpleTask {
    @NonNull
    private final ISFDBResultsListener callback;

    @NonNull
    private final List<String> editionUrls;
    private final Bundle mBookData = new Bundle();
    private final boolean fetchThumbnail;

    ISFDBBookTask(final @NonNull List<String> editionUrls,
                  final boolean fetchThumbnail,
                  final @NonNull ISFDBResultsListener callback) {
        this.callback = callback;
        this.editionUrls = editionUrls;
        this.fetchThumbnail = fetchThumbnail;
    }

    @Override
    public void run(final @NonNull SimpleTaskQueue.SimpleTaskContext taskContext) throws SocketTimeoutException {
        ISFDBBook isfdbBook = new ISFDBBook(editionUrls);
        isfdbBook.fetch(mBookData, fetchThumbnail);
    }

    @Override
    public void onFinish(final @Nullable Exception e) {
        callback.onGotISFDBBook(mBookData);
    }
}
