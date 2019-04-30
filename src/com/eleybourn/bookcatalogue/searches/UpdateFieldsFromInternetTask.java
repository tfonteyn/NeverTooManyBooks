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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.UpdateFieldsFromInternetActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.FieldUsage;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * ManagedTask to update requested fields by doing a search.
 * <p>
 * NEWKIND must stay in sync with {@link UpdateFieldsFromInternetActivity}
 *
 * @author Philip Warner
 */
public class UpdateFieldsFromInternetTask
        extends ManagedTask
        implements SearchCoordinator.SearchCoordinatorListener {

    /** The fields that the user requested to update. */
    @NonNull
    private final Map<String, FieldUsage> mFields;

    /** Lock help by pop and by push when an item was added to an empty stack. */
    private final ReentrantLock mSearchLock = new ReentrantLock();
    /** Signal for available items. */
    private final Condition mSearchDone = mSearchLock.newCondition();
    /** Sites to search. */
    private final int mSearchSites;
    /** Active search manager. */
    private final SearchCoordinator mSearchCoordinator;

    /** DB connection. */
    private DBA mDb;

    // Data related to current row being processed
    /** Original row data. */
    private Bundle mOriginalBookData;
    /** current book ID. */
    private long mCurrentBookId;
    /** current book UUID. */
    private String mCurrentUuid;

    /** The (subset) of fields relevant to the current book. */
    private Map<String, FieldUsage> mCurrentBookFieldUsages;

//    /**
//     * Our class local {@link SearchManager.SearchManagerListener}.
//     * This must be a class global. Don't make this local to the constructor.
//     */
//    @SuppressWarnings("FieldCanBeLocal")
//    private final SearchManager.SearchManagerListener mSearchListener =
//           new SearchManager.SearchManagerListener() {
//        @Override
//        public boolean onSearchFinished(@NonNull final Bundle bookData,
//                                        final boolean wasCancelled) {
//            return UpdateFieldsFromInternetTask.this.onSearchFinished(bookData, wasCancelled);
//        }
//    };

    /** WHERE clause to use in cursor, none by default, but see {@link #setBookId(long)}. */
    @NonNull
    private String mBookWhereClause = "";

    /**
     * Constructor.
     *
     * @param taskManager Object to manage background tasks
     * @param searchSites sites to search, see {@link Site#SEARCH_ALL}
     * @param fields      fields to update
     * @param listener    where to send our results to
     */
    public UpdateFieldsFromInternetTask(@NonNull final TaskManager taskManager,
                                        final int searchSites,
                                        @NonNull final Map<String, FieldUsage> fields,
                                        @NonNull final ManagedTaskListener listener) {
        super(taskManager, "UpdateFieldsFromInternetTask");

        mDb = new DBA();
        mFields = fields;
        mSearchSites = searchSites;

        mSearchCoordinator = new SearchCoordinator(mTaskManager, this);
        mTaskManager.sendHeaderUpdate(R.string.progress_msg_starting_search);
        getMessageSwitch().addListener(getSenderId(), listener, false);
    }

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param key        for the ArrayList to combine
     * @param sourceData Bundle with the 'original' ArrayList (if any)
     * @param destData   Destination Bundle where the combined list is updated
     * @param <T>        type of the ArrayList elements
     */
    private static <T extends Parcelable> void merge(@NonNull final String key,
                                                     @NonNull final Bundle sourceData,
                                                     @NonNull final Bundle destData) {
        // Each of the lists to combine
        ArrayList<T> sourceList = null;
        ArrayList<T> destList = null;
        // Get the list from the original, if present.
        if (sourceData.containsKey(key)) {
            sourceList = sourceData.getParcelableArrayList(key);
        }
        // Otherwise an empty list
        if (sourceList == null) {
            sourceList = new ArrayList<>();
        }

        // Get the list from the new data
        if (destData.containsKey(key)) {
            destList = destData.getParcelableArrayList(key);
        }
        if (destList == null) {
            destList = new ArrayList<>();
        }

        for (T item : destList) {
            if (!sourceList.contains(item)) {
                sourceList.add(item);
            }
        }
        // Save combined version to the new data
        destData.putParcelableArrayList(key, sourceList);
    }

    /**
     * By default, the update is for all books. By calling this before starting the task,
     * you can limit it to one book.
     * <p>
     * This call is mutually exclusive with {@link #setBookId(List)}.
     *
     * @param bookId for the book to update
     */
    public void setBookId(final long bookId) {
        //TODO: not really happy exposing the DOM's here, but it will do for now.
        // Ideally the sql behind this becomes static and uses binds
        mBookWhereClause = DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_PK_ID)
                + '=' + bookId;
    }

    /**
     * By default, the update is for all books. By calling this before starting the task,
     * you can limit it to a set of books.
     * <p>
     * This call is mutually exclusive with {@link #setBookId(long)}.
     *
     * @param idList a list of book ids to update
     */
    public void setBookId(@NonNull final List<Long> idList) {
        //TODO: not really happy exposing the DOM's here, but it will do for now.
        // Ideally the sql behind this becomes static and uses binds
        mBookWhereClause = DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_PK_ID)
                + " IN (" + Csv.join(",", idList) + ')';
    }

    @Override
    public void runTask()
            throws InterruptedException {
        int progressCounter = 0;
        // the 'order by' used in fetchBooksForFieldUpdate makes sure we update the 'oldest'
        // book to 'newest'. So if we get interrupted, we can pick up the thread (arf...) later.
        try (BookCursor books = mDb.fetchBooksForFieldUpdate(mBookWhereClause)) {

            mTaskManager.setMaxProgress(this, books.getCount());
            while (books.moveToNext() && !isCancelled()) {
                progressCounter++;

                // Copy the fields from the cursor and build a complete set of data for this book.
                // This only needs to include data that we can fetch (so, for example,
                // bookshelves are ignored).
                mOriginalBookData = new Bundle();
                for (int i = 0; i < books.getColumnCount(); i++) {
                    mOriginalBookData.putString(books.getColumnName(i), books.getString(i));
                }

                // Get the book ID
                mCurrentBookId = books.getId();
                // Get the book UUID
                mCurrentUuid = mOriginalBookData.getString(DBDefinitions.KEY_BOOK_UUID);

                // Get the array data about the book
                mOriginalBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                         mDb.getAuthorsByBookId(mCurrentBookId));
                mOriginalBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                         mDb.getSeriesByBookId(mCurrentBookId));
                mOriginalBookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                                         mDb.getTocEntryByBook(mCurrentBookId));

                // Grab the searchable fields. Ideally we will have an ISBN but we may not.

                // Make sure the searchable fields are not NULL
                // (legacy data, and possibly set to null when adding new book)
                String isbn = mOriginalBookData.getString(DBDefinitions.KEY_ISBN, "");
                String author = mOriginalBookData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED, "");
                String title = mOriginalBookData.getString(DBDefinitions.KEY_TITLE, "");

                // Check which fields this book needs.
                mCurrentBookFieldUsages = getCurrentBookFieldUsages(mFields);
                // if no data required, skip to next book
                if (mCurrentBookFieldUsages.isEmpty()
                        || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    // Update progress appropriately
                    mTaskManager.sendHeaderUpdate(
                            getContext().getString(R.string.progress_msg_skip_title, title));
                    continue;
                }

                // at this point we do want a search.

                // Update the progress appropriately
                if (!title.isEmpty()) {
                    mTaskManager.sendHeaderUpdate(title);
                } else {
                    mTaskManager.sendHeaderUpdate(isbn);
                }
                // update the counter
                mTaskManager.sendProgress(this, 0, progressCounter);

                // Start searching, then wait...
                mSearchCoordinator.search(mSearchSites, author, title, isbn,
                                          mCurrentBookFieldUsages.containsKey(
                                                  UniqueId.BKEY_COVER_IMAGE));

                mSearchLock.lock();
                try {
//                    Logger.info(this, "runTask","awaiting end of search");
                    /*
                     * Wait for the search to complete.
                     * After processing the results, it wil call mSearchDone.signal()
                     */
                    mSearchDone.await();
//                    Logger.info(this, "runTask","search done, next!");
                } finally {
                    mSearchLock.unlock();
                }

            }
        } finally {
            // Tell our listener they can clear the progress message.
            mTaskManager.sendHeaderUpdate(null);
            // Create the final message for them (user message, not a Progress message)
            mFinalMessage = getContext().getString(R.string.progress_end_num_books_searched,
                                                   progressCounter);
            if (isCancelled()) {
                mFinalMessage = getContext().getString(R.string.progress_end_cancelled_info,
                                                       mFinalMessage);
//                Logger.info(this, " was cancelled");
            }
        }
    }

    /**
     * See if there is a reason to fetch ANY data by checking which fields this book needs.
     */
    private Map<String, FieldUsage> getCurrentBookFieldUsages(
            @NonNull final Map<String, FieldUsage> requestedFields) {

        Map<String, FieldUsage> fieldUsages = new LinkedHashMap<>();
        for (FieldUsage usage : requestedFields.values()) {
            // Not selected, we don't want it
            if (usage.isSelected()) {
                switch (usage.usage) {
                    case AddExtra:
                    case Overwrite:
                        // Add and Overwrite mean we always get the data
                        fieldUsages.put(usage.fieldId, usage);
                        break;
                    case CopyIfBlank:
                        currentCopyIfBlank(fieldUsages, usage);
                        break;
                }
            }
        }

        return fieldUsages;
    }

    private void currentCopyIfBlank(@NonNull final Map<String, FieldUsage> fieldUsages,
                                    @NonNull final FieldUsage usage) {
        // Handle special cases first, 'default:' for the rest
        switch (usage.fieldId) {
            // - If it's a thumbnail, then see if it's missing or empty.
            case UniqueId.BKEY_COVER_IMAGE:
                File file = StorageUtils.getCoverFile(mCurrentUuid);
                if (!file.exists() || file.length() == 0) {
                    fieldUsages.put(usage.fieldId, usage);
                }
                break;

            case UniqueId.BKEY_AUTHOR_ARRAY:
                // We should never have a book without authors, but be paranoid
                if (mOriginalBookData.containsKey(usage.fieldId)) {
                    ArrayList<Author> list =
                            mOriginalBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                    if (list == null || list.isEmpty()) {
                        fieldUsages.put(usage.fieldId, usage);
                    }
                }
                break;

            case UniqueId.BKEY_SERIES_ARRAY:
                if (mOriginalBookData.containsKey(usage.fieldId)) {
                    ArrayList<Series> list =
                            mOriginalBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                    if (list == null || list.isEmpty()) {
                        fieldUsages.put(usage.fieldId, usage);
                    }
                }
                break;

            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                if (mOriginalBookData.containsKey(usage.fieldId)) {
                    ArrayList<TocEntry> list =
                            mOriginalBookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
                    if (list == null || list.isEmpty()) {
                        fieldUsages.put(usage.fieldId, usage);
                    }
                }
                break;

            default:
                // If the original was blank, add to list
                String value = mOriginalBookData.getString(usage.fieldId);
                if (value == null || value.isEmpty()) {
                    fieldUsages.put(usage.fieldId, usage);
                }
                break;
        }
    }

    /**
     * The Update task is done.
     */
    @Override
    public void onTaskFinish() {
        cleanup();
    }

    /**
     * Called in the main thread for this object when the search for one book has completed.
     */
    public void onSearchFinished(final boolean wasCancelled,
                                 @NonNull final Bundle bookData) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debugEnter(this, "onSearchFinished",
                              "bookId=" + mCurrentBookId);
        }

        if (wasCancelled) {
            // if the search was cancelled, propagate by cancelling ourselves.
            cancelTask();
        } else if (bookData.isEmpty()) {
            // tell the user if the search failed.
            mTaskManager.sendUserMessage(R.string.warning_unable_to_find_book);
        }

        // Save the local data from the context so we can start a new search
        if (!isCancelled() && !bookData.isEmpty()) {
            processSearchResults(mCurrentBookId, mCurrentUuid, mCurrentBookFieldUsages, bookData,
                                 mOriginalBookData);
        }

        /*
         * The search is complete AND the class-level data has been cached by the processing thread.
         * Let another search begin.
         */
        mSearchLock.lock();
        try {
            mSearchDone.signal();
        } finally {
            mSearchLock.unlock();
        }
    }

    /**
     * Passed the old & new data, construct the update data and perform the update.
     *
     * @param bookId           Book ID
     * @param newBookData      Data gathered from internet
     * @param originalBookData Original data
     */
    private void processSearchResults(final long bookId,
                                      @NonNull final String uuid,
                                      @NonNull final Map<String, FieldUsage> requestedFields,
                                      @NonNull final Bundle newBookData,
                                      @NonNull final Bundle originalBookData) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "processSearchResults", "bookId=" + bookId);
        }
        // First, filter the data to remove keys we don't care about
        List<String> toRemove = new ArrayList<>();
        for (String key : newBookData.keySet()) {
            //noinspection ConstantConditions
            if (!requestedFields.containsKey(key) || !requestedFields.get(key).isSelected()) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            newBookData.remove(key);
        }

        // For each field, process it according the usage.
        for (FieldUsage usage : requestedFields.values()) {
            if (newBookData.containsKey(usage.fieldId)) {
                // Handle thumbnail specially
                if (usage.fieldId.equals(UniqueId.BKEY_COVER_IMAGE)) {
                    File downloadedFile = StorageUtils.getTempCoverFile();
                    boolean copyThumb = false;
                    if (usage.usage == FieldUsage.Usage.CopyIfBlank) {
                        File file = StorageUtils.getCoverFile(uuid);
                        copyThumb = !file.exists() || file.length() == 0;
                    } else if (usage.usage == FieldUsage.Usage.Overwrite) {
                        copyThumb = true;
                    }
                    if (copyThumb) {
                        File destination = StorageUtils.getCoverFile(uuid);
                        StorageUtils.renameFile(downloadedFile, destination);
                    } else {
                        StorageUtils.deleteFile(downloadedFile);
                    }
                } else {
                    switch (usage.usage) {
                        case Overwrite:
                            // Nothing to do; just use new data
                            break;

                        case CopyIfBlank:
                            handleCopyIfBlank(usage, originalBookData, newBookData);
                            break;

                        case AddExtra:
                            handleAddExtra(usage, originalBookData, newBookData);
                            break;
                    }
                }
            }
        }

        // Update
        if (!newBookData.isEmpty()) {
            mDb.updateBook(bookId, new Book(newBookData), 0);
        }
    }

    /**
     * AddExtra: merge arrays.
     */
    private void handleAddExtra(@NonNull final FieldUsage usage,
                                @NonNull final Bundle originalBookData,
                                @NonNull final Bundle newBookData) {
        // Handle arrays (note: before you're clever, and collapse this to
        // one... Android Studio hides the type in the <~> notation!
        switch (usage.fieldId) {
            case UniqueId.BKEY_AUTHOR_ARRAY:
                UpdateFieldsFromInternetTask.<Author>merge(usage.fieldId,
                                                           originalBookData, newBookData);
                break;

            case UniqueId.BKEY_SERIES_ARRAY:
                UpdateFieldsFromInternetTask.<Series>merge(usage.fieldId,
                                                           originalBookData, newBookData);
                break;

            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                UpdateFieldsFromInternetTask.<TocEntry>merge(usage.fieldId,
                                                             originalBookData, newBookData);
                break;

            default:
                // No idea how to handle this for non-arrays
                throw new IllegalTypeException("Illegal usage `" + usage.usage
                                                       + "` for field `"
                                                       + usage.fieldId + '`');
        }
    }

    /**
     * CopyIfBlank: replace if needed.
     */
    private void handleCopyIfBlank(@NonNull final FieldUsage usage,
                                   @NonNull final Bundle originalBookData,
                                   @NonNull final Bundle newBookData) {
        switch (usage.fieldId) {
            case UniqueId.BKEY_AUTHOR_ARRAY:
                if (originalBookData.containsKey(usage.fieldId)) {
                    ArrayList<Author> list =
                            originalBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                    if (list != null && !list.isEmpty()) {
                        newBookData.remove(usage.fieldId);
                    }
                }
                break;

            case UniqueId.BKEY_SERIES_ARRAY:
                if (originalBookData.containsKey(usage.fieldId)) {
                    ArrayList<Series> list =
                            originalBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                    if (list != null && !list.isEmpty()) {
                        newBookData.remove(usage.fieldId);
                    }
                }
                break;

            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                if (originalBookData.containsKey(usage.fieldId)) {
                    ArrayList<TocEntry> list =
                            originalBookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
                    if (list != null && !list.isEmpty()) {
                        newBookData.remove(usage.fieldId);
                    }
                }
                break;

            default:
                // If the original was non-blank, erase from list
                String value = originalBookData.getString(usage.fieldId);
                if (value != null && !value.isEmpty()) {
                    newBookData.remove(usage.fieldId);
                }
                break;
        }
    }

    /**
     * Cleanup.
     */
    private void cleanup() {
        if (mDb != null) {
            mDb.close();
        }
        mDb = null;
    }

    /**
     * Cleanup.
     */
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        cleanup();
        super.finalize();
    }
}
