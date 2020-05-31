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
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

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
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

public class BooksOnBookshelfModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfModel";
    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";

    /** The fixed list of domains we always need in {@link #buildBookList}. */
    private static final List<VirtualDomain> FIXED_DOMAIN_LIST = Arrays.asList(
            // Title for displaying; do NOT sort on it
            new VirtualDomain(
                    DBDefinitions.DOM_TITLE,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE)),
            // Title for sorting
            new VirtualDomain(
                    DBDefinitions.DOM_TITLE_OB,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE_OB),
                    VirtualDomain.SORT_ASC),

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
            //NEWTHINGS: add new site specific ID: add VirtualDomain
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
    private final MutableLiveData<List<RowStateDAO.Node>> mBuilderSuccess =
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
    private final List<BookshelfSpinnerEntry> mBookshelfNameList = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /** Lazy init, always use {@link #getGoodreadsTaskListener(Context)}. */
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
     * Stores the book id for the desired list position.
     * Used to call {@link GetBookListTask}. Reset afterwards.
     */
    private long mDesiredCentralBookId;

    /** Preferred booklist state in next rebuild. */
    @BooklistBuilder.ListRebuildMode
    private int mRebuildState;
    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks;
    /** Total number of unique books in current list. */
    private int mUniqueBooks;
    /** Current displayed list cursor. */
    @Nullable
    private BooklistCursor mCursor;
    /**
     * Listener for {@link GetBookListTask} results.
     */
    private final TaskListener<BuilderResult> mOnGetBookListTaskListener =
            new TaskListener<BuilderResult>() {
                @Override
                public void onFinished(@NonNull final FinishMessage<BuilderResult> message) {
                    mShowProgressBar.setValue(false);

                    // reset the central book id.
                    mDesiredCentralBookId = 0;

                    switch (message.status) {
                        case Success: {
                            // sanity check
                            Objects.requireNonNull(message.result, ErrorMsg.NULL_CURSOR);
                            Objects.requireNonNull(message.result.listCursor, ErrorMsg.NULL_CURSOR);

                            // Save a flag to say list was loaded at least once successfully
                            mListHasBeenLoaded = true;

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
                            break;
                        }
                        case Cancelled:
                            // just restore the old list if we can
                            if (mListHasBeenLoaded) {
                                mBuilderFailed.setValue(mCursor);
                            } else {
                                // Something is REALLY BAD
                                throw new IllegalStateException("BuilderResult=" + message);
                            }
                            break;

                        case Failed:
                            // Something is REALLY BAD
                            throw new IllegalStateException("BuilderResult=" + message);
                    }
                }
            };

    /**
     * Get the current preferred rebuild state for the list.
     *
     * @param context Current context
     *
     * @return ListRebuildMode
     */
    @BooklistBuilder.ListRebuildMode
    private int getPreferredListRebuildState(@NonNull final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
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

        // Set the last/preferred bookshelf
        mCurrentBookshelf =
                Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
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
    public List<BookshelfSpinnerEntry> getBookshelfSpinnerList() {
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
        mBookshelfNameList.add(new BookshelfSpinnerEntry(
                Bookshelf.getBookshelf(context, mDb, Bookshelf.ALL_BOOKS, Bookshelf.ALL_BOOKS)));

        int selectedPosition = 0;

        // position of the default shelf
        int defaultPosition = 0;

        // start at 1, as position 0 is 'All Books'
        int count = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.getId() == Bookshelf.DEFAULT) {
                defaultPosition = count;
            }

            if (bookshelf.getId() == getCurrentBookshelf().getId()) {
                selectedPosition = count;
            }

            mBookshelfNameList.add(new BookshelfSpinnerEntry(bookshelf));
            count++;
        }

        return selectedPosition != 0 ? selectedPosition : defaultPosition;
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context Current context
     * @param id      of desired Bookshelf
     */
    public void setCurrentBookshelf(@NonNull final Context context,
                                    final long id) {
        mCurrentBookshelf = mDb.getBookshelf(id);
        if (mCurrentBookshelf == null) {
            mCurrentBookshelf =
                    Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        }
        mCurrentBookshelf.setAsPreferred(context);
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context   Current context
     * @param bookshelf desired Bookshelf
     */
    public void setCurrentBookshelf(@NonNull final Context context,
                                    @NonNull final Bookshelf bookshelf) {
        mCurrentBookshelf = bookshelf;
        mCurrentBookshelf.setAsPreferred(context);
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

        // always save a new style to the database
        if (style.getId() == 0) {
            BooklistStyle.StyleDAO.updateOrInsert(mDb, style);
        }

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

    public void setRebuildState(final int rebuildState) {
        mRebuildState = rebuildState;
    }

    /**
     * Save current position information in the preferences.
     * We do this to preserve this data across application shutdown/startup.
     *
     * @param context       Current context
     * @param position      adapter list position; i.e. first visible position in the list
     * @param topViewOffset offset in pixels for the first visible position in the list
     * @param rowId         the row id for that position
     */
    public void saveListPosition(@NonNull final Context context,
                                 final int position,
                                 final int topViewOffset,
                                 final long rowId) {
        if (mListHasBeenLoaded) {
            Objects.requireNonNull(mCurrentBookshelf, ErrorMsg.NULL_CURRENT_BOOKSHELF);
            mCurrentBookshelf.setTopListPosition(context, mDb, position, topViewOffset, rowId);
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

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final BooklistStyle style = mCurrentBookshelf.getStyle(context, mDb);

        // get a new builder and add the required domains
        final BooklistBuilder blb = new BooklistBuilder(style, mCurrentBookshelf, mRebuildState);

        // Add the fixed list of domains we always need.
        for (VirtualDomain domainDetails : FIXED_DOMAIN_LIST) {
            blb.addDomain(domainDetails);
        }

        // Add the conditional domains; global level.

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_EDITION_BITMASK)) {
            // The edition bitmask
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EDITION_BITMASK)));
        }

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SIGNED)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_SIGNED,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_SIGNED)));
        }

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_BOOK_CONDITION)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOK_CONDITION,
                    DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_BOOK_CONDITION)));
        }

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BL_LOANEE_AS_BOOL,
                    DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN));
        }

        // Add the conditional domains; style level.

        if (style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_BOOKSHELF_NAME)) {
            // This collects a CSV list of the bookshelves the book is on.
            blb.addDomain(new VirtualDomain(
                    DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                    DAO.SqlColumns.EXP_BOOKSHELF_NAME_CSV));
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
            blb.setFilterOnBookIdList(mSearchCriteria.bookIdList);

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
            blb.setRebuildState(BooklistBuilder.PREF_REBUILD_ALWAYS_EXPANDED);
        }

        new GetBookListTask(blb, mCursor, mDesiredCentralBookId, mOnGetBookListTaskListener)
                .execute();
    }

    /**
     * The result of {@link GetBookListTask}.
     *
     * @return the target rows to position the list
     */
    @NonNull
    public MutableLiveData<List<RowStateDAO.Node>> onBuilderSuccess() {
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
        final Bookshelf newBookshelf =
                Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        if (!newBookshelf.equals(mCurrentBookshelf)) {
            // if it was.. switch to it.
            mCurrentBookshelf = newBookshelf;
            return true;
        }
        return false;
    }

    /**
     * Called when a task wants to display a user message.
     *
     * @return Observable: string to display
     */
    @NonNull
    public MutableLiveData<String> onUserMessage() {
        return mUserMessage;
    }

    /**
     * Called when a task needs Goodreads access, and current has no access.
     *
     * @return Observable: {@code true} when access is needed
     */
    @NonNull
    public MutableLiveData<Boolean> onNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    /**
     * Called when a task wants to display (or hide) a progressbar.
     *
     * @return Observable: {@code true} to display, {@code false} to hide
     */
    @NonNull
    public MutableLiveData<Boolean> onShowProgressBar() {
        return mShowProgressBar;
    }

    @NonNull
    public TaskListener<Integer> getGoodreadsTaskListener(@NonNull final Context context) {
        if (mGoodreadsTaskListener == null) {
            mGoodreadsTaskListener = new TaskListener<Integer>() {

                @Override
                public void onFinished(@NonNull final FinishMessage<Integer> message) {
                    if (GoodreadsHandler.authNeeded(message)) {
                        mNeedsGoodreads.setValue(true);
                    } else {
                        mUserMessage.setValue(GoodreadsHandler.digest(context, message));
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
     * i.e. {@link #mDesiredCentralBookId} was set in #onActivityResult
     * but a rebuild was not needed.
     *
     * @return the node(s), or {@code null} if none
     */
    @Nullable
    public ArrayList<RowStateDAO.Node> getTargetNodes() {
        if (mDesiredCentralBookId == 0) {
            return null;
        }

        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        final long bookId = mDesiredCentralBookId;
        mDesiredCentralBookId = 0;

        return mCursor.getBooklistBuilder().getBookNodes(bookId);
    }

    /**
     * Set the desired state on the given node.
     *
     * @param nodeRowId          list-view row id of the node in the list
     * @param nextState          the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     *
     * @return the node
     */
    @NonNull
    public RowStateDAO.Node toggleNode(final long nodeRowId,
                                       @RowStateDAO.Node.NodeNextState final int nextState,
                                       final int relativeChildLevel) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().toggleNode(nodeRowId, nextState, relativeChildLevel);
    }

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().getCurrentBookIdList();
    }

    @Nullable
    public RowStateDAO.Node getNextBookWithoutCover(@NonNull final Context context,
                                                    final long rowId) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().getNextBookWithoutCover(context, rowId);
    }

    @NonNull
    public String createFlattenedBooklist() {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        return mCursor.getBooklistBuilder().createFlattenedBooklist();
    }

    public void expandAllNodes(@IntRange(from = 1) final int topLevel,
                               final boolean expand) {
        Objects.requireNonNull(mCursor, ErrorMsg.NULL_CURSOR);
        mCursor.getBooklistBuilder().expandAllNodes(topLevel, expand);
    }

    @Nullable
    public String getHeaderFilterText(@NonNull final Context context) {
        final BooklistStyle style = getCurrentStyle(context);
        if (style.showHeader(context, BooklistStyle.HEADER_SHOW_FILTER)) {
            final Collection<String> filterText = new ArrayList<>();
            for (Filter filter : style.getActiveFilters(context)) {
                filterText.add(filter.getLabel(context));
            }

            final String ftsSearchText = mSearchCriteria.getFtsSearchText();
            if (!ftsSearchText.isEmpty()) {
                filterText.add('"' + ftsSearchText + '"');
            }

            if (!filterText.isEmpty()) {
                return context.getString(R.string.lbl_search_filtered_on_x,
                                         TextUtils.join(", ", filterText));
            }
        }
        return null;
    }

    @Nullable
    public String getHeaderStyleName(@NonNull final Context context) {
        final BooklistStyle style = getCurrentStyle(context);
        if (style.showHeader(context, BooklistStyle.HEADER_SHOW_STYLE_NAME)) {
            return style.getLabel(context);
        }
        return null;
    }

    @Nullable
    public String getHeaderBookCount(@NonNull final Context context) {
        final BooklistStyle style = getCurrentStyle(context);
        if (style.showHeader(context, BooklistStyle.HEADER_SHOW_BOOK_COUNT)) {
            if (mUniqueBooks != mTotalBooks) {
                return context.getString(R.string.txt_displaying_n_books_in_m_entries,
                                         mUniqueBooks, mTotalBooks);
            } else {
                return context.getResources().getQuantityString(R.plurals.displaying_n_books,
                                                                mUniqueBooks, mUniqueBooks);
            }
        }
        return null;
    }

    @NonNull
    public Book getBook(final long bookId) {
        return new Book(bookId, mDb);
    }

    @Nullable
    public Series getSeries(final long seriesId) {
        return mDb.getSeries(seriesId);
    }

    @Nullable
    public Author getAuthor(final long authorId) {
        return mDb.getAuthor(authorId);
    }

    /**
     * A Spinner entry with a Bookshelf.
     */
    public static class BookshelfSpinnerEntry {

        @NonNull
        private final Bookshelf mBookshelf;

        BookshelfSpinnerEntry(@NonNull final Bookshelf bookshelf) {
            mBookshelf = bookshelf;
        }

        @NonNull
        public Bookshelf getBookshelf() {
            return mBookshelf;
        }

        /**
         * NOT debug, used by the Spinner view.
         *
         * @return entry title
         */
        @Override
        @NonNull
        public String toString() {
            return mBookshelf.getName();
        }
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     */
    public static class SearchCriteria {

        /** Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "SearchCriteria";

        /** Bundle key for generic search text. */
        public static final String BKEY_SEARCH_TEXT_KEYWORDS = TAG + ":keywords";

        /**
         * Bundle key for Author search text
         * (all DB KEY's and the ARRAY key is for authors with verified names).
         */
        public static final String BKEY_SEARCH_TEXT_AUTHOR = TAG + ":author";

        /**
         * List of book ids to display.
         * The RESULT of a search with {@link FTSSearchActivity}
         * which can be re-used for the builder.
         */
        @Nullable
        ArrayList<Long> bookIdList;

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

            bookIdList = null;
        }

        /**
         * Get a single string with all FTS search words, for displaying.
         *
         * @return csv string, can be empty, but never {@code null}.
         */
        @NonNull
        String getFtsSearchText() {
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

            if (bundle.containsKey(BKEY_SEARCH_TEXT_KEYWORDS)) {
                setKeywords(bundle.getString(BKEY_SEARCH_TEXT_KEYWORDS));
                isSet = true;
            }
            if (bundle.containsKey(BKEY_SEARCH_TEXT_AUTHOR)) {
                ftsAuthor = bundle.getString(BKEY_SEARCH_TEXT_AUTHOR);
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
            if (bundle.containsKey(Book.BKEY_BOOK_ID_ARRAY)) {
                //noinspection unchecked
                bookIdList = (ArrayList<Long>) bundle.getSerializable(Book.BKEY_BOOK_ID_ARRAY);
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
            intent.putExtra(BKEY_SEARCH_TEXT_KEYWORDS, ftsKeywords)
                  .putExtra(BKEY_SEARCH_TEXT_AUTHOR, ftsAuthor)
                  .putExtra(DBDefinitions.KEY_TITLE, ftsTitle)
                  .putExtra(DBDefinitions.KEY_SERIES_TITLE, ftsSeries)

                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)
                  .putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList);
        }

        public boolean isEmpty() {
            return (ftsKeywords == null || ftsKeywords.isEmpty())
                   && (ftsAuthor == null || ftsAuthor.isEmpty())
                   && (ftsTitle == null || ftsTitle.isEmpty())
                   && (ftsSeries == null || ftsSeries.isEmpty())

                   && (loanee == null || loanee.isEmpty())
                   && (bookIdList == null || bookIdList.isEmpty());
        }

        boolean hasIdList() {
            return bookIdList != null && !bookIdList.isEmpty();
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
                   + ", bookList=" + bookIdList
                   + '}';
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     */
    private static class GetBookListTask
            extends TaskBase<BuilderResult> {

        /** the builder. */
        @NonNull
        private final BooklistBuilder mBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderResult mResultsHolder;
        /** The row id we want the new list to display more-or-less in the center. */
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
        protected BuilderResult doInBackground(@Nullable final Void... voids) {
            Thread.currentThread().setName(TAG);
            final Context context = App.getTaskContext();

            try {
                // Build the underlying data
                // (performance measuring: this is where all the actual action is done)
                mBuilder.build(context);

                if (isCancelled()) {
                    // empty result
                    return mResultsHolder;
                }
                // process the results.

                // these are the row(s) we want to center the new list on.
                mResultsHolder.targetRows = mBuilder.getBookNodes(mDesiredCentralBookId);
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
                Logger.error(context, TAG, e);
                mException = e;
                cleanup();
            }

            return mResultsHolder;
        }

        @Override
        protected void onCancelled(@Nullable final BuilderResult result) {
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
        ArrayList<RowStateDAO.Node> targetRows;

        @Nullable
        public List<RowStateDAO.Node> getTargetRows() {
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
