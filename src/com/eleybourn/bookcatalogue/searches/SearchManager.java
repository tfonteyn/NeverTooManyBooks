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
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.MessageSwitch;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;


/**
 * Class to co-ordinate multiple {@link ManagedSearchTask} objects using an existing {@link TaskManager}.
 *
 * It uses the {@link TaskManager} it is passed and listens to
 * {@link TaskManager.TaskManagerListener} messages.
 *
 * It maintain its own internal list of tasks {@link #mManagedTasks} and as tasks it knows about
 * finish, it processes the data. Once all tasks are complete, it sends a message to its
 * creator via its {@link MessageSwitch}
 *
 * @author Philip Warner
 */
public class SearchManager {

    /**
     * STATIC Object for passing messages from background tasks to activities that may be recreated
     *
     * This object handles all underlying task messages for every instance of this class.
     */
    private static final MessageSwitch<SearchManagerListener, SearchManagerController> mMessageSwitch = new MessageSwitch<>();

    /**
     * Unique identifier for this instance.
     *
     * Used as senderId for SENDING messages specific to this instance.
     */
    private final Long mMessageSenderId;
    /**
     * List of ManagedTask being managed by *this* object
     */
    private final ArrayList<ManagedTask> mManagedTasks = new ArrayList<>();
    /**
     * TaskManager which will execute our tasks, and send {@link TaskManager.TaskManagerListener}
     * messages.
     * This TaskManager may have other ManagedTask's than the ones *this* object creates.
     */
    @NonNull
    private final TaskManager mTaskManager;

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
     * Listener for TaskManager messages.
     */
    private final TaskManager.TaskManagerListener mTaskManagerListener =
            new TaskManager.TaskManagerListener() {
                /**
                 * {@link TaskManager.TaskFinishedMessage}
                 *
                 * When a task has ended, see check if there are more tasks running.
                 * If not, finish and send results back with {@link SearchManager#sendResults}
                 */
                @Override
                public void onTaskFinished(final @NonNull TaskManager manager,
                                           final @NonNull ManagedTask task) {
                    // Handle the result, and optionally queue another task
                    if (task instanceof ManagedSearchTask) {
                        handleSearchTaskFinished((ManagedSearchTask) task);
                    }

                    int size;
                    // Remove the finished task from our list
                    synchronized (mManagedTasks) {
                        mManagedTasks.remove(task);
                        size = mManagedTasks.size();

                        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                            for (ManagedTask t : mManagedTasks) {
                                Logger.info(SearchManager.this,
                                        "|Task `" + t.getName() + "` still running");
                            }
                        }
                    }
                    // no more tasks ? Then send the results back to our creator.
                    if (size == 0) {
                        // Stop listening FIRST...otherwise, if sendResults() calls a listener that starts
                        // a new task, we will stop listening for the new task.
                        TaskManager.getMessageSwitch().removeListener(mTaskManager.getId(), this);
                        // all searches done.
                        sendResults();
                    }
                }

                /**
                 * {@link TaskManager.TaskProgressMessage} ignored
                 */
                @Override
                public void onProgress(final int count, final int max, final @NonNull String message) {
                }

