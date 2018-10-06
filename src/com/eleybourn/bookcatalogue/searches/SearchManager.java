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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch;
import com.eleybourn.bookcatalogue.searches.amazon.SearchAmazonThread;
import com.eleybourn.bookcatalogue.searches.goodreads.SearchGoodreadsThread;
import com.eleybourn.bookcatalogue.searches.googlebooks.SearchGoogleBooksThread;
import com.eleybourn.bookcatalogue.searches.isfdb.SearchISFDBThread;
import com.eleybourn.bookcatalogue.searches.librarything.SearchLibraryThingThread;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.TaskManager.TaskManagerListener;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

/**
 * Class to co-ordinate multiple SearchThread objects using an existing TaskManager.
 *
 * It uses the task manager it is passed and listens to OnTaskEndedListener messages;
 * it maintain its own internal list of tasks and as tasks it knows about end, it
 * processes the data. Once all tasks are complete, it sends a message to its
 * creator via its SearchHandler.
 *
 * @author Philip Warner
 */
public class SearchManager implements TaskManagerListener {
    /** Flag indicating a search source to use */
    public static final int SEARCH_GOOGLE = 1;
    /** Flag indicating a search source to use */
    public static final int SEARCH_AMAZON = 2;
    /** Flag indicating a search source to use */
    public static final int SEARCH_LIBRARY_THING = 4;
    /** Flag indicating a search source to use */
    public static final int SEARCH_GOODREADS = 8;
    /** Flag indicating a search source to use */
    public static final int SEARCH_ISFDB = 16;
    /** Mask including all search sources */
    public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON | SEARCH_LIBRARY_THING | SEARCH_GOODREADS | SEARCH_ISFDB;
    /** */
    static final String BKEY_SEARCH_SITES = "searchSitesList";
    private static final String TAG = "SearchManager";

    /** the default search site order */
    private static final List<SearchSite> mSearchOrderDefaults = new ArrayList<>();
    /** the default search site order */
    private static final List<SearchSite> mCoverSearchOrderDefaults = new ArrayList<>();
    /** TODO: not user configurable for now, but plumbing installed */
    private static final List<SearchSite> mReliabilityOrder;
    /** see {@link TaskSwitch} */
    private static final TaskSwitch mMessageSwitch = new TaskSwitch();
    /** the users preferred search site order */
    private static ArrayList<SearchSite> mPreferredSearchOrder;
    /** the users preferred search site order */
    private static ArrayList<SearchSite> mPreferredCoverSearchOrder;

    static {
        /*ENHANCE: note to self... redo this mess. To complicated for what it is doing.

         * default search order
         *
         * NEWKIND: make sure to set the reliability field correctly
         *
         *  Original app reliability order was:
         *  {SEARCH_GOODREADS, SEARCH_AMAZON, SEARCH_GOOGLE, SEARCH_LIBRARY_THING}
         */
        mSearchOrderDefaults.add(new SearchSite(SEARCH_AMAZON, "Amazon", 0, true, 1));
        mSearchOrderDefaults.add(new SearchSite(SEARCH_GOODREADS, "GoodReads", 1, true, 0));
        mSearchOrderDefaults.add(new SearchSite(SEARCH_GOOGLE, "Google", 2, true, 2));
        mSearchOrderDefaults.add(new SearchSite(SEARCH_LIBRARY_THING, "LibraryThing", 3, true, 3));
        mSearchOrderDefaults.add(new SearchSite(SEARCH_ISFDB, "ISFDB", 4, false, 4));

        mCoverSearchOrderDefaults.add(new SearchSite(SEARCH_GOOGLE, "Google", 0, true));
        mCoverSearchOrderDefaults.add(new SearchSite(SEARCH_LIBRARY_THING, "LibraryThing", 1, true));
        mCoverSearchOrderDefaults.add(new SearchSite(SEARCH_ISFDB, "ISFDB", 2, false));


        // we're going to use set(index,...), so make them big enough
        mPreferredSearchOrder = new ArrayList<>(mSearchOrderDefaults);
        mReliabilityOrder = new ArrayList<>(mSearchOrderDefaults);

        mPreferredCoverSearchOrder = new ArrayList<>(mCoverSearchOrderDefaults);

        SharedPreferences prefs = BookCatalogueApp.getSharedPreferences();
        for (SearchSite site : mSearchOrderDefaults) {
            site.enabled = prefs.getBoolean(TAG + "." + site.name + ".enabled", site.enabled);
            site.order = prefs.getInt(TAG + "." + site.name + ".order", site.order);
            site.reliability = prefs.getInt(TAG + "." + site.name + ".reliability", site.reliability);

            mReliabilityOrder.set(site.reliability, site);
            mPreferredSearchOrder.set(site.order, site);
        }

        for (SearchSite site : mCoverSearchOrderDefaults) {
            site.enabled = prefs.getBoolean(TAG + "." + site.name + ".cover.enabled", site.enabled);
            site.order = prefs.getInt(TAG + "." + site.name + ".cover.order", site.order);

            mPreferredCoverSearchOrder.set(site.order, site);
        }
    }

