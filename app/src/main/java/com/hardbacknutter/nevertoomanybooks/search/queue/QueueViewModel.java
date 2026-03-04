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
package com.hardbacknutter.nevertoomanybooks.search.queue;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.utils.Code;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.util.logger.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public abstract class QueueViewModel<CODE extends Code>
        extends ViewModel {

    /** Return code from {@link #addToQueueAndStartSearch(QueuedItem, Function)}. */
    public static final int SEARCH_NOT_STARTED = 0;
    /** Return code from {@link #addToQueueAndStartSearch(QueuedItem, Function)}. */
    public static final int SEARCH_DUPLICATE_ITEM = -1;

    /** Log tag. */
    private static final String TAG = "QueueViewModel";

    private final MutableLiveData<Iterator<QueuedItem<CODE>>> queueUpdate =
            new MutableLiveData<>();

    private final Object queueLock = new Object();

    /** The batch mode queue. */
    @GuardedBy("queueLock")
    private ItemQueue<CODE> queue;

    /**
     * Pseudo constructor.
     * <p>
     * Must call {@link #init(String, Function)}.
     */
    public abstract void init();

    protected void init(@NonNull final String pkQueue,
                        @NonNull final Function<String, CODE> codeFactory) {
        if (queue == null) {
            queue = new ItemQueue<>(pkQueue, codeFactory);
        }
    }

    /**
     * Observer for queue updates.
     *
     * @return an iterator over the queue
     */
    @NonNull
    public LiveData<Iterator<QueuedItem<CODE>>> onQueueUpdate() {
        return queueUpdate;
    }

    /**
     * Whether any item in the queue is actively running a search.
     *
     * @return flag
     */
    public boolean isQueueSearching() {
        return queue.isSearching();
    }

    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Get an iterator over the queue.
     *
     * @return iterator
     */
    @NonNull
    public Iterator<QueuedItem<CODE>> getQueue() {
        return queue.iterator();
    }

    /**
     * Add the list of items to the queue and start a search for them.
     * <p>
     * <strong>Does</strong> trigger {@link #onQueueUpdate}.
     *
     * @param items       to add
     * @param startSearch method
     *
     * @return {@code true} if at least one item was added and a search started for it
     */
    public boolean addToQueueAndStartSearch(@NonNull final List<QueuedItem<CODE>> items,
                                            @NonNull final Function<CODE, Integer> startSearch) {
        boolean atLeastOneStarted = false;
        synchronized (queueLock) {
            for (final QueuedItem<CODE> item : items) {
                final int searchId = addToQueueAndStartSearch(item, startSearch);
                if (searchId > 0) {
                    atLeastOneStarted = true;
                }
            }

            if (atLeastOneStarted) {
                queueUpdate.setValue(queue.iterator());
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Add a single item to the queue and start a search for it.
     * <p>
     * Does <strong>NOT</strong> trigger {@link #onQueueUpdate}.
     *
     * @param item        to add
     * @param startSearch method
     *
     * @return the searchId, or:
     *         {@link #SEARCH_NOT_STARTED} if no search was started.
     *         This is not necessarily an error;
     *         {@link #SEARCH_DUPLICATE_ITEM} if the item was already present.
     */
    public int addToQueueAndStartSearch(@NonNull final QueuedItem<CODE> item,
                                        @NonNull final Function<CODE, Integer> startSearch) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "addToQueueAndStartSearch", "item=" + item);
        }
        synchronized (queueLock) {
            // duplicates are rejected
            if (queue.contains(item.getCode())) {
                return SEARCH_DUPLICATE_ITEM;
            }
            // FIRST ADD at the end of the queue.
            queue.add(item);
            // THEN START the search.
            final int searchId = startSearch.apply(item.getCode());
            if (searchId > 0) {
                item.setSearchId(searchId);
                queueUpdate.setValue(queue.iterator());
                return searchId;
            }
            // No search was started. Remove from the queue
            queue.remove(item);
            return SEARCH_NOT_STARTED;
        }
    }

    /**
     * Start searches for all current items in the queue.
     * <p>
     * <strong>Does</strong> trigger {@link #onQueueUpdate}.
     *
     * @param startSearch method
     *
     * @return {@code true} if at least one search was started
     */
    public boolean startQueueSearches(@NonNull final Function<CODE, Integer> startSearch) {
        boolean atLeastOneStarted = false;
        synchronized (queueLock) {
            final Iterator<QueuedItem<CODE>> list = queue.iterator();
            while (list.hasNext()) {
                final QueuedItem<CODE> item = list.next();
                // not started yet?
                if (!item.isSearching()) {
                    final int searchId = startSearch.apply(item.getCode());
                    if (searchId > 0) {
                        item.setSearchId(searchId);
                        atLeastOneStarted = true;
                    } else {
                        // FAIL; simple remove
                        queue.remove(item);
                    }
                }
            }
            queueUpdate.setValue(queue.iterator());
        }
        return atLeastOneStarted;
    }

    /**
     * Called when a search result came in for an item in the queue.
     * Updated the item and triggers a {@link #onQueueUpdate}.
     *
     * @param result received
     */
    public void onQueueSearchResults(@NonNull final BookSearchResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onQueueSearchResults", "result=" + result);
        }
        synchronized (queueLock) {
            queue.bySearchId(result.getSearchId()).ifPresent(item -> {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    LoggerFactory.getLogger().d(TAG, "onQueueSearchResults|mapped",
                                                "result=" + result);
                }
                item.setResult(result);
                queueUpdate.setValue(queue.iterator());
            });
        }
    }

    /**
     * Remove all items from the queue; cancel any searches for them.
     * <p>
     * <strong>Does</strong> trigger {@link #onQueueUpdate()}.
     *
     * @param coordinator used to cancel searches
     * @param clear       flag
     */
    public void clearQueueAndCancelSearches(@NonNull final SearchCoordinator coordinator,
                                            final boolean clear) {
        synchronized (queueLock) {
            coordinator.cancel();
            if (clear) {
                queue.clear();
            }
            queueUpdate.setValue(queue.iterator());
        }
    }

    /**
     * Remove the given code from the queue; cancel any searches for it.
     * <p>
     * Does <strong>NOT</strong> trigger {@link #onQueueUpdate()}.
     *
     * @param coordinator used to cancel searches
     * @param item        to remove/cancel
     */
    public void removeFromQueueAndCancelSearch(@NonNull final SearchCoordinator coordinator,
                                               @NonNull final QueuedItem<CODE> item) {
        synchronized (queueLock) {
            // don't care about the result, we're discarding the whole item
            final int searchId = item.getSearchId();
            if (searchId > 0) {
                coordinator.cancelSearch(searchId);
            }
            queue.remove(item);
        }
    }

    /**
     * See {@link ItemQueue#readFromFile(Context, Uri)}.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @return list
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    public List<QueuedItem<CODE>> readFromFile(@NonNull final Context context,
                                               @NonNull final Uri uri)
            throws IOException {
        return queue.readFromFile(context, uri);
    }

    /**
     * See {@link ItemQueue#readFromPreferences()}.
     *
     * @return list
     */
    @NonNull
    public List<QueuedItem<CODE>> readFromPreferences() {
        return queue.readFromPreferences();
    }

    /**
     * See {@link ItemQueue#clearPreferences()}.
     */
    public void clearPreferences() {
        queue.clearPreferences();
    }
}
