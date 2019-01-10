package com.eleybourn.bookcatalogue.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.net.SocketTimeoutException;
import java.util.List;

class ISFDBEditionsTask
        implements SimpleTaskQueue.SimpleTask {

    private List<String> mEditions;
    private final String mIsbn;
    private final ISFDBResultsListener mCallback;

    ISFDBEditionsTask(@NonNull final String isbn,
                      @NonNull final ISFDBResultsListener callback) {
        if (!IsbnUtils.isValid(isbn)) {
            throw new RTE.IsbnInvalidException(isbn);
        }
        mIsbn = isbn;
        mCallback = callback;
    }

    @Override
    public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext)
            throws SocketTimeoutException {
        Editions bookEditions = new Editions(mIsbn);
        mEditions = bookEditions.fetch();
    }

    @Override
    public void onFinish(@Nullable final Exception e) {
        mCallback.onGotISFDBEditions(mEditions);
    }
}
