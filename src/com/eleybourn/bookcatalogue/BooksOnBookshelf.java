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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler.BooklistChangeListener;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.searches.AdvancedLocalSearchActivity;
import com.eleybourn.bookcatalogue.settings.BooklistStyleSettingsFragment;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf
        extends BaseListActivity
        implements
        BooklistChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    /** Preference name - Saved position of last top row. */
    public static final String PREF_BOB_TOP_ROW = "BooksOnBookshelf.TopRow";
    /** Preference name - Saved position of last top row offset from view top. */
    public static final String PREF_BOB_TOP_ROW_OFFSET = "BooksOnBookshelf.TopRowOffset";

    /** Activity Request Code. */
    static final int REQ_BOOK_EDIT = 1;
    /** Activity Request Code. */
    static final int REQ_BOOK_SEARCH = 2;
    /** Activity Request Code. */
    private static final int REQ_BOOK_VIEW = 0;
    /** Activity Request Code. */
    private static final int REQ_ADVANCED_LOCAL_SEARCH = 10;

    /** Task queue to get book lists in background. */
    private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("BoB-GetBookListTask", 1);

    /**
     * Flag indicating activity has been destroyed.
     * Used for background tasks.
     */
    private boolean mIsDead;
    /**
     * Flag to indicate that a list has been successfully loaded.
     * Affects the way we save state
     */
    private boolean mListHasBeenLoaded;

    /**
     * ProgressDialog used to display "Getting books...".
     * Needed here so we can dismiss it on close.
     */
    @Nullable
    @Deprecated
    private ProgressDialog mProgressDialog;

    /**
     * A book ID used for keeping/updating current list position,
     * eg. when a book is viewed/edited.
     */
    private long mCurrentPositionedBookId;
    /** Currently selected bookshelf. */
    private Bookshelf mCurrentBookshelf;

    /** Holder for all (semi)supported search criteria. See class for mre info. */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    /** Saved position of top row. */
    private int mTopRow;
    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTopRow = -1;
    /**
     * Saved position of last top row offset from view top.
     * <p>
     * {@link ListView#setSelectionFromTop(int position, int y)} :
     * * @param y The distance from the top edge of the ListView (plus padding) that the
     * *        item will be positioned.
     */
    private int mTopRowOffset;
    /** Handler to manage all Views on the list. */
    private BooksMultiTypeListHandler mListHandler;
    /** Current displayed list cursor. */
    private BooklistPseudoCursor mListCursor;
    /** Multi-type adapter to manage list connection to cursor. */
    private MultiTypeListCursorAdapter mAdapter;
    /** Preferred booklist state in next rebuild. */
    private int mRebuildState;
    /** Total number of books in current list. */
    private int mTotalBooks;
    /** Total number of unique books in current list. */
    private int mUniqueBooks;
    private Spinner mBookshelfSpinner;
    private ArrayAdapter<String> mBookshelfAdapter;

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        //setTitle(R.string.app_name);

        if (savedInstanceState == null) {
            // Get preferred booklist state to use from preferences;
            // default to always expanded (MUCH faster than 'preserve' with lots of books)
            mRebuildState = BooklistBuilder.getListRebuildState();

            // optional search criteria.
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                mSearchCriteria.from(extras);
            }
        } else {
            // Always preserve state when rebuilding/recreating etc
            mRebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
            mSearchCriteria.from(savedInstanceState);
        }

        mDb = new DBA(this);

        // Restore bookshelf
        mCurrentBookshelf = Bookshelf.getPreferred(mDb);

        // Restore list position on bookshelf
        mTopRow = Prefs.getPrefs().getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = Prefs.getPrefs().getInt(PREF_BOB_TOP_ROW_OFFSET, 0);

        // set the search capability to local (application) search
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        final Intent intent = getIntent();
        String searchText = "";
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Return the search results instead of all books (for the bookshelf)
            searchText = intent.getStringExtra(SearchManager.QUERY).trim();

        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            searchText = intent.getDataString();
        }

        mSearchCriteria.text = initSearchField(searchText);

        // use the custom fast scroller (the ListView in the XML is our custom version).
        getListView().setFastScrollEnabled(true);

