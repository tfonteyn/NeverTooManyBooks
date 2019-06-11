package com.eleybourn.bookcatalogue.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.FTSSearchActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.tasks.TaskListener;

/**
 * First attempt to split of into a model for BoB.
 */
public class BooksOnBookshelfModel
        extends ViewModel {

    /** Preference name - Saved position of last top row. */
    public static final String PREF_BOB_TOP_ROW = "BooksOnBookshelf.TopRow";
    /** Preference name - Saved position of last top row offset from view top. */
    public static final String PREF_BOB_TOP_ROW_OFFSET = "BooksOnBookshelf.TopRowOffset";
    /**
     * Set to true to enable true rebuild for debugging. See {@link #initBookList}.
     */
    private static final boolean __DEBUG_THE_REBUILD_ISSUE = false;

    /** The result of building the booklist. */
    private final MutableLiveData<BuilderHolder> mBuilderResult = new MutableLiveData<>();
    /**
     * Holder for all search criteria.
     * See {@link SearchCriteria} for more info.
     */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    /** Database access. */
    private DAO mDb;
    /**
     * Flag (potentially) set in {@link BooksOnBookshelf}#onActivityResult}.
     * Indicates if list rebuild is needed.
     */
    @Nullable
    private Boolean mAfterOnActivityResultDoFullRebuild;
    /** Flag to indicate that a list has been successfully loaded. Affects the way we save state. */
    private boolean mListHasBeenLoaded;
    /** Stores the book id for the current list position, e.g. while a book is viewed/edited. */
    private long mCurrentPositionedBookId;
    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTopRow = -1;
    /** Preferred booklist state in next rebuild. */
    private int mRebuildState;
    /** Current displayed list cursor. */
    @Nullable
    private BooklistPseudoCursor mListCursor;
    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks;
    /** Total number of unique books in current list. */
    private int mUniqueBooks;
    /**
     * Listener for {@link GetBookListTask} results.
     */
    private final TaskListener<Object, BuilderHolder> mOnGetBookListTaskListener =
            new TaskListener<Object, BuilderHolder>() {

                @Override
                public void onTaskFinished(final int taskId,
                                           final boolean success,
                                           @NonNull final BuilderHolder result,
                                           @Nullable final Exception e) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debugEnter(this, "onGetBookListTaskFinished",
                                          "success=" + success,
                                          "result=" + result,
                                          "exception=" + e);
                    }
                    // Save a flag to say list was loaded at least once successfully (or not)
                    mListHasBeenLoaded = success;

                    if (mListHasBeenLoaded) {
                        // always copy modified fields.
                        mCurrentPositionedBookId = result.currentPositionedBookId;
                        mRebuildState = result.rebuildState;

                        // always copy these results
                        mTotalBooks = result.resultTotalBooks;
                        mUniqueBooks = result.resultUniqueBooks;

                        // do not copy the result.resultListCursor, as it might be null
                        // in which case we will use the old value in mListCursor
                    }

                    // always call back, even if there is no new list.
                    mBuilderResult.setValue(result);
                }
            };
    /** Saved position of top row. */
    private int mTopRow;
    /**
     * Saved position of last top row offset from view top.
     * <p>
     * See {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    private int mTopRowOffset;
    /** Currently selected bookshelf. */
    private Bookshelf mCurrentBookshelf;

    @Override
    protected void onCleared() {

        if (mListCursor != null) {
            mListCursor.getBuilder().close();
            mListCursor.close();
            mListCursor = null;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            TrackedCursor.dumpCursors();
        }

        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     *
     */
    public void init(@Nullable final Bundle extras,
                     @Nullable final Bundle savedInstanceState) {

        if (mDb == null) {
            mDb = new DAO();
        }

        Bundle args = savedInstanceState != null ? savedInstanceState : extras;

        if (args == null) {
            // Get preferred booklist state to use from preferences;
            // always do this here in init, as the prefs might have changed anytime.
            mRebuildState = BooklistBuilder.getPreferredListRebuildState();
        } else {
            // Always preserve state when rebuilding/recreating etc
            mRebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
        }

        // Restore list position on bookshelf
        mTopRow = App.getPrefs().getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = App.getPrefs().getInt(PREF_BOB_TOP_ROW_OFFSET, 0);

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;

        // search criteria come only from the intent - if any.
        if (extras != null) {
            mSearchCriteria.from(extras, true);
        }
    }

    /**
     * NEVER close this database!
     *
     * @return the dao
     */
    public DAO getDb() {
        return mDb;
    }

    public String debugBuilderTables() {
        if (mListCursor != null) {
            return mListCursor.getBuilder().debugInfoForTables();
        } else {
            return "no cursor";
        }
    }


    public Bookshelf getCurrentBookshelf() {
        return mCurrentBookshelf;
    }

    public void setCurrentBookshelf(@NonNull final Bookshelf currentBookshelf) {
        mCurrentBookshelf = currentBookshelf;
    }

    public void setCurrentBookshelf(final long id) {
        mCurrentBookshelf = mDb.getBookshelf(id);
    }

    public void setCurrentBookshelf(@NonNull final Context context,
                                    @NonNull final String selected) {
        mCurrentBookshelf = mDb.getBookshelfByName(selected);
        // make sure the shelf exists.
        if (mCurrentBookshelf == null) {
            // shelf must have been deleted, switch to 'all book'
            mCurrentBookshelf = Bookshelf.getAllBooksBookshelf(context, mDb);
        }
        // and make it the new default
        mCurrentBookshelf.setAsPreferred();
    }

    @NonNull
    public BooklistStyle getCurrentStyle() {
        return mCurrentBookshelf.getStyle(mDb);
    }

    public void setCurrentStyle(@NonNull final BooklistStyle style) {
        mCurrentBookshelf.setStyle(mDb, style);
    }

    /**
     * Save current position information in the preferences, including view nodes that are expanded.
     * We do this to preserve this data across application shutdown/startup.
     *
     * <p>
     * ENHANCE: Handle positions a little better when books are deleted.
     * <p>
     * Deleting a book by 'n' authors from the last author in list results in the list decreasing
     * in length by, potentially, n*2 items. The current code will return to the old position
     * in the list after such an operation...which will be too far down.
     *
     * @param topRow the position of the top visible row in the list
     */
    public void savePosition(final int topRow,
                             @NonNull final RecyclerView listView) {
        if (mListHasBeenLoaded) {
            setTopRow(topRow, listView);
        }
    }

    private void savePosition() {
        App.getPrefs().edit()
           .putInt(PREF_BOB_TOP_ROW, mTopRow)
           .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
           .apply();
    }

    /**
     * Set the style and position.
     *
     * @param style    that was selected
     * @param topRow   the top row to store
     * @param listView used to derive the top row offset
     */
    public void onStyleChanged(@NonNull final BooklistStyle style,
                               final int topRow,
                               @NonNull final RecyclerView listView) {

        // save the new bookshelf/style combination
        mCurrentBookshelf.setAsPreferred();
        mCurrentBookshelf.setStyle(mDb, style);

        // Set the rebuild state like this is the first time in, which it sort of is,
        // given we are changing style.
        mRebuildState = BooklistBuilder.getPreferredListRebuildState();

        /* There is very little ability to preserve position when going from
         * a list sorted by author/series to on sorted by unread/addedDate/publisher.
         * Keeping the current row/pos is probably the most useful thing we can
         * do since we *may* come back to a similar list.
         */
        setTopRow(topRow, listView);
    }

    /**
     * @param topRow   the top row to store
     * @param listView used to derive the top row offset
     */
    private void setTopRow(final int topRow,
                           @Nullable final RecyclerView listView) {
        mTopRow = topRow;
        if (listView != null) {
            View topView = listView.getChildAt(0);
            if (topView != null) {
                mTopRowOffset = topView.getTop();
            } else {
                mTopRowOffset = 0;
            }
        }
        savePosition();
    }

    public int getTopRow() {
        return mTopRow;
    }

    public void setTopRow(final int topRow) {
        setTopRow(topRow, null);
    }

    public int getTopRowOffset() {
        return mTopRowOffset;
    }

    public int getLastTopRow() {
        return mLastTopRow;
    }

    public void setLastTopRow(final int lastTopRow) {
        mLastTopRow = lastTopRow;
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     * @param context       NOT cached, only used to get locale strings
     */
    public void initBookList(@SuppressWarnings("ParameterCanBeLocal") boolean isFullRebuild,
                             @NonNull final Context context) {

        //FIXME: this is one from the original code. isFullRebuild=false is BROKEN.
        // basically all group headers are no longer in the TBL_BOOK_LIST.
        // See DatabaseDefinitions#TBL_BOOK_LIST for an example of the correct table content
        // After rebuild(false) all rows which don't show an expanded node are gone.
        //
        if (__DEBUG_THE_REBUILD_ISSUE) {
            Logger.debugWithStackTrace(this, "initBookList",
                                       "isFullRebuild=" + isFullRebuild);
        } else {
            isFullRebuild = true;
        }

        BooklistBuilder bookListBuilder;

        if (mListCursor != null && !isFullRebuild) {
            // use the current builder to re-query the underlying data
            bookListBuilder = mListCursor.getBuilder();

        } else {
            // get a new builder and add the required extra domains
            bookListBuilder = new BooklistBuilder(context, mCurrentBookshelf.getStyle(mDb));

            bookListBuilder.requireDomain(DBDefinitions.DOM_TITLE, DBDefinitions.DOM_TITLE_OB,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                                          true);

            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_READ, null,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
                                          false);

            if (mSearchCriteria.hasIdList()) {
                // if we have a list of id's, ignore other criteria.
                bookListBuilder.setFilterOnBookIdList(mSearchCriteria.bookList);

            } else {
                // always limit to the current bookshelf.
                bookListBuilder.setFilterOnBookshelfId(mCurrentBookshelf.getId());

                // Criteria supported by FTS
                bookListBuilder.setFilter(mSearchCriteria.ftsAuthor,
                                          mSearchCriteria.ftsTitle,
                                          mSearchCriteria.getKeywords());

                // non-FTS
                bookListBuilder.setFilterOnSeriesName(mSearchCriteria.series);
                bookListBuilder.setFilterOnLoanedToPerson(mSearchCriteria.loanee);
            }
        }

        new GetBookListTask(bookListBuilder, isFullRebuild,
                            mListCursor, mCurrentPositionedBookId, mRebuildState,
                            mOnGetBookListTaskListener)
                .execute();
    }

    public int getTotalBooks() {
        return mTotalBooks;
    }

    public int getUniqueBooks() {
        return mUniqueBooks;
    }

    /**
     * The result of {@link GetBookListTask}.
     *
     * @return a BuilderHolder with result fields populated.
     */
    public MutableLiveData<BuilderHolder> getBuilderResult() {
        return mBuilderResult;
    }

    @Nullable
    public BooklistPseudoCursor getListCursor() {
        return mListCursor;
    }

    public void setListCursor(@NonNull final BooklistPseudoCursor listCursor) {
        mListCursor = listCursor;
    }

    /**
     * @return {@code null} if no rebuild is requested;
     * {@code true} or {@code false} if we're requesting a full or partial rebuild.
     */
    @Nullable
    public Boolean isForceFullRebuild() {
        return mAfterOnActivityResultDoFullRebuild;
    }

    /**
     * Request a full or partial rebuild at the next onResume.
     *
     * @param fullRebuild {@code true} for a full rebuild; {@code false} for a partial rebuild;
     *                    {@code null} for no rebuild.
     */
    public void setFullRebuild(@Nullable final Boolean fullRebuild) {
        mAfterOnActivityResultDoFullRebuild = fullRebuild;
    }

    public void setCurrentPositionedBookId(final long currentPositionedBookId) {
        mCurrentPositionedBookId = currentPositionedBookId;
    }


    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @return formatted Author name
     */
    @Nullable
    public String getAuthorFromRow(@NonNull final BooklistCursorRow row) {
        if (row.hasAuthorId() && row.getAuthorId() > 0) {
            Author author = mDb.getAuthor(row.getAuthorId());
            if (author != null) {
                return author.getLabel();
            }

        } else if (row.getRowKind() == BooklistGroup.RowKind.BOOK) {
            List<Author> authors = mDb.getAuthorsByBookId(row.getBookId());
            if (!authors.isEmpty()) {
                return authors.get(0).getLabel();
            }
        }
        return null;
    }

    /**
     * @return the unformatted Series name (i.e. without the number)
     */
    @Nullable
    public String getSeriesFromRow(@NonNull final BooklistCursorRow row) {
        if (row.hasSeriesId() && row.getSeriesId() > 0) {
            Series series = mDb.getSeries(row.getSeriesId());
            if (series != null) {
                return series.getName();
            }
        } else if (row.getRowKind() == BooklistGroup.RowKind.BOOK) {
            ArrayList<Series> series = mDb.getSeriesByBookId(row.getBookId());
            if (!series.isEmpty()) {
                return series.get(0).getName();
            }
        }
        return null;
    }


    @NonNull
    public SearchCriteria getSearchCriteria() {
        return mSearchCriteria;
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     */
    public static class SearchCriteria {

        /**
         * List of bookId's to display. The RESULT of a search with {@link FTSSearchActivity}
         * which can be re-used for the builder.
         */
        @Nullable
        ArrayList<Long> bookList;

        /**
         * Author to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         */
        @Nullable
        String ftsAuthor;

        /**
         * Title to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         */
        @Nullable
        String ftsTitle;

        /**
         * Series to use in search query.
         * Supported in the builder, but not user-settable yet.
         */
        @Nullable
        String series;

        /**
         * Name of the person we loaned books to, to use in search query.
         * Supported in the builder, but not user-settable yet.
         */
        @Nullable
        String loanee;

        /**
         * Keywords to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         * <p>
         * Always use the setter as we need to intercept the "." character.
         */
        @Nullable
        private String ftsKeywords;

        public void clear() {
            ftsKeywords = null;
            ftsAuthor = null;
            ftsTitle = null;
            series = null;
            loanee = null;
            bookList = null;
        }


        @Nullable
        public String getKeywords() {
            return ftsKeywords;
        }

        public void setKeywords(@Nullable final String keywords) {
            if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
                ftsKeywords = null;
            } else {
                ftsKeywords = keywords;
            }
        }

        /**
         * Only copies the criteria which are set.
         * Criteria not set in the bundle, are preserved!
         *
         * @param bundle with criteria.
         */
        public void from(@NonNull final Bundle bundle,
                         final boolean clear) {
            if (clear) {
                clear();
            }

            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                setKeywords(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                ftsAuthor = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            }
            if (bundle.containsKey(DBDefinitions.KEY_TITLE)) {
                ftsTitle = bundle.getString(DBDefinitions.KEY_TITLE);
            }
            if (bundle.containsKey(DBDefinitions.KEY_SERIES)) {
                series = bundle.getString(DBDefinitions.KEY_SERIES);
            }
            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                //noinspection unchecked
                bookList = (ArrayList<Long>) (bundle.get(UniqueId.BKEY_ID_LIST));
            }
        }

        /**
         * @param intent which will be used for a #startActivityForResult call
         */
        public void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, ftsKeywords)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, ftsAuthor)
                  .putExtra(DBDefinitions.KEY_TITLE, ftsTitle)
                  .putExtra(DBDefinitions.KEY_SERIES, series)
                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)
                  .putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        public boolean isEmpty() {
            return (ftsKeywords == null || ftsKeywords.isEmpty())
                    && (ftsAuthor == null || ftsAuthor.isEmpty())
                    && (ftsTitle == null || ftsTitle.isEmpty())
                    && (series == null || series.isEmpty())
                    && (loanee == null || loanee.isEmpty())
                    && (bookList == null || bookList.isEmpty());
        }

        boolean hasIdList() {
            return bookList != null && !bookList.isEmpty();
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private static class GetBookListTask
            extends AsyncTask<Void, Void, BuilderHolder> {

        /**
         * Indicates whole table structure needs rebuild,
         * versus just do a reselect of underlying data.
         */
        private final boolean mIsFullRebuild;
        /** the builder. */
        @NonNull
        private final BooklistBuilder mBooklistBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderHolder mHolder;
        private final int mTaskId = R.id.TASK_ID_GET_BOOKLIST;
        @Nullable
        private final BooklistPseudoCursor mCurrentListCursor;
        private final WeakReference<TaskListener<Object, BuilderHolder>> mTaskListener;
        /**
         * {@link #doInBackground} should catch exceptions, and set this field.
         * {@link #onPostExecute} can then check it.
         */
        @Nullable
        protected Exception mException;
        /** Resulting Cursor. */
        private BooklistPseudoCursor tempListCursor;


        /**
         * Constructor.
         *
         * @param bookListBuilder         the builder
         * @param isFullRebuild           Indicates whole table structure needs rebuild,
         * @param currentListCursor       Current displayed list cursor.
         * @param currentPositionedBookId Current position in the list.
         * @param rebuildState            Requested list state (expanded,collapsed, preserved)
         * @param taskListener            TaskListener
         */
        @UiThread
        GetBookListTask(@NonNull final BooklistBuilder bookListBuilder,
                        final boolean isFullRebuild,
                        @Nullable final BooklistPseudoCursor currentListCursor,

                        final long currentPositionedBookId,
                        final int rebuildState,
                        final TaskListener<Object, BuilderHolder> taskListener) {

            mTaskListener = new WeakReference<>(taskListener);

            mBooklistBuilder = bookListBuilder;
            mIsFullRebuild = isFullRebuild;
            mCurrentListCursor = currentListCursor;

            // input/output fields for the task.
            mHolder = new BuilderHolder(currentPositionedBookId, rebuildState);
        }

        /**
         * Try to sync the previously selected book ID.
         *
         * @return the target rows, or {@code null} if none.
         */
        private ArrayList<BooklistBuilder.BookRowInfo> syncPreviouslySelectedBookId() {
            // no input, no output...
            if (mHolder.currentPositionedBookId == 0) {
                return null;
            }

            // get all positions of the book
            ArrayList<BooklistBuilder.BookRowInfo> rows = mBooklistBuilder
                    .getBookAbsolutePositions(mHolder.currentPositionedBookId);

            if (rows != null && !rows.isEmpty()) {
                // First, get the ones that are currently visible...
                ArrayList<BooklistBuilder.BookRowInfo> visibleRows = new ArrayList<>();
                for (BooklistBuilder.BookRowInfo rowInfo : rows) {
                    if (rowInfo.visible) {
                        visibleRows.add(rowInfo);
                    }
                }

                // If we have any visible rows, only consider those for the new position
                if (!visibleRows.isEmpty()) {
                    rows = visibleRows;
                } else {
                    // Make them ALL visible
                    for (BooklistBuilder.BookRowInfo rowInfo : rows) {
                        if (!rowInfo.visible) {
                            mBooklistBuilder.ensureAbsolutePositionVisible(
                                    rowInfo.absolutePosition);
                        }
                    }
                    // Recalculate all positions
                    for (BooklistBuilder.BookRowInfo rowInfo : rows) {
                        rowInfo.listPosition = mBooklistBuilder.getPosition(
                                rowInfo.absolutePosition);
                    }
                }
                // Find the nearest row to the recorded 'top' row.
//                        int targetRow = bookRows[0];
//                        int minDist = Math.abs(mModel.getTopRow() - b.getPosition(targetRow));
//                        for (int i = 1; i < bookRows.length; i++) {
//                            int pos = b.getPosition(bookRows[i]);
//                            int dist = Math.abs(mModel.getTopRow() - pos);
//                            if (dist < minDist) {
//                                targetRow = bookRows[i];
//                            }
//                        }
//                        // Make sure the target row is visible/expanded.
//                        b.ensureAbsolutePositionVisible(targetRow);
//                        // Now find the position it will occupy in the view
//                        mTargetPos = b.getPosition(targetRow);
            }
            return rows;
        }

        @Override
        @NonNull
        @WorkerThread
        protected BuilderHolder doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookListTask");
            try {
                long t0;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t0 = System.nanoTime();
                }
                // Build the underlying data
                if (mCurrentListCursor != null && !mIsFullRebuild) {
                    mBooklistBuilder.rebuild();
                } else {
                    mBooklistBuilder.build(mHolder.rebuildState, mHolder.currentPositionedBookId);
                    // After first build, always preserve this object state
                    mHolder.rebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
                }

                if (isCancelled()) {
                    return mHolder;
                }

                long t1;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t1 = System.nanoTime();
                }

                mHolder.resultTargetRows = syncPreviouslySelectedBookId();

                if (isCancelled()) {
                    return mHolder;
                }

                long t2;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t2 = System.nanoTime();
                }

                // Now we have the expanded groups as needed, get the list cursor
                tempListCursor = mBooklistBuilder.getNewListCursor();

                // Clear it so it won't be reused.
                mHolder.currentPositionedBookId = 0;

                long t3;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t3 = System.nanoTime();
                }
                // get a count() from the cursor in background task because the setAdapter() call
                // will do a count() and potentially block the UI thread while it pages through the
                // entire cursor. If we do it here, subsequent calls will be fast.
                int count = tempListCursor.getCount();

                long t4;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t4 = System.nanoTime();
                }
                // pre-fetch this count
                mHolder.resultUniqueBooks = tempListCursor.getUniqueBookCount();

                if (isCancelled()) {
                    return mHolder;
                }

                long t5;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t5 = System.nanoTime();
                }
                // pre-fetch this count
                mHolder.resultTotalBooks = tempListCursor.getBookCount();

                long t6;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t6 = System.nanoTime();
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    Logger.debug("doInBackground",
                                 "\n Build: " + (t1 - t0),
                                 "\n Position: " + (t2 - t1),
                                 "\n Select: " + (t3 - t2),
                                 "\n Count(" + count + "): " + (t4 - t3)
                                         + '/' + (t5 - t4) + '/' + (t6 - t5),
                                 "\n ====== ",
                                 "\n Total time: " + (t6 - t0) + "nano");
                }

                if (isCancelled()) {
                    return mHolder;
                }

                // Set the results.
                mHolder.resultListCursor = tempListCursor;

            } catch (RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                cleanup();
            }

            return mHolder;
        }

        @Override
        protected void onCancelled(@Nullable final BuilderHolder result) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "onCancelled",
                             "result=" + result,
                             "mTaskListener.get()=" + mTaskListener.get());
            }

            cleanup();

            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskCancelled(mTaskId);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onCancelled",
                                 "WeakReference to listener was dead");
                }
            }
        }

        @AnyThread
        private void cleanup() {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "cleanup",
                             "exception=" + mException);
            }
            if (tempListCursor != null && tempListCursor != mCurrentListCursor) {
                if (mCurrentListCursor == null
                        || (tempListCursor.getBuilder() != mCurrentListCursor.getBuilder())) {
                    tempListCursor.getBuilder().close();
                }
                tempListCursor.close();
            }
            tempListCursor = null;
        }

        /**
         * If the task was cancelled (by the user cancelling the progress dialog) then
         * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
         *
         * @param result of the task
         */
        @Override
        @UiThread
        protected void onPostExecute(@NonNull final BuilderHolder result) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "onPostExecute",
                             "result=" + result);
            }

            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /** value class for the Builder. */
    public static class BuilderHolder {

        /** input/output field. */
        long currentPositionedBookId;
        /** input/output field. */
        int rebuildState;

        /**
         * Resulting Cursor; can be {@code null} if the list did not get build.
         */
        @Nullable
        BooklistPseudoCursor resultListCursor;

        /**
         * output field. Pre-fetched from the resultListCursor.
         * {@link BooklistPseudoCursor#getBookCount()}
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultTotalBooks;
        /**
         * output field. Pre-fetched from the resultListCursor.
         * {@link BooklistPseudoCursor#getUniqueBookCount()}
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultUniqueBooks;

        /**
         * output field. Used to determine new cursor position; can be {@code null}.
         * Should be ignored if resultListCursor is {@code null}
         */
        @Nullable
        ArrayList<BooklistBuilder.BookRowInfo> resultTargetRows;

        /**
         * Constructor: these are the fields we need as input.
         */
        BuilderHolder(final long currentPositionedBookId,
                      final int rebuildState) {
            this.currentPositionedBookId = currentPositionedBookId;
            this.rebuildState = rebuildState;
        }

        @Nullable
        public BooklistPseudoCursor getResultListCursor() {
            return resultListCursor;
        }

        @Nullable
        public ArrayList<BooklistBuilder.BookRowInfo> getResultTargetRows() {
            return resultTargetRows;
        }

        @Override
        @NonNull
        public String toString() {
            return "BuilderHolder{"
                    + ", currentPositionedBookId=" + currentPositionedBookId
                    + ", rebuildState=" + rebuildState
                    + ", resultTotalBooks=" + resultTotalBooks
                    + ", resultUniqueBooks=" + resultUniqueBooks
                    + ", resultListCursor=" + resultListCursor
                    + ", resultTargetRows=" + resultTargetRows
                    + '}';
        }
    }
}
