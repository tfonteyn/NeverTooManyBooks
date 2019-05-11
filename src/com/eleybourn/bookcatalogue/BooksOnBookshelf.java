/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.RadioGroupRecyclerAdapter;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.MenuPicker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditAuthorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditFormatDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditGenreDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditLanguageDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditLocationDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditPublisherDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditSeriesDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.LendBookDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.SearchSuggestionProvider;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonSearchPage;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.tasks.OnTaskListener;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.viewmodels.BooksOnBookshelfModel;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf
        extends BaseActivity
        implements BookChangedListener {

    private final List<String> mBookshelfNameList = new ArrayList<>();
    /** Listener for GoodreadsTasks. */
    private final OnTaskListener<Object, Integer> mOnGoodreadsTaskFinished
            = new OnTaskListener<Object, Integer>() {
        @Override
        public void onTaskFinished(final int taskId,
                                   final boolean success,
                                   @StringRes final Integer result,
                                   @Nullable final Exception e) {
            GoodreadsUtils.handleGoodreadsTaskResult(taskId, success, result, e,
                                                     getWindow().getDecorView(), this);
        }
    };
    /**
     * Views for the current row level-text.
     * These are shown at the top of the list (just below the bookshelf spinner) while scrolling.
     */
    private final TextView[] mLevelTextView = new TextView[2];

    /** The number of books in the current list. */
    private TextView mBookCountView;

    /** Database access. */
    private DAO mDb;
    /** The View for the list. */
    private ListView mListView;
    private ProgressBar mProgressBar;
    /** Multi-type adapter to manage list connection to cursor. */
    private MultiTypeListCursorAdapter mAdapter;
    /** The dropdown button to select a Bookshelf. */
    private Spinner mBookshelfSpinner;
    /** The adapter used to fill the mBookshelfSpinner. */
    private ArrayAdapter<String> mBookshelfSpinnerAdapter;
    /** The ViewModel. */
    private BooksOnBookshelfModel mModel;
    /** Listener for the Bookshelf Spinner. */
    private final OnItemSelectedListener mOnBookshelfSelectionChanged = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(@NonNull final AdapterView<?> parent,
                                   @NonNull final View view,
                                   final int position,
                                   final long id) {
            @Nullable
            String selected = (String) parent.getItemAtPosition(position);
            String previous = mModel.getCurrentBookshelf().getName();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "mOnBookshelfSelectionChanged",
                             "previous=" + previous, "selected=" + selected);
            }

            if (selected != null && !selected.equalsIgnoreCase(previous)) {

                // make the new shelf the current
                mModel.setCurrentBookshelf(mDb.getBookshelfByName(selected));

                // make sure the shelf exists.
                if (mModel.getCurrentBookshelf() == null) {
                    // shelf must have been deleted, switch to 'all book'
                    mModel.setCurrentBookshelf(
                            Bookshelf.getAllBooksBookshelf(parent.getContext(), mDb));
                }
                // and make it the new default
                mModel.getCurrentBookshelf().setAsPreferred();

                // new shelf selected, so we need a new list.
                initBookList(true);
            }
        }

        @Override
        public void onNothingSelected(@NonNull final AdapterView<?> parent) {
            // Do Nothing
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        // set the search capability to local (application) search
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        mDb = new DAO();

        Bundle args = savedInstanceState == null ? getIntent().getExtras() : savedInstanceState;

        mModel = ViewModelProviders.of(this).get(BooksOnBookshelfModel.class);
        mModel.init(args);
        // listen for the booklist being ready to display.
        mModel.getBuilderResult().observe(this, holder -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debugEnter(this, "getBuilderResult().observe",
                                  "holder=" + holder);
            }

            // *always* ...
            mProgressBar.setVisibility(View.GONE);

            if (holder != null) {
                // check if we have a new list before using it.
                BooklistPseudoCursor list = holder.getResultListCursor();
                if (list != null) {
                    displayList(list, holder.getResultTargetRows());
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debugEnter(this, "getBuilderResult().observe",
                                          "no list?");
                    }
                    UserMessage.showUserMessage(mListView, R.string.error_not_available);
                }
            }
        });

        // Restore bookshelf
        mModel.setCurrentBookshelf(Bookshelf.getPreferred(this, mDb));

        String searchText = "";
        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            // Return the search results instead of all books (for the bookshelf)
            searchText = getIntent().getStringExtra(SearchManager.QUERY).trim();

        } else if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            searchText = getIntent().getDataString();
        }
        mModel.getSearchCriteria().setText(searchText);

        mProgressBar = findViewById(R.id.progressBar);

        mListView = findViewById(android.R.id.list);
        mListView.setFastScrollEnabled(true);
        mListView.setOnItemClickListener(this::onItemClick);
        mListView.setOnItemLongClickListener(this::onItemLongClick);

        initSearchField(mModel.getSearchCriteria().getText());

        mBookCountView = findViewById(R.id.book_count);

        mLevelTextView[0] = findViewById(R.id.level_1_text);
        mLevelTextView[1] = findViewById(R.id.level_2_text);

        initBookshelfSpinner();

        if (savedInstanceState == null) {
            initHints();
        }

        // populating the spinner and loading the list is done in onResume.
        Tracker.exitOnCreate(this);
    }

    /**
     * Setup the bookshelf spinner and its adapter.
     */
    private void initBookshelfSpinner() {
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);

        mBookshelfSpinnerAdapter =
                new ArrayAdapter<>(this, R.layout.booksonbookshelf_bookshelf_spinner,
                                   mBookshelfNameList);

        mBookshelfSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mBookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);
    }

    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (App.isRecreating()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit("onResume", "isRecreating",
                                 LocaleUtils.toDebugString(this));
            }
            return;
        }

        // Try to prevent null-pointer errors for rapidly pressing 'back'; this
        // is in response to errors reporting NullPointerException when, most likely,
        // a null is returned by getResources(). The most likely explanation for that
        // is the call occurs after Activity is destroyed.
        //
        // we also need to make sure we don't start the initBookList task in these cases.
        if (isFinishing() || isDestroyed()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "onResume",
                                 "isFinishing=" + isFinishing(),
                                 "isDestroyed=" + isDestroyed());
            }
            return;
        }

        // no matter what happened before or where we came from, make sure the progress bar is gone.


        // Update the activity title from the current style name.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mModel.getCurrentBookshelf().getStyle(mDb).getLabel(this));
            actionBar.setSubtitle(null);
        }

        // Update the list of bookshelves + set the current bookshelf.
        // This will trigger an initBooklist call ONLY IF the shelf was changed.
        // to be clear: will NOT build the list at app startup.
        boolean rebuildRequested = populateBookShelfSpinner();

        // if it did not call a rebuild, see if we need one anyhow,
        if (!rebuildRequested) {
            // If we got here after onActivityResult, see if we need to force a rebuild.
            Boolean forceRebuild = mModel.getAfterOnActivityResultForceRebuild();
            if (forceRebuild != null) {
                // Build the book list according to onActivityResult outcome
                initBookList(forceRebuild);

            } else {
                if (mModel.hasListBeenLoaded()) {
                    // a list has been build previously and we should re-use it.
                    BooklistBuilder booklistBuilder = mModel.getListCursor().getBuilder();
                    displayList(booklistBuilder.getListCursor(), null);
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debug(this, "onResume", "initial build");
                    }
                    initBookList(true);
                }
            }
        }

        // always reset for next iteration.
        mModel.setAfterOnActivityResultDoFullRebuild(null);

        Tracker.exitOnResume(this);
    }

    /**
     * Populate the BookShelf list in the Spinner and switch to the preferred bookshelf/style.
     *
     * @return {@code true} if we caused a bookshelf switch; i.e. if we caused an initBooklist.
     */
    private boolean populateBookShelfSpinner() {

        // disable the listener while we add the names.
        mBookshelfSpinner.setOnItemSelectedListener(null);

        @Nullable
        String previous = (String) mBookshelfSpinner.getSelectedItem();

        mBookshelfNameList.clear();

        // Add the default 'All Books' bookshelf
        mBookshelfNameList.add(getString(R.string.bookshelf_all_books));
        // default to 'All Books'
        int currentPos = 0;
        // start at 1, as position 0 is 'All Books'
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.getId() == mModel.getCurrentBookshelf().getId()) {
                currentPos = position;
            }
            position++;
            mBookshelfNameList.add(bookshelf.getName());
        }

        mBookshelfSpinnerAdapter.notifyDataSetChanged();

        String selected = mModel.getCurrentBookshelf().getName();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debug(this, "populateBookShelfSpinner",
                         "previous=" + previous, "selected=" + selected);
        }

        // (re-)enable the listener
        mBookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);

        // Set the current bookshelf. As the listener is now active, this triggers an
        // initBooklist call *IF* the selection is different from the previous one.
        mBookshelfSpinner.setSelection(currentPos);

        // Flag up if the selection was indeed different; i.e. if initBooklist was called.
        return (previous != null && !previous.equalsIgnoreCase(selected));
    }

    /**
     * Queue a rebuild of the underlying cursor and data. This is a wrapper for calling
     * {@link BooksOnBookshelfModel#initBookList(boolean, Resources, DAO)}
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    public void initBookList(final boolean isFullRebuild) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugWithStackTrace(this, "initBookList",
                                       "isFullRebuild=" + isFullRebuild);
        }
        mModel.initBookList(isFullRebuild, getResources(), mDb);

        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Save position when paused.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        if (mModel.getSearchCriteria().isEmpty()) {
            savePosition();
        }
        super.onPause();
        Tracker.exitOnPause(this);
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
     */
    private void savePosition() {
        if (isDestroyed()) {
            return;
        }

        // Save position in list
        if (mModel.hasListBeenLoaded()) {
            int topRowOffset;
            View v = mListView.getChildAt(0);
            if (v != null) {
                topRowOffset = v.getTop();
            } else {
                topRowOffset = 0;
            }
            // and save to preferences.
            mModel.savePosition(mListView.getFirstVisiblePosition(), topRowOffset);
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        BooklistPseudoCursor listCursor = mModel.getListCursor();
        if (listCursor != null) {
            listCursor.getBuilder().close();
            listCursor.close();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            TrackedCursor.dumpCursors();
        }

        if (mDb != null) {
            mDb.close();
        }

        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    /**
     * There was a search requested by the user.
     *
     * @return Returns {@code true} if search launched, and {@code false} if the activity does
     * not respond to search.
     * <p>
     * Note: uses the 'advanced' FTS search activity. To use the standard search,
     * comment this method out. The system will use {@link SearchSuggestionProvider}
     * as configured in res/xml/searchable.xml
     */
    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        mModel.getSearchCriteria().to(intent);
        startActivityForResult(intent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
        return true;
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        // add the 'add book' submenu
        MenuHandler.addCreateBookSubMenu(menu);

        menu.add(Menu.NONE, R.id.MENU_SORT, 0, R.string.menu_sort_and_style_ellipsis)
            .setIcon(R.drawable.ic_sort_by_alpha)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_CLEAR_FILTERS, 0, R.string.menu_clear_filters)
            .setIcon(R.drawable.ic_undo);

        menu.add(Menu.NONE, R.id.MENU_EXPAND, 0, R.string.menu_expand_all)
            .setIcon(R.drawable.ic_unfold_more);

        menu.add(Menu.NONE, R.id.MENU_COLLAPSE, 0, R.string.menu_collapse_all)
            .setIcon(R.drawable.ic_unfold_less);

        if (BuildConfig.DEBUG /* always */) {
            SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_DEBUG, R.id.SUBMENU_DEBUG,
                                              0, R.string.debug);

            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_RUN_TEST, 0, R.string.debug_test);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_PREFS, 0, R.string.lbl_settings);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_STYLE, 0, R.string.lbl_style);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_TRACKER, 0, R.string.debug_history);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_EXPORT_DATABASE, 0,
                        R.string.menu_copy_database);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                HintManager.displayHint(getLayoutInflater(),
                                        R.string.hint_booklist_style_menu,
                                        () -> doSortMenu(false));
                return true;

            case R.id.MENU_EXPAND:
                expandNode(true);
                return true;

            case R.id.MENU_COLLAPSE:
                expandNode(false);
                return true;

            case R.id.MENU_CLEAR_FILTERS:
                mModel.getSearchCriteria().clear();
                initBookList(true);
                return true;

            default:
                if (BuildConfig.DEBUG  /* always */) {
                    switch (item.getItemId()) {
                        case R.id.MENU_DEBUG_RUN_TEST:
                            // manually swapped for whatever test I want to run...
                            // crude? yup! nasty? absolutely!
                            // doSomething();
                            return true;

                        case R.id.MENU_DEBUG_DUMP_PREFS:
                            Prefs.dumpPreferences(null);
                            return true;

                        case R.id.MENU_DEBUG_DUMP_STYLE:
                            Logger.debug(this, "onOptionsItemSelected",
                                         mModel.getCurrentBookshelf().getStyle(mDb));
                            return true;

                        case R.id.MENU_DEBUG_DUMP_TRACKER:
                            Logger.debug(this, "onOptionsItemSelected",
                                         Tracker.getEventsInfo());
                            return true;

                        case R.id.MENU_DEBUG_EXPORT_DATABASE:
                            StorageUtils.exportDatabaseFiles();
                            UserMessage.showUserMessage(mListView,
                                                        R.string.progress_end_backup_success);
                            return true;
                    }
                }

                return MenuHandler.handleBookSubMenu(this, item)
                        || super.onOptionsItemSelected(item);
        }
    }

    /**
     * Expand/Collapse the current position in the list.
     *
     * @param expand {@code true} to expand, {@code false} to collapse
     */
    private void expandNode(final boolean expand) {
        // It is possible that the list will be empty, if so, ignore
        if (mListView.getChildCount() != 0) {
            int oldAbsPos = mAdapter.getAbsolutePosition(mListView.getChildAt(0));
            savePosition();
            BooklistBuilder booklistBuilder = mModel.getListCursor().getBuilder();
            booklistBuilder.expandAll(expand);
            mModel.setTopRow(booklistBuilder.getPosition(oldAbsPos));
            displayList(booklistBuilder.getListCursor(), null);
        }
    }

    /**
     * The user clicked on a row:
     * - Book: open the details screen.
     * - expand/collapse the section as appropriate.
     *
     * @param position in the list that was clicked.
     */
    public void onItemClick(@SuppressWarnings("unused") @NonNull final AdapterView<?> parent,
                            @SuppressWarnings("unused") @NonNull final View view,
                            final int position,
                            @SuppressWarnings("unused") final long id) {
        BooklistPseudoCursor listCursor = mModel.getListCursor();
        listCursor.moveToPosition(position);
        BooklistCursorRow row = listCursor.getCursorRow();

        //noinspection SwitchStatementWithTooFewBranches
        switch (row.getRowKind()) {
            // If it's a book, view or edit it.
            case BooklistGroup.RowKind.BOOK:
                long bookId = row.getBookId();
                boolean openInReadOnly =
                        App.getPrefs().getBoolean(Prefs.pk_bob_open_book_read_only, true);

                if (openInReadOnly) {
                    String listTable = listCursor.getBuilder()
                                                 .createFlattenedBooklist()
                                                 .getTable()
                                                 .getName();

                    Intent intent = new Intent(this, BookDetailsActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, bookId)
                            .putExtra(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST, listTable)
                            .putExtra(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST_POSITION, position);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_VIEW);

                } else {
                    Intent intent = new Intent(this, EditBookActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, bookId)
                            .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                }
                break;

            // if's not a book, expand/collapse as needed
            default:
                // If it's a level, expand/collapse. Technically, we could expand/collapse any level
                // but storing and recovering the view becomes unmanageable.
                // ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/542
                if (row.getLevel() == 1) {
                    listCursor.getBuilder().toggleExpandNode(row.getAbsolutePosition());
                    listCursor.requery();
                    mAdapter.notifyDataSetChanged();
                }
        }
    }

    /**
     * User long-clicked on a row. Bring up a context menu as appropriate.
     *
     * @param view     to tie the context menu to
     * @param position in the list that was clicked.
     */
    private boolean onItemLongClick(@SuppressWarnings("unused") @NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    @SuppressWarnings("unused") final long id) {
        BooklistPseudoCursor listCursor = mModel.getListCursor();
        listCursor.moveToPosition(position);

        Context context = view.getContext();
        BooklistCursorRow cursorRow = listCursor.getCursorRow();
        Menu menu = MenuPicker.createMenu(context);
        // build/check the menu for this row
        if (prepareListViewContextMenu(menu, cursorRow)) {
            // we have a menu to show
            int level = cursorRow.getLevel();
            String menuTitle;
            if (mModel.getCurrentBookshelf().getStyle(mDb).groupCount() < level) {
                // it's a book (level 3 or 4)
                menuTitle = cursorRow.getTitle();
            } else {
                // it's a group (level 1,2,3)
                menuTitle = cursorRow.getLevelText(this, level);
            }
            // bring up the context menu
            new MenuPicker<>(context, menuTitle, menu, position, (menuItem, pos) -> {
                listCursor.moveToPosition(pos);
                return onContextItemSelected(menuItem, listCursor.getCursorRow());
            }).show();
        }
        return true;
    }

    /**
     * Adds 'standard' menu options based on row type.
     *
     * @param row Row view pointing to current row for this context menu
     *
     * @return {@code true} if there actually is a menu to show.
     * {@code false} if not OR if the only menus would be the 'search Amazon' set.
     */
    boolean prepareListViewContextMenu(@NonNull final Menu /* in/out */ menu,
                                       @NonNull final BooklistCursorRow row) {
        menu.clear();

        int rowKind = row.getRowKind();
        switch (rowKind) {
            case BooklistGroup.RowKind.BOOK:
                if (row.isRead()) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read)
                        .setIcon(R.drawable.ic_check_box);
                }

                menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete_book)
                    .setIcon(R.drawable.ic_delete);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit_book)
                    .setIcon(R.drawable.ic_edit);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_NOTES, 0, R.string.menu_edit_book_notes)
                    .setIcon(R.drawable.ic_note);

                if (App.isUsed(DBDefinitions.KEY_LOANEE)) {
                    boolean isAvailable = null == mDb.getLoaneeByBookId(row.getBookId());
                    if (isAvailable) {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_LOAN,
                                 MenuHandler.MENU_ORDER_LENDING,
                                 R.string.menu_loan_lend_book)
                            .setIcon(R.drawable.ic_people);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_RETURNED,
                                 MenuHandler.MENU_ORDER_LENDING,
                                 R.string.menu_loan_return_book)
                            .setIcon(R.drawable.ic_people);
                    }
                }

                menu.add(Menu.NONE, R.id.MENU_SHARE, MenuHandler.MENU_ORDER_SHARE,
                         R.string.menu_share_this)
                    .setIcon(R.drawable.ic_share);

                menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS, 0,
                         R.string.gr_menu_send_to_goodreads)
                    .setIcon(R.drawable.ic_goodreads);
                break;

            case BooklistGroup.RowKind.AUTHOR:
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_DETAILS, 0, R.string.menu_author_details)
                    .setIcon(R.drawable.ic_details);
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT, 0, R.string.menu_edit_author)
                    .setIcon(R.drawable.ic_edit);
                if (row.isAuthorComplete()) {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_incomplete)
                        .setIcon(R.drawable.ic_check_box);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_complete)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                }
                break;

            case BooklistGroup.RowKind.SERIES:
                if (row.getSeriesId() != 0) {
                    menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE, 0, R.string.menu_delete_series)
                        .setIcon(R.drawable.ic_delete);
                    menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT, 0, R.string.menu_edit_series)
                        .setIcon(R.drawable.ic_edit);
                    if (row.isSeriesComplete()) {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_incomplete)
                            .setIcon(R.drawable.ic_check_box);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_complete)
                            .setIcon(R.drawable.ic_check_box_outline_blank);
                    }
                }
                break;

            case BooklistGroup.RowKind.PUBLISHER:
                if (!row.getPublisherName().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.LANGUAGE:
                if (!row.getLanguageCode().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.LOCATION:
                if (!row.getLocation().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.GENRE:
                if (!row.getGenre().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.FORMAT:
                if (!row.getFormat().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "Unexpected rowKind=" + rowKind);
                break;
        }

        // if there are no specific menus for the current row.
        if (menu.size() == 0) {
            return false;
        }

        // at least one other menu item; now add Amazon menus ?
        boolean hasAuthor = row.hasAuthorId() && row.getAuthorId() > 0;
        boolean hasSeries = row.hasSeriesId() && row.getSeriesId() > 0;
        if (hasAuthor || hasSeries) {
            SubMenu subMenu = MenuHandler.addAmazonSearchSubMenu(menu);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, hasAuthor);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                                    hasAuthor && hasSeries);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_IN_SERIES, hasSeries);
        }

        return true;
    }

    /**
     * Handle the 'standard' menu items. If the passed activity implements
     * {@link BookChangedListener} then inform it when changes have been made.
     * <p>
     * ENHANCE: Consider using {@link LocalBroadcastManager} instead of {@link BookChangedListener}.
     *
     * @param menuItem Related MenuItem
     * @param row      Row view for affected cursor row
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                  @NonNull final BooklistCursorRow row) {

        final long bookId = row.getBookId();

        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_DELETE:
                String title = row.getTitle();
                List<Author> authors = mDb.getAuthorsByBookId(bookId);
                StandardDialogs.deleteBookAlert(this, title, authors, () -> {
                    mDb.deleteBook(bookId);
                    onBookChanged(bookId, BookChangedListener.BOOK_WAS_DELETED, null);
                });

                return true;

            case R.id.MENU_BOOK_READ:
                // toggle the read status
                if (mDb.setBookRead(bookId, !row.isRead())) {
//                    Bundle data = new Bundle();
//                    data.putBoolean(KEY_READ, !row.isRead());
//                    data.putInt(UniqueId.POSITION, row.getPosition());
                    onBookChanged(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;

            case R.id.MENU_BOOK_EDIT: {
                Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, bookId)
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_NOTES: {
                Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, bookId)
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB,
                                  EditBookFragment.TAB_EDIT_NOTES);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_EDIT_LOAN:
                // reports back to the Activity via the BookChangedListener interface
                LendBookDialogFragment.show(getSupportFragmentManager(),
                                            bookId, row.getAuthorId(), row.getTitle());
                return true;

            case R.id.MENU_BOOK_LOAN_RETURNED:
                mDb.deleteLoan(bookId);
                onBookChanged(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_SHARE:
                Book book = new Book(bookId, mDb);
                startActivity(Intent.createChooser(book.getShareBookIntent(this),
                                                   getString(R.string.menu_share_this)));
                return true;

            case R.id.MENU_BOOK_SEND_TO_GOODREADS:
                //TEST sendOneBook
                new GoodreadsUtils.SendOneBookTask(this, bookId, mOnGoodreadsTaskFinished)
                        .execute();
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT:
                //noinspection ConstantConditions
                EditSeriesDialogFragment.show(getSupportFragmentManager(),
                                              mDb.getSeries(row.getSeriesId()));
                return true;

            case R.id.MENU_SERIES_COMPLETE:
                // toggle the complete status
                if (mDb.setSeriesComplete(row.getSeriesId(), !row.isSeriesComplete())) {
                    onBookChanged(0, BookChangedListener.SERIES, null);
                }
                return true;

            case R.id.MENU_SERIES_DELETE: {
                Series series = mDb.getSeries(row.getSeriesId());
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(
                            this, series, () -> {
                                mDb.deleteSeries(series.getId());
                                onBookChanged(0, BookChangedListener.SERIES, null);
                            });
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_DETAILS: {
                Intent intent = new Intent(this, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, row.getAuthorId());
                startActivity(intent);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT:
                //noinspection ConstantConditions
                EditAuthorDialogFragment.show(getSupportFragmentManager(),
                                              mDb.getAuthor(row.getAuthorId()));
                return true;

            case R.id.MENU_AUTHOR_COMPLETE:
                // toggle the complete status
                if (mDb.setAuthorComplete(row.getAuthorId(), !row.isAuthorComplete())) {
                    onBookChanged(0, BookChangedListener.AUTHOR, null);
                }
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT:
                EditPublisherDialogFragment.show(getSupportFragmentManager(),
                                                 new Publisher(row.getPublisherName()));
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT:
                new EditFormatDialog(this, mDb,
                                     () -> onBookChanged(0, BookChangedListener.FORMAT, null))
                        .edit(row.getFormat());
                return true;

            case R.id.MENU_GENRE_EDIT:
                new EditGenreDialog(this, mDb,
                                    () -> onBookChanged(0, BookChangedListener.GENRE, null))
                        .edit(row.getGenre());
                return true;

            case R.id.MENU_LANGUAGE_EDIT:
                new EditLanguageDialog(this, mDb,
                                       () -> onBookChanged(0, BookChangedListener.LANGUAGE, null))
                        .edit(row.getLanguageCode());
                return true;

            case R.id.MENU_LOCATION_EDIT:
                new EditLocationDialog(this, mDb,
                                       () -> onBookChanged(0, BookChangedListener.LOCATION, null))
                        .edit(row.getLocation());
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonSearchPage.open(this, getAuthorFromRow(row), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonSearchPage.open(this, null, getSeriesFromRow(row));
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonSearchPage.open(this, getAuthorFromRow(row), getSeriesFromRow(row));
                return true;

            default:
                return false;
        }
    }

    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @return formatted Author name
     */
    @Nullable
    private String getAuthorFromRow(@NonNull final BooklistCursorRow row) {
        if (row.hasAuthorId() && row.getAuthorId() > 0) {
            Author author = mDb.getAuthor(row.getAuthorId());
            if (author != null) {
                return author.getLabel();
            }

        } else if (row.getRowKind() == BooklistGroup.RowKind.BOOK) {
            List<Author> authors = mDb.getAuthorsByBookId(row.getBookId());
            if (!authors.isEmpty()) {
                return authors.get(0).getLabel();
            }
        }
        return null;
    }

    /**
     * @return the unformatted Series name (i.e. without the number)
     */
    @Nullable
    private String getSeriesFromRow(@NonNull final BooklistCursorRow row) {
        if (row.hasSeriesId() && row.getSeriesId() > 0) {
            Series series = mDb.getSeries(row.getSeriesId());
            if (series != null) {
                return series.getName();
            }
        } else if (row.getRowKind() == BooklistGroup.RowKind.BOOK) {
            ArrayList<Series> series = mDb.getSeriesByBookId(row.getBookId());
            if (!series.isEmpty()) {
                return series.get(0).getName();
            }
        }
        return null;
    }

    /**
     * Reminder: don't do any commits on the fragment manager.
     * This includes showing fragments, or starting tasks that show fragments.
     * Do this in {@link #onResume} which will be called after onActivityResult.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        mModel.setCurrentPositionedBookId(0);

        switch (requestCode) {
            case UniqueId.REQ_BOOK_VIEW:
            case UniqueId.REQ_BOOK_EDIT:
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                        // handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the ID 'above' the deleted one;
                        mModel.setAfterOnActivityResultDoFullRebuild(false);
                        break;

                    case Activity.RESULT_OK:
                        //noinspection ConstantConditions
                        long newId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                        if (newId != 0) {
                            mModel.setCurrentPositionedBookId(newId);
                        }
                        mModel.setAfterOnActivityResultDoFullRebuild(false);
                        break;


                    default:
                        if (resultCode != Activity.RESULT_CANCELED) {
                            Logger.warnWithStackTrace(this, "unknown resultCode=" + resultCode);
                        }
                        break;
                }
                break;

            case UniqueId.REQ_BOOK_SEARCH:
                if (resultCode == Activity.RESULT_OK) {
                    /* don't enforce having an id. We might not have found or added anything.
                     * but if we do, the data will be what EditBookActivity returns. */
                    if (data != null) {
                        long newId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                        if (newId != 0) {
                            mModel.setCurrentPositionedBookId(newId);
                        }
                    }
                    // regardless, do a rebuild just in case
                    mModel.setAfterOnActivityResultDoFullRebuild(false);
                }
                break;

            case UniqueId.REQ_ADVANCED_LOCAL_SEARCH:
                // no changes made, but we might have data to act on.
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Bundle extras = data.getExtras();
                        if (extras != null) {
                            mModel.getSearchCriteria().from(extras);
                            initSearchField(mModel.getSearchCriteria().getText());
                        }
                        mModel.setAfterOnActivityResultDoFullRebuild(true);
                    }
                }
                break;

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (resultCode == Activity.RESULT_OK) {
                    // the last edited/inserted shelf
                    //noinspection ConstantConditions
                    long bookshelfId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                    mModel.setCurrentBookshelf(mDb.getBookshelf(bookshelfId));

                    // bookshelves modified, update everything
                    mModel.setAfterOnActivityResultDoFullRebuild(true);
                }
                break;

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (resultCode == Activity.RESULT_OK) {
                    if ((data != null) && data.hasExtra(UniqueId.BKEY_IMPORT_RESULT)) {
                        // RestoreActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                       ImportSettings.NOTHING);

                        if ((options & ImportSettings.PREFERENCES) != 0) {
                            // the imported prefs could have a different preferred bookshelf.
                            Bookshelf newBookshelf = Bookshelf.getPreferred(this, mDb);
                            if (!mModel.getCurrentBookshelf().equals(newBookshelf)) {
                                // if it was.. then switch to it.
                                mModel.setCurrentBookshelf(newBookshelf);
                                mModel.setAfterOnActivityResultDoFullRebuild(true);
                            }
                        }
                    } else if ((data != null) && data.hasExtra(UniqueId.BKEY_EXPORT_RESULT)) {
                        // BackupActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_EXPORT_RESULT,
                                                       ExportSettings.NOTHING);
                    }

