/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

/**
 * This task is bypassing {@link SearchEngine.AlternativeEditions}
 * as in this particular circumstance it's faster.
 */
public class IsfdbGetEditionsTask
        extends MTask<List<AltEditionIsfdb>> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetEditionsTask";

    /** The isbn we're looking up. */
    private String isbn;

    @Nullable
    private IsfdbSearchEngine searchEngine;

    /**
     * Constructor.
     */
    public IsfdbGetEditionsTask() {
        super(R.id.TASK_ID_SEARCH_EDITIONS, TAG);
    }

    /**
     * Start the search.
     *
     * @param isbn to search for
     */
    @UiThread
    public void search(@NonNull final ISBN isbn) {
        this.isbn = isbn.asText();
        execute();
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (searchEngine != null) {
                searchEngine.cancel();
            }
        }
    }

    @NonNull
    @Override
    @WorkerThread
    protected List<AltEditionIsfdb> doWork()
            throws StorageException, SearchException, CredentialsException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // create a new instance just for our own use
        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(this);

        return searchEngine.fetchEditionsByIsbn(context, isbn);
    }
}
