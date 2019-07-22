package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;

public class FetchWorksTask
        extends AsyncTask<Void, Void, List<GoodreadsWork>> {

    @NonNull
    private final WeakReference<TaskListener<Void, List<GoodreadsWork>>> mTaskListener;
    private final String mSearchText;
    @Nullable
    private Exception mException;

    public FetchWorksTask(@NonNull final String searchText,
                          @NonNull final TaskListener<Void, List<GoodreadsWork>> taskListener) {
        mSearchText = searchText;
        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @Nullable
    protected List<GoodreadsWork> doInBackground(final Void... voids) {
        Thread.currentThread().setName("GR.FetchWorksTask");

        GoodreadsManager grManager = new GoodreadsManager();
        try {
            SearchBooksApiHandler searcher = new SearchBooksApiHandler(grManager);
            return searcher.search(mSearchText);

        } catch (@NonNull final BookNotFoundException
                | CredentialsException
                | IOException
                | RuntimeException e) {
            Logger.error(this, e, "Failed when searching Goodreads");
            mException = e;
        }

        return null;
    }

    @Override
    protected void onPostExecute(@Nullable final List<GoodreadsWork> result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskFinished(R.id.TASK_ID_GR_GET_WORKS, mException == null,
                                               result, mException);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
