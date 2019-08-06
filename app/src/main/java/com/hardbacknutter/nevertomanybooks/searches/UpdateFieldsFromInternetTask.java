/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.searches;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.UpdateFieldsFromInternetActivity;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.entities.Book;
import com.hardbacknutter.nevertomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertomanybooks.tasks.managedtasks.ManagedTaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

/**
 * ManagedTask to update requested fields by doing a search.
 * <p>
 * NEWKIND must stay in sync with {@link UpdateFieldsFromInternetActivity}
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

    /** Database Access. */
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
     * <b>Note:</b> do not make it local... we need a strong reference here.
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

    /** List of book ID's to update, {@code null} for all books. */
    @Nullable
    private List<Long> mBookIds;

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
     * you can limit it to a set of books.
     *
     * @param bookIds a list of book ID's to update
     */
    public void setBookId(@NonNull final List<Long> bookIds) {
        mBookIds = bookIds;
    }

    /**
     * If you keep a handle to this task, then you can call this to check what book was last done.
     *
     * @return bookId
     */
    public long getLastBookIdDone() {
        return mCurrentBookId;
    }

    /**
     * Allows to set the 'lowest' Book ID to start from. See {@link DAO#fetchBooks(List, long)}
     *
     * @param fromBookIdOnwards the lowest book ID to start from.
     *                          This allows to fetch a subset of the requested set.
     *                          Defaults to 0, i.e. the full set.
     */
    public void setCurrentBookId(final long fromBookIdOnwards) {
        mCurrentBookId = fromBookIdOnwards;
    }

    /**
     * See if there is a reason to fetch ANY data by checking which fields this book needs.
     *
     * @param requestedFields the FieldUsage map to clean up
     *
     * @return the consolidated FieldUsage map
     */
    private Map<String, FieldUsage> getCurrentBookFieldUsages(
            @NonNull final Map<String, FieldUsage> requestedFields) {

        Map<String, FieldUsage> fieldUsages = new LinkedHashMap<>();
        for (FieldUsage usage : requestedFields.values()) {
            switch (usage.usage) {
                case Append:
                case Overwrite:
                    // Append + Overwrite: we always need to get the data
                    fieldUsages.put(usage.fieldId, usage);
                    break;

                case CopyIfBlank:
                    currentCopyIfBlank(fieldUsages, usage);
                    break;

                case Skip:
                    // duh...
                    break;
            }
        }

        return fieldUsages;
    }

    private void currentCopyIfBlank(@NonNull final Map<String, FieldUsage> fieldUsages,
                                    @NonNull final FieldUsage usage) {
        // Handle special cases first, 'default:' for the rest
        switch (usage.fieldId) {
            // - If it's a thumbnail, then see if it's missing or empty.
            case UniqueId.BKEY_IMAGE:
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
     * The task is done.
     */
    @Override
    public void onTaskFinish() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    @Override
    public void runTask()
            throws InterruptedException {
        int progressCounter = 0;

        Context context = getContext();

        try (BookCursor books = mDb.fetchBooks(mBookIds, mCurrentBookId)) {

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

                // always add the language to the ORIGINAL data if we have one,
                // so we can use it for the Locale details when processing the results.
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
                String title = mOriginalBookData.getString(DBDefinitions.KEY_TITLE, "");
                String publisher = mOriginalBookData.getString(DBDefinitions.KEY_PUBLISHER, "");
                String author = mOriginalBookData.getString(
                        DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST, "");

                // Check which fields this book needs.
                mCurrentBookFieldUsages = getCurrentBookFieldUsages(mFields);
                // if no data required, skip to next book
                if (mCurrentBookFieldUsages.isEmpty()
                    || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    // Update progress appropriately
                    mTaskManager.sendHeaderUpdate(
                            context.getString(R.string.progress_msg_skip_title, title));
                    continue;
                }

                boolean wantCoverImage = mCurrentBookFieldUsages.containsKey(UniqueId.BKEY_IMAGE);

                // at this point we know we want a search, update the progress base message.
                if (!title.isEmpty()) {
                    mTaskManager.sendHeaderUpdate(title);
                } else {
                    mTaskManager.sendHeaderUpdate(isbn);
                }

                // Start searching, then wait...
                mSearchCoordinator.search(mSearchSites, isbn,
                                          author, title, publisher, wantCoverImage);

                mSearchLock.lock();
                try {
                    /*
                     * Wait for the search to complete.
                     * After processing the results, it wil call mSearchDone.signal()
                     */
                    mSearchDone.await();
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
            mFinalMessage = context.getString(R.string.progress_end_num_books_searched,
                                              progressCounter);
            if (isCancelled()) {
                mFinalMessage = context.getString(R.string.progress_end_cancelled_info,
                                                  mFinalMessage);
            }
        }
    }

    /**
     * Passed the old & new data, construct the update data and perform the update.
     *
     * @param newBookData Data gathered from internet
     */
    private void processSearchResults(@NonNull final Bundle newBookData) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(this, "processSearchResults",
                         "bookId=" + mCurrentBookId);
        }

        // Filter the data to remove keys we don't care about
        List<String> toRemove = new ArrayList<>();
        for (String key : newBookData.keySet()) {
            //noinspection ConstantConditions
            if (!mCurrentBookFieldUsages.containsKey(key)
                || !mCurrentBookFieldUsages.get(key).isWanted()) {
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
                if (usage.fieldId.equals(UniqueId.BKEY_IMAGE)) {
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

                        case Append:
                            append(usage.fieldId, newBookData);
                            break;

                        case Overwrite:
                            // Nothing to do; just use the new data
                            break;
                    }
                }
            }
        }

        // Commit the new data
        if (!newBookData.isEmpty()) {

            // Get the language, if there was one requested for updating.
            String bookLang = newBookData.getString(DBDefinitions.KEY_LANGUAGE);
            if (bookLang == null || bookLang.isEmpty()) {
                // Otherwise add the original one.
                bookLang = mOriginalBookData.getString(DBDefinitions.KEY_LANGUAGE);
                if (bookLang != null && !bookLang.isEmpty()) {
                    newBookData.putString(DBDefinitions.KEY_LANGUAGE, bookLang);
                }
            }

            //TODO: should be using a user context.
            Context context = App.getAppContext();
            Locale userLocale = LocaleUtils.getPreferredLocale();
            mDb.updateBook(context, userLocale, mCurrentBookId, new Book(newBookData), 0);
        }
    }

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param <T>         type of the ArrayList elements
     * @param key         for data
     * @param newBookData Destination Bundle where the combined list is updated
     */
    private <T extends Parcelable> void append(@NonNull final String key,
                                               @NonNull final Bundle newBookData) {
        // Get the list from the original, if it's present.
        ArrayList<T> destinationList = null;
        if (mOriginalBookData.containsKey(key)) {
            destinationList = mOriginalBookData.getParcelableArrayList(key);
        }

        // Otherwise use an empty list
        if (destinationList == null) {
            destinationList = new ArrayList<>();
        }

        // Get the list from the new data, if it's present.
        ArrayList<T> newDataList = newBookData.getParcelableArrayList(key);
        if (newDataList != null && !newDataList.isEmpty()) {
            // do the actual append by copying new data to the source list
            // if the latter does not already have the object.
            for (T item : newDataList) {
                if (!destinationList.contains(item)) {
                    destinationList.add(item);
                }
            }
        }

        // Save the combined list to the new data bundle.
        newBookData.putParcelableArrayList(key, destinationList);
    }

    /**
     * If we already had this field in the original data, then remove the new stuff.
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