                /**
                 * {@link TaskManager.TaskUserMessage} ignored
                 */
                @Override
                public void onUserMessage(final @NonNull String message) {
                }

            };

    /**
     * Constructor.
     *
     * @param taskManager           TaskManager to use
     * @param searchManagerListener to send results to
     */
    public SearchManager(final @NonNull TaskManager taskManager,
                         final @NonNull SearchManagerListener searchManagerListener) {

        /* Controller instance for this specific SearchManager */
        SearchManagerController controller = new SearchManagerController() {
            public void requestAbort() {
                mTaskManager.cancelAllTasks();
            }

            @NonNull
            @Override
            public SearchManager getManager() {
                return SearchManager.this;
            }
        };
        mMessageSenderId = mMessageSwitch.createSender(controller);

        mTaskManager = taskManager;
        getMessageSwitch().addListener(getId(), searchManagerListener, false);
    }

    @NonNull
    public static MessageSwitch<SearchManagerListener, SearchManagerController> getMessageSwitch() {
        return mMessageSwitch;
    }

    /**
     * Start a search
     *
     * @param searchFlags    bitmask with sites to search, see {@link SearchSites.Site#SEARCH_ALL} and individual flags
     *
     *                       ONE of these three parameters must be !.isEmpty
     * @param author         to search for (can be empty)
     * @param title          to search for (can be empty)
     * @param isbn           to search for (can be empty)
     * @param fetchThumbnail whether to fetch thumbnails
     */
    public void search(final int searchFlags,
                       final @NonNull String author,
                       final @NonNull String title,
                       final @NonNull String isbn,
                       final boolean fetchThumbnail) {
        if ((searchFlags & SearchSites.Site.SEARCH_ALL) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }

        if (mManagedTasks.size() > 0) {
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
        if (mFetchThumbnail) {
            // delete any leftover temporary thumbnails
            StorageUtils.deleteTempCoverFile();
        }

        // Listen for TaskManager messages.
        TaskManager.getMessageSwitch().addListener(mTaskManager.getId(), mTaskManagerListener, false);

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
                    tasksStarted = startSearches(SearchSites.Site.SEARCH_AMAZON);
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
                TaskManager.getMessageSwitch().removeListener(mTaskManager.getId(), mTaskManagerListener);
                if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                    Logger.info(this, "listener stopped id=" + mTaskManager.getId());
                }
            }
        }

    }

    /**
     * Copy data from passed Bundle to current accumulated data.
     * Does some careful processing of the data.
     *
     * The Bundle will contain by default only String and {@link StringList} based data.
     *
     * NEWKIND: if you add a new Search task that adds non-string based data, you need handle that here.
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
            if (UniqueId.KEY_BOOK_DATE_PUBLISHED.equals(key)
                    || UniqueId.KEY_FIRST_PUBLICATION.equals(key)
                    ) {
                accumulateDates(key, bookData);

            } else if (UniqueId.BKEY_AUTHOR_ARRAY.equals(key)
                    || UniqueId.BKEY_SERIES_ARRAY.equals(key)
                    || UniqueId.BKEY_TOC_TITLES_ARRAY.equals(key)
                    || UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY.equals(key)
                    ) {
                accumulateParcelableArrayList(key, bookData);

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
    private void accumulateStringData(final @NonNull String key, final @NonNull Bundle bookData) {
        Object dataToAdd = bookData.get(key);
        if (dataToAdd == null || dataToAdd.toString().trim().isEmpty()) {
            return;
        }

        String dest = mBookData.getString(key);
        if (dest == null || dest.isEmpty()) {
            // just use it
            mBookData.putString(key, dataToAdd.toString());
            if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                Logger.info(this, "copied: key=" + key + ", value=`" + dataToAdd + "`");
            }

//        } else if (UniqueId.BKEY_THUMBNAIL_FILE_SPEC_STRING_LIST.equals(key)) {
//            // special case: StringList incoming, transform and add to the ArrayList
//            ArrayList<String> incomingList = StringList.decode("" + dataToAdd);
//            ArrayList<String> list = mBookData.getStringArrayList(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY);
//            if (list == null) {
//                list = new ArrayList<>();
//            }
//            list.addAll(incomingList);
//            mBookData.putStringArrayList(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY, list);
//            mBookData.remove(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_STRING_LIST);
//
//            if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
//                Logger.info(this, "appended: new thumbnail, fileSpec=`" + dataToAdd + "`");
//            }
        } else {
            if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                Logger.info(this, "skipping: key=" + key);
            }
        }
    }

    /**
     * Grabs the 'new' date and checks if it's parsable.
     * If so, then check if the previous date was actually valid at all.
     * if not, use new date.
     */
    private void accumulateDates(final @NonNull String key, final @NonNull Bundle bookData) {
        String currentDateHeld = mBookData.getString(key);
        String dataToAdd = bookData.getString(key);
        // for debug message only
        boolean skipped = true;

        if (currentDateHeld == null || currentDateHeld.isEmpty()) {
            // copy, even if the incoming date might not be valid. We'll deal with that later.
            mBookData.putString(key, dataToAdd);
        } else {
            // Overwrite with the new date if we can parse it and if the current one was present but not valid.
            if (dataToAdd != null) {
                Date newDate = DateUtils.parseDate(dataToAdd);
                if (newDate != null) {
                    if (DateUtils.parseDate(currentDateHeld) == null) {
                        // current date was invalid, use new one.
                        mBookData.putString(key, DateUtils.utcSqlDate(newDate));
                        skipped = false;
                        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                            Logger.info(this, "copied: key=" + key);
                        }
                    }
                }
            }
        }

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            if (skipped) {
                Logger.info(this, "skipped: key=" + key);
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
    private <T extends Parcelable> void accumulateParcelableArrayList(final @NonNull String key,
                                                                      final @NonNull Bundle bookData) {
        ArrayList<T> dataToAdd = bookData.getParcelableArrayList(key);
        if (dataToAdd == null || dataToAdd.isEmpty()) {
            return;
        }

        ArrayList<T> dest = mBookData.getParcelableArrayList(key);
        if (dest == null || dest.isEmpty()) {
            // just copy
            dest = dataToAdd;
            if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                Logger.info(this, "copied: key=" + key + ", value=`" + dataToAdd + "`");
            }
        } else {
            // append
            dest.addAll(dataToAdd);
            if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                Logger.info(this, "appended: key=" + key + ", value=`" + dataToAdd + "`");
            }
        }
        mBookData.putParcelableArrayList(key, dest);

    }

    /**
     * Combine all the data and create a book or display an error.
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
            for (SearchSites.Site site : SearchSites.getSitesByReliability()) {
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
            for (SearchSites.Site site : SearchSites.getSitesByReliability()) {
                results.add(site.id);
            }
        }

        // Merge the data we have. We do this in a fixed order rather than as the threads finish.
        for (int siteId : results) {
            accumulateData(siteId);
        }

        // If there are thumbnails present, pick the biggest, delete others and rename.
        ImageUtils.cleanupThumbnails(mBookData);

        // Try to use/construct isbn
        String isbn = mBookData.getString(UniqueId.KEY_BOOK_ISBN);
        if (isbn == null || isbn.isEmpty()) {
            // use the isbn we originally searched for.
            isbn = mIsbn;
        }
        if (isbn != null && !isbn.isEmpty()) {
            mBookData.putString(UniqueId.KEY_BOOK_ISBN, isbn);
        }

        // Try to use/construct title
        String title = mBookData.getString(UniqueId.KEY_TITLE);
        if (title == null || title.isEmpty()) {
            // use the title we originally searched for.
            title = mTitle;
        }
        if (title != null && !title.isEmpty()) {
            mBookData.putString(UniqueId.KEY_TITLE, title);
        }

        // check required data
        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

        // If book not found or is missing required data, warn the user
        if (mBookData.size() == 0 || authors == null || authors.isEmpty() || title == null || title.isEmpty()) {
            mTaskManager.sendTaskUserMessage(BookCatalogueApp.getResourceString(R.string.warning_book_not_found));
        }

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "All searches done for MessageSenderId:" + mMessageSenderId);
        }
        // All done, Pass the data back
        mMessageSwitch.send(mMessageSenderId, new MessageSwitch.Message<SearchManagerListener>() {
                    @Override
                    public boolean deliver(final @NonNull SearchManagerListener listener) {
                        if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                            Logger.info(SearchManager.this, "Delivering to SearchListener=" + listener +
                                    "|title=`" + mBookData.getString(UniqueId.KEY_TITLE) + "`");
                        }
                        return listener.onSearchFinished(mCancelledFlg, mBookData);
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
        for (SearchSites.Site source : SearchSites.getSites()) {
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
        for (SearchSites.Site source : SearchSites.getSites()) {
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

        ManagedSearchTask task = site.getTask(mTaskManager);
        task.setIsbn(mIsbn);
        task.setAuthor(mAuthor);
        task.setTitle(mTitle);
        task.setFetchThumbnail(mFetchThumbnail);

        synchronized (mManagedTasks) {
            mManagedTasks.add(task);
            mTaskManager.addTask(task);
        }

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "Starting search: " + task.getName());
        }
        task.start();
        return true;
    }

    /**
     * Handle task search results; start another task if necessary.
     *
     * @see TaskManager.TaskManagerListener#onTaskFinished
     */
    private void handleSearchTaskFinished(final @NonNull ManagedSearchTask managedSearchTask) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "ManagedSearchTask `" + managedSearchTask.getName() + "` finished");
        }
        mCancelledFlg = managedSearchTask.isCancelled();
        final Bundle bookData = managedSearchTask.getBookData();
        mSearchResults.put(managedSearchTask.getSearchId(), bookData);

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
                        // We got them, so pretend we are searching by author/title now, and waiting for an ISBN...
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

                    // Start the others...even if they have run before. They will redo the search with the ISBN.
                    startSearches(mSearchFlags);
                } else {
                    // Start next one that has not run.
                    startNext();
                }
            }
        }
    }

    public Long getId() {
        return mMessageSenderId;
    }

    /**
     * Controller interface for this Object
     */
    public interface SearchManagerController {
        void requestAbort();

        @NonNull
        SearchManager getManager();
    }

    /**
     * Allows other objects to know when a task completed.
     *
     * @author Philip Warner
     */
    public interface SearchManagerListener {
        boolean onSearchFinished(final boolean wasCancelled, final @NonNull Bundle bookData);
    }
}
