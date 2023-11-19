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
import android.os.Debug;

import androidx.annotation.Dimension;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.TopRowListPosition;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
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

    @SuppressWarnings("NonFinalStaticVariableUsedInClassInitialization")
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

    @SuppressWarnings("NonFinalStaticVariableUsedInClassInitialization")
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
                                        BooklistGroup.BlgKey.PUB_MONTH)),

            Map.entry(BooklistGroup.DATE_FIRST_PUBLICATION_YEAR,
                      new BLGDateRecord(R.string.lbl_date_first_publication,
                                        BooklistGroup.BlgKey.FIRST_PUB_YEAR)),
            Map.entry(BooklistGroup.DATE_FIRST_PUBLICATION_MONTH,
                      new BLGDateRecord(R.string.lbl_date_first_publication,
                                        BooklistGroup.BlgKey.FIRST_PUB_YEAR,
                                        BooklistGroup.BlgKey.FIRST_PUB_MONTH))
    );

    /** Cache for all bookshelves. */
    private final List<Bookshelf> bookshelfList = new ArrayList<>();

    private final BoBTask boBTask = new BoBTask();

    private final MutableLiveData<int[]> positionsUpdated = new MutableLiveData<>();

    private final MutableLiveData<Pair<Integer, Integer>> highlightSelection =
            new MutableLiveData<>();

    private final MutableLiveData<Boolean> triggerRebuildList = new MutableLiveData<>();

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

    /** Currently selected {@link Bookshelf}. */
    @Nullable
    private Bookshelf bookshelf;

    /**
     * Set when the LayoutManager is created.
     * <p>
     * When either Bookshelf or Style is changed, we'll reset to this to {@code null}.
     * This will FORCE a recreation of the LayoutManager during BooksOnBookshelf#onResume
     * even when the type itself happens to be the same between 2 bookshelves or styles.
     */
    @Nullable
    private Style.Layout currentLayout;


    /**
     * The book id we want the new list to display more-or-less in the center.
     */
    private long selectedBookId;

    /**
     * The currently selected (highlighted) adapter position.
     */
    private int selectedAdapterPosition = RecyclerView.NO_POSITION;

    private List<MenuHandler> menuHandlers;

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return boBTask.onProgress();
    }

    @NonNull
    LiveData<LiveDataEvent<BoBTask.Outcome>> onCancelled() {
        return boBTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    LiveData<LiveDataEvent<Throwable>> onFailure() {
        return boBTask.onFailure();
    }

    /**
     * Observable to receive success.
     *
     * @return the {@link BoBTask.Outcome} which can be considered to be complete and correct.
     */
    @NonNull
    LiveData<LiveDataEvent<BoBTask.Outcome>> onFinished() {
        return boBTask.onFinished();
    }

    /**
     * Trigger a rebuild of the book list.
     *
     * @return flag, whether the LayoutManager needs to be recreated or not.
     */
    @NonNull
    LiveData<Boolean> onTriggerRebuildList() {
        return triggerRebuildList;
    }

    /**
     * Observable: select (highlight) the current row.
     *
     * @return first: previous adapter position which should be un-selected
     *         second: current adapter position to select; can be {@code RecyclerView.NO_POSITION}
     */
    @NonNull
    LiveData<Pair<Integer, Integer>> onHighlightSelection() {
        return highlightSelection;
    }

    @NonNull
    LiveData<int[]> onPositionsUpdated() {
        return positionsUpdated;
    }

    @Override
    protected void onCleared() {
        if (booklist != null) {
            booklist.close();
        }
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

        // preserve the current Layout so we can react to style/layout changes
//        currentLayout = bookshelf.getStyle().getLayout();
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
     * Get the {@link Bookshelf} list to show in the Spinner.
     * Will be empty until a call to {@link #reloadBookshelfList(Context)} is made.
     *
     * @return list
     */
    @NonNull
    List<Bookshelf> getBookshelfList() {
        return bookshelfList;
    }

    /**
     * Construct the {@link Bookshelf} list to show in the Spinner.
     *
     * @param context Current context.
     */
    void reloadBookshelfList(@NonNull final Context context) {
        bookshelfList.clear();
        bookshelfList.add(Bookshelf.getBookshelf(context, Bookshelf.ALL_BOOKS).orElseThrow());
        bookshelfList.addAll(ServiceLocator.getInstance().getBookshelfDao().getAll());
    }

    /**
     * Find the position of the currently set {@link Bookshelf} in the Spinner.
     * (with fallback to the default, or to 0 if needed)
     *
     * @param context Current context.
     *
     * @return the position that reflects the current {@link Bookshelf}.
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
    Bookshelf getBookshelf() {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf;
    }

    /**
     * Load and set the desired {@link Bookshelf}.
     *
     * @param context     Current context
     * @param bookshelfId of desired {@link Bookshelf}
     */
    void selectBookshelf(@NonNull final Context context,
                         final long bookshelfId) {
        final long previousBookshelfId = bookshelf == null ? 0 : bookshelf.getId();

        bookshelf = ServiceLocator.getInstance().getBookshelfDao()
                                  .getById(bookshelfId).orElseGet(
                        () -> Bookshelf.getBookshelf(context, Bookshelf.PREFERRED,
                                                     Bookshelf.ALL_BOOKS)
                                       .orElseThrow());
        bookshelf.setAsPreferred(context);

        if (previousBookshelfId != bookshelf.getId()) {
            currentLayout = null;
            // just set, don't propagate
            selectedBookId = 0;
            selectedAdapterPosition = RecyclerView.NO_POSITION;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean reloadSelectedBookshelf(@NonNull final Context context) {

        final Bookshelf newBookshelf = Bookshelf
                .getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS)
                .orElseThrow();
        if (newBookshelf.equals(bookshelf)) {
            return false;
        }
        bookshelf = newBookshelf;
        return true;
    }

    /**
     * Get the style of the current {@link Bookshelf}.
     *
     * @return style
     */
    @NonNull
    Style getStyle() {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf.getStyle();
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
        // Validate and use the default if needed.
        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
        final Style style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);

        Objects.requireNonNull(bookshelf, Bookshelf.TAG);

        // set as the global default.
        stylesHelper.setDefault(style.getUuid());
        // save the new bookshelf/style combination
        bookshelf.setAsPreferred(context);
        bookshelf.setStyle(context, style);
        currentLayout = null;
    }

    @Nullable
    Style.Layout getCurrentLayout() {
        return currentLayout;
    }

    /**
     * Set/remember the layout we're using.
     *
     * @param currentLayout to set
     */
    void setCurrentLayout(@NonNull final Style.Layout currentLayout) {
        this.currentLayout = currentLayout;
    }

    /**
     * Create the adapter, and set the Booklist on it.
     *
     * @param context                 Current context
     * @param hasEmbeddedDetailsFrame whether the display Activity is showing
     *                                the embedded details-frame.
     *
     * @return the adapter
     */
    @NonNull
    BooklistAdapter createBooklistAdapter(@NonNull final Context context,
                                          final boolean hasEmbeddedDetailsFrame) {
        final Style style = getStyle();
        final BooklistAdapter adapter = new BooklistAdapter(
                context, style,
                style.getLayout(hasEmbeddedDetailsFrame),
                getCoverLongestSide(context, style, hasEmbeddedDetailsFrame));
        adapter.setBooklist(booklist);
        return adapter;
    }

    @Dimension
    private int getCoverLongestSide(@NonNull final Context context,
                                    @NonNull final Style style,
                                    final boolean hasEmbeddedDetailsFrame) {
        if (style.isShowField(FieldVisibility.Screen.List, DBKey.COVER[0])) {
            if (hasEmbeddedDetailsFrame) {
                return style.getCoverMaxSizeInPixels(context, Style.Layout.List);
            } else {
                return style.getCoverMaxSizeInPixels(context);
            }
        } else {
            return 0;
        }
    }

    /**
     * Get the mode to use for the context-menu's.
     *
     * @param context                 Current context
     * @param hasEmbeddedDetailsFrame whether the display Activity is showing
     *                                the embedded details-frame.
     *
     * @return the mode to use
     */
    @NonNull
    ShowContextMenu getShowContextMenuMode(@NonNull final Context context,
                                           final boolean hasEmbeddedDetailsFrame) {
        final ShowContextMenu preferredMode = ShowContextMenu.getPreferredMode(context);
        if (preferredMode == ShowContextMenu.ButtonIfSpace && hasEmbeddedDetailsFrame) {
            return ShowContextMenu.NoButton;
        } else {
            return preferredMode;
        }
    }

    @NonNull
    TopRowListPosition getBookshelfTopRowPosition() {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf.getTopRowPosition();
    }

    /**
     * Save the current booklist adapter position on the current {@link Bookshelf}.
     *
     * @param context        Current context
     * @param topRowPosition The booklist position of the first visible view.
     */
    void saveBookshelfTopRowPosition(@NonNull final Context context,
                                     @NonNull final TopRowListPosition topRowPosition) {
        if (listLoaded) {
            Objects.requireNonNull(bookshelf, Bookshelf.TAG);
            bookshelf.saveTopRowPosition(context, topRowPosition);
        }
    }

    /**
     * Check if a rebuild is needed in {@code Activity#onResume()}.
     * <p>
     * <strong>Important</strong>: this method will reset the flag to {@code false}
     *
     * @return {@code true} if a rebuild is needed
     */
    boolean isForceRebuildInOnResume() {
        final boolean force = forceRebuildInOnResume;
        // always reset for next iteration.
        forceRebuildInOnResume = false;
        return force;
    }

    /**
     * Request a rebuild at the next {@code Activity#onResume()}.
     */
    void setForceRebuildInOnResume() {
        forceRebuildInOnResume = true;
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
     * Set the book id around which we want to center the list after a rebuild.
     * Set and propagate the new adapter position to highlight the current row.
     * <p>
     * Dev. note: reminder: book can appear on multiple positions hence the need for both params.
     *
     * @param bookId          to store
     *                        use {@code 0} to disable centering
     * @param adapterPosition to highlight;
     *                        use {@code RecyclerView.NO_POSITION} to remove all highlighting
     */
    void setSelectedBook(@IntRange(from = 0) final long bookId,
                         @IntRange(from = RecyclerView.NO_POSITION) final int adapterPosition) {
        this.selectedBookId = bookId;

        // Call with previous selectedAdapterPosition and new position
        highlightSelection.setValue(new Pair<>(selectedAdapterPosition, adapterPosition));
        // and replace previous with new.
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
        return new BooklistHeader(context, getStyle(),
                                  booklist.countBooks(), booklist.countDistinctBooks(),
                                  bookshelf.getFilters(),
                                  searchCriteria);
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
        //noinspection DataFlowIssue
        final List<Long> books = booklist.getCurrentBookIdList();

        final String title = context.getString(R.string.name_colon_value,
                                               context.getString(R.string.lbl_bookshelf),
                                               getBookshelf().getName());
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

        @BooklistGroup.Id
        final int groupId = rowData.getInt(DBKey.BL_NODE_GROUP);
        final BLGRecord blgRecord = Objects.requireNonNull(BLG_RECORD.get(groupId),
                                                           () -> GROUP_NOT_DEFINED + groupId);

        final List<Long> books;

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
        final List<Long> books = booklist.getBookIdsForNodeKey(nodeKey, level);

        @BooklistGroup.Id
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
     * <p>
     * Triggers a {@link BooklistAdapter#requery(int[])} for the changed positions.
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
            positionsUpdated.setValue(positions);
        }
    }

    /**
     * Update the 'complete' status of the given Series.
     * <p>
     * Triggers a {@link BooklistAdapter#requery(int[])} for the changed positions.
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
            positionsUpdated.setValue(positions);
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
        if (bookDao.setRead(book, read)) {
            onBookReadStatusChanged(book);
        }
    }

    /**
     * Should be called when the read-status of a book was changed.
     *
     * @param book which was changed
     *
     * @return {@code true} if a full rebuild of the list was triggered
     */
    private boolean onBookReadStatusChanged(@NonNull final Book book) {
        if (getStyle().hasGroup(BooklistGroup.READ_STATUS)) {
            // The book might move to another group - no choice, we must rebuild
            triggerRebuildList.setValue(false);
            return true;

        } else {
            // The change will not affect the group the book is in,
            // update the <strong>book-list</strong> 'read' status of the given book.
            final long id = book.getId();
            final boolean read = book.getBoolean(DBKey.READ__BOOL);
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            final int[] positions = booklist.updateBookRead(id, read)
                                            .stream()
                                            .mapToInt(BooklistNode::getAdapterPosition)
                                            .toArray();
            positionsUpdated.setValue(positions);
            return false;
        }
    }

    /**
     * Update the 'loanee' for the given book.
     *
     * @param id     Book to update
     * @param loanee new loanee or {@code null} for a returned book
     */
    void setBookLoanee(@IntRange(from = 1) final long id,
                       @SuppressWarnings("SameParameterValue") @Nullable final String loanee) {
        if (ServiceLocator.getInstance().getLoaneeDao().setLoanee(id, loanee)) {
            onBookLoaneeChanged(id, loanee);
        }
    }

    /**
     * Should be called when a loanee of a book was changed.
     *
     * @param id     Book to update
     * @param loanee new loanee or {@code null} for a returned book
     *
     * @return {@code true} if a full rebuild of the list was triggered
     */
    boolean onBookLoaneeChanged(@IntRange(from = 1) final long id,
                                @SuppressWarnings("SameParameterValue") @Nullable final String loanee) {
        if (getStyle().hasGroup(BooklistGroup.LENDING)) {
            // The book might move to another group - no choice, we must rebuild
            triggerRebuildList.setValue(false);
            return true;
        } else {
            // The change will not affect the group the book is in,
            // update the <strong>book-list</strong> 'loanee' of the given book.
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            final int[] positions = booklist.updateBookLoanee(id, loanee)
                                            .stream()
                                            .mapToInt(BooklistNode::getAdapterPosition)
                                            .toArray();
            positionsUpdated.setValue(positions);
            return false;
        }
    }

    /**
     * Should be called when a cover of a book was changed.
     *
     * @param bookId which was changed
     *
     * @return {@code true} if a full rebuild of the list was triggered
     */
    private boolean onBookCoverChanged(@IntRange(from = 1) final long bookId) {
        // The change will not affect the group the book is in.
        final int[] positions = getVisibleBookNodes(bookId)
                .stream()
                .mapToInt(BooklistNode::getAdapterPosition)
                .toArray();
        positionsUpdated.setValue(positions);
        return false;
    }

    /**
     * Receives notifications that an inline-string column was updated.
     *
     * @param dbKey    the request-key, a {@link DBKey}, from the update event
     * @param original the original string
     * @param modified the updated string
     */
    void onInlineStringUpdate(@NonNull final String dbKey,
                              @NonNull final String original,
                              @NonNull final String modified) {
        if (getStyle().isShowField(FieldVisibility.Screen.List, dbKey)) {
            // The entity is shown on the book level, do a full rebuild
            triggerRebuildList.setValue(false);
        } else {
            // Update only the levels, and trigger an adapter update
            // ENHANCE: update the modified row without a rebuild.
            triggerRebuildList.setValue(false);
        }
    }

    /**
     * Receives notifications that an {@link Entity} (but NOT a Book) potentially was updated.
     *
     * @param dbKey  the request-key, a {@link DBKey}, from the update event
     * @param entity the entity that potentially was updated
     */
    void onEntityUpdate(@NonNull final String dbKey,
                        @NonNull final Entity entity) {
        if (getStyle().isShowField(FieldVisibility.Screen.List, dbKey)) {
            // The entity is shown on the book level, do a full rebuild
            triggerRebuildList.setValue(false);
        } else {
            // Update only the levels, and trigger an adapter update
            // ENHANCE: update the modified row without a rebuild.
            triggerRebuildList.setValue(false);
        }
    }

    /**
     * Receives notifications that a {@link Book} potentially was updated.
     * <p>
     * For a limited set of keys, we directly update the list table which is very fast.
     * <p>
     * Other keys, or full books, will always trigger a list rebuild.
     *
     * @param book the book
     * @param keys the item(s) that potentially were changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    void onBookUpdated(@Nullable final Book book,
                       @Nullable final String... keys) {
        // Reminder: the actual Book table and/or relations are ALREADY UPDATED.
        // The only thing we are updating here is the temporary BookList table
        // as itself and/or the displayed data

        if (keys != null && Arrays.asList(keys).contains(DBKey.READ__BOOL)) {
            Objects.requireNonNull(book);
            if (onBookReadStatusChanged(book)) {
                // Full rebuild was triggered
                return;
            }
        }

        if (keys != null && Arrays.asList(keys).contains(DBKey.LOANEE_NAME)) {
            Objects.requireNonNull(book);
            if (onBookLoaneeChanged(book.getId(), book.getLoanee().orElse(null))) {
                // Full rebuild was triggered
                return;
            }
        }

        if (keys != null && Arrays.asList(keys).contains(DBKey.COVER[0])) {
            Objects.requireNonNull(book);
            if (onBookCoverChanged(book.getId())) {
                // Full rebuild was triggered
                return;
            }
        }

        // ENHANCE: update the modified row without a rebuild for more keys
        triggerRebuildList.setValue(false);
    }

    /**
     * Delete the given {@link Series}.
     *
     * @param context Current context
     * @param series  to delete
     */
    void delete(@NonNull final Context context,
                @NonNull final Series series) {
        if (ServiceLocator.getInstance().getSeriesDao().delete(context, series)) {
            triggerRebuildList.setValue(false);
        }
    }

    /**
     * Delete the given {@link Publisher}.
     *
     * @param context   Current context
     * @param publisher to delete
     */
    void delete(@NonNull final Context context,
                @NonNull final Publisher publisher) {
        if (ServiceLocator.getInstance().getPublisherDao().delete(context, publisher)) {
            triggerRebuildList.setValue(false);
        }
    }

    /**
     * Delete the given {@link Bookshelf}.
     *
     * @param bookshelf to delete
     */
    void delete(@NonNull final Bookshelf bookshelf) {
        if (ServiceLocator.getInstance().getBookshelfDao().delete(bookshelf)) {
            triggerRebuildList.setValue(false);
        }
    }

    /**
     * Delete the given Book.
     *
     * @param bookId to delete
     */
    void deleteBook(@IntRange(from = 1) final long bookId) {
        if (bookDao.delete(bookId)) {
            onBookDeleted(bookId);
        }
    }

    /**
     * Should be called when a a book was deleted.
     *
     * @param bookId which was deleted
     */
    void onBookDeleted(final long bookId) {
        if (bookId == 0 || bookId == selectedBookId) {
            setSelectedBook(0, RecyclerView.NO_POSITION);
        }
        // We don't try to remove the row without a rebuild as this could quickly become complex...
        // e.g. if there is(was) only a single book on the level, we'd have to recursively
        // cleanup each level above the book
        triggerRebuildList.setValue(false);
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
        if (bookshelfId != 0 && bookshelfId != getBookshelf().getId()) {
            selectBookshelf(context, bookshelfId);
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
            Debug.startMethodTracing("trace-" + LocalDateTime
                    .now().withNano(0)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        Objects.requireNonNull(bookshelf, ERROR_NULL_BOOKLIST);
        Objects.requireNonNull(searchCriteria, "searchCriteria");

        boBTask.build(bookshelf, rebuildMode, searchCriteria, selectedBookId);
    }

    boolean isBuilding() {
        return boBTask.isActive();
    }

    /**
     * Called when the list build succeeded.
     *
     * @param outcome from the task; contains the (optional) target rows.
     */
    void onBuildFinished(@NonNull final BoBTask.Outcome outcome) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
            Debug.stopMethodTracing();
        }

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

    void recoverAfterFailedBuild(@NonNull final Context context) {
        // Something is REALLY BAD
        // This is usually (BUT NOT ALWAYS) due to the developer making an oopsie
        // with the Styles. i.e. the style used to build is very likely corrupt.
        // Another reason can be during development when the database structure
        // was changed...
        final Style style = getStyle();
        // so we reset the style to recover.. and restarting the app will work.
        onStyleChanged(context, BuiltinStyle.DEFAULT_UUID);
        // but we STILL FORCE A CRASH, SO WE CAN COLLECT DEBUG INFORMATION!
        throw new IllegalStateException("Style=" + style);
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
