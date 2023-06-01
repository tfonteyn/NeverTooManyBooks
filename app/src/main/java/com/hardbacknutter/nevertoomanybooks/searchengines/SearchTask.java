/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;

/**
 * Searches a single {@link SearchEngine}.
 */
public class SearchTask
        extends LTask<Book> {

    /** Log tag. */
    private static final String TAG = "SearchTask";

    @NonNull
    private final SearchEngine searchEngine;
    /** Whether to fetch covers. {false,false} by default. */
    @NonNull
    private final boolean[] fetchCovers = new boolean[2];
    /** What criteria to search by. */
    private SearchEngine.SearchBy by;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String externalId;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private ISBN isbn;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String author;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String title;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String publisher;

    /**
     * Constructor. Will search according to passed parameters.
     * <ol>
     *      <li>external id</li>
     *      <li>valid ISBN</li>
     *      <li>valid barcode</li>
     *      <li>text</li>
     * </ol>
     *
     * @param context      Current context
     * @param taskId       a unique task identifier, returned with each message
     * @param searchEngine the search site engine
     * @param taskListener for the results
     */
    SearchTask(@NonNull final Context context,
               final int taskId,
               @NonNull final SearchEngine searchEngine,
               @NonNull final TaskListener<Book> taskListener) {
        super(taskId, TAG + ' ' + searchEngine.getName(context),
              taskListener);

        this.searchEngine = searchEngine;
        this.searchEngine.setCaller(this);
    }

    @NonNull
    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    @NonNull
    SearchEngine.SearchBy getSearchBy() {
        return by;
    }

    void setSearchBy(@NonNull final SearchEngine.SearchBy by) {
        this.by = by;
    }

    /**
     * Set/reset the criteria.
     *
     * @param externalId to search for
     */
    void setExternalId(@Nullable final String externalId) {
        this.externalId = externalId;
    }

    /**
     * Set/reset the criteria.
     *
     * @param isbn to search for
     */
    void setIsbn(@Nullable final ISBN isbn) {
        this.isbn = isbn;
    }

    /**
     * Set/reset the criteria.
     *
     * @param author to search for
     */
    void setAuthor(@Nullable final String author) {
        this.author = author;
    }

    /**
     * Set/reset the criteria.
     *
     * @param title to search for
     */
    void setTitle(@Nullable final String title) {
        this.title = title;
    }

    /**
     * Set/reset the criteria.
     *
     * @param publisher to search for
     */
    void setPublisher(@Nullable final String publisher) {
        this.publisher = publisher;
    }

    /**
     * Set/reset the criteria.
     *
     * @param fetchCovers Set to {@code true} if we want to get covers
     */
    void setFetchCovers(@Nullable final boolean[] fetchCovers) {
        if (fetchCovers == null || fetchCovers.length == 0) {
            this.fetchCovers[0] = false;
            this.fetchCovers[1] = false;
        } else if (fetchCovers.length == 1) {
            this.fetchCovers[0] = fetchCovers[0];
            this.fetchCovers[1] = false;
        } else {
            this.fetchCovers[0] = fetchCovers[0];
            this.fetchCovers[1] = fetchCovers[1];
        }
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
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        publishProgress(1, context.getString(R.string.progress_msg_searching_site,
                                             searchEngine.getName(context)));

        // Checking this each time a search starts is not needed...
        // But it makes error handling slightly easier and doing
        // it here offloads it from the UI thread.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        searchEngine.ping(context);

        @Nullable
        final String isbnStr;
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

        final Book book;
        switch (by) {
            case ExternalId:
                if (externalId == null || externalId.isEmpty()) {
                    throw new IllegalArgumentException("externalId=" + externalId);
                }
                book = ((SearchEngine.ByExternalId) searchEngine)
                        .searchByExternalId(context, externalId, fetchCovers);
                break;

            case Isbn:
                if (isbnStr == null || isbnStr.isEmpty()) {
                    throw new IllegalArgumentException("isbnStr=" + isbnStr);
                }
                book = ((SearchEngine.ByIsbn) searchEngine)
                        .searchByIsbn(context, isbnStr, fetchCovers);
                break;

            case Barcode:
                if (isbnStr == null || isbnStr.isEmpty()) {
                    throw new IllegalArgumentException("isbnStr=" + isbnStr);
                }
                book = ((SearchEngine.ByBarcode) searchEngine)
                        .searchByBarcode(context, isbnStr, fetchCovers);
                break;

            case Text:
                book = ((SearchEngine.ByText) searchEngine)
                        .search(context, isbnStr, author, title, publisher, fetchCovers);
                break;

            default:
                // we should never get here...
                throw new IllegalArgumentException("SearchEngine "
                                                   + searchEngine.getName(context)
                                                   + " does not implement By=" + by);
        }

        return book;
    }
}
