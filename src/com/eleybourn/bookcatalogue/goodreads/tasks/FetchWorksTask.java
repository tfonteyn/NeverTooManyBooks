package com.eleybourn.bookcatalogue.goodreads.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskBase;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;

public class FetchWorksTask
        extends TaskBase<List<GoodreadsWork>> {

    private final String mSearchText;

    /**
     * Constructor.
     *
     * @param searchText   keywords to search for
     * @param taskListener for sending progress and finish messages to.
     */
    public FetchWorksTask(@NonNull final String searchText,
                          @NonNull final TaskListener<List<GoodreadsWork>> taskListener) {
        super(R.id.TASK_ID_GR_GET_WORKS, taskListener);
        mSearchText = searchText;
    }

    @Override
    @Nullable
    protected List<GoodreadsWork> doInBackground(final Void... voids) {
        Thread.currentThread().setName("GR.FetchWorksTask");

        GoodreadsManager grManager = new GoodreadsManager();
        try {
            SearchBooksApiHandler searcher = new SearchBooksApiHandler(grManager);
            return searcher.search(mSearchText);

        } catch (@NonNull final CredentialsException | BookNotFoundException | IOException
                | RuntimeException e) {
            Logger.error(this, e);
            mException = e;
        }
        return null;
    }
}
