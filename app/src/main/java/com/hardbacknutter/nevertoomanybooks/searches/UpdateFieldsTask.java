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

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.UpdateFieldsFragment;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Task to update requested fields by doing a search.
 *
 * <strong>Note:</strong> this is currently a low-level Thread!
 * <p>
 * NEWTHINGS: This class must stay in sync with {@link UpdateFieldsFragment}
 */
public class UpdateFieldsTask
        extends Thread
        implements ProgressDialogFragment.Cancellable {

    /** log tag. */
    private static final String TAG = "UpdateFieldsTask";

    /** The fields that the user requested to update. */
    @NonNull
    private final Map<String, FieldUsage> mFields;
    /** Lock help by pop and by push when an item was added to an empty stack. */
    private final Lock mSearchLock = new ReentrantLock();
    /** Signal for available items. */
    private final Condition mOneSearch = mSearchLock.newCondition();

    /** Active search manager. */
    private final SearchCoordinator mSearchCoordinator;
    private final TaskListener<Long> mTaskListener;

    // Data related to current row being processed
    private final DAO mDb;
    /** Original row data. */
    private Bundle mOriginalBookData;
    /** current book ID. */
    private long mCurrentBookId;
    /** current book UUID. */
    private String mCurrentUuid;
    /** The (subset) of fields relevant to the current book. */
    private Map<String, FieldUsage> mCurrentBookFieldUsages;
    /** List of book ID's to update, {@code null} for all books. */
    @Nullable
    private List<Long> mBookIds;
    /** Indicates the user has requested a cancel. Up to the subclass to decide what to do. */
    private boolean mIsCancelled;

    /**
     * Called in the main thread for this object when the search for one book has completed.
     * <p>
     * <strong>Note:</strong> we ignore all search errors silently.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SearchCoordinator.SearchCoordinatorListener mSearchCoordinatorListener =
            new SearchCoordinator.SearchCoordinatorListener() {
                @Override
                public void onFinished(@NonNull final Bundle bookData,
                                       @Nullable final String searchErrors) {
                    Context context = App.getLocalizedAppContext();

                    if (!mIsCancelled && !bookData.isEmpty()) {
                        processSearchResults(context, bookData);
                    }

                    // This search is complete. Let another search begin.
                    mSearchLock.lock();
                    try {
                        mOneSearch.signal();
                    } finally {
                        mSearchLock.unlock();
                    }
                }

                @Override
                public void onCancelled() {
                    // if the search was cancelled, propagate by cancelling ourselves.
                    mIsCancelled = true;
                    interrupt();
                }

                @Override
                public void onProgress(@NonNull final TaskListener.ProgressMessage message) {
                    mTaskListener.onProgress(message);
                }
            };

    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param searchSites  sites to search, see {@link SearchSites#SEARCH_FLAG_MASK}
     * @param fields       fields to update
     * @param taskListener were to send the UpdateFieldsTask messages
     */
    public UpdateFieldsTask(@NonNull final DAO db,
                            @NonNull final SiteList searchSites,
                            @NonNull final Map<String, FieldUsage> fields,
                            @NonNull final TaskListener<Long> taskListener) {

        // Set the thread name to something helpful.
        setName("UpdateFieldsTask");

        // owned by a ViewModel, so can keep strong reference.
        mTaskListener = taskListener;

        mDb = db;
        mFields = fields;

        mSearchCoordinator = new SearchCoordinator();
        mSearchCoordinator.init(null, mSearchCoordinatorListener);
        mSearchCoordinator.setSiteList(searchSites);
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
     * Allows to set the 'lowest' Book id to start from. See {@link DAO#fetchBooks(List, long)}
     *
     * @param fromBookIdOnwards the lowest book id to start from.
     *                          This allows to fetch a subset of the requested set.
     *                          Defaults to 0, i.e. the full set.
     */
    public void setFromBookIdOnwards(final long fromBookIdOnwards) {
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
            switch (usage.getUsage()) {
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
        // Handle special cases first, 'default' for the rest
        switch (usage.fieldId) {
            // - If it's a thumbnail, then see if it's missing or empty.
            case UniqueId.BKEY_IMAGE:
                File file = StorageUtils.getCoverFileForUuid(mCurrentUuid);
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
     * Passed the old & new data, construct the update data and perform the update.
     *
     * @param newBookData Data gathered from internet
     */
    private void processSearchResults(@NonNull final Context context,
                                      @NonNull final Bundle newBookData) {
        // Filter the data to remove keys we don't care about
        Collection<String> toRemove = new ArrayList<>();
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
                    switch (usage.getUsage()) {
                        case CopyIfBlank:
                            File file = StorageUtils.getCoverFileForUuid(mCurrentUuid);
                            copyThumb = !file.exists() || file.length() == 0;
                            break;

                        case Overwrite:
                            copyThumb = true;
                            break;

                        case Skip:
                            // nothing to do
                            break;

                        case Append:
                            // not applicable
                            break;
                    }

                    File downloadedFile = StorageUtils.getTempCoverFile();
                    if (copyThumb) {
                        File destination = StorageUtils.getCoverFileForUuid(mCurrentUuid);
                        StorageUtils.renameFile(downloadedFile, destination);
                    } else {
                        StorageUtils.deleteFile(downloadedFile);
                    }

                } else {
                    switch (usage.getUsage()) {
                        case CopyIfBlank:
                            copyIfBlank(usage.fieldId, newBookData);
                            break;

                        case Append:
                            append(usage.fieldId, newBookData);
                            break;

                        case Overwrite:
                            // Nothing to do; just use the new data
                            break;

                        case Skip:
                            // nothing to do
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

            mDb.updateBook(context, mCurrentBookId, new Book(newBookData), 0);
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

    /**
     * Executed in main task thread.
     */
    @Override
    public void run() {
        Context context = App.getLocalizedAppContext();
        int progressCounter = 0;

        try (BookCursor bookCursor = mDb.fetchBooks(mBookIds, mCurrentBookId)) {

            int langCol = bookCursor.getColumnIndex(DBDefinitions.KEY_LANGUAGE);
            int maxProgress = bookCursor.getCount();

            while (bookCursor.moveToNext() && !mIsCancelled) {
                progressCounter++;

                // Copy the fields from the cursor and build a complete set of data for this book.
                // This only needs to include data that we can fetch (so, for example,
                // bookshelves are ignored).
                mOriginalBookData = new Bundle();
                for (int i = 0; i < bookCursor.getColumnCount(); i++) {
                    mOriginalBookData
                            .putString(bookCursor.getColumnName(i), bookCursor.getString(i));
                }

                // always add the language to the ORIGINAL data if we have one,
                // so we can use it for the Locale details when processing the results.
                if (langCol > 0) {
                    String lang = bookCursor.getString(langCol);
                    if (lang != null && !lang.isEmpty()) {
                        mOriginalBookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
                    }
                }

                // Get the book ID
                mCurrentBookId = bookCursor.getId();
                // Get the book UUID
                mCurrentUuid = mOriginalBookData.getString(DBDefinitions.KEY_BOOK_UUID);

                // Get the array data about the book
                mOriginalBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                         mDb.getAuthorsByBookId(
                                                                 mCurrentBookId));
                mOriginalBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                         mDb.getSeriesByBookId(mCurrentBookId));
                mOriginalBookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                                         mDb.getTocEntryByBook(mCurrentBookId));

                // Grab the searchable fields. Ideally we will have an ISBN but we may not.
                // Make sure the searchable fields are not NULL
                String isbn = mOriginalBookData.getString(DBDefinitions.KEY_ISBN, "");
                String title = mOriginalBookData.getString(DBDefinitions.KEY_TITLE, "");
                String author = mOriginalBookData.getString(
                        DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST, "");

                // Check which fields this book needs.
                mCurrentBookFieldUsages = getCurrentBookFieldUsages(mFields);
                // if no data required, skip to next book
                if (mCurrentBookFieldUsages.isEmpty()
                    || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    //update the progress base message.
                    mSearchCoordinator.setBaseMessage(
                            context.getString(R.string.progress_msg_skip_title, title));
                    continue;
                }

                // at this point we know we want a search,update the progress base message.
                if (!title.isEmpty()) {
                    mSearchCoordinator.setBaseMessage(title);
                } else {
                    mSearchCoordinator.setBaseMessage(isbn);
                }

                boolean wantCoverImage = mCurrentBookFieldUsages.containsKey(UniqueId.BKEY_IMAGE);
                // optional
                String publisher = mOriginalBookData.getString(DBDefinitions.KEY_PUBLISHER, "");

                mSearchCoordinator.setFetchThumbnail(wantCoverImage);
                mSearchCoordinator.setIsbnSearchText(isbn);
                mSearchCoordinator.setAuthorSearchText(author);
                mSearchCoordinator.setTitleSearchText(title);
                mSearchCoordinator.setPublisherSearchText(publisher);
                // Start searching, then wait...
                mSearchCoordinator.searchByText();
                mSearchLock.lock();
                try {
                    // Wait for the one search to complete.
                    // After processing the results, it wil call mOneSearch.signal()
                    mOneSearch.await();
                } finally {
                    mSearchLock.unlock();
                }

                //update the counter, another one done.
                mTaskListener.onProgress(new TaskListener.ProgressMessage(
                        R.id.TASK_ID_UPDATE_FIELDS, maxProgress, progressCounter, null));
            }

        } catch (@NonNull final InterruptedException e) {
            mIsCancelled = true;

        } catch (@NonNull final Exception e) {
            Logger.error(context, TAG, e);
            mException = e;

        } finally {
            // Tell the SearchCoordinator we're done.
            mSearchCoordinator.setBaseMessage(null);
            mSearchCoordinator.cancel(false);

            if (mIsCancelled) {
                mTaskListener.onFinished(new TaskListener.FinishMessage<>(
                        R.id.TASK_ID_UPDATE_FIELDS, TaskListener.TaskStatus.Cancelled,
                        mCurrentBookId, mException));
            } else {
                mTaskListener.onFinished(new TaskListener.FinishMessage<>(
                        R.id.TASK_ID_UPDATE_FIELDS,
                        mException != null ? TaskListener.TaskStatus.Failed
                                           : TaskListener.TaskStatus.Success,
                        mCurrentBookId, mException));
            }
        }
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        // disregard the mayInterruptIfRunning flag....
        interrupt();
        return true;
    }
}
