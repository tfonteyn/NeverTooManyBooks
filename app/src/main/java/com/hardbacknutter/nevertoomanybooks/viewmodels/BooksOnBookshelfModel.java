/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FTSSearchActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DAOSql;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

public class BooksOnBookshelfModel
        extends VMTask<List<BooklistNode>> {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfModel";
    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";
    /** Allows to set an explicit shelf. */
    public static final String BKEY_BOOKSHELF = TAG + ":bs";

    /** The fixed list of domains we always need for building the book list. */
    private static final Collection<VirtualDomain> FIXED_DOMAIN_LIST = new ArrayList<>();

    static {
        FIXED_DOMAIN_LIST.add(
                // Title for displaying; do NOT sort on it
                new VirtualDomain(
                        DBDefinitions.DOM_TITLE,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE)));
        FIXED_DOMAIN_LIST.add(
                // Title for sorting
                new VirtualDomain(
                        DBDefinitions.DOM_TITLE_OB,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE_OB),
                        VirtualDomain.SORT_ASC));

        FIXED_DOMAIN_LIST.add(
                // the book language is needed for reordering titles
                new VirtualDomain(
                        DBDefinitions.DOM_BOOK_LANGUAGE,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LANGUAGE)));

        FIXED_DOMAIN_LIST.add(
                // Always get the read flag
                new VirtualDomain(
                        DBDefinitions.DOM_BOOK_READ,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_READ)));

        FIXED_DOMAIN_LIST.add(
                // Always get the Author ID
                // (the need for the name will depend on the style).
                new VirtualDomain(
                        DBDefinitions.DOM_FK_AUTHOR,
                        DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.KEY_FK_AUTHOR)));

        FIXED_DOMAIN_LIST.add(
                // We want the UUID for the book so we can get thumbnails
                new VirtualDomain(
                        DBDefinitions.DOM_BOOK_UUID,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_BOOK_UUID)));

        FIXED_DOMAIN_LIST.add(
                // Always get the ISBN
                new VirtualDomain(
                        DBDefinitions.DOM_BOOK_ISBN,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_ISBN)));

        // external site ID's
        for (Domain domain : SearchEngineRegistry.getExternalIdDomains()) {
            FIXED_DOMAIN_LIST.add(
                    new VirtualDomain(domain, DBDefinitions.TBL_BOOKS.dot(domain.getName())));
        }
    }

    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    /** Cache for all bookshelves. */
    private final List<Bookshelf> mBookshelfList = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /**
     * Flag (potentially) set in {@link BooksOnBookshelf} #onActivityResult.
     * Indicates if list rebuild is needed in {@link BooksOnBookshelf}#onResume.
     */
    private boolean mForceRebuildInOnResume;
    /** Flag to indicate that a list has been successfully loaded. */
    private boolean mListHasBeenLoaded;
    /** Currently selected bookshelf. */
    @Nullable
    private Bookshelf mBookshelf;

    /** The row id we want the new list to display more-or-less in the center. */
    private long mDesiredCentralBookId;

    /** Preferred booklist state in next rebuild. */
    @Booklist.ListRebuildMode
    private int mRebuildState;

    /** Current displayed list. */
    @Nullable
    private Booklist mBooklist;

    @Override
    protected void onCleared() {
        if (mBooklist != null) {
            mBooklist.close();
        }

        if (mDb != null) {
            mDb.close();
        }

        super.onCleared();
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

                // check for an explicit bookshelf set
                if (args.containsKey(BKEY_BOOKSHELF)) {
                    int exShelfId = args.getInt(BKEY_BOOKSHELF);
                    // might be null, that's ok.
                    mBookshelf = Bookshelf.getBookshelf(context, mDb, exShelfId);
                }
            }
        }

        // Set the last/preferred bookshelf if not explicitly set above
        if (mBookshelf == null) {
            mBookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED,
                                                // or use the default == first start
                                                Bookshelf.DEFAULT);
        }


        final Bundle currentArgs = savedInstanceState != null ? savedInstanceState : args;

        if (currentArgs == null) {
            // Get preferred booklist state to use from preferences;
            // always do this here in init, as the prefs might have changed anytime.
            mRebuildState = getPreferredListRebuildState(context);
        } else {
            // Unless set by the caller, preserve state when rebuilding/recreating etc
            mRebuildState = currentArgs.getInt(BKEY_LIST_STATE,
                                               Booklist.PREF_REBUILD_SAVED_STATE);
        }
    }


    public void setPreferredListRebuildState(@NonNull final Context context) {
        mRebuildState = getPreferredListRebuildState(context);
    }

    /**
     * Get the current preferred rebuild state for the list.
     *
     * @param context Current context
     *
     * @return ListRebuildMode
     */
    @Booklist.ListRebuildMode
    private int getPreferredListRebuildState(@NonNull final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
                                              .getString(Prefs.pk_booklist_rebuild_state, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return Booklist.PREF_REBUILD_SAVED_STATE;
    }

    public void setRebuildState(final int rebuildState) {
        mRebuildState = rebuildState;
    }


    /**
     * Get the Bookshelf list to show in the Spinner.
     * Will be empty until a call to {@link #reloadBookshelfList(Context)} is made.
     *
     * @return list
     */
    @NonNull
    public List<Bookshelf> getBookshelfList() {
        return mBookshelfList;
    }

    /**
     * Construct the Bookshelf list to show in the Spinner.
     *
     * @param context Current context.
     */
    public void reloadBookshelfList(@NonNull final Context context) {
        mBookshelfList.clear();
        mBookshelfList.add(
                Bookshelf.getBookshelf(context, mDb, Bookshelf.ALL_BOOKS, Bookshelf.ALL_BOOKS));
        mBookshelfList.addAll(mDb.getBookshelves());
    }


    /**
     * Find the position of the currently set Bookshelf in the Spinner.
     * (with fallback to the default, or to 0 if needed)
     *
     * @param context Current context.
     *
     * @return the position that reflects the current bookshelf.
     */
    public int getSelectedBookshelfSpinnerPosition(@NonNull final Context context) {
        Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);

        final List<Bookshelf> bookshelfList = getBookshelfList();
        // Not strictly needed, but guard against future changes
        if (bookshelfList.isEmpty()) {
            reloadBookshelfList(context);
        }

        // position we want to find
        Integer selectedPosition = null;
        // fallback if no selection found
        Integer defaultPosition = null;

        for (int i = 0; i < bookshelfList.size(); i++) {
            final Bookshelf bookshelf = bookshelfList.get(i);
            // find the position of the default shelf.
            if (bookshelf.getId() == Bookshelf.DEFAULT) {
                defaultPosition = i;
            }
            // find the position of the selected shelf
            if (bookshelf.getId() == mBookshelf.getId()) {
                selectedPosition = i;
            }
        }

        if (selectedPosition != null) {
            return selectedPosition;

        } else if (defaultPosition != null) {
            return defaultPosition;
        } else {
            // shouldn't get here... flw
            return 0;
        }
    }

    @NonNull
    public Bookshelf getSelectedBookshelf() {
        Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);
        return mBookshelf;
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context Current context
     * @param id      of desired Bookshelf
     */
    public void setSelectedBookshelf(@NonNull final Context context,
                                     final long id) {
        mBookshelf = mDb.getBookshelf(id);
        if (mBookshelf == null) {
            mBookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED,
                                                Bookshelf.ALL_BOOKS);
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mBookshelf.setAsPreferred(prefs);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean reloadSelectedBookshelf(@NonNull final Context context) {
        final Bookshelf newBookshelf =
                Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        if (!newBookshelf.equals(mBookshelf)) {
            // if it was.. switch to it.
            mBookshelf = newBookshelf;
            return true;
        }
        return false;
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
        Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);
        return mBookshelf.getStyle(context, mDb);
    }

    /**
     * Should be called after a style change.
     *
     * @param context Current context
     * @param style   the style to apply
     */
    public void onStyleChanged(@NonNull final Context context,
                               @NonNull final BooklistStyle style) {

        BooklistStyle.StyleDAO.updateOrInsert(mDb, style);
        onStyleChanged(context, style.getUuid());
    }

    /**
     * Should be called after a style change.
     * The style should exist (id != 0), or if it doesn't, the default style will be used instead.
     *
     * @param context Current context
     * @param uuid    the style to apply
     */
    public void onStyleChanged(@NonNull final Context context,
                               @NonNull final String uuid) {
        Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);

        // Always validate first
        final BooklistStyle style = BooklistStyle.getStyleOrDefault(context, mDb, uuid);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // set as the global default.
        BooklistStyle.setDefault(prefs, style.getUuid());
        // save the new bookshelf/style combination
        mBookshelf.setAsPreferred(prefs);
        mBookshelf.setStyle(context, mDb, style);
    }


    /**
     * Save current position information in the preferences.
     * We do this to preserve this data across application shutdown/startup.
     *
     * @param context       Current context
     * @param position      adapter list position; i.e. first visible position in the list
     * @param topViewOffset offset in pixels for the first visible position in the list
     */
    public void saveListPosition(@NonNull final Context context,
                                 final int position,
                                 final int topViewOffset) {
        if (mListHasBeenLoaded) {
            Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);
            mBookshelf.setTopListPosition(context, mDb, position, topViewOffset);
        }
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
    public boolean isAvailable(@NonNull final DataHolder rowData) {
        final String loanee;
        if (rowData.contains(DBDefinitions.KEY_LOANEE)) {
            loanee = rowData.getString(DBDefinitions.KEY_LOANEE);
        } else {
            loanee = mDb.getLoaneeByBookId(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
        }
        return (loanee == null) || loanee.isEmpty();
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


    /**
     * This is used to re-display the list in onResume.
     * i.e. {@link #mDesiredCentralBookId} was set in #onActivityResult
     * but a rebuild was not needed.
     *
     * @return the node(s), or {@code null} if none
     */
    @Nullable
    public ArrayList<BooklistNode> getTargetNodes() {
        if (mDesiredCentralBookId == 0) {
            return null;
        }

        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        final long bookId = mDesiredCentralBookId;
        mDesiredCentralBookId = 0;

        return mBooklist.getBookNodes(bookId);
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
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public BooklistNode setNode(final long nodeRowId,
                                @BooklistNode.NextState final int nextState,
                                final int relativeChildLevel) {
        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        return mBooklist.setNode(nodeRowId, nextState, relativeChildLevel);
    }

    public void expandAllNodes(@IntRange(from = 1) final int topLevel,
                               final boolean expand) {
        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        mBooklist.setAllNodes(topLevel, expand);
    }

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        return mBooklist.getCurrentBookIdList();
    }

    @Nullable
    public BooklistNode getNextBookWithoutCover(@NonNull final Context context,
                                                final long rowId) {
        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        return mBooklist.getNextBookWithoutCover(context, rowId);
    }


    @Nullable
    public String getHeaderFilterText(@NonNull final Context context) {
        final BooklistStyle style = getCurrentStyle(context);
        if (style.showHeader(context, BooklistStyle.HEADER_SHOW_FILTER)) {
            final Collection<String> filterText = new ArrayList<>();
            for (Filter<?> filter : style.getActiveFilters(context)) {
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
            //noinspection ConstantConditions
            final int totalBooks = mBooklist.countBooks();
            final int distinctBooks = mBooklist.countDistinctBooks();
            if (distinctBooks != totalBooks) {
                return context.getString(R.string.txt_displaying_n_books_in_m_entries,
                                         distinctBooks, totalBooks);
            } else {
                return context.getResources().getQuantityString(R.plurals.displaying_n_books,
                                                                distinctBooks, totalBooks);
            }
        }
        return null;
    }


    /**
     * Get the Book for the given row.
     *
     * @param rowData with data
     *
     * @return Book, or {@code null} if the row contains no Book id.
     */
    @Nullable
    public Book getBook(@NonNull final DataHolder rowData) {
        final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
        if (bookId > 0) {
            final Book book = new Book();
            book.load(bookId, mDb);
            return book;
        }
        return null;
    }

    /**
     * Get the Author for the given row.
     *
     * @param rowData with data
     *
     * @return Author, or {@code null} if the row contains no Author id.
     */
    @Nullable
    public Author getAuthor(@NonNull final DataHolder rowData) {
        if (rowData.contains(DBDefinitions.KEY_FK_AUTHOR)) {
            final long id = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
            if (id > 0) {
                return mDb.getAuthor(id);
            }
        } else if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
            final List<Author> authors = mDb.getAuthorsByBookId(
                    rowData.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!authors.isEmpty()) {
                return authors.get(0);
            }
        }
        return null;
    }

    /**
     * Get the Series for the given row.
     *
     * @param rowData with book data
     *
     * @return Series, or {@code null} if the row contains no Series id.
     */
    @Nullable
    public Series getSeries(@NonNull final DataHolder rowData) {
        if (rowData.contains(DBDefinitions.KEY_FK_SERIES)) {
            final long id = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
            if (id > 0) {
                return mDb.getSeries(rowData.getLong(DBDefinitions.KEY_FK_SERIES));
            }
        } else if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
            final ArrayList<Series> series = mDb.getSeriesByBookId(
                    rowData.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!series.isEmpty()) {
                return series.get(0);
            }
        }
        return null;
    }

    /**
     * Get the Publisher for the given row.
     *
     * @param rowData with book data
     *
     * @return Publisher, or {@code null} if the row contains no Publisher id.
     */
    @Nullable
    public Publisher getPublisher(@NonNull final DataHolder rowData) {
        final long id = rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER);
        if (id > 0) {
            return mDb.getPublisher(id);
        }
        return null;
    }

    @NonNull
    public String getBooklistTableName() {
        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        return mBooklist.getListTableName();
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     */
    public void buildBookList() {
        execute(R.id.TASK_ID_BOOKLIST_BUILDER);
    }

    /**
     * (re)Build the book list.
     *
     * @return the row(s) we want to center the new list on when displaying; can be {@code null}.
     */
    @Nullable
    @Override
    @WorkerThread
    protected List<BooklistNode> doWork(@NonNull final Context context) {
        Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);

        Thread.currentThread().setName(TAG);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final BooklistStyle style = mBookshelf.getStyle(context, mDb);

        Booklist builder = null;
        try {
            // get a new builder and add the required domains
            builder = new Booklist(mDb.getSyncDb(), style, mBookshelf, mRebuildState);

            // Add the fixed list of domains we always need.
            for (VirtualDomain domainDetails : FIXED_DOMAIN_LIST) {
                builder.addDomain(domainDetails);
            }

            // Add the conditional domains; global level.

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_EDITION_BITMASK)) {
                // The edition bitmask
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EDITION_BITMASK)));
            }

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SIGNED)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOK_SIGNED,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_SIGNED)));
            }

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_BOOK_CONDITION)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOK_CONDITION,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_BOOK_CONDITION)));
            }

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BL_LOANEE_AS_BOOL,
                        DAOSql.SqlColumns.EXP_LOANEE_AS_BOOLEAN));
            }

            // Add the conditional domains; style level.

            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_bookshelves)) {
                // This collects a CSV list of the bookshelves the book is on.
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                        DAOSql.SqlColumns.EXP_BOOKSHELF_NAME_CSV));
            }

            // we fetch ONLY the primary author
            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_author)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_AUTHOR_FORMATTED,
                        DAOSql.SqlColumns.getDisplayAuthor(DBDefinitions.TBL_AUTHORS.getAlias(),
                                                           style.isShowAuthorByGivenName(
                                                                   context))));
            }

            // for now, don't get the author type.
            //  if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_author_type)) {
            //      builder.addDomain(new VirtualDomain(
            //              DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
            //              DBDefinitions.TBL_BOOK_AUTHOR
            //              .dot(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)));
            //  }

            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_publisher)) {
                // Collect a CSV list of the publishers of the book
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_PUBLISHER_NAME_CSV,
                        DAOSql.SqlColumns.EXP_PUBLISHER_NAME_CSV));
            }
            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_pub_date)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_DATE_PUBLISHED,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_DATE_PUBLISHED)));
            }
            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_format)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOK_FORMAT,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_FORMAT)));
            }
            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_location)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOK_LOCATION,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LOCATION)));
            }
            if (style.useBookDetail(context, prefs, BooklistStyle.pk_book_show_rating)) {
                builder.addDomain(new VirtualDomain(
                        DBDefinitions.DOM_BOOK_RATING,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_RATING)));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Log.d(TAG, "mSearchCriteria=" + mSearchCriteria);
            }

            // if we have a list of ID's, ignore other criteria
            if (mSearchCriteria.hasIdList()) {
                builder.setFilterOnBookIdList(mSearchCriteria.bookIdList);

            } else {
                // Criteria supported by FTS
                builder.setFilter(mSearchCriteria.ftsAuthor,
                                  mSearchCriteria.ftsTitle,
                                  mSearchCriteria.ftsSeries,
                                  mSearchCriteria.ftsPublisher,
                                  mSearchCriteria.ftsKeywords);

                builder.setFilterOnLoanee(mSearchCriteria.loanee);
            }

            // if we have any criteria set at all, the build should expand the book list.
            if (!mSearchCriteria.isEmpty()) {
                builder.setRebuildState(Booklist.PREF_REBUILD_ALWAYS_EXPANDED);
            }

            // Build the underlying data
            builder.build(context);

            // pre-count and cache (in the builder) these while we're in the background.
            // They are used for the header, and will not change even if the list cursor changes.
            if (style.showHeader(context, BooklistStyle.HEADER_SHOW_BOOK_COUNT)) {
                builder.countBooks();
                builder.countDistinctBooks();
            }

            // Get the row(s) which will be used to determine new cursor position
            @Nullable
            final ArrayList<BooklistNode> targetRows =
                    builder.getBookNodes(mDesiredCentralBookId);

            // the new build is completely done. We can safely discard the previous one.
            if (mBooklist != null) {
                mBooklist.close();
            }
            mBooklist = builder;

            // Save a flag to say list was loaded at least once successfully
            mListHasBeenLoaded = true;

            // preserve the new state by default
            mRebuildState = Booklist.PREF_REBUILD_SAVED_STATE;

            return targetRows;

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            if (builder != null) {
                builder.close();
            }
            throw e;

        } finally {
            // reset the central book id.
            mDesiredCentralBookId = 0;
        }
    }

    /**
     * Create a new list cursor.
     * <p>
     * Note this is a {@link BooklistCursor}
     *
     * @return cursor
     */
    @NonNull
    public BooklistCursor newListCursor() {
        Objects.requireNonNull(mBooklist, ErrorMsg.NULL_BOOK_LIST);
        return mBooklist.newListCursor();
    }

    public List<Author> getAuthorsByBookId(final long bookId) {
        return mDb.getAuthorsByBookId(bookId);
    }

    @NonNull
    public ArrayList<Long> getBookIdsByAuthor(final long authorId) {
        return mDb.getBookIdsByAuthor(authorId);
    }

    @NonNull
    public ArrayList<Long> getBookIdsBySeries(final long seriesId) {
        return mDb.getBookIdsBySeries(seriesId);
    }

    @NonNull
    public ArrayList<Long> getBookIdsByPublisher(final long publisherId) {
        return mDb.getBookIdsByPublisher(publisherId);
    }

    public boolean setAuthorComplete(final long authorId,
                                     final boolean isComplete) {
        return mDb.setAuthorComplete(authorId, isComplete);
    }

    public boolean setSeriesComplete(final long seriesId,
                                     final boolean isComplete) {
        return mDb.setSeriesComplete(seriesId, isComplete);
    }

    public boolean setBookRead(final long bookId,
                               final boolean isRead) {
        return mDb.setBookRead(bookId, isRead);
    }

    public boolean delete(@NonNull final Context context,
                          @NonNull final Series series) {
        return mDb.delete(context, series);
    }

    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {
        return mDb.delete(context, publisher);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean lendBook(final long bookId,
                            @Nullable final String loanee) {
        return mDb.lendBook(bookId, loanee);
    }

    public boolean deleteBook(@NonNull final Context context,
                              @IntRange(from = 1) final long bookId) {
        return mDb.deleteBook(context, bookId);
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
         * Bundle key for Publisher search text
         * (all DB KEY's and the ARRAY key is for publishers with verified names).
         */
        public static final String BKEY_SEARCH_TEXT_PUBLISHER = TAG + ":publisher";

        /**
         * List of book ID's to display.
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
         * Publisher to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         */
        @Nullable
        String ftsPublisher;

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
         * Name of the person we lend books to, to use in search query.
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
        String ftsKeywords;

        public void clear() {
            ftsKeywords = null;
            ftsAuthor = null;
            ftsPublisher = null;
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
            if (ftsPublisher != null && !ftsPublisher.isEmpty()) {
                list.add(ftsPublisher);
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
            if (bundle.containsKey(BKEY_SEARCH_TEXT_PUBLISHER)) {
                ftsPublisher = bundle.getString(BKEY_SEARCH_TEXT_PUBLISHER);
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
                  .putExtra(BKEY_SEARCH_TEXT_PUBLISHER, ftsPublisher)
                  .putExtra(DBDefinitions.KEY_TITLE, ftsTitle)
                  .putExtra(DBDefinitions.KEY_SERIES_TITLE, ftsSeries)

                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)
                  .putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList);
        }

        public boolean isEmpty() {
            return (ftsKeywords == null || ftsKeywords.isEmpty())
                   && (ftsAuthor == null || ftsAuthor.isEmpty())
                   && (ftsPublisher == null || ftsPublisher.isEmpty())
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
                   + ", ftsPublisher=`" + ftsPublisher + '`'
                   + ", loanee=`" + loanee + '`'
                   + ", ftsKeywords=`" + ftsKeywords + '`'
                   + ", bookList=" + bookIdList
                   + '}';
        }
    }
}
