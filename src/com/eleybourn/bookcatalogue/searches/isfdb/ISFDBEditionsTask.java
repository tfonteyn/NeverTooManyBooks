package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.net.SocketTimeoutException;
import java.util.List;

class ISFDBEditionsTask implements SimpleTaskQueue.SimpleTask {

    private List<String> editions;
    private String isbn;
    private ISFDBResultsListener callback;

    ISFDBEditionsTask(final @NonNull String isbn, ISFDBResultsListener callback) {
        if (!IsbnUtils.isValid(isbn)) {
            throw new RTE.IsbnInvalidException(isbn);
        }
        this.isbn = isbn;
        this.callback = callback;
    }

    @Override
    public void run(final @NonNull SimpleTaskQueue.SimpleTaskContext taskContext) throws SocketTimeoutException {
        Editions bookEditions = new Editions(isbn);
        editions = bookEditions.fetch();
    }

    @Override
    public void onFinish(final @Nullable Exception e) {
        callback.onGotISFDBEditions(editions);
    }
}
