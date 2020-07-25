/*
 * @Copyright 2020 HardBackNutter
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
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseLongArray;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;

/**
 * Co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #mActiveTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link MutableLiveData}.
 * <p>
 * The {@link Site#engineId} is used as the task id.
 */
public class SearchCoordinator
        extends ViewModel
        implements Canceller {

    /** Log tag. */
    private static final String TAG = "SearchCoordinator";

    public static final String BKEY_SEARCH_ERROR = TAG + ":error";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    protected final MutableLiveData<ProgressMessage>
            mSearchCoordinatorProgress = new MutableLiveData<>();
    protected final MutableLiveData<FinishedMessage<Bundle>>
            mSearchCoordinatorCancelled = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Bundle>>
            mSearchCoordinatorFinished = new MutableLiveData<>();

    /** List of Tasks being managed by *this* object. */
    @NonNull
    private final Collection<SearchTask> mActiveTasks = new HashSet<>();
    /** Accumulates the results from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, Bundle> mSearchResults = new HashMap<>();
    /** Mappers to apply. */
    @NonNull
    private final Collection<Mapper> mMappers = new ArrayList<>();


    /** Accumulates the last message from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, Exception>
            mSearchFinishedMessages = Collections.synchronizedMap(new HashMap<>());
    /** Accumulates the results from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, ProgressMessage> mSearchProgressMessages = new HashMap<>();


    /** Sites to search on. If this list is empty, all searches will return {@code false}. */
    private SiteList mSiteList;
    /** Base message for progress updates. */
    private String mBaseMessage;
    /** Flag indicating at least one search is currently running. */
    private boolean mIsSearchActive;
    /** Flag indicating we're shutting down. */
    private boolean mIsCancelled;
    /** Whether of not to fetch thumbnails. */
    @Nullable
    private boolean[] mFetchThumbnail;
    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean mWaitingForExactCode;
    /** Original ISBN text for search. */
    @NonNull
    private String mIsbnSearchText = "";
    /** {@code true} for strict ISBN checking, {@code false} for allowing generic codes. */
    private boolean mStrictIsbn = true;
    /** Created by {@link #prepareSearch(Context)}. NonNull afterwards. */
    private ISBN mIsbn;
    /** Site external id for search. */
    @Nullable
    private SparseArray<String> mExternalIdSearchText;
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
    /** DEBUG timer. */
    private long mSearchStartTime;
    /** DEBUG timer. */
    private SparseLongArray mSearchTasksStartTime;
    /** DEBUG timer. */
    private SparseLongArray mSearchTasksEndTime;
    /** Listener for <strong>individual</strong> search tasks. */
    private final TaskListener<Bundle> mSearchTaskListener = new TaskListener<Bundle>() {

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            synchronized (mSearchProgressMessages) {
                mSearchProgressMessages.put(message.taskId, message);
            }
            final Context context = LocaleUtils.applyLocale(App.getAppContext());
            // forward the accumulated progress
            mSearchCoordinatorProgress.setValue(accumulateProgress(context));
        }

        @Override
        public void onFinished(@NonNull final FinishedMessage<Bundle> message) {
            // sanity check
            Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
            synchronized (mSearchResults) {
                mSearchResults.put(message.taskId, message.result);
            }
            onSearchTaskFinished(message.taskId, message.result);
        }

        @Override
        public void onCancelled(@NonNull final FinishedMessage<Bundle> message) {
            synchronized (mSearchFinishedMessages) {
                mSearchFinishedMessages.put(message.taskId, null);
            }
            onSearchTaskFinished(message.taskId, message.result);
        }

        @Override
        public void onFailure(@NonNull final FinishedMessage<Exception> message) {
            synchronized (mSearchFinishedMessages) {
                mSearchFinishedMessages.put(message.taskId, message.result);
            }
            onSearchTaskFinished(message.taskId, null);
        }
    };

    /** Observable. */
    @NonNull
    public MutableLiveData<ProgressMessage> onProgress() {
        return mSearchCoordinatorProgress;
    }

    /**
     * Handles both Successful and Failed searches.
     * <p>
     * The Bundle will (optionally) contain {@link #BKEY_SEARCH_ERROR} with a list of errors.
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Bundle>> onSearchFinished() {
        return mSearchCoordinatorFinished;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<FinishedMessage<Bundle>> onSearchCancelled() {
        return mSearchCoordinatorCancelled;
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
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {

        if (mSiteList == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                mSearchTasksStartTime = new SparseLongArray();
                mSearchTasksEndTime = new SparseLongArray();
            }

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (FormatMapper.isMappingAllowed(prefs)) {
                mMappers.add(new FormatMapper());
            }
            if (ColorMapper.isMappingAllowed(prefs)) {
                mMappers.add(new ColorMapper());
            }

            if (args != null) {
                final boolean useThumbnails = DBDefinitions
                        .isUsed(prefs, DBDefinitions.PREFS_IS_USED_THUMBNAIL);
                mFetchThumbnail = new boolean[2];
                mFetchThumbnail[0] = useThumbnails;
                mFetchThumbnail[1] = useThumbnails;

                mIsbnSearchText = args.getString(DBDefinitions.KEY_ISBN, "");

                //TODO: (maybe) implement external id as argument
                //mExternalIdSearchText = args.get...

                mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE, "");

                mAuthorSearchText = args.getString(
                        BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR, "");

                mPublisherSearchText = args.getString(
                        BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER, "");

                // use global preference.
                final Locale systemLocale = LocaleUtils.getSystemLocale();
                final Locale userLocale = LocaleUtils.getUserLocale(context);
                mSiteList = SiteList.getList(context, systemLocale, userLocale,
                                             SiteList.Type.Data);
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

    @Override
    public boolean isCancelled() {
        return mIsCancelled;
    }

    /**
     * Process the message and start another task if required.
     *
     * @param taskId of task
     * @param result of a search (can be null for failed/cancelled searches)
     */
    private void onSearchTaskFinished(final int taskId,
                                      @Nullable final Bundle result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchTasksEndTime.put(taskId, System.nanoTime());
        }

        final Context appContext = LocaleUtils.applyLocale(App.getAppContext());

        // clear obsolete progress status
        synchronized (mSearchProgressMessages) {
            mSearchProgressMessages.remove(taskId);
        }
        // and update our listener.
        mSearchCoordinatorProgress.setValue(accumulateProgress(appContext));

        if (mWaitingForExactCode) {
            if (result != null && hasIsbn(result)) {
                mWaitingForExactCode = false;
                // replace the search text with the (we hope) exact isbn
                mIsbnSearchText = result.getString(DBDefinitions.KEY_ISBN, "");

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "onSearchTaskFinished|mWaitingForExactCode|isbn=" + mIsbnSearchText);
                }

                // Start the others...even if they have run before.
                // They will redo the search WITH the ISBN.
                startSearch();
            } else {
                // Start next one that has not run yet.
                startNextSearch();
            }
        }

        boolean allDone;
        synchronized (mActiveTasks) {
            // Remove the finished task from our list
            for (SearchTask searchTask : mActiveTasks) {
                if (searchTask.getTaskId() == taskId) {
                    mActiveTasks.remove(searchTask);
                    break;
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Site.Config config = Site.getConfig(taskId);
                //noinspection ConstantConditions
                Log.d(TAG, "mSearchTaskListener.onFinished|finished="
                           + appContext.getString(config.getNameResId()));

                for (SearchTask searchTask : mActiveTasks) {
                    config = Site.getConfig(searchTask.getTaskId());
                    //noinspection ConstantConditions
                    Log.d(TAG, "mSearchTaskListener.onFinished|running="
                               + appContext.getString(config.getNameResId()));
                }
            }

            allDone = mActiveTasks.isEmpty();
        }

        if (allDone) {
            // no more tasks ? Then send the results back to our creator.

            final long processTime = System.nanoTime();

            mIsSearchActive = false;
            accumulateResults(appContext);
            final String searchErrors = accumulateErrors(appContext);

            if (searchErrors != null && !searchErrors.isEmpty()) {
                mBookData.putString(BKEY_SEARCH_ERROR, searchErrors);
            }

            final FinishedMessage<Bundle> message =
                    new FinishedMessage<>(R.id.TASK_ID_SEARCH_COORDINATOR, mBookData);
            if (mIsCancelled) {
                mSearchCoordinatorCancelled.setValue(message);
            } else {
                mSearchCoordinatorFinished.setValue(message);
            }

            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "mSearchTaskListener.onFinished"
                           + "|wasCancelled=" + mIsCancelled
                           + "|searchErrors=" + searchErrors);

                if (DEBUG_SWITCHES.TIMERS) {
                    for (int i = 0; i < mSearchTasksStartTime.size(); i++) {
                        final long start = mSearchTasksStartTime.valueAt(i);
                        // use the key, not the index!
                        final int key = mSearchTasksStartTime.keyAt(i);
                        final long end = mSearchTasksEndTime.get(key);

                        //noinspection ConstantConditions
                        final String engineName = appContext.getString(
                                Site.getConfig(key).getNameResId());

                        if (end != 0) {
                            Log.d(TAG, String.format(Locale.ENGLISH,
                                                     "mSearchTaskListener.onFinished"
                                                     + "|engine=%20s:%10d ms",
                                                     engineName,
                                                     (end - start) / NANO_TO_MILLIS));
                        } else {
                            Log.d(TAG, String.format(Locale.ENGLISH,
                                                     "mSearchTaskListener.onFinished"
                                                     + "|engine=%20s|never finished",
                                                     engineName));
                        }
                    }

                    Log.d(TAG, String.format(Locale.ENGLISH,
                                             "mSearchTaskListener.onFinished"
                                             + "|total search time: %10d ms",
                                             (processTime - mSearchStartTime)
                                             / NANO_TO_MILLIS));
                    Log.d(TAG, String.format(Locale.ENGLISH,
                                             "mSearchTaskListener.onFinished"
                                             + "|processing time: %10d ms",
                                             (System.nanoTime() - processTime)
                                             / NANO_TO_MILLIS));
                }
            }
        }
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
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     */
    protected void setFetchThumbnail(@Nullable final boolean[] fetchThumbnail) {
        mFetchThumbnail = fetchThumbnail;
    }

    /**
     * Clear all search criteria.
     */
    public void clearSearchText() {
        mExternalIdSearchText = null;
        mIsbnSearchText = "";
        mAuthorSearchText = "";
        mTitleSearchText = "";
        mPublisherSearchText = "";
    }

    /**
     * Search criteria.
     *
     * @param externalIds one or more ID's
     *                    The key is the engine id,
     *                    The value us the value of the external domain for that engine
     */
    @SuppressWarnings("WeakerAccess")
    public void setExternalIds(@Nullable final SparseArray<String> externalIds) {
        mExternalIdSearchText = externalIds;
    }

    @NonNull
    public String getIsbnSearchText() {
        return mIsbnSearchText;
    }

    public void setIsbnSearchText(@NonNull final String isbnSearchText) {
        mIsbnSearchText = isbnSearchText;
    }

    public boolean isStrictIsbn() {
        return mStrictIsbn;
    }

    public void setStrictIsbn(final boolean strictIsbn) {
        mStrictIsbn = strictIsbn;
    }

    /**
     * Search criteria.
     *
     * @param isbnSearchText to search for
     * @param strictIsbn     Flag: set to {@link false} to allow invalid isbn numbers
     *                       to be passed to the searches
     */
    @SuppressWarnings("WeakerAccess")
    public void setIsbnSearchText(@NonNull final String isbnSearchText,
                                  final boolean strictIsbn) {
        mIsbnSearchText = isbnSearchText;
        mStrictIsbn = strictIsbn;
    }

    @NonNull
    public String getAuthorSearchText() {
        return mAuthorSearchText;
    }

    /**
     * Search criteria.
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
     * Search criteria.
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
     * Search criteria.
     *
     * @param publisherSearchText to search for
     */
    public void setPublisherSearchText(@NonNull final String publisherSearchText) {
        mPublisherSearchText = publisherSearchText;
    }

    /**
     * Search the given engine with the site specific book id.
     *
     * @param context              Current context
     * @param searchEngine         to use
     * @param externalIdSearchText to search for
     *
     * @return {@code true} if the search was started.
     */
    public boolean searchByExternalId(@NonNull final Context context,
                                      @NonNull final SearchEngine searchEngine,
                                      @NonNull final String externalIdSearchText) {
        // sanity check
        if (externalIdSearchText.isEmpty()) {
            throw new IllegalStateException("externalIdSearchText was empty");
        }
        // remove all other criteria (this is CRUCIAL)
        clearSearchText();

        mExternalIdSearchText = new SparseArray<>();
        mExternalIdSearchText.put(searchEngine.getId(), externalIdSearchText);
        prepareSearch(context);

        mIsSearchActive = startSearch(searchEngine);
        return mIsSearchActive;
    }

    /**
     * Start a search.
     * <p>
     * If there is a valid ISBN/code, we start a concurrent search on all sites.
     * When all sites are searched, we're done.
     * <p>
     * Otherwise, we start a serial search using author/title (and optional publisher)
     * until we find an ISBN/code or until we searched all sites.
     * Once/if an ISBN/code is found, the serial search is abandoned, and a new concurrent search
     * is started on all sites using the ISBN/code.
     *
     * @param context Current context
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean search(@NonNull final Context context) {
        prepareSearch(context);

        // If we have one or more ID's
        if ((mExternalIdSearchText != null && mExternalIdSearchText.size() > 0)
            // or we have a valid code
            || mIsbn.isValid(mStrictIsbn)) {

            // then start a concurrent search
            mWaitingForExactCode = false;
            mIsSearchActive = startSearch();

        } else {
            // We really want to ensure we get the same book from each,
            // so if the ISBN/code is NOT PRESENT, search the sites
            // one at a time until we get a ISBN/code.
            mWaitingForExactCode = true;
            mIsSearchActive = startNextSearch();
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
        final String s = bundle.getString(DBDefinitions.KEY_ISBN);
        return s != null && !s.trim().isEmpty();
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
                && (mExternalIdSearchText == null || mExternalIdSearchText.size() == 0)) {
                throw new IllegalArgumentException("empty criteria");
            }
        }

        // reset flags
        mWaitingForExactCode = false;
        mIsCancelled = false;
        mIsSearchActive = false;

        mBookData = new Bundle();
        // no synchronized needed here
        mSearchResults.clear();
        // no synchronized needed here
        mSearchFinishedMessages.clear();

        mIsbn = new ISBN(mIsbnSearchText, mStrictIsbn);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "prepareSearch"
                       + "|mExternalIdSearchText=" + mExternalIdSearchText
                       + "|mIsbnSearchText=" + mIsbnSearchText
                       + "|mIsbn=" + mIsbn
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
     * Start all searches which have not been run yet.
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearch() {
        // if currentSearchSites is empty, we return false.
        boolean atLeastOneStarted = false;
        for (Site site : mSiteList.getEnabledSites()) {
            // If the site has not been searched yet, search it
            if (!mSearchResults.containsKey(site.engineId)) {
                if (startSearch(site.getSearchEngine())) {
                    atLeastOneStarted = true;
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Start a single task.
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNextSearch() {
        for (Site site : mSiteList.getEnabledSites()) {
            // If the site has not been searched yet, search it
            if (!mSearchResults.containsKey(site.engineId)) {
                return startSearch(site.getSearchEngine());
            }
        }
        return false;
    }

    /**
     * Start specified site search.
     *
     * @param searchEngine to use
     *
     * @return {@code true} if the search was started.
     */
    private boolean startSearch(@NonNull final SearchEngine searchEngine) {

        // refuse new searches if we're shutting down.
        if (mIsCancelled) {
            return false;
        }

        if (!searchEngine.isAvailable()) {
            return false;
        }

        // check for a external id matching the site.
        String externalId = null;
        if (mExternalIdSearchText != null
            && mExternalIdSearchText.size() > 0
            && mExternalIdSearchText.get(searchEngine.getId()) != null) {
            externalId = mExternalIdSearchText.get(searchEngine.getId());
        }

        final SearchTask task = new SearchTask(searchEngine, mSearchTaskListener);
        task.setFetchThumbnail(mFetchThumbnail);

        if (externalId != null && !externalId.isEmpty()
            && (searchEngine instanceof SearchEngine.ByExternalId)) {
            task.setSearchBy(SearchTask.BY_EXTERNAL_ID);
            task.setExternalId(externalId);

        } else if (mIsbn.isValid(true)
                   && (searchEngine instanceof SearchEngine.ByIsbn)) {
            task.setSearchBy(SearchTask.BY_ISBN);
            if (((SearchEngine.ByIsbn) searchEngine).isPreferIsbn10()
                && mIsbn.isIsbn10Compat()) {
                task.setIsbn(mIsbn.asText(ISBN.TYPE_ISBN10));
            } else {
                task.setIsbn(mIsbn.asText());
            }

        } else if (mIsbn.isValid(false)
                   && (searchEngine instanceof SearchEngine.ByBarcode)) {
            task.setSearchBy(SearchTask.BY_BARCODE);
            task.setIsbn(mIsbn.asText());

        } else if (searchEngine instanceof SearchEngine.ByText) {
            task.setSearchBy(SearchTask.BY_TEXT);
            task.setIsbn(mIsbn.asText());
            task.setAuthor(mAuthorSearchText);
            task.setTitle(mTitleSearchText);
            task.setPublisher(mPublisherSearchText);

        } else {
            // search data and engine have nothing in common, abort silently.
            return false;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchTasksStartTime.put(task.getTaskId(), System.nanoTime());
        }

        synchronized (mActiveTasks) {
            mActiveTasks.add(task);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "startSearch|searchEngine=" + searchEngine.getName());
        }
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
        return true;
    }

    /**
     * Accumulate data from all sites.
     *
     * <strong>Developer note:</strong> before you think you can simplify this method
     * by working directly with engine-id and SearchEngines... DON'T
     * Read class docs for {@link SearchSites} and {@link SiteList#getDataSitesByReliability}.
     *
     * @param context Localized context
     */
    private void accumulateResults(@NonNull final Context context) {
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final Collection<Site> sites = new ArrayList<>();

        // determine the order of the sites which should give us the most reliable data.
        if (mIsbn.isValid(true)) {
            // If an ISBN was passed, ignore entries with the wrong ISBN,
            // and put entries without ISBN at the end
            final Collection<Site> sitesWithoutIsbn = new ArrayList<>();
            final List<Site> allSites = SiteList.getDataSitesByReliability(context);
            for (Site site : allSites) {
                if (mSearchResults.containsKey(site.engineId)) {
                    final Bundle bookData = mSearchResults.get(site.engineId);
                    if (bookData != null && bookData.containsKey(DBDefinitions.KEY_ISBN)) {
                        final String isbnFound = bookData.getString(DBDefinitions.KEY_ISBN);
                        // do they match?
                        if (isbnFound != null && !isbnFound.isEmpty()
                            && mIsbn.equals(ISBN.createISBN(isbnFound))) {
                            sites.add(site);
                        }
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "accumulateResults"
                                       + "|mIsbn" + mIsbn
                                       + "|isbnFound" + isbnFound);
                        }
                    } else {
                        sitesWithoutIsbn.add(site);
                    }
                }
            }
            // now add the less reliable ones at the end of the list.
            sites.addAll(sitesWithoutIsbn);
            // Add the passed ISBN first;
            // avoids overwriting with potentially different isbn from the sites
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbnSearchText);

        } else {
            // If an ISBN was not passed, then just use the default order
            sites.addAll(SiteList.getDataSitesByReliability(context));
        }

        // Merge the data we have in the order as decided upon above.
        for (Site site : sites) {
            final SearchEngine searchEngine = site.getSearchEngine();

            final Bundle siteData = mSearchResults.get(searchEngine.getId());
            if (siteData != null && !siteData.isEmpty()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "accumulateSiteData|searchEngine=" + searchEngine.getName());
                }
                final Locale locale = searchEngine.getLocale();
                accumulateSiteData(context, siteData, locale);
            }
        }

        // run the mappers
        for (Mapper mapper : mMappers) {
            mapper.map(context, mBookData);
        }

        // Pick the best covers for each list (if any) and clean/delete all others.
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            final ArrayList<String> imageList = mBookData
                    .getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[cIdx]);
            if (imageList != null && !imageList.isEmpty()) {
                final String coverName = getBestImage(imageList);
                if (coverName != null) {
                    mBookData.putString(Book.BKEY_FILE_SPEC[cIdx], coverName);
                }
            }
            mBookData.remove(Book.BKEY_FILE_SPEC_ARRAY[cIdx]);
        }

        // If we did not get an ISBN, use the one we originally searched for.
        final String isbn = mBookData.getString(DBDefinitions.KEY_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbnSearchText);
        }

        // If we did not get an title, use the one we originally searched for.
        final String title = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_TITLE, mTitleSearchText);
        }
    }

    /**
     * Pick the largest image from the given list, and delete all others.
     *
     * @param imageList a list of images
     *
     * @return name of cover found, or {@code null} for none.
     */
    @AnyThread
    @Nullable
    public String getBestImage(@NonNull final ArrayList<String> imageList) {

        // biggest size based on height * width
        long bestImageSize = -1;
        // index of the file which is the biggest
        int bestFileIndex = -1;

        // Just read the image files to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            final String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    final long size = opt.outHeight * opt.outWidth;
                    if (size > bestImageSize) {
                        bestImageSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one.
        // Note there *may* be no best one, so all would be deleted. This is fine.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                FileUtils.delete(new File(imageList.get(i)));
            }
        }

        if (bestFileIndex >= 0) {
            return imageList.get(bestFileIndex);
        }

        return null;
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
     * @param context Current context
     */
    private void accumulateSiteData(@NonNull final Context context,
                                    @NonNull final Bundle siteData,
                                    @NonNull final Locale locale) {

        for (String key : siteData.keySet()) {
            if (DBDefinitions.KEY_DATE_PUBLISHED.equals(key)
                || DBDefinitions.KEY_DATE_FIRST_PUBLICATION.equals(key)) {
                accumulateDates(context, locale, key, siteData);

            } else if (Book.BKEY_AUTHOR_ARRAY.equals(key)
                       || Book.BKEY_SERIES_ARRAY.equals(key)
                       || Book.BKEY_PUBLISHER_ARRAY.equals(key)
                       || Book.BKEY_TOC_ARRAY.equals(key)
                       || Book.BKEY_FILE_SPEC_ARRAY[0].equals(key)
                       || Book.BKEY_FILE_SPEC_ARRAY[1].equals(key)) {
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
        final Object dataToAdd = siteData.get(key);
        if (dataToAdd == null || dataToAdd.toString().trim().isEmpty()) {
            return;
        }

        final String dest = mBookData.getString(key);
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
     *
     * @param context    Current context
     * @param siteLocale the specific Locale of the website
     * @param key        for the date field
     * @param siteData   to digest
     */
    private void accumulateDates(@NonNull final Context context,
                                 @NonNull final Locale siteLocale,
                                 @NonNull final String key,
                                 @NonNull final Bundle siteData) {
        final String currentDateHeld = mBookData.getString(key);
        final String dataToAdd = siteData.getString(key);

        final DateParser dateParser = DateParser.getInstance(context);

        if (currentDateHeld == null || currentDateHeld.isEmpty()) {
            // copy, even if the incoming date might not be valid.
            // We'll deal with that later.
            mBookData.putString(key, dataToAdd);

        } else {
            // Overwrite with the new date if we can parse it and
            // if the current one was present but not valid.
            if (dataToAdd != null) {
                final LocalDateTime newDate = dateParser.parse(siteLocale, dataToAdd);
                if (newDate != null) {
                    if (dateParser.parse(siteLocale, currentDateHeld) == null) {
                        // current date was invalid, use the new one instead.
                        // (theoretically this check was not needed, as we should not have
                        // an invalid date stored anyhow... but paranoia rules)
                        mBookData.putString(key, newDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
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
        final ArrayList<T> dataToAdd = siteData.getParcelableArrayList(key);
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
        // no synchronized needed, at this point all other threads have finished.
        if (!mSearchFinishedMessages.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, Exception> entry : mSearchFinishedMessages.entrySet()) {
                final Site.Config config = Site.getConfig(entry.getKey());
                //noinspection ConstantConditions
                final String engineName = context.getString(config.getNameResId());
                final Exception exception = entry.getValue();

                final String error;
                if (exception == null) {
                    error = context.getString(R.string.cancelled);
                } else {
                    error = createSiteError(context, engineName, exception);
                }

                sb.append(context.getString(R.string.error_search_x_failed_y, engineName, error))
                  .append('\n');
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
     * @param context   Localized context
     * @param exception to process
     *
     * @return user-friendly error message for the given site
     */
    private String createSiteError(@NonNull final Context context,
                                   @NonNull final String siteName,
                                   @NonNull final Exception exception) {
        final String text;
        if (exception instanceof CredentialsException) {
            text = context.getString(R.string.error_site_authentication_failed, siteName);
        } else if (exception instanceof SocketTimeoutException) {
            text = context.getString(R.string.error_network_timeout);
        } else if (exception instanceof MalformedURLException) {
            text = context.getString(R.string.error_search_failed_network);
        } else if (exception instanceof UnknownHostException) {
            text = context.getString(R.string.error_search_failed_network);
        } else if (exception instanceof IOException) {
            //ENHANCE: if (exception.getCause() instanceof ErrnoException) {
            //           int errno = ((ErrnoException) exception.getCause()).errno;
            text = context.getString(R.string.error_search_failed_network);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                // in debug mode we add the raw exception
                text = context.getString(R.string.error_unknown)
                       + "\n\n" + exception.getLocalizedMessage();
            } else {
                // when not in debug, ask for feedback
                text = context.getString(R.string.error_unknown)
                       + "\n\n" + context.getString(R.string.error_if_the_problem_persists,
                                                    context.getString(R.string.lbl_send_debug));
            }
        }

        return text;
    }

    /**
     * Creates {@link ProgressMessage} with the global/total progress of all tasks.
     *
     * @param context Current context
     *
     * @return instance
     */
    @NonNull
    private ProgressMessage accumulateProgress(@NonNull final Context context) {

        // Sum the current & max values for each active task.
        int progressMax = 0;
        int progressCount = 0;

        // Start with the base message if we have one.
        final StringBuilder sb;
        if (mBaseMessage != null && !mBaseMessage.isEmpty()) {
            sb = new StringBuilder(mBaseMessage);
        } else {
            sb = new StringBuilder();
        }

        synchronized (mSearchProgressMessages) {
            if (!mSearchProgressMessages.isEmpty()) {
                // if there was a baseMessage, add a linefeed to it.
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                // Append each task message
                sb.append(Csv.textList(context, mSearchProgressMessages.values(),
                                       element -> element.text));

                for (ProgressMessage progressMessage : mSearchProgressMessages.values()) {
                    progressMax += progressMessage.maxPosition;
                    progressCount += progressMessage.position;
                }

            }
        }

        return new ProgressMessage(R.id.TASK_ID_SEARCH_COORDINATOR, sb.toString(),
                                   progressMax, progressCount, null
        );
    }
}
