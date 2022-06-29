/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class SearchBookUpdatesViewModel
        extends SearchCoordinator {

    /** Log tag. */
    private static final String TAG = "SearchBookUpdatesViewModel";
    public static final String BKEY_LAST_BOOK_ID_PROCESSED = TAG + ":lastId";
    /** Boolean - whether a list was processed/modified. */
    public static final String BKEY_LIST_MODIFIED = TAG + ":modified:list";
    /**
     * Long - if this was a single-book request, the book id if it was modified.
     * Only present if {@link #BKEY_LIST_MODIFIED} is NOT present.
     */
    public static final String BKEY_BOOK_MODIFIED = TAG + ":modified:book";

    /** Prefix to store the settings. */
    private static final String SYNC_PROCESSOR_PREFIX = "fields.update.usage.";

    private final MutableLiveData<LiveDataEvent<TaskResult<Bundle>>> listFinished =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> listFailed =
            new MutableLiveData<>();

    /**
     * Current and original book data.
     * The object gets cleared and reused for each iteration of the loop.
     */
    private final Book currentBook = new Book();

    /** The configuration on which fields to update and how. */
    private SyncReaderProcessor.Builder syncProcessorBuilder;

    /**
     * The final configuration build from {@link #syncProcessorBuilder},
     * ready to start processing.
     */
    @Nullable
    private SyncReaderProcessor syncProcessor;

    /** Database Access. */
    private BookDao bookDao;

    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private List<Long> bookIdList;

    /** Allows restarting an update task from the given book id onwards. 0 for all. */
    private long lastBookIdProcessed;

    /** The (subset) of fields relevant to the current book. */
    private Map<String, SyncField> currentFieldsWanted;
    /** Tracks the current book ID. */
    private long currentBookId;
    private Cursor currentCursor;

    private int currentProgressCounter;
    private int currentCursorCount;

    /** Observable. */
    @NonNull
    LiveData<LiveDataEvent<TaskResult<Bundle>>> onAllDone() {
        return listFinished;
    }

    /** Observable. */
    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onAbort() {
        return listFailed;
    }

    @Override
    protected void onCleared() {
        // sanity check, should already have been closed.
        if (currentCursor != null) {
            currentCursor.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        // init the SearchCoordinator.
        super.init(context, args);

        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            if (args != null) {
                // if we have args, then we can expect the list to be present
                bookIdList = Objects.requireNonNull(
                        ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST));
            }

            syncProcessorBuilder = createSyncProcessorBuilder(context);
        }
    }

    @NonNull
    private SyncReaderProcessor.Builder createSyncProcessorBuilder(@NonNull final Context context) {
        final SyncReaderProcessor.Builder builder =
                new SyncReaderProcessor.Builder(SYNC_PROCESSOR_PREFIX);

        final SortedMap<String, String[]> map = new TreeMap<>();
        map.put(context.getString(R.string.lbl_cover_front),
                new String[]{FieldVisibility.COVER[0]});
        map.put(context.getString(R.string.lbl_cover_back),
                new String[]{FieldVisibility.COVER[1]});
        map.put(context.getString(R.string.lbl_title),
                new String[]{DBKey.TITLE});
        map.put(context.getString(R.string.lbl_isbn),
                new String[]{DBKey.BOOK_ISBN});
        map.put(context.getString(R.string.lbl_description),
                new String[]{DBKey.DESCRIPTION});
        map.put(context.getString(R.string.lbl_print_run),
                new String[]{DBKey.PRINT_RUN});
        map.put(context.getString(R.string.lbl_date_published),
                new String[]{DBKey.BOOK_PUBLICATION__DATE});
        map.put(context.getString(R.string.lbl_first_publication),
                new String[]{DBKey.FIRST_PUBLICATION__DATE});
        map.put(context.getString(R.string.lbl_price_listed),
                new String[]{DBKey.PRICE_LISTED});
        map.put(context.getString(R.string.lbl_pages),
                new String[]{DBKey.PAGE_COUNT});
        map.put(context.getString(R.string.lbl_format),
                new String[]{DBKey.FORMAT});
        map.put(context.getString(R.string.lbl_color),
                new String[]{DBKey.COLOR});
        map.put(context.getString(R.string.lbl_language),
                new String[]{DBKey.LANGUAGE});
        map.put(context.getString(R.string.lbl_genre),
                new String[]{DBKey.GENRE});
        map.put(context.getString(R.string.lbl_authors),
                new String[]{DBKey.FK_AUTHOR, Book.BKEY_AUTHOR_LIST});
        map.put(context.getString(R.string.lbl_series_multiple),
                new String[]{DBKey.FK_SERIES, Book.BKEY_SERIES_LIST});
        map.put(context.getString(R.string.lbl_table_of_content),
                new String[]{DBKey.TOC_TYPE__BITMASK, Book.BKEY_TOC_LIST});
        map.put(context.getString(R.string.lbl_publishers),
                new String[]{DBKey.FK_PUBLISHER, Book.BKEY_PUBLISHER_LIST});

        map.forEach(builder::add);

        builder.addRelatedField(FieldVisibility.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
               .addRelatedField(FieldVisibility.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
               .addRelatedField(DBKey.PRICE_LISTED, DBKey.PRICE_LISTED_CURRENCY);


        // Add the external-id fields at the end.
        final SortedMap<String, String> sidMap = new TreeMap<>();
        SearchEngineRegistry.getInstance().getAll().forEach(seConfig -> {
            final Domain domain = seConfig.getExternalIdDomain();
            if (domain != null) {
                sidMap.put(context.getString(seConfig.getLabelResId()), domain.getName());
            }
        });
        sidMap.forEach((label, key) -> builder.add(label, key, SyncAction.Overwrite));

        return builder;
    }

    @NonNull
    Collection<SyncField> getSyncFields() {
        return syncProcessorBuilder.getSyncFields();
    }

    /**
     * Whether the user needs to be warned about lengthy download of covers.
     *
     * @return {@code true} if a dialog should be shown
     */
    boolean isShowWarningAboutCovers() {

        // Less than (arbitrary) 10 books, don't check/warn needed.
        if (bookIdList != null && bookIdList.size() < 10) {
            return false;
        }

        // More than 10 books, check if the user wants ALL covers
        return syncProcessorBuilder.getSyncAction(FieldVisibility.COVER[0])
               == SyncAction.Overwrite;
    }

    /**
     * Update the covers {@link SyncAction}.
     * Does nothing if the field ws not actually added before.
     *
     * @param action to set
     */
    void setCoverSyncAction(@NonNull final SyncAction action) {
        syncProcessorBuilder.setSyncAction(FieldVisibility.COVER[0], action);
        syncProcessorBuilder.setSyncAction(FieldVisibility.COVER[1], action);
    }

    /**
     * Allows to set the 'lowest' Book id to start from.
     * See {@link BookDao#fetchFromIdOnwards(long)}
     *
     * @param id the lowest book id to start from.
     *           This allows to fetch a subset of the requested set.
     *           Defaults to 0, i.e. the full set.
     */
    void setNextBookIdToProcess(final long id) {
        this.lastBookIdProcessed = id;
    }

    /**
     * Write current settings to the user preferences.
     */
    void writePreferences() {
        syncProcessorBuilder.writePreferences();
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     */
    void resetAll() {
        syncProcessorBuilder.resetPreferences();
    }

    public void setAll(@NonNull final SyncAction action) {
        syncProcessorBuilder.setSyncAction(action);
    }

    /**
     * Start a search.
     *
     * @param context Current context
     *
     * @return {@code true} if a search was started.
     */
    boolean startSearch(@NonNull final Context context) {

        syncProcessor = syncProcessorBuilder.build();

        currentProgressCounter = 0;

        try {
            if (bookIdList == null || bookIdList.isEmpty()) {
                currentCursor = bookDao.fetchFromIdOnwards(lastBookIdProcessed);
            } else {
                currentCursor = bookDao.fetchById(bookIdList);
            }
            currentCursorCount = currentCursor.getCount();

        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }

        // kick off the first book
        return nextBook(context);
    }

    /**
     * Move the cursor forward and update the next book.
     *
     * @param context Current context
     *
     * @return {@code true} if a search was started.
     */
    private boolean nextBook(@NonNull final Context context) {
        try {
            final int idCol = currentCursor.getColumnIndex(DBKey.PK_ID);

            // loop/skip until we start a search for a book.
            while (currentCursor.moveToNext() && !isCancelled()) {

                currentProgressCounter++;

                //read the book ID
                currentBookId = currentCursor.getLong(idCol);

                // and populate the actual book based on the cursor data
                currentBook.load(currentBookId, currentCursor);

                // Check which fields this book needs.
                //noinspection ConstantConditions
                currentFieldsWanted = syncProcessor.filter(currentBook);

                final String title = currentBook.getTitle();

                if (!currentFieldsWanted.isEmpty()) {
                    // remove all other criteria (this is CRUCIAL)
                    clearSearchCriteria();
                    boolean canSearch = false;

                    final String isbnStr = currentBook.getString(DBKey.BOOK_ISBN);
                    if (!isbnStr.isEmpty()) {
                        setIsbnSearchText(isbnStr);
                        canSearch = true;
                    }

                    final Author author = currentBook.getPrimaryAuthor();
                    if (author != null) {
                        final String authorName = author.getFormattedName(true);
                        if (!authorName.isEmpty() && !title.isEmpty()) {
                            setAuthorSearchText(authorName);
                            setTitleSearchText(title);
                            canSearch = true;
                        }
                    }

                    // Collect external ID's we can use
                    final SparseArray<String> externalIds = new SparseArray<>();
                    for (final SearchEngineConfig config : SearchEngineRegistry
                            .getInstance().getAll()) {
                        final Domain domain = config.getExternalIdDomain();
                        if (domain != null) {
                            final String value = currentBook.getString(domain.getName());
                            if (!value.isEmpty() && !"0".equals(value)) {
                                externalIds.put(config.getEngineId(), value);
                            }
                        }
                    }

                    if (externalIds.size() > 0) {
                        setExternalIds(externalIds);
                        canSearch = true;
                    }

                    if (canSearch) {
                        // optional: whether this is used will depend on SearchEngine/Preferences
                        currentBook.getPrimaryPublisher().ifPresent(publisher -> {
                            final String publisherName = publisher.getName();
                            if (!publisherName.isEmpty()) {
                                setPublisherSearchText(publisherName);
                            }
                        });

                        // optional: whether this is used will depend on SearchEngine/Preferences
                        final boolean[] fetchCovers = new boolean[2];
                        for (int cIdx = 0; cIdx < 2; cIdx++) {
                            fetchCovers[cIdx] = currentFieldsWanted
                                    .containsKey(Book.BKEY_TMP_FILE_SPEC[cIdx]);
                        }
                        setFetchCover(fetchCovers);

                        // Start searching
                        if (search()) {
                            // Update the progress base message.
                            if (title.isEmpty()) {
                                setBaseMessage(isbnStr);
                            } else {
                                setBaseMessage(title);
                            }
                            return true;
                        }
                        // else if no search was started, fall through and loop to the next book.
                    }
                }

                // no data needed, or no search-data available.
                setBaseMessage(context.getString(R.string.progress_msg_skip_s, title));
            }
        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }

        postSearch(false);
        return false;
    }

    /**
     * Process the search-result data for one book.
     *
     * @param context  Current context
     * @param bookData result-data to process
     *
     * @return {@code true} if a new search (for the next book) was started.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean processOne(@NonNull final Context context,
                       @Nullable final Bundle bookData) {

        if (!isCancelled() && bookData != null && !bookData.isEmpty()) {
            //noinspection ConstantConditions
            final Book delta = syncProcessor.process(context, currentBookId, currentBook,
                                                     currentFieldsWanted, bookData);
            if (delta != null) {
                try {
                    bookDao.update(context, delta);
                } catch (@NonNull final StorageException | DaoWriteException e) {
                    // ignore, but log it.
                    Logger.error(TAG, e);
                }
            }
        }

        //update the counter, another one done.
        final TaskProgress taskProgress = new TaskProgress(
                R.id.TASK_ID_UPDATE_FIELDS, null,
                currentProgressCounter, currentCursorCount, null);
        searchCoordinatorProgress.setValue(new LiveDataEvent<>(taskProgress));

        // On to the next book in the list.
        return nextBook(context);
    }


    /**
     * Cleanup up and report the final outcome.
     *
     * <ul>Callers:
     * <li>when we're all done; success==true</li>
     * <li>when we've not started a search (for whatever reason)</li>
     * <li>when we're cancelled</li>
     * </ul>
     * <p>
     * Result message contains:
     * - BKEY_LAST_BOOK_ID_PROCESSED for later resuming
     * - DBKey.FK_BOOK: the first book in the list / the only book; not set if we did 'all' books
     */
    private void postSearch(final boolean wasCancelled) {
        if (currentCursor != null) {
            currentCursor.close();
        }

        // Tell the SearchCoordinator we're done and it should clean up.
        setBaseMessage(null);
        super.cancel();

        // the last book id which was handled; can be used to restart the update.
        lastBookIdProcessed = currentBookId;

        final Bundle results = ServiceLocator.newBundle();
        results.putLong(BKEY_LAST_BOOK_ID_PROCESSED, lastBookIdProcessed);

        // all books || a list of books (i.e. 2 or more books)
        if (bookIdList == null || bookIdList.size() > 1) {
            // Technically speaking when doing a list of books, the task might have been
            // cancelled before the first book was done. We disregard this fringe case.
            results.putBoolean(BKEY_LIST_MODIFIED, true);
        }

        // a single book
        if (bookIdList != null && bookIdList.size() == 1) {
            //URGENT: we should only return this is we actually modified the book
            results.putLong(BKEY_BOOK_MODIFIED, bookIdList.get(0));
        }

        // if applicable, pass the first book for repositioning the list on screen
        if (bookIdList != null && !bookIdList.isEmpty()) {
            results.putLong(DBKey.FK_BOOK, bookIdList.get(0));
        }

        final LiveDataEvent<TaskResult<Bundle>> message =
                new LiveDataEvent<>(new TaskResult<>(R.id.TASK_ID_UPDATE_FIELDS, results));
        if (wasCancelled) {
            searchCoordinatorCancelled.setValue(message);
        } else {
            listFinished.setValue(message);
        }
    }

    /**
     * There was an Exception thrown during the search;
     * Cleanup up and report the final outcome.
     *
     * @param e exception
     */
    private void postSearch(@NonNull final Exception e) {
        Logger.error(TAG, e);

        if (currentCursor != null) {
            currentCursor.close();
        }

        // Tell the SearchCoordinator we're done and it should clean up.
        setBaseMessage(null);
        super.cancel();

        // the last book id which was handled; can be used to restart the update.
        lastBookIdProcessed = currentBookId;

//        final Bundle results = ServiceLocator.newBundle();
//        results.putLong(BKEY_LAST_BOOK_ID_PROCESSED, lastBookIdProcessed);
//
//        // if applicable, pass the first book for repositioning the list on screen
//        if (bookIdList != null && !bookIdList.isEmpty()) {
//            results.putLong(DBKey.FK_BOOK, bookIdList.get(0));
//        }

        final LiveDataEvent<TaskResult<Exception>> message =
                new LiveDataEvent<>(new TaskResult<>(R.id.TASK_ID_UPDATE_FIELDS, e));
        listFailed.setValue(message);
    }

    @Override
    public void cancel() {
        super.cancel();
        postSearch(true);
    }
}
