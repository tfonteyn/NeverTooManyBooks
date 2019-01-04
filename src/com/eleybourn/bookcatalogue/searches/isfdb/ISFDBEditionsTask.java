package com.eleybourn.bookcatalogue.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.net.SocketTimeoutException;
import java.util.List;

class ISFDBEditionsTask implements SimpleTaskQueue.SimpleTask {

    private List<String> editions;
    private String isbn;
    private ISFDBResultsListener callback;

    ISFDBEditionsTask(@NonNull final String isbn, ISFDBResultsListener callback) {
        if (!IsbnUtils.isValid(isbn)) {
            throw new RTE.IsbnInvalidException(isbn);
        }
        this.isbn = isbn;
        this.callback = callback;
    }

    @Override
    public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) throws SocketTimeoutException {
        Editions bookEditions = new Editions(isbn);
        editions = bookEditions.fetch();
    }

    @Override
    public void onFinish(@Nullable final Exception e) {
        callback.onGotISFDBEditions(editions);
    }
}
