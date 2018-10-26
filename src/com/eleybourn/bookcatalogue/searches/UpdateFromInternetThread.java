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
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

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
public class UpdateFromInternetThread extends ManagedTask {
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
    @NonNull
    private String mCurrentBookUuid = "";

    /** The (subset) of fields relevant to the current book */
    private UpdateFromInternetActivity.FieldUsages mCurrentBookFieldUsages;

    /**
     * Our class local {@link SearchManager.SearchListener}.
     * This must be a class global. Don't make this local to the constructor.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SearchManager.SearchListener mSearchListener = new SearchManager.SearchListener() {
        @Override
        public boolean onSearchFinished(@NonNull final Bundle bookData, final boolean cancelled) {
            return UpdateFromInternetThread.this.onSearchFinished(bookData, cancelled);
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
    public UpdateFromInternetThread(@NonNull final TaskManager manager,
                                    @NonNull final UpdateFromInternetActivity.FieldUsages requestedFields,
                                    @NonNull final TaskListener listener) {
        super("UpdateFromInternetThread", manager);

        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext())
                .open();

        mRequestedFields = requestedFields;

        mSearchManager = new SearchManager(mTaskManager, mSearchListener);
        mTaskManager.doProgress(BookCatalogueApp.getResourceString(R.string.starting_search));
        getMessageSwitch().addListener(getSenderId(), listener, false);
    }

    private static <T extends Serializable> void combineArrays(@NonNull final String key,
                                                               @NonNull final Bundle origData,
                                                               @NonNull final Bundle newData) {
        // Each of the lists to combine
        ArrayList<T> origList = null;
        ArrayList<T> newList = null;
        // Get the list from the original, if present.
        if (origData.containsKey(key)) {
            origList = ArrayUtils.getListFromBundle(origData, key);
        }
        // Otherwise an empty list
        if (origList == null) {
            origList = new ArrayList<>();
        }

        // Get the list from the new data
        if (newData.containsKey(key)) {
            newList = ArrayUtils.getListFromBundle(newData, key);
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
        mBookWhereClause = DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_ID) + "=" + bookId;
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
                DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_ID))) {

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
                mCurrentBookId = Utils.getLongFromBundle(mOriginalBookData, UniqueId.KEY_ID);
                // Get the book UUID
                //noinspection ConstantConditions
                mCurrentBookUuid = mOriginalBookData.getString(UniqueId.KEY_BOOK_UUID);
                // Get the extra data about the book
                mOriginalBookData.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, mDb.getBookAuthorList(mCurrentBookId));
                mOriginalBookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, mDb.getBookSeriesList(mCurrentBookId));
                mOriginalBookData.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, mDb.getAnthologyTitleListByBook(mCurrentBookId));

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
                boolean tmpThumbWanted = mCurrentBookFieldUsages.containsKey(UniqueId.KEY_BOOK_THUMBNAIL);
                if (tmpThumbWanted) {
                    // delete any temporary thumbnails //
                    StorageUtils.deleteFile(StorageUtils.getTempCoverFile());
                }

                // Use this to flag if we actually need a search.
                boolean wantSearch = false;
                // Update the progress appropriately
                if (mCurrentBookFieldUsages.size() == 0 || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    mTaskManager.doProgress(String.format(getString(R.string.skip_title), title));
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
                    // TODO: Allow user-selection of search sources specific for this similar to 'normal' search order preferences.
                    // Might be nice to select on the fly here ?
                    mSearchManager.search(author, title, isbn, tmpThumbWanted, SearchManager.SEARCH_ALL);
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
            // Make the final message (brief message, not a Progress message)
            mFinalMessage = String.format(getString(R.string.num_books_searched), "" + progressCounter);
            if (isCancelled()) {
                mFinalMessage = String.format(BookCatalogueApp.getResourceString(R.string.cancelled_info), mFinalMessage);
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
                    case ADD_EXTRA:
                    case OVERWRITE:
                        // Add and Overwrite mean we always get the data
                        mCurrentBookFieldUsages.put(usage);
                        break;
                    case COPY_IF_BLANK:
                        // Handle special cases first, 'default:' for the rest
                        switch (usage.key) {
                            // - If it's a thumbnail, then see if it's missing or empty.
                            case UniqueId.KEY_BOOK_THUMBNAIL:
                                File file = StorageUtils.getCoverFile(mCurrentBookUuid);
                                if (!file.exists() || file.length() == 0) {
                                    mCurrentBookFieldUsages.put(usage);
                                }
                                break;

                            case UniqueId.BKEY_AUTHOR_ARRAY:
                                // We should never have a book with no authors, but lets be paranoid
                                if (mOriginalBookData.containsKey(usage.key)) {
                                    List<Author> list = ArrayUtils.getAuthorsFromBundle(mOriginalBookData);
                                    if (list == null || list.size() == 0) {
                                        mCurrentBookFieldUsages.put(usage);
                                    }
                                }
                                break;

                            case UniqueId.BKEY_SERIES_ARRAY:
                                if (mOriginalBookData.containsKey(usage.key)) {
                                    List<Series> list = ArrayUtils.getSeriesFromBundle(mOriginalBookData);
                                    if (list == null || list.size() == 0) {
                                        mCurrentBookFieldUsages.put(usage);
                                    }
                                }
                                break;

                            case UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY:
                                if (mOriginalBookData.containsKey(usage.key)) {
                                    List<AnthologyTitle> list = ArrayUtils.getAnthologyTitleFromBundle(mOriginalBookData);
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
            mTaskManager.showBriefMessage(mFinalMessage);
        } finally {
            cleanup();
        }
    }

    /**
     * Called in the main thread for this object when a search has completed.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean onSearchFinished(@NonNull final Bundle newBookData, final boolean cancelled) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, " onSearchFinished");
        }

        // Set cancelled flag if the task was cancelled
        if (cancelled) {
            cancelTask();
        } else if (newBookData.size() == 0) {
            mTaskManager.showBriefMessage(BookCatalogueApp.getResourceString(R.string.unable_to_find_book));
        }

        // Save the local data from the context so we can start a new search
        if (!isCancelled() && newBookData.size() > 0) {
            processSearchResults(mCurrentBookId, mCurrentBookUuid, mCurrentBookFieldUsages, newBookData, mOriginalBookData);
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
                                      @NonNull final String bookUuid,
                                      @NonNull final UpdateFromInternetActivity.FieldUsages requestedFields,
                                      @NonNull final Bundle newBookData,
                                      @NonNull final Bundle originalBookData) {
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
                if (usage.key.equals(UniqueId.KEY_BOOK_THUMBNAIL)) {
                    File downloadedFile = StorageUtils.getTempCoverFile();
                    boolean copyThumb = false;
                    if (usage.usage == UpdateFromInternetActivity.FieldUsage.Usage.COPY_IF_BLANK) {
                        File file = StorageUtils.getCoverFile(bookUuid);
                        copyThumb = (!file.exists() || file.length() == 0);
                    } else if (usage.usage == UpdateFromInternetActivity.FieldUsage.Usage.OVERWRITE) {
                        copyThumb = true;
                    }
                    if (copyThumb) {
                        File file = StorageUtils.getCoverFile(bookUuid);
                        StorageUtils.renameFile(downloadedFile, file);
                    } else {
                        StorageUtils.deleteFile(downloadedFile);
                    }
                } else {
                    switch (usage.usage) {
                        case OVERWRITE:
                            // Nothing to do; just use new data
                            break;
                        case COPY_IF_BLANK:
                            // Handle special cases
                            switch (usage.key) {
                                case UniqueId.BKEY_AUTHOR_ARRAY:
                                    if (originalBookData.containsKey(usage.key)) {
                                        ArrayList<Author> list = ArrayUtils.getAuthorsFromBundle(originalBookData);
                                        if (list != null && list.size() > 0) {
                                            newBookData.remove(usage.key);
                                        }
                                    }
                                    break;
                                case UniqueId.BKEY_SERIES_ARRAY:
                                    if (originalBookData.containsKey(usage.key)) {
                                        ArrayList<Series> list = ArrayUtils.getSeriesFromBundle(originalBookData);
                                        if (list != null && list.size() > 0) {
                                            newBookData.remove(usage.key);
                                        }
                                    }
                                    break;
                                case UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY:
                                    if (originalBookData.containsKey(usage.key)) {
                                        ArrayList<AnthologyTitle> list = ArrayUtils.getAnthologyTitleFromBundle(originalBookData);
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

                        case ADD_EXTRA:
                            // Handle arrays (note: before you're clever, and collapse this to one... Android Studio hides the type in the <~> notation!
                            switch (usage.key) {
                                case UniqueId.BKEY_AUTHOR_ARRAY:
                                    UpdateFromInternetThread.<Author>combineArrays(usage.key, originalBookData, newBookData);
                                    break;
                                case UniqueId.BKEY_SERIES_ARRAY:
                                    UpdateFromInternetThread.<Series>combineArrays(usage.key, originalBookData, newBookData);
                                    break;
                                case UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY:
                                    UpdateFromInternetThread.<AnthologyTitle>combineArrays(usage.key, originalBookData, newBookData);
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
            mDb.updateBook(bookId, new Book(bookId, newBookData), 0);
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
