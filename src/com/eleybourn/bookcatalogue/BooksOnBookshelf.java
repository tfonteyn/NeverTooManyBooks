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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
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

import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler.BooklistChangeListener;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferredStylesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStylePropertiesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.RowKinds;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.searches.SearchLocalActivity;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf extends BaseListActivity implements
        BooklistChangeListener {
    /** Is book info opened in read-only mode. (old name for backwards compatibility) */
    public static final String PREF_OPEN_BOOK_READ_ONLY = "App.OpenBookReadOnly";

    /** Prefix used in preferences for this activity */
    private final static String TAG = "BooksOnBookshelf";

    /** Preference name for the bookshelf to load next time we startup */
    public final static String PREF_BOOKSHELF = TAG + ".BOOKSHELF";
    /** Preference name for the default style to use */
    private final static String PREF_LIST_STYLE = TAG + ".LIST_STYLE";
    @StringRes
    private final static int DEFAULT_STYLE = R.string.style_builtin_author_series;
    /** Preference name for the style to use specific to a bookshelf */
    private final static String PREF_LIST_STYLE_FOR_BOOKSHELF = TAG + ".LIST_STYLE.BOOKSHELF." /* + name of shelf */;

    /** Preference name */
    private final static String PREF_TOP_ROW = TAG + ".TOP_ROW";
    /** Preference name */
    private final static String PREF_TOP_ROW_TOP = TAG + ".TOP_ROW_TOP";

    /** Task queue to get book lists in background */
    private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("BoB-GetBookListTask", 1);


    /** Options indicating activity has been destroyed. Used for background tasks */
    private boolean mIsDead = false;
    /** Options to indicate that a list has been successfully loaded -- affects the way we save state */
    private boolean mListHasBeenLoaded = false;

    /** ProgressDialog used to display "Getting books...". Needed here so we can dismiss it on close. */
    @Nullable
    private ProgressDialog mListDialog = null;

    /** A book ID used for keeping/updating current list position, eg. when a book is viewed/edited. */
    private long mCurrentPositionedBookId = 0;
    /** Currently selected list style */
    private BooklistStyle mCurrentStyle = null;
    /** Currently selected bookshelf */
    private Bookshelf mCurrentBookshelf;

    /** Text to use in search query */
    @Nullable
    private String mSearchText = "";
    @Nullable
    private String mAuthorSearchText = "";
    @Nullable
    private String mTitleSearchText = "";

    /** List of bookId's to display. The result of a search. EXPERIMENTAL */
    @Nullable
    private List<Integer> mSearchBookIdList = null;

    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTop = -1;
    /** Saved position of last top row */
    private int mTopRow = 0;
    /** Saved position of last top row offset from view top */
    private int mTopRowTop = 0;

    /** Database connection */
    private CatalogueDBAdapter mDb;
    /** Handler to manage all Views on the list */
    private BooksMultiTypeListHandler mListHandler;
    /** Current displayed list cursor */
    private BooklistPseudoCursor mListCursor;
    /** Multi-type adapter to manage list connection to cursor */
    private MultiTypeListCursorAdapter mAdapter;
    /** Preferred booklist state in next rebuild */
    private int mRebuildState;
    /** Total number of books in current list */
    private int mTotalBooks = 0;
    /** Total number of unique books in current list */
    private int mUniqueBooks = 0;

    private Spinner mBookshelfSpinner;
    private ArrayAdapter<String> mBookshelfAdapter;

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        if (savedInstanceState == null) {
            // Get preferred booklist state to use from preferences;
            // default to always expanded (MUCH faster than 'preserve' with lots of books)
            mRebuildState = BooklistPreferencesActivity.getRebuildState();
        } else {
            // Always preserve state when rebuilding/recreating etc
            mRebuildState = BooklistPreferencesActivity.BOOK_LIST_STATE_PRESERVED;
        }

        mDb = new CatalogueDBAdapter(this);

        // Restore bookshelf
        String bookshelf_name = BookCatalogueApp.getStringPreference(PREF_BOOKSHELF, null);
        if (bookshelf_name == null || bookshelf_name.isEmpty()) {
            // pref not set, start with initial shelf
            mCurrentBookshelf = new Bookshelf(Bookshelf.DEFAULT_ID, getString(R.string.initial_bookshelf));
        } else {
            // try to get the id of the preferred shelf
            mCurrentBookshelf = mDb.getBookshelfByName(bookshelf_name);
            if (mCurrentBookshelf == null) {
                // shelf must have been deleted, switch to 'all book'
                mCurrentBookshelf = new Bookshelf(Bookshelf.ALL_BOOKS, getString(R.string.all_books));
            }
        }

        // Restore list position on bookshelf
        mTopRow = BookCatalogueApp.getIntPreference(PREF_TOP_ROW, 0);
        mTopRowTop = BookCatalogueApp.getIntPreference(PREF_TOP_ROW_TOP, 0);

        // Restore view style
        refreshStyle();

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

        mSearchText = initSearchField(searchText);

        // use the custom fast scroller (the ListView in the XML is our custom version).
        getListView().setFastScrollEnabled(true);

//            FloatingActionButton floatingAddButton = findViewById(R.id.floatingAddButton);
//            floatingAddButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    Snackbar.make(view, "Here's a Snackbar", Snackbar.LENGTH_LONG)
//                            .setAction("Action", null).show();
//                }
//            });
//            floatingAddButton.show();

        // Handle item click events

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;

        initBookshelfSpinner();
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
        Intent intent = new Intent(this, SearchLocalActivity.class);
        intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, mSearchText);
        intent.putExtra(UniqueId.BKEY_SEARCH_AUTHOR, mAuthorSearchText);
        intent.putExtra(UniqueId.KEY_TITLE, mTitleSearchText);
        startActivityForResult(intent, SearchLocalActivity.REQUEST_CODE); /* 6f6e83e1-10fb-445c-8e35-fede41eba03b */
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
     * Save position when paused
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        if ((mSearchText == null || mSearchText.isEmpty())
                && (mAuthorSearchText == null || mAuthorSearchText.isEmpty())
                && (mTitleSearchText == null || mTitleSearchText.isEmpty())) {
            savePosition();
        }

        if (isFinishing()) {
            mTaskQueue.terminate();
        }

        if (mListDialog != null) {
            mListDialog.dismiss();
        }

        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);

        mIsDead = true;
        mTaskQueue.terminate();

        try {
            if (mListCursor != null) {
                try {
                    mListCursor.getBuilder().close();
                } catch (Exception e) {
                    Logger.error(e);
                }
                mListCursor.close();
            }
        } catch (Exception e) {
            Logger.error(e);
        }

        if (mDb != null) {
            mDb.close();
        }
        if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
            TrackedCursor.dumpCursors();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    /**
     * * {@link BaseListActivity} enables 'this' as the listener for our ListView
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        mListCursor.moveToPosition(position);

        switch (mListCursor.getCursorRow().getRowKind()) {
            // If it's a book, view or edit it.
            case RowKinds.ROW_KIND_BOOK: {
                long bookId = mListCursor.getCursorRow().getBookId();
                boolean openInReadOnly = BookCatalogueApp.getBooleanPreference(PREF_OPEN_BOOK_READ_ONLY, true);

                if (openInReadOnly) {
                    String listTable = mListCursor.getBuilder().createFlattenedBooklist().getTable().getName();
                    Intent intent = new Intent(BooksOnBookshelf.this, BookDetailsActivity.class);
                    intent.putExtra(UniqueId.KEY_ID, bookId);
                    intent.putExtra(BookFragment.REQUEST_BKEY_FLATTENED_BOOKLIST, listTable);
                    intent.putExtra(BookFragment.REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION, position);
                    startActivityForResult(intent, BookDetailsActivity.REQUEST_CODE); /* e63944b6-b63a-42b1-897a-a0e8e0dabf8a */

                } else {
                    EditBookActivity.startActivityForResult(BooksOnBookshelf.this, bookId, EditBookFragment.TAB_EDIT); /* 91b95a7f-17d6-4f98-af58-5f040b52414f */
                }
                break;
            }
            default: {
                // If it's level, expand/collapse. Technically, TODO: we could expand/collapse any level
                // but storing and recovering the view becomes unmanageable.
                if (mListCursor.getCursorRow().getLevel() == 1) {
                    mListCursor.getBuilder().toggleExpandNode(mListCursor.getCursorRow().getAbsolutePosition());
                    mListCursor.requery();
                    mAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     *
     * Called by {@link BaseListActivity#onCreate}
     */
    @Override
    public void initListViewContextMenuListener(final @NonNull Context context) {
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                mListCursor.moveToPosition(position);

                String menuTitle = mListCursor.getCursorRow().getTitle();

                // legal trick to get an instance of Menu.
                mListViewContextMenu = new PopupMenu(context, null).getMenu();
                // custom menuInfo
                SelectOneDialog.SimpleDialogMenuInfo menuInfo = new SelectOneDialog.SimpleDialogMenuInfo(menuTitle, view, position);
                // populate the menu
                mListHandler.prepareListViewContextMenu(mListViewContextMenu, mListCursor.getCursorRow());
                // display
                onCreateListViewContextMenu(mListViewContextMenu, view, menuInfo);
                return true;
            }
        });
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public boolean onListViewContextItemSelected(final @NonNull MenuItem menuItem,
                                                 final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo) {
        mListCursor.moveToPosition(menuInfo.position);

        return mListHandler.onContextItemSelected(menuItem,
                mDb, mListCursor.getCursorRow(), BooksOnBookshelf.this);
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
    public boolean onCreateOptionsMenu(final @NonNull Menu menu) {

        MenuHandler.addCreateBookSubMenu(menu);

        menu.add(Menu.NONE, R.id.MENU_SORT, 0, R.string.menu_sort_and_style_ellipsis)
                .setIcon(R.drawable.ic_sort_by_alpha)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_EXPAND, 0, R.string.menu_expand_all)
                .setIcon(R.drawable.ic_unfold_more);

        menu.add(Menu.NONE, R.id.MENU_COLLAPSE, 0, R.string.menu_collapse_all)
                .setIcon(R.drawable.ic_unfold_less);

        return super.onCreateOptionsMenu(menu);
    }


    /**
     * This will be called when a menu item is selected. A large switch
     * statement to call the appropriate functions (or other activities)
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                HintManager.displayHint(this.getLayoutInflater(), R.string.hint_booklist_style_menu, new Runnable() {
                    @Override
                    public void run() {
                        doSortMenu(false);
                    }
                });
                return true;

            case R.id.MENU_EXPAND: {
                // It is possible that the list will be empty, if so, ignore
                if (getListView().getChildCount() != 0) {
                    int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                    savePosition();
                    mListCursor.getBuilder().expandAll(true);
                    mTopRow = mListCursor.getBuilder().getPosition(oldAbsPos);
                    displayList(mListCursor.getBuilder().getList(), null);
                }
                return true;
            }
            case R.id.MENU_COLLAPSE: {
                // It is possible that the list will be empty, if so, ignore
                if (getListView().getChildCount() != 0) {
                    int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                    savePosition();
                    mListCursor.getBuilder().expandAll(false);
                    mTopRow = mListCursor.getBuilder().getPosition(oldAbsPos);
                    displayList(mListCursor.getBuilder().getList(), null);
                }
                return true;
            }
        }

        return MenuHandler.handleBookSubMenu(this, item) || super.onOptionsItemSelected(item);
    }

    /**
     * Called when an activity launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     */
    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        mCurrentPositionedBookId = 0;

        switch (requestCode) {
            case BookDetailsActivity.REQUEST_CODE: /* e63944b6-b63a-42b1-897a-a0e8e0dabf8a */
            case EditBookActivity.REQUEST_CODE: { /* 88a6c414-2d3b-4637-9044-b7291b6b9100,
             91b95a7f-17d6-4f98-af58-5f040b52414f, 01564e26-b463-425e-8889-55a8228c82d5,
              8a5c649a-e97b-4d53-8133-6060ef3c3072, 0308715c-e1d2-4a7f-9ba3-cb8f641e096b */
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_BOOK_DELETED: {
                        // handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the ID 'above' the deleted one;
                        initBookList(false);
                        break;
                    }
                    case Activity.RESULT_OK: {
                        /* there *has* to be 'data' */
                        Objects.requireNonNull(data);
                        long newId = data.getLongExtra(UniqueId.KEY_ID, 0);
                        if (newId != 0) {
                            mCurrentPositionedBookId = newId;
                        }
                        initBookList(false);
                        break;
                    }
                }
            }
            case BookSearchActivity.REQUEST_CODE: /* 59fd9653-f033-40b5-bee8-f1dfa5b5be6b, f1e0d846-852e-451b-9077-6daa5d94f37d */
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

            case SearchLocalActivity.REQUEST_CODE: { /* 6f6e83e1-10fb-445c-8e35-fede41eba03b */
                // no changes made, but we have data to act on.
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    mSearchText = initSearchField(data.getStringExtra(UniqueId.BKEY_SEARCH_TEXT));
                    mAuthorSearchText = initSearchField(data.getStringExtra(UniqueId.BKEY_SEARCH_AUTHOR));
                    mTitleSearchText = initSearchField(data.getStringExtra(UniqueId.KEY_TITLE));
                    mSearchBookIdList = data.getIntegerArrayListExtra(UniqueId.BKEY_BOOK_ID_LIST);
                    initBookList(true);
                }
                break;
            }
            // from BaseActivity Nav Panel
            case EditBookshelfListActivity.REQUEST_CODE: { /* 41e84172-5833-4906-a891-8df302ecc190 */
                if (resultCode == Activity.RESULT_OK) {
                    // bookshelves modified, update everything
                    initBookshelfSpinner();
                    populateBookShelfSpinner();
                    savePosition();
                    initBookList(true);
                }
                break;
            }
            // from BaseActivity Nav Panel
            case AdminActivity.REQUEST_CODE: { /* 7f46620d-7951-4637-8783-b410730cd460 */
                // no results of it's own,
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_PREFS_MIGHT_HAVE_CHANGED: {
                        // child-activities results:
//                        PreferencesBaseActivity: /* 9cdb2cbe-1390-4ed8-a491-87b3b1a1edb9, 46f41e7b-f49c-465d-bea0-80ec85330d1c */
//                        FieldVisibilityActivity: /* 2f885b11-27f2-40d7-8c8b-fcb4d95a4151 */
                        refreshStyle();
                        savePosition();
                        initBookList(true);
                    }
                    break;
                }
            }

            // from BaseActivity Nav Panel or from sort menu dialog
            case BooklistPreferredStylesActivity.REQUEST_CODE: { /* 13854efe-e8fd-447a-a195-47678c0d87e7 */
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_CHANGES_MADE_BOOKLIST_STYLES: {
                        refreshStyle();
                        savePosition();
                        initBookList(true);
                        break;
                    }
                    case UniqueId.ACTIVITY_RESULT_CHANGES_MADE_BOOKLIST_STYLE_PROPERTIES: {
                        // we currently don't let the result from this one trickle up. Leaving in place as this might change.
                        handleStyleChange(data);
                        break;
                    }
                }
                break;
            }
            case BooklistStylePropertiesActivity.REQUEST_CODE: {
                // we currently don't start the BooklistStylePropertiesActivity from here. Leaving in place as this might change.
                if (resultCode == UniqueId.ACTIVITY_RESULT_CHANGES_MADE_BOOKLIST_STYLE_PROPERTIES) {
                    handleStyleChange(data);
                }
                break;
            }

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    /** Future enhancement maybe. See {@link #onActivityResult} */
    private void handleStyleChange(final @Nullable Intent data) {
        Logger.error("onActivityResult: BooklistStylePropertiesActivity.RESULT_CHANGES_MADE was supposed to be unused?");
        /* there *has* to be 'data' */
        Objects.requireNonNull(data);
        BooklistStyle style = data.getParcelableExtra(BooklistStylePropertiesActivity.REQUEST_BKEY_STYLE);
        // can be null if a style was deleted.
        if (style != null) {
            mCurrentStyle = style;
            saveBookShelfAndStyle(mCurrentBookshelf, mCurrentStyle);
        }
        savePosition();
        initBookList(true);
    }

    /**
     * Display the passed cursor in the ListView
     *
     * @param newList    New cursor to use
     * @param targetRows if set, change the position to targetRow.
     */
    private void displayList(final @NonNull BooklistPseudoCursor newList,
                             final @Nullable ArrayList<BookRowInfo> targetRows) {

        final int showHeaderFlags = (mCurrentStyle == null ? BooklistStyle.SUMMARY_SHOW_ALL : mCurrentStyle.getShowHeaderInfo());

        populateBookCountField(showHeaderFlags);

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
        final ListViewHolder listViewHolder = new ListViewHolder();
        ViewTagger.setTag(listView, R.id.TAG_HOLDER, listViewHolder);// value: BooksOnBookshelf.ListViewHolder

        listView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of ListView
        listView.setFastScrollEnabled(false);
        listView.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mListCursor.getCount();
        try {
            if (mTopRow >= count) {
                mTopRow = count - 1;
                listView.setSelection(mTopRow);
            } else {
                listView.setSelectionFromTop(mTopRow, mTopRowTop);
            }
        } catch (Exception ignored) {
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and we can tell
        // if it is already in the view.
        if (targetRows != null) {
            fixPositionWhenDrawn(listView, targetRows);
        }

        final boolean hasLevel1 = (mListCursor.numLevels() > 1);
        final boolean hasLevel2 = (mListCursor.numLevels() > 2);

        if (hasLevel2 && (showHeaderFlags & BooklistStyle.SUMMARY_SHOW_LEVEL_2) != 0) {
            listViewHolder.level2Text.setVisibility(View.VISIBLE);
            listViewHolder.level2Text.setText("");
        } else {
            listViewHolder.level2Text.setVisibility(View.GONE);
        }
        if (hasLevel1 && (showHeaderFlags & BooklistStyle.SUMMARY_SHOW_LEVEL_1) != 0) {
            listViewHolder.level1Text.setVisibility(View.VISIBLE);
            listViewHolder.level1Text.setText("");
        } else {
            listViewHolder.level1Text.setVisibility(View.GONE);
        }

        // Update the header details
        if (count > 0 && (showHeaderFlags &
                (BooklistStyle.SUMMARY_SHOW_LEVEL_1 ^ BooklistStyle.SUMMARY_SHOW_LEVEL_2)) != 0) {
            updateListHeader(listViewHolder, mTopRow, hasLevel1, hasLevel2, showHeaderFlags);
        }

        // Define a scroller to update header detail when top row changes
        listView.setOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScroll(final @NonNull AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
                        // TODO: Investigate why BooklistPseudoCursor causes a scroll even when it is closed!
                        // Need to check isDead because BooklistPseudoCursor misbehaves when activity
                        // terminates and closes cursor
                        if (mLastTop != firstVisibleItem && !mIsDead && (showHeaderFlags != 0)) {
                            ListViewHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_HOLDER);// value: BooksOnBookshelf.ListViewHolder
                            updateListHeader(holder, firstVisibleItem, hasLevel1, hasLevel2, showHeaderFlags);
                        }
                    }

                    @Override
                    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
                    }
                }
        );

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            //TODO: move this to the bookshelf spinner ? more space in there
            actionBar.setSubtitle(mCurrentStyle == null ? "" : mCurrentStyle.getDisplayName());
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
                        this.getString(R.string.displaying_n_books_in_m_entries, mUniqueBooks, mTotalBooks)));
            } else {
                bookCounts.setText(getString(R.string.brackets,
                        this.getResources().getQuantityString(R.plurals.displaying_n_books, mUniqueBooks, mUniqueBooks)));
            }
            bookCounts.setVisibility(View.VISIBLE);
        } else {
            bookCounts.setVisibility(View.GONE);
        }
    }

    /**
     * Queue a runnable to set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     *
     * called from {@link #displayList}
     */
    private void fixPositionWhenDrawn(final @NonNull ListView lv, final @NonNull ArrayList<BookRowInfo> targetRows) {
        getListView().post(new Runnable() {
            @Override
            public void run() {
                // Find the actual extend of the current view and get centre.
                int first = lv.getFirstVisiblePosition();
                int last = lv.getLastVisiblePosition();
                int centre = (last + first) / 2;
                if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                    Logger.info("", " New List: (" + first + ", " + last + ")<-" + centre);
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
                    Logger.info("", " Best listPosition @" + best.listPosition);
                }
                // Try to put at top if not already visible, or only partially visible
                if (first >= best.listPosition || last <= best.listPosition) {
                    if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                        Logger.info("", " Adjusting position");
                    }
                    // setSelectionFromTop does not seem to always do what is expected.
                    // But adding smoothScrollToPosition seems to get the job done reasonably well.
                    //
                    // Specific problem occurs if:
                    // - put phone in portrait mode
                    // - edit a book near bottom of list
                    // - turn phone to landscape
                    // - save the book (don't cancel)
                    // Book will be off bottom of screen without the smoothScroll in the second Runnable.
                    //
                    lv.setSelectionFromTop(best.listPosition, 0);
                    // Code below does not behave as expected. Results in items often being near bottom.
                    //lv.setSelectionFromTop(best.listPosition, lv.getHeight() / 2);

                    // Without this call some positioning may be off by one row (see above).
                    final int newPos = best.listPosition;
                    getListView().post(new Runnable() {
                        @Override
                        public void run() {
                            lv.smoothScrollToPosition(newPos);
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
     * Update the list header to match the current top item.
     *
     * @param holder    object for header
     * @param topItem   Top row
     * @param hasLevel1 flag indicating level 1 is present
     * @param hasLevel2 flag indicating level 2 is present
     */
    private void updateListHeader(final @NonNull ListViewHolder holder,
                                  int topItem,
                                  final boolean hasLevel1,
                                  final boolean hasLevel2,
                                  final int flags) {
        if (topItem < 0) {
            topItem = 0;
        }

        mLastTop = topItem;
        if (hasLevel1 && (flags & BooklistStyle.SUMMARY_SHOW_LEVEL_1) != 0) {
            if (mListCursor.moveToPosition(topItem)) {
                String text = mListCursor.getCursorRow().getLevel1Data();
                holder.level1Text.setText(text);
                if (hasLevel2 && (flags & BooklistStyle.SUMMARY_SHOW_LEVEL_2) != 0) {
                    text = mListCursor.getCursorRow().getLevel2Data();
                    holder.level2Text.setText(text);
                }
            }
        }
    }

    /**
     * Save current position information, including view nodes that are expanded.
     *
     * ENHANCE: Handle positions a little better when books are deleted.
     *
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
            final SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
            final ListView lv = getListView();
            mTopRow = lv.getFirstVisiblePosition();
            ed.putInt(PREF_TOP_ROW, mTopRow);

            View v = lv.getChildAt(0);
            mTopRowTop = v == null ? 0 : v.getTop();
            ed.putInt(PREF_TOP_ROW_TOP, mTopRowTop);

            ed.apply();
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
            searchTextView.setText(getString(R.string.search_with_text, searchText));
        }

        return searchText;
    }

    private void initHints() {
        HintManager.displayHint(this.getLayoutInflater(), R.string.hint_view_only_book_details, null);
        HintManager.displayHint(this.getLayoutInflater(), R.string.hint_book_list, null);
        if (StartupActivity.getShowAmazonHint() && HintManager.shouldBeShown(R.string.hint_amazon_links_blurb)) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_amazon_links_blurb, null,
                    getString(R.string.menu_amazon_books_by_author),
                    getString(R.string.menu_amazon_books_in_series),
                    getString(R.string.menu_amazon_books_by_author_in_series),
                    getString(R.string.app_name));
        }
    }

    /**
     * Setup the bookshelf spinner.
     * The spinner listener will also call {@link #initBookList} when switching bookshelf.
     */
    private void initBookshelfSpinner() {
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);
        mBookshelfAdapter = new ArrayAdapter<>(this, R.layout.bookshelf_spinner);
        mBookshelfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfAdapter);

        /*
         * This is fired whenever a bookshelf is selected. Takes care of style and rebuilds the booklist
         */
        mBookshelfSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parentView, final View view, final int position, final long id) {
                // Check to see if mBookshelfAdapter is null, which should only occur if
                // the activity is being torn down: see Issue 370.
                if (mBookshelfAdapter == null) {
                    return;
                }

                String new_bookshelf = mBookshelfAdapter.getItem(position);
                if (new_bookshelf != null && !new_bookshelf.equalsIgnoreCase(mCurrentBookshelf.name)) {
                    // make the new shelf the current, and get it's preferred style
                    mCurrentBookshelf = mDb.getBookshelfByName(new_bookshelf);
                    if (mCurrentBookshelf == null) {
                        // shelf must have been deleted, switch to 'all book'
                        mCurrentBookshelf = new Bookshelf(Bookshelf.ALL_BOOKS, getString(R.string.all_books));
                    }

                    mCurrentStyle = getBookshelfStyle(mCurrentBookshelf);
                    saveBookShelfAndStyle(mCurrentBookshelf, mCurrentStyle);

                    initBookList(true);
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parentView) {
                // Do Nothing
            }
        });
    }

    /**
     * setup/reload the BookShelf list in the Spinner
     */
    private void populateBookShelfSpinner() {
        mBookshelfAdapter.clear();
        // Add the default All Books bookshelf
        mBookshelfAdapter.add(getString(R.string.all_books));
        int currentPos = 0;
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.id == mCurrentBookshelf.id) {
                currentPos = position;
            }
            position++;
            mBookshelfAdapter.add(bookshelf.name);
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
    public void onBooklistChange(final int extraFieldsInUse, final int fieldsChanged,
                                 final int rowPosition, final long bookId) {

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
     * FIXME: check  {@link BooklistChangeListener} flags and try avoid full rebuild? but the 'false' rebuild is broken anyhow.
     *
     * Called if global changes were made that (potentially) affect the whole list.
     *
     * @param extraFieldsInUse a bitmask with the Extra fields being used (visible) in the current
     *                         {@link BooklistStyle} as configured by the user
     * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
     */
    @Override
    public void onBooklistChange(final int extraFieldsInUse, int fieldsChanged) {
        //Something changed. Just regenerate the list.
        if (fieldsChanged != 0) {
            savePosition();
            initBookList(true);
        }
    }

    /**
     * Save the bookshelf + it's style + the style as the new default.
     */
    private void saveBookShelfAndStyle(final @NonNull Bookshelf bookshelf, final @NonNull BooklistStyle style) {
        SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
        // current bookshelf
        ed.putString(PREF_BOOKSHELF, bookshelf.name);
        // current global style, used as default if a bookshelf has no own style yet
        ed.putString(PREF_LIST_STYLE, style.getCanonicalName());
        // current style for current bookshelf
        ed.putString(PREF_LIST_STYLE_FOR_BOOKSHELF + bookshelf.name, style.getCanonicalName());
        ed.apply();
    }

    //<editor-fold desc="Booklist Style operations">

    /**
     * get the dedicated style for our bookshelf, or the global one.
     *
     * @see #getBookshelfStyle(Bookshelf, BooklistStyles)
     */
    @NonNull
    private BooklistStyle getBookshelfStyle(final @NonNull Bookshelf bookshelf) {
        return getBookshelfStyle(bookshelf, BooklistStyles.getAllStyles(mDb));
    }

    /**
     * get the dedicated style for our bookshelf
     *
     * @param bookshelf for which we want the style
     * @param styles    all styles
     *
     * @see #getBookshelfStyle(Bookshelf)
     */
    @NonNull
    private BooklistStyle getBookshelfStyle(final @NonNull Bookshelf bookshelf, final @NonNull BooklistStyles styles) {
        String globalDefaultStyle = BookCatalogueApp.getStringPreference(PREF_LIST_STYLE, getString(DEFAULT_STYLE));
        String key = PREF_LIST_STYLE_FOR_BOOKSHELF + bookshelf.name;
        String styleName = BookCatalogueApp.getStringPreference(key, globalDefaultStyle);

        // styleName is never null, see above, always a default available.
        @SuppressWarnings("ConstantConditions")
        BooklistStyle style = styles.findCanonical(styleName);

        if (style == null) {
            style = styles.get(0);
        }
        return style;
    }

    /**
     * Update and/or create the current style definition.
     */
    private void refreshStyle() {
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);

        BooklistStyle style;
        if (mCurrentStyle == null) {
            style = getBookshelfStyle(mCurrentBookshelf, styles);
        } else {
            style = styles.findCanonical(mCurrentStyle.getCanonicalName());
            if (style == null) {
                style = styles.get(0);
            }
        }

        mCurrentStyle = style;
    }

    /**
     * Setup the sort options. This function will also call fillData when
     * complete having loaded the appropriate view.
     */
    private void doSortMenu(final boolean showAll) {
        LayoutInflater inf = this.getLayoutInflater();
        @SuppressLint("InflateParams") // root==null as it's a dialog
                View dialogView = inf.inflate(R.layout.booklist_style_menu, null);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle(R.string.title_select_style)
                .create();
        dialog.show();

        // first section is a list of the styles
        ViewGroup group = dialogView.findViewById(R.id.radio_buttons);
        BooklistStyles styles;
        if (showAll) {
            styles = BooklistStyles.getAllStyles(mDb);
        } else {
            styles = BooklistStyles.getPreferredStyles(mDb);
        }
        for (BooklistStyle style : styles) {
            addStyleButtonMenuItem(dialog, inf, group, style);
        }

        // second section are options to do 'more things then listed'
        ViewGroup menu = dialogView.findViewById(R.id.menu);
        int moreOrLess = showAll ? R.string.menu_show_fewer_ellipsis : R.string.menu_show_more_ellipsis;
        addStyleTextMenuItem(menu, inf, moreOrLess, new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                doSortMenu(!showAll);
            }
        });
        addStyleTextMenuItem(menu, inf, R.string.menu_customize_ellipsis, new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent intent = new Intent(BooksOnBookshelf.this, BooklistPreferredStylesActivity.class);
                startActivityForResult(intent, BooklistPreferredStylesActivity.REQUEST_CODE); /* 3f210502-91ab-4b11-b165-605e09bb0c17 */
            }
        });
    }

    /**
     * Add a radio box to the sort options dialogue.
     */
    private void addStyleButtonMenuItem(final @NonNull AlertDialog dialog,
                                        final @NonNull LayoutInflater inf,
                                        final @NonNull ViewGroup parent,
                                        final @NonNull BooklistStyle style) {
        CompoundButton btn = (CompoundButton) inf.inflate(R.layout.booklist_style_menu_radio, dialog.getListView());
        btn.setText(style.getDisplayName());
        btn.setChecked(mCurrentStyle.getCanonicalName().equalsIgnoreCase(style.getCanonicalName()));

        parent.addView(btn);

        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onStyleSelected(style.getCanonicalName());
                dialog.dismiss();
            }
        });
    }

    /**
     * Add a text box to the sort options dialogue.
     */
    private void addStyleTextMenuItem(final @NonNull ViewGroup parent,
                                      final @NonNull LayoutInflater inf,
                                      final @StringRes int stringId,
                                      final @NonNull OnClickListener listener) {
        @SuppressLint("InflateParams") // root==null as it's a dialog
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
     * @param name Name of the selected style
     */
    private void onStyleSelected(final @NonNull String name) {
        // Find the style, if no match warn user and exit
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        BooklistStyle style = styles.findCanonical(name);
        if (style == null) {
            StandardDialogs.showUserMessage(this, R.string.error_no_style_found);
            return;
        }
        mCurrentStyle = style;

        // save the new bookshelf/style combination into the preferences
        saveBookShelfAndStyle(mCurrentBookshelf, mCurrentStyle);

        /* Set the rebuild state like this is the first time in, which it sort of is, given we
         * are changing style. There is very little ability to preserve position when going from
         * a list sorted by author/series to on sorted by unread/addedDate/publisher.
         * Keeping the current row/pos is probably the most useful thing we can do since we *may*
         * come back to a similar list.
         */
        try {
            ListView lv = getListView();
            mTopRow = lv.getFirstVisiblePosition();
            View v = lv.getChildAt(0);
            mTopRowTop = v == null ? 0 : v.getTop();
        } catch (Exception ignored) {
        }

        // New style, so use user-pref for rebuild
        mRebuildState = BooklistPreferencesActivity.getRebuildState();

        // Do a rebuild
        initBookList(true);
    }

    @NonNull
    private BooklistBuilder getBooklistBuilder() {
        // Make sure we have a style chosen
        if (mCurrentStyle == null) {
            mCurrentStyle = getBookshelfStyle(mCurrentBookshelf);
        }

        // get a new builder and add the required extra domains
        BooklistBuilder builder = new BooklistBuilder(this, mCurrentStyle);

        builder.requireDomain(DatabaseDefinitions.DOM_TITLE,
                DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_TITLE), true);

        builder.requireDomain(DatabaseDefinitions.DOM_BOOK_READ,
                DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_BOOK_READ), false);

        return builder;
    }
    //</editor-fold>

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
     */
    private void initBookList(final boolean isFullRebuild) {

        //TOMF: FIXME: now confirmed, this is one from the original code. isFullRebuild=false is BROKEN.
        // basically all group headers are no longer in the TBL_BOOK_LIST.
        // See DatabaseDefinitions#TBL_BOOK_LIST for an example of the correct table content
        // After rebuild(false) all rows which don't show an expanded node are gone.
        //

        // Hardcoded override to always do a full rebuild.
//        mTaskQueue.enqueue(new GetBookListTask(isFullRebuild,
        mTaskQueue.enqueue(new GetBookListTask(true,
                mCurrentBookshelf.id,
                mSearchText,
                mAuthorSearchText,
                mTitleSearchText,
                mSearchBookIdList));

        if (mListDialog == null) {
            mListDialog = ProgressDialog.show(this,
                    "",
                    getString(R.string.progress_msg_getting_books),
                    true,
                    true, new OnCancelListener() {
                        @Override
                        public void onCancel(@NonNull DialogInterface dialog) {
                            // Cancelling the list cancels the activity.
                            BooksOnBookshelf.this.finish();
                            dialog.dismiss();
                            mListDialog = null;
                        }
                    });
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private class GetBookListTask implements SimpleTaskQueue.SimpleTask {
        /** Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data */
        private final boolean isFullRebuild;
        /** the builder */
        @NonNull
        private final BooklistBuilder bookListBuilder;
        /** Resulting Cursor */
        private BooklistPseudoCursor tempList = null;
        /** used to determine new cursor position */
        @Nullable
        private ArrayList<BookRowInfo> targetRows = null;

        /**
         * Constructor.
         *
         * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
         */
        GetBookListTask(final boolean isFullRebuild,
                        final long currentBookshelfId,
                        final @Nullable String searchText,
                        final @Nullable String searchAuthor,
                        final @Nullable String searchTitle,
                        final @Nullable List<Integer> searchBookIdList) {

            if (!isFullRebuild && BuildConfig.DEBUG) {
                Logger.info(this, " constructor, isFullRebuild=false");
            }
            this.isFullRebuild = isFullRebuild;

            // If not a full rebuild then just use the current builder to re-query the underlying data
            //noinspection ConstantConditions
            if (mListCursor != null && !this.isFullRebuild) {
                bookListBuilder = mListCursor.getBuilder();
            } else {
                bookListBuilder = getBooklistBuilder();
                // Build based on our current criteria
                bookListBuilder.setCriteriaText(searchText);
                bookListBuilder.setCriteriaAuthorName(searchAuthor);
                bookListBuilder.setCriteriaTitle(searchTitle);
                bookListBuilder.setCriteriaBookIdList(searchBookIdList);
                bookListBuilder.setCriteriaBookshelfId(currentBookshelfId);
            }
        }

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
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
                    mRebuildState = BooklistPreferencesActivity.BOOK_LIST_STATE_PRESERVED;
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
                                    bookListBuilder.ensureAbsolutePositionVisible(rowInfo.absolutePosition);
                                }
                            }
                            // Recalculate all positions
                            for (BookRowInfo rowInfo : targetRows) {
                                rowInfo.listPosition = bookListBuilder.getPosition(rowInfo.absolutePosition);
                            }
                        }
