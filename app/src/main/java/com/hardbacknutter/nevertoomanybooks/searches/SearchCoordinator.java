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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
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

/**
 * Class to co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #mActiveTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link MutableLiveData}.
 * <p>
 * The {@link Site#id} is used as the task id.
 */
public class SearchCoordinator
        extends ViewModel
        implements ProgressDialogFragment.Cancellable {

    /** log tag. */
    private static final String TAG = "SearchCoordinator";

    public static final String BKEY_SEARCH_ERROR = TAG + ":error";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int TO_MILLIS = 1_000_000;
    protected final MutableLiveData<TaskListener.ProgressMessage>
            mSearchCoordinatorProgressMessage = new MutableLiveData<>();
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
    private final MutableLiveData<TaskListener.FinishMessage<Bundle>>
            mSearchCoordinatorFinishedMessage = new MutableLiveData<>();
    /** Mappers to apply. */
    @NonNull
    private final Collection<Mapper> mMappers = new ArrayList<>();
    /** Sites to search on. If this list is empty, all searches will return {@code false}. */
    private SiteList mSiteList;
    /** Base message for progress updates. */
    private String mBaseMessage;
    /** Flag */
    private boolean mIsSearchActive;
    /** Flag indicating we're shutting down. */
    private boolean mIsCancelled;
    /**
     * Indicates original ISBN/EAN is really present and valid.
     * Being ISBN or EAN depends on {@link #mStrictIsbn}.
     */
    private boolean mHasValidIsbnOrEAN;
    /** {@code true} for strict ISBN checking, {@code false} for also allowing EAN-13. */
    private boolean mStrictIsbn = true;
    /** Whether of not to fetch thumbnails. */
    private boolean mFetchThumbnail;
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
            Context localizedContext = App.getLocalizedAppContext();

            // process the outcome and queue another task as needed.
            int tasksActive = onSearchTaskFinished(localizedContext, message);

            // no more tasks ? Then send the results back to our creator.
            if (tasksActive == 0) {
                long processTime = System.nanoTime();

                mIsSearchActive = false;
                accumulateResults(localizedContext);
                String searchErrors = accumulateErrors(localizedContext);

                if (searchErrors != null && !searchErrors.isEmpty()) {
                    mBookData.putString(BKEY_SEARCH_ERROR, searchErrors);
                }
                FinishMessage<Bundle> scFinished = new FinishMessage<>(
                        R.id.TASK_ID_SEARCH_COORDINATOR,
                        (mIsCancelled ? TaskStatus.Cancelled : TaskStatus.Success),
                        mBookData, null);

                mSearchCoordinatorFinishedMessage.setValue(scFinished);

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "mSearchTaskListener.onFinished"
                               + "|wasCancelled=" + mIsCancelled
                               + "|searchErrors=" + searchErrors);

                    if (DEBUG_SWITCHES.TIMERS) {
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
                }
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            synchronized (mSearchProgressMessages) {
                mSearchProgressMessages.put(message.taskId, message);
            }
            // forward the accumulated progress
            mSearchCoordinatorProgressMessage.setValue(accumulateProgress());
        }
    };

    public MutableLiveData<TaskListener.ProgressMessage>
    getSearchCoordinatorProgressMessage() {
        return mSearchCoordinatorProgressMessage;
    }

    public MutableLiveData<TaskListener.FinishMessage<Bundle>>
    getSearchCoordinatorFinishedMessage() {
        return mSearchCoordinatorFinishedMessage;
    }

    @Override
    protected void onCleared() {
        cancel(false);
    }

    /**
     * Pseudo constructor.
     *
     * @param context Localized context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    @SuppressLint("UseSparseArrays")
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {

        if (mSiteList == null) {

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                mSearchTasksStartTime = new HashMap<>();
                mSearchTasksEndTime = new HashMap<>();
            }

            if (FormatMapper.isMappingAllowed(context)) {
                mMappers.add(new FormatMapper());
            }
            if (ColorMapper.isMappingAllowed(context)) {
                mMappers.add(new ColorMapper());
            }

            if (args != null) {
                mFetchThumbnail = App.isUsed(UniqueId.BKEY_THUMBNAIL);

                mIsbnSearchText = args.getString(DBDefinitions.KEY_ISBN, "");

                //TODO: we'd need to pass a site to make this useful
                //mNativeIdSearchText = args.getString(UniqueId.BKEY_BOOK_NATIVE_ID, "");

                mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");

                mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE, "");
                mPublisherSearchText = args.getString(DBDefinitions.KEY_PUBLISHER, "");

                // use global preference.
                mSiteList = SiteList.getList(context, SiteList.Type.Data);
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

    /**
     * Process the message and start another task if required.
     *
     * @param context Localized context
     * @param message to process
     *
     * @return number of active tasks
     */
    private int onSearchTaskFinished(@NonNull final Context context,
                                     @NonNull final TaskListener.FinishMessage<Bundle> message) {
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
        mSearchCoordinatorProgressMessage.setValue(accumulateProgress());

        if (mWaitingForIsbn) {
            if (hasIsbn(message.result)) {
                mWaitingForIsbn = false;
                // replace the search text with the (we hope) exact isbn
                mIsbnSearchText = message.result.getString(DBDefinitions.KEY_ISBN, "");

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "onSearchTaskFinished|isbn=" + mIsbnSearchText);
                }

                // Start the others...even if they have run before.
                // They will redo the search WITH the ISBN.
                startSearch(context, mSiteList.getSites());
            } else {
                // Start next one that has not run yet.
                startNextSearch(context);
            }
        }

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

        return tasksActive;
    }


    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list
     */
    @NonNull
    public SiteList getSiteList() {
        return mSiteList;
    }

    /**
     * Override the initial list.
     *
     * @param siteList to use temporarily
     */
    public void setSiteList(@NonNull final SiteList siteList) {
        mSiteList = siteList;
    }

    /**
     * Indicate we want a thumbnail.
     *
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    public void setFetchThumbnail(final boolean fetchThumbnail) {
        mFetchThumbnail = fetchThumbnail;
    }


    /**
     * Search a <strong>single</strong> site with the site specific book id.
     *
     * @param context            Current context
     * @param site               to search
     * @param nativeIdSearchText to search for
     *
     * @return {@code true} if the search was started.
     */
    public boolean searchByNativeId(@NonNull final Context context,
                                    @NonNull final Site site,
                                    @NonNull final String nativeIdSearchText) {
        // sanity check
        if (nativeIdSearchText.isEmpty()) {
            throw new IllegalStateException("nativeId was empty");
        }

        clearSearchText();
        mNativeIdSearchText = nativeIdSearchText;
        prepareSearch(context);

        mIsSearchActive = startSearch(context, site);
        return mIsSearchActive;
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

    /**
     * Used by {@link #searchByText}.
     *
     * @param authorSearchText to search for
     */
    public void setAuthorSearchText(@NonNull final String authorSearchText) {
        mAuthorSearchText = authorSearchText;
    }

    @NonNull
    public String getTitleSearchText() {
        return mTitleSearchText;
    }

    /**
     * Used by {@link #searchByText}.
     *
     * @param titleSearchText to search for
     */
    public void setTitleSearchText(@NonNull final String titleSearchText) {
        mTitleSearchText = titleSearchText;
    }

    @NonNull
    public String getPublisherSearchText() {
        return mPublisherSearchText;
    }

    /**
     * Used by {@link #searchByText}.
     *
     * @param publisherSearchText to search for
     */
    public void setPublisherSearchText(@NonNull final String publisherSearchText) {
        mPublisherSearchText = publisherSearchText;
    }

    /**
     * Used by {@link #searchByText}.
     *
     * @param isbnSearchText to search for
     * @param strictIsbn     Flag: set to {@link false} to allow invalid isbn numbers
     *                       to be passed to the searches
     */
    public void setIsbnSearchText(@NonNull final String isbnSearchText,
                                  final boolean strictIsbn) {
        mIsbnSearchText = isbnSearchText;
        mStrictIsbn = strictIsbn;
    }

    /**
     * Start a search.
     * <p>
     * If there is a valid ISBN/EAN code, we start a concurrent search on all sites.
     * When all sites are searched, we're done.
     * <p>
     * Otherwise, we start a serial search using author/title (and optional publisher)
     * until we find an ISBN or until we searched all sites.
     * Once/if an ISBN is found, the serial search is abandoned, and a new concurrent search
     * is started on all sites using the ISBN.
     *
     * @param context Current context
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean searchByText(@NonNull final Context context) {
        // clear criteria NOT used by this search.
        mNativeIdSearchText = "";
        prepareSearch(context);

        if (!mIsbnSearchText.isEmpty()) {
            mWaitingForIsbn = false;
            // searching by text requires a valid ISBN *OR* EAN.
            if (mHasValidIsbnOrEAN || !mStrictIsbn) {
                mIsSearchActive = startSearch(context, mSiteList.getSites());
            }

        } else {
            // We really want to ensure we get the same book from each, so if the isbn is
            // NOT PRESENT, search the sites one at a time until we get an isbn.
            mWaitingForIsbn = true;
            mIsSearchActive = startNextSearch(context);
        }

        return mIsSearchActive;
    }

    protected void setBaseMessage(@Nullable final String baseMessage) {
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
     * Check if the given site is enabled.
     *
     * @param siteId to check
     *
     * @return {@code true} if enabled
     */
    @SuppressWarnings("unused")
    public boolean isEnabled(final int siteId) {
        return (mSiteList.getEnabledSites() & siteId) != 0;
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     *
     * @param context Current context
     */
    private void prepareSearch(@NonNull final Context context) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchStartTime = System.nanoTime();
        }

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!NetworkUtils.isNetworkAvailable(context)) {
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
                throw new IllegalArgumentException("empty criteria");
            }
        }

        // reset flags
        mWaitingForIsbn = false;
        mIsCancelled = false;
        mIsSearchActive = false;

        mBookData = new Bundle();
        mSearchResults.clear();
        mSearchFinishedMessages.clear();

        if (mIsbnSearchText.isEmpty()) {
            mHasValidIsbnOrEAN = false;
        } else {
            mHasValidIsbnOrEAN = new ISBN(mIsbnSearchText, mStrictIsbn).isValid();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "prepareSearch"
                       + "|mNativeId=" + mNativeIdSearchText
                       + "|mIsbn=" + mIsbnSearchText
                       + "|mHasValidIsbnOrEAN=" + mHasValidIsbnOrEAN
                       + "|mStrictIsbn=" + mStrictIsbn
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
     * @param context Localized context
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNextSearch(@NonNull final Context context) {
        // if mSiteList is empty, we return false.
        for (Site site : mSiteList.getSites()) {
            if (site.isEnabled()) {
                // If the site has not been searched yet, search it
                if (!mSearchResults.containsKey(site.id)) {
                    return startSearch(context, site);
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
     * @param context Localized context
     * @param sites   to search, see {@link SearchSites#SEARCH_FLAG_MASK}
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearch(@NonNull final Context context,
                                @NonNull final Iterable<Site> sites) {
        // if currentSearchSites is empty, we return false.
        boolean atLeastOneStarted = false;
        for (Site site : sites) {
            if (site.isEnabled()) {
                // If the site has not been searched yet, search it
                if (!mSearchResults.containsKey(site.id)) {
                    if (startSearch(context, site)) {
                        atLeastOneStarted = true;
                    }
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Start specific search.
     *
     * @param context Localized context
     * @param site    to search
     *
     * @return {@code true} if the search was started.
     */
    private boolean startSearch(@NonNull final Context context,
                                @NonNull final Site site) {
        // refuse new searches if we're shutting down.
        if (mIsCancelled) {
            return false;
        }

        SearchEngine searchEngine = site.getSearchEngine();
        if (!searchEngine.isAvailable(context)) {
            return false;
        }

        boolean canSearch =
                // if we have a native id, and the engine supports it, we can search.
                (!mNativeIdSearchText.isEmpty()
                 && (searchEngine instanceof SearchEngine.ByNativeId))
                ||
                // If we have a valid ISBN, ...
                (mHasValidIsbnOrEAN && mStrictIsbn
                 && (searchEngine instanceof SearchEngine.ByIsbn))
                ||
                // If we have a generic barcode, ...
                ((!mIsbnSearchText.isEmpty() && !mStrictIsbn)
                 && (searchEngine instanceof SearchEngine.ByBarcode))
                ||
                // If we have valid text to search on, ...
                (((!mAuthorSearchText.isEmpty() && !mTitleSearchText.isEmpty())
                  || !mIsbnSearchText.isEmpty())
                 && (searchEngine instanceof SearchEngine.ByText));

        if (!canSearch) {
            return false;
        }

        SearchTask task = new SearchTask(context, site.id, searchEngine,
                                         mSearchTaskListener);

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
        return true;
    }

    /**
     * Accumulate data from all sites.
     *
     * @param context Localized context
     */
    private void accumulateResults(@NonNull final Context context) {
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final Collection<Integer> sites = new ArrayList<>();

        // determine the order of the sites which should give us the most reliable data.
        if (mHasValidIsbnOrEAN && mStrictIsbn) {
            // If an ISBN was passed, ignore entries with the wrong ISBN,
            // and put entries without ISBN at the end
            final Collection<Integer> uncertain = new ArrayList<>();
            for (Site site : SiteList.getDataSitesByReliability(context)) {
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
            // If an ISBN was not passed, then just use the default order
            for (Site site : SiteList.getDataSitesByReliability(context)) {
                sites.add(site.id);
            }
        }

        // Merge the data we have in the order as decided upon above.
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
            mapper.map(context, mBookData);
        }

        // Pick the best front cover (if any) and clean/delete all others.
        ArrayList<String> imageList = mBookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
        if (imageList != null && !imageList.isEmpty()) {
            String coverName = ImageUtils.cleanupImages(imageList);
            if (coverName != null) {
                mBookData.putString(UniqueId.BKEY_FILE_SPEC[0], coverName);
            }
        }
        mBookData.remove(UniqueId.BKEY_FILE_SPEC_ARRAY);

        //FIXME: there is only one (potential) back-cover coming from StripInfo.
        // it's stored in UniqueId.BKEY_FILE_SPEC[1]

        // If we did not get an ISBN, use the one we originally searched for.
        String isbn = mBookData.getString(DBDefinitions.KEY_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbnSearchText);
        }

        // If we did not get an title, use the one we originally searched for.
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
     * @param context Localized context
     *
     * @return the error message
     */
    private String accumulateErrors(@NonNull final Context context) {
        String errorMessage = null;
        if (!mSearchFinishedMessages.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, TaskListener.FinishMessage<Bundle>>
                    error : mSearchFinishedMessages.entrySet()) {
                String siteError = createSiteError(context, error.getKey(), error.getValue());
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
     * @param context Localized context
     * @param siteId  the site id
     * @param message to digest
     *
     * @return user-friendly error message for the given site
     */
    private String createSiteError(@NonNull final Context context,
                                   @StringRes final int siteId,
                                   @NonNull final TaskListener.FinishMessage<Bundle> message) {

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
                        // by design a FormattedMessageException can be shown to the user
                        eMsg = ((FormattedMessageException) message.exception)
                                .getLocalizedMessage(context);
                    } else if (BuildConfig.DEBUG /* always */) {
                        // but a raw exception should not
                        eMsg = message.exception.getLocalizedMessage();
                    } else {
                        // instead ask for feedback
                        eMsg = context.getString(R.string.error_if_the_problem_persists);
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
