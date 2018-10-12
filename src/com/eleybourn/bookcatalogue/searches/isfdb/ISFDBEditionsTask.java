package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.List;

class ISFDBEditionsTask implements SimpleTaskQueue.SimpleTask {

    private List<String> editions;
    private String isbn;
    private HandlesISFDB callback;

    ISFDBEditionsTask(@NonNull final String isbn, HandlesISFDB callback) {
        if (!IsbnUtils.isValid(isbn)) {
            throw new RTE.IsbnInvalidException(isbn);
        }
        this.isbn = isbn;
        this.callback = callback;
    }

    @Override
    public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
        Editions bookEditions = new Editions(isbn);
        editions = bookEditions.fetchEditions();
    }

    @Override
    public void onFinish(@Nullable final Exception e) {
        callback.onGotISFDBEditions(editions);
    }
}
