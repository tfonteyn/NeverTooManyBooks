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
package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.util.logger.LoggerFactory;

public class SearchBookByIsbnViewModel
        extends ViewModel {

    public static final int SEARCH_NOT_STARTED = 0;
    public static final int SEARCH_DUPLICATE_ISBN = -1;

    /** Log tag. */
    private static final String TAG = "SearchBookByIsbnViewModel";

    /** The {@link ScanMode} to start in. */
    public static final String BKEY_SCANNER_MODE = TAG + ":scanMode";

    /** The batch mode queue. */
    private final IsbnQueue scanQueue = new IsbnQueue();

    private final MutableLiveData<Iterator<IsbnQueue.Item>> scanQueueUpdate =
            new MutableLiveData<>();

    @NonNull
    private final EditBookOutput resultData = new EditBookOutput();
    /** Database Access. */
    private BookDao bookDao;

    private Style style;

    @NonNull
    private ScanMode scanMode = ScanMode.Off;

    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean firstStart = true;
    /** The raw text. The 'isStrict' flag is get/set directly with SharedPreferences. */
    @Nullable
    private String isbnText;

    @NonNull
    Intent createResultIntent() {
        return resultData.createResultIntent();
    }

    void onBookEditingDone(@NonNull final EditBookOutput data) {
        resultData.update(data);
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#getArguments()}
     */
    void init(@Nullable final Bundle args) {
        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            if (args != null) {
                final ScanMode tmpScanMode = args.getParcelable(BKEY_SCANNER_MODE);
                if (tmpScanMode != null) {
                    this.scanMode = tmpScanMode;
                }

                // Lookup the provided style or use the default if not found.
                final String styleUuid = args.getString(Style.BKEY_UUID);
                final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
                style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);
            }
        }
    }

    /**
     * Auto-start scanner the first time this fragment starts.
     *
     * @return flag
     */
    boolean isAutoStart() {
        if (scanMode != ScanMode.Off && firstStart) {
            firstStart = false;
            return true;
        }
        return false;
    }

    @NonNull
    Style getStyle() {
        Objects.requireNonNull(style, "style");
        return style;
    }

    @Nullable
    String getIsbnText() {
        return isbnText;
    }

    void setIsbnText(@Nullable final String isbnText) {
        this.isbnText = isbnText;
    }

    @NonNull
    ScanMode getScannerMode() {
        return scanMode;
    }

    void setScannerMode(@NonNull final ScanMode scanMode) {
        this.scanMode = scanMode;
        synchronized (scanQueue) {
            scanQueueUpdate.setValue(scanQueue.iterator());
        }
    }

    @NonNull
    List<Pair<Long, String>> getBookIdAndTitlesByIsbn(@NonNull final ISBN code) {
        return bookDao.getBookIdAndTitleByIsbn(code);
    }

    @NonNull
    LiveData<Iterator<IsbnQueue.Item>> onScanQueueUpdate() {
        return scanQueueUpdate;
    }

    boolean isQueueSearching() {
        return scanQueue.isSearching();
    }

    @NonNull
    Iterator<IsbnQueue.Item> getScanQueue() {
        return scanQueue.iterator();
    }

    /**
     * Add the list of items to the queue and start a search for them.
     * <p>
     * <strong>Does</strong> trigger {@link #onScanQueueUpdate}.
     *
     * @param context     current context
     * @param items       to add
     * @param startSearch method
     *
     * @return {@code true} if at least one item was added and a search started for it
     */
    boolean addToQueueAndStartSearch(@NonNull final Context context,
                                     @NonNull final List<IsbnQueue.Item> items,
                                     @NonNull final Function<ISBN, Integer> startSearch) {
        boolean atLeastOneStarted = false;
        synchronized (scanQueue) {
            for (final IsbnQueue.Item item : items) {
                final int searchId = addToQueueAndStartSearch(context, item, startSearch);
                if (searchId > 0) {
                    atLeastOneStarted = true;
                }
            }

            if (atLeastOneStarted) {
                scanQueueUpdate.setValue(scanQueue.iterator());
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Add a single item to the queue and start a search for it.
     * <p>
     * Does <strong>NOT</strong> trigger {@link #onScanQueueUpdate}.
     *
     * @param context     current context
     * @param item        to add
     * @param startSearch method
     *
     * @return the searchId, or:
     *         {@link #SEARCH_NOT_STARTED} if no search was started.
     *         This is not necessarily an error;
     *         {@link #SEARCH_DUPLICATE_ISBN} if the item ISBN was already present.
     */
    int addToQueueAndStartSearch(@NonNull final Context context,
                                 @NonNull final IsbnQueue.Item item,
                                 @NonNull final Function<ISBN, Integer> startSearch) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "addToQueueAndStartSearch", "item=" + item);
        }
        synchronized (scanQueue) {
            // duplicates are rejected
            if (scanQueue.containsIsbn(item)) {
                return SEARCH_DUPLICATE_ISBN;
            }
            // FIRST ADD at the end of the queue.
            scanQueue.add(context, item);
            // THEN START the search.
            final int searchId = startSearch.apply(item.getIsbn());
            if (searchId > 0) {
                item.setSearchId(searchId);
                scanQueueUpdate.setValue(scanQueue.iterator());
                return searchId;
            }
            // No search was started. Remove from the queue
            scanQueue.remove(context, item);
            return SEARCH_NOT_STARTED;
        }
    }

    /**
     * Start searches for all current items in the queue.
     * <p>
     * <strong>Does</strong> trigger {@link #onScanQueueUpdate}.
     *
     * @param context     current context
     * @param startSearch method
     *
     * @return {@code true} if at least one search was started
     */
    boolean startQueueSearches(@NonNull final Context context,
                               @NonNull final Function<ISBN, Integer> startSearch) {
        boolean atLeastOneStarted = false;
        synchronized (scanQueue) {
            final Iterator<IsbnQueue.Item> list = scanQueue.iterator();
            while (list.hasNext()) {
                final IsbnQueue.Item item = list.next();
                // not started yet?
                if (!item.isSearching()) {
                    final int searchId = startSearch.apply(item.getIsbn());
                    if (searchId > 0) {
                        item.setSearchId(searchId);
                        atLeastOneStarted = true;
                    } else {
                        // FAIL; simple remove
                        scanQueue.remove(context, item);
                    }
                }
            }
            scanQueueUpdate.setValue(scanQueue.iterator());
        }
        return atLeastOneStarted;
    }

    /**
     * Called when a search result came in for an item in the queue.
     * Updated the item and triggers a {@link #onScanQueueUpdate}.
     *
     * @param result received
     */
    void onQueueSearchResults(@NonNull final BookSearchResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onQueueSearchResults", "result=" + result);
        }
        synchronized (scanQueue) {
            scanQueue.bySearchId(result.getSearchId()).ifPresent(item -> {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    LoggerFactory.getLogger().d(TAG, "onQueueSearchResults|mapped",
                                                "result=" + result);
                }
                item.setResult(result);
                scanQueueUpdate.setValue(scanQueue.iterator());
            });
        }
    }

    /**
     * Remove all items from the queue; cancel any searches for them.
     * <p>
     * <strong>Does</strong> trigger {@link #onScanQueueUpdate()}.
     *
     * @param context     Current context
     * @param coordinator used to cancel searches
     * @param clear       flag
     */
    void clearQueueAndCancelSearches(@NonNull final Context context,
                                     @NonNull final SearchCoordinator coordinator,
                                     final boolean clear) {
        synchronized (scanQueue) {
            coordinator.cancel();
            if (clear) {
                scanQueue.clear(context);
            }
            scanQueueUpdate.setValue(scanQueue.iterator());
        }
    }

    /**
     * Remove the given code from the queue; cancel any searches for it.
     * <p>
     * Does <strong>NOT</strong> trigger {@link #onScanQueueUpdate()}.
     *
     * @param context     Current context
     * @param coordinator used to cancel searches
     * @param item        to remove/cancel
     */
    void removeFromQueueAndCancelSearch(@NonNull final Context context,
                                        @NonNull final SearchCoordinator coordinator,
                                        @NonNull final IsbnQueue.Item item) {
        synchronized (scanQueue) {
            // don't care about the result, we're discarding the whole item
            final int searchId = item.getSearchId();
            if (searchId > 0) {
                coordinator.cancelSearch(searchId);
            }
            scanQueue.remove(context, item);
        }
    }
}
