/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
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
