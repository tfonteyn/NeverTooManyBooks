/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * Hard coded not to fetch any images.
 */
public class IsfdbGetBookTask
        extends MTask<Bundle> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetBookTask";

    /** ISFDB book id to get. */
    private long mIsfdbId;
    /** ISFDB book edition to get. */
    @Nullable
    private Edition mEdition;

    public IsfdbGetBookTask() {
        super(R.id.TASK_ID_ISFDB_GET_BOOK, TAG);
    }

    /**
     * Initiate a single book lookup by edition.
     *
     * @param edition to get
     */
    @UiThread
    public void search(@NonNull final Edition edition) {
        mIsfdbId = 0;
        mEdition = edition;

        execute();
    }

    /**
     * Initiate a single book lookup by ID.
     *
     * @param isfdbId Single ISFDB book ID's
     */
    @UiThread
    public void search(final long isfdbId) {
        mIsfdbId = isfdbId;
        mEdition = null;

        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Bundle doWork(@NonNull final Context context)
            throws DiskFullException, CoverStorageException, SearchException,
                   CredentialsException {

        final IsfdbSearchEngine searchEngine = (IsfdbSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.ISFDB);
        searchEngine.setCaller(this);

        final boolean[] fetchCovers = {false, false};
        if (mEdition != null) {
            final Bundle bookData = new Bundle();
            searchEngine.fetchByEdition(context, mEdition, fetchCovers, bookData);
            return bookData;

        } else if (mIsfdbId != 0) {
            return searchEngine.searchByExternalId(context, String.valueOf(mIsfdbId), fetchCovers);

        } else {
            throw new IllegalStateException("how did we get here?");
        }
    }
}
