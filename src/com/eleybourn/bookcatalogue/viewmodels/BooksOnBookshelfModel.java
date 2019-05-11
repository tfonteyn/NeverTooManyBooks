package com.eleybourn.bookcatalogue.viewmodels;

import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ListView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.tasks.OnTaskListener;

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
    private SearchCriteria mSearchCriteria;
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
    private BooklistPseudoCursor mListCursor;

    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks;

    /** Total number of unique books in current list. */
    private int mUniqueBooks;

    /** Saved position of top row. */
    private int mTopRow;

    /**
     * Saved position of last top row offset from view top.
     * <p>
     * {@link ListView#setSelectionFromTop(int position, int y)} :
     * * @param y The distance from the top edge of the ListView (plus padding) that the
     * *        item will be positioned.
     */
    private int mTopRowOffset;

    /** Currently selected bookshelf. */
    private Bookshelf mCurrentBookshelf;

    @Override
    protected void onCleared() {
        if (BuildConfig.DEBUG) {
            Logger.debug(this, "onCleared");
        }
    }

    /**
     * @param args Bundle savedInstance/Extras
     */
    public void init(@Nullable final Bundle args) {
        if (args == null) {
            // Get preferred booklist state to use from preferences;
            // always do this here in init, as the prefs might have changed anytime.
            mRebuildState = BooklistBuilder.getListRebuildState();
        } else {
            // Always preserve state when rebuilding/recreating etc
            mRebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
        }

        // Restore list position on bookshelf
        mTopRow = App.getPrefs().getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = App.getPrefs().getInt(PREF_BOB_TOP_ROW_OFFSET, 0);

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;


        //============  anything below, only do once at real init time. =====================

        if (mSearchCriteria != null) {
            // already initialized.
            return;
        }
        mSearchCriteria = new SearchCriteria();
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

    public void setCurrentBookshelf(final Bookshelf currentBookshelf) {
        mCurrentBookshelf = currentBookshelf;
    }

    @Nullable
    public Boolean getAfterOnActivityResultForceRebuild() {
        return mAfterOnActivityResultDoFullRebuild;
    }

    public void setAfterOnActivityResultDoFullRebuild(@Nullable final Boolean rebuild) {
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
    public void initBookList(final boolean isFullRebuild,
                             @NonNull final Resources resources,
                             @NonNull final DAO db) {

        //FIXME: this is one from the original code. isFullRebuild=false is BROKEN.
        // basically all group headers are no longer in the TBL_BOOK_LIST.
        // See DatabaseDefinitions#TBL_BOOK_LIST for an example of the correct table content
        // After rebuild(false) all rows which don't show an expanded node are gone.
        //
        boolean fullRebuild;
        if (__DEBUG_THE_REBUILD_ISSUE) {
            fullRebuild = isFullRebuild;
        } else {
            fullRebuild = true;
        }

        BooklistBuilder bookListBuilder;

        if (mListCursor != null && !fullRebuild) {
            // use the current builder to re-query the underlying data
            bookListBuilder = mListCursor.getBuilder();

        } else {
            // get a new builder and add the required extra domains
            bookListBuilder = new BooklistBuilder(resources, mCurrentBookshelf.getStyle(db));

            bookListBuilder.requireDomain(DBDefinitions.DOM_TITLE,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                                          true);

            bookListBuilder.requireDomain(DBDefinitions.DOM_BOOK_READ,
                                          DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
                                          false);
        }

        // always limit to one bookshelf.
        bookListBuilder.setFilterOnBookshelfId(mCurrentBookshelf.getId());

        // Use current criteria
        bookListBuilder.setFilterOnText(mSearchCriteria.getText());
        bookListBuilder.setFilterOnTitle(mSearchCriteria.title);
        bookListBuilder.setFilterOnAuthorName(mSearchCriteria.author);
        bookListBuilder.setFilterOnSeriesName(mSearchCriteria.series);
        bookListBuilder.setFilterOnLoanedToPerson(mSearchCriteria.loanee);

        bookListBuilder.setFilterOnBookIdList(mSearchCriteria.bookList);


        BuilderHolder builderHolder = new BuilderHolder(mListCursor, mCurrentPositionedBookId,
                                                        mRebuildState);

        new GetBookListTask(builderHolder, bookListBuilder, isFullRebuild, mOnGetBookListTaskFinished)
                .execute();
    }

    /**
     * Listener for {@link GetBookListTask} results.
     */
    private final OnTaskListener<Object, BuilderHolder> mOnGetBookListTaskFinished = new OnTaskListener<Object, BuilderHolder>() {
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
     * <p>
     * All individual criteria are supported by the {@link BooklistBuilder},
     * but not necessarily in {@link BooksOnBookshelf}.
     * <p>
     * Only some are supported by
     * {@link com.eleybourn.bookcatalogue.entities.Book}#onSearchRequested()}.
     */
    public static class SearchCriteria {

        /**
         * Author to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        public
        String author = "";
        /**
         * Title to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        public
        String title = "";
        /**
         * Series to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        public
        String series = "";
        /**
         * Name of the person we loaned books to, to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @SuppressWarnings("WeakerAccess")
        @Nullable
        public
        String loanee = "";
        /**
         * List of bookId's to display. The result of a search.
         */
        @SuppressWarnings("WeakerAccess")
        @Nullable
        public
        ArrayList<Integer> bookList;
        /**
         * Text to use in search query.
         * <p>
         * Always use the setter!
         */
        @NonNull
        private String mText = "";

        public void clear() {
            mText = "";
            author = "";
            title = "";
            series = "";
            loanee = "";
            bookList = null;
        }

        @NonNull
        public String getText() {
            return mText;
        }

        public void setText(@Nullable final String text) {
            if (text == null || text.isEmpty() || ".".equals(text)) {
                mText = "";
            } else {
                mText = text;
            }
        }

        public void from(@NonNull final Bundle bundle) {
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                setText(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                author = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            }
            if (bundle.containsKey(DBDefinitions.KEY_TITLE)) {
                title = bundle.getString(DBDefinitions.KEY_TITLE);
            }
            if (bundle.containsKey(DBDefinitions.KEY_SERIES)) {
                series = bundle.getString(DBDefinitions.KEY_SERIES);
            }
            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                bookList = bundle.getIntegerArrayList(UniqueId.BKEY_ID_LIST);
            }
        }

        /**
         * @param intent which will be used for a #startActivityForResult call
         */
        public void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, mText)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, author)
                  .putExtra(DBDefinitions.KEY_TITLE, title)
                  .putExtra(DBDefinitions.KEY_SERIES, series)
                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)
                  .putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        /**
         * @param outState from a #onSaveInstanceState
         */
        public void to(final Bundle outState) {
            outState.putString(UniqueId.BKEY_SEARCH_TEXT, mText);
            outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, author);
            outState.putString(DBDefinitions.KEY_TITLE, title);
            outState.putString(DBDefinitions.KEY_SERIES, series);
            outState.putString(DBDefinitions.KEY_LOANEE, loanee);
            outState.putIntegerArrayList(UniqueId.BKEY_ID_LIST, bookList);
        }

        public boolean isEmpty() {
            return mText.isEmpty()
                    && (author == null || author.isEmpty())
                    && (title == null || title.isEmpty())
                    && (series == null || series.isEmpty())
                    && (loanee == null || loanee.isEmpty())
                    && (bookList == null || bookList.isEmpty());
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private static class GetBookListTask
            extends AsyncTask<Void, Object, BuilderHolder> {

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
        private final WeakReference<OnTaskListener<Object, BuilderHolder>> mListener;
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
         * @param builderHolder   holder class with input fields / results.
         * @param bookListBuilder the builder
         * @param isFullRebuild   Indicates whole table structure needs rebuild,
         * @param listener        OnTaskListener
         */
        @UiThread
        private GetBookListTask(@NonNull final BuilderHolder builderHolder,
                                @NonNull final BooklistBuilder bookListBuilder,
                                final boolean isFullRebuild,
                                @NonNull final OnTaskListener<Object, BuilderHolder> listener) {

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "constructor", "mIsFullRebuild=" + isFullRebuild);
            }

            mListener = new WeakReference<>(listener);
            mIsFullRebuild = isFullRebuild;
            mBooklistBuilder = bookListBuilder;
            mHolder = builderHolder;
        }

        /** Try to sync the previously selected book ID. */
        private void syncPreviouslySelectedBookId() {

            if (mHolder.currentPositionedBookId != 0) {
                // get all positions of the book
                targetRows = mBooklistBuilder
                        .getBookAbsolutePositions(mHolder.currentPositionedBookId);

                if (targetRows != null && !targetRows.isEmpty()) {
                    // First, get the ones that are currently visible... 30 should do in most cases.
                    ArrayList<BooklistBuilder.BookRowInfo> visRows = new ArrayList<>(30);
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
            try {
                long t0;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t0 = System.nanoTime();
                }
                // Build the underlying data
                if (mHolder.oldListCursor != null && !mIsFullRebuild) {
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
                tempListCursor = mBooklistBuilder.getListCursor();
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
                mHolder.resultUniqueBooks = tempListCursor.getUniqueBookCount();

                if (isCancelled()) {
                    return mHolder;
                }

                long t5;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t5 = System.nanoTime();
                }
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
                                           "mListener.get()=" + mListener.get());
            }

            cleanup();

            if (mListener.get() != null) {
                mListener.get().onTaskCancelled(mTaskId, result, mException);
            }
        }

        @AnyThread
        private void cleanup() {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "cleanup",
                             "exception=" + mException);
            }
            if (tempListCursor != null && tempListCursor != mHolder.oldListCursor) {
                if (mHolder.oldListCursor == null
                        || (tempListCursor.getBuilder() != mHolder.oldListCursor.getBuilder())) {
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
                Logger.debugWithStackTrace(this, "onPostExecute",
                                           "result=" + result,
                                           "mListener.get()=" + mListener.get());
            }

            if (mListener.get() != null) {
                mListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
            } else {
                throw new RuntimeException("WeakReference to listener was dead");
            }
        }
    }

    /** value class for the Builder. */
    public static class BuilderHolder {

        /** input field. */
        final BooklistPseudoCursor oldListCursor;

        /** input/output field. */
        long currentPositionedBookId;
        /** input/output field. */
        int rebuildState;

        /** output field. */
        int resultTotalBooks;
        /** output field. */
        int resultUniqueBooks;

        /** Resulting Cursor; can be {@code null} if the list did not get build. */
        @Nullable
        BooklistPseudoCursor resultListCursor;
        /** used to determine new cursor position; can be {@code null}. */
        @Nullable
        ArrayList<BooklistBuilder.BookRowInfo> resultTargetRows;

        /**
         * Constructor: these are the fields we need as input.
         */
        BuilderHolder(@NonNull final BooklistPseudoCursor oldListCursor,
                      final long currentPositionedBookId,
                      final int rebuildState) {
            this.currentPositionedBookId = currentPositionedBookId;
            this.oldListCursor = oldListCursor;
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
                    + "oldListCursor=" + oldListCursor
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
