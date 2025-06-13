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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Searches a single {@link SearchEngine}.
 */
final class SearchTask
        extends LTask<Book> {

    /** Log tag. */
    private static final String TAG = "SearchTask";

    private static final AtomicInteger TASK_ID = new AtomicInteger();

    private static final String ERROR_ISBN_STR_NOT_SET = "isbnStr not set";

    private final int searchId;
    @NonNull
    private final SearchEngine searchEngine;
    /** Search criteria. Usage depends on {@link #by}. */
    @NonNull
    private final SearchCoordinatorCriteria criteria;
    /** What criteria to search by. */
    private SearchEngine.SearchBy by;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param searchId     a unique search identifier, to which this task belongs
     * @param taskId       a unique task identifier, returned with each message
     * @param searchEngine the search site engine
     * @param criteria     to use
     * @param taskListener for the results
     */
    private SearchTask(@NonNull final Context context,
                       final int searchId,
                       final int taskId,
                       @NonNull final SearchEngine searchEngine,
                       @NonNull final SearchCoordinatorCriteria criteria,
                       @NonNull final TaskListener<Book> taskListener) {
        super(taskId, TAG + ' ' + searchEngine.getName(context), taskListener);
        this.searchId = searchId;
        this.searchEngine = searchEngine;
        this.criteria = criteria;
    }

    /**
     * Constructor. Will search according to passed {@link SearchCoordinatorCriteria}.
     * <ol>
     *      <li>external id</li>
     *      <li>valid ISBN</li>
     *      <li>valid barcode</li>
     *      <li>text</li>
     * </ol>
     *
     * @param context      Current context
     * @param searchId     a unique search identifier, to which this task belongs
     * @param searchEngine the search site engine
     * @param criteria     to use
     * @param taskListener for the results
     *
     * @return task; will be {@code null} if the given criteria don't match up with the given
     *         SearchEngine
     */
    @Nullable
    static SearchTask createSearchTask(@NonNull final Context context,
                                       final int searchId,
                                       @NonNull final SearchEngine searchEngine,
                                       @NonNull final SearchCoordinatorCriteria criteria,
                                       @NonNull final TaskListener<Book> taskListener) {

        final SearchTask task = new SearchTask(context, searchId,
                                               TASK_ID.getAndIncrement(),
                                               searchEngine, criteria,
                                               taskListener);

        searchEngine.setCaller(task);
        task.setExecutor(ASyncExecutor.NETWORK);

        final EngineId engineId = searchEngine.getEngineId();

        // check for a sid matching the site.
        // This always takes preference over all other criteria
        final Optional<String> oSid = criteria.getSid(engineId);
        if (oSid.isPresent()
            && engineId.supports(SearchEngine.SearchBy.ExternalId)) {
            task.setSearchBy(SearchEngine.SearchBy.ExternalId);
            return task;
        }

        final Optional<ISBN> oIsbn = criteria.getIsbn();
        if (oIsbn.isPresent() && oIsbn.get().isValid(true)
            && engineId.supports(SearchEngine.SearchBy.Isbn)) {
            task.setSearchBy(SearchEngine.SearchBy.Isbn);
            return task;
        }
        if (oIsbn.isPresent() && oIsbn.get().isValid(false)
            && engineId.supports(SearchEngine.SearchBy.Barcode)) {
            task.setSearchBy(SearchEngine.SearchBy.Barcode);
            return task;
        }
        if (engineId.supports(SearchEngine.SearchBy.Text)) {
            task.setSearchBy(SearchEngine.SearchBy.Text);
            return task;
        }

        // search data and engine have nothing in common, abort.
        return null;
    }

    public int getSearchId() {
        return searchId;
    }

    @NonNull
    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    @NonNull
    SearchEngine.SearchBy getSearchBy() {
        return by;
    }

    private void setSearchBy(@NonNull final SearchEngine.SearchBy by) {
        this.by = by;
    }

    void startSearch() {
        execute();
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (searchEngine) {
            searchEngine.cancel();
        }
    }

    @NonNull
    @Override
    @WorkerThread
    protected Book doWork()
            throws StorageException,
                   SearchException,
                   CredentialsException,
                   IOException {
        Objects.requireNonNull(criteria);

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Checking this each time a search starts is not needed...
        // But it makes error handling slightly easier and doing
        // it here offloads it from the UI thread.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        searchEngine.ping(context);

        if (searchEngine instanceof SearchEngine.Login) {
            final SearchEngine.Login sel = (SearchEngine.Login) searchEngine;
            if (sel.isLoginToSearch(context)) {
                publishProgress(1, context.getString(R.string.progress_msg_authenticating_to_site,
                                                     searchEngine.getName(context)));
                sel.login(context);
            }
        }

        publishProgress(1, context.getString(R.string.progress_msg_searching_site,
                                             searchEngine.getName(context)));

        final Book book;
        switch (by) {
            case ExternalId: {
                final Optional<String> oSid = criteria.getSid(searchEngine.getEngineId());
                if (oSid.isEmpty()) {
                    throw new IllegalArgumentException("sid not set");
                }
                book = ((SearchEngine.ByExternalId) searchEngine)
                        .searchByExternalId(context, oSid.get(), criteria.getFetchCovers());
                break;
            }
            case Isbn: {
                final String isbnStr = requireIsbnString(context);
                book = ((SearchEngine.ByIsbn) searchEngine)
                        .searchByIsbn(context, isbnStr, criteria.getFetchCovers());
                break;
            }
            case Barcode: {
                final String isbnStr = requireIsbnString(context);
                book = ((SearchEngine.ByBarcode) searchEngine)
                        .searchByBarcode(context, isbnStr, criteria.getFetchCovers());
                break;
            }
            case Text: {
                // FIXME: github #131 "ISBN: 01-001-86" allow searches with null/empty criteria
                //  when there is an isbnStr
                //  => must update code in ALL SearchEngines to allow this!
                final String isbnStr = isbnToString(context);
                book = ((SearchEngine.ByText) searchEngine)
                        .search(context, criteria, isbnStr, criteria.getFetchCovers());
                break;
            }
            default: {
                // we should never get here...
                throw new IllegalArgumentException("SearchEngine "
                                                   + searchEngine.getName(context)
                                                   + " does not implement By=" + by);
            }
        }

        return book;
    }

    @NonNull
    private String requireIsbnString(@NonNull final Context context) {
        final String s = isbnToString(context);
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException(ERROR_ISBN_STR_NOT_SET);
        }
        return s;
    }

    @Nullable
    private String isbnToString(@NonNull final Context context) {
        @Nullable
        final String isbnStr;
        // Do NOT check on validity, at this point the isbn IS
        // allowed to be any other code as well.
        final ISBN isbn = criteria.getIsbn().orElse(null);
        if (isbn != null) {
            //noinspection DataFlowIssue
            if (searchEngine.getEngineId().getConfig()
                            .prefersIsbn10(context) && isbn.isIsbn10Compat()) {
                isbnStr = isbn.asText(ISBN.Type.Isbn10);
            } else {
                isbnStr = isbn.asText();
            }
        } else {
            isbnStr = null;
        }
        return isbnStr;
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchTask{"
               + "searchEngine=" + searchEngine.getEngineId()
               + ", by=" + by
               + ", criteria=`" + criteria + '`'
               + '}';
    }
}
