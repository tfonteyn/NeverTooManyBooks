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

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.UpdateFromInternetActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class to update all thumbnails and other data in a background thread.
 *
 * NEWKIND must stay in sync with {@link com.eleybourn.bookcatalogue.UpdateFromInternetActivity}
 *
 * @author Philip Warner
 */
public class UpdateFromInternetTask extends ManagedTask {
    /** The fields that the user requested to update */
    @NonNull
    private final UpdateFromInternetActivity.FieldUsages mRequestedFields;

    //** Lock help by pop and by push when an item was added to an empty stack. */
    private final ReentrantLock mSearchLock = new ReentrantLock();
    /** Signal for available items */
    private final Condition mSearchDone = mSearchLock.newCondition();
    /** Active search manager */
    @NonNull
    private final SearchManager mSearchManager;
    /** Sites to search */
    private final int mSearchSites; // = SearchManager.SEARCH_ALL;

    /** message to display when all is said and done */
    private String mFinalMessage;

    /** DB connection */
    private CatalogueDBAdapter mDb;

    // Data related to current row being processed
    /** Original row data */
    private Bundle mOriginalBookData = null;
    /** current book ID */
    private long mCurrentBookId = 0;
    /** current book UUID */
    private String mCurrentUuid = null;

    /** The (subset) of fields relevant to the current book */
    private UpdateFromInternetActivity.FieldUsages mCurrentBookFieldUsages;

    /**
     * Our class local {@link SearchManager.SearchListener}.
     * This must be a class global. Don't make this local to the constructor.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SearchManager.SearchListener mSearchListener = new SearchManager.SearchListener() {
        @Override
        public boolean onSearchFinished(final @NonNull Bundle bookData, final boolean cancelled) {
            return UpdateFromInternetTask.this.onSearchFinished(bookData, cancelled);
        }
    };

    /**
     * where clause to use in cursor, none by default, but
     * @see #setBookId(long)
     */
    @NonNull
    private String mBookWhereClause = "";

    /**
     * Constructor.
     *
     * @param manager         Object to manage background tasks
     * @param requestedFields fields to update
     */
    public UpdateFromInternetTask(final @NonNull TaskManager manager,
                                  final @NonNull UpdateFromInternetActivity.FieldUsages requestedFields,
                                  final int searchSites,
                                  final @NonNull TaskListener listener) {
        super("UpdateFromInternetTask", manager);

        mDb = new CatalogueDBAdapter(manager.getContext());

        mRequestedFields = requestedFields;

        mSearchSites = searchSites;
        mSearchManager = new SearchManager(mTaskManager, mSearchListener);
        mTaskManager.doProgress(BookCatalogueApp.getResourceString(R.string.progress_msg_starting_search));
        getMessageSwitch().addListener(getSenderId(), listener, false);
    }

    private static <T extends Serializable> void combineArrays(final @NonNull String key,
                                                               final @NonNull Bundle origData,
                                                               final @NonNull Bundle newData) {
        // Each of the lists to combine
        ArrayList<T> origList = null;
        ArrayList<T> newList = null;
        // Get the list from the original, if present.
        if (origData.containsKey(key)) {
            origList = BundleUtils.getListFromBundle(key, origData);
        }
        // Otherwise an empty list
        if (origList == null) {
            origList = new ArrayList<>();
        }

        // Get the list from the new data
        if (newData.containsKey(key)) {
            newList = BundleUtils.getListFromBundle(key, newData);
        }
        if (newList == null) {
            newList = new ArrayList<>();
        }

        //TEST weeding out duplicates
        for (T item : newList) {
            if (!origList.contains(item)) {
                origList.add(item);
            }
        }
        // Save combined version to the new data
        newData.putSerializable(key, origList);
    }

