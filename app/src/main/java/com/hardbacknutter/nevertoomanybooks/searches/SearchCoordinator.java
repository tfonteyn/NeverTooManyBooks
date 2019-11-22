/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Class to co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #mActiveTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link SearchCoordinatorListener}.
 * <p>
 * The {@link Site#id} is used as the task id.
 *
 * <strong>Note:</strong> primarily used as a ViewModel by the regular searches.
 * However, {@link UpdateFieldsTask} uses this as a pure object/listener for now.
 */
public class SearchCoordinator
        extends ViewModel
        implements ProgressDialogFragment.Cancellable {

    /** log tag. */
    private static final String TAG = "SearchCoordinator";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int TO_MILLIS = 1_000_000;

    /** List of Tasks being managed by *this* object. */
    @NonNull
    private final Collection<SearchTask> mActiveTasks = new HashSet<>();

    /**
     * Results from the search tasks.
     * <p>
     * key: site id (== task id)
     */
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, Bundle> mSearchResults
            = Collections.synchronizedMap(new HashMap<>());
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, TaskListener.FinishMessage<Bundle>> mSearchFinishedMessages
            = Collections.synchronizedMap(new HashMap<>());
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, TaskListener.ProgressMessage> mSearchProgressMessages
            = Collections.synchronizedMap(new HashMap<>());

    /** Mappers to apply. */
    @NonNull
    private final Collection<Mapper> mMappers = new ArrayList<>();
    @Nullable
    private WeakReference<SearchCoordinatorListener> mSearchSearchCoordinatorListener;
    /** Sites to search on. If this list is empty, all searches will return {@code false}. */
    private ArrayList<Site> mSearchSites;
    /** Base message for progress updates. */
    private String mBaseMessage;
    /** Flag */
    private boolean mIsSearchActive;
    /** Flag indicating we're shutting down. */
    private boolean mIsCancelled;
    /** Indicates original ISBN is really present and valid. */
    private boolean mHasValidIsbn;
    /** Whether of not to fetch thumbnails. */
    private boolean mFetchThumbnail;
    /** Flag indicating searches will be non-concurrent title/author found via ASIN. */
    private boolean mSearchingAsin;
    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean mWaitingForIsbn;

    /** Original ISBN for search. */
    @NonNull
    private String mIsbnSearchText = "";
    /** Site native id for search. */
    @NonNull
    private String mNativeIdSearchText = "";
    /** Original author for search. */
    @NonNull
    private String mAuthorSearchText = "";
    /** Original title for search. */
    @NonNull
    private String mTitleSearchText = "";
    /** Original publisher for search. */
    @NonNull
    private String mPublisherSearchText = "";

    /** Accumulated book data. */
    private Bundle mBookData;
    private long mSearchStartTime;
    private Map<Integer, Long> mSearchTasksStartTime;
    private Map<Integer, Long> mSearchTasksEndTime;

    /** Listen for <strong>individual</strong> search tasks. */
    private final TaskListener<Bundle> mSearchTaskListener = new TaskListener<Bundle>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Bundle> message) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                mSearchTasksEndTime.put(message.taskId, System.nanoTime());
            }

            switch (message.status) {
                case Cancelled:
                case Failed:
                    // Store for later
                    if (message.exception != null) {
                        mSearchFinishedMessages.put(message.taskId, message);
                    }
                    break;

                case Success:
                    // Store for later
                    mSearchResults.put(message.taskId, message.result);
                    break;
            }

            // clear obsolete progress status
            synchronized (mSearchProgressMessages) {
                mSearchProgressMessages.remove(message.taskId);
            }
            // and update our listener.
            if (mSearchSearchCoordinatorListener.get() != null) {
                mSearchSearchCoordinatorListener.get().onProgress(accumulateProgress());
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "mSearchTaskListener.onFinished|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }

            // queue another task as needed.
            onSearchTaskFinished(message.result);

            int tasksActive;
            // Remove the finished task from our list
            synchronized (mActiveTasks) {
                for (SearchTask searchTask : mActiveTasks) {
                    if (searchTask.getId() == message.taskId) {
                        mActiveTasks.remove(searchTask);
                        break;
                    }
                }

                tasksActive = mActiveTasks.size();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "mSearchTaskListener.onFinished|finished="
                               + SearchSites.getName(message.taskId));
                    for (SearchTask searchTask : mActiveTasks) {
                        Log.d(TAG, "mSearchTaskListener.onFinished|running="
                                   + SearchSites.getName(searchTask.getId()));
                    }
                }
            }
            // no more tasks ? Then send the results back to our creator.
            if (tasksActive == 0) {
                long processTime = System.nanoTime();

                accumulateResults();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    for (Map.Entry<Integer, Long> entry : mSearchTasksStartTime.entrySet()) {
                        String name = SearchSites.getName(entry.getKey());
                        long start = entry.getValue();
                        Long end = mSearchTasksEndTime.get(entry.getKey());
                        if (end != null) {
                            Log.d(TAG, String.format(Locale.UK,
                                                     "mSearchTaskListener.onFinished"
                                                     + "|taskId=%20s:%10d ms",
                                                     name, (end - start) / TO_MILLIS));
                        } else {
                            Log.d(TAG, String.format(Locale.UK,
                                                     "mSearchTaskListener.onFinished"
                                                     + "|task=%20s|never finished",
                                                     name));
                        }
                    }

                    Log.d(TAG, String.format(Locale.UK,
                                             "mSearchTaskListener.onFinished"
                                             + "|total search time: %10d ms",
                                             (processTime - mSearchStartTime) / TO_MILLIS));
                    Log.d(TAG, String.format(Locale.UK,
                                             "mSearchTaskListener.onFinished"
                                             + "|processing time: %10d ms",
                                             (System.nanoTime() - processTime) / TO_MILLIS));
                }

                // All done, deliver the data.
                mIsSearchActive = false;
                String searchErrors = accumulateErrors();

                Log.d(TAG, "mSearchTaskListener.onFinished"
                           + "|wasCancelled=" + mIsCancelled
                           + "|searchErrors=" + searchErrors);

                if (mSearchSearchCoordinatorListener.get() != null) {
                    if (mIsCancelled) {
                        mSearchSearchCoordinatorListener.get().onCancelled();
                    } else {
                        mSearchSearchCoordinatorListener.get().onFinished(mBookData, searchErrors);
                    }
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                        Log.d(TAG, "onFinished|" + Logger.WEAK_REFERENCE_DEAD);
                    }
                }
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            synchronized (mSearchProgressMessages) {
                mSearchProgressMessages.put(message.taskId, message);
            }
            // forward the accumulated progress
            if (mSearchSearchCoordinatorListener.get() != null) {
                mSearchSearchCoordinatorListener.get().onProgress(accumulateProgress());
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onFinished|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }
    };

    @Override
    protected void onCleared() {
        cancel(false);
    }

    /**
     * Pseudo constructor.
     *
     * @param args     {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     * @param listener to send results to
     */
    @SuppressLint("UseSparseArrays")
    public void init(@Nullable final Bundle args,
                     @NonNull final SearchCoordinatorListener listener) {

        mSearchSearchCoordinatorListener = new WeakReference<>(listener);

        if (mSearchSites == null) {

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                mSearchTasksStartTime = new HashMap<>();
                mSearchTasksEndTime = new HashMap<>();
            }
            // don't pass a context to init().
            // We will keep hold of it, so must use the app context.
            Context context = App.getLocalizedAppContext();

            if (FormatMapper.isMappingAllowed(context)) {
                mMappers.add(new FormatMapper(context));
            }
            if (ColorMapper.isMappingAllowed(context)) {
                mMappers.add(new ColorMapper(context));
            }

            if (args != null) {
                mFetchThumbnail = App.isUsed(UniqueId.BKEY_IMAGE);

                mIsbnSearchText = args.getString(DBDefinitions.KEY_ISBN, "");

                //TODO: we'd need to pass a site to make this useful
                //mNativeIdSearchText = args.getString(UniqueId.BKEY_BOOK_NATIVE_ID, "");

                mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");

                mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE, "");
                mPublisherSearchText = args.getString(DBDefinitions.KEY_PUBLISHER, "");

                // use global preference.
                mSearchSites = SearchSites.getSites(context, SearchSites.ListType.Data);
            }
        }
    }

    public boolean isSearchActive() {
        return mIsSearchActive;
    }

    /**
     * Cancel all searches.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *                              task should be interrupted; otherwise,
     *                              in-progress tasks are allowed to complete
     *
     * @return {@code true}
     */
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        mIsCancelled = true;
        synchronized (mActiveTasks) {
            for (SearchTask searchTask : mActiveTasks) {
                searchTask.cancel(mayInterruptIfRunning);
            }
        }
        return true;
    }


    private void onSearchTaskFinished(@NonNull final Bundle bookData) {
        // If we searched AMAZON for an Asin, then see what we found
        if (mSearchingAsin) {
            mSearchingAsin = false;
            // clear the ASIN
            mIsbnSearchText = "";
            if (hasIsbn(bookData)) {
                // We got an ISBN, so pretend we were searching for an ISBN
                // and fall through to the next block.
                mWaitingForIsbn = true;
            } else {
                // See if we got author/title
                mAuthorSearchText = bookData.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");
                mTitleSearchText = bookData.getString(DBDefinitions.KEY_TITLE, "");
                if (!mAuthorSearchText.isEmpty() && !mTitleSearchText.isEmpty()) {
                    // We got them, so pretend we are searching by author/title now,
                    // and waiting for an ISBN...
                    mWaitingForIsbn = true;
                }
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "onSearchTaskFinished|was SearchingAsin"
                           + "|mWaitingForIsbn=" + mWaitingForIsbn);
            }
        }

        if (mWaitingForIsbn) {
            if (hasIsbn(bookData)) {
                mWaitingForIsbn = false;
                mIsbnSearchText = bookData.getString(DBDefinitions.KEY_ISBN, "");

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "onSearchTaskFinished|isbn=" + mIsbnSearchText);
                }

                // Start the others...even if they have run before.
                // They will redo the search with the ISBN.
                startSearch(mSearchSites);
            } else {
                // Start next one that has not run.
                startNextSearch();
            }
        }
    }


    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list
     */
    @NonNull
    public ArrayList<Site> getSearchSites() {
        return mSearchSites;
    }

    /**
     * Override the initial list.
     *
     * @param searchSites to use temporarily
     */
    public void setSearchSites(@NonNull final ArrayList<Site> searchSites) {
        mSearchSites = searchSites;
    }

    /**
     * Indicate we want a thumbnail.
     *
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    public void setFetchThumbnail(final boolean fetchThumbnail) {
        mFetchThumbnail = fetchThumbnail;
    }

    public void clearSearchText() {
        mNativeIdSearchText = "";
        mIsbnSearchText = "";
        mAuthorSearchText = "";
        mTitleSearchText = "";
        mPublisherSearchText = "";
    }

    @NonNull
    public String getAuthorSearchText() {
        return mAuthorSearchText;
    }

    public void setAuthorSearchText(@NonNull final String authorSearchText) {
        mAuthorSearchText = authorSearchText;
    }

    @NonNull
    public String getTitleSearchText() {
        return mTitleSearchText;
    }

    public void setTitleSearchText(@NonNull final String titleSearchText) {
        mTitleSearchText = titleSearchText;
    }

    @NonNull
    public String getPublisherSearchText() {
        return mPublisherSearchText;
    }

    public void setPublisherSearchText(@NonNull final String publisherSearchText) {
        mPublisherSearchText = publisherSearchText;
    }

    @NonNull
    public String getIsbnSearchText() {
        return mIsbnSearchText;
    }

    public void setIsbnSearchText(@NonNull final String isbnSearchText) {
        // intercept UPC numbers
        mIsbnSearchText = ISBN.upc2isbn(isbnSearchText);
    }

    @NonNull
    public String getNativeIdSearchText() {
        return mNativeIdSearchText;
    }

    public void setNativeIdSearchText(@NonNull final String nativeIdSearchText) {
        mNativeIdSearchText = nativeIdSearchText;
    }

    /**
     * Search a single site with the site specific book id.
     *
     * @param site to search
     *
     * @return {@code true} if the search was started.
     */
    public boolean searchByNativeId(@NonNull final Site site) {
        // sanity check
        if (mNativeIdSearchText.isEmpty()) {
            throw new IllegalStateException("mNativeId was empty");
        }

        // clear criteria NOT used by this search.
        mIsbnSearchText = "";
        mAuthorSearchText = "";
        mTitleSearchText = "";
        mPublisherSearchText = "";
        prepareSearch();

        return startSearch(site);
    }

    /**
     * Start a search.
     * At least one of isbn,title,author must be not be empty.
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean searchByText() {
        // clear criteria NOT used by this search.
        mNativeIdSearchText = "";
        prepareSearch();

        if (!mIsbnSearchText.isEmpty()) {
            // We really want to ensure we get the same book from each, so if the isbn is
            // not present, search the sites one at a time until we get an isbn
            mWaitingForIsbn = false;
            if (mHasValidIsbn) {
                mIsSearchActive = startSearch(mSearchSites);

            } else if (SearchSites.ENABLE_AMAZON_AWS) {
                // Assume it's an ASIN, and just search Amazon
                mSearchingAsin = true;
                Collection<Site> amazon = new ArrayList<>();
                amazon.add(Site.newSite(SearchSites.AMAZON));
                mIsSearchActive = startSearch(amazon);
            }
        } else {
            // Run one at a time until we find an ISBN. This in return will start
            // a new search using that ISBN.
            mWaitingForIsbn = true;
            mIsSearchActive = startNextSearch();
        }

        return mIsSearchActive;
    }

    void setBaseMessage(@Nullable final String baseMessage) {
        mBaseMessage = baseMessage;
    }

    /**
     * Check if passed Bundle contains a non-blank ISBN string. Does not check if the ISBN is valid.
     *
     * @param bundle to check
     *
     * @return Present/absent
     */
    private boolean hasIsbn(@NonNull final Bundle bundle) {
        String s = bundle.getString(DBDefinitions.KEY_ISBN);
        return s != null && !s.trim().isEmpty();
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     */
    private void prepareSearch() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchStartTime = System.nanoTime();
        }

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!NetworkUtils.isNetworkAvailable(App.getAppContext())) {
                throw new IllegalStateException("network should be checked before starting search");
            }

            if (!mActiveTasks.isEmpty()) {
                throw new IllegalStateException("a search is already running");
            }

            // Note we don't care about publisher.
            if (mAuthorSearchText.isEmpty()
                && mTitleSearchText.isEmpty()
                && mIsbnSearchText.isEmpty()
                && mNativeIdSearchText.isEmpty()) {
                throw new IllegalArgumentException("Must specify at least one non-empty criteria|"
                                                   + "|mNativeId=" + mNativeIdSearchText
                                                   + "|mIsbn=" + mIsbnSearchText
                                                   + "|mAuthor=" + mAuthorSearchText
                                                   + "|mTitle=" + mTitleSearchText
                                                   + "|mPublisher=" + mPublisherSearchText);
            }
        }

        // reset flags
        mWaitingForIsbn = false;
        mSearchingAsin = false;
        mIsCancelled = false;
        mIsSearchActive = false;

        if (mFetchThumbnail) {
            // each site might have a cover, but when accumulating all covers found,
            // we rename the 'best' to the standard name. So make sure to
            // delete any orphaned temporary cover file
            StorageUtils.deleteFile(StorageUtils.getTempCoverFile());
        }

        mBookData = new Bundle();
        mSearchResults.clear();
        mSearchFinishedMessages.clear();

        mHasValidIsbn = ISBN.isValid(mIsbnSearchText);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "prepareSearch"
                       + "|mNativeId=" + mNativeIdSearchText
                       + "|mIsbn=" + mIsbnSearchText
                       + "|mHasValidIsbn=" + mHasValidIsbn
                       + "|mAuthor=" + mAuthorSearchText
                       + "|mTitle=" + mTitleSearchText
                       + "|mPublisher=" + mPublisherSearchText);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchTasksStartTime.clear();
            mSearchTasksEndTime.clear();
        }
    }

    /**
     * Start a single task.
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNextSearch() {
        // if mSearchSites is empty, we return false.
        for (Site site : mSearchSites) {
            if (site.isEnabled()) {
                // If the site has not been searched yet, search it
                if (!mSearchResults.containsKey(site.id)) {
                    return startSearch(site);
                }
            }
        }
        return false;
    }

    /**
     * Start all searches in in the given list which have not been run yet.
     *
     * <strong>Note:</strong> we explicitly pass in the searchSites instead of using the global
     * so we can force an Amazon-only search (ASIN based) when needed.
     *
     * @param currentSearchSites sites to search, see {@link SearchSites#SEARCH_FLAG_MASK}
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearch(@NonNull final Iterable<Site> currentSearchSites) {
        // if currentSearchSites is empty, we return false.
        boolean atLeastOneStarted = false;
        for (Site site : currentSearchSites) {
            if (site.isEnabled()) {
                // If the site has not been searched yet, search it
                if (!mSearchResults.containsKey(site.id)) {
                    if (startSearch(site)) {
                        atLeastOneStarted = true;
                    }
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Check if the given site is enabled.
     *
     * @param siteId to check
     *
     * @return {@code true} if enabled
     */
    public boolean isEnabled(final int siteId) {
        return (SearchSites.getEnabledSites(mSearchSites) & siteId) != 0;
    }

    /**
     * Start specific search.
     *
     * @param site to search
     *
     * @return {@code true} if the search was started.
     */
    private boolean startSearch(@NonNull final Site site) {
        // refuse new searches if we're shutting down.
        if (mIsCancelled) {
            return false;
        }

        SearchEngine searchEngine = site.getSearchEngine();
        if (!searchEngine.isAvailable()) {
            return false;
        }

        boolean canSearch =
                // if we have a native id, and the engine supports it, we can search.
                (!mNativeIdSearchText.isEmpty()
                 && (searchEngine instanceof SearchEngine.ByNativeId))
                ||
                // If have a valid ISBN, ...
                (mHasValidIsbn && (searchEngine instanceof SearchEngine.ByIsbn))
                ||
                // If we have valid text to search on, ...
                (((!mAuthorSearchText.isEmpty() && !mTitleSearchText.isEmpty())
                  || !mIsbnSearchText.isEmpty())
                 && (searchEngine instanceof SearchEngine.ByText));

        if (!canSearch) {
            return false;
        }

        SearchTask task = new SearchTask(site.id, searchEngine, mSearchTaskListener);

        task.setFetchThumbnail(mFetchThumbnail);

        task.setNativeId(mNativeIdSearchText);
        task.setIsbn(mIsbnSearchText);
        task.setAuthor(mAuthorSearchText);
        task.setTitle(mTitleSearchText);
        task.setPublisher(mPublisherSearchText);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchTasksStartTime.put(task.getId(), System.nanoTime());
        }

        synchronized (mActiveTasks) {
            mActiveTasks.add(task);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "startSearch|site=" + site);
        }
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        mIsSearchActive = true;
        return true;
    }

    /**
     * Accumulate data from all sites.
     */
    private void accumulateResults() {
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final Collection<Integer> sites = new ArrayList<>();

        // determine the order of the sites which should give us the most reliable data.
        if (mHasValidIsbn) {
            // If ISBN was passed, ignore entries with the wrong ISBN,
            // and put entries without ISBN at the end
            final Collection<Integer> uncertain = new ArrayList<>();
            for (Site site : SearchSites.getReliabilityOrder()) {
                if (mSearchResults.containsKey(site.id)) {
                    Bundle bookData = mSearchResults.get(site.id);
                    if (bookData != null && bookData.containsKey(DBDefinitions.KEY_ISBN)) {
                        String isbnFound = bookData.getString(DBDefinitions.KEY_ISBN);

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "accumulateResults|ISBN.matches"
                                       + "|mIsbn" + mIsbnSearchText
                                       + "|isbnFound" + isbnFound);
                        }

                        if (ISBN.matches(mIsbnSearchText, isbnFound, true)) {
                            sites.add(site.id);
                        }
                    } else {
                        uncertain.add(site.id);
                    }
                }
            }
            // good results come first, less reliable ones last in the list.
            sites.addAll(uncertain);
            // Add the passed ISBN first;
            // avoids overwriting with potentially different isbn from the sites
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbnSearchText);

        } else {
            // If ISBN was not passed, then just use the default order
            for (Site site : SearchSites.getReliabilityOrder()) {
                sites.add(site.id);
            }
        }

        // Merge the data we have. We do this in a fixed order rather than as the threads finish.
        for (int siteId : sites) {
            accumulateSiteData(siteId);
        }

        //ENHANCE: for now, we need to compress the list of publishers into a single String.
        if (mBookData.containsKey(UniqueId.BKEY_PUBLISHER_ARRAY)) {
            ArrayList<Publisher> publishers =
                    mBookData.getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
            if (publishers != null && !publishers.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Publisher publisher : publishers) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(" - ");
                    }
                    sb.append(publisher.getName());
                }
                mBookData.putString(DBDefinitions.KEY_PUBLISHER, sb.toString());
            }
            mBookData.remove(UniqueId.BKEY_PUBLISHER_ARRAY);
        }

        // run the mappers
        for (Mapper mapper : mMappers) {
            mapper.map(mBookData);
        }

        // If there are thumbnails present, pick the biggest, delete others and rename.
        ImageUtils.cleanupImages(mBookData);

        // If we did not get an ISBN, use the originally we searched for.
        String isbn = mBookData.getString(DBDefinitions.KEY_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbnSearchText);
        }

        // If we did not get an title, use the originally we searched for.
        String title = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_TITLE, mTitleSearchText);
        }
    }

    /**
     * Accumulate all data from the given site.
     * <p>
     * Copies data from passed Bundle to current accumulated data.
     * Does some careful processing of the data.
     * <p>
     * The Bundle will contain by default only String and ArrayList based data.
     * <p>
     * NEWTHINGS: if you add a new Search task that adds non-string based data, handle that here.
     *
     * @param siteId site
     */
    private void accumulateSiteData(@SearchSites.Id final int siteId) {
        Bundle siteData = mSearchResults.get(siteId);
        if (siteData == null || siteData.isEmpty()) {
            return;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "accumulateSiteData|site=" + SearchSites.getName(siteId));
        }
        for (String key : siteData.keySet()) {
            if (DBDefinitions.KEY_DATE_PUBLISHED.equals(key)
                || DBDefinitions.KEY_DATE_FIRST_PUBLICATION.equals(key)) {
                accumulateDates(key, siteData);

            } else if (UniqueId.BKEY_AUTHOR_ARRAY.equals(key)
                       || UniqueId.BKEY_SERIES_ARRAY.equals(key)
                       || UniqueId.BKEY_PUBLISHER_ARRAY.equals(key)
                       || UniqueId.BKEY_TOC_ENTRY_ARRAY.equals(key)
                       || UniqueId.BKEY_FILE_SPEC_ARRAY.equals(key)) {
                accumulateList(key, siteData);

            } else {
                // handle all normal String based entries
                accumulateStringData(key, siteData);
            }
        }
    }

    /**
     * Accumulate String data.
     * Handles other types via a .toString()
     *
     * @param key      Key of data
     * @param siteData Source Bundle
     */
    private void accumulateStringData(@NonNull final String key,
                                      @NonNull final Bundle siteData) {
        Object dataToAdd = siteData.get(key);
        if (dataToAdd == null || dataToAdd.toString().trim().isEmpty()) {
            return;
        }

        String dest = mBookData.getString(key);
        if (dest == null || dest.isEmpty()) {
            // just use it
            mBookData.putString(key, dataToAdd.toString());
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateStringData|copied"
                           + "|key=" + key + "|value=`" + dataToAdd + '`');
            }
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateStringData|skipping|key=" + key);
            }
        }
    }

    /**
     * Grabs the 'new' date and checks if it's parsable.
     * If so, then check if the previous date was actually valid at all.
     * if not, use new date.
     */
    private void accumulateDates(@NonNull final String key,
                                 @NonNull final Bundle siteData) {
        String currentDateHeld = mBookData.getString(key);
        String dataToAdd = siteData.getString(key);

        if (currentDateHeld == null || currentDateHeld.isEmpty()) {
            // copy, even if the incoming date might not be valid.
            // We'll deal with that later.
            mBookData.putString(key, dataToAdd);

        } else {
            // Overwrite with the new date if we can parse it and
            // if the current one was present but not valid.
            if (dataToAdd != null) {
                Date newDate = DateUtils.parseDate(dataToAdd);
                if (newDate != null) {
                    if (DateUtils.parseDate(currentDateHeld) == null) {
                        String value = DateUtils.utcSqlDate(newDate);
                        // current date was invalid, use new one.
                        mBookData.putString(key, value);
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "accumulateDates|copied"
                                       + "|key=" + key + "|value=`" + dataToAdd + '`');
                        }
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "accumulateDates|skipped|key=" + key);
                        }
                    }
                }
            }
        }
    }

    /**
     * Accumulate ParcelableArrayList data.
     * Add if not present, or append.
     *
     * @param key      Key of data
     * @param siteData Source bundle with a ParcelableArrayList for the key
     * @param <T>      type of items in the ArrayList
     */
    private <T extends Parcelable> void accumulateList(@NonNull final String key,
                                                       @NonNull final Bundle siteData) {
        ArrayList<T> dataToAdd = siteData.getParcelableArrayList(key);
        if (dataToAdd == null || dataToAdd.isEmpty()) {
            return;
        }

        ArrayList<T> dest = mBookData.getParcelableArrayList(key);
        if (dest == null || dest.isEmpty()) {
            // just copy
            dest = dataToAdd;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateList|copied"
                           + "|key=" + key + "|value=`" + dataToAdd + '`');
            }
        } else {
            // append
            dest.addAll(dataToAdd);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateList|appended"
                           + "|key=" + key + "|value=`" + dataToAdd + '`');
            }
        }
        mBookData.putParcelableArrayList(key, dest);
    }

    /**
     * Called when all is said and done. Collects all individual website errors (if any)
     * into a single user-formatted message.
     *
     * @return the error message
     */
    private String accumulateErrors() {
        String errorMessage = null;
        if (!mSearchFinishedMessages.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, TaskListener.FinishMessage<Bundle>>
                    error : mSearchFinishedMessages.entrySet()) {
                String siteError = createSiteError(error.getKey(), error.getValue());
                sb.append(siteError).append('\n');
            }
            errorMessage = sb.toString();
        }
        //no longer needed
        mSearchFinishedMessages.clear();
        return errorMessage;
    }

    /**
     * Prepare an error message to show after the task finishes.
     *
     * @param siteId  the site id
     * @param message to digest
     *
     * @return user-friendly error message for the given site
     */
    private String createSiteError(@StringRes final int siteId,
                                   @NonNull final TaskListener.FinishMessage<Bundle> message) {

        Context context = App.getLocalizedAppContext();
        String siteName = SearchSites.getName(siteId);
        String text;

        switch (message.status) {
            case Cancelled:
                text = context.getString(R.string.progress_end_cancelled);
                break;

            case Failed: {
                @StringRes
                int messageId;
                if (message.exception instanceof CredentialsException) {
                    messageId = R.string.error_authentication_failed;
                } else if (message.exception instanceof SocketTimeoutException) {
                    messageId = R.string.error_network_timeout;
                } else if (message.exception instanceof MalformedURLException) {
                    messageId = R.string.error_search_failed_network;
                } else if (message.exception instanceof UnknownHostException) {
                    messageId = R.string.error_search_failed_network;
                } else if (message.exception instanceof IOException) {
                    messageId = R.string.error_search_failed_network;
                } else {
                    messageId = R.string.error_unknown;
                }

                text = context.getString(messageId);

                if (message.exception != null) {
                    String eMsg;
                    if (message.exception instanceof FormattedMessageException) {
                        eMsg = ((FormattedMessageException) message.exception)
                                .getLocalizedMessage(context);
                    } else {
                        eMsg = message.exception.getLocalizedMessage();
                    }

                    if (eMsg != null) {
                        text += "\n\n" + eMsg;
                    }
                }
                break;
            }
            case Success:
            default:
                // we shouldn't get here
                return "\n";
        }

        return context.getString(R.string.error_search_x_failed_y, siteName, text);
    }

    /**
     * Creates {@link TaskListener.ProgressMessage} with the global/total progress of all tasks.
     */
    @NonNull
    private TaskListener.ProgressMessage accumulateProgress() {

        // Sum the current & max values for each active task.
        int progressMax = 0;
        int progressCount = 0;

        // Start with the base message if we have one.
        StringBuilder message;
        if (mBaseMessage != null && !mBaseMessage.isEmpty()) {
            message = new StringBuilder(mBaseMessage);
        } else {
            message = new StringBuilder();
        }

        synchronized (mSearchProgressMessages) {
            if (!mSearchProgressMessages.isEmpty()) {
                // if there was a baseMessage, add a linefeed to it.
                if (message.length() > 0) {
                    message.append('\n');
                }
                // Append each task message
                message.append(Csv.join("\n", mSearchProgressMessages.values(), true,
                                        "• ", element -> element.text));

                for (TaskListener.ProgressMessage progressMessage : mSearchProgressMessages
                        .values()) {
                    progressMax += progressMessage.maxPosition;
                    progressCount += progressMessage.absPosition;
                }

            }
        }

        return new TaskListener.ProgressMessage(R.id.TASK_ID_SEARCH_COORDINATOR,
                                                progressCount, progressMax, message.toString());
    }

    public enum Searching {
        /** All is well, task started. */
        Started,
        /** The SearchCoordinator is shutting down. */
        Cancelled,
        /** The SearchEngine for this site is not available. */
        NotAvailable,
        /** The search criteria are invalid and/or the SearchEngine does not support them. */
        CannotSearch
    }

    /**
     * Allows other objects get updates on the search.
     */
    public interface SearchCoordinatorListener {

        /**
         * Called when all individual search tasks are finished.
         *
         * @param bookData     resulting data, can be empty
         * @param searchErrors a user-displayable error message.
         *                     Even when set, bookData *might* still contain data.
         */
        void onFinished(@NonNull Bundle bookData,
                        @Nullable String searchErrors);

        void onCancelled();

        /**
         * Progress messages.
         */
        void onProgress(@NonNull TaskListener.ProgressMessage message);
    }
}
