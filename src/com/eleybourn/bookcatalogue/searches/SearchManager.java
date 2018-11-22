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

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.TaskManager.TaskManagerListener;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

/**
 * Class to co-ordinate multiple {@link SearchTask} objects using an existing {@link TaskManager}.
 *
 * It uses the {@link TaskManager} it is passed and listens to {@link #onTaskEnded} messages;
 * it maintain its own internal list of tasks {@link #mRunningTasks} and as tasks it knows about
 * end, it processes the data. Once all tasks are complete, it sends a message to its
 * creator via its SearchHandler.
 *
 * @author Philip Warner
 */
public class SearchManager implements TaskManagerListener {

    /** see {@link TaskSwitch} */
    private static final TaskSwitch mMessageSwitch = new TaskSwitch();

    /** TaskManager for threads; may have other threads than the ones this object creates. */
    @NonNull
    private final TaskManager mTaskManager;
    /** List of threads created by *this* object. */
    private final ArrayList<ManagedTask> mRunningTasks = new ArrayList<>();

    private final SearchController mController = new SearchController() {
        public void requestAbort() {
            mTaskManager.cancelAllTasks();
        }

        @NonNull
        @Override
        public SearchManager getSearchManager() {
            return SearchManager.this;
        }
    };
    private final long mMessageSenderId = mMessageSwitch.createSender(mController);

    /** Flags applicable to *current* search */
    private int mSearchFlags;
    /** Accumulated book data */
    private Bundle mBookData = null;
    /** Options indicating searches will be non-concurrent title/author found via ASIN */
    private boolean mSearchingAsin = false;
    /** Options indicating searches will be non-concurrent until an ISBN is found */
    private boolean mWaitingForIsbn = false;
    /** Options indicating a task was cancelled. */
    private boolean mCancelledFlg = false;
    /** Original author for search */
    private String mAuthor;
    /** Original title for search */
    private String mTitle;
    /** Original ISBN for search */
    private String mIsbn;
    /** Indicates original ISBN is really present and valid */
    private boolean mHasValidIsbn;
    /** Whether of not to fetch thumbnails */
    private boolean mFetchThumbnail;
    /** Output from search threads */
    @NonNull
    private Hashtable<Integer, Bundle> mSearchResults = new Hashtable<>();

    /**
     * Constructor.
     *
     * @param taskManager    TaskManager to use
     * @param searchListener to send results to
     */
    public SearchManager(final @NonNull TaskManager taskManager,
                         final @NonNull SearchListener searchListener) {
        mTaskManager = taskManager;
        getMessageSwitch().addListener(getSenderId(), searchListener, false);
    }

    @NonNull
    public static TaskSwitch getMessageSwitch() {
        return mMessageSwitch;
    }