    /** TaskManager for threads; may have other threads than the ones this object creates. */
    @NonNull
    private final TaskManager mTaskManager;
    /** List of threads created by *this* object. */
    private final ArrayList<ManagedTask> mRunningTasks = new ArrayList<>();
    private final SearchController mController = new SearchController() {
        public void requestAbort() {
            mTaskManager.cancelAllTasks();
        }

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
    /** Flag indicating searches will be non-concurrent title/author found via ASIN */
    private boolean mSearchingAsin = false;
    /** Flag indicating searches will be non-concurrent until an ISBN is found */
    private boolean mWaitingForIsbn = false;
    /** Flag indicating a task was cancelled. */
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
    private Hashtable<Integer, Bundle> mSearchResults = new Hashtable<>();

    /**
     * Constructor.
     *
     * @param taskManager TaskManager to use
     * @param taskHandler SearchHandler to send results
     */
    public SearchManager(@NonNull final TaskManager taskManager,
                         @NonNull final SearchListener taskHandler) {
        mTaskManager = taskManager;
        getMessageSwitch().addListener(getSenderId(), taskHandler, false);
    }

    @NonNull
    static ArrayList<SearchSite> getSiteSearchOrder() {
        return mPreferredSearchOrder;
    }

    static void setSearchOrder(@NonNull final ArrayList<SearchSite> newList) {
        mPreferredSearchOrder = newList;
        SharedPreferences.Editor e = BookCatalogueApp.getSharedPreferences().edit();
        for (SearchSite site : newList) {
            e.putInt(TAG + "." + site.name + ".reliability", site.reliability);
            e.putBoolean(TAG + "." + site.name + ".enabled", site.enabled);
            e.putInt(TAG + "." + site.name + ".order", site.order);
        }
        e.apply();
    }

    @NonNull
    public static ArrayList<SearchSite> getSiteCoverSearchOrder() {
        return mPreferredCoverSearchOrder;
    }

    static void setCoverSearchOrder(@NonNull final ArrayList<SearchSite> newList) {
        mPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor e = BookCatalogueApp.getSharedPreferences().edit();
        for (SearchSite site : newList) {
            e.putBoolean(TAG + "." + site.name + ".cover.enabled", site.enabled);
            e.putInt(TAG + "." + site.name + ".cover.order", site.order);
        }
        e.apply();
    }


    public static TaskSwitch getMessageSwitch() {
        return mMessageSwitch;
    }

    /**
     * When a task has ended, see if we are finished (no more tasks running).
     * If so, finish.
     */
    @Override
    public void onTaskEnded(@NonNull final TaskManager manager, @NonNull final ManagedTask task) {
        int size;
        if (BuildConfig.DEBUG) {
            System.out.println(task.getClass().getSimpleName() + "(" + +task.getId() + ") onTaskEnded starting");
        }

        // Handle the result, and optionally queue another task
        if (task instanceof SearchThread) {
            handleSearchTaskFinished((SearchThread) task);
        }

        // Remove the finished task, and terminate if no more.
        synchronized (mRunningTasks) {
            mRunningTasks.remove(task);
            size = mRunningTasks.size();
            if (BuildConfig.DEBUG) {
                for (ManagedTask t : mRunningTasks) {
                    System.out.println(t.getClass().getSimpleName() + "(" + +t.getId() + ") still running");
                }
            }
        }
        if (size == 0) {
            // Stop listening FIRST...otherwise, if sendResults() calls a listener that starts
            // a new task, we will stop listening for the new task.
            TaskManager.getMessageSwitch().removeListener(mTaskManager.getSenderId(), this);
            if (BuildConfig.DEBUG) {
                System.out.println("SearchManager not listening(1)");
            }
            // Notify the listeners.
            sendResults();
        }
        if (BuildConfig.DEBUG) {
            System.out.println(task.getClass().getSimpleName() + "(" + +task.getId() + ") onTaskEnded Exiting");
        }
    }

    /**
     * Other taskManager messages...we ignore them
     */
    @Override
    public void onProgress(final int count, final int max, @NonNull final String message) {
    }

    @Override
    public void onToast(@NonNull final String message) {
    }

    @Override
    public void onFinished() {
    }

    /**
     * Utility routine to start a task
     *
     * @param thread Task to start
     */
    private void startOne(@NonNull final SearchThread thread) {
        synchronized (mRunningTasks) {
            mRunningTasks.add(thread);
            mTaskManager.addTask(thread);
            if (BuildConfig.DEBUG) {
                System.out.println(thread.getClass().getSimpleName() + "(" + +thread.getId() + ") STARTING");
            }
        }
        thread.start();
    }

    /**
     * Start an Amazon search
     */
    private boolean startAmazon() {
        if (!mCancelledFlg) {
            startOne(new SearchAmazonThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Start a Google search
     */
    private boolean startGoogle() {
        if (!mCancelledFlg) {
            startOne(new SearchGoogleBooksThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Start an Amazon search
     */
    private boolean startLibraryThing() {
        if (!mCancelledFlg && mHasValidIsbn) {
            startOne(new SearchLibraryThingThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Start an Goodreads search
     */
    private boolean startGoodreads() {
        if (!mCancelledFlg) {
            startOne(new SearchGoodreadsThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Start an ISFDB search
     */
    private boolean startISFDB() {
        if (!mCancelledFlg) {
            startOne(new SearchISFDBThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Start a search
     *
     * ONE of the three parameters must be !.isEmpty
     *
     * @param author Author to search for
     * @param title  Title to search for
     * @param isbn   ISBN to search for
     */
    public void search(@NonNull final String author,
                       @NonNull final String title,
                       @NonNull final String isbn,
                       final boolean fetchThumbnail,
                       final int searchFlags) {
        if ((searchFlags & SEARCH_ALL) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }

        if (mRunningTasks.size() > 0) {
            throw new RuntimeException("Attempting to start new search while previous search running");
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
        if (BuildConfig.DEBUG) {
            System.out.println("SearchManager.doSearch listener started");
        }

        // We really want to ensure we get the same book from each, so if isbn is not present, do
        // these in series.

        boolean tasksStarted = false;
        mSearchingAsin = false;
        try {
            if (mIsbn != null && !mIsbn.isEmpty()) {
                if (mHasValidIsbn) {
                    // We have a valid ISBN, just do the search
                    mWaitingForIsbn = false;
                    tasksStarted = startSearches(mSearchFlags);
                } else {
                    // Assume it's an ASIN, and just search Amazon
                    mSearchingAsin = true;
                    mWaitingForIsbn = false;
                    tasksStarted = startOneSearch(SEARCH_AMAZON);
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
                if (BuildConfig.DEBUG) {
                    System.out.println("SearchManager.doSearch listener stopped");
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
    private void appendData(@NonNull final String key, @NonNull final Bundle source, @NonNull final Bundle dest) {
        String res = dest.getString(key) + ArrayUtils.MULTI_STRING_SEPARATOR + source.getString(key);
        dest.putString(key, res);
    }

    /**
     * Copy data from passed Bundle to current accumulated data. Does some careful
     * processing of the data.
     *
     * @param searchId Source
     */
    private void accumulateData(int searchId) {
        // See if we got data from this source
        if (!mSearchResults.containsKey(searchId)) {
            return;
        }
        Bundle bookData = mSearchResults.get(searchId);

        // See if we REALLY got data from this source
        if (bookData == null) {
            return;
        }

        for (String key : bookData.keySet()) {
            // If its not there, copy it.
            if (!mBookData.containsKey(key) || mBookData.getString(key) == null || mBookData.getString(key).isEmpty()) {
                mBookData.putString(key, bookData.get(key).toString());
            } else {
                // Copy, append or update data as appropriate.
                if (UniqueId.BKEY_AUTHOR_DETAILS.equals(key)) {
                    appendData(key, bookData, mBookData);

                } else if (UniqueId.BKEY_SERIES_DETAILS.equals(key)) {
                    appendData(key, bookData, mBookData);

                } else if (UniqueId.KEY_BOOK_DATE_PUBLISHED.equals(key)) {// Grab a different date if we can parse it.
                    String pd = bookData.getString(key);
                    if (pd != null) {
                        Date newDate = DateUtils.parseDate(pd);
                        if (newDate != null) {
                            String curr = mBookData.getString(key);
                            if (curr != null && DateUtils.parseDate(curr) == null) {
                                mBookData.putString(key, DateUtils.toSqlDateOnly(newDate));
                            }
                        }
                    }

                } else if (UniqueId.BKEY_THUMBNAIL_USCORE.equals(key)) {
                    appendData(key, bookData, mBookData);

                }
            }
        }
    }

    /**
     * Combine all the data and create a book or display an error.
     */
    private void sendResults() {
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final List<Integer> results = new ArrayList<>();

        if (mHasValidIsbn) {
            // If ISBN was passed, ignore entries with the wrong ISBN, and put entries with no ISBN at the end
            final List<Integer> uncertain = new ArrayList<>();
            for (SearchSite site : mReliabilityOrder) {
                if (mSearchResults.containsKey(site.id)) {
                    Bundle bookData = mSearchResults.get(site.id);
                    if (bookData.containsKey(UniqueId.KEY_ISBN)) {
                        if (IsbnUtils.matches(mIsbn, bookData.getString(UniqueId.KEY_ISBN))) {
                            results.add(site.id);
                        }
                    } else {
                        uncertain.add(site.id);
                    }
                }
            }
            results.addAll(uncertain);
            // Add the passed ISBN first; avoid overwriting
            mBookData.putString(UniqueId.KEY_ISBN, mIsbn);
        } else {
            // If ISBN was not passed, then just use the default order
            for (SearchSite site : mReliabilityOrder) {
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
        String authors = null;
        try {
            authors = mBookData.getString(UniqueId.BKEY_AUTHOR_DETAILS);
        } catch (Exception ignored) {
        }

        if (authors == null || authors.isEmpty()) {
            authors = mAuthor;
        }

        if (authors != null && !authors.isEmpty()) {
            ArrayList<Author> aa = ArrayUtils.getAuthorUtils().decodeList(authors, false);
            mBookData.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, aa);
        }

        // Try to use/construct title
        String title = null;
        try {
            title = mBookData.getString(UniqueId.KEY_TITLE);
        } catch (Exception ignored) {
        }

        if (title == null || title.isEmpty()) {
            title = mTitle;
        }

        if (title != null && !title.isEmpty()) {
            mBookData.putString(UniqueId.KEY_TITLE, title);
        }

        // Try to use/construct isbn
        String isbn = null;
        try {
            isbn = mBookData.getString(UniqueId.KEY_ISBN);
        } catch (Exception ignored) {
        }

        if (isbn == null || isbn.isEmpty()) {
            isbn = mIsbn;
        }

        if (isbn != null && !isbn.isEmpty()) {
            mBookData.putString(UniqueId.KEY_ISBN, isbn);
        }

        // Try to use/construct series
        String series = null;
        try {
            series = mBookData.getString(UniqueId.BKEY_SERIES_DETAILS);
        } catch (Exception ignored) {
        }

        if (series != null && !series.isEmpty()) {
            try {
                ArrayList<Series> sa = ArrayUtils.getSeriesUtils().decodeList(series, false);
                mBookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, sa);
            } catch (Exception e) {
                Logger.logError(e);
            }
        } else {
            //add series to stop crashing
            mBookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, new ArrayList<Series>());
        }

        // If book is not found or missing required data, warn the user
        if (authors == null || authors.isEmpty() || title == null || title.isEmpty()) {
            mTaskManager.doToast(BookCatalogueApp.getResourceString(R.string.book_not_found));
        }
        // Pass the data back
        mMessageSwitch.send(mMessageSenderId, new MessageSwitch.Message<SearchListener>() {
                    @Override
                    public boolean deliver(@NonNull final SearchListener listener) {
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
        for (SearchSite source : mPreferredSearchOrder) {
            // If this search includes the source, check it
            if (source.enabled && ((mSearchFlags & source.id) != 0)) {
                // If the source has not been searched, search it
                if (!mSearchResults.containsKey(source.id)) {
                    return startOneSearch(source.id);
                }
            }
        }
        return false;
    }

    /**
     * Start all searches listed in passed parameter that have not been run yet.
     */
    private boolean startSearches(final int sources) {
        boolean started = false;
        // Scan searches in priority order
        for (SearchSite source : mPreferredSearchOrder) {
            // If requested search contains this source...
            if (source.enabled && ((sources & source.id) != 0)) {
                // If we have not run this search...
                if (!mSearchResults.containsKey(source.id)) {
                    // Run it now
                    if (startOneSearch(source.id)) {
                        started = true;
                    }
                }
            }
        }
        return started;
    }

    /**
     * Start specific search listed in passed parameter.
     */
    private boolean startOneSearch(final int source) {
        switch (source) {
            case SEARCH_GOOGLE:
                return startGoogle();
            case SEARCH_AMAZON:
                return startAmazon();
            case SEARCH_LIBRARY_THING:
                return startLibraryThing();
            case SEARCH_GOODREADS:
                return startGoodreads();
            case SEARCH_ISFDB:
                return startISFDB();
            default:
                throw new RuntimeException("Unexpected search source: " + source);
        }
    }

    /**
     * Handle task search results; start another task if necessary.
     */
    private void handleSearchTaskFinished(@NonNull final SearchThread searchThread) {
        mCancelledFlg = searchThread.isCancelled();
        final Bundle bookData = searchThread.getBookData();
        mSearchResults.put(searchThread.getSearchId(), bookData);
        if (mCancelledFlg) {
            mWaitingForIsbn = false;
        } else {
            if (mSearchingAsin) {
                // If we searched AMAZON for an Asin, then see what we found
                mSearchingAsin = false;
                // Clear the 'isbn'
                mIsbn = "";
                if (Utils.isNonBlankString(bookData, UniqueId.KEY_ISBN)) {
                    // We got an ISBN, so pretend we were searching for an ISBN
                    mWaitingForIsbn = true;
                } else {
                    // See if we got author/title
                    mAuthor = bookData.getString(UniqueId.KEY_AUTHOR_NAME);
                    mTitle = bookData.getString(UniqueId.KEY_TITLE);
                    if (mAuthor != null && !mAuthor.isEmpty() && mTitle != null && !mTitle.isEmpty()) {
                        // We got them, so pretend we are searching by author/title now, and waiting for an ASIN...
                        mWaitingForIsbn = true;
                    }
                }
            }
            if (mWaitingForIsbn) {
                if (Utils.isNonBlankString(bookData, UniqueId.KEY_ISBN)) {
                    mWaitingForIsbn = false;
                    // Start the other two...even if they have run before
                    mIsbn = bookData.getString(UniqueId.KEY_ISBN);
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
        boolean onSearchFinished(Bundle bookData, boolean cancelled);
    }

    public interface SearchController {
        void requestAbort();

        SearchManager getSearchManager();
    }

    public static class SearchSite {
        public final int id;
        final String name;
        public boolean enabled;
        int order;
        int reliability;

        @SuppressWarnings("SameParameterValue")
        SearchSite(final int bit, final String name,
                   final int order, final boolean enabled) {
            this.id = bit;
            this.name = name;
            this.order = order;
            this.enabled = enabled;
            this.reliability = order;
        }

        @SuppressWarnings("SameParameterValue")
        SearchSite(final int id, final String name,
                   final int order, final boolean enabled,
                   final int reliability) {
            this.id = id;
            this.name = name;
            this.order = order;
            this.enabled = enabled;
            this.reliability = reliability;
        }
//        @Override
//        public String toString() {
//            return "SearchSite{" +
//                    "id=" + id +
//                    ", name='" + name + '\'' +
//                    ", enabled=" + enabled +
//                    ", order=" + order +
//                    ", reliability=" + reliability +
//                    '}';
//        }
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
