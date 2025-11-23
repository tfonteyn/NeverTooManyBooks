/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

/**
 * This task is bypassing {@link SearchEngine.AlternativeEditions}
 * and uses {@link IsfdbSearchEngine#fetchEditionsByIsbn(Context, String)}
 * directly. The former strips the full book document (on purpose),
 * while the latter does not which saves us from an unneeded
 * round trip fetching the same info twice.
 * <p>
 * This is basically a stripped down, single engine, variant of
 * {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEditionsTask}.
 * <p>
 * Exceptions ARE returned.
 */
public class IsfdbGetEditionsTask
        extends MTask<List<AltEditionIsfdb>> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetEditionsTask";

    /** The isbn we're looking up. */
    private String validIsbn;

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
        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            ISBN.requireValidIsbn(validIsbn);
        }

        this.validIsbn = isbn.asText();
        execute();
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (searchEngine != null) {
                searchEngine.cancel();
            }
        }
    }

    @Override
    @WorkerThread
    @NonNull
    protected List<AltEditionIsfdb> doWork()
            throws StorageException,
                   SearchException,
                   CredentialsException,
                   IOException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // create a new instance just for our own use
        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(this);

        // can we reach the site ?
        searchEngine.ping(context);

        return searchEngine.fetchEditionsByIsbn(context, validIsbn);
    }
}
