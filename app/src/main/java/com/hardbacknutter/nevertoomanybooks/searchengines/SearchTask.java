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
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

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
    /** Search criteria. Usage depends on {@link #by}. */
    @NonNull
    private final BookSearchCriteria criteria;
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
                       @NonNull final BookSearchCriteria criteria,
                       @NonNull final TaskListener<Book> taskListener) {
        super(taskId, TAG + ' ' + searchEngine.getName(context), taskListener);
        this.searchId = searchId;
        this.searchEngine = searchEngine;
        this.criteria = criteria;
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
                                       @NonNull final BookSearchCriteria criteria,
                                       @NonNull final TaskListener<Book> taskListener) {

        final SearchTask task = new SearchTask(context, searchId,
                                               TASK_ID.incrementAndGet(),
                                               searchEngine, criteria,
                                               taskListener);

        searchEngine.setCaller(task);
        task.setExecutor(ASyncExecutor.NETWORK);

        final EngineId engineId = searchEngine.getEngineId();

        // Search by SID takes preference over all other criteria
        if (engineId.supports(SearchEngine.SearchBy.ExternalId)) {
            if (criteria.getSid(engineId).isPresent()) {
                task.setSearchBy(SearchEngine.SearchBy.ExternalId);
                return task;
            }
        }

        final ProductCode productCode = criteria.getProductCode();

        // Search by a VALID code.
        if (engineId.supports(SearchEngine.SearchBy.Isbn) && productCode != null) {
            // Either strict ISBN, or any other valid code
            // depending on the user criteria 'strict' flag.
            if (criteria.isStrictIsbn() ? productCode.isIsbn()
                                        : productCode.getType() != ProductCodeType.Invalid) {
                task.setSearchBy(SearchEngine.SearchBy.Isbn);
                return task;
            }
        }

        // Search by any code, including invalid ones
        if (engineId.supports(SearchEngine.SearchBy.Barcode) && productCode != null) {
            task.setSearchBy(SearchEngine.SearchBy.Barcode);
            return task;
        }

        // Search by anything which may be supported by the engine.
        // Check on empty criteria is paranoia...
        if (engineId.supports(SearchEngine.SearchBy.Text) && !criteria.isEmpty()) {
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
                                                   + " does not implement By=" + by);
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
               + ", by=" + by
               + ", criteria=`" + criteria + '`'
               + '}';
    }
}
