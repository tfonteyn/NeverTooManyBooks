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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FTSSearchActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistPseudoCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.database.cursors.TrackedCursor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * First attempt to split of into a model for BoB.
 */
public class BooksOnBookshelfModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfModel";
    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";

    private static final String PREF_PREFIX = "booklist.";

    /** Preference name - Saved position of last top row. */
    private static final String PREF_BOB_TOP_ROW = PREF_PREFIX + "top.row";
    /** Preference name - Saved position of last top row offset from view top. */
    private static final String PREF_BOB_TOP_ROW_OFFSET = PREF_PREFIX + "top.offset";
    /** The result of building the booklist. */
    private final MutableLiveData<BuilderHolder> mBuilderResult = new MutableLiveData<>();
    /** Allows progress message from a task to update the user. */
    private final MutableLiveData<String> mUserMessage = new MutableLiveData<>();
    /** Inform user that Goodreads needs authentication/authorization. */
    private final MutableLiveData<Boolean> mNeedsGoodreads = new MutableLiveData<>();

    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    /** Cache for all bookshelf names / spinner list. */
    private final List<String> mBookshelfNameList = new ArrayList<>();

    /**
     * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_CSV} when
     * NOT using a background task for the extras.
     */
    private final String BOOKSHELVES_CSV_SOURCE_EXPRESSION =
            "("
            + "SELECT GROUP_CONCAT("
            + DBDefinitions.TBL_BOOKSHELF.dot(DBDefinitions.DOM_BOOKSHELF) + ",', ')"
            + " FROM "
            + DBDefinitions.TBL_BOOKSHELF.ref()
            + DBDefinitions.TBL_BOOKSHELF.join(DBDefinitions.TBL_BOOK_BOOKSHELF)
            + " WHERE "
            + DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_PK_ID) + "="
            + DBDefinitions.TBL_BOOK_BOOKSHELF.dot(DBDefinitions.DOM_FK_BOOK)
            + ")";

    /** Database Access. */
    private DAO mDb;
    /** Lazy init, always use {@link #getGoodreadsTaskListener()}. */
    private TaskListener<Integer> mGoodreadsTaskListener;
    /**
     * Flag (potentially) set in {@link BooksOnBookshelf} #onActivityResult.
     * Indicates if list rebuild is needed in {@link BooksOnBookshelf}#onResume.
     */
    private boolean mForceRebuildInOnResume;
    /** Flag to indicate that a list has been successfully loaded. */
    private boolean mListHasBeenLoaded;
    /** Currently selected bookshelf. */
    @Nullable
    private Bookshelf mCurrentBookshelf;
    /**
     * Stores the book id for the current list position, e.g. while a book is viewed/edited.
     * Gets set after the {@link GetBookListTask} finishes and after edit/view.
     */
    private long mCurrentPositionedBookId;

    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTopRow = -1;
    /** Saved position of top row. */
    private int mTopRow;
    /**
     * Saved position of last top row offset from view top.
     * <p>
     * See {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    private int mTopRowOffset;
    /** Preferred booklist state in next rebuild. */
    @BooklistBuilder.ListRebuildMode
    private int mRebuildState;
    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks;
    /** Total number of unique books in current list. */
    private int mUniqueBooks;
    /**
     * Listener for {@link GetBookListTask} results.
     */
    private final TaskListener<BuilderHolder> mOnGetBookListTaskListener =
            new TaskListener<BuilderHolder>() {
                @Override
                public void onFinished(@NonNull final FinishMessage<BuilderHolder> message) {
                    // Save a flag to say list was loaded at least once successfully (or not)
                    mListHasBeenLoaded = message.status == TaskStatus.Success;

                    if (mListHasBeenLoaded) {
                        // always copy modified fields.
                        mCurrentPositionedBookId = message.result.currentPosBookId;
                        mRebuildState = message.result.listState;

                        // always copy these results
                        mTotalBooks = message.result.resultTotalBookCount;
                        mUniqueBooks = message.result.resultDistinctBookCount;

                        // do not copy the result.resultListCursor, as it might be null
                        // in which case we will use the old value
                    }

                    // always call back, even if there is no new list.
                    mBuilderResult.setValue(message.result);
                }
            };
    /** Current displayed list cursor. */
    @Nullable
    private BooklistPseudoCursor mCursor;

    @Override
    protected void onCleared() {

        if (mCursor != null) {
            mCursor.getBuilder().close();
            mCursor.close();
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
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args,
                     @Nullable final Bundle savedInstanceState) {

        if (mDb == null) {
            mDb = new DAO(TAG);

            if (args != null) {
                mSearchCriteria.from(args, true);
            }
        }

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : args;

        if (currentArgs == null) {
            // Get preferred booklist state to use from preferences;
            // always do this here in init, as the prefs might have changed anytime.
            mRebuildState = BooklistBuilder.getPreferredListRebuildState();

        } else {
            // Unless set by the caller, preserve state when rebuilding/recreating etc
            mRebuildState = currentArgs.getInt(BKEY_LIST_STATE,
                                               BooklistBuilder.PREF_LIST_REBUILD_SAVED_STATE);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Restore list position on bookshelf
        mTopRow = prefs.getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = prefs.getInt(PREF_BOB_TOP_ROW_OFFSET, 0);
    }

    /**
     * NEVER close this database.
     *
     * @return the DAO
     */
    public DAO getDb() {
        return mDb;
    }

    @NonNull
    public List<String> getBookshelfNameList() {
        return mBookshelfNameList;
    }

    /**
     * Construct the Bookshelf list to show in the Spinner.
     *
     * @param context Current context.
     *
     * @return the position that reflects the current bookshelf.
     */
    public int initBookshelfNameList(@NonNull final Context context) {
        mBookshelfNameList.clear();
        mBookshelfNameList.add(context.getString(R.string.bookshelf_all_books));
        // default to 'All Books'
        int currentPos = 0;
        // start at 1, as position 0 is 'All Books'
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.getId() == getCurrentBookshelf().getId()) {
                currentPos = position;
            }
            position++;
            mBookshelfNameList.add(bookshelf.getName());
        }

        return currentPos;
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
        mCurrentBookshelf.setAsPreferred(context);
    }

    @NonNull
    public Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(mCurrentBookshelf);
        return mCurrentBookshelf;
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
     * Get the style of the current bookshelf.
     *
     * @param context Current context
     *
     * @return style
     */
    @NonNull
    public BooklistStyle getCurrentStyle(@NonNull final Context context) {
        Objects.requireNonNull(mCurrentBookshelf);
        return mCurrentBookshelf.getStyle(context, mDb);
    }

    /**
     * Set the style on the current bookshelf.
     *
     * @param context Current context
     * @param style   to set
     */
    public void setCurrentStyle(@NonNull final Context context,
                                @NonNull final BooklistStyle style) {
        Objects.requireNonNull(mCurrentBookshelf);
        mCurrentBookshelf.setStyle(context, mDb, style);
    }

    /**
     * Set the style and position.
     *
     * @param context  Current context
     * @param style    that was selected
     * @param topRow   the top row to store
     * @param listView used to derive the top row offset
     */
    public void onStyleChanged(@NonNull final Context context,
                               @NonNull final BooklistStyle style,
                               final int topRow,
                               @NonNull final RecyclerView listView) {
        Objects.requireNonNull(mCurrentBookshelf);

        // save the new bookshelf/style combination
        mCurrentBookshelf.setAsPreferred(context);
        mCurrentBookshelf.setStyle(context, mDb, style);

        // Set the rebuild state like this is the first time in, which it sort of is,
        // given we are changing style.
        mRebuildState = BooklistBuilder.getPreferredListRebuildState();

        /* There is very little ability to preserve position when going from
         * a list sorted by Author/Series to on sorted by unread/addedDate/publisher.
         * Keeping the current row/pos is probably the most useful thing we can
         * do since we *may* come back to a similar list.
         */
        savePosition(topRow, listView);
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
            mTopRow = topRow;
            View topView = listView.getChildAt(0);
            if (topView != null) {
                mTopRowOffset = topView.getTop();
            } else {
                mTopRowOffset = 0;
            }

            Context context = listView.getContext();
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                             .putInt(PREF_BOB_TOP_ROW, mTopRow)
                             .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
                             .apply();
        }
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param context Current context
     */
    public void initBookList(@NonNull final Context context) {
        Objects.requireNonNull(mCurrentBookshelf);

        BooklistStyle style = mCurrentBookshelf.getStyle(context, mDb);

        // get a new builder and add the required extra domains
        BooklistBuilder blb = new BooklistBuilder(style);

        // Title for displaying + the book language
        blb.addExtraDomain(DBDefinitions.DOM_TITLE,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                           false);
        // the language
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_LANGUAGE,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_LANGUAGE),
                           false);

        // Title for sorting
        blb.addExtraDomain(DBDefinitions.DOM_TITLE_OB,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE_OB),
                           true);
        // The read flag
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_READ,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
                           false);

        // Always get the Author ID (the name is depending on style).
        blb.addExtraDomain(DBDefinitions.DOM_FK_AUTHOR,
                           DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.DOM_FK_AUTHOR),
                           false);

        // external site ID's
        //NEWTHINGS: add new site specific ID: add
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_ISFDB_ID,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_ISFDB_ID),
                           false);
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_GOODREADS_ID,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_GOODREADS_ID),
                           false);
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_LIBRARY_THING_ID,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_LIBRARY_THING_ID),
                           false);
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_STRIP_INFO_BE_ID,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_STRIP_INFO_BE_ID),
                           false);
        blb.addExtraDomain(DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID,
                           DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID),
                           false);


        if (style.isUsed(DBDefinitions.KEY_EDITION_BITMASK)) {
            // The edition bitmask
            blb.addExtraDomain(DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                               DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_EDITION_BITMASK),
                               false);
        }

        if (style.isUsed(DBDefinitions.KEY_SIGNED)) {
            blb.addExtraDomain(DBDefinitions.DOM_BOOK_SIGNED,
                               DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_SIGNED),
                               false);
        }

        if (style.isUsed(DBDefinitions.KEY_LOANEE)) {
            blb.addExtraDomain(DBDefinitions.DOM_LOANEE_AS_BOOLEAN,
                               DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN,
                               false);
        }

        /*
         * If we do not use a background task for the extras,
         * then we add the needed extras columns to the main query.
         * Depending on the device speed, and how the user uses styles,
         * BOTH methods can be advantageous.
         * Hence this is a preference <strong>per style</strong>.
         */
        if (!style.useTaskForExtras()) {
            if (style.isExtraUsed(DBDefinitions.KEY_BOOKSHELF)) {
                blb.addExtraDomain(DBDefinitions.DOM_BOOKSHELF_CSV,
                                   BOOKSHELVES_CSV_SOURCE_EXPRESSION, false);
            }

            // we fetch ONLY the primary author
            if (style.isExtraUsed(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                blb.addExtraDomain(DBDefinitions.DOM_AUTHOR_FORMATTED,
                                   style.showAuthorGivenNameFirst(context)
                                   ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                                   : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN,
                                   false);
            }
            // and for now, don't get the author type.
//                if (style.isExtraUsed(DBDefinitions.KEY_AUTHOR_TYPE)) {
//                    blb.addExtraDomain(DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
//                                      DBDefinitions.TBL_BOOK_AUTHOR
//                                              .dot(DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK),
//                                      false);
//                }

            if (style.isExtraUsed(DBDefinitions.KEY_PUBLISHER)) {
                blb.addExtraDomain(DBDefinitions.DOM_BOOK_PUBLISHER,
                                   DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_PUBLISHER),
                                   false);
            }
            if (style.isExtraUsed(DBDefinitions.KEY_DATE_PUBLISHED)) {
                blb.addExtraDomain(DBDefinitions.DOM_DATE_PUBLISHED,
                                   DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_DATE_PUBLISHED),
                                   false);
            }
            if (style.isExtraUsed(DBDefinitions.KEY_ISBN)) {
                blb.addExtraDomain(DBDefinitions.DOM_BOOK_ISBN,
                                   DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_ISBN),
                                   false);
            }
            if (style.isExtraUsed(DBDefinitions.KEY_FORMAT)) {
                blb.addExtraDomain(DBDefinitions.DOM_BOOK_FORMAT,
                                   DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_FORMAT),
                                   false);
            }
            if (style.isExtraUsed(DBDefinitions.KEY_LOCATION)) {
                blb.addExtraDomain(DBDefinitions.DOM_BOOK_LOCATION,
                                   DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_LOCATION),
                                   false);
            }
        }

        // Always limit to the current bookshelf.
        blb.setBookshelf(mCurrentBookshelf);

        // if we have a list of ID's, ignore other criteria.
        if (mSearchCriteria.hasIdList()) {
            blb.setFilterOnBookIdList(mSearchCriteria.bookList);

        } else {
            // Criteria supported by FTS
            blb.setFilter(mSearchCriteria.ftsAuthor,
                          mSearchCriteria.ftsTitle,
                          mSearchCriteria.ftsSeries,
                          mSearchCriteria.ftsKeywords);

            // non-FTS
            //blb.setFilterOnSeriesName(mSearchCriteria.ftsSeries);
            blb.setFilterOnLoanedToPerson(mSearchCriteria.loanee);
        }

        new GetBookListTask(blb, mCursor, mCurrentPositionedBookId, mRebuildState,
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
    @NonNull
    public MutableLiveData<BuilderHolder> getBooklist() {
        return mBuilderResult;
    }

    @Nullable
    public BooklistPseudoCursor getListCursor() {
        return mCursor;
    }

    public void setListCursor(@NonNull final BooklistPseudoCursor listCursor) {
        mCursor = listCursor;
    }

    /** Convenience method. */
    @Nullable
    public BooklistBuilder getBuilder() {
        return mCursor != null ? mCursor.getBuilder() : null;
    }

    /**
     * Check if a rebuild is needed in {@code Activity#onResume()}.
     *
     * @return {@code true} if a rebuild is needed
     */
    public boolean isForceRebuildInOnResume() {
        return mForceRebuildInOnResume;
    }

    /**
     * Request a rebuild at the next {@code Activity#onResume()}.
     *
     * @param forceRebuild Flag
     */
    public void setForceRebuildInOnResume(final boolean forceRebuild) {
        mForceRebuildInOnResume = forceRebuild;
    }

    /**
     * Check if the list has (ever) loaded successfully.
     *
     * @return {@code true} if loaded at least once.
     */
    public boolean isListLoaded() {
        return mListHasBeenLoaded;
    }


    /**
     * Store the current/desired book id to position the list.
     *
     * @param bookId to use
     */
    public void setCurrentPositionedBookId(final long bookId) {
        mCurrentPositionedBookId = bookId;
    }

    /**
     * Convenience method to hide the internals.
     * <p>
     * Get the target rows based on the current book position.
     *
     * @return RowDetails
     */
    @Nullable
    public ArrayList<BooklistBuilder.RowDetails> getTargetRows() {
        //noinspection ConstantConditions
        return getBuilder().getTargetRows(mCurrentPositionedBookId);
    }

    /**
     * Check if this book is lend out, or not.
     *
     * @param mapper cursor row with book data
     *
     * @return {@code true} if this book is available for lending.
     */
    public boolean isAvailable(@NonNull final CursorMapper mapper) {
        String loanee;
        if (mapper.contains(DBDefinitions.KEY_LOANEE)) {
            loanee = mapper.getString(DBDefinitions.KEY_LOANEE);
        } else {
            loanee = mDb.getLoaneeByBookId(mapper.getLong(DBDefinitions.KEY_FK_BOOK));
        }
        return (loanee == null) || loanee.isEmpty();
    }

    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @param context Current context
     * @param mapper  cursor row with book data
     *
     * @return formatted Author name
     */
    @Nullable
    public String getAuthorFromRow(@NonNull final Context context,
                                   @NonNull final CursorMapper mapper) {
        if (mapper.contains(DBDefinitions.KEY_FK_AUTHOR)
            && mapper.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0) {
            Author author = mDb.getAuthor(mapper.getLong(DBDefinitions.KEY_FK_AUTHOR));
            if (author != null) {
                return author.getLabel(context);
            }

        } else if (mapper.getInt(DBDefinitions.KEY_BL_NODE_KIND)
                   == BooklistGroup.RowKind.BOOK) {
            List<Author> authors = mDb.getAuthorsByBookId(
                    mapper.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!authors.isEmpty()) {
                return authors.get(0).getLabel(context);
            }
        }
        return null;
    }

    /**
     * Get the Series name.
     *
     * @param mapper cursor row with book data
     *
     * @return the unformatted Series name (i.e. without the number)
     */
    @Nullable
    public String getSeriesFromRow(@NonNull final CursorMapper mapper) {
        if (mapper.contains(DBDefinitions.KEY_FK_SERIES)
            && mapper.getLong(DBDefinitions.KEY_FK_SERIES) > 0) {
            Series series = mDb.getSeries(mapper.getLong(DBDefinitions.KEY_FK_SERIES));
            if (series != null) {
                return series.getTitle();
            }
        } else if (mapper.getInt(DBDefinitions.KEY_BL_NODE_KIND)
                   == BooklistGroup.RowKind.BOOK) {
            ArrayList<Series> series =
                    mDb.getSeriesByBookId(mapper.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!series.isEmpty()) {
                return series.get(0).getTitle();
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
        return mCursor.getBuilder().getCurrentBookIdList();
    }

    public void restoreCurrentBookshelf(@NonNull final Context context) {
        mCurrentBookshelf = Bookshelf.getBookshelf(context, mDb, true);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean reloadCurrentBookshelf(@NonNull final Context context) {
        Bookshelf newBookshelf = Bookshelf.getBookshelf(context, mDb, true);
        if (!newBookshelf.equals(mCurrentBookshelf)) {
            // if it was.. switch to it.
            mCurrentBookshelf = newBookshelf;
            return true;
        }
        return false;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<String> getUserMessage() {
        return mUserMessage;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<Boolean> getNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    @NonNull
    public TaskListener<Integer> getGoodreadsTaskListener() {
        if (mGoodreadsTaskListener == null) {
            mGoodreadsTaskListener = new GoodreadsTaskListener(mUserMessage, mNeedsGoodreads);
        }
        return mGoodreadsTaskListener;
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     */
    public static class SearchCriteria {

        /**
         * List of currentPosBookId's to display.
         * The RESULT of a search with {@link FTSSearchActivity}
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
         * Series to use in FTS search query.
         * Supported in the builder, but not yet user-settable.
         */
        @Nullable
        String ftsSeries;

        /**
         * Name of the person we loaned books to, to use in search query.
         * Supported in the builder, but not yet user-settable.
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

            ftsSeries = null;
            loanee = null;

            bookList = null;
        }

        /**
         * Get a single string with all search words, for displaying.
         *
         * @return csv string, can be empty, but never {@code null}.
         */
        @NonNull
        public String getDisplayString() {
            Collection<String> list = new ArrayList<>();

            if (ftsAuthor != null && !ftsAuthor.isEmpty()) {
                list.add(ftsAuthor);
            }
            if (ftsTitle != null && !ftsTitle.isEmpty()) {
                list.add(ftsTitle);
            }
            if (ftsKeywords != null && !ftsKeywords.isEmpty()) {
                list.add(ftsKeywords);
            }
            return TextUtils.join(",", list);
        }

        public void setKeywords(@Nullable final String keywords) {
            if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
                ftsKeywords = null;
            } else {
                ftsKeywords = keywords.trim();
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
            if (bundle.containsKey(DBDefinitions.KEY_TITLE)) {
                ftsTitle = bundle.getString(DBDefinitions.KEY_TITLE);
            }

            if (bundle.containsKey(DBDefinitions.KEY_SERIES_TITLE)) {
                ftsSeries = bundle.getString(DBDefinitions.KEY_SERIES_TITLE);
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
         * Put the search criteria as extras in the Intent.
         *
         * @param intent which will be used for a #startActivityForResult call
         */
        public void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, ftsKeywords)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, ftsAuthor)

                  .putExtra(DBDefinitions.KEY_TITLE, ftsTitle)
                  .putExtra(DBDefinitions.KEY_SERIES_TITLE, ftsSeries)
                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)

                  .putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isEmpty() {
            return (ftsKeywords == null || ftsKeywords.isEmpty())
                   && (ftsAuthor == null || ftsAuthor.isEmpty())
                   && (ftsTitle == null || ftsTitle.isEmpty())
                   && (ftsSeries == null || ftsSeries.isEmpty())
                   && (loanee == null || loanee.isEmpty())
                   && (bookList == null || bookList.isEmpty());
        }

        boolean hasIdList() {
            return bookList != null && !bookList.isEmpty();
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     */
    private static class GetBookListTask
            extends TaskBase<BuilderHolder> {

        /** the builder. */
        @NonNull
        private final BooklistBuilder mBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderHolder mHolder;
        @Nullable
        private final BooklistPseudoCursor mCurrentListCursor;
        /** Resulting Cursor. */
        private BooklistPseudoCursor tempListCursor;

        /**
         * Constructor.
         *
         * @param bookListBuilder   the builder
         * @param currentListCursor Current displayed list cursor.
         * @param currentPosBookId  Current position in the list.
         * @param listState         Requested list state
         * @param taskListener      listener
         */
        @UiThread
        GetBookListTask(@NonNull final BooklistBuilder bookListBuilder,
                        @Nullable final BooklistPseudoCursor currentListCursor,
                        final long currentPosBookId,
                        @BooklistBuilder.ListRebuildMode final int listState,
                        final TaskListener<BuilderHolder> taskListener) {
            super(R.id.TASK_ID_GET_BOOKLIST, taskListener);

            mBuilder = bookListBuilder;
            mCurrentListCursor = currentListCursor;

            // input/output fields for the task.
            mHolder = new BuilderHolder(currentPosBookId, listState);
        }

        @Override
        @NonNull
        @WorkerThread
        protected BuilderHolder doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookListTask");
            // timers removed as all 'real' time spend here is in mBuilder.build.
            try {
                // Build the underlying data and preserve this state
                mBuilder.build(mHolder.listState);
                mHolder.listState = BooklistBuilder.PREF_LIST_REBUILD_SAVED_STATE;

                if (isCancelled()) {
                    return mHolder;
                }

                mHolder.resultTargetRows = mBuilder.getTargetRows(mHolder.currentPosBookId);
                // Clear it so it won't be reused.
                mHolder.currentPosBookId = 0;

                // Now we have the expanded groups as needed, get the list cursor
                tempListCursor = mBuilder.getNewListCursor();
                // get a count() from the cursor in background task because the setAdapter()
                // call will do a count() and potentially block the UI thread while it
                // pages through the entire cursor. Doing it here makes subsequent calls faster.
                tempListCursor.getCount();
                mHolder.resultDistinctBookCount = mBuilder.getDistinctBookCount();
                mHolder.resultTotalBookCount = mBuilder.getBookCount();
                // Set the results.
                mHolder.resultListCursor = tempListCursor;

            } catch (@NonNull final Exception e) {
                // catch ALL exceptions, so we get them logged for certain.
                Logger.error(TAG, e);
                mException = e;
                cleanup();
            }

            return mHolder;
        }

        @Override
        protected void onCancelled(@NonNull final BuilderHolder result) {
            cleanup();
            super.onCancelled(result);
        }

        @AnyThread
        private void cleanup() {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "cleanup", mException);
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
    }

    /** value class for the Builder. */
    public static class BuilderHolder {

        /** input/output field. The book id around which the list is positioned. */
        long currentPosBookId;
        /** input/output field. */
        @BooklistBuilder.ListRebuildMode
        int listState;

        /**
         * Resulting Cursor; can be {@code null} if the list did not get build.
         */
        @Nullable
        BooklistPseudoCursor resultListCursor;

        /**
         * output field. Pre-fetched from the resultListCursor's builder.
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultTotalBookCount;
        /**
         * output field. Pre-fetched from the resultListCursor's builder.
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultDistinctBookCount;

        /**
         * output field. Used to determine new cursor position; can be {@code null}.
         * Should be ignored if resultListCursor is {@code null}
         */
        @Nullable
        ArrayList<BooklistBuilder.RowDetails> resultTargetRows;

        /**
         * Constructor: these are the fields we need as input.
         */
        BuilderHolder(final long currentPosBookId,
                      @BooklistBuilder.ListRebuildMode final int listState) {
            this.currentPosBookId = currentPosBookId;
            this.listState = listState;
        }

        @Nullable
        public BooklistPseudoCursor getResultListCursor() {
            return resultListCursor;
        }

        @Nullable
        public ArrayList<BooklistBuilder.RowDetails> getResultTargetRows() {
            return resultTargetRows;
        }

        @Override
        @NonNull
        public String toString() {
            return "BuilderHolder{"
                   + "currentPosBookId=" + currentPosBookId
                   + ", listState=" + listState
                   + ", resultTotalBookCount=" + resultTotalBookCount
                   + ", resultDistinctBookCount=" + resultDistinctBookCount
                   + ", resultListCursor=" + resultListCursor
                   + ", resultTargetRows=" + resultTargetRows
                   + '}';
        }
    }
}
