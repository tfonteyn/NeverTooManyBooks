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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

/**
 * Hard coded not to fetch any images.
 */
public class IsfdbGetBookTask
        extends MTask<Book> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetBookTask";

    /** ISFDB book id to get. */
    private long isfdbId;
    /** ISFDB book edition to get. */
    @Nullable
    private AltEditionIsfdb edition;

    @Nullable
    private IsfdbSearchEngine searchEngine;

    /**
     * Constructor.
     */
    public IsfdbGetBookTask() {
        super(R.id.TASK_ID_ISFDB_GET_BOOK, TAG);
    }

    /**
     * Initiate a single book lookup by edition.
     *
     * @param edition to get
     */
    @UiThread
    public void search(@NonNull final AltEditionIsfdb edition) {
        isfdbId = 0;
        this.edition = edition;

        execute();
    }

    /**
     * Initiate a single book lookup by ID.
     *
     * @param isfdbId Single ISFDB book ID's
     */
    @UiThread
    public void search(final long isfdbId) {
        this.isfdbId = isfdbId;
        edition = null;

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
    protected Book doWork()
            throws StorageException, SearchException, CredentialsException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // create a new instance just for our own use
        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(this);

        final boolean[] fetchCovers = {false, false};
        if (edition != null) {
            final Book book = new Book();
            searchEngine.fetchByEdition(context, edition, fetchCovers, book);
            CoverFileSpecArray.process(book);
            return book;

        } else if (isfdbId != 0) {
            final Book book = searchEngine.searchByExternalId(context, String.valueOf(isfdbId),
                                                              fetchCovers);
            CoverFileSpecArray.process(book);
            return book;

        } else {
            throw new IllegalStateException("how did we get here?");
        }
    }
}
