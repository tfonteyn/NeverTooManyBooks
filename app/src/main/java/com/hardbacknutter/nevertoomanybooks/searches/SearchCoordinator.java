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
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.MessageSwitch;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManagerListener;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Class to co-ordinate {@link SearchTask} objects using an existing {@link TaskManager}.
 * <p>
 * Uses the {@link TaskManager} and listens to {@link TaskManagerListener} messages.
 * <p>
 * It maintains its own internal list of tasks {@link #mManagedTasks} and as tasks it knows about
 * finish, it processes the data. Once all tasks are complete, it sends a message to its
 * creator via the {@link MessageSwitch}
 */
public class SearchCoordinator {

    /**
     * STATIC Object for passing messages from background tasks to activities
     * that may be recreated.
     * <p>
     * This object handles all underlying task messages for every instance of this class.
     */
    public static final MessageSwitch<SearchFinishedListener, SearchCoordinatorController>
            MESSAGE_SWITCH = new MessageSwitch<>();
    private static final String TAG = "SearchCoordinator";
    private static final int TO_MILLIS = 1_000_000;
    /**
     * Unique identifier for this instance.
     * <p>
     * Used as senderId for SENDING messages specific to this instance.
     */
    @NonNull
    private final Long mMessageSenderId;

    /** List of ManagedTask being managed by *this* object. */
    @NonNull
    private final ArrayList<ManagedTask> mManagedTasks = new ArrayList<>();

    /**
     * TaskManager which will execute our tasks, and send {@link TaskManagerListener}
     * messages.
     * This TaskManager may have other ManagedTask's than the ones *this* object creates.
     */
    @NonNull
    private final TaskManager mTaskManager;
    /**
     * Results from the search tasks.
     * <p>
     * key: site id (== task id)
     */
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, Bundle> mSearchResults =
            Collections.synchronizedMap(new HashMap<>());

    /** Controller instance (strong reference) for this object. */
    @SuppressWarnings("FieldCanBeLocal")
    private final SearchCoordinatorController mController = new SearchCoordinatorController() {

        @Override
        public void requestAbort() {
            mTaskManager.cancelAllTasks();
        }

        @NonNull
        @Override
        public SearchCoordinator getSearchCoordinator() {
            return SearchCoordinator.this;
        }
    };
    /** Sites to search on. */
    private ArrayList<Site> mSearchSites;
    /** Accumulated book data. */
    private Bundle mBookData;
    /** Flag indicating searches will be non-concurrent title/author found via ASIN. */
    private boolean mSearchingAsin;
    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean mWaitingForIsbn;
    /** Flag indicating a task was cancelled. */
    private boolean mCancelledFlg;
    /** Original author for search. */
    private String mAuthor;
    /** Original title for search. */
    private String mTitle;
    /** Original publisher for search. */
    private String mPublisher;
    /** Original ISBN for search. */
    private String mIsbn;
    /** Site native id for search. */
    private String mNativeId;
    /** Indicates original ISBN is really present and valid. */
    private boolean mHasValidIsbn;
    /** Whether of not to fetch thumbnails. */
    private boolean mFetchThumbnail;

    /** TerminologyMapper. */
    @Nullable
    private ColorMapper mColorMapper;
    /** TerminologyMapper. */
    @Nullable
    private FormatMapper mFormatMapper;

    private long mSearchStartTime;
    @SuppressLint("UseSparseArrays")
    private Map<Integer, Long> mSearchTasksStartTime = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    private Map<Integer, Long> mSearchTasksEndTime = new HashMap<>();

    /** Listen for finished searches. */
    private final TaskManagerListener mTaskManagerListener = new TaskManagerListener() {

        /**
         * When a task has ended, check if there are more tasks running.
         * If not, finish and send results back with {@link SearchCoordinator#sendResults}
         *
         * {@inheritDoc}
         */
        @Override
        public void onTaskFinished(@NonNull final ManagedTask task) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                mSearchTasksEndTime.put(task.getTaskId(), System.nanoTime());
            }

            // display final message from task.
            String finalMessage = task.getFinalMessage();
            if (finalMessage != null) {
                onTaskUserMessage(finalMessage);
            }

            // Handle the result, and optionally queue another task
            if (task instanceof SearchTask) {
                handleSearchTaskFinished((SearchTask) task);
            }

            int tasksActive;
            // Remove the finished task from our list
            synchronized (mManagedTasks) {
                mManagedTasks.remove(task);
                tasksActive = mManagedTasks.size();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "onTaskFinished"
                               + "|Task `" + task.getName() + "` finished");

                    for (ManagedTask t : mManagedTasks) {
                        Log.d(TAG, "onTaskFinished"
                                   + "|Task `" + t.getName() + "` still running");
                    }
                }
            }
            // no more tasks ? Then send the results back to our creator.
            if (tasksActive == 0) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "onTaskFinished|calling sendResults()");
                }

                sendResults(true);
            }
        }
    };

    /**
     * Constructor.
     *
     * @param taskManager            TaskManager to use
     * @param searchFinishedListener to send results to
     */
    public SearchCoordinator(@NonNull final TaskManager taskManager,
                             @NonNull final SearchFinishedListener searchFinishedListener) {

        mMessageSenderId = MESSAGE_SWITCH.createSender(mController);

        mTaskManager = taskManager;
        MESSAGE_SWITCH.addListener(mMessageSenderId, false, searchFinishedListener);

        Context context = App.getLocalizedAppContext();

        if (FormatMapper.isMappingAllowed(context)) {
            mFormatMapper = new FormatMapper(context);
        }
        if (ColorMapper.isMappingAllowed(context)) {
            mColorMapper = new ColorMapper(context);
        }
    }

    /**
     * Check if passed Bundle contains a non-blank ISBN string. Does not check if the ISBN is valid.
     *
     * @param bundle to check
     *
     * @return Present/absent
     */
    private static boolean hasIsbn(@NonNull final Bundle bundle) {
        String s = bundle.getString(DBDefinitions.KEY_ISBN);
        return s != null && !s.trim().isEmpty();
    }

    @NonNull
    public Long getId() {
        return mMessageSenderId;
    }

    public void setSearchSites(@NonNull final ArrayList<Site> searchSites) {
        // Developer sanity check
        if ((SearchSites.getEnabledSites(searchSites) & SearchSites.SEARCH_FLAG_MASK) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }

        mSearchSites = searchSites;
    }

    /**
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    public void setFetchThumbnail(final boolean fetchThumbnail) {
        mFetchThumbnail = fetchThumbnail;
    }

    public void searchByNativeId(@NonNull final Site site,
                                 @NonNull final String nativeId) {
        ArrayList<Site> sites = new ArrayList<>();
        sites.add(site);
        setSearchSites(sites);

        prepareSearch(nativeId, "", "", "", "");

        if (!startOneSearch(site)) {
            // if we did not start any tasks we are done.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "searchByNativeId|calling sendResults()");
            }

            sendResults(false);
        }
    }

    /**
     * Start a search.
     * At least one of isbn,title,author must be not be empty.
     *
     * @param isbn      to search for (can be empty)
     * @param author    to search for (can be empty)
     * @param title     to search for (can be empty)
     * @param publisher to search for (can be empty)
     */
    public void search(@NonNull final String isbn,
                       @NonNull final String author,
                       @NonNull final String title,
                       @NonNull final String publisher) {

        if (mSearchSites == null) {
            throw new IllegalArgumentException("call setSearchSites() first");
        }

        prepareSearch("", isbn, author, title, publisher);

        // We really want to ensure we get the same book from each, so if the isbn is
        // not present, search the sites one at a time until we get an isbn
        boolean tasksStarted = false;

        try {
            if (mIsbn != null && !mIsbn.isEmpty()) {
                mWaitingForIsbn = false;
                if (mHasValidIsbn) {
                    tasksStarted = startSearches(mSearchSites);

                } else if (SearchSites.ENABLE_AMAZON_AWS) {
                    // Assume it's an ASIN, and just search Amazon
                    mSearchingAsin = true;
                    ArrayList<Site> amazon = new ArrayList<>();
                    amazon.add(Site.newSite(SearchSites.AMAZON));
                    tasksStarted = startSearches(amazon);
                }
            } else {
                // Run one at a time until we find an ISBN.
                mWaitingForIsbn = true;
                tasksStarted = startNext();
            }
        } finally {
            // if we did not start any tasks we are done.
            if (!tasksStarted) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "search|calling sendResults()");
                }

                sendResults(false);
            }
        }
    }

    private void prepareSearch(@NonNull final String nativeId,
                               @NonNull final String isbn,
                               @NonNull final String author,
                               @NonNull final String title,
                               @NonNull final String publisher) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchStartTime = System.nanoTime();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "search"
                       + "|nativeId=" + nativeId
                       + "|isbn=" + isbn
                       + "|author=" + author
                       + "|title=" + title
                       + "|publisher=" + publisher);
        }

        // Developer sanity check
        if (NetworkUtils.networkUnavailable()) {
            throw new IllegalStateException("network should be checked before starting search");
        }

        // Developer sanity check
        if (!mManagedTasks.isEmpty()) {
            throw new IllegalStateException("don't start a new search while a search is running");
        }

        // Developer sanity check
        // Note we don't care about publisher.
        if (author.isEmpty() && title.isEmpty() && isbn.isEmpty() && nativeId.isEmpty()) {
            throw new IllegalArgumentException("Must specify at least one criteria non-empty:"
                                               + " nativeId=" + nativeId
                                               + ", isbn=" + isbn
                                               + ", author=" + author
                                               + ", publisher=" + publisher
                                               + ", title=" + title);
        }

        mWaitingForIsbn = false;
        mSearchingAsin = false;
        mCancelledFlg = false;

        // Save the input and initialize
        mBookData = new Bundle();
        mSearchResults.clear();

        mNativeId = nativeId;
        mIsbn = isbn;
        mHasValidIsbn = ISBN.isValid(mIsbn);

        mAuthor = author;
        mTitle = title;
        mPublisher = publisher;

        if (mFetchThumbnail) {
            // each site might have a cover, but when accumulating all covers found,
            // we rename the 'best' to the standard name. So here we make sure to
            // delete any orphaned temporary cover file
            StorageUtils.deleteFile(StorageUtils.getTempCoverFile());
        }

        // Listen for TaskManager messages.
        TaskManager.MESSAGE_SWITCH.addListener(mTaskManager.getId(), false, mTaskManagerListener);
    }

    /**
     * Start a single task.
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNext() {
        for (Site site : mSearchSites) {
            if (site.isEnabled()) {
                // If the site has not been searched yet, search it
                if (!mSearchResults.containsKey(site.id)) {
                    return startOneSearch(site);
                }
            }
        }
        return false;
    }

    /**
     * Start all searches listed in passed parameter that have not been run yet.
     *
     * <strong>Note:</strong> we explicitly pass in the searchSites instead of using the global
     * so we can force an Amazon-only search (ASIN based) when needed.
     *
     * @param currentSearchSites sites to search, see {@link SearchSites#SEARCH_FLAG_MASK}
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearches(@NonNull final ArrayList<Site> currentSearchSites) {
        boolean atLeastOneStarted = false;
        for (Site site : currentSearchSites) {
            if (site.isEnabled()) {
                // If the site has not been searched yet, search it
                if (!mSearchResults.containsKey(site.id)) {
                    if (startOneSearch(site)) {
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
     * @param site to search
     *
     * @return {@code true} if the search was started.
     */
    private boolean startOneSearch(@NonNull final Site site) {
        if (mCancelledFlg) {
            return false;
        }

        SearchEngine searchEngine = site.getSearchEngine();
        //URGENT: split the SearchTask ... we're testing conditions twice now.
        boolean canSearch =
                // if we have a native id, and the engine supports it, we can search.
                (!mNativeId.isEmpty() && (searchEngine instanceof SearchEngine.ByNativeId))
                ||
                // If have a valid ISBN, ...
                (mHasValidIsbn && (searchEngine instanceof SearchEngine.ByIsbn))
                ||
                // If we have valid text to search on, ...
                (((!mAuthor.isEmpty() && !mTitle.isEmpty()) || !mIsbn.isEmpty())
                 && (searchEngine instanceof SearchEngine.ByText));

        if (!(canSearch && searchEngine.isAvailable())) {
            return false;
        }

        // Note to self: we pass id/name and not site in the presumption we might
        // have search tasks for non-site related searches.
        SearchTask task = new SearchTask(mTaskManager, site.id, site.getName(), searchEngine);
        task.setNativeId(mNativeId);
        task.setIsbn(mIsbn);
        task.setAuthor(mAuthor);
        task.setTitle(mTitle);
        task.setPublisher(mPublisher);
        task.setFetchThumbnail(mFetchThumbnail);

        synchronized (mManagedTasks) {
            mManagedTasks.add(task);
            mTaskManager.addTask(task);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "startOneSearch|site=" + site);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            mSearchTasksStartTime.put(task.getTaskId(), System.nanoTime());
        }
        task.start();
        return true;
    }

    /**
     * Handle task search results; start another task if necessary.
     */
    private void handleSearchTaskFinished(@NonNull final SearchTask task) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "handleSearchTaskFinished"
                       + "|task=" + task.getName() + '`');
        }

        mCancelledFlg = task.isCancelled();
        Bundle bookData = task.getBookData();
        mSearchResults.put(task.getTaskId(), bookData);

        if (mCancelledFlg) {
            mWaitingForIsbn = false;
        } else {
            if (mSearchingAsin) {
                // If we searched AMAZON for an Asin, then see what we found
                mSearchingAsin = false;
                // Clear the 'isbn'
                mIsbn = "";
                if (hasIsbn(bookData)) {
                    // We got an ISBN, so pretend we were searching for an ISBN
                    mWaitingForIsbn = true;
                } else {
                    // See if we got author/title
                    mAuthor = bookData.getString(UniqueId.BKEY_SEARCH_AUTHOR);
                    mTitle = bookData.getString(DBDefinitions.KEY_TITLE);
                    if (mAuthor != null && !mAuthor.isEmpty()
                        && mTitle != null && !mTitle.isEmpty()) {
                        // We got them, so pretend we are searching by author/title now,
                        // and waiting for an ISBN...
                        mWaitingForIsbn = true;
                    }
                }
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "handleSearchTaskFinished"
                               + "|mSearchingAsin"
                               + "|mWaitingForIsbn=" + mWaitingForIsbn);
                }
            }

            if (mWaitingForIsbn) {
                if (hasIsbn(bookData)) {

                    mWaitingForIsbn = false;
                    mIsbn = bookData.getString(DBDefinitions.KEY_ISBN);

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                        Log.d(TAG, "handleSearchTaskFinished"
                                   + "|mWaitingForIsbn"
                                   + "|isbn=" + mIsbn);
                    }

                    // Start the others...even if they have run before.
                    // They will redo the search with the ISBN.
                    startSearches(mSearchSites);
                } else {
                    // Start next one that has not run.
                    startNext();
                }
            }
        }
    }

    /**
     * Accumulate all data and send it back to our caller.
     *
     * @param searchesDone {@code true} if at least one search was done.
     */
    private void sendResults(final boolean searchesDone) {
        // don't accept new tasks.
        TaskManager.MESSAGE_SWITCH.removeListener(mTaskManager.getId(), mTaskManagerListener);

        // set the end of the actual search task timer, and start a new timer for the processing.
        long processTime = System.nanoTime();

        if (searchesDone) {
            prepareResults();
        }

        // All done, Pass the data back
        MESSAGE_SWITCH.send(mMessageSenderId, listener -> {

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                for (Map.Entry<Integer, Long> entry : mSearchTasksStartTime.entrySet()) {
                    String name = SearchSites.getName(entry.getKey());
                    long start = entry.getValue();
                    Long end = mSearchTasksEndTime.get(entry.getKey());
                    if (end != null) {
                        Log.d(TAG, String.format(Locale.UK,
                                                 "onSearchFinished|taskId=%20s:%10d ms",
                                                 name, (end - start) / TO_MILLIS));
                    } else {
                        Log.d(TAG, String.format(Locale.UK,
                                                 "onSearchFinished|task=%20s|never finished",
                                                 name));
                    }
                }

                Log.d(TAG, String.format(Locale.UK,
                                         "onSearchFinished|total search time: %10d ms",
                                         (processTime - mSearchStartTime) / TO_MILLIS));
                Log.d(TAG, String.format(Locale.UK,
                                         "onSearchFinished|processing time: %10d ms",
                                         (System.nanoTime() - processTime) / TO_MILLIS));
            }

            listener.onSearchFinished(mCancelledFlg, mBookData);
            return true;
        });
    }

    private void prepareResults() {
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final List<Integer> sites = new ArrayList<>();

        if (mHasValidIsbn) {
            // If ISBN was passed, ignore entries with the wrong ISBN,
            // and put entries without ISBN at the end
            final List<Integer> uncertain = new ArrayList<>();
            for (Site site : SearchSites.getReliabilityOrder()) {
                if (mSearchResults.containsKey(site.id)) {
                    Bundle bookData = mSearchResults.get(site.id);
                    if (bookData != null && bookData.containsKey(DBDefinitions.KEY_ISBN)) {
                        String isbnFound = bookData.getString(DBDefinitions.KEY_ISBN);

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "ISBN.matches"
                                       + "|mIsbn" + mIsbn
                                       + "|isbnFound" + isbnFound);
                        }

                        if (ISBN.matches(mIsbn, isbnFound, true)) {
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
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbn);

        } else {
            // If ISBN was not passed, then just use the default order
            for (Site site : SearchSites.getReliabilityOrder()) {
                sites.add(site.id);
            }
        }

        // Merge the data we have. We do this in a fixed order rather than as the threads finish.
        for (int siteId : sites) {
            accumulateAllData(siteId);
        }

        // run the mappers
        if (mFormatMapper != null) {
            mFormatMapper.map(mBookData);
        }
        if (mColorMapper != null) {
            mColorMapper.map(mBookData);
        }

        // If there are thumbnails present, pick the biggest, delete others and rename.
        ImageUtils.cleanupImages(mBookData);

        // If we did not get an ISBN, use the originally we searched for.
        String isbn = mBookData.getString(DBDefinitions.KEY_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbn);
        }

        // If we did not get an title, use the originally we searched for.
        String title = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_TITLE, mTitle);
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
    private void accumulateAllData(@SearchSites.Id final int siteId) {
        // See if we got data from this site
        if (!mSearchResults.containsKey(siteId)) {
            return;
        }
        Bundle bookData = mSearchResults.get(siteId);

        // See if we REALLY got data from this source
        if (bookData == null || bookData.isEmpty()) {
            return;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "accumulateAllData"
                       + "|Processing data from search engine: " + SearchSites.getName(siteId));
        }
        for (String key : bookData.keySet()) {
            if (DBDefinitions.KEY_DATE_PUBLISHED.equals(key)
                || DBDefinitions.KEY_DATE_FIRST_PUBLICATION.equals(key)) {
                accumulateDates(key, bookData);

            } else if (UniqueId.BKEY_AUTHOR_ARRAY.equals(key)
                       || UniqueId.BKEY_SERIES_ARRAY.equals(key)
                       || UniqueId.BKEY_TOC_ENTRY_ARRAY.equals(key)
                       || UniqueId.BKEY_FILE_SPEC_ARRAY.equals(key)) {
                accumulateList(key, bookData);

            } else {
                // handle all normal String based entries
                accumulateStringData(key, bookData);
            }
        }
    }

    /**
     * Accumulate String data.
     * Handles other types via a .toString()
     *
     * @param key      Key of data
     * @param bookData Source Bundle
     */
    private void accumulateStringData(@NonNull final String key,
                                      @NonNull final Bundle bookData) {
        Object dataToAdd = bookData.get(key);
        if (dataToAdd == null || dataToAdd.toString().trim().isEmpty()) {
            return;
        }

        String dest = mBookData.getString(key);
        if (dest == null || dest.isEmpty()) {
            // just use it
            mBookData.putString(key, dataToAdd.toString());
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateStringData"
                           + "|copied: key=" + key
                           + "|value=`" + dataToAdd + '`');
            }
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateStringData|skipping: key=" + key);
            }
        }
    }

    /**
     * Grabs the 'new' date and checks if it's parsable.
     * If so, then check if the previous date was actually valid at all.
     * if not, use new date.
     */
    private void accumulateDates(@NonNull final String key,
                                 @NonNull final Bundle bookData) {
        String currentDateHeld = mBookData.getString(key);
        String dataToAdd = bookData.getString(key);
        // for debug message only
        boolean skipped = true;

        if (currentDateHeld == null || currentDateHeld.isEmpty()) {
            // copy, even if the incoming date might not be valid. We'll deal with that later.
            mBookData.putString(key, dataToAdd);
        } else {
            // Overwrite with the new date if we can parse it and if the current one
            // was present but not valid.
            if (dataToAdd != null) {
                Date newDate = DateUtils.parseDate(dataToAdd);
                if (newDate != null) {
                    if (DateUtils.parseDate(currentDateHeld) == null) {
                        // current date was invalid, use new one.
                        mBookData.putString(key, DateUtils.utcSqlDate(newDate));
                        skipped = false;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            Log.d(TAG, "accumulateDates|copied: key=" + key);
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            if (skipped) {
                Log.d(TAG, "accumulateDates|skipped: key=" + key);
            }
        }
    }

    /**
     * Accumulate ParcelableArrayList data.
     * Add if not present, or append.
     *
     * @param key      Key of data
     * @param bookData Source bundle with a ParcelableArrayList for the key
     * @param <T>      type of items in the ArrayList
     */
    private <T extends Parcelable> void accumulateList(@NonNull final String key,
                                                       @NonNull final Bundle bookData) {
        ArrayList<T> dataToAdd = bookData.getParcelableArrayList(key);
        if (dataToAdd == null || dataToAdd.isEmpty()) {
            return;
        }

        ArrayList<T> dest = mBookData.getParcelableArrayList(key);
        if (dest == null || dest.isEmpty()) {
            // just copy
            dest = dataToAdd;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateList"
                           + "|copied: key=" + key
                           + "|value=`" + dataToAdd + '`');
            }
        } else {
            // append
            dest.addAll(dataToAdd);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "accumulateList"
                           + "|appended: key=" + key
                           + "|value=`" + dataToAdd + '`');
            }
        }
        mBookData.putParcelableArrayList(key, dest);
    }

    /**
     * Controller interface for this Object.
     */
    interface SearchCoordinatorController {

        void requestAbort();

        @NonNull
        SearchCoordinator getSearchCoordinator();
    }

    /**
     * Allows other objects to know when a task completed.
     */
    public interface SearchFinishedListener {

        void onSearchFinished(boolean wasCancelled,
                              @NonNull Bundle bookData);
    }
}
