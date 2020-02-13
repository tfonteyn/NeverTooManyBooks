/*
 * @Copyright 2020 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TrackedCursor;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

public class BooksOnBookshelfModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfModel";
    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";

    private static final String PREF_PREFIX = "booklist.";

    /** Preference name - Saved adapter position of current top row. */
    private static final String PREF_BOB_TOP_ITEM_POSITION = PREF_PREFIX + "top.row";
    /** Preference name - Saved rowId of current top row. */
    private static final String PREF_BOB_TOP_ROW_ID = PREF_PREFIX + "top.rowId";
    /** Preference name - Saved position of last top row offset from view top. */
    private static final String PREF_BOB_TOP_VIEW_OFFSET = PREF_PREFIX + "top.offset";
    /**
     * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_CSV} when
     * NOT using a background task for the extras.
     */
    private static final String BOOKSHELVES_CSV_SOURCE_EXPRESSION =
            "("
            + "SELECT GROUP_CONCAT("
            + DBDefinitions.TBL_BOOKSHELF.dot(DBDefinitions.KEY_BOOKSHELF) + ",', ')"
            + " FROM "
            + DBDefinitions.TBL_BOOKSHELF.ref()
            + DBDefinitions.TBL_BOOKSHELF.join(DBDefinitions.TBL_BOOK_BOOKSHELF)
            + " WHERE "
            + DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_PK_ID) + "="
            + DBDefinitions.TBL_BOOK_BOOKSHELF.dot(DBDefinitions.KEY_FK_BOOK)
            + ")";
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
    /** Database Access. */
    private DAO mDb;
    /** Lazy init, always use {@link #getGoodreadsTaskListener(Context)}. */
    private TaskListener<GrStatus> mGoodreadsTaskListener;
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
     * Stores the book id for the desired list position.
     * Used to call {@link GetBookListTask}. Reset to afterwards.
     */
    private long mDesiredCentralBookId;

    /**
     * Saved adapter position of top row.
     * See {@link LinearLayoutManager#scrollToPosition(int)}
     */
    private int mItemPosition = RecyclerView.NO_POSITION;

    /**
     * Saved view offset of top row.
     * See {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    private int mTopViewOffset;

    /** Used by onScroll to detect when the top row has actually changed. */
    private int mPreviousFirstVisibleItemPosition = RecyclerView.NO_POSITION;

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
                        // right now (2020-01-07) this means 0 / PREF_REBUILD_SAVED_STATE
                        // but blindly copying makes this flexible
                        mDesiredCentralBookId = message.result.desiredCentralBookId;
                        mRebuildState = message.result.listState;

                        // always copy these results
                        mTotalBooks = message.result.resultTotalBookCount;
                        mUniqueBooks = message.result.resultDistinctBookCount;

                        // Do not copy the resultListCursor yet, as it might be null
                        // in which case we will use the old value.
                        // The target rows get read when we'll use them
                    }

                    // always call back, even if there is no new list.
                    mBuilderResult.setValue(message.result);
                }
            };
    /** Current displayed list cursor. */
    @Nullable
    private BooklistCursor mCursor;
    private long mTopRowRowId;

    @Override
    protected void onCleared() {

        if (mCursor != null) {
            mCursor.getBooklistBuilder().close();
            mCursor.close();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            TrackedCursor.dumpCursors();
        }

        if (mDb != null) {
            mDb.close();
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

        final Bundle currentArgs = savedInstanceState != null ? savedInstanceState : args;

        if (currentArgs == null) {
            // Get preferred booklist state to use from preferences;
            // always do this here in init, as the prefs might have changed anytime.
            mRebuildState = BooklistBuilder.getPreferredListRebuildState();

        } else {
            // Unless set by the caller, preserve state when rebuilding/recreating etc
            mRebuildState = currentArgs.getInt(BKEY_LIST_STATE,
                                               BooklistBuilder.PREF_REBUILD_SAVED_STATE);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Restore list position on bookshelf
        mTopRowRowId = prefs.getLong(PREF_BOB_TOP_ROW_ID, 0);

        mItemPosition = prefs.getInt(PREF_BOB_TOP_ITEM_POSITION, RecyclerView.NO_POSITION);
        mTopViewOffset = prefs.getInt(PREF_BOB_TOP_VIEW_OFFSET, 0);
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
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);
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
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);
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
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);
        mCurrentBookshelf.setStyle(context, mDb, style);
    }

    /**
     * Should be called after a style change.
     *
     * @param context Current context
     * @param style   that was selected
     */
    public void onStyleChanged(@NonNull final Context context,
                               @NonNull final BooklistStyle style) {
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);

        // save the new bookshelf/style combination
        mCurrentBookshelf.setAsPreferred(context);
        mCurrentBookshelf.setStyle(context, mDb, style);

        // Set the rebuild state like this is the first time in, which it sort of is,
        // given we are changing style.
        mRebuildState = BooklistBuilder.getPreferredListRebuildState();
    }

    public int getItemPosition() {
        return mItemPosition;
    }

    public long getTopRowRowId() {
        return mTopRowRowId;
    }

    public int getTopViewOffset() {
        return mTopViewOffset;
    }

    public int getPreviousFirstVisibleItemPosition() {
        return mPreviousFirstVisibleItemPosition;
    }

    public void setPreviousFirstVisibleItemPosition(final int adapterPosition) {
        mPreviousFirstVisibleItemPosition = adapterPosition;
    }

    public void setRebuildState(final int rebuildState) {
        mRebuildState = rebuildState;
    }

    /**
     * Save current position information in the preferences.
     * We do this to preserve this data across application shutdown/startup.
     *
     * @param context       Current context
     * @param position      adapter list position; i.e. first visible position in the list
     * @param rowId         the row id for that position
     * @param topViewOffset offset in pixels for the first visible position in the list
     */
    public void saveListPosition(@NonNull final Context context,
                                 final int position,
                                 final long rowId,
                                 final int topViewOffset) {


        if (mListHasBeenLoaded) {
            mItemPosition = position;
            mTopRowRowId = rowId;

            mTopViewOffset = topViewOffset;

            PreferenceManager.getDefaultSharedPreferences(context).edit()
                             .putInt(PREF_BOB_TOP_ITEM_POSITION, mItemPosition)
                             .putLong(PREF_BOB_TOP_ROW_ID, mTopRowRowId)
                             .putInt(PREF_BOB_TOP_VIEW_OFFSET, mTopViewOffset)
                             .apply();
        }
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param context Current context
     */
    public void initBookList(@NonNull final Context context) {
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);

        final BooklistStyle style = mCurrentBookshelf.getStyle(context, mDb);

        // get a new builder and add the required extra domains
        final BooklistBuilder blb = new BooklistBuilder(mCurrentBookshelf, style);

        // Title for displaying
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_TITLE,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE), 0));
        // the book language is needed for reordering titles
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_BOOK_LANGUAGE,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LANGUAGE), 0));
        // Title for sorting
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_TITLE_OB,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE_OB), 0));

        // Always get the read flag
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_BOOK_READ,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_READ), 0));

        // Always get the Author ID (the need for the actual name is depending on the style).
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_FK_AUTHOR,
                DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.KEY_FK_AUTHOR), 0));

        // Always get the ISBN
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_BOOK_ISBN,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_ISBN), 0));
        // external site ID's
        //NEWTHINGS: add new site specific ID: add
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_EID_ISFDB,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_ISFDB), 0));
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_EID_GOODREADS_BOOK,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_GOODREADS_BOOK), 0));
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_EID_LIBRARY_THING,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_LIBRARY_THING), 0));
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_EID_STRIP_INFO_BE,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_STRIP_INFO_BE), 0));
        blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                DBDefinitions.DOM_EID_OPEN_LIBRARY,
                DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_OPEN_LIBRARY), 0));


        if (style.isUsed(DBDefinitions.KEY_EDITION_BITMASK)) {
            // The edition bitmask
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EDITION_BITMASK), 0));
        }

        if (style.isUsed(DBDefinitions.KEY_SIGNED)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_BOOK_SIGNED,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_SIGNED), 0));
        }

        if (style.isUsed(DBDefinitions.KEY_LOANEE)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_LOANEE_AS_BOOLEAN,
                    DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN, 0));
        }

        if (style.isExtraUsed(DBDefinitions.KEY_BOOKSHELF)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_BOOKSHELF_CSV,
                    BOOKSHELVES_CSV_SOURCE_EXPRESSION, 0));
        }

        // we fetch ONLY the primary author
        if (style.isExtraUsed(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_AUTHOR_FORMATTED,
                    style.showAuthorGivenNameFirst(context)
                    ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                    : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN,
                    0));
        }
        // for now, don't get the author type.
        // if (style.isExtraUsed(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK)) {
        //     blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
        //             DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
        //             DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK),
        //             0));
        // }

        if (style.isExtraUsed(DBDefinitions.KEY_PUBLISHER)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_BOOK_PUBLISHER,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_PUBLISHER), 0));
        }
        if (style.isExtraUsed(DBDefinitions.KEY_DATE_PUBLISHED)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_DATE_PUBLISHED,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_DATE_PUBLISHED), 0));
        }
        if (style.isExtraUsed(DBDefinitions.KEY_FORMAT)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_BOOK_FORMAT,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_FORMAT), 0));
        }
        if (style.isExtraUsed(DBDefinitions.KEY_LOCATION)) {
            blb.addDomain(new BooklistBuilder.ExtraDomainDetails(
                    DBDefinitions.DOM_BOOK_LOCATION,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LOCATION), 0));
        }

        // if we have a list of ID's, ignore other criteria.
        if (mSearchCriteria.hasIdList()) {
            blb.setFilterOnBookIdList(mSearchCriteria.bookList);

        } else {
            // Criteria supported by FTS
            blb.setFilter(mSearchCriteria.ftsAuthor,
                          mSearchCriteria.ftsTitle,
                          mSearchCriteria.ftsSeries,
                          mSearchCriteria.ftsKeywords);

            blb.setFilterOnLoanedToPerson(mSearchCriteria.loanee);
        }

        new GetBookListTask(blb, mCursor, mDesiredCentralBookId, mRebuildState,
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
    public BooklistCursor getListCursor() {
        return mCursor;
    }

    public void setListCursor(@NonNull final BooklistCursor listCursor) {
        mCursor = listCursor;
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
     * Set the <strong>desired</strong> book id to position the list.
     *
     * @param bookId to use
     */
    public void setDesiredCentralBookId(final long bookId) {
        mDesiredCentralBookId = bookId;
    }

    /**
     * Check if this book is lend out, or not.
     *
     * @param rowData with data
     *
     * @return {@code true} if this book is available for lending.
     */
    public boolean isAvailable(@NonNull final RowDataHolder rowData) {
        final String loanee;
        if (rowData.contains(DBDefinitions.KEY_LOANEE)) {
            loanee = rowData.getString(DBDefinitions.KEY_LOANEE);
        } else {
            loanee = mDb.getLoaneeByBookId(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
        }
        return (loanee == null) || loanee.isEmpty();
    }

    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @param context Current context
     * @param rowData with data
     *
     * @return formatted Author name
     */
    @Nullable
    public String getAuthorFromRow(@NonNull final Context context,
                                   @NonNull final RowDataHolder rowData) {
        if (rowData.contains(DBDefinitions.KEY_FK_AUTHOR)
            && rowData.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0) {
            final Author author = mDb.getAuthor(rowData.getLong(DBDefinitions.KEY_FK_AUTHOR));
            if (author != null) {
                return author.getLabel(context);
            }

        } else if (rowData.getInt(DBDefinitions.KEY_BL_NODE_KIND)
                   == BooklistGroup.RowKind.BOOK) {
            final List<Author> authors = mDb.getAuthorsByBookId(
                    rowData.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!authors.isEmpty()) {
                return authors.get(0).getLabel(context);
            }
        }
        return null;
    }

    /**
     * Get the Series name.
     *
     * @param rowData with book data
     *
     * @return the unformatted Series name (i.e. without the number)
     */
    @Nullable
    public String getSeriesFromRow(@NonNull final RowDataHolder rowData) {
        if (rowData.contains(DBDefinitions.KEY_FK_SERIES)
            && rowData.getLong(DBDefinitions.KEY_FK_SERIES) > 0) {
            final Series series = mDb.getSeries(rowData.getLong(DBDefinitions.KEY_FK_SERIES));
            if (series != null) {
                return series.getTitle();
            }
        } else if (rowData.getInt(DBDefinitions.KEY_BL_NODE_KIND)
                   == BooklistGroup.RowKind.BOOK) {
            final ArrayList<Series> series =
                    mDb.getSeriesByBookId(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
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

    public void restoreCurrentBookshelf(@NonNull final Context context) {
        mCurrentBookshelf = Bookshelf.getBookshelf(context, mDb, true);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean reloadCurrentBookshelf(@NonNull final Context context) {
        final Bookshelf newBookshelf = Bookshelf.getBookshelf(context, mDb, true);
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
    public TaskListener<GrStatus> getGoodreadsTaskListener(@NonNull final Context context) {
        if (mGoodreadsTaskListener == null) {
            mGoodreadsTaskListener = new TaskListener<GrStatus>() {

                @Override
                public void onFinished(@NonNull final FinishMessage<GrStatus> message) {
                    String msg = GoodreadsHandler.handleResult(context, message);
                    if (msg != null) {
                        // success, failure, cancelled
                        mUserMessage.setValue(msg);
                    } else {
                        // needs Registration
                        mNeedsGoodreads.setValue(true);
                    }
                }

                @Override
                public void onProgress(@NonNull final ProgressMessage message) {
                    if (message.text != null) {
                        mUserMessage.setValue(message.text);
                    }
                }
            };
        }
        return mGoodreadsTaskListener;
    }

    public void safeClose(@Nullable final BooklistCursor cursorToClose) {
        BooklistBuilder.closeCursor(cursorToClose, mCursor);
    }

    @Nullable
    public ArrayList<RowStateDAO.ListRowDetails> getTargetRows() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().getTargetRows(mDesiredCentralBookId);
    }

    public boolean toggleNode(final long rowId) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().toggleNode(rowId);
    }

    @NonNull
    public BooklistCursor getNewListCursor() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().getNewListCursor();
    }

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().getCurrentBookIdList();
    }

    @NonNull
    public String createFlattenedBooklist() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().createFlattenedBooklist();
    }

    public int getListPosition(final long rowId) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().getListPosition(rowId);
    }

    public void expandAllNodes(final int topLevel,
                               final boolean expand) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        mCursor.getBooklistBuilder().expandAllNodes(topLevel, expand);
    }

    public void saveAllNodes() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        mCursor.getBooklistBuilder().saveAllNodes();
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     */
    public static class SearchCriteria {

        /**
         * List of desiredCentralBookId's to display.
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
         * Get a single string with all FTS search words, for displaying.
         *
         * @return csv string, can be empty, but never {@code null}.
         */
        @NonNull
        public String getFtsSearchText() {
            final Collection<String> list = new ArrayList<>();

            if (ftsAuthor != null && !ftsAuthor.isEmpty()) {
                list.add(ftsAuthor);
            }
            if (ftsTitle != null && !ftsTitle.isEmpty()) {
                list.add(ftsTitle);
            }
            if (ftsSeries != null && !ftsSeries.isEmpty()) {
                list.add(ftsSeries);
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
         *
         * @return {@code true} if at least one criteria was set
         */
        public boolean from(@NonNull final Bundle bundle,
                            final boolean clearFirst) {
            if (clearFirst) {
                clear();
            }
            boolean isSet = false;

            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                setKeywords(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
                isSet = true;
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                ftsAuthor = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
                isSet = true;
            }
            if (bundle.containsKey(DBDefinitions.KEY_TITLE)) {
                ftsTitle = bundle.getString(DBDefinitions.KEY_TITLE);
                isSet = true;
            }
            if (bundle.containsKey(DBDefinitions.KEY_SERIES_TITLE)) {
                ftsSeries = bundle.getString(DBDefinitions.KEY_SERIES_TITLE);
                isSet = true;
            }

            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
                isSet = true;
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                //noinspection unchecked
                bookList = (ArrayList<Long>) bundle.getSerializable(UniqueId.BKEY_ID_LIST);
                isSet = true;
            }

            return isSet;
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
            extends TaskBase<Void, BuilderHolder> {

        /** the builder. */
        @NonNull
        private final BooklistBuilder mBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderHolder mHolder;
        @Nullable
        private final BooklistCursor mCurrentListCursor;
        /** Resulting Cursor. */
        private BooklistCursor mTempListCursor;

        /**
         * Constructor.
         *
         * @param bookListBuilder      the builder
         * @param currentListCursor    Current displayed list cursor.
         * @param desiredCentralBookId current position in the list.
         * @param listState            Requested list state
         * @param taskListener         listener
         */
        @UiThread
        GetBookListTask(@NonNull final BooklistBuilder bookListBuilder,
                        @Nullable final BooklistCursor currentListCursor,
                        final long desiredCentralBookId,
                        @BooklistBuilder.ListRebuildMode final int listState,
                        final TaskListener<BuilderHolder> taskListener) {
            super(R.id.TASK_ID_GET_BOOKLIST, taskListener);

            mBuilder = bookListBuilder;
            mCurrentListCursor = currentListCursor;

            // input/output fields for the task.
            mHolder = new BuilderHolder(desiredCentralBookId, listState);
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
                mHolder.listState = BooklistBuilder.PREF_REBUILD_SAVED_STATE;

                if (isCancelled()) {
                    return mHolder;
                }

                // these are the row(s) we want to center the new list on.
                mHolder.resultTargetRows = mBuilder.getTargetRows(mHolder.desiredCentralBookId);
                // Clear it so it won't be reused.
                mHolder.desiredCentralBookId = 0;

                // Now we have the expanded groups as needed, get the list cursor
                // The cursor will hold a reference to the builder.
                mTempListCursor = mBuilder.getNewListCursor();
                // get a count() from the cursor in background task because the setAdapter()
                // call will do a count() and potentially block the UI thread while it
                // pages through the entire cursor. Doing it here makes subsequent calls faster.
                mTempListCursor.getCount();

                // Set the results.
                mHolder.resultListCursor = mTempListCursor;
                mHolder.resultDistinctBookCount = mBuilder.getDistinctBookCount();
                mHolder.resultTotalBookCount = mBuilder.getBookCount();

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
            BooklistBuilder.closeCursor(mTempListCursor, mCurrentListCursor);
        }
    }

    /** value class for the Builder. */
    public static class BuilderHolder {

        /** input field. The book id around which the list is currently positioned. */
        long desiredCentralBookId;
        /** input/output field. */
        @BooklistBuilder.ListRebuildMode
        int listState;

        /**
         * Resulting Cursor; can be {@code null} if the list did not get build.
         */
        @Nullable
        BooklistCursor resultListCursor;

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
        ArrayList<RowStateDAO.ListRowDetails> resultTargetRows;

        /**
         * Constructor: these are the fields we need as input.
         */
        BuilderHolder(final long desiredCentralBookId,
                      @BooklistBuilder.ListRebuildMode final int listState) {
            this.desiredCentralBookId = desiredCentralBookId;
            this.listState = listState;
        }

        @Nullable
        public BooklistCursor getResultListCursor() {
            return resultListCursor;
        }

        @Nullable
        public List<RowStateDAO.ListRowDetails> getResultTargetRows() {
            return resultTargetRows;
        }

        @Override
        @NonNull
        public String toString() {
            return "BuilderHolder{"
                   + "desiredCentralBookId=" + desiredCentralBookId
                   + ", listState=" + listState
                   + ", resultTotalBookCount=" + resultTotalBookCount
                   + ", resultDistinctBookCount=" + resultDistinctBookCount
                   + ", resultListCursor=" + resultListCursor
                   + ", resultTargetRows=" + resultTargetRows
                   + '}';
        }
    }
}