//            FloatingActionButton floatingAddButton = findViewById(R.id.floatingAddButton);
//            floatingAddButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(@NonNull final View v) {
//                    Snackbar.make(v, "Here's a Snackbar", Snackbar.LENGTH_LONG)
//                            .setAction("Action", null).show();
//                }
//            });
//            floatingAddButton.show();

        // Handle item click events

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;

        initBookshelfSpinner();

        // populate and switch to the preferred bookshelf.
        populateBookShelfSpinner();

        // global criteria are all null
        initBookList(true);

        if (savedInstanceState == null) {
            initHints();
        }
        Tracker.exitOnCreate(this);
    }

    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, AdvancedLocalSearchActivity.class);
        mSearchCriteria.to(intent);
        startActivityForResult(intent, REQ_ADVANCED_LOCAL_SEARCH);
        return true;
    }

    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        // Try to prevent null-pointer errors for rapidly pressing 'back'; this
        // is in response to errors reporting NullPointerException when, most likely,
        // a null is returned by getResources(). The most likely explanation for that
        // is the call occurs after Activity is destroyed.
        if (mIsDead) {
            return;
        }

        // bookshelves can have changed, so reload
        populateBookShelfSpinner();

        Tracker.exitOnResume(this);
    }

    /**
     * Save position when paused.
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mSearchCriteria.isEmpty()) {
            savePosition();
        }

        if (isFinishing()) {
            mTaskQueue.terminate();
        }

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        super.onPause();
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        mSearchCriteria.to(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        mIsDead = true;
        mTaskQueue.terminate();

        if (mListCursor != null) {
            mListCursor.getBuilder().close();
            mListCursor.close();
        }

        if (DEBUG_SWITCHES.TRACKED_CURSOR && BuildConfig.DEBUG) {
            TrackedCursor.dumpCursors();
        }
        super.onDestroy();
    }

    /**
     * When a row is tapped.
     * <p>
     * Book: open the details screen.
     * Other: expand/collapse as appropriate.
     * <p>
     * * {@link BaseListActivity} enables 'this' as the listener for our ListView.
     */
    @Override
    public void onItemClick(@NonNull final AdapterView<?> parent,
                            @NonNull final View view,
                            final int position,
                            final long id) {
        mListCursor.moveToPosition(position);

        switch (mListCursor.getCursorRow().getRowKind()) {
            // If it's a book, view or edit it.
            case BooklistGroup.RowKind.BOOK:
                long bookId = mListCursor.getCursorRow().getBookId();
                boolean openInReadOnly = Prefs.getBoolean(R.string.pk_bob_open_book_read_only,
                                                          true);

                if (openInReadOnly) {
                    String listTable = mListCursor.getBuilder()
                                                  .createFlattenedBooklist()
                                                  .getTable()
                                                  .getName();

                    Intent intent = new Intent(BooksOnBookshelf.this,
                                               BookDetailsActivity.class);
                    intent.putExtra(UniqueId.KEY_ID, bookId);
                    intent.putExtra(BookFragment.REQUEST_BKEY_FLATTENED_BOOKLIST, listTable);
                    intent.putExtra(BookFragment.REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION,
                                    position);
                    startActivityForResult(intent, REQ_BOOK_VIEW);

                } else {
                    Intent intent = new Intent(BooksOnBookshelf.this,
                                               EditBookActivity.class);
                    intent.putExtra(UniqueId.KEY_ID, bookId);
                    intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                    startActivityForResult(intent, REQ_BOOK_EDIT);
                }
                break;

            default:
                // If it's a level, expand/collapse. Technically, we could expand/collapse any level
                // but storing and recovering the view becomes unmanageable.
                // ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/542
                if (mListCursor.getCursorRow().getLevel() == 1) {
                    mListCursor.getBuilder().toggleExpandNode(
                            mListCursor.getCursorRow().getAbsolutePosition());
                    mListCursor.requery();
                    mAdapter.notifyDataSetChanged();
                }

        }
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     * <p>
     * Called by {@link BaseListActivity#onCreate}
     */
    @Override
    public void initContextMenuOnListView() {
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(@NonNull final AdapterView<?> parent,
                                           @NonNull final View view,
                                           final int position,
                                           final long id) {
                mListCursor.moveToPosition(position);

                String menuTitle = mListCursor.getCursorRow().getTitle();

                // legal trick to get an instance of Menu.
                mListViewContextMenu = new PopupMenu(view.getContext(), null).getMenu();
                // custom menuInfo
                SimpleDialog.ContextMenuInfo menuInfo =
                        new SimpleDialog.ContextMenuInfo(menuTitle, position);
                // populate the menu
                mListHandler.prepareListViewContextMenu(mListViewContextMenu,
                                                        mListCursor.getCursorRow());
                // display
                onCreateListViewContextMenu(view, mListViewContextMenu, menuInfo);
                return true;
            }
        });
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public boolean onListViewContextItemSelected(@NonNull final MenuItem menuItem,
                                                 final int position) {
        mListCursor.moveToPosition(position);

        return mListHandler.onContextItemSelected(menuItem,
                                                  mDb, mListCursor.getCursorRow(),
                                                  BooksOnBookshelf.this);
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @return super.onCreateOptionsMenu(menu);
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

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

        if (BuildConfig.DEBUG) {
            menu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_PREFS, 0, R.string.lbl_preferences);
            menu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_STYLE, 0, R.string.lbl_style);
            menu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_TRACKER, 0, R.string.debug_history);
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                HintManager.displayHint(this.getLayoutInflater(),
                                        R.string.hint_booklist_style_menu, new Runnable() {
                            @Override
                            public void run() {
                                doSortMenu(false);
                            }
                        });
                return true;

            case R.id.MENU_EXPAND:
                // It is possible that the list will be empty, if so, ignore
                if (getListView().getChildCount() != 0) {
                    int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                    savePosition();
                    mListCursor.getBuilder().expandAll(true);
                    mTopRow = mListCursor.getBuilder().getPosition(oldAbsPos);
                    displayList(mListCursor.getBuilder().getList(), null);
                }
                return true;

            case R.id.MENU_COLLAPSE:
                // It is possible that the list will be empty, if so, ignore
                if (getListView().getChildCount() != 0) {
                    int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                    savePosition();
                    mListCursor.getBuilder().expandAll(false);
                    mTopRow = mListCursor.getBuilder().getPosition(oldAbsPos);
                    displayList(mListCursor.getBuilder().getList(), null);
                }
                return true;

            case R.id.MENU_CLEAR_FILTERS:
                mSearchCriteria.clear();
                initBookList(true);
                return true;

            default:
                if (BuildConfig.DEBUG) {
                    switch (item.getItemId()) {
                        case R.id.MENU_DEBUG_DUMP_PREFS:
                            Prefs.dumpPreferences(null);
                            return true;
                        case R.id.MENU_DEBUG_DUMP_STYLE:
                            Logger.info(this, mCurrentBookshelf.getStyle(mDb).toString());
                            return true;
                        case R.id.MENU_DEBUG_DUMP_TRACKER:
                            Logger.info(this, Tracker.getEventsInfo());
                            return true;
                    }
                }
                return MenuHandler.handleBookSubMenu(this, item)
                        || super.onOptionsItemSelected(
                        item);
        }
    }

    /**
     * Called when an activity launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     */
    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        mCurrentPositionedBookId = 0;

        switch (requestCode) {
            case REQ_BOOK_VIEW:
            case REQ_BOOK_EDIT: {
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                        // handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the ID 'above' the deleted one;
                        initBookList(false);
                        break;

                    case Activity.RESULT_OK:
                        Objects.requireNonNull(data);
                        long newId = data.getLongExtra(UniqueId.KEY_ID, 0);
                        if (newId != 0) {
                            mCurrentPositionedBookId = newId;
                        }
                        initBookList(false);
                        break;


                    default:
                        if (resultCode != Activity.RESULT_CANCELED) {
                            Logger.debug("unknown resultCode=" + resultCode);
                        }
                        break;
                }
                break;
            }
            case REQ_BOOK_SEARCH: {
                if (resultCode == Activity.RESULT_OK) {
                    /* don't enforce having an id. We might not have found or added anything.
                     * but if we do, the data will be what EditBookActivity returns. */
                    if (data != null) {
                        long newId = data.getLongExtra(UniqueId.KEY_ID, 0);
                        if (newId != 0) {
                            mCurrentPositionedBookId = newId;
                        }
                    }
                    // regardless, do a rebuild just in case
                    initBookList(false);
                }
                break;
            }
            case REQ_ADVANCED_LOCAL_SEARCH: {
                // no changes made, but we might have data to act on.
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Bundle extras = data.getExtras();
                        if (extras != null) {
                            mSearchCriteria.from(data.getExtras());
                        }
                        initBookList(true);
                    }
                }
                break;
            }
            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    // the last edited/inserted shelf
                    long bookshelfId = data.getLongExtra(UniqueId.KEY_ID, 0);
                    mCurrentBookshelf = mDb.getBookshelf(bookshelfId);

                    // bookshelves modified, update everything
                    initBookshelfSpinner();
                    populateBookShelfSpinner();
                    savePosition();
                    initBookList(true);
                }
                break;
            }

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_ADMIN: {
                if (resultCode == Activity.RESULT_OK) {
                    if ((data != null) && data.hasExtra(UniqueId.BKEY_IMPORT_RESULT_OPTIONS)) {
                        // BackupAndRestoreActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_IMPORT_RESULT_OPTIONS,
                                                       ImportSettings.NOTHING);

                        if ((options & ImportSettings.PREFERENCES) != 0) {
                            // the imported prefs could have a different preferred bookshelf.
                            Bookshelf newBookshelf = Bookshelf.getPreferred(mDb);
                            if (!mCurrentBookshelf.equals(newBookshelf)) {
                                // if it was.. then switch to it.
                                mCurrentBookshelf = newBookshelf;
                                // take care of both bookshelf & style and trigger a rebuild.
                                populateBookShelfSpinner();
                            }
                        }
                    } else if ((data != null)
                            && data.hasExtra(UniqueId.BKEY_EXPORT_RESULT_OPTIONS)) {
                        // BackupAndRestoreActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_EXPORT_RESULT_OPTIONS,
                                                       ExportSettings.NOTHING);
                    }

