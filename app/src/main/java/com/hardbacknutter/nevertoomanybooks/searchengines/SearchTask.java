/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.AnyThread;
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
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;

/**
 * Searches a single {@link SearchEngine}.
 */
final class SearchTask
        extends LTask<Book> {

    /** Log tag. */
    private static final String TAG = "SearchTask";

    private static final AtomicInteger TASK_ID = new AtomicInteger();

    private static final String ERROR_PRODUCT_CODE_NOT_SET = "ProductCode not set";

    private final int searchId;
    @NonNull
    private final SearchEngine searchEngine;
    /** Search criteria. Usage depends on {@link #searchBy}. */
    @NonNull
    private final BookSearchCriteria criteria;
    /** What criteria to search by. */
    private final SearchEngine.SearchBy searchBy;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param taskId       a unique task identifier, returned with each message
     * @param searchId     a unique search identifier, to which this task belongs
     * @param searchEngine the search site engine
     * @param searchBy     how to search
     * @param criteria     to use
     * @param taskListener for the results
     */
    private SearchTask(@NonNull final Context context,
                       final int taskId,
                       final int searchId,
                       @NonNull final SearchEngine searchEngine,
                       @NonNull final SearchEngine.SearchBy searchBy,
                       @NonNull final BookSearchCriteria criteria,
                       @NonNull final TaskListener<Book> taskListener) {
        super(taskId, TAG + ' ' + searchEngine.getName(context), taskListener);
        this.searchId = searchId;
        this.searchEngine = searchEngine;
        this.criteria = criteria;
        this.searchBy = searchBy;
    }

    /**
     * Constructor. Will search according to passed {@link BookSearchCriteria}.
     * <ol>
     *      <li>external id</li>
     *      <li>valid ISBN</li>
     *      <li>valid barcode</li>
     *      <li>text</li>
     * </ol>
     *
     * @param context      Current context
     * @param searchId     a unique search identifier, to which this task will belong
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
                                       @NonNull final BookSearchCriteria criteria,
                                       @NonNull final TaskListener<Book> taskListener) {

        final EngineId engineId = searchEngine.getEngineId();

        final SearchEngine.SearchBy searchBy = determineSearchBy(engineId, criteria);
        if (searchBy == null) {
            return null;
        }

        final SearchTask task = new SearchTask(context, TASK_ID.incrementAndGet(),
                                               searchId, searchEngine, searchBy, criteria,
                                               taskListener);
        task.setExecutor(ASyncExecutor.NETWORK);

        searchEngine.setCaller(task);
        return task;
    }

    @Nullable
    private static SearchEngine.SearchBy determineSearchBy(
            @NonNull final EngineId engineId,
            @NonNull final BookSearchCriteria criteria) {

        // We seemingly do double-work here by first determning by which method we
        // will search and then later on in doWork using that to decide to actual
        // API method to call on the SearchEngine.
        // The whole reason for this is, that we could end up NOT searching
        // when there is no compatible engine/criteria combination.
        // This way, we avoid starting a task which would decide it does not need to run.

        // Search by SID takes preference over all other criteria
        if (engineId.supports(SearchEngine.SearchBy.ExternalId)) {
            if (criteria.getSid(engineId).isPresent()) {
                return SearchEngine.SearchBy.ExternalId;
            }
        }

        final ProductCode productCode = criteria.getProductCode();

        // Search by a VALID code.
        if (engineId.supports(SearchEngine.SearchBy.Isbn) && productCode != null) {
            // Either strict ISBN, or any other valid code
            // depending on the user criteria 'strict' flag.
            if (criteria.isStrictIsbn() ? productCode.isIsbn()
                                        : productCode.getType() != ProductCodeType.Invalid) {
                return SearchEngine.SearchBy.Isbn;
            }
        }

        // Search by any code, including invalid ones
        if (engineId.supports(SearchEngine.SearchBy.Barcode) && productCode != null) {
            return SearchEngine.SearchBy.Barcode;
        }

        // Search by anything which may be supported by the engine.
        // Check on empty criteria is paranoia...
        if (engineId.supports(SearchEngine.SearchBy.Text) && !criteria.isEmpty()) {
            return SearchEngine.SearchBy.Text;
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
        return searchBy;
    }

    void startSearch() {
        execute();
    }

    @Override
    @AnyThread
    public void cancel() {
        super.cancel();
        synchronized (searchEngine) {
            searchEngine.cancel();
        }
    }

    @Override
    @WorkerThread
    @NonNull
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
        searchEngine.ping();

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
        switch (searchBy) {
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
                final ProductCode productCode = criteria.getProductCode();
                if (productCode == null) {
                    throw new IllegalArgumentException(ERROR_PRODUCT_CODE_NOT_SET);
                }
                book = ((SearchEngine.ByIsbn) searchEngine)
                        .searchByIsbn(context, productCode, criteria.getFetchCovers());
                break;
            }
            case Barcode: {
                final ProductCode productCode = criteria.getProductCode();
                if (productCode == null) {
                    throw new IllegalArgumentException(ERROR_PRODUCT_CODE_NOT_SET);
                }
                book = ((SearchEngine.ByBarcode) searchEngine)
                        .searchByBarcode(context, productCode, criteria.getFetchCovers());
                break;
            }
            case Text: {
                book = ((SearchEngine.ByText) searchEngine)
                        .search(context, criteria, criteria.getFetchCovers());
                break;
            }
            default: {
                // we should never get here...
                throw new IllegalArgumentException("SearchEngine "
                                                   + searchEngine.getName(context)
                                                   + " does not implement " + searchBy);
            }
        }

        return book;
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchTask{"
               + "searchEngine=" + searchEngine.getEngineId()
               + ", searchId=" + searchId
               + ", searchBy=" + searchBy
               + ", criteria=`" + criteria + '`'
               + '}';
    }
}
