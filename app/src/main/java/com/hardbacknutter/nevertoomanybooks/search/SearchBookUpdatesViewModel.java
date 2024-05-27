/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * IMPORTANT: {@link #onAllDone()} returns a {@link Book} while it SHOULD
 * really just return an {@link EditBookOutput} object to be compatible
 * with this class extending {@link SearchCoordinator} where
 * related MutableLiveData methods return a Book.
 */
public class SearchBookUpdatesViewModel
        extends SearchCoordinator {

    /** Log tag. */
    private static final String TAG = "SearchBookUpdatesViewModel";

    /** Prefix to store the settings. */
    private static final String SYNC_PROCESSOR_PREFIX = "fields.update.usage.";
    /** Ask confirmation if the number of covers to download exceeds this number. */
    private static final int WARN_FOR_NUMBER_OF_COVERS = 10;

    private final MutableLiveData<LiveDataEvent<Book>> listFinished =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Throwable>> listFailed =
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
    private int cachedSize;

    @NonNull
    LiveData<LiveDataEvent<Book>> onAllDone() {
        return listFinished;
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onAbort() {
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
                new SyncReaderProcessor.Builder(context, SYNC_PROCESSOR_PREFIX);

        // Cover fields will be at the top of the list.
        builder.add(context, context.getString(R.string.lbl_cover_front),
                    new String[]{DBKey.COVER[0]});
        builder.add(context, context.getString(R.string.lbl_cover_back),
                    new String[]{DBKey.COVER[1]});

        // These fields will be locally sorted and come next on the list
        final SortedMap<String, String[]> map = new TreeMap<>();
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
        map.put(context.getString(R.string.lbl_date_first_publication),
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
                new String[]{DBKey.BOOK_CONTENT_TYPE, Book.BKEY_TOC_LIST});
        map.put(context.getString(R.string.lbl_publishers),
                new String[]{DBKey.FK_PUBLISHER, Book.BKEY_PUBLISHER_LIST});

        map.forEach((label, keys) -> builder.add(context, label, keys));

        builder.addRelatedField(DBKey.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
               .addRelatedField(DBKey.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
               .addRelatedField(DBKey.PRICE_LISTED, DBKey.PRICE_LISTED_CURRENCY);

        // The (locally sorted) external-id fields are added at the end of the list.
        builder.addSidFields(context);

        return builder;
    }

    @NonNull
    Collection<SyncField> getSyncFields() {
        return syncProcessorBuilder.getSyncFields();
    }

    /**
     * Whether the user needs to be warned about lengthy download of covers.
     *
     * @return {@code true} if the user should be warned
     */
    boolean isShowWarningAboutCovers() {
        // Less than set number of books: don't check/warn
        if (bookIdList != null && bookIdList.size() < WARN_FOR_NUMBER_OF_COVERS) {
            return false;
        }

        // More than set number of books, ask the user if they really want to overwrite ALL covers
        return syncProcessorBuilder.getSyncAction(DBKey.COVER[0]) == SyncAction.Overwrite;
    }

    /**
     * Update the covers {@link SyncAction}.
     * Does nothing if the field ws not actually added before.
     *
     * @param action to set
     */
    void setCoverSyncAction(@NonNull final SyncAction action) {
        syncProcessorBuilder.setSyncAction(DBKey.COVER[0], action);
        syncProcessorBuilder.setSyncAction(DBKey.COVER[1], action);
    }

    int getTotalBooks() {
        if (bookIdList != null) {
            return bookIdList.size();
        } else {
            if (cachedSize == 0) {
                cachedSize = bookDao.count();
            }
            return cachedSize;
        }
    }

    /**
     * Allows to set the 'lowest' Book id to start from.
     * See {@link BookDao#fetchForAutoUpdateFromIdOnwards(long)}
     *
     * @param id the lowest book id to start from.
     *           This allows to fetch a subset of the requested set.
     *           Defaults to 0, i.e. the full set.
     */
    void setNextBookIdToProcess(final long id) {
        this.lastBookIdProcessed = id;
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     */
    void resetAll() {
        syncProcessorBuilder.resetPreferences();
    }

    /**
     * Update the {@link SyncAction} for all keys.
     *
     * @param action to set
     */
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
        syncProcessorBuilder.writePreferences();

        syncProcessor = syncProcessorBuilder.build();

        currentProgressCounter = 0;

        //noinspection OverlyBroadCatchBlock,CheckStyle
        try {
            if (bookIdList == null || bookIdList.isEmpty()) {
                currentCursor = bookDao.fetchForAutoUpdateFromIdOnwards(lastBookIdProcessed);
            } else {
                currentCursor = bookDao.fetchForAutoUpdate(bookIdList);
            }
            currentCursorCount = currentCursor.getCount();

        } catch (@NonNull final Exception e) {
            postSearch(e);
            // quit searching
            return false;
        }

        if (currentCursorCount == 0) {
            postSearch(true);
            // we're done searching
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
        //noinspection OverlyBroadCatchBlock,CheckStyle
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
                //noinspection DataFlowIssue
                currentFieldsWanted = syncProcessor.filter(currentBook);

                final String title = currentBook.getTitle();

                if (!currentFieldsWanted.isEmpty()) {
                    // remove all other criteria (this is CRUCIAL)
                    clearSearchCriteria();
                    boolean canSearch = false;

                    final String isbnStr = currentBook.getString(DBKey.BOOK_ISBN, null);
                    if (isbnStr != null && !isbnStr.isEmpty()) {
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
                    final Map<EngineId, String> externalIds = new EnumMap<>(EngineId.class);
                    SearchEngineConfig.getAll().forEach(seConfig -> {
                        final Domain domain = seConfig.getExternalIdDomain();
                        if (domain != null) {
                            final String value = currentBook.getString(domain.getName(), null);
                            if (value != null && !value.isEmpty() && !"0".equals(value)) {
                                externalIds.put(seConfig.getEngineId(), value);
                            }
                        }
                    });

                    if (!externalIds.isEmpty()) {
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
            // quit searching
            return false;
        }

        postSearch(true);
        // we're done searching
        return false;
    }

    /**
     * Process the search-result data for one book.
     *
     * @param context    Current context
     * @param remoteBook results of the search
     *
     * @return {@code true} if a new search for the next book was started.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean processOne(@NonNull final Context context,
                       @Nullable final Book remoteBook) {

        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            if (!isCancelled() && remoteBook != null && !remoteBook.isEmpty()) {
                final RealNumberParser realNumberParser =
                        new RealNumberParser(LocaleListUtils.asList(context));
                //noinspection DataFlowIssue
                final Book delta = syncProcessor.process(context, currentBookId, currentBook,
                                                         currentFieldsWanted, remoteBook,
                                                         realNumberParser);
                if (delta != null) {
                    try {
                        bookDao.update(context, delta);
                    } catch (@NonNull final StorageException | DaoWriteException e) {
                        // ignore, but log it.
                        LoggerFactory.getLogger().e(TAG, e);
                    }
                }
            }

            //update the counter, another one done.
            final TaskProgress taskProgress = new TaskProgress(
                    R.id.TASK_ID_UPDATE_FIELDS, null,
                    currentProgressCounter, currentCursorCount, null);
            synchronized (searchCoordinatorProgress) {
                searchCoordinatorProgress.setValue(LiveDataEvent.of(taskProgress));
            }

            // On to the next book in the list.
            return nextBook(context);
        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }
    }


    /**
     * Cleanup up and report the final outcome.
     * <p>
     * Callers:
     * <ul>
     * <li>when we're all done; success==true</li>
     * <li>when we've not started a search (for whatever reason)</li>
     * <li>when we're cancelled</li>
     * </ul>
     * <p>
     * Result message contains:
     * <ul>
     * <li>{@link EditBookOutput#BKEY_LAST_BOOK_ID_PROCESSED} for later resuming</li>
     * <li>{@link DBKey#FK_BOOK}: the first book in the list / the only book;
     *      not set if we did 'all' books</li>
     * </ul>
     *
     * @param success {@code true} if the search was successful
     *                or {@code false} if it was cancelled.
     */
    private void postSearch(final boolean success) {
        if (currentCursor != null) {
            currentCursor.close();
        }

        // Tell the SearchCoordinator we're done and it should clean up.
        setBaseMessage(null);
        super.cancel();

        // the last book id which was handled; can be used to restart the update.
        lastBookIdProcessed = currentBookId;

        // See class docs above as to why this is not an EditBookOutput object!
        final Book book = new Book();

        book.putLong(EditBookOutput.BKEY_LAST_BOOK_ID_PROCESSED, lastBookIdProcessed);

        // all books || a list of books (i.e. 1 or more books)
        if (bookIdList == null || !bookIdList.isEmpty()) {
            //FIXME: we should only return this if we actually modified a book
            book.putBoolean(EditBookOutput.BKEY_MODIFIED, true);
        }

        // if applicable, pass the first book for repositioning the list on screen
        if (bookIdList != null && !bookIdList.isEmpty()) {
            book.putLong(DBKey.FK_BOOK, bookIdList.get(0));
        }


        final LiveDataEvent<Book> message = LiveDataEvent.of(book);
        if (success) {
            listFinished.setValue(message);
        } else {
            searchCoordinatorCancelled.setValue(message);
        }
    }

    /**
     * There was an Exception thrown during the search;
     * Cleanup up and report the final outcome.
     *
     * @param e exception
     */
    private void postSearch(@NonNull final Exception e) {
        LoggerFactory.getLogger().e(TAG, e);

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

        final LiveDataEvent<Throwable> message = LiveDataEvent.of(e);
        listFailed.setValue(message);
    }

    @Override
    public void cancel() {
        super.cancel();
        postSearch(false);
    }
}