    /**
     * When a task has ended, see check if there are more tasks running.
     * If not, finish and send results back with {@link #sendResults}
     */
    @Override
    public void onTaskEnded(final @NonNull TaskManager manager, final @NonNull ManagedTask task) {
        // Handle the result, and optionally queue another task
        if (task instanceof SearchTask) {
            handleSearchTaskFinished((SearchTask) task);
        }

        int size;
        // Remove the finished task, and terminate if no more.
        synchronized (mRunningTasks) {
            mRunningTasks.remove(task);
            size = mRunningTasks.size();
            if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                for (ManagedTask t : mRunningTasks) {
                    Logger.info(this, "Task `" + t.getName() + "` still running");
                }
            }
        }
        if (size == 0) {
            // Stop listening FIRST...otherwise, if sendResults() calls a listener that starts
            // a new task, we will stop listening for the new task.
            TaskManager.getMessageSwitch().removeListener(mTaskManager.getSenderId(), this);
            // Notify the listeners.
            sendResults();
        }
    }

    /**
     * Other taskManager messages...we ignore them
     */
    @Override
    public void onProgress(final int count, final int max, final @NonNull String message) {
    }

    @Override
    public void onShowUserMessage(final @NonNull String message) {
    }

    @Override
    public void onFinished() {
    }

    /**
     * Start a search
     *
     * @param searchFlags    bitmask with sites to search, see {@link SearchSites#SEARCH_ALL} and individual flags
     *
     *                       ONE of these three parameters must be !.isEmpty
     * @param author         to search for (can be empty)
     * @param title          to search for (can be empty)
     * @param isbn           to search for (can be empty)
     *
     * @param fetchThumbnail whether to fetch thumbnails
     */
    public void search(final int searchFlags,
                       final @NonNull String author,
                       final @NonNull String title,
                       final @NonNull String isbn,
                       final boolean fetchThumbnail) {
        if ((searchFlags & SearchSites.SEARCH_ALL) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }

        if (mRunningTasks.size() > 0) {
            throw new IllegalStateException("Attempting to start new search while previous search running");
        }
        if (author.isEmpty() && title.isEmpty() && isbn.isEmpty()) {
            throw new IllegalArgumentException(
                    "Must specify at least one criteria non-empty: isbn=" + isbn + ", author=" + author + ", title=" + title);
        }

        // Save the flags
        mSearchFlags = searchFlags;

        // Save the input and initialize
        mBookData = new Bundle();
        mSearchResults = new Hashtable<>();

        mWaitingForIsbn = false;
        mCancelledFlg = false;

        mAuthor = author;
        mTitle = title;
        mIsbn = isbn;
        mHasValidIsbn = IsbnUtils.isValid(mIsbn);

        mFetchThumbnail = fetchThumbnail;

        // List for task ends
        TaskManager.getMessageSwitch().addListener(mTaskManager.getSenderId(), this, false);


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
                    tasksStarted = startSearches(SearchSites.SEARCH_AMAZON);
                }
            } else {
                // Run one at a time, startNext() defined the order.
                mWaitingForIsbn = true;
                tasksStarted = startNext();
            }
        } finally {
            if (!tasksStarted) {
                sendResults();
                TaskManager.getMessageSwitch().removeListener(mTaskManager.getSenderId(), this);
                if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                    Logger.info(this, "listener stopped id=" + mTaskManager.getSenderId());
                }
            }
        }

    }

    /**
     * Utility routine to append text data from one Bundle to another
     *
     * @param key    Key of data
     * @param source Source Bundle
     * @param dest   Destination Bundle
     */
    private void appendData(final @NonNull String key, final @NonNull Bundle source, final @NonNull Bundle dest) {
        String res = dest.getString(key) + StringList.MULTI_STRING_SEPARATOR + source.getString(key);
        dest.putString(key, res);

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "appended data to: key=" + key + ", value=`" + res + "`");
        }
    }

    /**
     * Copy data from passed Bundle to current accumulated data.
     * Does some careful processing of the data.
     *
     * @param searchId Source
     */
    private void accumulateData(final int searchId) {
        // See if we got data from this source
        if (!mSearchResults.containsKey(searchId)) {
            return;
        }
        Bundle bookData = mSearchResults.get(searchId);

        // See if we REALLY got data from this source
        if (bookData == null) {
            return;
        }
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "Processing data from search engine: id=" + searchId);
        }
        for (String key : bookData.keySet()) {
            // If its not there, copy it.
            String test = mBookData.getString(key);
            if (test == null || test.isEmpty()) {
                Object value = bookData.get(key);
                if (value != null && !value.toString().isEmpty()) {
                    mBookData.putString(key, value.toString());
                }
                if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                    if (value != null && !value.toString().isEmpty()) {
                        Logger.info(this, "copied: key=" + key + ", value=`" + value + "`");
                    } else {
                        Logger.info(this, "null not copied: key=" + key);
                    }
                }
            } else {
                // Copy, append or update data as appropriate.
                if (UniqueId.BKEY_AUTHOR_STRING_LIST.equals(key)) {
                    appendData(key, bookData, mBookData);
                } else if (UniqueId.BKEY_SERIES_STRING_LIST.equals(key)) {
                    appendData(key, bookData, mBookData);
                } else if (UniqueId.BKEY_TOC_STRING_LIST.equals(key)) {
                    appendData(key, bookData, mBookData);

                } else if (UniqueId.BKEY_THUMBNAIL_FILE_SPEC.equals(key)) {
                    appendData(key, bookData, mBookData);

                } else if (UniqueId.KEY_BOOK_DATE_PUBLISHED.equals(key)) {
                    grabDateIfParsable(bookData, key);

                } else if (UniqueId.KEY_FIRST_PUBLICATION.equals(key)) {
                    grabDateIfParsable(bookData, key);

                } else {
                    if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                        Logger.info(this, "not processed: key=" + key);
                    }
                }
            }
        }
    }

    /**
     * Grabs the 'new' date and checks if it's parsable.
     * If so, then check if the previous date was actually valid at all.
     * if not, use new date.
     */
    private void grabDateIfParsable(final @NonNull Bundle bookData, final @NonNull String key) {
        // Grab a different date if we can parse it.
        String pd = bookData.getString(key);
        if (pd != null) {
            Date newDate = DateUtils.parseDate(pd);
            if (newDate != null) {
                // replace 'previous' date if it was useless
                String curr = mBookData.getString(key);
                if (curr != null && DateUtils.parseDate(curr) == null) {
                    mBookData.putString(key, DateUtils.utcSqlDate(newDate));
                    if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                        Logger.info(this, "replaced: key=" + key);
                    }
                }
            }
        }
    }

    /**
     * Combine all the data and create a book or display an error.
     *
     * This is where string encoded fields get transformed into arrays
     * {@link UniqueId#BKEY_AUTHOR_STRING_LIST} -> {@link UniqueId#BKEY_AUTHOR_ARRAY}
     * {@link UniqueId#BKEY_SERIES_STRING_LIST} -> {@link UniqueId#BKEY_SERIES_STRING_LIST}
     * {@link UniqueId#BKEY_TOC_STRING_LIST} -> {@link UniqueId#BKEY_TOC_STRING_LIST}
     */
    private void sendResults() {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "All searches done, preparing results");
        }
        /* This list will be the actual order of the result we apply, based on the
         * actual results and the default order. */
        final List<Integer> results = new ArrayList<>();

        if (mHasValidIsbn) {
            // If ISBN was passed, ignore entries with the wrong ISBN, and put entries with no ISBN at the end
            final List<Integer> uncertain = new ArrayList<>();
            for (SearchSites.Site site : SearchSites.getReliabilityOrder()) {
                if (mSearchResults.containsKey(site.id)) {
                    Bundle bookData = mSearchResults.get(site.id);
                    if (bookData.containsKey(UniqueId.KEY_BOOK_ISBN)) {
                        if (IsbnUtils.matches(mIsbn, bookData.getString(UniqueId.KEY_BOOK_ISBN))) {
                            results.add(site.id);
                        }
                    } else {
                        uncertain.add(site.id);
                    }
                }
            }
            results.addAll(uncertain);
            // Add the passed ISBN first; avoid overwriting
            mBookData.putString(UniqueId.KEY_BOOK_ISBN, mIsbn);
        } else {
            // If ISBN was not passed, then just use the default order
            for (SearchSites.Site site : SearchSites.getReliabilityOrder()) {
                results.add(site.id);
            }
        }

        // Merge the data we have. We do this in a fixed order rather than as the threads finish.
        for (int i : results) {
            accumulateData(i);
        }

        // If there are thumbnails present, pick the biggest, delete others and rename.
        ImageUtils.cleanupThumbnails(mBookData);

        // Try to use/construct authors
        String authors = mBookData.getString(UniqueId.BKEY_AUTHOR_STRING_LIST);
        if (authors == null || authors.isEmpty()) {
            authors = mAuthor;
        }
        if (authors != null && !authors.isEmpty()) {
            ArrayList<Author> list = StringList.getAuthorUtils().decode(authors, false);
            mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
            mBookData.remove(UniqueId.BKEY_AUTHOR_STRING_LIST);
        }

        // Try to use/construct title
        String title = mBookData.getString(UniqueId.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            title = mTitle;
        }
        if (title != null && !title.isEmpty()) {
            mBookData.putString(UniqueId.KEY_TITLE, title);
        }

        // Try to use/construct isbn
        String isbn = mBookData.getString(UniqueId.KEY_BOOK_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            isbn = mIsbn;
        }
        if (isbn != null && !isbn.isEmpty()) {
            mBookData.putString(UniqueId.KEY_BOOK_ISBN, isbn);
        }

        // Try to use/construct series
        String series = mBookData.getString(UniqueId.BKEY_SERIES_STRING_LIST);
        if (series != null && !series.isEmpty()) {
            ArrayList<Series> list = StringList.getSeriesUtils().decode(series, false);
            mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
            mBookData.remove(UniqueId.BKEY_SERIES_STRING_LIST);
        }

        // Try to use/construct TOCEntries
        String tocEntriesAsStringList = mBookData.getString(UniqueId.BKEY_TOC_STRING_LIST);
        if (tocEntriesAsStringList != null && !tocEntriesAsStringList.isEmpty()) {
            ArrayList<TOCEntry> list = StringList.getTOCUtils().decode(tocEntriesAsStringList, false);
            mBookData.putParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY, list);
            mBookData.remove(UniqueId.BKEY_TOC_STRING_LIST);
        }

        // If book is not found or missing required data, warn the user
        if (authors == null || authors.isEmpty() || title == null || title.isEmpty()) {
            mTaskManager.showUserMessage(BookCatalogueApp.getResourceString(R.string.warning_book_not_found));
        }
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "All searches done, passing the data back to mMessageSenderId:" + mMessageSenderId);
        }
        // All done, Pass the data back
        mMessageSwitch.send(mMessageSenderId, new MessageSwitch.Message<SearchListener>() {
                    @Override
                    public boolean deliver(final @NonNull SearchListener listener) {
                        if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
                            Logger.info(SearchManager.this, "Delivering search results to SearchListener: " + listener +
                                    "\n for title: " + mBookData.getString(UniqueId.KEY_TITLE));
                        }
                        return listener.onSearchFinished(mBookData, mCancelledFlg);
                    }
                }
        );
    }

    /**
     * When running in single-stream mode, start the next thread that has no data.
     * While Google is reputedly most likely to succeed, it also produces garbage a lot.
     * So we search Amazon, Goodreads, Google and LT last as it REQUIRES an ISBN.
     */
    private boolean startNext() {
        // Loop though in 'search-priority' order
        for (SearchSites.Site source : SearchSites.getSiteSearchOrder()) {
            // If this search includes the source, check it
            if (source.enabled && ((mSearchFlags & source.id) != 0)) {
                // If the source has not been searched, search it
                if (!mSearchResults.containsKey(source.id)) {
                    return startOneSearch(source);
                }
            }
        }
        return false;
    }

    /**
     * Start all searches listed in passed parameter that have not been run yet.
     *
     * @param sources bitmask with sites to search
     */
    private boolean startSearches(final int sources) {
        boolean atLeastOneStarted = false;
        // Loop searches in priority order
        for (SearchSites.Site source : SearchSites.getSiteSearchOrder()) {
            // If requested search contains this source...
            if (source.enabled && ((sources & source.id) != 0)) {
                // If we have not run this search...
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
     */
    private boolean startOneSearch(final @NonNull SearchSites.Site site) {
        if (mCancelledFlg) {
            return false;
        }
        // special case, some sites can only be searched with an ISBN
        if (site.isbnOnly && !mHasValidIsbn) {
            return false;
        }

        SearchTask searchTask = site.getTask(mTaskManager);
        searchTask.setIsbn(mIsbn);
        searchTask.setAuthor(mAuthor);
        searchTask.setTitle(mTitle);
        searchTask.setFetchThumbnail(mFetchThumbnail);

        synchronized (mRunningTasks) {
            mRunningTasks.add(searchTask);
            mTaskManager.addTask(searchTask);
        }

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "Starting search: " + searchTask.getName());
        }
        searchTask.start();
        return true;
    }

    /**
     * Handle task search results; start another task if necessary.
     *
     * @see #onTaskEnded
     */
    private void handleSearchTaskFinished(final @NonNull SearchTask searchTask) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "SearchTask `" + searchTask.getName() + "` finished");
        }
        mCancelledFlg = searchTask.isCancelled();
        final Bundle bookData = searchTask.getBookData();
        mSearchResults.put(searchTask.getSearchId(), bookData);

        if (mCancelledFlg) {
            mWaitingForIsbn = false;
        } else {
            if (mSearchingAsin) {
                // If we searched AMAZON for an Asin, then see what we found
                mSearchingAsin = false;
                // Clear the 'isbn'
                mIsbn = "";
                if (BundleUtils.isNonBlankString(bookData, UniqueId.KEY_BOOK_ISBN)) {
                    // We got an ISBN, so pretend we were searching for an ISBN
                    mWaitingForIsbn = true;
                } else {
                    // See if we got author/title
                    mAuthor = bookData.getString(UniqueId.BKEY_SEARCH_AUTHOR);
                    mTitle = bookData.getString(UniqueId.KEY_TITLE);
                    if (mAuthor != null && !mAuthor.isEmpty() && mTitle != null && !mTitle.isEmpty()) {
                        // We got them, so pretend we are searching by author/title now, and waiting for an ASIN...
                        mWaitingForIsbn = true;
                    }
                }
                if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                    Logger.info(this, "mSearchingAsin result mWaitingForIsbn=" + mWaitingForIsbn);
                }
            }

            if (mWaitingForIsbn) {
                if (BundleUtils.isNonBlankString(bookData, UniqueId.KEY_BOOK_ISBN)) {

                    mWaitingForIsbn = false;

                    mIsbn = bookData.getString(UniqueId.KEY_BOOK_ISBN);
                    if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                        Logger.info(this, "mWaitingForIsbn result isbn=" + mIsbn);
                    }

                    // Start the others...even if they have run before
                    startSearches(mSearchFlags);
                } else {
                    // Start next one that has not run.
                    startNext();
                }
            }
        }
    }

    public long getSenderId() {
        return mMessageSenderId;
    }

    /**
     * Allows other objects to know when a task completed.
     *
     * @author Philip Warner
     */
    public interface SearchListener {
        boolean onSearchFinished(final @NonNull Bundle bookData, final boolean wasCancelled);
    }

    /**
     * don't believe Android Studio. These methods *are* used
     */
    public interface SearchController {
        void requestAbort();

        @NonNull
        SearchManager getSearchManager();
    }

    /**
     * STATIC Object for passing messages from background tasks to activities that may be recreated
     *
     * This object handles all underlying OnTaskEndedListener messages for every instance of this class.
     *
     * REMINDER: must be public or compile fails... lint is to eager here.
     */
    @SuppressWarnings("WeakerAccess")
    public static class TaskSwitch extends MessageSwitch<SearchListener, SearchController> {
    }

}
