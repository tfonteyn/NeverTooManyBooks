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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

/**
 * Hard coded not to fetch any images.
 */
public class IsfdbGetBookTask
        extends VMTask<Bundle> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetBookTask";

    /** ISFDB book id to get. */
    private long mIsfdbId;
    /** ISFDB book edition to get. */
    @Nullable
    private Edition mEdition;

    /**
     * Initiate a single book lookup by edition.
     *
     * @param edition to get
     */
    @UiThread
    public void search(@NonNull final Edition edition) {
        mIsfdbId = 0;
        mEdition = edition;

        execute(R.id.TASK_ID_ISFDB_GET_BOOK);
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

        execute(R.id.TASK_ID_ISFDB_GET_BOOK);
    }

    @NonNull
    @Override
    @WorkerThread
    protected Bundle doWork(@NonNull final Context context)
            throws IOException {
        Thread.currentThread().setName(TAG);

        final IsfdbSearchEngine searchEngine = (IsfdbSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.ISFDB);
        searchEngine.setCaller(this);

        final boolean[] fetchThumbnails = {false, false};
        if (mEdition != null) {
            final Bundle bookData = new Bundle();
            searchEngine.fetchByEdition(mEdition, fetchThumbnails, bookData);
            return bookData;

        } else if (mIsfdbId != 0) {
            return searchEngine.searchByExternalId(String.valueOf(mIsfdbId), fetchThumbnails);

        } else {
            throw new IllegalStateException("how did we get here?");
        }
    }
}
