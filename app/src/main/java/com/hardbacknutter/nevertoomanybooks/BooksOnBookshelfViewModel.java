/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.MenuHandlerFactory;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

public class BooksOnBookshelfViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfViewModel";

    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";

    static final String BKEY_PROPOSE_BACKUP = TAG + ":pb";

    private static final String GROUP_NOT_DEFINED = "Group not defined: ";
    private static final String ERROR_NULL_BOOKLIST = "booklist";

    private static final Map<Integer, BLGRecord> BLG_RECORD = Map.ofEntries(
            Map.entry(BooklistGroup.AUTHOR,
                      new BLGRecord(DBKey.FK_AUTHOR,
                                    R.string.lbl_author,
                                    DBKey.AUTHOR_FORMATTED,
                                    R.string.bob_empty_author)),
            Map.entry(BooklistGroup.SERIES,
                      new BLGRecord(DBKey.FK_SERIES,
                                    R.string.lbl_series,
                                    DBKey.SERIES_TITLE,
                                    R.string.bob_empty_series)),
            Map.entry(BooklistGroup.PUBLISHER,
                      new BLGRecord(DBKey.FK_PUBLISHER,
                                    R.string.lbl_publisher,
                                    DBKey.PUBLISHER_NAME,
                                    R.string.bob_empty_publisher)),
            Map.entry(BooklistGroup.BOOKSHELF,
                      new BLGRecord(DBKey.FK_BOOKSHELF,
                                    R.string.lbl_bookshelf,
                                    DBKey.BOOKSHELF_NAME,
                                    // not used; Books are always on a shelf.
                                    R.string.lbl_bookshelf))
    );

    private static final Map<Integer, BLGDateRecord> BLG_DATE_RECORD = Map.ofEntries(
            Map.entry(BooklistGroup.DATE_ACQUIRED_YEAR,
                      new BLGDateRecord(R.string.lbl_date_acquired,
                                        BooklistGroup.BlgKey.ACQUIRED_YEAR)),
            Map.entry(BooklistGroup.DATE_ACQUIRED_MONTH,
                      new BLGDateRecord(R.string.lbl_date_acquired,
                                        BooklistGroup.BlgKey.ACQUIRED_YEAR,
                                        BooklistGroup.BlgKey.ACQUIRED_MONTH)),
            Map.entry(BooklistGroup.DATE_ACQUIRED_DAY,
                      new BLGDateRecord(R.string.lbl_date_acquired,
                                        BooklistGroup.BlgKey.ACQUIRED_YEAR,
                                        BooklistGroup.BlgKey.ACQUIRED_MONTH,
                                        BooklistGroup.BlgKey.ACQUIRED_DAY)),
            Map.entry(BooklistGroup.DATE_ADDED_YEAR,
                      new BLGDateRecord(R.string.lbl_date_added,
                                        BooklistGroup.BlgKey.ADDED_YEAR)),
            Map.entry(BooklistGroup.DATE_ADDED_MONTH,
                      new BLGDateRecord(R.string.lbl_date_added,
                                        BooklistGroup.BlgKey.ADDED_YEAR,
                                        BooklistGroup.BlgKey.ADDED_DAY)),
            Map.entry(BooklistGroup.DATE_ADDED_DAY,
                      new BLGDateRecord(R.string.lbl_date_added,
                                        BooklistGroup.BlgKey.ADDED_YEAR,
                                        BooklistGroup.BlgKey.ADDED_DAY,
                                        BooklistGroup.BlgKey.ADDED_MONTH)),
            Map.entry(BooklistGroup.DATE_PUBLISHED_YEAR,
                      new BLGDateRecord(R.string.lbl_date_published,
                                        BooklistGroup.BlgKey.PUB_YEAR)),
            Map.entry(BooklistGroup.DATE_PUBLISHED_MONTH,
                      new BLGDateRecord(R.string.lbl_date_published,
                                        BooklistGroup.BlgKey.PUB_YEAR,
                                        BooklistGroup.BlgKey.PUB_MONTH))
    );

    /** Cache for all bookshelves. */
    private final List<Bookshelf> bookshelfList = new ArrayList<>();

    private final BoBTask boBTask = new BoBTask();

    private final MutableLiveData<int[]> onPositionsUpdated = new MutableLiveData<>();

    private final MutableLiveData<Pair<Integer, Integer>> onSelectAdapterPosition =
            new MutableLiveData<>();

    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    @Nullable
    private SearchCriteria searchCriteria;
    /** Database Access. */
    private BookDao bookDao;

    /** Preferred booklist state in next rebuild. */
    private RebuildBooklist rebuildMode;

    /** Current displayed list. */
    @Nullable
    private Booklist booklist;

    /**
     * Flag (potentially) set when coming back from another Activity.
     * Indicates if list rebuild is needed in {@link BooksOnBookshelf}#onResume.
     */
    private boolean forceRebuildInOnResume;

    /** Flag to indicate that a list has been successfully loaded. */
    private boolean listLoaded;

    /** Flag to prompt the user to make a backup after startup. */
    private boolean proposeBackup;

    /** Currently selected bookshelf. */
    @Nullable
    private Bookshelf bookshelf;

    /**
     * The book id we want the new list to display more-or-less in the center.
     * Takes precedence above {@link #selectedAdapterPosition}
     */
    private long selectedBookId;

    /**
     * The selected (book) adapter position for the book (to be) displayed in the details frame.
     *
     * @see #selectedBookId
     */
    private int selectedAdapterPosition = RecyclerView.NO_POSITION;

    private List<MenuHandler> menuHandlers;

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return boBTask.onProgress();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<BoBTask.Outcome>>> onCancelled() {
        return boBTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception; {@link TaskResult#getResult()} will always
     *         return a valid {@link Throwable} and never {@code null}
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onFailure() {
        return boBTask.onFailure();
    }

    /**
     * Observable to receive success.
     *
     * @return the {@link BoBTask.Outcome} which can be considered to be complete and correct.
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<BoBTask.Outcome>>> onFinished() {
        return boBTask.onFinished();
    }

    /**
     * @return first: previous adapter position which should be un-selected
     *         second: current adapter position to select
     */
    @NonNull
    LiveData<Pair<Integer, Integer>> onSelectAdapterPosition() {
        return onSelectAdapterPosition;
    }

    @NonNull
    LiveData<int[]> getOnPositionsUpdated() {
        return onPositionsUpdated;
    }

    @Override
    protected void onCleared() {
        if (booklist != null) {
            booklist.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @Nullable final Bundle args) {

        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            // first start of the activity, read from user preference
            rebuildMode = RebuildBooklist.getPreferredMode(context);

            if (args != null) {
                proposeBackup = args.getBoolean(BKEY_PROPOSE_BACKUP, false);

                // extract search criteria if any are present
                searchCriteria = args.getParcelable(SearchCriteria.BKEY);
                if (searchCriteria == null) {
                    searchCriteria = new SearchCriteria();
                }

                final List<Long> bookIdlist = ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST);
                if (bookIdlist != null) {
                    searchCriteria.setBookIdList(bookIdlist);
                }

                // allow the caller to override the user preference
                if (args.containsKey(BKEY_LIST_STATE)) {
                    // If present, must not be null
                    rebuildMode = Objects.requireNonNull(args.getParcelable(BKEY_LIST_STATE),
                                                         BKEY_LIST_STATE);
                }

                // check for an explicit bookshelf set
                if (args.containsKey(DBKey.FK_BOOKSHELF)) {
                    // might be null, that's ok.
                    bookshelf = Bookshelf.getBookshelf(context, args.getInt(DBKey.FK_BOOKSHELF))
                                         .orElse(null);
                }
            }
        } else {
            // always preserve the state when the hosting fragment was revived
            rebuildMode = RebuildBooklist.FromSaved;
        }

        menuHandlers = MenuHandlerFactory.create();

        // create if not explicitly set above
        if (searchCriteria == null) {
            searchCriteria = new SearchCriteria();
        }

        // Set the last/preferred bookshelf if not explicitly set above
        // or use the default == first start of the app
        if (bookshelf == null) {
            bookshelf = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT)
                                 .orElseThrow();
        }
    }

    boolean isProposeBackup() {
        // We only offer ONCE
        final boolean tmp = proposeBackup;
        proposeBackup = false;
        return tmp;
    }

    void resetPreferredListRebuildMode(@NonNull final Context context) {
        rebuildMode = RebuildBooklist.getPreferredMode(context);
    }

    @NonNull
    List<MenuHandler> getMenuHandlers() {
        return menuHandlers;
    }

    /**
     * Get the Bookshelf list to show in the Spinner.
     * Will be empty until a call to {@link #reloadBookshelfList(Context)} is made.
     *
     * @return list
     */
    @NonNull
    List<Bookshelf> getBookshelfList() {
        return bookshelfList;
    }

    /**
     * Construct the Bookshelf list to show in the Spinner.
     *
     * @param context Current context.
     */
    void reloadBookshelfList(@NonNull final Context context) {
        bookshelfList.clear();
        bookshelfList.add(Bookshelf.getBookshelf(context, Bookshelf.ALL_BOOKS).orElseThrow());
        bookshelfList.addAll(ServiceLocator.getInstance().getBookshelfDao().getAll());
    }

    /**
     * Find the position of the currently set Bookshelf in the Spinner.
     * (with fallback to the default, or to 0 if needed)
     *
     * @param context Current context.
     *
     * @return the position that reflects the current bookshelf.
     */
    int getSelectedBookshelfSpinnerPosition(@NonNull final Context context) {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);

        // Not strictly needed, but guard against future changes
        if (bookshelfList.isEmpty()) {
            reloadBookshelfList(context);
        }

        // position we want to find
        Integer selectedPosition = null;
        // fallback if no selection found
        Integer defaultPosition = null;

        for (int i = 0; i < bookshelfList.size(); i++) {
            final long id = bookshelfList.get(i).getId();
            // find the position of the default shelf.
            if (id == Bookshelf.DEFAULT) {
                defaultPosition = i;
            }
            // find the position of the selected shelf
            if (id == bookshelf.getId()) {
                selectedPosition = i;
            }
        }

        if (selectedPosition != null) {
            return selectedPosition;

        } else {
            return Objects.requireNonNullElse(defaultPosition, 0);
        }
    }

    @NonNull
    Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf;
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context     Current context
     * @param bookshelfId of desired Bookshelf
     */
    void setCurrentBookshelf(@NonNull final Context context,
                             final long bookshelfId) {
        final long previousBookshelfId = bookshelf == null ? 0 : bookshelf.getId();

        bookshelf = ServiceLocator.getInstance().getBookshelfDao()
                                  .getById(bookshelfId).orElseGet(
                        () -> Bookshelf.getBookshelf(context, Bookshelf.PREFERRED,
                                                     Bookshelf.ALL_BOOKS)
                                       .orElseThrow());
        bookshelf.setAsPreferred(context);

        if (previousBookshelfId != bookshelf.getId()) {
            resetSelectedBook();
        }
    }

    private void resetSelectedBook() {
        selectedBookId = 0;
        selectedAdapterPosition = RecyclerView.NO_POSITION;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean reloadSelectedBookshelf(@NonNull final Context context) {

        final Bookshelf newBookshelf = Bookshelf
                .getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS)
                .orElseThrow();
        if (!newBookshelf.equals(bookshelf)) {
            // if it was.. switch to it.
            bookshelf = newBookshelf;
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
    Style getStyle(@NonNull final Context context) {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf.getStyle(context);
    }

    /**
     * Should be called after <strong>a style was changed/selected</strong>.
     * The style should exist (id != 0), or if it doesn't, the default style will be used instead.
     *
     * @param context   Current context
     * @param styleUuid the style to apply
     */
    void onStyleChanged(@NonNull final Context context,
                        @NonNull final String styleUuid) {
        // Always validate first
        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
        final Style style = stylesHelper.getStyleOrDefault(context, styleUuid);
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);

        // set as the global default.
        stylesHelper.setDefault(context, style.getUuid());
        // save the new bookshelf/style combination
        bookshelf.setAsPreferred(context);
        bookshelf.setStyle(context, style);
    }

    /**
     * Save the current booklist adapter position on the current bookshelf.
     *
     * @param context    Current context
     * @param position   The booklist <strong>adapter</strong> position of the first visible view.
     * @param viewOffset Value of {@link RecyclerView#getChildAt(int)} #getTop()
     */
    void saveListPosition(@NonNull final Context context,
                          final int position,
                          final int viewOffset) {
        if (listLoaded) {
            Objects.requireNonNull(bookshelf, Bookshelf.TAG);
            bookshelf.saveListPosition(context, position, viewOffset);
        }
    }

    Pair<Integer, Integer> getSavedListPosition() {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return new Pair<>(bookshelf.getBooklistAdapterPosition(),
                          bookshelf.getFirstVisibleItemViewOffset());
    }

    /**
     * Check if a rebuild is needed in {@code Activity#onResume()}.
     *
     * @return {@code true} if a rebuild is needed
     */
    boolean isForceRebuildInOnResume() {
        return forceRebuildInOnResume;
    }

    /**
     * Request a rebuild at the next {@code Activity#onResume()}.
     *
     * @param forceRebuild Flag
     */
    void setForceRebuildInOnResume(final boolean forceRebuild) {
        forceRebuildInOnResume = forceRebuild;
    }

    /**
     * Check if the list has (ever) loaded successfully.
     *
     * @return {@code true} if loaded at least once.
     */
    boolean isListLoaded() {
        return listLoaded;
    }

    @IntRange(from = 0)
    long getSelectedBookId() {
        return selectedBookId;
    }

    /**
     * Set the book id and its adapter position to which we like to return to after a rebuild.
     * <p>
     * Pass {@code 0} to disable.
     * <p>
     * Dev. note: reminder: book can appear on multiple positions.
     *
     * @param bookId          to store
     * @param adapterPosition to store
     */
    void setSelectedPosition(@IntRange(from = 0) final long bookId,
                             final int adapterPosition) {
        selectedBookId = bookId;

        onSelectAdapterPosition.setValue(new Pair<>(selectedAdapterPosition, adapterPosition));
        selectedAdapterPosition = adapterPosition;
    }

    /**
     * Check if this book is lend out, or not.
     *
     * @param rowData with data
     *
     * @return {@code true} if this book is available for lending.
     */
    boolean isAvailable(@NonNull final DataHolder rowData) {
        final String loanee;
        if (rowData.contains(DBKey.LOANEE_NAME)) {
            loanee = rowData.getString(DBKey.LOANEE_NAME);
        } else {
            loanee = ServiceLocator.getInstance().getLoaneeDao().getLoaneeByBookId(
                    rowData.getLong(DBKey.FK_BOOK));
        }
        return loanee == null || loanee.isEmpty();
    }

    @NonNull
    SearchCriteria getSearchCriteria() {
        return Objects.requireNonNull(searchCriteria);
    }

    /**
     * This is used to re-display the list in onResume.
     * i.e. {@link #selectedBookId} was set, but a rebuild was not needed.
     *
     * @return the node(s), can be empty, but never {@code null}
     */
    @NonNull
    List<BooklistNode> getTargetNodes() {
        if (selectedBookId != 0) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            return booklist.getVisibleBookNodes(selectedBookId);
        }

        return new ArrayList<>();
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
    BooklistNode setNode(final long nodeRowId,
                         @NonNull final BooklistNode.NextState nextState,
                         final int relativeChildLevel) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.setNode(nodeRowId, nextState, relativeChildLevel);
    }

    void expandAllNodes(@IntRange(from = 1) final int topLevel,
                        final boolean expand) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        booklist.setAllNodes(topLevel, expand);
    }

    @NonNull
    Optional<BooklistNode> getNextBookWithoutCover(final long rowId) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getNextBookWithoutCover(rowId);
    }

    @NonNull
    BooklistHeader getHeaderContent(@NonNull final Context context) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        Objects.requireNonNull(bookshelf, "bookshelf");
        return new BooklistHeader(context, getStyle(context),
                                  booklist.countBooks(), booklist.countDistinctBooks(),
                                  bookshelf.getFilters(),
                                  searchCriteria);
    }

    @NonNull
    public Booklist getBooklist() {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist;
    }

    @NonNull
    String getBookNavigationTableName() {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getNavigationTableName();
    }

    @NonNull
    List<Author> getAuthorsByBookId(@IntRange(from = 1) final long bookId) {
        return ServiceLocator.getInstance().getAuthorDao().getByBookId(bookId);
    }

    /**
     * Create the contract input object for the currently displayed book list.
     *
     * @param context Current context
     *
     * @return a fully initialized input object
     */
    @NonNull
    UpdateBooklistContract.Input createUpdateBooklistContractInput(
            @NonNull final Context context) {
        final ArrayList<Long> books = getBooklist().getCurrentBookIdList();

        final String title = context.getString(R.string.name_colon_value,
                                               context.getString(R.string.lbl_bookshelf),
                                               getCurrentBookshelf().getName());
        return new UpdateBooklistContract.Input(books, title, null);
    }

    /**
     * Create the contract input object for one the groups in {@link #BLG_RECORD}.
     *
     * @param context       Current context
     * @param rowData       the data at the selected row
     * @param onlyThisShelf flag
     *
     * @return a fully initialized input object
     */
    @NonNull
    UpdateBooklistContract.Input createUpdateBooklistContractInput(
            @NonNull final Context context,
            @NonNull final DataHolder rowData,
            final boolean onlyThisShelf) {

        final int groupId = rowData.getInt(DBKey.BL_NODE_GROUP);
        final BLGRecord blgRecord = Objects.requireNonNull(BLG_RECORD.get(groupId),
                                                           () -> GROUP_NOT_DEFINED + groupId);

        final ArrayList<Long> books;

        final long id = rowData.getLong(blgRecord.dbKey);
        // the id should never be 0. But paranoia...
        if (onlyThisShelf || id == 0) {
            // We're going to update all book under THIS node only (regardless of node type).
            final String nodeKey = rowData.getString(DBKey.BL_NODE_KEY);
            final int level = rowData.getInt(DBKey.BL_NODE_LEVEL);

            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            books = booklist.getBookIdsForNodeKey(nodeKey, level);
        } else {
            // The 'id' represents a specific author, series, ...
            // We're going to update ALL books referenced by that id, for ALL bookshelves.
            switch (groupId) {
                case BooklistGroup.AUTHOR:
                    books = ServiceLocator.getInstance().getAuthorDao().getBookIds(id);
                    break;
                case BooklistGroup.SERIES:
                    books = ServiceLocator.getInstance().getSeriesDao().getBookIds(id);
                    break;
                case BooklistGroup.PUBLISHER:
                    books = ServiceLocator.getInstance().getPublisherDao().getBookIds(id);
                    break;
                case BooklistGroup.BOOKSHELF:
                    books = ServiceLocator.getInstance().getBookshelfDao().getBookIds(id);
                    break;
                default:
                    throw new IllegalArgumentException(String.valueOf(groupId));
            }
        }

        final String title;
        if (id != 0) {
            title = context.getString(R.string.name_colon_value,
                                      context.getString(blgRecord.labelResId),
                                      rowData.getString(blgRecord.labelKey));
        } else {
            title = context.getString(R.string.name_colon_value,
                                      context.getString(blgRecord.labelResId),
                                      context.getString(blgRecord.emptyItemTextResId));
        }

        return new UpdateBooklistContract.Input(books, title, null);
    }

    /**
     * Create the contract input object for one the groups in {@link #BLG_DATE_RECORD}.
     *
     * @param context Current context
     * @param rowData the data at the selected row
     *
     * @return a fully initialized input object
     */
    @NonNull
    UpdateBooklistContract.Input createDateRowUpdateBooklistContractInput(
            @NonNull final Context context,
            @NonNull final DataHolder rowData) {

        final String nodeKey = rowData.getString(DBKey.BL_NODE_KEY);
        final int level = rowData.getInt(DBKey.BL_NODE_LEVEL);

        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        final ArrayList<Long> books = booklist.getBookIdsForNodeKey(nodeKey, level);

        final int groupId = rowData.getInt(DBKey.BL_NODE_GROUP);

        final BLGDateRecord blgRecord = Objects.requireNonNull(BLG_DATE_RECORD.get(groupId),
                                                               () -> GROUP_NOT_DEFINED + groupId);

        final StringJoiner sj = new StringJoiner("-");
        for (int g = 0; g < blgRecord.dbKeys.length; g++) {
            final String text = rowData.getString(blgRecord.dbKeys[g]);
            if (text.isBlank()) {
                break;
            }
            sj.add(text);
        }

        final String title = context.getString(R.string.name_colon_value,
                                               context.getString(blgRecord.labelResId),
                                               sj.toString());
        return new UpdateBooklistContract.Input(books, title, null);
    }

    /**
     * Update the 'complete' status of the given Author.
     *
     * @param author   Author to update
     * @param complete new status
     */
    void setAuthorComplete(@NonNull final Author author,
                           final boolean complete) {
        if (ServiceLocator.getInstance().getAuthorDao().setComplete(author, complete)) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            final int[] positions =
                    booklist.updateAuthorComplete(author.getId(), author.isComplete())
                            .stream()
                            .mapToInt(BooklistNode::getAdapterPosition)
                            .toArray();
            onPositionsUpdated.setValue(positions);
        }
    }

    /**
     * Update the 'complete' status of the given Series.
     *
     * @param series   Series to update
     * @param complete new status
     */
    void setSeriesComplete(@NonNull final Series series,
                           final boolean complete) {
        if (ServiceLocator.getInstance().getSeriesDao().setComplete(series, complete)) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            final int[] positions =
                    booklist.updateSeriesComplete(series.getId(), series.isComplete())
                            .stream()
                            .mapToInt(BooklistNode::getAdapterPosition)
                            .toArray();
            onPositionsUpdated.setValue(positions);
        }
    }

    /**
     * Update the 'read' status of the given book.
     *
     * @param id   Book to update
     * @param read new status
     */
    void setBookRead(@IntRange(from = 1) final long id,
                     final boolean read) {
        final Book book = Book.from(id);
        if (bookDao.setRead(book.getId(), read)) {
            updateBooklistOnBookRead(book.getId(), book.getBoolean(DBKey.READ__BOOL));
        }
    }

    /**
     * Update the <strong>book-list</strong> 'read' status of the given book.
     *
     * @param id   Book to update
     * @param read new status
     */
    void updateBooklistOnBookRead(@IntRange(from = 1) final long id,
                                  final boolean read) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        final int[] positions =
                booklist.updateBookRead(id, read)
                        .stream()
                        .mapToInt(BooklistNode::getAdapterPosition)
                        .toArray();
        onPositionsUpdated.setValue(positions);
    }

    /**
     * Update the 'loanee' for the given book.
     *
     * @param id     Book to update
     * @param loanee new loanee or {@code null} for a returned book
     */
    void lendBook(@IntRange(from = 1) final long id,
                  @SuppressWarnings("SameParameterValue") @Nullable final String loanee) {
        if (ServiceLocator.getInstance().getLoaneeDao().setLoanee(id, loanee)) {
            updateBooklistOnBookLend(id, loanee);
        }
    }

    /**
     * Update the <strong>book-list</strong> 'loanee' status of the given book.
     *
     * @param bookId to update
     * @param loanee new loanee or {@code null} for a returned book
     */
    void updateBooklistOnBookLend(@IntRange(from = 1) final long bookId,
                                  @Nullable final String loanee) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        final int[] positions = booklist.updateBookLoanee(bookId, loanee)
                                        .stream()
                                        .mapToInt(BooklistNode::getAdapterPosition)
                                        .toArray();
        onPositionsUpdated.setValue(positions);
    }

    /**
     * Update the <strong>book-list</strong> 'cover' of the given book.
     *
     * @param bookId to update
     */
    void updateBooklistOnBookCover(@IntRange(from = 1) final long bookId) {
        final int[] positions = getVisibleBookNodes(bookId)
                .stream()
                .mapToInt(BooklistNode::getAdapterPosition)
                .toArray();
        onPositionsUpdated.setValue(positions);
    }

    /**
     * Delete the given Series.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} on a successful delete
     */
    boolean delete(@NonNull final Context context,
                   @NonNull final Series series) {
        return ServiceLocator.getInstance().getSeriesDao().delete(context, series);
    }

    /**
     * Delete the given Publisher.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} on a successful delete
     */
    boolean delete(@NonNull final Context context,
                   @NonNull final Publisher publisher) {
        return ServiceLocator.getInstance().getPublisherDao().delete(context, publisher);
    }

    /**
     * Delete the given Bookshelf.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} on a successful delete
     */
    boolean delete(@NonNull final Bookshelf bookshelf) {
        return ServiceLocator.getInstance().getBookshelfDao().delete(bookshelf);
    }

    /**
     * Delete the given Book.
     *
     * @param bookId to delete
     *
     * @return {@code true} on a successful delete
     */
    boolean deleteBook(@IntRange(from = 1) final long bookId) {
        final boolean success = bookDao.delete(bookId);
        if (success) {
            onBookDeleted(bookId);
        }
        return success;
    }

    /**
     * Entry point after a Book (or list of) was edited; either manually by the user,
     * or automatically with an internet update action.
     *
     * @param data as returned from the contract
     */
    void onBookEditFinished(@NonNull final EditBookOutput data) {
        if (data.isModified()) {
            //URGENT: if we processed a single book, we should NOT do a full rebuild
            forceRebuildInOnResume = true;
            // to find out if we updated a list, use data.getLastBookIdProcessed()
        }

        // If we got an reposition id back, make any potential rebuild re-position to it.
        if (data.getRepositionToBookId() > 0) {
            selectedBookId = data.getRepositionToBookId();
        }
    }

    /**
     * This method is called from an ActivityResultContract after the result intent is parsed.
     *
     * @param context Current context
     * @param data    returned from the view/edit contract
     */
    void onEditStylesFinished(@NonNull final Context context,
                              @NonNull final PreferredStylesContract.Output data) {
        // we get the UUID for the selected style back.
        data.getUuid().ifPresent(uuid -> onStyleChanged(context, uuid));

        // This is independent from the above style having been modified ot not.
        if (data.isModified()) {
            forceRebuildInOnResume = true;
        }
    }

    /**
     * This method is called from an ActivityResultContract after the result intent is parsed.
     *
     * @param context Current context
     * @param data    returned from the view/edit contract
     */
    void onEditStyleFinished(@NonNull final Context context,
                             @NonNull final EditStyleContract.Output data) {
        // We get here from the StylePickerDialogFragment (i.e. the style menu)
        // when the user choose to EDIT a style.
        if (data.getUuid().isPresent()) {
            onStyleChanged(context, data.getUuid().get());

            // ALWAYS rebuild here, even when the style was not modified
            // as we're handling this as a style-change
            // (we could do checks... but it's not worth the effort.)
            // i.e. same as in mOnStylePickerListener
            forceRebuildInOnResume = true;
        }
    }

    void onManageBookshelvesFinished(@NonNull final Context context,
                                     final long bookshelfId) {
        if (bookshelfId != 0 && bookshelfId != getCurrentBookshelf().getId()) {
            setCurrentBookshelf(context, bookshelfId);
            forceRebuildInOnResume = true;
        }
    }

    void onFtsSearchFinished(@NonNull final SearchCriteria criteria) {
        searchCriteria = criteria;
        forceRebuildInOnResume = true;
    }

    /**
     * Called when the user has finished an Import.
     * <p>
     * This method is called from a ActivityResultContract after the result intent is parsed.
     *
     * @param context       Current context
     * @param importResults returned from the import
     */
    void onImportFinished(@NonNull final Context context,
                          @NonNull final ImportResults importResults) {
        if (importResults.styles > 0) {
            // Force a refresh of the cached styles
            ServiceLocator.getInstance().getStyles().clearCache();
        }
        if (importResults.preferences > 0) {
            // Refresh the preferred bookshelf. This also refreshes its style.
            reloadSelectedBookshelf(context);
        }

        // styles, prefs, books, covers,... it all requires a rebuild.
        forceRebuildInOnResume = true;
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     */
    void buildBookList() {
        Objects.requireNonNull(bookshelf, ERROR_NULL_BOOKLIST);

        //noinspection ConstantConditions
        boBTask.build(bookshelf, rebuildMode, searchCriteria, selectedBookId);
    }

    boolean isBuilding() {
        return boBTask.isActive();
    }

    void onBuildFinished(@NonNull final BoBTask.Outcome outcome) {
        // the new build is completely done. We can safely discard the previous one.
        if (booklist != null) {
            booklist.close();
        }

        booklist = outcome.getList();

        // Save a flag to say list was loaded at least once successfully
        listLoaded = true;

        // preserve the new state by default
        rebuildMode = RebuildBooklist.FromSaved;
    }

    void onBuildCancelled() {
        resetSelectedBook();
    }

    void onBuildFailed() {
        resetSelectedBook();
    }

    void onBookDeleted(final long bookId) {
        if (bookId == 0 || bookId == selectedBookId) {
            resetSelectedBook();
        }
    }

    @NonNull
    List<BooklistNode> getVisibleBookNodes(final long bookId) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getVisibleBookNodes(bookId);
    }

    private static class BLGDateRecord {
        @NonNull
        final String[] dbKeys;
        @StringRes
        final int labelResId;

        BLGDateRecord(@StringRes final int labelResId,
                      @NonNull final String... dbKeys) {
            this.dbKeys = dbKeys;
            this.labelResId = labelResId;
        }
    }

    private static class BLGRecord {
        @NonNull
        final String dbKey;
        @StringRes
        final int labelResId;
        @NonNull
        final String labelKey;
        @StringRes
        final int emptyItemTextResId;

        BLGRecord(@NonNull final String dbKey,
                  @StringRes final int labelResId,
                  @NonNull final String labelKey,
                  @StringRes final int emptyItemTextResId) {
            this.dbKey = dbKey;
            this.labelResId = labelResId;
            this.labelKey = labelKey;
            this.emptyItemTextResId = emptyItemTextResId;
        }
    }

}
