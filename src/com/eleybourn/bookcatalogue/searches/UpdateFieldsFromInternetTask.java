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
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.FieldUsage;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTaskListener;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * ManagedTask to update requested fields by doing a search.
 * <p>
 * NEWKIND must stay in sync with {@link UpdateFieldsFromInternetActivity}
 *
 * @author Philip Warner
 */
public class UpdateFieldsFromInternetTask
        extends ManagedTask {

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

    /** Database access. */
    private DAO mDb;

    // Data related to current row being processed
    /** Original row data. */
    private Bundle mOriginalBookData;
    /** current book ID. */
    private long mCurrentBookId;
    /** current book UUID. */
    private String mCurrentUuid;

    /** The (subset) of fields relevant to the current book. */
    private Map<String, FieldUsage> mCurrentBookFieldUsages;
    /**
     * Called in the main thread for this object when the search for one book has completed.
     * <p>
     * Note: do not make it local... we need a strong reference here.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SearchCoordinator.SearchFinishedListener mListener =
            new SearchCoordinator.SearchFinishedListener() {
                @Override
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
                        processSearchResults(bookData);
                    }

                    /*
                     * The search is complete AND the class-level data has been cached
                     * by the processing thread. Let another search begin.
                     */
                    mSearchLock.lock();
                    try {
                        mSearchDone.signal();
                    } finally {
                        mSearchLock.unlock();
                    }
                }
            };
    /** WHERE clause to use in cursor, none by default, but see {@link #setBookId(long)}. */
    @NonNull
    private String mBookWhereClause = "";

    /**
     * Constructor.
     *
     * @param taskManager Object to manage background tasks
     * @param searchSites sites to search, see {@link SearchSites#SEARCH_ALL}
     * @param fields      fields to update
     * @param listener    where to send our results to
     */
    public UpdateFieldsFromInternetTask(@NonNull final TaskManager taskManager,
                                        final int searchSites,
                                        @NonNull final Map<String, FieldUsage> fields,
                                        @NonNull final ManagedTaskListener listener) {
        super(taskManager, "UpdateFieldsFromInternetTask");

        mDb = new DAO();
        mFields = fields;
        mSearchSites = searchSites;

        mSearchCoordinator = new SearchCoordinator(mTaskManager, mListener);
        mTaskManager.sendHeaderUpdate(R.string.progress_msg_starting_search);
        MESSAGE_SWITCH.addListener(getSenderId(), false, listener);
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
        // the 'order by' used in fetchBooksForFieldUpdate makes sure we update from 'oldest'
        // book to 'newest'. So if we get interrupted, we can pick up the thread (arf...) later.
        try (BookCursor books = mDb.fetchBooksForFieldUpdate(mBookWhereClause)) {

            int langCol = books.getColumnIndex(DBDefinitions.KEY_LANGUAGE);

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

                // always add the language if we have one, so we can use it for the locale details.
                if (langCol > 0) {
                    String lang = books.getString(langCol);
                    if (lang != null && !lang.isEmpty()) {
                        mOriginalBookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
                    }
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
                            getResources().getString(R.string.progress_msg_skip_title, title));
                    continue;
                }

                boolean wantCoverImage = mCurrentBookFieldUsages
                        .containsKey(UniqueId.BKEY_COVER_IMAGE);

                // at this point we know we want a search.

                // Update the progress with a new base message.
                if (!title.isEmpty()) {
                    mTaskManager.sendHeaderUpdate(title);
                } else {
                    mTaskManager.sendHeaderUpdate(isbn);
                }

                // Start searching, then wait...
                mSearchCoordinator.search(mSearchSites, author, title, isbn, wantCoverImage);

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

                // update the counter, another one done.
                mTaskManager.sendProgress(this, 0, progressCounter);

            }
        } finally {
            // Tell our listener they can clear the progress message.
            mTaskManager.sendHeaderUpdate(null);
            // Create the final message for them (user message, not a Progress message)
            mFinalMessage = getResources().getString(R.string.progress_end_num_books_searched,
                                                     progressCounter);
            if (isCancelled()) {
                mFinalMessage = getResources().getString(R.string.progress_end_cancelled_info,
                                                         mFinalMessage);
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
                    case Merge:
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

            // We should never have a book without authors, but be paranoid
            case UniqueId.BKEY_AUTHOR_ARRAY:
            case UniqueId.BKEY_SERIES_ARRAY:
            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                if (mOriginalBookData.containsKey(usage.fieldId)) {
                    ArrayList list = mOriginalBookData.getParcelableArrayList(usage.fieldId);
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
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     * Passed the old & new data, construct the update data and perform the update.
     *
     * @param newBookData Data gathered from internet
     */
    private void processSearchResults(@NonNull final Bundle newBookData) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "processSearchResults", "bookId=" + mCurrentBookId);
        }

        // Filter the data to remove keys we don't care about
        List<String> toRemove = new ArrayList<>();
        for (String key : newBookData.keySet()) {
            //noinspection ConstantConditions
            if (!mCurrentBookFieldUsages.containsKey(key)
                    || !mCurrentBookFieldUsages.get(key).isSelected()) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            newBookData.remove(key);
        }

        // For each field, process it according the usage.
        for (FieldUsage usage : mCurrentBookFieldUsages.values()) {
            if (newBookData.containsKey(usage.fieldId)) {
                // Handle thumbnail specially
                if (usage.fieldId.equals(UniqueId.BKEY_COVER_IMAGE)) {
                    boolean copyThumb = false;
                    switch (usage.usage) {
                        case CopyIfBlank:
                            File file = StorageUtils.getCoverFile(mCurrentUuid);
                            copyThumb = !file.exists() || file.length() == 0;
                            break;

                        case Overwrite:
                            copyThumb = true;
                            break;

                        default:
                            // not applicable
                    }

                    File downloadedFile = StorageUtils.getTempCoverFile();
                    if (copyThumb) {
                        File destination = StorageUtils.getCoverFile(mCurrentUuid);
                        StorageUtils.renameFile(downloadedFile, destination);
                    } else {
                        StorageUtils.deleteFile(downloadedFile);
                    }

                } else {
                    switch (usage.usage) {
                        case CopyIfBlank:
                            copyIfBlank(usage.fieldId, newBookData);
                            break;

                        case Merge:
                            merge(usage.fieldId, newBookData);
                            break;

                        case Overwrite:
                            // Nothing to do; just use new data
                            break;
                    }
                }
            }
        }

        // Commit new data
        if (!newBookData.isEmpty()) {

            // Get the language, if there was one. Otherwise use the original one.
            String bookLang = newBookData.getString(DBDefinitions.KEY_LANGUAGE);
            if (bookLang == null || bookLang.isEmpty()) {
                bookLang = mOriginalBookData.getString(DBDefinitions.KEY_LANGUAGE);
                if (bookLang != null && !bookLang.isEmpty()) {
                    newBookData.putString(DBDefinitions.KEY_LANGUAGE, bookLang);
                }
            }

            mDb.updateBook(mCurrentBookId, new Book(newBookData), 0);
        }
    }

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param <T>         type of the ArrayList elements
     * @param listKey     for the ArrayList to combine
     * @param newBookData Destination Bundle where the combined list is updated
     */
    private <T extends Parcelable> void merge(@NonNull final String listKey,
                                              @NonNull final Bundle newBookData) {

        // Get the list from the original, if it's present.
        ArrayList<T> destinationList = null;
        if (mOriginalBookData.containsKey(listKey)) {
            destinationList = mOriginalBookData.getParcelableArrayList(listKey);
        }
        // Otherwise use an empty list
        if (destinationList == null) {
            destinationList = new ArrayList<>();
        }

        // Get the list from the new data, if it's present.
        ArrayList<T> newDataList = newBookData.getParcelableArrayList(listKey);
        if (newDataList != null && !newDataList.isEmpty()) {
            // do the actual merge by copying new data to the source list
            // if the latter does not already have the object.
            for (T item : newDataList) {
                if (!destinationList.contains(item)) {
                    destinationList.add(item);
                }
            }
        }

        // Save the combined list to the new data bundle.
        newBookData.putParcelableArrayList(listKey, destinationList);
    }

    /**
     * CopyIfBlank: replace null/empty data.
     */
    private void copyIfBlank(@NonNull final String key,
                             @NonNull final Bundle /* in/out */ newBookData) {
        switch (key) {
            case UniqueId.BKEY_AUTHOR_ARRAY:
            case UniqueId.BKEY_SERIES_ARRAY:
            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                if (mOriginalBookData.containsKey(key)) {
                    ArrayList<Parcelable> list = mOriginalBookData.getParcelableArrayList(key);
                    if (list != null && !list.isEmpty()) {
                        newBookData.remove(key);
                    }
                }
                break;

            default:
                // If the original was non-blank, erase from list
                String value = mOriginalBookData.getString(key);
                if (value != null && !value.isEmpty()) {
                    newBookData.remove(key);
                }
                break;
        }
    }
}
