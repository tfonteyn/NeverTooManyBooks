package com.hardbacknutter.nevertomanybooks.goodreads.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.goodreads.GoodreadsWork;
import com.hardbacknutter.nevertomanybooks.goodreads.api.SearchBooksApiHandler;
import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;

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
