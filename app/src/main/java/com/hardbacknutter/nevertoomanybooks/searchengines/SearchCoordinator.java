/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseLongArray;

import androidx.annotation.AnyThread;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.ColorMapper;
import com.hardbacknutter.nevertoomanybooks.sync.FormatMapper;
import com.hardbacknutter.nevertoomanybooks.sync.Mapper;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

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

    /**
     * List of front/back cover file specs as collected during the search.
     * <p>
     * <br>type: {@code ArrayList<String>}
     */
    public static final String[] BKEY_FILE_SPEC_ARRAY = {
            TAG + ":fileSpec_array:0",
            TAG + ":fileSpec_array:1"
    };

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
    private final Collection<SearchTask> mActiveTasks = new HashSet<>();
    /** Accumulates the results from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Bundle> mSearchResults = new HashMap<>();
    /** Mappers to apply. */
    private final Collection<Mapper> mMappers = new ArrayList<>();
    private final CoverFilter mCoverFilter = new CoverFilter();

    /** Accumulates the last message from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Exception>
            mSearchFinishedMessages = Collections.synchronizedMap(new HashMap<>());
    /** Accumulates the results from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, ProgressMessage> mSearchProgressMessages = new HashMap<>();


    /**
     * Sites to search on. If this list is empty, all searches will return {@code false}.
     * This list includes both enabled and disabled sites.
     */
    private ArrayList<Site> mAllSites;
    /** Base message for progress updates. */
    @Nullable
    private String mBaseMessage;
    /** Flag indicating at least one search is currently running. */
    private boolean mIsSearchActive;
    /** Flag indicating we're shutting down. */
    private boolean mIsCancelled;
    /** Whether of not to fetch thumbnails. */
    @Nullable
    private boolean[] mFetchCover;
    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean mWaitingForExactCode;
    /** Original ISBN text for search. */
    @NonNull
    private String mIsbnSearchText = "";
    /** {@code true} for strict ISBN checking, {@code false} for allowing generic codes. */
    private boolean mStrictIsbn = true;
    /** Created by {@link #prepareSearch()}. NonNull afterwards. */
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
    private SearchEngineRegistry mSearchEngineRegistry;
    /** Cached string resource. */
    private String mListElementPrefixString;
    /** Listener for <strong>individual</strong> search tasks. */
    private final TaskListener<Bundle> mSearchTaskListener = new TaskListener<Bundle>() {

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            synchronized (mSearchProgressMessages) {
                mSearchProgressMessages.put(message.taskId, message);
            }
            // forward the accumulated progress
            mSearchCoordinatorProgress.setValue(accumulateProgress());
        }

        @Override
        public void onFinished(@NonNull final FinishedMessage<Bundle> message) {
            //TaskListener, don't check if (message.isNewEvent())

            // sanity check
            final Bundle result = message.requireResult();
            synchronized (mSearchResults) {
                mSearchResults.put(message.getTaskId(), result);
            }
            onSearchTaskFinished(message.getTaskId(), result);
        }

        @Override
        public void onCancelled(@NonNull final FinishedMessage<Bundle> message) {
            //TaskListener, don't check if (message.isNewEvent())

            synchronized (mSearchFinishedMessages) {
                mSearchFinishedMessages.put(message.getTaskId(), null);
            }
            onSearchTaskFinished(message.getTaskId(), message.getResult());
        }

        @Override
        public void onFailure(@NonNull final FinishedMessage<Exception> message) {
            //TaskListener, don't check if (message.isNewEvent())

            synchronized (mSearchFinishedMessages) {
                mSearchFinishedMessages.put(message.getTaskId(), message.getResult());
            }
            onSearchTaskFinished(message.getTaskId(), null);
        }
    };
    private DateParser mDateParser;

    /** Observable. */
    @NonNull
    public LiveData<ProgressMessage> onProgress() {
        return mSearchCoordinatorProgress;
    }

    /**
     * Handles both Successful and Failed searches.
     * <p>
     * The Bundle will (optionally) contain {@link #BKEY_SEARCH_ERROR} with a list of errors.
     */
    @NonNull
    public LiveData<FinishedMessage<Bundle>> onSearchFinished() {
        return mSearchCoordinatorFinished;
    }

    /** Observable. */
    @NonNull
    public LiveData<FinishedMessage<Bundle>> onSearchCancelled() {
        return mSearchCoordinatorCancelled;
    }

    @Override
    protected void onCleared() {
        cancel();
    }

    public boolean isSearchActive() {
        return mIsSearchActive;
    }

    /**
     * Cancel all searches.
     *
     * @return {@code true}
     */
    public boolean cancel() {
        mIsCancelled = true;
        synchronized (mActiveTasks) {
            for (final SearchTask searchTask : mActiveTasks) {
                searchTask.cancel();
            }
        }
        return true;
    }

    @Override
    public boolean isCancelled() {
        return mIsCancelled;
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {

        if (mSearchEngineRegistry == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                mSearchTasksStartTime = new SparseLongArray();
                mSearchTasksEndTime = new SparseLongArray();
            }

            mSearchEngineRegistry = SearchEngineRegistry.getInstance();
            mAllSites = Site.Type.Data.getSites();

            mDateParser = new FullDateParser(context);

            mListElementPrefixString = context.getString(R.string.list_element);

            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
            if (FormatMapper.isMappingAllowed(global)) {
                mMappers.add(new FormatMapper());
            }
            if (ColorMapper.isMappingAllowed(global)) {
                mMappers.add(new ColorMapper());
            }

            if (args != null) {
                mFetchCover = new boolean[]{
                        DBKey.isUsed(global, DBKey.COVER_IS_USED[0]),
                        DBKey.isUsed(global, DBKey.COVER_IS_USED[1])
                };

                mIsbnSearchText = args.getString(DBKey.KEY_ISBN, "");

                mTitleSearchText = args.getString(DBKey.KEY_TITLE, "");

                mAuthorSearchText = args.getString(
                        SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR, "");

                mPublisherSearchText = args.getString(
                        SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER, "");
            }
        }
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list with the enabled sites
     */
    @NonNull
    public ArrayList<Site> getSiteList() {
        return mAllSites;
    }

    /**
     * Override the initial list.
     *
     * @param sites to use
     */
    public void setSiteList(@NonNull final ArrayList<Site> sites) {
        mAllSites = sites;
    }

    /**
     * Indicate we want a thumbnail.
     *
     * @param fetchCovers Set to {@code true} if we want to get covers
     */
    protected void setFetchCover(@Nullable final boolean[] fetchCovers) {
        mFetchCover = fetchCovers;
    }

    /**
     * Clear all search criteria.
     */
    public void clearSearchCriteria() {
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
     * @param searchEngine         to use
     * @param externalIdSearchText to search for
     *
     * @return {@code true} if the search was started.
     */
    public boolean searchByExternalId(@NonNull final SearchEngine searchEngine,
                                      @NonNull final String externalIdSearchText) {
        SanityCheck.requireValue(externalIdSearchText, "externalIdSearchText");

        // remove all other criteria (this is CRUCIAL)
        clearSearchCriteria();

        mExternalIdSearchText = new SparseArray<>();
        mExternalIdSearchText.put(searchEngine.getId(), externalIdSearchText);
        prepareSearch();

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
     * @return {@code true} if at least one search was started.
     */
    public boolean search() {
        prepareSearch();

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
        final String isbnStr = bundle.getString(DBKey.KEY_ISBN);
        return isbnStr != null && !isbnStr.trim().isEmpty();
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     */
    private void prepareSearch() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            mSearchStartTime = System.nanoTime();
        }

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!NetworkUtils.isNetworkAvailable()) {
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
                throw new SanityCheck.MissingValueException("empty criteria");
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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
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
        for (final Site site : Site.filterForEnabled(mAllSites)) {
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
        for (final Site site : Site.filterForEnabled(mAllSites)) {
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
        task.setExecutor(ASyncExecutor.MAIN);

        task.setFetchCovers(mFetchCover);

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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            mSearchTasksStartTime.put(task.getTaskId(), System.nanoTime());
        }

        synchronized (mActiveTasks) {
            mActiveTasks.add(task);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "startSearch|searchEngine="
                       + searchEngine.getName(ServiceLocator.getAppContext()));
        }

        task.startSearch();
        return true;
    }

    /**
     * Accumulate data from all sites.
     *
     * <strong>Developer note:</strong> before you think you can simplify this method
     * by working directly with engine-id and SearchEngines... DON'T
     * Read class docs for {@link SearchSites} and {@link Site.Type#getDataSitesByReliability}.
     *
     * @param context Current context
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
            final List<Site> allSites = Site.Type.getDataSitesByReliability();
            for (final Site site : allSites) {
                if (mSearchResults.containsKey(site.engineId)) {
                    final Bundle bookData = mSearchResults.get(site.engineId);
                    if (bookData != null && bookData.containsKey(DBKey.KEY_ISBN)) {
                        final String isbnFound = bookData.getString(DBKey.KEY_ISBN);
                        // do they match?
                        if (isbnFound != null && !isbnFound.isEmpty()
                            && mIsbn.equals(ISBN.createISBN(isbnFound))) {
                            sites.add(site);
                        }
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "accumulateResults"
                                       + "|mIsbn=" + mIsbn
                                       + "|isbnFound=" + isbnFound);
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
            mBookData.putString(DBKey.KEY_ISBN, mIsbnSearchText);

        } else {
            // If an ISBN was not passed, then just use the default order
            sites.addAll(Site.Type.getDataSitesByReliability());
        }

        // Merge the data we have in the order as decided upon above.
        for (final Site site : sites) {
            final SearchEngine searchEngine = site.getSearchEngine();

            final Bundle siteData = mSearchResults.get(searchEngine.getId());
            if (siteData != null && !siteData.isEmpty()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "accumulateSiteData|searchEngine="
                               + searchEngine.getName(context));
                }
                accumulateSiteData(searchEngine.getLocale(context), siteData);
            }
        }

        // run the mappers
        for (final Mapper mapper : mMappers) {
            mapper.map(context, mBookData);
        }

        // Pick the best covers for each list (if any) and clean/delete all others.
        mCoverFilter.filter(mBookData);

        // If we did not get an ISBN, use the one we originally searched for.
        final String isbnStr = mBookData.getString(DBKey.KEY_ISBN);
        if (isbnStr == null || isbnStr.isEmpty()) {
            mBookData.putString(DBKey.KEY_ISBN, mIsbnSearchText);
        }

        // If we did not get an title, use the one we originally searched for.
        final String title = mBookData.getString(DBKey.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            mBookData.putString(DBKey.KEY_TITLE, mTitleSearchText);
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
     */
    private void accumulateSiteData(@NonNull final Locale siteLocale,
                                    @NonNull final Bundle siteData) {

        for (final String key : siteData.keySet()) {
            if (DBKey.DATE_BOOK_PUBLICATION.equals(key)
                || DBKey.DATE_FIRST_PUBLICATION.equals(key)) {
                accumulateDates(siteLocale, key, siteData);

            } else if (Book.BKEY_AUTHOR_LIST.equals(key)
                       || Book.BKEY_SERIES_LIST.equals(key)
                       || Book.BKEY_PUBLISHER_LIST.equals(key)
                       || Book.BKEY_TOC_LIST.equals(key)
                       || BKEY_FILE_SPEC_ARRAY[0].equals(key)
                       || BKEY_FILE_SPEC_ARRAY[1].equals(key)) {
                accumulateList(key, siteData);

            } else {
                //FIXME: doing this will for example put a LONG id in the bundle
                // as a String. This is as-designed, but you do get an Exception in the log
                // when the data gets to the EditBook formatters. Harmless, but not clean.

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
     * @param siteLocale the specific Locale of the website
     * @param key        for the date field
     * @param siteData   to digest
     */
    private void accumulateDates(@NonNull final Locale siteLocale,
                                 @NonNull final String key,
                                 @NonNull final Bundle siteData) {
        final String currentDateHeld = mBookData.getString(key);
        final String dataToAdd = siteData.getString(key);

        if (currentDateHeld == null || currentDateHeld.isEmpty()) {
            // copy, even if the incoming date might not be valid.
            // We'll deal with that later.
            mBookData.putString(key, dataToAdd);

        } else {
            // FIXME: there is overlap with some SearchEngines which already do a full
            //  validity check on the dates they gather. We should avoid a double-check.
            //
            // Overwrite with the new date if we can parse it and
            // if the current one was present but not valid.
            if (dataToAdd != null) {
                final LocalDateTime newDate = mDateParser.parse(dataToAdd, siteLocale);
                if (newDate != null) {
                    if (mDateParser.parse(currentDateHeld, siteLocale) == null) {
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
     * Process the message and start another task if required.
     *
     * @param taskId of task
     * @param result of a search (can be null for failed/cancelled searches)
     */
    @SuppressLint("WrongConstant")
    private void onSearchTaskFinished(final int taskId,
                                      @Nullable final Bundle result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            mSearchTasksEndTime.put(taskId, System.nanoTime());
        }

        final Context context = ServiceLocator.getLocalizedAppContext();

        // clear obsolete progress status
        synchronized (mSearchProgressMessages) {
            mSearchProgressMessages.remove(taskId);
        }
        // and update our listener.
        mSearchCoordinatorProgress.setValue(accumulateProgress());

        if (mWaitingForExactCode) {
            if (result != null && hasIsbn(result)) {
                mWaitingForExactCode = false;
                // replace the search text with the (we hope) exact isbn
                mIsbnSearchText = result.getString(DBKey.KEY_ISBN, "");

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

        final boolean allDone;
        synchronized (mActiveTasks) {
            // Remove the finished task from our list
            for (final SearchTask searchTask : mActiveTasks) {
                if (searchTask.getTaskId() == taskId) {
                    mActiveTasks.remove(searchTask);
                    break;
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                SearchEngineConfig config = mSearchEngineRegistry.getByEngineId(taskId);
                Log.d(TAG, "mSearchTaskListener.onFinished"
                           + "|finished=" + context.getString(config.getLabelId()));

                for (final SearchTask searchTask : mActiveTasks) {
                    config = mSearchEngineRegistry.getByEngineId(searchTask.getTaskId());
                    Log.d(TAG, "mSearchTaskListener.onFinished"
                               + "|running=" + context.getString(config.getLabelId()));
                }
            }

            allDone = mActiveTasks.isEmpty();
        }

        if (allDone) {
            // no more tasks ? Then send the results back to our creator.

            final long processTime = System.nanoTime();

            mIsSearchActive = false;
            accumulateResults(context);
            final String searchErrors = accumulateErrors(context);

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

                if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                    for (int i = 0; i < mSearchTasksStartTime.size(); i++) {
                        final long start = mSearchTasksStartTime.valueAt(i);
                        // use the key, not the index!
                        final int key = mSearchTasksStartTime.keyAt(i);
                        final long end = mSearchTasksEndTime.get(key);

                        final String engineName = context.getString(
                                mSearchEngineRegistry.getByEngineId(key).getLabelId());

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
     * Called when all is said and done. Collects all individual website errors (if any)
     * into a single user-formatted message.
     *
     * @param context Current context
     *
     * @return the error message
     */
    @Nullable
    private String accumulateErrors(@NonNull final Context context) {
        String errorMessage = null;
        // no synchronized needed, at this point all other threads have finished.
        if (!mSearchFinishedMessages.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final Map.Entry<Integer, Exception> entry : mSearchFinishedMessages.entrySet()) {
                final SearchEngineConfig config = mSearchEngineRegistry
                        .getByEngineId(entry.getKey());
                final String engineName = context.getString(config.getLabelId());
                final Exception exception = entry.getValue();

                final String error;
                if (exception == null) {
                    error = context.getString(R.string.error_search_x_failed_y, engineName,
                                              context.getString(R.string.cancelled));
                } else {
                    error = createSiteError(context, engineName, exception);
                }

                sb.append(error)
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
     * <p>
     * Dev Note: the return value should preferable fit on a single line
     *
     * @param context    Current context
     * @param engineName the site where the error happened
     * @param e          Exception to process
     *
     * @return user-friendly error message for the given site
     */
    @NonNull
    private String createSiteError(@NonNull final Context context,
                                   @NonNull final String engineName,
                                   @NonNull final Exception e) {
        final String msg = ExMsg.map(context, e).orElseGet(() -> {
            // generic network related IOException message
            if (e instanceof IOException) {
                return context.getString(R.string.error_search_failed_network);
            }
            // generic unknown message
            return context.getString(R.string.error_unknown);
        });

        return context.getString(R.string.error_search_x_failed_y, engineName, msg);
    }

    /**
     * Creates {@link ProgressMessage} with the global/total progress of all tasks.
     *
     * @return instance
     */
    @NonNull
    private ProgressMessage accumulateProgress() {

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
                sb.append(mSearchProgressMessages
                                  .values().stream()
                                  .map(msg -> String.format(mListElementPrefixString, msg.text))
                                  .collect(Collectors.joining("\n")));

                for (final ProgressMessage progressMessage : mSearchProgressMessages.values()) {
                    progressMax += progressMessage.maxPosition;
                    progressCount += progressMessage.position;
                }

            }
        }

        return new ProgressMessage(R.id.TASK_ID_SEARCH_COORDINATOR, sb.toString(),
                                   progressMax, progressCount, null
        );
    }

    public void cancelTask(@IdRes final int taskId) {
        // reminder: this object, the SearchCoordinator is a pseudo task
        // we're only using "cancelTask" to conform with other usage
        cancel();
    }

    public static class CoverFilter {

        /**
         * Filter the {@link #BKEY_FILE_SPEC_ARRAY} present, selecting only the best
         * image for each index, and store those in {@link Book#BKEY_TMP_FILE_SPEC}.
         * This may result in removing ALL images if none are found suitable.
         *
         * @param bookData to filter
         */
        @AnyThread
        public void filter(@NonNull final Bundle bookData) {
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                final ArrayList<String> imageList =
                        bookData.getStringArrayList(BKEY_FILE_SPEC_ARRAY[cIdx]);

                if (imageList != null && !imageList.isEmpty()) {
                    // ALWAYS call even if we only have 1 image...
                    // We want to remove bad ones if needed.
                    final String coverName = getBestImage(imageList);
                    if (coverName != null) {
                        bookData.putString(Book.BKEY_TMP_FILE_SPEC[cIdx], coverName);
                    }
                }
                bookData.remove(BKEY_FILE_SPEC_ARRAY[cIdx]);
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
        private String getBestImage(@NonNull final ArrayList<String> imageList) {

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
                        final long size = (long) opt.outHeight * (long) opt.outWidth;
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
    }
}
