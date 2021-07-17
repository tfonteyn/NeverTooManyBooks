/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

public class SearchBookUpdatesViewModel
        extends SearchCoordinator {

    /** Log tag. */
    private static final String TAG = "SearchBookUpdatesViewModel";
    private static final String BKEY_LAST_BOOK_ID = TAG + ":lastId";

    /** Prefix to store the settings. */
    private static final String SYNC_PROCESSOR_PREFIX = "fields.update.usage.";

    private final MutableLiveData<FinishedMessage<Bundle>> mListFinished = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Exception>> mListFailed = new MutableLiveData<>();

    /**
     * Current and original book data.
     * The object gets cleared and reused for each iteration of the loop.
     */
    private final Book mCurrentBook = new Book();

    /** The configuration on which fields to update and how. */
    private SyncReaderProcessor.Builder mSyncProcessorBuilder;

    /**
     * The final configuration build from {@link #mSyncProcessorBuilder},
     * ready to start processing.
     */
    @Nullable
    private SyncReaderProcessor mSyncProcessor;

    /** Database Access. */
    private BookDao mBookDao;

    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private ArrayList<Long> mBookIdList;

    /** Allows restarting an update task from the given book id onwards. 0 for all. */
    private long mFromBookIdOnwards;

    /** Indicates the user has requested a cancel. Up to the subclass to decide what to do. */
    private boolean mIsCancelled;


    /** The (subset) of fields relevant to the current book. */
    private Map<String, SyncField> mCurrentFieldsWanted;
    /** Tracks the current book ID. */
    private long mCurrentBookId;
    private Cursor mCurrentCursor;

    private int mCurrentProgressCounter;
    private int mCurrentCursorCount;

    /** Observable. */
    @NonNull
    LiveData<FinishedMessage<Bundle>> onAllDone() {
        return mListFinished;
    }

    /** Observable. */
    @NonNull
    LiveData<FinishedMessage<Exception>> onAbort() {
        return mListFailed;
    }

    @Override
    protected void onCleared() {
        // sanity check, should already have been closed.
        if (mCurrentCursor != null) {
            mCurrentCursor.close();
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

        if (mBookDao == null) {
            mBookDao = ServiceLocator.getInstance().getBookDao();

            if (args != null) {
                //noinspection unchecked
                mBookIdList = (ArrayList<Long>) args.getSerializable(Book.BKEY_BOOK_ID_LIST);
            }

            mSyncProcessorBuilder = createSyncProcessorBuilder();
        }
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private SyncReaderProcessor.Builder createSyncProcessorBuilder() {
        final SyncReaderProcessor.Builder builder =
                new SyncReaderProcessor.Builder(SYNC_PROCESSOR_PREFIX)
                        // DBKey.PREFS_IS_USED_COVER is the SharedPreference key indicating
                        // the Action needed for this field.
                        // The actual file names are in the Book.BKEY_TMP_FILE_SPEC array.
                        .add(R.string.lbl_cover_front, DBKey.COVER_IS_USED[0])
                        .addRelatedField(DBKey.COVER_IS_USED[0], Book.BKEY_TMP_FILE_SPEC[0])

                        .add(R.string.lbl_cover_back, DBKey.COVER_IS_USED[1])
                        .addRelatedField(DBKey.COVER_IS_USED[1], Book.BKEY_TMP_FILE_SPEC[1])

                        .add(R.string.lbl_title, DBKey.KEY_TITLE)
                        .add(R.string.lbl_isbn, DBKey.KEY_ISBN)

                        .addList(R.string.lbl_authors, DBKey.FK_AUTHOR, Book.BKEY_AUTHOR_LIST)
                        .addList(R.string.lbl_series_multiple, DBKey.KEY_SERIES_TITLE,
                                 Book.BKEY_SERIES_LIST)

                        .add(R.string.lbl_description, DBKey.KEY_DESCRIPTION)

                        .addList(R.string.lbl_table_of_content, DBKey.BITMASK_TOC,
                                 Book.BKEY_TOC_LIST)

                        .addList(R.string.lbl_publishers, DBKey.KEY_PUBLISHER_NAME,
                                 Book.BKEY_PUBLISHER_LIST)
                        .add(R.string.lbl_print_run, DBKey.KEY_PRINT_RUN)
                        .add(R.string.lbl_date_published, DBKey.DATE_BOOK_PUBLICATION)
                        .add(R.string.lbl_first_publication, DBKey.DATE_FIRST_PUBLICATION)

                        .add(R.string.lbl_price_listed, DBKey.PRICE_LISTED)
                        .addRelatedField(DBKey.PRICE_LISTED, DBKey.PRICE_LISTED_CURRENCY)

                        .add(R.string.lbl_pages, DBKey.KEY_PAGES)
                        .add(R.string.lbl_format, DBKey.KEY_FORMAT)
                        .add(R.string.lbl_color, DBKey.KEY_COLOR)
                        .add(R.string.lbl_language, DBKey.KEY_LANGUAGE)
                        .add(R.string.lbl_genre, DBKey.KEY_GENRE);

        for (final SearchEngineConfig seConfig : SearchEngineRegistry.getInstance().getAll()) {
            final Domain domain = seConfig.getExternalIdDomain();
            if (domain != null) {
                builder.add(seConfig.getLabelId(), domain.getName(), SyncAction.Overwrite);
            }
        }

        return builder;
    }

    @NonNull
    Collection<SyncField> getFieldSyncList() {
        return mSyncProcessorBuilder.getFieldSyncList();
    }

    /**
     * Whether the user needs to be warned about lengthy download of covers.
     *
     * @return {@code true} if a dialog should be shown
     */
    boolean isShowWarningAboutCovers() {

        // Less than (arbitrary) 10 books, don't check/warn needed.
        if (mBookIdList != null && mBookIdList.size() < 10) {
            return false;
        }

        // More than 10 books, check if the user wants ALL covers
        return mSyncProcessorBuilder.getSyncAction(DBKey.COVER_IS_USED[0]) == SyncAction.Overwrite;
    }

    /**
     * Update the covers {@link SyncAction}.
     * Does nothing if the field ws not actually added before.
     *
     * @param action to set
     */
    void setCoverSyncAction(@NonNull final SyncAction action) {
        mSyncProcessorBuilder.setSyncAction(DBKey.COVER_IS_USED[0], action);
        mSyncProcessorBuilder.setSyncAction(DBKey.COVER_IS_USED[1], action);
    }

    /**
     * Allows to set the 'lowest' Book id to start from.
     * See {@link BookDao#fetchFromIdOnwards(long)}
     *
     * @param fromBookIdOnwards the lowest book id to start from.
     *                          This allows to fetch a subset of the requested set.
     *                          Defaults to 0, i.e. the full set.
     */
    void setFromBookIdOnwards(final long fromBookIdOnwards) {
        mFromBookIdOnwards = fromBookIdOnwards;
    }

    /**
     * Write current settings to the user preferences.
     */
    void writePreferences() {
        mSyncProcessorBuilder.writePreferences();
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     */
    void resetPreferences() {
        mSyncProcessorBuilder.resetPreferences();
    }

    /**
     * Start a search.
     *
     * @param context Current context
     *
     * @return {@code true} if a search was started.
     */
    boolean startSearch(@NonNull final Context context) {

        mSyncProcessor = mSyncProcessorBuilder.build();

        mCurrentProgressCounter = 0;

        try {
            if (mBookIdList == null || mBookIdList.isEmpty()) {
                mCurrentCursor = mBookDao.fetchFromIdOnwards(mFromBookIdOnwards);
            } else {
                mCurrentCursor = mBookDao.fetchById(mBookIdList);
            }
            mCurrentCursorCount = mCurrentCursor.getCount();

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
            final int idCol = mCurrentCursor.getColumnIndex(DBKey.PK_ID);

            // loop/skip until we start a search for a book.
            while (mCurrentCursor.moveToNext() && !mIsCancelled) {

                mCurrentProgressCounter++;

                //read the book ID
                mCurrentBookId = mCurrentCursor.getLong(idCol);

                // and populate the actual book based on the cursor data
                mCurrentBook.load(mCurrentBookId, mCurrentCursor);

                // Check which fields this book needs.
                //noinspection ConstantConditions
                mCurrentFieldsWanted = mSyncProcessor.filter(mCurrentBook);

                final String title = mCurrentBook.getString(DBKey.KEY_TITLE);

                if (!mCurrentFieldsWanted.isEmpty()) {
                    // remove all other criteria (this is CRUCIAL)
                    clearSearchCriteria();
                    boolean canSearch = false;

                    final String isbnStr = mCurrentBook.getString(DBKey.KEY_ISBN);
                    if (!isbnStr.isEmpty()) {
                        setIsbnSearchText(isbnStr, true);
                        canSearch = true;
                    }

                    final Author author = mCurrentBook.getPrimaryAuthor();
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
                            final String value = mCurrentBook.getString(domain.getName());
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
                        final Publisher publisher = mCurrentBook.getPrimaryPublisher();
                        if (publisher != null) {
                            final String publisherName = publisher.getName();
                            if (!publisherName.isEmpty()) {
                                setPublisherSearchText(publisherName);
                            }
                        }

                        // optional: whether this is used will depend on SearchEngine/Preferences
                        final boolean[] fetchCovers = new boolean[2];
                        for (int cIdx = 0; cIdx < 2; cIdx++) {
                            fetchCovers[cIdx] = mCurrentFieldsWanted
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

        postSearch(null);
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

        if (!mIsCancelled && bookData != null && !bookData.isEmpty()) {
            //noinspection ConstantConditions
            final Book delta = mSyncProcessor.process(context, mCurrentBookId, mCurrentBook,
                                                      mCurrentFieldsWanted, bookData);
            if (delta != null) {
                try {
                    mBookDao.update(context, delta, 0);
                } catch (@NonNull final CoverStorageException | DaoWriteException e) {
                    // ignore, but log it.
                    Logger.error(TAG, e);
                }
            }
        }

        //update the counter, another one done.
        mSearchCoordinatorProgress.setValue(new ProgressMessage(
                R.id.TASK_ID_UPDATE_FIELDS, null,
                mCurrentProgressCounter, mCurrentCursorCount, null
        ));

        // On to the next book in the list.
        return nextBook(context);
    }


    /**
     * Cleanup up and report the final outcome.
     *
     * <ul>Callers:
     *      <li>when we've not started a search (for whatever reason, including we're all done)</li>
     *      <li>when an exception is thrown</li>
     *      <li>when we're cancelled</li>
     * </ul>
     *
     * @param e (optional) exception
     */
    private void postSearch(@Nullable final Exception e) {
        if (mCurrentCursor != null) {
            mCurrentCursor.close();
        }

        // Tell the SearchCoordinator we're done and it should clean up.
        setBaseMessage(null);
        super.cancel();

        // the last book id which was handled; can be used to restart the update.
        mFromBookIdOnwards = mCurrentBookId;

        final Bundle results = new Bundle();
        results.putLong(BKEY_LAST_BOOK_ID, mFromBookIdOnwards);

        // all books || a list of books || (single book && ) not cancelled
        if (mBookIdList == null || mBookIdList.size() > 1 || !mIsCancelled) {
            // One or more books were changed.
            // Technically speaking when doing a list of books, the task might have been
            // cancelled before the first book was done. We disregard this fringe case.
            results.putBoolean(Entity.BKEY_DATA_MODIFIED, true);

            // if applicable, pass the first book for repositioning the list on screen
            if (mBookIdList != null && !mBookIdList.isEmpty()) {
                results.putLong(DBKey.PK_ID, mBookIdList.get(0));
            }
        }

        if (e != null) {
            Logger.error(TAG, e);
            final FinishedMessage<Exception> message =
                    new FinishedMessage<>(R.id.TASK_ID_UPDATE_FIELDS, e);
            mListFailed.setValue(message);

        } else {
            final FinishedMessage<Bundle> message =
                    new FinishedMessage<>(R.id.TASK_ID_UPDATE_FIELDS, results);
            if (mIsCancelled) {
                mSearchCoordinatorCancelled.setValue(message);
            } else {
                mListFinished.setValue(message);
            }
        }
    }

    @Override
    public boolean cancel() {
        mIsCancelled = true;
        postSearch(null);
        return true;
    }
}
