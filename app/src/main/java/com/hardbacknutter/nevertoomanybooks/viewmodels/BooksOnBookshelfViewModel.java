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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DAOSql;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

public class BooksOnBookshelfViewModel
        extends VMTask<List<BooklistNode>> {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfViewModel";
    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";
    /** Allows to set an explicit shelf. */
    public static final String BKEY_BOOKSHELF = TAG + ":bs";

    /** The fixed list of domains we always need for building the book list. */
    private final Collection<DomainExpression> mFixedDomainList = new ArrayList<>();
    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    /** Cache for all bookshelves. */
    private final List<Bookshelf> mBookshelfList = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /**
     * Flag (potentially) set when coming back from another Activity.
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

    private void initFixedDomainList() {
        mFixedDomainList.add(
                // Title for displaying; do NOT sort on it
                new DomainExpression(
                        DBDefinitions.DOM_TITLE,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE)));
        mFixedDomainList.add(
                // Title for sorting
                new DomainExpression(
                        DBDefinitions.DOM_TITLE_OB,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_TITLE_OB),
                        DomainExpression.SORT_ASC));

        mFixedDomainList.add(
                // the book language is needed for reordering titles
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_LANGUAGE,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LANGUAGE)));

        mFixedDomainList.add(
                // Always get the read flag
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_READ,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_READ)));

        mFixedDomainList.add(
                // Always get the Author ID
                // (the need for the name will depend on the style).
                new DomainExpression(
                        DBDefinitions.DOM_FK_AUTHOR,
                        DBDefinitions.TBL_BOOK_AUTHOR.dot(DBDefinitions.KEY_FK_AUTHOR)));

        mFixedDomainList.add(
                // We want the UUID for the book so we can get thumbnails
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_UUID,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_BOOK_UUID)));

        mFixedDomainList.add(
                // Always get the ISBN
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_ISBN,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_ISBN)));

        // external site ID's
        for (final Domain domain : SearchEngineRegistry.getInstance().getExternalIdDomains()) {
            mFixedDomainList.add(
                    new DomainExpression(domain, DBDefinitions.TBL_BOOKS.dot(domain.getName())));
        }
    }

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
                     @Nullable final Bundle args) {

        if (mDb == null) {
            initFixedDomainList();

            mDb = new DAO(TAG);

            // first start of the activity, read from user preference
            mRebuildState = getPreferredListRebuildState(context);

            if (args != null) {
                // extract search criteria if any are present
                mSearchCriteria.from(args, true);

                // allow the caller to override the user preference
                if (args.containsKey(BKEY_LIST_STATE)) {
                    mRebuildState = args.getInt(BKEY_LIST_STATE);
                }

                // check for an explicit bookshelf set
                if (args.containsKey(BKEY_BOOKSHELF)) {
                    // might be null, that's ok.
                    mBookshelf = Bookshelf.getBookshelf(context, mDb, args.getInt(BKEY_BOOKSHELF));
                }
            }
        } else {
            // always preserve the state when the hosting fragment was revived
            mRebuildState = Booklist.PREF_REBUILD_SAVED_STATE;
        }

        // Set the last/preferred bookshelf if not explicitly set above
        if (mBookshelf == null) {
            mBookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED,
                                                // or use the default == first start
                                                Bookshelf.DEFAULT);
        }
    }

    public void resetPreferredListRebuildState(@NonNull final Context context) {
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
        mBookshelfList.add(Bookshelf.getBookshelf(context, mDb, Bookshelf.ALL_BOOKS));
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
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);

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
    public Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
        return mBookshelf;
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context Current context
     * @param id      of desired Bookshelf
     */
    public void setCurrentBookshelf(@NonNull final Context context,
                                    final long id) {
        mBookshelf = mDb.getBookshelf(id);
        if (mBookshelf == null) {
            mBookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED,
                                                Bookshelf.ALL_BOOKS);
        }
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        mBookshelf.setAsPreferred(global);
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
    public ListStyle getCurrentStyle(@NonNull final Context context) {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
        return mBookshelf.getStyle(context, mDb);
    }

    /**
     * Should be called after <strong>a style was edited</strong>.
     *
     * @param context Current context
     * @param uuid    the style which was edited
     */
    public void onStyleEdited(@NonNull final Context context,
                              @NonNull final String uuid) {

        @Nullable
        final ListStyle style = StyleDAO.getStyle(context, mDb, uuid);
        // Sanity check. The uuid SHOULD be valid, and hence the style SHOULD be found
        if (style != null) {
            onStyleChanged(context, style.getUuid());
        }
    }

    /**
     * Should be called after <strong>a style was changed/selected</strong>.
     * The style should exist (id != 0), or if it doesn't, the default style will be used instead.
     *
     * @param context Current context
     * @param uuid    the style to apply
     */
    public void onStyleChanged(@NonNull final Context context,
                               @NonNull final String uuid) {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);

        // Always validate first
        final ListStyle style = StyleDAO.getStyleOrDefault(context, mDb, uuid);

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        // set as the global default.
        StyleDAO.setDefault(global, style.getUuid());
        // save the new bookshelf/style combination
        mBookshelf.setAsPreferred(global);
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
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
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
    public void setDesiredCentralBookId(@IntRange(from = 1) final long bookId) {
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
     * i.e. {@link #mDesiredCentralBookId} was set, but a rebuild was not needed.
     *
     * @return the node(s), or {@code null} if none
     */
    @Nullable
    public ArrayList<BooklistNode> getTargetNodes() {
        if (mDesiredCentralBookId == 0) {
            return null;
        }

        Objects.requireNonNull(mBooklist, Booklist.TAG);
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
        Objects.requireNonNull(mBooklist, Booklist.TAG);
        return mBooklist.setNode(nodeRowId, nextState, relativeChildLevel);
    }

    public void expandAllNodes(@IntRange(from = 1) final int topLevel,
                               final boolean expand) {
        Objects.requireNonNull(mBooklist, Booklist.TAG);
        mBooklist.setAllNodes(topLevel, expand);
    }

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        Objects.requireNonNull(mBooklist, Booklist.TAG);
        return mBooklist.getCurrentBookIdList();
    }

    @Nullable
    public BooklistNode getNextBookWithoutCover(@NonNull final Context context,
                                                final long rowId) {
        Objects.requireNonNull(mBooklist, Booklist.TAG);
        return mBooklist.getNextBookWithoutCover(context, rowId);
    }

    @Nullable
    public String getHeaderFilterText(@NonNull final Context context) {
        final ListStyle style = getCurrentStyle(context);
        if (style.isShowHeader(ListStyle.HEADER_SHOW_FILTER)) {

            final Collection<String> filterText = style
                    .getFilters()
                    .getAll()
                    .stream()
                    .filter(f -> f.isActive(context))
                    .map(filter -> filter.getLabel(context))
                    .collect(Collectors.toList());

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
        final ListStyle style = getCurrentStyle(context);
        if (style.isShowHeader(ListStyle.HEADER_SHOW_STYLE_NAME)) {
            return style.getLabel(context);
        }
        return null;
    }

    @Nullable
    public String getHeaderBookCount(@NonNull final Context context) {
        final ListStyle style = getCurrentStyle(context);
        if (style.isShowHeader(ListStyle.HEADER_SHOW_BOOK_COUNT)) {
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

    @NonNull
    public String getBookNavigationTableName() {
        Objects.requireNonNull(mBooklist, Booklist.TAG);
        return mBooklist.getNavigationTableName();
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
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);

        Thread.currentThread().setName(TAG);

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final ListStyle style = mBookshelf.getStyle(context, mDb);

        Booklist builder = null;
        try {
            // get a new builder and add the required domains
            builder = new Booklist(mDb.getSyncDb(), style, mBookshelf, mRebuildState);

            // Add the fixed list of domains we always need.
            for (final DomainExpression domainDetails : mFixedDomainList) {
                builder.addDomain(domainDetails);
            }

            // Add Calibre bridging data ?
            if (CalibreContentServer.isShowSyncMenus(global)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_CALIBRE_BOOK_ID,
                        DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBDefinitions.KEY_CALIBRE_BOOK_ID)));
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_CALIBRE_BOOK_UUID,
                        DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBDefinitions.KEY_CALIBRE_BOOK_UUID)));
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_CALIBRE_BOOK_LIBRARY_ID,
                        DBDefinitions.TBL_CALIBRE_BOOKS
                                .dot(DBDefinitions.KEY_CALIBRE_BOOK_LIBRARY_ID)));
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_CALIBRE_BOOK_MAIN_FORMAT,
                        DBDefinitions.TBL_CALIBRE_BOOKS
                                .dot(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT)));
            }

            // Add the conditional domains; global level.

            if (DBDefinitions.isUsed(global, DBDefinitions.KEY_EDITION_BITMASK)) {
                // The edition bitmask
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_EDITION_BITMASK)));
            }

            if (DBDefinitions.isUsed(global, DBDefinitions.KEY_SIGNED)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_SIGNED,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_SIGNED)));
            }

            if (DBDefinitions.isUsed(global, DBDefinitions.KEY_BOOK_CONDITION)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_CONDITION,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_BOOK_CONDITION)));
            }

            if (DBDefinitions.isUsed(global, DBDefinitions.KEY_LOANEE)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BL_LOANEE_AS_BOOL,
                        DAOSql.SqlColumns.EXP_LOANEE_AS_BOOLEAN));
            }

            // Add the conditional domains; style level.
            final ListScreenBookFields bookFields = style.getListScreenBookFields();

            if (bookFields.isShowField(global, ListScreenBookFields.PK_BOOKSHELVES)) {
                // This collects a CSV list of the bookshelves the book is on.
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                        DAOSql.SqlColumns.EXP_BOOKSHELF_NAME_CSV));
            }

            // we fetch ONLY the primary author
            if (bookFields.isShowField(global, ListScreenBookFields.PK_AUTHOR)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_AUTHOR_FORMATTED, DAOSql.SqlColumns
                        .getDisplayAuthor(DBDefinitions.TBL_AUTHORS.getAlias(),
                                          style.isShowAuthorByGivenName())));
            }

            // for now, don't get the author type.