//                    if ((data != null) && data.hasExtra(UniqueId.ZZZZ)) {
//                        // AdminActivity has results of it's own,, but no action needed for them.
//                        // child-activities results:
//                        // SearchAdminActivity:
//                    }
                }
                break;
            }

            // from BaseActivity Nav Panel or from sort menu dialog
            // TODO: more complicated then it should be....
            case UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES: {
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                    case UniqueId.ACTIVITY_RESULT_OK_BooklistPreferredStyles:
                        // no data
                        savePosition();
                        initBookList(true);
                        break;

                    case UniqueId.ACTIVITY_RESULT_OK_BooklistStyleProperties:
                        Objects.requireNonNull(data);
                        BooklistStyle style =
                                data.getParcelableExtra(
                                        BooklistStyleSettingsFragment.REQUEST_BKEY_STYLE);
                        // can be null if a style was deleted.
                        if (style != null) {
                            // save the new bookshelf/style combination
                            mCurrentBookshelf.setAsPreferred();
                            mCurrentBookshelf.setStyle(mDb, style);
                        }
                        savePosition();
                        initBookList(true);
                        break;

                    default:
                        if (resultCode != Activity.RESULT_CANCELED) {
                            Logger.debug("unknown resultCode=" + resultCode);
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
     * @param newList    New cursor to use
     * @param targetRows if set, change the position to targetRow.
     */
    private void displayList(@NonNull final BooklistPseudoCursor newList,
                             @Nullable final ArrayList<BookRowInfo> targetRows) {

        // get a local copy.
        final BooklistStyle style = mCurrentBookshelf.getStyle(mDb);
        final int headersToShow = style.getShowHeaderInfo();


        populateBookCountField(headersToShow);

        long t0;
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            //noinspection UnusedAssignment
            t0 = System.currentTimeMillis();
        }

        // Save the old list so we can close it later, and set the new list locally
        BooklistPseudoCursor oldList = mListCursor;
        mListCursor = newList;

        // Get new handler and adapter since list may be radically different structure
        mListHandler = new BooksMultiTypeListHandler();
        mAdapter = new MultiTypeListCursorAdapter(this, mListCursor, mListHandler);

        // Get the ListView and set it up
        final ListView listView = getListView();
        listView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of ListView
        listView.setFastScrollEnabled(false);
        listView.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mListCursor.getCount();
        if (mTopRow >= count) {
            mTopRow = count - 1;
            listView.setSelection(mTopRow);
        } else {
            listView.setSelectionFromTop(mTopRow, mTopRowOffset);
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and we can tell
        // if it is already in the view.
        if (targetRows != null) {
            fixPositionWhenDrawn(listView, targetRows);
        }

        // setup the row holder
        final ListHeader listHeader = new ListHeader();
        for (int level = 1; level <= 2; level++) {
            listHeader.setVisible(level,
                                  mListCursor.numLevels() > level
                                          && style.showLevel(level));
        }
        listView.setTag(listHeader);

        // Update the header details
        if (count > 0 && (headersToShow
                & (BooklistStyle.SUMMARY_SHOW_LEVEL_1 ^ BooklistStyle.SUMMARY_SHOW_LEVEL_2)) != 0) {
            listHeader.updateListHeader(mTopRow);
        }

        // Define a scroller to update header detail when the top row changes
        listView.setOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScroll(@NonNull final AbsListView view,
                                         final int firstVisibleItem,
                                         final int visibleItemCount,
                                         final int totalItemCount) {
                        // TODO: why is BooklistPseudoCursor causing a scroll even when it's closed!
                        // Need to check isDead because BooklistPseudoCursor misbehaves when
                        // activity terminates and closes cursor
                        if (mLastTopRow != firstVisibleItem
                                && !mIsDead
                                && (headersToShow != 0)) {
                            ListHeader listHeader = (ListHeader) view.getTag();
                            //noinspection ConstantConditions
                            listHeader.updateListHeader(firstVisibleItem);
                        }
                    }

                    @Override
                    public void onScrollStateChanged(@NonNull final AbsListView view,
                                                     final int scrollState) {
                    }
                }
        );

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            //TODO: move this to the bookshelf spinner ? more space in there
            actionBar.setSubtitle(mCurrentBookshelf.getStyle(mDb).getDisplayName());
        }

        // Close old list
        if (oldList != null) {
            if (mListCursor.getBuilder() != oldList.getBuilder()) {
                oldList.getBuilder().close();
            }
            oldList.close();
        }
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info(this, " displayList: " + (System.currentTimeMillis() - t0));
        }
    }

    private void populateBookCountField(final int showHeaderFlags) {
        final TextView bookCounts = findViewById(R.id.bookshelf_count);
        if ((showHeaderFlags & BooklistStyle.SUMMARY_SHOW_COUNT) != 0) {
            if (mUniqueBooks != mTotalBooks) {
                bookCounts.setText(getString(R.string.brackets,
                                             getString(R.string.displaying_n_books_in_m_entries,
                                                       mUniqueBooks, mTotalBooks)));
            } else {
                bookCounts.setText(getString(R.string.brackets,
                                             getResources().getQuantityString(
                                                     R.plurals.displaying_n_books,
                                                     mUniqueBooks, mUniqueBooks)));
            }
            bookCounts.setVisibility(View.VISIBLE);
        } else {
            bookCounts.setVisibility(View.GONE);
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
        getListView().post(new Runnable() {
            @Override
            public void run() {
                // Find the actual extend of the current view and get centre.
                int first = listView.getFirstVisiblePosition();
                int last = listView.getLastVisiblePosition();
                int centre = (last + first) / 2;
                if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                    Logger.info(BooksOnBookshelf.class,
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

                if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                    Logger.info(BooksOnBookshelf.class, " Best listPosition @" + best.listPosition);
                }
                // Try to put at top if not already visible, or only partially visible
                if (first >= best.listPosition || last <= best.listPosition) {
                    if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                        Logger.info(BooksOnBookshelf.class, " Adjusting position");
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
                    getListView().post(new Runnable() {
                        @Override
                        public void run() {
                            listView.smoothScrollToPosition(newPos);
                        }
                    });

                    //int newTop = best.listPosition - (last-first)/2;
                    // if (BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                    //Logger.info(this, " New Top @" + newTop );
                    //}
                    //lv.setSelection(newTop);
                }
            }
        });
    }

    /**
     * Save current position information, including view nodes that are expanded.
     * <p>
     * ENHANCE: Handle positions a little better when books are deleted.
     * <p>
     * Deleting a book by 'n' authors from the last author in list results in the list decreasing
     * in length by, potentially, n*2 items. The current code will return to the old position
     * in the list after such an operation...which will be too far down.
     */
    private void savePosition() {
        if (mIsDead) {
            return;
        }

        // Save position in list
        if (mListHasBeenLoaded) {
            ListView lv = getListView();
            mTopRow = lv.getFirstVisiblePosition();
            View v = lv.getChildAt(0);
            mTopRowOffset = v == null ? 0 : v.getTop();

            Prefs.getPrefs().edit()
                 .putInt(PREF_BOB_TOP_ROW, mTopRow)
                 .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
                 .apply();
        }
    }

    /**
     * display or hide the search text field in the header.
     *
     * @return the search text as we might have 'cleaned' it
     */
    @NonNull
    private String initSearchField(@Nullable String searchText) {
        if (searchText == null || ".".equals(searchText)) {
            searchText = "";
        }

        final TextView searchTextView = findViewById(R.id.search_text);
        if (searchText.isEmpty()) {
            searchTextView.setVisibility(View.GONE);
        } else {
            searchTextView.setVisibility(View.VISIBLE);
            searchTextView.setText(getString(R.string.name_colon_value,
                                             getString(R.string.search_with_text),
                                             searchText));
        }

        return searchText;
    }

    /**
     * Show the hints used in this class.
     */
    private void initHints() {
        HintManager.displayHint(this.getLayoutInflater(),
                                R.string.hint_view_only_book_details, null);
        HintManager.displayHint(this.getLayoutInflater(),
                                R.string.hint_book_list, null);
        if (StartupActivity.showAmazonHint()) {
            HintManager.showAmazonHint(this.getLayoutInflater());
        }
    }

    /**
     * Setup the bookshelf spinner.
     * <p>
     * The spinner listener will set the style associated with the newly selected Bookshelf
     * and call {@link #initBookList}.
     */
    private void initBookshelfSpinner() {
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);
        mBookshelfAdapter = new ArrayAdapter<>(this, R.layout.bookshelf_spinner);
        mBookshelfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfAdapter);

        /*
         * This is fired whenever a bookshelf is selected.
         * Takes care of style and rebuilds the booklist
         */
        mBookshelfSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(@NonNull final AdapterView<?> parent,
                                       @NonNull final View view,
                                       final int position,
                                       final long id) {
                // Check to see if mBookshelfAdapter is null, which should only occur if
                // the activity is being torn down: see Issue #370.
                if (mBookshelfAdapter == null) {
                    Logger.info(BooksOnBookshelf.this,
                                "spinner mBookshelfAdapter was null");
                    return;
                }

                String bookshelf = mBookshelfAdapter.getItem(position);
                if (bookshelf != null && !bookshelf.equalsIgnoreCase(mCurrentBookshelf.getName())) {
                    // make the new shelf the current, and get it's preferred style
                    mCurrentBookshelf = mDb.getBookshelfByName(bookshelf);
                    if (mCurrentBookshelf == null) {
                        // shelf must have been deleted, switch to 'all book'
                        mCurrentBookshelf = Bookshelf.getAllBooksBookshelf(mDb);
                    }

                    mCurrentBookshelf.setAsPreferred();

                    initBookList(true);
                }
            }

            @Override
            public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                // Do Nothing
            }
        });
    }

    /**
     * Populate the BookShelf list in the Spinner and switch to the preferred bookshelf/style.
     */
    private void populateBookShelfSpinner() {
        mBookshelfAdapter.clear();
        // Add the default All Books bookshelf
        mBookshelfAdapter.add(getString(R.string.all_books));
        int currentPos = 0;
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.getId() == mCurrentBookshelf.getId()) {
                currentPos = position;
            }
            position++;
            mBookshelfAdapter.add(bookshelf.getName());
        }
        // Set the current bookshelf. We use this to force the correct bookshelf after
        // the state has been restored.
        mBookshelfSpinner.setSelection(currentPos);
    }

    /**
     * Called if changes were made to a single book. *Try* not to do a full rebuild.
     *
     * @param extraFieldsInUse a bitmask with the Extra fields being used (visible) in the current
     *                         {@link BooklistStyle} as configured by the user
     * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
     * @param rowPosition      the absolute position of the row in the cursor view
     * @param bookId           the book that was changed
     */
    @Override
    public void onBookChange(final int extraFieldsInUse,
                             final int fieldsChanged,
                             final int rowPosition,
                             final long bookId) {

        if ((fieldsChanged & BooklistChangeListener.FLAG_BOOK_READ) != 0) {
            //ENHANCE: avoid the rebuild altogether ? but the 'false' rebuild is broken anyhow.
            savePosition();
            initBookList(false);
            return;
        }

        //Something changed and we have no special handling for the row
        onBooklistChange(extraFieldsInUse, fieldsChanged);
    }

    /**
     * FIXME: check  {@link BooklistChangeListener} flags and try avoid full rebuild?
     * but the 'false' rebuild is broken anyhow.
     * <p>
     * Called if global changes were made that (potentially) affect the whole list.
     *
     * @param extraFieldsInUse a bitmask with the Extra fields being used (visible) in the current
     *                         {@link BooklistStyle} as configured by the user
     * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
     */
    @Override
    public void onBooklistChange(final int extraFieldsInUse,
                                 final int fieldsChanged) {
        //Something changed. Just regenerate the list.
        if (fieldsChanged != 0) {
            savePosition();
            initBookList(true);
        }
    }

    /**
     * Setup the sort options. This function will also call fillData when
     * complete having loaded the appropriate view.
     *
     * @param showAll if <tt>true</tt> show all styles, otherwise only the preferred ones.
     */
    private void doSortMenu(final boolean showAll) {
        LayoutInflater inflater = this.getLayoutInflater();
        @SuppressLint("InflateParams")
        View dialogView = inflater.inflate(R.layout.booklist_style_menu, null);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle(R.string.title_select_style)
                .create();
        dialog.show();

        // first section is a list of the styles
        ViewGroup group = dialogView.findViewById(R.id.radio_buttons);

        for (BooklistStyle style : BooklistStyles.getStyles(mDb, showAll).values()) {
            addStyleButtonMenuItem(dialog, inflater, group, style);
        }

        // second section are options to do 'more things then listed'
        ViewGroup menu = dialogView.findViewById(R.id.menu);
        int moreOrLess = showAll ? R.string.menu_show_fewer_ellipsis
                                 : R.string.menu_show_more_ellipsis;
        addStyleTextMenuItem(menu, inflater, moreOrLess, new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
                doSortMenu(!showAll);
            }
        });
        addStyleTextMenuItem(menu, inflater, R.string.menu_customize_ellipsis,
                             new OnClickListener() {
                                 @Override
                                 public void onClick(@NonNull final View v) {
                                     dialog.dismiss();
                                     Intent intent = new Intent(BooksOnBookshelf.this,
                                                                PreferredStylesActivity.class);
                                     startActivityForResult(intent,
                                                            UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES);
                                 }
                             });
    }

    /**
     * Add a radio box to the sort options dialogue.
     */
    private void addStyleButtonMenuItem(@NonNull final AlertDialog dialog,
                                        @NonNull final LayoutInflater inf,
                                        @NonNull final ViewGroup parent,
                                        @NonNull final BooklistStyle style) {
        CompoundButton btn = (CompoundButton) inf.inflate(R.layout.booklist_style_menu_radio,
                                                          dialog.getListView());
        btn.setText(style.getDisplayName());
        btn.setChecked(mCurrentBookshelf.getStyle(mDb).getId() == style.getId());

        parent.addView(btn);

        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                onStyleSelected(style);
                dialog.dismiss();
            }
        });
    }

    /**
     * Add a text box to the sort options dialog.
     */
    private void addStyleTextMenuItem(@NonNull final ViewGroup parent,
                                      @NonNull final LayoutInflater inf,
                                      @StringRes final int stringId,
                                      @NonNull final OnClickListener listener) {
        @SuppressLint("InflateParams")
        TextView textView = (TextView) inf.inflate(R.layout.booklist_style_menu_text, null);

        Typeface tf = textView.getTypeface();
        textView.setTypeface(tf, Typeface.ITALIC);
        textView.setText(stringId);
        textView.setOnClickListener(listener);
        parent.addView(textView);
    }

    /**
     * Handle the style that a user has selected.
     *
     * @param style that was selected
     */
    private void onStyleSelected(@NonNull final BooklistStyle style) {

        // save the new bookshelf/style combination
        mCurrentBookshelf.setAsPreferred();
        mCurrentBookshelf.setStyle(mDb, style);

        /* Set the rebuild state like this is the first time in, which it sort of is, given we
         * are changing style. There is very little ability to preserve position when going from
         * a list sorted by author/series to on sorted by unread/addedDate/publisher.
         * Keeping the current row/pos is probably the most useful thing we can do since we *may*
         * come back to a similar list.
         */
        try {
            ListView listView = getListView();
            mTopRow = listView.getFirstVisiblePosition();
            View view = listView.getChildAt(0);
            mTopRowOffset = (view != null) ? view.getTop() : 0;
        } catch (RuntimeException ignored) {
        }

        // New style, so use user-pref for rebuild
        mRebuildState = BooklistBuilder.getListRebuildState();

        // Do a rebuild
        initBookList(true);
    }

    /**
     * Create a BooklistBuilder with the basic setup.
     *
     * @return the builder.
     */
    @NonNull
    private BooklistBuilder getBooklistBuilder() {
        if (DEBUG_SWITCHES.DUMP_STYLE && BuildConfig.DEBUG) {
            Logger.info(this, "getBooklistBuilder|" + mCurrentBookshelf.getStyle(mDb).toString());
        }

        // get a new builder and add the required extra domains
        BooklistBuilder builder = new BooklistBuilder(this, mCurrentBookshelf.getStyle(mDb));

        builder.requireDomain(DatabaseDefinitions.DOM_TITLE,
                              DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_TITLE),
                              true);

        builder.requireDomain(DatabaseDefinitions.DOM_BOOK_READ,
                              DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_BOOK_READ),
                              false);

        return builder;
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    private void initBookList(final boolean isFullRebuild) {

        //FIXME: this is one from the original code. isFullRebuild=false is BROKEN.
        // basically all group headers are no longer in the TBL_BOOK_LIST.
        // See DatabaseDefinitions#TBL_BOOK_LIST for an example of the correct table content
        // After rebuild(false) all rows which don't show an expanded node are gone.
        //
        boolean rebuild;
        if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD) {
            rebuild = isFullRebuild;
        } else {
            rebuild = true;
        }

        mTaskQueue.enqueue(new GetBookListTask(rebuild, mCurrentBookshelf.getId()));

        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog.show(this,
                                                  "",
                                                  getString(R.string.progress_msg_getting_books),
                                                  true,
                                                  true, new OnCancelListener() {
                        @Override
                        public void onCancel(@NonNull final DialogInterface dialog) {
                            // Cancelling the list cancels the activity.
                            BooksOnBookshelf.this.finish();
                            dialog.dismiss();
                            mProgressDialog = null;
                        }
                    });
        }
    }

    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        // this will likely not happen while we're actively listening.
        if (Bookshelf.PREF_BOOKSHELF_CURRENT.equals(key)) {
            Bookshelf newBookshelf = Bookshelf.getPreferred(mDb);
            if (!mCurrentBookshelf.equals(newBookshelf)) {
                populateBookShelfSpinner();
            }
            return;
        }
        super.onSharedPreferenceChanged(sharedPreferences, key);
    }

    /**
     * older class for search criteria with some methods to mass manipulate them.
     * <p>
     * All individual criteria are supported by the {@link BooklistBuilder},
     * but not necessarily in {@link BooksOnBookshelf}.
     * <p>
     * Some are supported by {@link #onSearchRequested()}.
     */
    private class SearchCriteria {

        /**
         * Text to use in search query.
         */
        @Nullable
        String text = "";
        /**
         * Author to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String author = "";
        /**
         * Title to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String title = "";
        /**
         * Series to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String series = "";
        /**
         * Name of the person we loaned books to, to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String loanee = "";

        /**
         * List of bookId's to display. The result of a search.
         */
        @Nullable
        ArrayList<Integer> bookList;

        void clear() {
            text = "";
            author = "";
            title = "";
            series = "";
            loanee = "";
            bookList = null;
        }

        void from(@NonNull final Bundle bundle) {
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                text = initSearchField(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                author = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            }
            if (bundle.containsKey(UniqueId.KEY_TITLE)) {
                title = bundle.getString(UniqueId.KEY_TITLE);
            }
            if (bundle.containsKey(UniqueId.KEY_SERIES)) {
                series = bundle.getString(UniqueId.KEY_SERIES);
            }
            if (bundle.containsKey(UniqueId.KEY_BOOK_LOANEE)) {
                loanee = bundle.getString(UniqueId.KEY_BOOK_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                bookList = bundle.getIntegerArrayList(UniqueId.BKEY_ID_LIST);
            }
        }

        /**
         * @param intent which will be used for a {@link #startActivityForResult}
         */
        void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, text);
            intent.putExtra(UniqueId.BKEY_SEARCH_AUTHOR, author);
            intent.putExtra(UniqueId.KEY_TITLE, title);
            intent.putExtra(UniqueId.KEY_SERIES, series);
            intent.putExtra(UniqueId.KEY_BOOK_LOANEE, loanee);
            intent.putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        /**
         * @param outState from {@link #onSaveInstanceState}
         */
        void to(final Bundle outState) {
            outState.putString(UniqueId.BKEY_SEARCH_TEXT, text);
            outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, author);
            outState.putString(UniqueId.KEY_TITLE, title);
            outState.putString(UniqueId.KEY_SERIES, series);
            outState.putString(UniqueId.KEY_BOOK_LOANEE, loanee);
            outState.putIntegerArrayList(UniqueId.BKEY_ID_LIST, bookList);
        }

        boolean isEmpty() {
            return (text == null || text.isEmpty())
                    && (author == null || author.isEmpty())
                    && (title == null || title.isEmpty())
                    && (series == null || series.isEmpty())
                    && (loanee == null || loanee.isEmpty())
                    && (bookList == null || bookList.isEmpty())
                    ;
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private class GetBookListTask
            implements SimpleTaskQueue.SimpleTask {

        /**
         * Indicates whole table structure needs rebuild,
         * versus just do a reselect of underlying data.
         */
        private final boolean isFullRebuild;
        /** the builder. */
        @NonNull
        private final BooklistBuilder bookListBuilder;
        /** Resulting Cursor. */
        private BooklistPseudoCursor tempList;
        /** used to determine new cursor position. */
        @Nullable
        private ArrayList<BookRowInfo> targetRows;

        /**
         * Constructor.
         *
         * @param isFullRebuild      Indicates whole table structure needs rebuild,
         *                           versus just do a reselect of underlying data
         * @param currentBookshelfId strictly return books on this bookshelf
         */
        GetBookListTask(final boolean isFullRebuild,
                        final long currentBookshelfId) {

            if (!isFullRebuild && BuildConfig.DEBUG) {
                Logger.info(this, " constructor, isFullRebuild=false");
            }
            this.isFullRebuild = isFullRebuild;

            // If not a full rebuild then just use the current builder to re-query
            // the underlying data
            //noinspection ConstantConditions
            if (mListCursor != null && !this.isFullRebuild) {
                bookListBuilder = mListCursor.getBuilder();
            } else {
                bookListBuilder = getBooklistBuilder();
                // Build based on our current criteria
                bookListBuilder.setFilterOnText(mSearchCriteria.text);
                bookListBuilder.setFilterOnTitle(mSearchCriteria.title);
                bookListBuilder.setFilterOnAuthorName(mSearchCriteria.author);
                bookListBuilder.setFilterOnSeriesName(mSearchCriteria.series);
                bookListBuilder.setFilterOnLoanedToPerson(mSearchCriteria.loanee);
                bookListBuilder.setFilterOnBookIdList(mSearchCriteria.bookList);

                bookListBuilder.setFilterOnBookshelfId(currentBookshelfId);
            }
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            try {
                long t0;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t0 = System.currentTimeMillis();
                }
                // Build the underlying data
                if (mListCursor != null && !this.isFullRebuild) {
                    bookListBuilder.rebuild();
                } else {
                    bookListBuilder.build(mRebuildState, mCurrentPositionedBookId);
                    // After first build, always preserve this object state
                    mRebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
                }

                long t1;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t1 = System.currentTimeMillis();
                }
                // Try to sync the previously selected book ID
                if (mCurrentPositionedBookId != 0) {
                    // get all positions of the book
                    targetRows = bookListBuilder.getBookAbsolutePositions(mCurrentPositionedBookId);

                    if (targetRows != null && targetRows.size() > 0) {
                        // First, get the ones that are currently visible...
                        ArrayList<BookRowInfo> visRows = new ArrayList<>();
                        for (BookRowInfo i : targetRows) {
                            if (i.visible) {
                                visRows.add(i);
                            }
                        }
                        // If we have any visible rows, only consider them for the new position
                        if (visRows.size() > 0) {
                            targetRows = visRows;
                        } else {
                            // Make them ALL visible
                            for (BookRowInfo rowInfo : targetRows) {
                                if (!rowInfo.visible) {
                                    bookListBuilder.ensureAbsolutePositionVisible(
                                            rowInfo.absolutePosition);
                                }
                            }
                            // Recalculate all positions
                            for (BookRowInfo rowInfo : targetRows) {
                                rowInfo.listPosition = bookListBuilder.getPosition(
                                        rowInfo.absolutePosition);
                            }
                        }
                        // Find the nearest row to the recorded 'top' row.
//                        int targetRow = bookRows[0];
//                        int minDist = Math.abs(mTopRow - b.getPosition(targetRow));
//                        for (int i = 1; i < bookRows.length; i++) {
//                            int pos = b.getPosition(bookRows[i]);
//                            int dist = Math.abs(mTopRow - pos);
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

                long t2;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t2 = System.currentTimeMillis();
                }
                // Now we have expanded groups as needed, get the list cursor
                tempList = bookListBuilder.getList();
                // Clear it so it won't be reused.
                mCurrentPositionedBookId = 0;

                long t3;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t3 = System.currentTimeMillis();
                }
                // get a count() from the cursor in background task because the setAdapter() call
                // will do a count() and potentially block the UI thread while it pages through the
                // entire cursor. If we do it here, subsequent calls will be fast.
                @SuppressWarnings("UnusedAssignment")
                int count = tempList.getCount();

                long t4;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t4 = System.currentTimeMillis();
                }
                mUniqueBooks = tempList.getUniqueBookCount();

                long t5;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t5 = System.currentTimeMillis();
                }
                mTotalBooks = tempList.getBookCount();

                long t6;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t6 = System.currentTimeMillis();
                }

                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info(this, " Build: " + (t1 - t0));
                    Logger.info(this, " Position: " + (t2 - t1));
                    Logger.info(this, " Select: " + (t3 - t2));
                    Logger.info(this,
                                " Count(" + count + "): " + (t4 - t3)
                                        + '/' + (t5 - t4) + '/' + (t6 - t5));
                    Logger.info(this, " ====== ");
                    Logger.info(this, " Total: " + (t6 - t0));
                }
                // Save a flag to say list was loaded at least once successfully
                mListHasBeenLoaded = true;

            } finally {
                if (taskContext.isTerminating()) {
                    // onFinish() will not be called, and we can discard our work...
                    if (tempList != null && tempList != mListCursor) {
                        if (mListCursor == null
                                || (tempList.getBuilder() != mListCursor.getBuilder())) {
                            tempList.getBuilder().close();
                        }
                        tempList.close();

                    }
                }
            }
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            // If activity dead, just do a local cleanup and exit.
            if (mIsDead) {
                tempList.close();
                return;
            }
            // Dismiss the progress dialog, if present
            if (mProgressDialog != null && !mTaskQueue.hasActiveTasks()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            // Update the data
            if (tempList != null) {
                displayList(tempList, targetRows);
            }
            tempList = null;
        }

    }

    /**
     * Hold the current ListView header details.
     * <p>
     * the API of this class accepts level in the range of 1..2
     */
    private class ListHeader {

        private static final int MAX = 2;

        private final TextView[] levelTextView = new TextView[MAX];

        private final boolean[] isVisible = new boolean[MAX];

        ListHeader() {
            levelTextView[0] = findViewById(R.id.level_1_text);
            levelTextView[1] = findViewById(R.id.level_2_text);
        }

        /**
         * Set the visibility of the passed level.
         *
         * @param level     to use; range is 1..
         * @param isVisible set to <tt>true</tt> to make this level visible.
         */
        void setVisible(@IntRange(from = 1, to = MAX) final int level,
                        final boolean isVisible) {

            int index = level - 1;

            this.isVisible[index] = isVisible;
            if (isVisible) {
                levelTextView[index].setVisibility(View.VISIBLE);
                levelTextView[index].setText("");
            } else {
                levelTextView[index].setVisibility(View.GONE);
            }
        }

        /**
         * Update the list header to match the current top item.
         *
         * @param firstVisibleItem Top row which is visible
         */
        void updateListHeader(@IntRange(from = 0) final int firstVisibleItem) {

            mLastTopRow = firstVisibleItem >= 0 ? firstVisibleItem : 0;

            if (isVisible[0]) {
                if (mListCursor.moveToPosition(mLastTopRow)) {
                    levelTextView[0].setText(mListCursor.getCursorRow().getLevelText(1));

                    if (isVisible[1]) {
                        levelTextView[1].setText(mListCursor.getCursorRow().getLevelText(2));
                    }
                }
            }
        }
    }
}