//						// Find the nearest row to the recorded 'top' row.
//						int targetRow = bookRows[0];
//						int minDist = Math.abs(mTopRow - b.getPosition(targetRow));
//						for(int i=1; i < bookRows.length; i++) {
//							int pos = b.getPosition(bookRows[i]);
//							int dist = Math.abs(mTopRow - pos);
//							if (dist < minDist)
//								targetRow = bookRows[i];
//						}
//						// Make sure the target row is visible/expanded.
//						b.ensureAbsolutePositionVisible(targetRow);
//						// Now find the position it will occupy in the view
//						mTargetPos = b.getPosition(targetRow);
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
                    Logger.info(this, " Count(" + count + "): " + (t4 - t3) + "/" + (t5 - t4) + "/" + (t6 - t5));
                    Logger.info(this, " ====== ");
                    Logger.info(this, " Total: " + (t6 - t0));
                }
                // Save a flag to say list was loaded at least once successfully
                mListHasBeenLoaded = true;

            } finally {
                if (taskContext.isTerminating()) {
                    // onFinish() will not be called, and we can discard our work...
                    if (tempList != null && tempList != mListCursor) {
                        if (mListCursor == null || tempList.getBuilder() != mListCursor.getBuilder()) {
                            try {
                                tempList.getBuilder().close();
                            } catch (Exception ignore) {
                            }
                        }
                        try {
                            tempList.close();
                        } catch (Exception ignore) {
                        }
                    }
                }
            }
        }

        @Override
        public void onFinish(Exception e) {
            // If activity dead, just do a local cleanup and exit.
            if (mIsDead) {
                tempList.close();
                return;
            }
            // Dismiss the progress dialog, if present
            if (mListDialog != null && !mTaskQueue.hasActiveTasks()) {
                mListDialog.dismiss();
                mListDialog = null;
            }
            // Update the data
            if (tempList != null) {
                displayList(tempList, targetRows);
            }
            tempList = null;
        }

    }

    /**
     * record to hold the current ListView header details.
     *
     * @author Philip Warner
     */
    private class ListViewHolder {
        final TextView level1Text;
        final TextView level2Text;

        ListViewHolder() {
            level1Text = findViewById(R.id.level_1_text);
            level2Text = findViewById(R.id.level_2_text);
        }
    }
}