//              if (bookFields.isShowField(context, ListScreenBookFields.PK_AUTHOR_TYPE)) {
//                  builder.addDomain(new DomainExpression(
//                          DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
//                          DBDefinitions.TBL_BOOK_AUTHOR
//                          .dot(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)));
//              }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_PUBLISHER)) {
                // Collect a CSV list of the publishers of the book
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_PUBLISHER_NAME_CSV,
                        DAOSql.SqlColumns.EXP_PUBLISHER_NAME_CSV));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_PUB_DATE)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_DATE_PUBLISHED,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_DATE_PUBLISHED)));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_FORMAT)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_FORMAT,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_FORMAT)));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_LOCATION)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_LOCATION,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_LOCATION)));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_RATING)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_RATING,
                        DBDefinitions.TBL_BOOKS.dot(DBDefinitions.KEY_RATING)));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "mSearchCriteria=" + mSearchCriteria);
            }

            // if we have a list of ID's, ignore other criteria
            if (mSearchCriteria.hasIdList()) {
                builder.addFilterOnBookIdList(mSearchCriteria.getBookIdList());

            } else {
                // Criteria supported by FTS
                builder.addFilterOnKeywords(mSearchCriteria.getFtsAuthor(),
                                            mSearchCriteria.getFtsTitle(),
                                            mSearchCriteria.getFtsSeries(),
                                            mSearchCriteria.getFtsPublisher(),
                                            mSearchCriteria.getFtsKeywords());

                builder.addFilterOnLoanee(mSearchCriteria.getLoanee());
            }

            // if we have any criteria set at all, the build should expand the book list.
            if (!mSearchCriteria.isEmpty()) {
                builder.setRebuildState(Booklist.PREF_REBUILD_EXPANDED);
            }

            // Build the underlying data
            builder.build(context);

            // pre-count and cache (in the builder) these while we're in the background.
            // They are used for the header, and will not change even if the list cursor changes.
            if (style.isShowHeader(ListStyle.HEADER_SHOW_BOOK_COUNT)) {
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
     * Wrapper to {@link Booklist#getNewListCursor()}.
     * Get the list cursor.
     * Note this is a {@link BooklistCursor}
     *
     * @return cursor
     */
    @NonNull
    public BooklistCursor getNewListCursor() {
        Objects.requireNonNull(mBooklist, Booklist.TAG);
        return mBooklist.getNewListCursor();
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
            return Book.from(bookId, mDb);
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

    /**
     * Get the Bookshelf for the given row.
     *
     * @param rowData with book data
     *
     * @return Bookshelf, or {@code null} if the row contains no Bookshelf id.
     */
    @Nullable
    public Bookshelf getBookshelf(@NonNull final DataHolder rowData) {
        final long id = rowData.getLong(DBDefinitions.KEY_FK_BOOKSHELF);
        if (id > 0) {
            return mDb.getBookshelf(id);
        }
        return null;
    }

    @NonNull
    public List<Author> getAuthorsByBookId(@IntRange(from = 1) final long bookId) {
        return mDb.getAuthorsByBookId(bookId);
    }

    @NonNull
    public ArrayList<Long> getBookIdsByAuthor(@IntRange(from = 1) final long authorId,
                                              final boolean justThisShelf) {
        if (justThisShelf) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            return mDb.getBookIdsByAuthor(authorId, mBookshelf.getId());
        } else {
            return mDb.getBookIdsByAuthor(authorId);
        }
    }

    @NonNull
    public ArrayList<Long> getBookIdsBySeries(@IntRange(from = 1) final long seriesId,
                                              final boolean justThisShelf) {
        if (justThisShelf) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            return mDb.getBookIdsBySeries(seriesId, mBookshelf.getId());
        } else {
            return mDb.getBookIdsBySeries(seriesId);
        }
    }

    @NonNull
    public ArrayList<Long> getBookIdsByPublisher(@IntRange(from = 1) final long publisherId,
                                                 final boolean justThisShelf) {
        if (justThisShelf) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            return mDb.getBookIdsByPublisher(publisherId, mBookshelf.getId());
        } else {
            return mDb.getBookIdsByPublisher(publisherId);
        }
    }


    public boolean setAuthorComplete(@IntRange(from = 1) final long authorId,
                                     final boolean isComplete) {
        return mDb.setAuthorComplete(authorId, isComplete);
    }

    public boolean setSeriesComplete(@IntRange(from = 1) final long seriesId,
                                     final boolean isComplete) {
        return mDb.setSeriesComplete(seriesId, isComplete);
    }

    public boolean setBookRead(@IntRange(from = 1) final long bookId,
                               final boolean isRead) {
        return mDb.setBookRead(bookId, isRead);
    }

    /**
     * Delete the given Series.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} on a successful delete
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final Series series) {
        return mDb.delete(context, series);
    }

    /**
     * Delete the given Publisher.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} on a successful delete
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {
        return mDb.delete(context, publisher);
    }

    /**
     * Delete the given Bookshelf.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} on a successful delete
     */
    public boolean delete(@NonNull final Bookshelf bookshelf) {
        return mDb.delete(bookshelf);
    }

    /**
     * Delete the given Book.
     *
     * @param context Current context
     * @param bookId  to delete
     *
     * @return {@code true} on a successful delete
     */
    public boolean deleteBook(@NonNull final Context context,
                              @IntRange(from = 1) final long bookId) {
        return mDb.deleteBook(context, bookId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean lendBook(@IntRange(from = 1) final long bookId,
                            @Nullable final String loanee) {
        return mDb.setLoanee(bookId, loanee, true);
    }
}