//                    if ((data != null) && data.hasExtra(UniqueId.ZZZZ)) {
//                        // AdminActivity has results of it's own,, but no action needed for them.
//                        // child-activities results:
//                        // SearchAdminActivity:
//                    }
                }
                break;

            // from BaseActivity Nav Panel or from sort menu dialog
            // TODO: more complicated then it should be....
            case UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES: {
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                    case UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_PREFERRED_STYLES:
                        // no data
                        mModel.setAfterOnActivityResultDoFullRebuild(true);
                        break;

                    case UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE:
                        //noinspection ConstantConditions
                        BooklistStyle style = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                        // can be null if a style was deleted.
                        if (style != null) {
                            // save the new bookshelf/style combination
                            mModel.getCurrentBookshelf().setAsPreferred();
                            mModel.getCurrentBookshelf().setStyle(mDb, style);
                        }
                        mModel.setAfterOnActivityResultDoFullRebuild(true);
                        break;

                    default:
                        if (resultCode != Activity.RESULT_CANCELED) {
                            Logger.warnWithStackTrace(this, "unknown resultCode=" + resultCode);
                        }
                        break;
                }
                break;
            }

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Display the passed cursor in the ListView.
     *
     * @param listCursor New cursor to use
     * @param targetRows if set, change the position to targetRow.
     */
    private void displayList(@NonNull final BooklistPseudoCursor listCursor,
                             @Nullable final ArrayList<BookRowInfo> targetRows) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugEnter(this, "displayList");
        }

        mProgressBar.setVisibility(View.GONE);

        // get a local copy.
        final BooklistStyle style = mModel.getCurrentBookshelf().getStyle(mDb);

        populateBookCountField(style);

        long t0;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            t0 = System.nanoTime();
        }

        // Save the old list so we can close it later
        BooklistPseudoCursor oldList = mModel.getListCursor();

        // and set the new list
        mModel.setListCursor(listCursor);

        // Get new handler and adapter since list may be radically different structure
        // compared to previous time we displayed the list.
        BooksMultiTypeListHandler listHandler = new BooksMultiTypeListHandler(this, mDb, style);
        mAdapter = new MultiTypeListCursorAdapter(this, mModel.getListCursor(), listHandler);

        mListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of ListView
        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mModel.getListCursor().getCount();
        if (mModel.getTopRow() >= count) {
            mModel.setTopRow(count - 1);
            mListView.setSelection(mModel.getTopRow());
        } else {
            mListView.setSelectionFromTop(mModel.getTopRow(), mModel.getTopRowOffset());
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and we can tell
        // if it is already in the view.
        if (targetRows != null) {
            fixPositionWhenDrawn(mListView, targetRows);
        }

        // setup the level-text's at the top of the list
        final boolean showLevelTexts = setLevelTextVisibility(style);
        // Set the initial details to the current first visible row.
        if (count > 0 && showLevelTexts) {
            setLevelText(mModel.getTopRow());
        }

        // Define a scroller to update header detail when the top row changes
        mListView.setOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScroll(@NonNull final AbsListView view,
                                         final int firstVisibleItem,
                                         final int visibleItemCount,
                                         final int totalItemCount) {
                        // Need to check isDestroyed() because BooklistPseudoCursor misbehaves when
                        // activity terminates and closes cursor
                        if (mModel.getLastTopRow() != firstVisibleItem
                                && !isDestroyed()
                                && showLevelTexts) {
                            setLevelText(firstVisibleItem);
                        }
                    }

                    @Override
                    public void onScrollStateChanged(@NonNull final AbsListView view,
                                                     final int scrollState) {
                    }
                }
        );

        // Close old list
        if (oldList != null) {
            if (mModel.getListCursor().getBuilder() != oldList.getBuilder()) {
                oldList.getBuilder().close();
            }
            oldList.close();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.debugExit(this, "displayList",
                             (System.nanoTime() - t0) + "nano");
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugExit(this, "displayList");
        }
    }

    /**
     * @param style to use
     *
     * @return {@code true} if the style supports any level at all.
     */
    private boolean setLevelTextVisibility(@NonNull final BooklistStyle style) {

        // for level, set the visibility of the views.
        for (int level = 1; level <= 2; level++) {

            int index = level - 1;

            // a level is visible if
            // 1. the cursor provides the data for this level, and
            // 2. the style defined the level.
            if (mModel.getListCursor().levels() > level && style.hasSummaryForLevel(level)) {
                mLevelTextView[index].setVisibility(View.VISIBLE);
                mLevelTextView[index].setText("");
            } else {
                mLevelTextView[index].setVisibility(View.GONE);
            }
        }
        // do we show any levels?
        return style.hasSummaryForLevel(1) || style.hasSummaryForLevel(2);
    }

    /**
     * Update the list header to match the current top item.
     *
     * @param firstVisibleItem Top row which is visible
     */
    private void setLevelText(@IntRange(from = 0) final int firstVisibleItem) {
        if (firstVisibleItem >= 0) {
            mModel.setLastTopRow(firstVisibleItem);
        } else {
            mModel.setLastTopRow(0);
        }
        if (mLevelTextView[0].getVisibility() == View.VISIBLE) {
            Context context = mLevelTextView[0].getContext();
            BooklistCursorRow row = mModel.getListCursor().getCursorRow();

            if (mModel.getListCursor().moveToPosition(mModel.getLastTopRow())) {
                mLevelTextView[0].setText(row.getLevelText(context, 1));
                if (mLevelTextView[1].getVisibility() == View.VISIBLE) {
                    mLevelTextView[1].setText(row.getLevelText(context, 2));
                }
            }
        }
    }

    /**
     * Queue a runnable to set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #displayList}
     */
    private void fixPositionWhenDrawn(@NonNull final ListView listView,
                                      @NonNull final ArrayList<BookRowInfo> targetRows) {
        mListView.post(() -> {
            // Find the actual extend of the current view and get centre.
            int first = listView.getFirstVisiblePosition();
            int last = listView.getLastVisiblePosition();
            int centre = (last + first) / 2;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                             " New List: (" + first + ", " + last + ")<-" + centre);
            }
            // Get the first 'target' and make it 'best candidate'
            BookRowInfo best = targetRows.get(0);
            int dist = Math.abs(best.listPosition - centre);
            // Loop all other rows, looking for a nearer one
            for (int i = 1; i < targetRows.size(); i++) {
                BookRowInfo ri = targetRows.get(i);
                int newDist = Math.abs(ri.listPosition - centre);
                if (newDist < dist) {
                    dist = newDist;
                    best = ri;
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                             " Best listPosition @" + best.listPosition);
            }
            // Try to put at top if not already visible, or only partially visible
            if (first >= best.listPosition || last <= best.listPosition) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                    Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                                 " Adjusting position");
                }
                // setSelectionFromTop does not seem to always do what is expected.
                // But adding smoothScrollToPosition seems to get the job done reasonably well.
                //
                // Specific problem occurs if:
                // - put phone in portrait mode
                // - edit a book near bottom of list
                // - turn phone to landscape
                // - save the book (don't cancel)
                // Book will be off bottom of screen without the smoothScroll in the
                // second Runnable.
                //
                listView.setSelectionFromTop(best.listPosition, 0);
                // Code below does not behave as expected.
                // Results in items often being near bottom.
                //lv.setSelectionFromTop(best.listPosition, lv.getHeight() / 2);

                // Without this call some positioning may be off by one row (see above).
                final int newPos = best.listPosition;
                mListView.post(() -> listView.smoothScrollToPosition(newPos));

                //int newTop = best.listPosition - (last-first)/2;
                // if (BuildConfig.DEBUG && BOOKS_ON_BOOKSHELF) {
                //Logger.info(this, "fixPositionWhenDrawn", "New Top @" + newTop );
                //}
                //lv.setSelection(newTop);
            }
        });
    }

    /**
     * display or hide the search text field in the header.
     *
     * @param searchText the text which was used for the search (if any).
     */
    void initSearchField(@NonNull final String searchText) {
        TextView searchTextView = findViewById(R.id.search_text);
        if (searchText.isEmpty()) {
            searchTextView.setVisibility(View.GONE);
        } else {
            searchTextView.setVisibility(View.VISIBLE);
            searchTextView.setText(getString(R.string.search_with_text, searchText));
        }
    }

    /**
     * Show the hints used in this class.
     */
    private void initHints() {
        HintManager.displayHint(getLayoutInflater(),
                                R.string.hint_view_only_book_details, null);
        HintManager.displayHint(getLayoutInflater(),
                                R.string.hint_book_list, null);
        if (StartupActivity.showAmazonHint()) {
            HintManager.showAmazonHint(getLayoutInflater());
        }
    }

    /**
     * Display the number of books in the current list.
     *
     * @param style the style decides the format of the counter field.
     */
    private void populateBookCountField(@NonNull final BooklistStyle style) {
        int showHeaderFlags = style.getShowHeaderInfo();
        if ((showHeaderFlags & BooklistStyle.SUMMARY_SHOW_COUNT) != 0) {
            int totalBooks = mModel.getTotalBooks();
            int uniqueBooks = mModel.getUniqueBooks();
            String stringArgs;
            if (uniqueBooks != totalBooks) {
                stringArgs = getString(R.string.info_displaying_n_books_in_m_entries,
                                       uniqueBooks, totalBooks);
            } else {
                stringArgs = getResources().getQuantityString(R.plurals.displaying_n_books,
                                                              uniqueBooks, uniqueBooks);
            }
            mBookCountView.setText(getString(R.string.brackets, stringArgs));
            mBookCountView.setVisibility(View.VISIBLE);
        } else {
            mBookCountView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBookChanged(final long bookId,
                              final int fieldsChanged,
                              @Nullable final Bundle data) {

        // changes were made to a single book
        if (bookId > 0) {
            //ENHANCE: update the modified row without a rebuild
            if ((fieldsChanged & BookChangedListener.BOOK_READ) != 0) {
                savePosition();
                initBookList(true);

//            } else if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                // we don't display the lend-status in the list for now.
//                if (data != null) {
//                    data.getString(UniqueId.KEY_LOANEE);

            } else if ((fieldsChanged & BookChangedListener.BOOK_WAS_DELETED) != 0) {
                //ENHANCE: remove the defunct book without a rebuild
                savePosition();
                initBookList(true);
            }
        } else {
            // changes were made to (potentially) the whole list
            if (fieldsChanged != 0) {
                savePosition();
                initBookList(true);
            }
        }
    }

    /**
     * Setup the sort options. This function will also call fillData when
     * complete having loaded the appropriate view.
     *
     * @param showAll if {@code true} show all styles, otherwise only the preferred ones.
     */
    private void doSortMenu(final boolean showAll) {
        FragmentManager fm = getSupportFragmentManager();
        SortMenuFragment smf;

        // Tried several tactics to re-show the fragment to no avail. Leaving these comments.
//        smf = (SortMenuFragment) fm.findFragmentByTag(SortMenuFragment.TAG);
//        if (smf == null) {
        smf = SortMenuFragment.newInstance(showAll, mModel.getCurrentBookshelf().getStyle(mDb));
        smf.show(fm, SortMenuFragment.TAG);
//        } else {
        // something... but what?
//        }
    }

    /**
     * Handle the style that a user has selected.
     *
     * @param style that was selected
     */
    private void onStyleSelected(@NonNull final BooklistStyle style) {

        // save the new bookshelf/style combination
        mModel.getCurrentBookshelf().setAsPreferred();
        mModel.getCurrentBookshelf().setStyle(mDb, style);

        /* Set the rebuild state like this is the first time in, which it sort of is, given we
         * are changing style. There is very little ability to preserve position when going from
         * a list sorted by author/series to on sorted by unread/addedDate/publisher.
         * Keeping the current row/pos is probably the most useful thing we can do since we *may*
         * come back to a similar list.
         */
        int topRowOffset;
        View view = mListView.getChildAt(0);
        if (view != null) {
            topRowOffset = view.getTop();
        } else {
            topRowOffset = 0;
        }

        mModel.setTopRow(mListView.getFirstVisiblePosition());
        mModel.setTopRowOffset(topRowOffset);

        // New style, so use the user-pref for rebuild
        mModel.setRebuildState(BooklistBuilder.getListRebuildState());

        // Do a rebuild
        initBookList(true);
    }

    public static class SortMenuFragment
            extends DialogFragment {

        /** Fragment manager tag. */
        public static final String TAG = SortMenuFragment.class.getSimpleName();

        private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";
        private static final String BKEY_CURRENT_STYLE = TAG + ":currentStyle";

        private boolean mShowAllStyles;
        private BooklistStyle mCurrentStyle;

        private RecyclerView mStylesListView;
        private List<BooklistStyle> mList;
        private RadioGroupRecyclerAdapter<BooklistStyle> mAdapter;

        /**
         * The sort menu is 100% tied to the main class, so might as well give full access.
         * And yes, this is not clean.
         */
        private BooksOnBookshelf mActivity;

        /**
         * Constructor.
         *
         * @param showAllStyles {@code false} to show only preferred style (or all if no
         *                      preferred ones)
         *                      {@code true} to show all
         * @param currentStyle  pre-selected/current style
         *
         * @return fragment
         */
        static SortMenuFragment newInstance(final boolean showAllStyles,
                                            @NonNull final BooklistStyle currentStyle) {
            SortMenuFragment frag = new SortMenuFragment();
            Bundle args = new Bundle();
            args.putBoolean(BKEY_SHOW_ALL_STYLES, showAllStyles);
            args.putParcelable(BKEY_CURRENT_STYLE, currentStyle);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            mActivity = (BooksOnBookshelf) getActivity();

            Bundle args = requireArguments();
            mShowAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);
            mCurrentStyle = args.getParcelable(BKEY_CURRENT_STYLE);

            @SuppressWarnings("ConstantConditions")
            View root = getActivity().getLayoutInflater()
                                     .inflate(R.layout.dialog_styles_menu, null);

            mStylesListView = root.findViewById(R.id.styles);
            mStylesListView.setHasFixedSize(true);
            mStylesListView.setLayoutManager(new LinearLayoutManager(getContext()));

            mList = new ArrayList<>(BooklistStyles.getStyles(mActivity.mDb, mShowAllStyles)
                                                  .values());

            //noinspection ConstantConditions
            mAdapter = new RadioGroupRecyclerAdapter<>(getContext(), mList, mCurrentStyle, (v) -> {
                BooklistStyle style = (BooklistStyle) v.getTag(R.id.TAG_ITEM);
                mActivity.onStyleSelected(style);
                dismiss();
            });
            mStylesListView.setAdapter(mAdapter);

            @StringRes
            int moreOrLess = mShowAllStyles ? R.string.menu_show_fewer_ellipsis
                                            : R.string.menu_show_more_ellipsis;

            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.title_select_style)
                    .setView(root)
                    .setNeutralButton(R.string.menu_customize_ellipsis, (d, which) -> {
                        Intent intent = new Intent(getContext(), PreferredStylesActivity.class);
                        startActivityForResult(intent,
                                               UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES);
                    })
                    .setPositiveButton(moreOrLess, (d, which) ->
                            mActivity.doSortMenu(!mShowAllStyles))
                    .create();
        }
    }
}
