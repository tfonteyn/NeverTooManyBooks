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
import java.util.Objects;

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
import com.eleybourn.bookcatalogue.database.cursors.BooklistMappedCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.Csv;

/**
 * First attempt to split of into a model for BoB.
 */
public class BooksOnBookshelfModel
        extends ViewModel {

    /** Preference name - Saved position of last top row. */
    public static final String PREF_BOB_TOP_ROW = "BooksOnBookshelf.TopRow";
    /** Preference name - Saved position of last top row offset from view top. */
    public static final String PREF_BOB_TOP_ROW_OFFSET = "BooksOnBookshelf.TopRowOffset";

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
    private Boolean mDoFullRebuildAfterOnActivityResult;

    /** Flag to indicate that a list has been successfully loaded. */
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
    @Nullable
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
     * Pseudo constructor.
     *
     * @param extras             Bundle with arguments from activity startup
     * @param savedInstanceState Bundle with arguments from activity waking up
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

        // search criteria (if any) can only come from the intent, not from savedInstanceState
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

    @NonNull
    public Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(mCurrentBookshelf);
        return mCurrentBookshelf;
    }

    /**
     * Load and set the desired Bookshelf; do NOT set it as the preferred.
     *
     * @param currentBookshelf to use
     */
    public void setCurrentBookshelf(@NonNull final Bookshelf currentBookshelf) {
        mCurrentBookshelf = currentBookshelf;
    }

    /**
     * Load and set the desired Bookshelf; do NOT set it as the preferred.
     *
     * @param id of Bookshelf
     */
    public void setCurrentBookshelf(final long id) {
        mCurrentBookshelf = mDb.getBookshelf(id);
    }

    /**
     * Load and set the desired Bookshelf as the preferred.
     *
     * @param context Current context
     * @param name    of desired Bookshelf
     */
    public void setCurrentBookshelf(@NonNull final Context context,
                                    @NonNull final String name) {

        mCurrentBookshelf = Bookshelf.getBookshelf(context, mDb, name, true);
        mCurrentBookshelf.setAsPreferred();
    }

    @NonNull
    public BooklistStyle getCurrentStyle() {
        Objects.requireNonNull(mCurrentBookshelf);
        return mCurrentBookshelf.getStyle(mDb);
    }

    public void setCurrentStyle(@NonNull final BooklistStyle style) {
        Objects.requireNonNull(mCurrentBookshelf);
        mCurrentBookshelf.setStyle(mDb, style);
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
        Objects.requireNonNull(mCurrentBookshelf);

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
        setAndSaveTopRow(topRow, listView);
    }

    public void setAndSaveTopRow(final int topRow) {
        setAndSaveTopRow(topRow, null);
    }

    /**
     * @param topRow   the top row to store
     * @param listView used to derive the top row offset
     */
    private void setAndSaveTopRow(final int topRow,
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
     * @param topRow   the position of the top visible row in the list
     * @param listView used to derive the top row offset
     */
    public void savePosition(final int topRow,
                             @NonNull final RecyclerView listView) {
        if (mListHasBeenLoaded) {
            setAndSaveTopRow(topRow, listView);
        }
    }

    private void savePosition() {
        App.getPrefs().edit()
           .putInt(PREF_BOB_TOP_ROW, mTopRow)
           .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
           .apply();
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param context       Current context, for accessing resources.
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    public void initBookList(@NonNull final Context context,
                             final boolean isFullRebuild) {
        Objects.requireNonNull(mCurrentBookshelf);

        BooklistBuilder bookListBuilder;

        if (mListCursor != null && !isFullRebuild) {
            // use the current builder to re-query the underlying data
            bookListBuilder = mListCursor.getBuilder();

        } else {
            // get a new builder and add the required extra domains
            bookListBuilder = new BooklistBuilder(context, mCurrentBookshelf.getStyle(mDb));

            // Title for displaying
            bookListBuilder.requireDomain(DBDefinitions.DOM_TITLE,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                                          false);

            // Title for sorting
            bookListBuilder.requireDomain(DBDefinitions.DOM_TITLE_OB,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE_OB),
                                          true);

            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_READ,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
                                          false);

            // external site ID's
            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_ISFDB_ID,
                                          DBDefinitions.TBL_BOOKS.dot(
                                                  DBDefinitions.DOM_BOOK_ISFDB_ID),
                                          false);
            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_GOODREADS_ID,
                                          DBDefinitions.TBL_BOOKS.dot(
                                                  DBDefinitions.DOM_BOOK_GOODREADS_ID),
                                          false);
            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_LIBRARY_THING_ID,
                                          DBDefinitions.TBL_BOOKS.dot(
                                                  DBDefinitions.DOM_BOOK_LIBRARY_THING_ID),
                                          false);
            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID,
                                          DBDefinitions.TBL_BOOKS.dot(
                                                  DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID),
                                          false);

            // if we have a list of ID's, ignore other criteria.
            if (mSearchCriteria.hasIdList()) {
                bookListBuilder.setFilterOnBookIdList(mSearchCriteria.bookList);

            } else {
                // always limit to the current bookshelf.
                bookListBuilder.setFilterOnBookshelfId(mCurrentBookshelf.getId());

                // Criteria supported by FTS
                bookListBuilder.setFilter(mSearchCriteria.ftsAuthor,
                                          mSearchCriteria.ftsTitle,
                                          mSearchCriteria.ftsKeywords);

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
        return mDoFullRebuildAfterOnActivityResult;
    }

    public boolean isListLoaded() {
        return mListHasBeenLoaded;
    }

    /**
     * Request a full or partial rebuild at the next onResume.
     *
     * @param fullRebuild {@code true} for a full rebuild; {@code false} for a partial rebuild;
     *                    {@code null} for no rebuild.
     */
    public void setFullRebuild(@Nullable final Boolean fullRebuild) {
        mDoFullRebuildAfterOnActivityResult = fullRebuild;
    }

    public void setCurrentPositionedBookId(final long currentPositionedBookId) {
        mCurrentPositionedBookId = currentPositionedBookId;
    }


    public boolean isAvailable(final long bookId) {
        String loanee = mDb.getLoaneeByBookId(bookId);
        return  (loanee == null) || loanee.isEmpty();
    }

    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @return formatted Author name
     */
    @Nullable
    public String getAuthorFromRow(@NonNull final BooklistMappedCursorRow row) {
        if (row.hasAuthorId()) {
            Author author = mDb.getAuthor(row.getLong(DBDefinitions.KEY_FK_AUTHOR));
            if (author != null) {
                return author.getLabel();
            }

        } else if (row.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND) == BooklistGroup.RowKind.BOOK) {
            List<Author> authors = mDb.getAuthorsByBookId(row.getLong(DBDefinitions.KEY_FK_BOOK));
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
    public String getSeriesFromRow(@NonNull final BooklistMappedCursorRow row) {
        if (row.hasSeriesId()) {
            Series series = mDb.getSeries(row.getLong(DBDefinitions.KEY_FK_SERIES));
            if (series != null) {
                return series.getName();
            }
        } else if (row.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND) == BooklistGroup.RowKind.BOOK) {
            ArrayList<Series> series = mDb.getSeriesByBookId(
                    row.getLong(DBDefinitions.KEY_FK_BOOK));
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

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        //noinspection ConstantConditions
        return mListCursor.getBuilder().getCurrentBookIdList();
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

        /**
         * Get a single string with all search words, for displaying.
         *
         * @return csv string, can be empty, never {@code null}.
         */
        @NonNull
        public String getDisplayString() {
            List<String> list = new ArrayList<>();

            if (ftsAuthor != null && !ftsAuthor.isEmpty()) {
                list.add(ftsAuthor);
            }
            if (ftsTitle != null && !ftsTitle.isEmpty()) {
                list.add(ftsTitle);
            }
            if (ftsKeywords != null && !ftsKeywords.isEmpty()) {
                list.add(ftsKeywords);
            }
            return Csv.join(",", list);
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
         * @param bundle     with criteria.
         * @param clearFirst Flag to force clearing all before loading the new criteria
         */
        public void from(@NonNull final Bundle bundle,
                         final boolean clearFirst) {
            if (clearFirst) {
                clear();
            }

            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                setKeywords(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                ftsAuthor = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TITLE)) {
                ftsTitle = bundle.getString(UniqueId.BKEY_SEARCH_TITLE);
            }

            if (bundle.containsKey(DBDefinitions.KEY_SERIES_TITLE)) {
                series = bundle.getString(DBDefinitions.KEY_SERIES_TITLE);
            }
            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                //noinspection unchecked
                bookList = (ArrayList<Long>) bundle.getSerializable(UniqueId.BKEY_ID_LIST);
            }
        }

        /**
         * @param intent which will be used for a #startActivityForResult call
         */
        public void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, ftsKeywords)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, ftsAuthor)
                  .putExtra(UniqueId.BKEY_SEARCH_TITLE, ftsTitle)

                  .putExtra(DBDefinitions.KEY_SERIES_TITLE, series)
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
        Exception mException;
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
                mHolder.resultUniqueBooks = tempListCursor.getBuilder().getUniqueBookCount();

                if (isCancelled()) {
                    return mHolder;
                }

                long t5;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t5 = System.nanoTime();
                }
                // pre-fetch this count
                mHolder.resultTotalBooks = tempListCursor.getBuilder().getBookCount();

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

            } catch (@NonNull final RuntimeException e) {
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
                        || (!tempListCursor.getBuilder().equals(mCurrentListCursor.getBuilder()))) {
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
         * output field. Pre-fetched from the resultListCursor's builder.
         * {@link BooklistBuilder#getBookCount()}
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultTotalBooks;
        /**
         * output field. Pre-fetched from the resultListCursor's builder.
         * {@link BooklistBuilder#getUniqueBookCount()}
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
