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
import android.database.Cursor;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
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
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

import static com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.PREF_REBUILD_ALWAYS_EXPANDED;

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

    /** The fixed list of domains we always need in {@link #buildBookList}. */
    private static final List<VirtualDomain> FIXED_DOMAIN_LIST = Arrays.asList(
            // Title for displaying
            new VirtualDomain(
                    DBDefinitions.DOM_TITLE,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE)),
            // Title for sorting
            new VirtualDomain(
                    DBDefinitions.DOM_TITLE_OB,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE_OB)),
            // the book language is needed for reordering titles
            new VirtualDomain(
                    DBDefinitions.DOM_BOOK_LANGUAGE,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LANGUAGE)),
            // Always get the read flag
            new VirtualDomain(
                    DBDefinitions.DOM_BOOK_READ,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_READ)),
            // Always get the Author ID (the need for the actual name is depending on the style).
            new VirtualDomain(
                    DBDefinitions.DOM_FK_AUTHOR,
                    DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.KEY_FK_AUTHOR)),
            // Always get the ISBN
            new VirtualDomain(
                    DBDefinitions.DOM_BOOK_ISBN,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_ISBN)),
            // We want the UUID for the book so we can get thumbnails
            new VirtualDomain(
                    DBDefinitions.DOM_BOOK_UUID,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_BOOK_UUID)),

            // external site ID's
            //NEWTHINGS: add new site specific ID: add
            new VirtualDomain(
                    DBDefinitions.DOM_EID_ISFDB,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_ISFDB)),
            new VirtualDomain(
                    DBDefinitions.DOM_EID_GOODREADS_BOOK,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_GOODREADS_BOOK)),
            new VirtualDomain(
                    DBDefinitions.DOM_EID_LIBRARY_THING,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_LIBRARY_THING)),
            new VirtualDomain(
                    DBDefinitions.DOM_EID_STRIP_INFO_BE,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_STRIP_INFO_BE)),
            new VirtualDomain(
                    DBDefinitions.DOM_EID_OPEN_LIBRARY,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EID_OPEN_LIBRARY)));

    /** The result of a successful build of the booklist. */
    private final MutableLiveData<List<RowStateDAO.ListRowDetails>> mBuilderSuccess =
            new MutableLiveData<>();
    /** The result of a failed build of the booklist. */
    private final MutableLiveData<Cursor> mBuilderFailed = new MutableLiveData<>();

    private final MutableLiveData<Boolean> mShowProgressBar = new MutableLiveData<>();

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
     * Used to call {@link GetBookListTask}. Reset afterwards.
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
    private final TaskListener<BuilderResult> mOnGetBookListTaskListener =
            new TaskListener<BuilderResult>() {
                @Override
                public void onFinished(@NonNull final FinishMessage<BuilderResult> message) {
                    // Save a flag to say list was loaded at least once successfully (or not)
                    mListHasBeenLoaded = message.status == TaskStatus.Success;

                    // reset the central book id.
                    mDesiredCentralBookId = 0;

                    if (mListHasBeenLoaded) {
                        // sanity check
                        Objects.requireNonNull(message.result.listCursor, ErrorMsg.NULL_CURSOR);

                        // preserve the new state
                        mRebuildState = BooklistBuilder.PREF_REBUILD_SAVED_STATE;

                        mTotalBooks = message.result.totalBookCount;
                        mUniqueBooks = message.result.distinctBookCount;

                        @Nullable
                        final BooklistCursor oldCursor = mCursor;
                        mCursor = message.result.listCursor;
                        if (oldCursor != null) {
                            BooklistBuilder.closeCursor(oldCursor, mCursor);
                        }

                        mBuilderSuccess.setValue(message.result.getTargetRows());

                    } else {
                        // restore the old list
                        mBuilderFailed.setValue(mCursor);
                    }

                    mShowProgressBar.setValue(false);
                }
            };
    /** Current displayed list cursor. */
    @Nullable
    private BooklistCursor mCursor;
    private long mTopRowRowId;

    /**
     * Get the current preferred rebuild state for the list.
     *
     * @param context Current context
     *
     * @return ListRebuildMode
     */
    @BooklistBuilder.ListRebuildMode
    private int getPreferredListRebuildState(@NonNull final Context context) {
        String value = PreferenceManager.getDefaultSharedPreferences(context)
                                        .getString(Prefs.pk_booklist_rebuild_state, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return BooklistBuilder.PREF_REBUILD_SAVED_STATE;
    }

    @Override
    protected void onCleared() {

        if (mCursor != null) {
            mCursor.getBooklistBuilder().close();
            mCursor.close();
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
            mRebuildState = getPreferredListRebuildState(context);

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
        // and set the last/preferred bookshelf
        mCurrentBookshelf = Bookshelf.getPreferredBookshelf(context, mDb, true);
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
     * If the shelf was changed, a {@link #buildBookList} is started.
     *
     * @param context Current context
     * @param name    of desired Bookshelf
     *
     */
    public void setCurrentBookshelf(@NonNull final Context context,
                                    @Nullable final String name) {
        // this test should not actually be needed.
        if (name != null && !name.equalsIgnoreCase(getCurrentBookshelf().getName())) {
            mCurrentBookshelf = Bookshelf.getBookshelf(context, mDb, name, true);
            mCurrentBookshelf.setAsPreferred(context);
            // new shelf, build the list
            buildBookList(context);
        }
    }

    /**
     * Load and set the desired Bookshelf; do NOT set it as the preferred. Do NOT build the list
     *
     * @param id of Bookshelf
     */
    public void setCurrentBookshelf(final long id) {
        mCurrentBookshelf = mDb.getBookshelf(id);
    }

    @NonNull
    public Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);
        return mCurrentBookshelf;
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
        mRebuildState = getPreferredListRebuildState(context);
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
    public void buildBookList(@NonNull final Context context) {
        Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);

        mShowProgressBar.setValue(true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final BooklistStyle style = mCurrentBookshelf.getStyle(context, mDb);

        // get a new builder and add the required domains
        final BooklistBuilder blb = new BooklistBuilder(style, mCurrentBookshelf,
                                                        mRebuildState);

        // Add the fixed list of domains we always need.
        for (VirtualDomain domainDetails : FIXED_DOMAIN_LIST) {
            blb.addDomain(domainDetails);
        }

        // Add the conditional domains
        if (App.isUsed(prefs, DBDefinitions.KEY_EDITION_BITMASK)) {
            // The edition bitmask
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EDITION_BITMASK)));
        }

        if (App.isUsed(prefs, DBDefinitions.KEY_SIGNED)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_SIGNED,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_SIGNED)));
        }

        if (App.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BL_LOANEE_AS_BOOL,
                    DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN));
        }

        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_BOOKSHELF)) {
            // This collects a CSV list of the bookshelves the book is on.
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOKSHELF_CSV,
                    DAO.SqlColumns.EXP_BOOKSHELVES_CSV));
        }

        // we fetch ONLY the primary author
        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_AUTHOR_FORMATTED)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_AUTHOR_FORMATTED,
                    style.isShowAuthorByGivenNameFirst(context)
                    ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                    : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN));
        }
        // for now, don't get the author type.
        // if (style.isBookDetailUsed(prefs, DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)) {
        //     blb.addDomain(new BooklistBuilder.BookDomain(
        //         DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
        //         DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)));
        // }

        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_PUBLISHER)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_PUBLISHER,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_PUBLISHER)));
        }
        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_DATE_PUBLISHED)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_DATE_PUBLISHED,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_DATE_PUBLISHED)));
        }
        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_FORMAT)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_FORMAT,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_FORMAT)));
        }
        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_LOCATION)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_LOCATION,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LOCATION)));
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Log.d(TAG, "mSearchCriteria=" + mSearchCriteria);
        }

        // if we have a list of ID's, ignore other criteria
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

        // if we have any criteria set at all, the build should expand the book list.
        if (!mSearchCriteria.isEmpty()) {
            blb.setRebuildState(PREF_REBUILD_ALWAYS_EXPANDED);
        }

        new GetBookListTask(blb, mCursor, mDesiredCentralBookId, mOnGetBookListTaskListener)
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
     * @return the target rows to position the list
     */
    @NonNull
    public MutableLiveData<List<RowStateDAO.ListRowDetails>> onBuilderSucess() {
        return mBuilderSuccess;
    }

    @NonNull
    public MutableLiveData<Cursor> onBuilderFailed() {
        return mBuilderFailed;
    }

    @NonNull
    public BooklistCursor getListCursor() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor;
    }

    /**
     * Safely close the current cursor, and create a new one.
     */
    public void createNewListCursor() {
        @Nullable
        final BooklistCursor oldCursor = mCursor;
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        mCursor = mCursor.getBooklistBuilder().getNewListCursor();
        BooklistBuilder.closeCursor(oldCursor, mCursor);
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

        } else if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP)
                   == BooklistGroup.BOOK) {
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
        } else if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP)
                   == BooklistGroup.BOOK) {
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

    public boolean setSearchCriteria(@Nullable final Bundle bundle,
                                     final boolean clearFirst) {
        if (bundle != null) {
            return mSearchCriteria.from(bundle, clearFirst);
        } else {
            return false;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean reloadCurrentBookshelf(@NonNull final Context context) {
        final Bookshelf newBookshelf = Bookshelf.getPreferredBookshelf(context, mDb, true);
        if (!newBookshelf.equals(mCurrentBookshelf)) {
            // if it was.. switch to it.
            mCurrentBookshelf = newBookshelf;
            return true;
        }
        return false;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<String> onUserMessage() {
        return mUserMessage;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<Boolean> onNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<Boolean> onShowProgressBar() {
        return mShowProgressBar;
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

    /**
     * This is used to re-display the list in onResume.
     * i.e. mDesiredCentralBookId was set in #onActivityResult
     * but a rebuild was not needed.
     *
     * @return the target rows, or {@code null} if none.
     */
    @Nullable
    public ArrayList<RowStateDAO.ListRowDetails> getTargetRows() {
        if (mDesiredCentralBookId == 0) {
            return null;
        }
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        ArrayList<RowStateDAO.ListRowDetails> targetRows =
                mCursor.getBooklistBuilder().getTargetRows(mDesiredCentralBookId);

        mDesiredCentralBookId = 0;
        return targetRows;
    }

    public boolean toggleNode(final long rowId) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().toggleNode(rowId);
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
         * List of book ids to display.
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

        @Override
        @NonNull
        public String toString() {
            return "SearchCriteria{"
                   + "ftsAuthor=`" + ftsAuthor + '`'
                   + ", ftsTitle=`" + ftsTitle + '`'
                   + ", ftsSeries=`" + ftsSeries + '`'
                   + ", loanee=`" + loanee + '`'
                   + ", ftsKeywords=`" + ftsKeywords + '`'
                   + ", bookList=" + bookList
                   + '}';
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     */
    private static class GetBookListTask
            extends TaskBase<Void, BuilderResult> {

        /** the builder. */
        @NonNull
        private final BooklistBuilder mBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderResult mResultsHolder;
        private final long mDesiredCentralBookId;
        @Nullable
        private final BooklistCursor mCurrentListCursor;

        /**
         * Constructor.
         *
         * @param bookListBuilder      the builder
         * @param currentListCursor    Current displayed list cursor.
         *                             (needed for potential cleanup actions)
         * @param desiredCentralBookId current position in the list.
         * @param taskListener         listener
         */
        @UiThread
        GetBookListTask(@NonNull final BooklistBuilder bookListBuilder,
                        @Nullable final BooklistCursor currentListCursor,
                        final long desiredCentralBookId,
                        final TaskListener<BuilderResult> taskListener) {
            super(R.id.TASK_ID_GET_BOOKLIST, taskListener);

            mBuilder = bookListBuilder;
            mCurrentListCursor = currentListCursor;
            mDesiredCentralBookId = desiredCentralBookId;

            // output fields for the task.
            mResultsHolder = new BuilderResult();
        }

        @Override
        @NonNull
        @WorkerThread
        protected BuilderResult doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookListTask");
            try {
                // Build the underlying data
                // (performance measuring: this is where all the actual action is done)
                mBuilder.build();

                if (isCancelled()) {
                    // empty result
                    return mResultsHolder;
                }
                // process the results.

                // these are the row(s) we want to center the new list on.
                mResultsHolder.targetRows = mBuilder.getTargetRows(mDesiredCentralBookId);
                // Now we have the expanded groups as needed, get the list cursor
                // The cursor will hold a reference to the builder.
                mResultsHolder.listCursor = mBuilder.getNewListCursor();
                // get a count() from the cursor in background task because the setAdapter()
                // call will do a count() and potentially block the UI thread while it
                // pages through the entire cursor. Doing it here makes subsequent calls faster.
                mResultsHolder.listCursor.getCount();

                mResultsHolder.distinctBookCount = mBuilder.getDistinctBookCount();
                mResultsHolder.totalBookCount = mBuilder.getBookCount();

            } catch (@NonNull final Exception e) {
                // catch ALL exceptions, so we get them logged for certain.
                Logger.error(TAG, e);
                mException = e;
                cleanup();
            }

            return mResultsHolder;
        }

        @Override
        protected void onCancelled(@NonNull final BuilderResult result) {
            cleanup();
            super.onCancelled(result);
        }

        @AnyThread
        private void cleanup() {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "cleanup", mException);
            }
            BooklistBuilder.closeCursor(mResultsHolder.listCursor, mCurrentListCursor);
            mResultsHolder.clear();
        }
    }

    /** value class for the Builder. */
    public static class BuilderResult {

        /** Resulting Cursor; will be {@code null} if the list did not get build. */
        @Nullable
        BooklistCursor listCursor;

        /**
         * Pre-fetched from the resultListCursor's builder.
         * Should be ignored if resultListCursor is {@code null}
         */
        int totalBookCount;
        /**
         * Pre-fetched from the resultListCursor's builder.
         * Should be ignored if resultListCursor is {@code null}
         */
        int distinctBookCount;

        /**
         * Used to determine new cursor position; can be {@code null}.
         * Should be ignored if resultListCursor is {@code null}
         */
        @Nullable
        ArrayList<RowStateDAO.ListRowDetails> targetRows;

        @Nullable
        public List<RowStateDAO.ListRowDetails> getTargetRows() {
            return targetRows;
        }

        public void clear() {
            listCursor = null;
            targetRows = null;
            totalBookCount = 0;
            distinctBookCount = 0;
        }

        @Override
        @NonNull
        public String toString() {
            return "BuilderHolder{"
                   + "listCursor=" + listCursor
                   + ", totalBookCount=" + totalBookCount
                   + ", distinctBookCount=" + distinctBookCount
                   + ", targetRows=" + targetRows
                   + '}';
        }


    }
}
