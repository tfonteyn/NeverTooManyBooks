/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.MessageSwitch;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManagerListener;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.StringList;

/**
 * Class to co-ordinate {@link SearchTask} objects using an existing {@link TaskManager}.
 * <p>
 * Uses the {@link TaskManager} and listens to {@link TaskManagerListener} messages.
 * <p>
 * It maintain its own internal list of tasks {@link #mManagedTasks} and as tasks it knows about
 * finish, it processes the data. Once all tasks are complete, it sends a message to its
 * creator via its {@link MessageSwitch}
 *
 * @author Philip Warner
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

    /**
     * Unique identifier for this instance.
     * <p>
     * Used as senderId for SENDING messages specific to this instance.
     */
    @NonNull
    private final Long mMessageSenderId;

    /**
     * List of ManagedTask being managed by *this* object.
     */
    @NonNull
    private final ArrayList<ManagedTask> mManagedTasks = new ArrayList<>();

    /**
     * TaskManager which will execute our tasks, and send {@link TaskManagerListener}
     * messages.
     * This TaskManager may have other ManagedTask's than the ones *this* object creates.
     */
    @NonNull
    private final TaskManager mTaskManager;
    /** Output from search threads. */
    @SuppressLint("UseSparseArrays")
    @NonNull
    private final Map<Integer, Bundle> mSearchResults =
            Collections.synchronizedMap(new HashMap<>());
    /** Controller instance (strong reference) for this specific SearchManager. */
    @SuppressWarnings("FieldCanBeLocal")
    private final SearchCoordinatorController mController = new SearchCoordinatorController() {

        public void requestAbort() {
            mTaskManager.cancelAllTasks();
        }

        /**
         *
         * @return the search coordinator.
         */
        @NonNull
        @Override
        public SearchCoordinator getSearchCoordinator() {
            return SearchCoordinator.this;
        }
    };
    /** Flags applicable to *current* search. */
    private int mSearchFlags;
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
    /** Original ISBN for search. */
    private String mIsbn;
    /** Indicates original ISBN is really present and valid. */
    private boolean mHasValidIsbn;
    /** Whether of not to fetch thumbnails. */
    private boolean mFetchThumbnail;
    private final TaskManagerListener mListener = new TaskManagerListener() {

        /**
         * {@link TaskManager.TaskFinishedMessage}
         * <p>
         * When a task has ended, see check if there are more tasks running.
         * If not, finish and send results back with {@link SearchCoordinator#sendResults}
         */
        @Override
        public void onTaskFinished(@NonNull final TaskManager taskManager,
                                   @NonNull final ManagedTask task) {
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

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                    dumpTasks(task);
                }
            }
            // no more tasks ? Then send the results back to our creator.
            if (tasksActive == 0) {
                // Stop listening FIRST...otherwise, if sendResults() calls a listener
                // that starts a new task, we will stop listening for the new task.
                TaskManager.MESSAGE_SWITCH.removeListener(mTaskManager.getId(), this);
                // all searches done.
                sendResults();
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

    /**
     * Start a search.
     *
     * @param searchFlags    bitmask with sites to search,
     *                       see {@link SearchSites#SEARCH_ALL} and individual flags
     *                       <p>
     *                       ONE of these three parameters must be !.isEmpty
     * @param author         to search for (can be empty)
     * @param title          to search for (can be empty)
     * @param isbn           to search for (can be empty)
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    public void search(final int searchFlags,
                       @NonNull final String author,
                       @NonNull final String title,
                       @NonNull final String isbn,
                       final boolean fetchThumbnail) {

        // Developer sanity check
        if (!NetworkUtils.isNetworkAvailable()) {
            throw new IllegalStateException("network should be checked before starting search");
        }

        // Developer sanity check
        if (!mManagedTasks.isEmpty()) {
            throw new IllegalStateException("don't start a new search while a search is running");
        }

        // Developer sanity check
        if ((searchFlags & SearchSites.SEARCH_ALL) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }

        // Developer sanity check
        if (author.isEmpty() && title.isEmpty() && isbn.isEmpty()) {
            throw new IllegalArgumentException("Must specify at least one criteria non-empty:"
                                                       + " isbn=" + isbn
                                                       + ", author=" + author
                                                       + ", title=" + title);
        }

        // Save the flags
        mSearchFlags = searchFlags;

        // Save the input and initialize
        mBookData = new Bundle();
        mSearchResults.clear();

        mWaitingForIsbn = false;
        mCancelledFlg = false;

        mAuthor = author;
        mTitle = title;
        mIsbn = isbn;
        mHasValidIsbn = ISBN.isValid(mIsbn);

        mFetchThumbnail = fetchThumbnail;
        if (mFetchThumbnail) {
            // delete any leftover temporary thumbnails
            StorageUtils.deleteTempCoverFile();
        }

        // Listen for TaskManager messages.
        TaskManager.MESSAGE_SWITCH.addListener(mTaskManager.getId(), false, mListener);

        // We really want to ensure we get the same book from each, so if isbn is
        // not present, search the sites one at a time till we get an isbn
        boolean tasksStarted = false;
        mSearchingAsin = false;
        try {
            if (mIsbn != null && !mIsbn.isEmpty()) {
                if (mHasValidIsbn) {
                    // We have a valid ISBN, just do the search
                    mWaitingForIsbn = false;
                    // go go go !!!
                    tasksStarted = startSearches(mSearchFlags);
                } else {
                    // Assume it's an ASIN, and just search Amazon
                    mSearchingAsin = true;
                    mWaitingForIsbn = false;
                    tasksStarted = startSearches(SearchSites.AMAZON);
                }
            } else {
                // Run one at a time, startNext() defined the order.
                mWaitingForIsbn = true;
                tasksStarted = startNext();
            }
        } finally {
            if (!tasksStarted) {
                // accumulate all data and send it back to our caller.
                sendResults();
                // stop listening
                TaskManager.MESSAGE_SWITCH.removeListener(mTaskManager.getId(), mListener);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                    Logger.debug(this, "search",
                                 "listener stopped id=" + mTaskManager.getId());
                }
            }
        }

    }

    /**
     * Start a single task.
     * <p>
     * When running in single-stream mode, start the next thread that has no data.
     * While Google is reputedly most likely to succeed, it also produces garbage a lot of the time.
     * So we search Amazon, Goodreads, Google and LT last as it REQUIRES an ISBN.
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNext() {
        // Loop though in 'search-priority' order
        for (Site site : SearchSites.getSites()) {
            // If this search includes the source, check it
            if (site.isEnabled() && ((mSearchFlags & site.id) != 0)) {
                // If the source has not been searched, search it
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
     * @param sources bitmask with sites to search
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearches(final int sources) {
        boolean atLeastOneStarted = false;
        // Loop searches in priority order
        for (Site source : SearchSites.getSites()) {
            // If this search includes the source, check it
            if (source.isEnabled() && ((sources & source.id) != 0)) {
                // If the source has not been searched, search it
                if (!mSearchResults.containsKey(source.id)) {
                    // Run it now
                    if (startOneSearch(source)) {
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

        // special case, some sites can only be searched with an ISBN
        if (searchEngine.isIsbnOnly() && !mHasValidIsbn) {
            return false;
        }

        SearchTask task = new SearchTask(mTaskManager, site.id, site.getName(), searchEngine);
        task.setIsbn(mIsbn);
        task.setAuthor(mAuthor);
        task.setTitle(mTitle);
        task.setFetchThumbnail(mFetchThumbnail);

        synchronized (mManagedTasks) {
            mManagedTasks.add(task);
            mTaskManager.addTask(task);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "startOneSearch",
                         "Starting search: " + task.getName());
        }

        task.start();
        return true;

    }


    /**
     * DEBUG only.
     * <p>
     * log current finishing task + any still active tasks.
     */
    private void dumpTasks(@NonNull final ManagedTask task) {
        Logger.debug(this, "onTaskFinished",
                     "Task `" + task.getName() + "` finished");

        for (ManagedTask t : mManagedTasks) {
            Logger.debug(this, "onTaskFinished",
                         "Task `" + t.getName() + "` still running");
        }
    }

    /**
     * Handle task search results; start another task if necessary.
     */
    private void handleSearchTaskFinished(@NonNull final SearchTask task) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debugEnter(this, "handleSearchTaskFinished",
                              '`' + task.getName() + '`');
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
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                    Logger.debug(this, "handleSearchTaskFinished",
                                 "mSearchingAsin result mWaitingForIsbn=" + mWaitingForIsbn);
                }
            }

            if (mWaitingForIsbn) {
                if (hasIsbn(bookData)) {

                    mWaitingForIsbn = false;
                    mIsbn = bookData.getString(DBDefinitions.KEY_ISBN);

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                        Logger.debug(this, "handleSearchTaskFinished",
                                     "mWaitingForIsbn result isbn=" + mIsbn);
                    }

                    // Start the others...even if they have run before.
                    // They will redo the search with the ISBN.
                    startSearches(mSearchFlags);
                } else {
                    // Start next one that has not run.
                    startNext();
                }
            }
        }
    }

    /**
     * Combine all the data and create a book or display an error.
     */
    private void sendResults() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "sendResults", "All searches done, preparing results");
        }
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final List<Integer> results = new ArrayList<>();

        if (mHasValidIsbn) {
            // If ISBN was passed, ignore entries with the wrong ISBN,
            // and put entries without ISBN at the end
            final List<Integer> uncertain = new ArrayList<>();
            for (Site site : SearchSites.getSitesByReliability()) {
                if (mSearchResults.containsKey(site.id)) {
                    Bundle bookData = mSearchResults.get(site.id);
                    if (bookData != null && bookData.containsKey(DBDefinitions.KEY_ISBN)) {
                        if (ISBN.matches(mIsbn, bookData.getString(DBDefinitions.KEY_ISBN))) {
                            results.add(site.id);
                        }
                    } else {
                        uncertain.add(site.id);
                    }
                }
            }
            results.addAll(uncertain);
            // Add the passed ISBN first; avoid overwriting
            mBookData.putString(DBDefinitions.KEY_ISBN, mIsbn);
        } else {
            // If ISBN was not passed, then just use the default order
            for (Site site : SearchSites.getSitesByReliability()) {
                results.add(site.id);
            }
        }

        // Merge the data we have. We do this in a fixed order rather than as the threads finish.
        for (int siteId : results) {
            accumulateAllData(siteId);
        }

        // If there are thumbnails present, pick the biggest, delete others and rename.
        ImageUtils.cleanupImages(mBookData);

        // Try to use/construct isbn
        String isbn = mBookData.getString(DBDefinitions.KEY_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            // use the isbn we originally searched for.
            isbn = mIsbn;
        }
        if (isbn != null && !isbn.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_ISBN, isbn);
        }

        // Try to use/construct title
        String title = mBookData.getString(DBDefinitions.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            // use the title we originally searched for.
            title = mTitle;
        }
        if (title != null && !title.isEmpty()) {
            mBookData.putString(DBDefinitions.KEY_TITLE, title);
        }

        // check required data
        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

        // If book not found or is missing required data, info the user
        if (mBookData.isEmpty()) {
            mTaskManager.sendUserMessage(R.string.warning_book_not_found);
        } else if (authors == null || authors.isEmpty()
                || title == null || title.isEmpty()) {
            mTaskManager.sendUserMessage(R.string.warning_required_title_and_author);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "sendResults",
                         "All searches done for MessageSenderId:" + mMessageSenderId);
        }
        // All done, Pass the data back
        MESSAGE_SWITCH
                .send(mMessageSenderId,
                      listener -> {
                          if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
                              Logger.debug(this, "sendResults",
                                           "Delivering to SearchListener=" + listener,
                                           "title=`" + mBookData.getString(
                                                   DBDefinitions.KEY_TITLE) + '`');
                          }
                          listener.onSearchFinished(mCancelledFlg, mBookData);
                          return true;
                      }
                );
    }

    /**
     * Copy data from passed Bundle to current accumulated data.
     * Does some careful processing of the data.
     * <p>
     * The Bundle will contain by default only String and {@link StringList} based data.
     * <p>
     * NEWKIND: if you add a new Search task that adds non-string based data, handle that here.
     *
     * @param searchId Source
     */
    private void accumulateAllData(final int searchId) {
        // See if we got data from this source
        if (!mSearchResults.containsKey(searchId)) {
            return;
        }
        Bundle bookData = mSearchResults.get(searchId);

        // See if we REALLY got data from this source
        if (bookData == null || bookData.isEmpty()) {
            return;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "accumulateAllData",
                         "Processing data from search engine: id=" + searchId);
        }
        for (String key : bookData.keySet()) {
            if (DBDefinitions.KEY_DATE_PUBLISHED.equals(key)
                    || DBDefinitions.KEY_DATE_FIRST_PUBLISHED.equals(key)) {
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
     * Accumulate String or {@link StringList} data.
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
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                Logger.debug(this, "accumulateStringData",
                             "copied: key=" + key + ", value=`" + dataToAdd + '`');
            }

//        } else if (UniqueId.BKEY_THUMBNAIL_FILE_SPEC_STRING_LIST.equals(key)) {
//            // special case: StringList incoming, transform and add to the ArrayList
//            ArrayList<String> incomingList = StringList.decode("" + dataToAdd);
//            ArrayList<String> list =
//                   mBookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
//            if (list == null) {
//                list = new ArrayList<>();
//            }
//            list.addAll(incomingList);
//            mBookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, list);
//            mBookData.remove(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_STRING_LIST);
//
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
//                Logger.info(this, "accumulateStringData",
//                      "appended: new thumbnail, fileSpec=`" + dataToAdd + "`");
//            }
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                Logger.debug(this, "accumulateStringData",
                             "skipping: key=" + key);
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
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                            Logger.debug(this, "accumulateDates",
                                         "copied: key=" + key);
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            if (skipped) {
                Logger.debug(this, "accumulateDates", "skipped: key=" + key);
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
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                Logger.debug(this, "accumulateList",
                             "copied: key=" + key + ", value=`" + dataToAdd + '`');
            }
        } else {
            // append
            dest.addAll(dataToAdd);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                Logger.debug(this, "accumulateList",
                             "appended: key=" + key + ", value=`" + dataToAdd + '`');
            }
        }
        mBookData.putParcelableArrayList(key, dest);
    }


    /**
     * Controller interface for this Object.
     */
    public interface SearchCoordinatorController {

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