    /**
     * By default, the update is for all books. By calling this before starting the task, you can limit it to one book.
     *
     * @param bookId for the book to update
     */
    public void setBookId(final long bookId) {
        //TODO: not really happy exposing the DOM's here, but it will do for now. Ideally the sql behind this becomes static and uses binds
        mBookWhereClause = DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_PK_ID) + "=" + bookId;
    }

    @Override
    public void runTask() throws InterruptedException {
        int progressCounter = 0;
        // Test write to the SDCard; abort if not writable
        if (StorageUtils.isWriteProtected()) {
            mFinalMessage = getString(R.string.error_storage_cannot_write);
            return;
        }
        // the 'order by' makes sure we update the 'oldest' book to 'newest'
        // So if we get interrupted, we can pick up the thread (arf...) again later.
        try (Cursor books = mDb.fetchBooksWhere(mBookWhereClause, new String[]{},
                DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_PK_ID))) {

            mTaskManager.setMax(this, books.getCount());
            while (books.moveToNext() && !isCancelled()) {
                progressCounter++;

                // Copy the fields from the cursor and build a complete set of data for this book.
                // This only needs to include data that we can fetch (so, for example, bookshelves are ignored).
                mOriginalBookData = new Bundle();
                for (int i = 0; i < books.getColumnCount(); i++) {
                    mOriginalBookData.putString(books.getColumnName(i), books.getString(i));
                }

                // Get the book ID
                mCurrentBookId = mOriginalBookData.getLong(UniqueId.KEY_ID);
                // Get the book UUID
                mCurrentUuid = mOriginalBookData.getString(UniqueId.KEY_BOOK_UUID);
                // Get the extra data about the book
                mOriginalBookData.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, mDb.getBookAuthorList(mCurrentBookId));
                mOriginalBookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, mDb.getBookSeriesList(mCurrentBookId));
                mOriginalBookData.putSerializable(UniqueId.BKEY_TOC_TITLES_ARRAY, mDb.getTOCEntriesByBook(mCurrentBookId));

                // Grab the searchable fields. Ideally we will have an ISBN but we may not.

                // Make sure ISBN is not NULL (legacy data, and possibly set to null when adding new book)
                String isbn = mOriginalBookData.getString(UniqueId.KEY_BOOK_ISBN,"");

                String author = mOriginalBookData.getString(UniqueId.KEY_AUTHOR_FORMATTED, "");
                String title = mOriginalBookData.getString(UniqueId.KEY_TITLE, "");

                // Reset the fields we want for THIS book
                mCurrentBookFieldUsages = new UpdateFromInternetActivity.FieldUsages();

                // See if there is a reason to fetch ANY data by checking which fields this book needs.
                checkIfWeShouldSearch();

                // Cache the value to indicate we need thumbnails (or not).
                boolean tmpThumbWanted = mCurrentBookFieldUsages.containsKey(UniqueId.BKEY_HAVE_THUMBNAIL);
                if (tmpThumbWanted) {
                    // delete any temporary thumbnails //
                    StorageUtils.deleteFile(StorageUtils.getTempCoverFile());
                }

                // Use this to flag if we actually need a search.
                boolean wantSearch = false;
                // Update the progress appropriately
                if (mCurrentBookFieldUsages.size() == 0 || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    mTaskManager.doProgress(String.format(getString(R.string.progress_msg_skip_title), title));
                } else {
                    wantSearch = true;
                    if (!title.isEmpty()) {
                        mTaskManager.doProgress(title);
                    } else {
                        mTaskManager.doProgress(isbn);
                    }
                }

                mTaskManager.doProgress(this, null, progressCounter);

                // Start searching if we need to, then wait...
                if (wantSearch) {
                    mSearchManager.search(mSearchSites, author, title, isbn, tmpThumbWanted);
                    // Wait for the search to complete. When the search has completed
                    // it uses class-level state data when processing the results.
                    // It will call "mSearchDone.signal()"  when it no longer needs any
                    // class level state data (eg. mOriginalBookData).
                    mSearchLock.lock();
                    try {
                        Logger.info(this, "mSearchDone await");
                        mSearchDone.await();
                        Logger.info(this, "mSearchDone done with await");
                    } finally {
                        mSearchLock.unlock();
                    }
                }
            }
        } finally {
            // Empty/close the progress.
            mTaskManager.doProgress(null);
            // Make the final message (user message, not a Progress message)
            mFinalMessage = String.format(getString(R.string.progress_end_num_books_searched), "" + progressCounter);
            if (isCancelled()) {
                mFinalMessage = String.format(BookCatalogueApp.getResourceString(R.string.progress_end_cancelled_info), mFinalMessage);
                Logger.info(this, " was cancelled");
            }
        }
    }

    /**
     * See if there is a reason to fetch ANY data by checking which fields this book needs.
     * This will be available in {@link #mCurrentBookFieldUsages}
     */
    private void checkIfWeShouldSearch() {

        for (UpdateFromInternetActivity.FieldUsage usage : mRequestedFields.values()) {
            // Not selected, we don't want it
            if (usage.isSelected()) {
                switch (usage.usage) {
                    case AddExtra:
                    case Overwrite:
                        // Add and Overwrite mean we always get the data
                        mCurrentBookFieldUsages.put(usage);
                        break;
                    case CopyIfBlank:
                        // Handle special cases first, 'default:' for the rest
                        switch (usage.key) {
                            // - If it's a thumbnail, then see if it's missing or empty.
                            case UniqueId.BKEY_HAVE_THUMBNAIL:
                                File file = StorageUtils.getCoverFile(mCurrentUuid);
                                if (!file.exists() || file.length() == 0) {
                                    mCurrentBookFieldUsages.put(usage);
                                }
                                break;

                            case UniqueId.BKEY_AUTHOR_ARRAY:
                                // We should never have a book without authors, but lets be paranoid
                                if (mOriginalBookData.containsKey(usage.key)) {
                                    ArrayList<Author> list = BundleUtils.getListFromBundle(UniqueId.BKEY_AUTHOR_ARRAY, mOriginalBookData);
                                    if (list == null || list.size() == 0) {
                                        mCurrentBookFieldUsages.put(usage);
                                    }
                                }
                                break;

                            case UniqueId.BKEY_SERIES_ARRAY:
                                if (mOriginalBookData.containsKey(usage.key)) {
                                    ArrayList<Series> list = BundleUtils.getListFromBundle(UniqueId.BKEY_SERIES_ARRAY, mOriginalBookData);
                                    if (list == null || list.size() == 0) {
                                        mCurrentBookFieldUsages.put(usage);
                                    }
                                }
                                break;

                            case UniqueId.BKEY_TOC_TITLES_ARRAY:
                                if (mOriginalBookData.containsKey(usage.key)) {
                                    ArrayList<TOCEntry> list = BundleUtils.getListFromBundle(UniqueId.BKEY_TOC_TITLES_ARRAY, mOriginalBookData);
                                    if (list == null || list.size() == 0) {
                                        mCurrentBookFieldUsages.put(usage);
                                    }
                                }
                                break;

                            default:
                                // If the original was blank, add to list
                                String value = mOriginalBookData.getString(usage.key);
                                if (value == null || value.isEmpty()) {
                                    mCurrentBookFieldUsages.put(usage);
                                }
                                break;
                        }
                        break;
                }
            }
        }
    }

    @Override
    public void onTaskFinish() {
        try {
            mTaskManager.showUserMessage(mFinalMessage);
        } finally {
            cleanup();
        }
    }

    /**
     * Called in the main thread for this object when a search has completed.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean onSearchFinished(final @NonNull Bundle newBookData, final boolean cancelled) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, " onSearchFinished");
        }

        // Set cancelled flag if the task was cancelled
        if (cancelled) {
            cancelTask();
        } else if (newBookData.size() == 0) {
            mTaskManager.showUserMessage(BookCatalogueApp.getResourceString(R.string.warning_unable_to_find_book));
        }

        // Save the local data from the context so we can start a new search
        if (!isCancelled() && newBookData.size() > 0) {
            processSearchResults(mCurrentBookId, mCurrentUuid, mCurrentBookFieldUsages, newBookData, mOriginalBookData);
        }

        // Done! This need to go after processSearchResults() because we will signal(free) the
        // main thread which may disconnect database connection if on last book.
//        if (BuildConfig.DEBUG) {
//            Logger.info(this, "onSearchFinished, Let another search begin, signal mSearchDone");
//        }
        // The search is complete AND the class-level data has
        // been cached by the processing thread, so that a new search can begin.
        // Let another search begin
        mSearchLock.lock();
        try {
            mSearchDone.signal();
        } finally {
            mSearchLock.unlock();
        }

        return true;
    }

    /**
     * Passed the old & new data, construct the update data and perform the update.
     *
     * @param bookId           Book ID
     * @param newBookData      Data gathered from internet
     * @param originalBookData Original data
     */
    private void processSearchResults(final long bookId,
                                      final @NonNull String uuid,
                                      final @NonNull UpdateFromInternetActivity.FieldUsages requestedFields,
                                      final @NonNull Bundle newBookData,
                                      final @NonNull Bundle originalBookData) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "processSearchResults bookId=" + bookId);
        }
        // First, filter the data to remove keys we don't care about
        List<String> toRemove = new ArrayList<>();
        for (String key : newBookData.keySet()) {
            if (!requestedFields.containsKey(key) || !requestedFields.get(key).isSelected()) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            newBookData.remove(key);
        }

        // For each field, process it according the the usage.
        for (UpdateFromInternetActivity.FieldUsage usage : requestedFields.values()) {
            if (newBookData.containsKey(usage.key)) {
                // Handle thumbnail specially
                if (usage.key.equals(UniqueId.BKEY_HAVE_THUMBNAIL)) {
                    File downloadedFile = StorageUtils.getTempCoverFile();
                    boolean copyThumb = false;
                    if (usage.usage == UpdateFromInternetActivity.FieldUsage.Usage.CopyIfBlank) {
                        File file = StorageUtils.getCoverFile(uuid);
                        copyThumb = (!file.exists() || file.length() == 0);
                    } else if (usage.usage == UpdateFromInternetActivity.FieldUsage.Usage.Overwrite) {
                        copyThumb = true;
                    }
                    if (copyThumb) {
                        File file = StorageUtils.getCoverFile(uuid);
                        StorageUtils.renameFile(downloadedFile, file);
                    } else {
                        StorageUtils.deleteFile(downloadedFile);
                    }
                } else {
                    switch (usage.usage) {
                        case Overwrite:
                            // Nothing to do; just use new data
                            break;
                        case CopyIfBlank:
                            // Handle special cases
                            switch (usage.key) {
                                case UniqueId.BKEY_AUTHOR_ARRAY:
                                    if (originalBookData.containsKey(usage.key)) {
                                        ArrayList<Author> list = BundleUtils.getListFromBundle(UniqueId.BKEY_AUTHOR_ARRAY, originalBookData);
                                        if (list != null && list.size() > 0) {
                                            newBookData.remove(usage.key);
                                        }
                                    }
                                    break;
                                case UniqueId.BKEY_SERIES_ARRAY:
                                    if (originalBookData.containsKey(usage.key)) {
                                        ArrayList<Series> list = BundleUtils.getListFromBundle(UniqueId.BKEY_SERIES_ARRAY, originalBookData);
                                        if (list != null && list.size() > 0) {
                                            newBookData.remove(usage.key);
                                        }
                                    }
                                    break;
                                case UniqueId.BKEY_TOC_TITLES_ARRAY:
                                    if (originalBookData.containsKey(usage.key)) {
                                        ArrayList<TOCEntry> list = BundleUtils.getListFromBundle(UniqueId.BKEY_TOC_TITLES_ARRAY, originalBookData);
                                        if (list != null && list.size() > 0) {
                                            newBookData.remove(usage.key);
                                        }
                                    }
                                    break;
                                default:
                                    // If the original was non-blank, erase from list
                                    String value = originalBookData.getString(usage.key);
                                    if (value != null && !value.isEmpty()) {
                                        newBookData.remove(usage.key);
                                    }
                                    break;
                            }
                            break;

                        case AddExtra:
                            // Handle arrays (note: before you're clever, and collapse this to one... Android Studio hides the type in the <~> notation!
                            switch (usage.key) {
                                case UniqueId.BKEY_AUTHOR_ARRAY:
                                    UpdateFromInternetTask.<Author>combineArrays(usage.key, originalBookData, newBookData);
                                    break;
                                case UniqueId.BKEY_SERIES_ARRAY:
                                    UpdateFromInternetTask.<Series>combineArrays(usage.key, originalBookData, newBookData);
                                    break;
                                case UniqueId.BKEY_TOC_TITLES_ARRAY:
                                    UpdateFromInternetTask.<TOCEntry>combineArrays(usage.key, originalBookData, newBookData);
                                    break;
                                default:
                                    // No idea how to handle this for non-arrays
                                    throw new RTE.IllegalTypeException("Illegal usage '" + usage.usage + "' specified for field '" + usage.key + "'");
                            }
                            break;
                    }
                }
            }
        }

        // Update
        if (!newBookData.isEmpty()) {
            mDb.updateBook(bookId, Book.getBook(mDb, bookId, newBookData), 0);
        }

    }

    /**
     * Cleanup any DB connection etc after main task has run.
     */
    private void cleanup() {
        if (mDb != null) {
            mDb.close();
        }
        mDb = null;
    }

    @Override
    @CallSuper
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

}
