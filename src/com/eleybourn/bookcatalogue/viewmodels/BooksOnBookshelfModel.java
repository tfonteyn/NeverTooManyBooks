package com.eleybourn.bookcatalogue.viewmodels;

import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.LinearLayoutManager;

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
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
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
     * Holder for all (semi)supported search criteria.
     * See {@link SearchCriteria} for more info.
     */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    private final List<String> mBookshelfNameList = new ArrayList<>();
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
                                           final BuilderHolder result,
                                           @Nullable final Exception e) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debugEnter(this, "onGetBookListTaskFinished",
                                          "success=" + success,
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

                        // do not copy the result.resultListCursor, as it might be null in which case we
                        // will use the old value in mListCursor
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
    public void init() {
        if (mDb != null) {
            return;
        }

        mDb = new DAO();
    }

    /**
     * NEVER close this database!
     *
     * @return the dao
     */
    public DAO getDb() {
        return mDb;
    }

    public void savePosition(final int topRow,
                             final int topRowOffset) {
        mTopRow = topRow;
        mTopRowOffset = topRowOffset;

        App.getPrefs().edit()
           .putInt(PREF_BOB_TOP_ROW, mTopRow)
           .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
           .apply();
    }

    @NonNull
    public SearchCriteria getSearchCriteria() {
        return mSearchCriteria;
    }

    @Nullable
    public BooklistPseudoCursor getListCursor() {
        return mListCursor;
    }

    public void setListCursor(@NonNull final BooklistPseudoCursor listCursor) {
        mListCursor = listCursor;
    }

    public void setRebuildState(final int rebuildState) {
        mRebuildState = rebuildState;
    }

    public int getTopRow() {
        return mTopRow;
    }

    public void setTopRow(final int topRow) {
        mTopRow = topRow;
    }

    public int getTopRowOffset() {
        return mTopRowOffset;
    }

    public void setTopRowOffset(final int topRowOffset) {
        mTopRowOffset = topRowOffset;
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

    public void setCurrentBookshelf(@NonNull final Resources resources,
                                    @NonNull final String selected) {
        mCurrentBookshelf = mDb.getBookshelfByName(selected);
        // make sure the shelf exists.
        if (mCurrentBookshelf == null) {
            // shelf must have been deleted, switch to 'all book'
            mCurrentBookshelf = Bookshelf.getAllBooksBookshelf(resources, mDb);
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

    public List<String> getBookshelfNameList() {
        return mBookshelfNameList;
    }

    public int initBookshelfNameList(@NonNull final Resources resources) {
        mBookshelfNameList.clear();
        mBookshelfNameList.add(resources.getString(R.string.bookshelf_all_books));
        // default to 'All Books'
        int currentPos = 0;
        // start at 1, as position 0 is 'All Books'
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.getId() == mCurrentBookshelf.getId()) {
                currentPos = position;
            }
            position++;
            mBookshelfNameList.add(bookshelf.getName());
        }

        return currentPos;
    }

    @Nullable
    public Boolean isForceRebuild() {
        return mAfterOnActivityResultDoFullRebuild;
    }

    public void setForceRebuild(@Nullable final Boolean rebuild) {
        mAfterOnActivityResultDoFullRebuild = rebuild;
    }

    public boolean hasListBeenLoaded() {
        return mListHasBeenLoaded;
    }

    public void setCurrentPositionedBookId(final long currentPositionedBookId) {
        mCurrentPositionedBookId = currentPositionedBookId;
    }

    public int getLastTopRow() {
        return mLastTopRow;
    }

    public void setLastTopRow(final int lastTopRow) {
        mLastTopRow = lastTopRow;
    }

    public int getTotalBooks() {
        return mTotalBooks;
    }

    public int getUniqueBooks() {
        return mUniqueBooks;
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    public void initBookList(@SuppressWarnings("ParameterCanBeLocal") boolean isFullRebuild,
                             @NonNull final Resources resources) {

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
            bookListBuilder = new BooklistBuilder(resources, mCurrentBookshelf.getStyle(mDb));

            bookListBuilder.requireDomain(DBDefinitions.DOM_TITLE,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                                          true);

            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_READ,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
                                          false);

            if (mSearchCriteria.hasIdList()) {
                // if we have a list of id's, ignore other criteria.
                // Meant to display the results from FTSSearchActivity directly.
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

    /**
     * The result of {@link GetBookListTask}
     *
     * @return a BuilderHolder with result fields populated.
     */
    public MutableLiveData<BuilderHolder> getBuilderResult() {
        return mBuilderResult;
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
        ArrayList<Integer> bookList;

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

        public void from(@NonNull final Bundle bundle) {
            clear();
            setKeywords(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            ftsAuthor = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            ftsTitle = bundle.getString(DBDefinitions.KEY_TITLE);
            series = bundle.getString(DBDefinitions.KEY_SERIES);
            loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            bookList = bundle.getIntegerArrayList(UniqueId.BKEY_ID_LIST);
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

        /**
         * @param outState from a #onSaveInstanceState
         */
        public void to(final Bundle outState) {
            outState.putString(UniqueId.BKEY_SEARCH_TEXT, ftsKeywords);
            outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, ftsAuthor);
            outState.putString(DBDefinitions.KEY_TITLE, ftsTitle);
            outState.putString(DBDefinitions.KEY_SERIES, series);
            outState.putString(DBDefinitions.KEY_LOANEE, loanee);
            outState.putIntegerArrayList(UniqueId.BKEY_ID_LIST, bookList);
        }

        public boolean isEmpty() {
            return (ftsKeywords == null || ftsKeywords.isEmpty())
                    && (ftsAuthor == null || ftsAuthor.isEmpty())
                    && (ftsTitle == null || ftsTitle.isEmpty())
                    && (series == null || series.isEmpty())
                    && (loanee == null || loanee.isEmpty())
                    && (bookList == null || bookList.isEmpty());
        }

        public boolean hasIdList() {
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
        /** used to determine new cursor position. */
        @Nullable
        private ArrayList<BooklistBuilder.BookRowInfo> targetRows;

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

        /** Try to sync the previously selected book ID. */
        private void syncPreviouslySelectedBookId() {

            if (mHolder.currentPositionedBookId != 0) {
                // get all positions of the book
                targetRows = mBooklistBuilder
                        .getBookAbsolutePositions(mHolder.currentPositionedBookId);

                if (targetRows != null && !targetRows.isEmpty()) {
                    // First, get the ones that are currently visible...
                    ArrayList<BooklistBuilder.BookRowInfo> visRows = new ArrayList<>();
                    for (BooklistBuilder.BookRowInfo i : targetRows) {
                        if (i.visible) {
                            visRows.add(i);
                        }
                    }
                    // If we have any visible rows, only consider them for the new position
                    if (!visRows.isEmpty()) {
                        targetRows = visRows;
                    } else {
                        // Make them ALL visible
                        for (BooklistBuilder.BookRowInfo rowInfo : targetRows) {
                            if (!rowInfo.visible) {
                                mBooklistBuilder.ensureAbsolutePositionVisible(
                                        rowInfo.absolutePosition);
                            }
                        }
                        // Recalculate all positions
                        for (BooklistBuilder.BookRowInfo rowInfo : targetRows) {
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
            } else {
                targetRows = null;
            }
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

                syncPreviouslySelectedBookId();

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
                mHolder.resultTargetRows = targetRows;

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
                mTaskListener.get().onTaskCancelled(mTaskId, result, mException);
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
                Logger.debug(this, "cleanup", "exception=" + mException);
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
        protected void onPostExecute(@Nullable final BuilderHolder result) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "onPostExecute",
                             "result=" + result,
                             "mTaskListener.get()=" + mTaskListener.get());
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
