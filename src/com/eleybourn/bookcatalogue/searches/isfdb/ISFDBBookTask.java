package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;

import java.net.SocketTimeoutException;
import java.util.List;

class ISFDBBookTask
        implements SimpleTaskQueue.SimpleTask {

    @NonNull
    private final ISFDBResultsListener mCallback;

    @NonNull
    private final List<String> mEditionUrls;
    private final Bundle mBookData = new Bundle();
    private final boolean mFetchThumbnail;

    ISFDBBookTask(@NonNull final List<String> editionUrls,
                  final boolean fetchThumbnail,
                  @NonNull final ISFDBResultsListener callback) {
        mCallback = callback;
        mEditionUrls = editionUrls;
        mFetchThumbnail = fetchThumbnail;
    }

    @Override
    public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext)
            throws SocketTimeoutException {
        ISFDBBook isfdbBook = new ISFDBBook(mEditionUrls);
        isfdbBook.fetch(mBookData, mFetchThumbnail);
    }

    @Override
    public void onFinish(@Nullable final Exception e) {
        mCallback.onGotISFDBBook(mBookData);
    }
}
