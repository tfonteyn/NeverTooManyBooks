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

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler.BooklistChangeListener;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStylePropertiesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.BooklistStylesListActivity;
import com.eleybourn.bookcatalogue.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogMenuItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.searches.SearchCatalogue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf extends BookCatalogueActivity implements BooklistChangeListener {
    /** Prefix used in preferences for this activity */
    private final static String TAG = "BooksOnBookshelf";
    /** Preference name for the bookshelf to load next time we startup  */
    public final static String PREF_BOOKSHELF = TAG + ".BOOKSHELF";
    /** * Preference name */
    private final static String PREF_TOP_ROW = TAG + ".TOP_ROW";
    /** * Preference name */
    private final static String PREF_TOP_ROW_TOP = TAG + ".TOP_ROW_TOP";
    /** * Preference name for the style to use */
    private final static String PREF_LIST_STYLE = TAG + ".LIST_STYLE";

    private final static String PREF_LIST_STYLE_FOR_BOOKSHELF = TAG + ".LIST_STYLE.BOOKSHELF." /* + name of shelf */;

    /** Menu id   */
    private static final int MENU_SORT = MenuHandler.FIRST + 1;
    private static final int MENU_EXPAND = MenuHandler.FIRST + 2;
    private static final int MENU_COLLAPSE = MenuHandler.FIRST + 3;

    /**
     * Counter for com.eleybourn.bookcatalogue.debug purposes
     */
    private static Integer mInstanceCount = 0;
    /**
     * Task queue to get book lists in background
     */
    private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("BoB-List", 1);
    /**
     * Currently selected list style
     */
    private BooklistStyle mCurrentStyle = null;
    /**
     * Currently selected bookshelf
     */
    private String mCurrentBookshelf = ""; //getString(R.string.all_books);
    /**
     * Flag indicating activity has been destroyed. Used for background tasks
     */
    private boolean mIsDead = false;
    /**
     * Flag to indicate that a list has been successfully loaded -- affects the way we save state
     */
    private boolean mListHasBeenLoaded = false;
    /**
     * Used by onScroll to detect when the top row has actually changed.
     */
    private int mLastTop = -1;
    /**
     * ProgressDialog used to display "Getting books...". Needed here so we can dismiss it on close.
     */
    private ProgressDialog mListDialog = null;
    /**
     * A book ID used for keeping/updating current list position, eg. when a book is edited.
     */
    private long mMarkBookId = 0;
    /**
     * Text to use in search query
     */
    private String mSearchText = "";
    /**
     * Saved position of last top row
     */
    private int mTopRow = 0;
    /**
     * Saved position of last top row offset from view top
     */
    private int mTopRowTop = 0;
    /**
     * Database connection
     */
    private CatalogueDBAdapter mDb;
    /**
     * Handler to manage all Views on the list
     */
    private BooksMultiTypeListHandler mListHandler;
    /**
     * Current displayed list cursor
     */
    private BooklistPseudoCursor mList;
    /**
     * Multi-type adapter to manage list connection to cursor
     */
    private MultiTypeListCursorAdapter mAdapter;
    /**
     * Preferred booklist state in next rebuild
     */
    private int mRebuildState;

    /**
     * Total number of books in current list
     */
    private int mTotalBooks = 0;
    /**
     * Total number of unique books in current list
     */
    private int mUniqueBooks = 0;
    /**
     * Setup the bookshelf spinner. This function will also call fillData when
     * complete having loaded the appropriate bookshelf.
     */
    private Spinner mBookshelfSpinner;
    private ArrayAdapter<String> mBookshelfAdapter;
    private MenuHandler mMenuHandler;

    /**
     * BOOKSONBOOKSHELF ONLY. Count instances
     */
    public BooksOnBookshelf() {
        super();
        if (DEBUG_SWITCHES.BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
            synchronized (mInstanceCount) {
                mInstanceCount++;
                System.out.println("BoB instances: " + mInstanceCount);
            }
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.app_name);

            if (savedInstanceState == null) {
                // Get preferred booklist state to use from preferences;
                // default to always expanded (MUCH faster than 'preserve' with lots of books)
                mRebuildState = BooklistPreferencesActivity.getRebuildState();
            } else {
                // Always preserve state when rebuilding/recreating etc
                mRebuildState = BooklistPreferencesActivity.BOOKLISTS_STATE_PRESERVED;
            }

            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            // Restore bookshelf and position
            mCurrentBookshelf = BCPreferences.getString(PREF_BOOKSHELF, mCurrentBookshelf);
            mTopRow = BCPreferences.getInt(PREF_TOP_ROW, 0);
            mTopRowTop = BCPreferences.getInt(PREF_TOP_ROW_TOP, 0);

            // Restore view style
            refreshStyle();

            // set the search capability to local (application) search
            setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
            setupSearch();

            setupListItemMenus();

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
            mMarkBookId = -1;

            setupBookshelfSpinner();

            setupBookList(true);

            if (savedInstanceState == null) {
                setupHints();
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
    }



    @Override
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
    public void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();
        if (mSearchText == null || mSearchText.isEmpty()) {
            savePosition();
        }

        if (isFinishing()) {
            mTaskQueue.finish();
        }

        if (mListDialog != null) {
            mListDialog.dismiss();
        }

        Tracker.exitOnPause(this);
    }

    @Override
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();

        mIsDead = true;
        mTaskQueue.finish();

        try {
            if (mList != null) {
                try {
                    mList.getBuilder().close();
                } catch (Exception e) {
                    Logger.logError(e);
                }
                mList.close();
            }
            mDb.close();
        } catch (Exception e) {
            Logger.logError(e);
        }
        mListHandler = null;
        mAdapter = null;
        mBookshelfSpinner = null;
        mBookshelfAdapter = null;
        if (DEBUG_SWITCHES.BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
            synchronized (mInstanceCount) {
                mInstanceCount--;

                System.out.println("BoB instances: " + mInstanceCount);
            }
        }

        if (DEBUG_SWITCHES.BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
            TrackedCursor.dumpCursors();
        }
        Tracker.exitOnDestroy(this);
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        getDrawerLayout().closeDrawers();

        switch (menuItem.getItemId()) {
            case R.id.nav_search:
                startActivity(new Intent(this, SearchCatalogue.class));
                return true;
            case R.id.nav_manage_bookshelves:
                startActivity(new Intent(this, EditBookshelfListActivity.class));
                break;
            case R.id.nav_booklist_prefs:
                startActivity(new Intent(this, BooklistPreferencesActivity.class));
                return true;
            case R.id.nav_other_prefs:
                startActivity(new Intent(this, PreferencesActivity.class));
                return true;
            case R.id.nav_admin:
                startActivity(new Intent(this, AdministrationFunctions.class));
                return true;
            case R.id.nav_about:
                startActivity(new Intent(this, AdministrationAbout.class));
                return true;
            case R.id.nav_help:
                startActivity(new Intent(this, Help.class));
                return true;
            case R.id.nav_donate:
                startActivity(new Intent(this, AdministrationDonate.class));
                break;
        }
        return super.onNavigationItemSelected(menuItem);
    }

    /**
     * Handle selections from context menu
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        mList.moveToPosition(info.position);
        return mListHandler.onContextItemSelected(mDb,
                mList.getRowView(), this, item.getItemId()) || super.onContextItemSelected(item);
    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mMenuHandler = new MenuHandler(menu);
        mMenuHandler.addCreateBookSubMenu(menu);

        mMenuHandler.addItem(menu, MENU_SORT, R.string.sort_and_style_ellipsis,
                android.R.drawable.ic_menu_sort_alphabetically)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        mMenuHandler.addItem(menu, MENU_EXPAND, R.string.menu_expand_all,R.drawable.ic_menu_expand);

        mMenuHandler.addItem(menu, MENU_COLLAPSE, R.string.menu_collapse_all, R.drawable.ic_menu_collapse);

        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected. A large switch statement to
     * call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mMenuHandler != null && !mMenuHandler.onOptionsItemSelected(this, item)) {
            switch (item.getItemId()) {

                case MENU_SORT:
                    HintManager.displayHint(this, R.string.hint_booklist_style_menu, new Runnable() {
                        @Override
                        public void run() {
                            doSortMenu(false);
                        }
                    });
                    return true;

                case MENU_EXPAND: {
                    // It is possible that the list will be empty, if so, ignore
                    if (getListView().getChildCount() != 0) {
                        int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                        savePosition();
                        mList.getBuilder().expandAll(true);
                        mTopRow = mList.getBuilder().getPosition(oldAbsPos);
                        BooklistPseudoCursor newList = mList.getBuilder().getList();
                        displayList(newList, null);
                    }
                    break;
                }
                case MENU_COLLAPSE: {
                    // It is possible that the list will be empty, if so, ignore
                    if (getListView().getChildCount() != 0) {
                        int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                        savePosition();
                        mList.getBuilder().expandAll(false);
                        mTopRow = mList.getBuilder().getPosition(oldAbsPos);
                        displayList(mList.getBuilder().getList(), null);
                    }
                    break;
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when an activity launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Tracker.enterOnActivityResult(this, requestCode, resultCode);

        mMarkBookId = 0;

        switch (requestCode) {
            case UniqueId.ACTIVITY_CREATE_BOOK_SCAN:
                try {
                    if (intent != null && intent.hasExtra(UniqueId.KEY_ID)) {
                        long newId = intent.getLongExtra(UniqueId.KEY_ID, 0);
                        if (newId != 0) {
                            mMarkBookId = newId;
                        }
                    }
                    // Always rebuild, even after a cancelled edit because the series may have had global edits
                    // ENHANCE: Allow detection of global changes to avoid unnecessary rebuilds
                    this.setupBookList(false);
                } catch (NullPointerException e) {
                    // This is not a scan result, but a normal return
                    //fillData();
                }
                break;
            case UniqueId.ACTIVITY_CREATE_BOOK_ISBN:
            case UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY:
            case UniqueId.ACTIVITY_VIEW_BOOK:
            case UniqueId.ACTIVITY_EDIT_BOOK:
                try {
                    if (intent != null && intent.hasExtra(UniqueId.KEY_ID)) {
                        long id = intent.getLongExtra(UniqueId.KEY_ID, 0);
                        if (id != 0) {
                            mMarkBookId = id;
                        }
                    }
                    // Always rebuild, even after a cancelled edit because the series may have had global edits
                    // ENHANCE: Allow detection of global changes to avoid unnecessary rebuilds
                    this.setupBookList(false);
                } catch (Exception e) {
                    Logger.logError(e);
                }
                break;
            case UniqueId.ACTIVITY_BOOKLIST_STYLE_PROPERTIES:
                try {
                    if (intent != null && intent.hasExtra(BooklistStylePropertiesActivity.BKEY_STYLE)) {
                        BooklistStyle style = (BooklistStyle) intent.getSerializableExtra(BooklistStylePropertiesActivity.BKEY_STYLE);
                        if (style != null) {
                            mCurrentStyle = style;
                        }
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                }
                this.savePosition();
                this.setupBookList(true);
                break;
            case UniqueId.ACTIVITY_BOOKLIST_STYLES:
            case UniqueId.ACTIVITY_ADMIN:
            case UniqueId.ACTIVITY_PREFERENCES:
                // Refresh the style because prefs may have changed
                refreshStyle();
                this.savePosition();
                this.setupBookList(true);
                break;
        }
        Tracker.exitOnActivityResult(this, requestCode, resultCode);
    }


    /**
     * Support routine now that this activity is no longer a ListActivity
     */
    private ListView getListView() {
        return (ListView) findViewById(android.R.id.list);
    }


    /**
     * Display the passed cursor in the ListView, and change the position to targetRow.
     *
     * @param newList    New cursor to use
     * @param targetRows if set, change the position to targetRow.
     */
    private void displayList(@NonNull BooklistPseudoCursor newList, @Nullable final ArrayList<BookRowInfo> targetRows) {
        Objects.requireNonNull(newList, "Unexpected empty list");

        final int showHeaderFlags = (mCurrentStyle == null ? BooklistStyle.SUMMARY_SHOW_ALL : mCurrentStyle.getShowHeaderInfo());

        final TextView bookCounts = findViewById(R.id.bookshelf_count);
        if ((showHeaderFlags & BooklistStyle.SUMMARY_SHOW_COUNT) != 0) {
            if (mUniqueBooks != mTotalBooks) {
                bookCounts.setText(BookCatalogueApp.getResourceString(R.string.brackets,
                        this.getString(R.string.displaying_n_books_in_m_entries, mUniqueBooks, mTotalBooks)));
            } else {
                bookCounts.setText(BookCatalogueApp.getResourceString(R.string.brackets,
                        this.getString(R.string.displaying_n_books, mUniqueBooks)));
            }
            bookCounts.setVisibility(View.VISIBLE);
        } else {
            bookCounts.setVisibility(View.GONE);
        }

        long t0 = System.currentTimeMillis();

        // Save the old list so we can close it later, and set the new list locally
        BooklistPseudoCursor oldList = mList;
        mList = newList;

        // Get new handler and adapter since list may be radically different structure
        mListHandler = new BooksMultiTypeListHandler();
        mAdapter = new MultiTypeListCursorAdapter(this, mList, mListHandler);

        // Get the ListView and set it up
        final ListView lv = getListView();
        final ListViewHolder lvHolder = new ListViewHolder();
        ViewTagger.setTag(lv, R.id.TAG_HOLDER, lvHolder);

        lv.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of ListView
        lv.setFastScrollEnabled(false);
        lv.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mList.getCount();
        try {
            if (mTopRow >= count) {
                mTopRow = count - 1;
                lv.setSelection(mTopRow);
            } else {
                lv.setSelectionFromTop(mTopRow, mTopRowTop);
            }
        } catch (Exception ignored) {
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and once we can tell
        // if it is already in the view.
        if (targetRows != null) {
            postRunnableToFixPositionOnceControlDrawn(lv, targetRows);
        }

        final boolean hasLevel1 = (mList.numLevels() > 1);
        final boolean hasLevel2 = (mList.numLevels() > 2);

        if (hasLevel2 && (showHeaderFlags & BooklistStyle.SUMMARY_SHOW_LEVEL_2) != 0) {
            lvHolder.level2Text.setVisibility(View.VISIBLE);
            lvHolder.level2Text.setText("");
        } else {
            lvHolder.level2Text.setVisibility(View.GONE);
        }
        if (hasLevel1 && (showHeaderFlags & BooklistStyle.SUMMARY_SHOW_LEVEL_1) != 0) {
            lvHolder.level1Text.setVisibility(View.VISIBLE);
            lvHolder.level1Text.setText("");
        } else {
            lvHolder.level1Text.setVisibility(View.GONE);
        }

        // Update the header details
        if (count > 0 && (showHeaderFlags &
                (BooklistStyle.SUMMARY_SHOW_LEVEL_1 ^ BooklistStyle.SUMMARY_SHOW_LEVEL_2)) != 0) {
            updateListHeader(lvHolder, mTopRow, hasLevel1, hasLevel2, showHeaderFlags);
        }

        // Define a scroller to update header detail when top row changes
        lv.setOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        // TODO: Investigate why BooklistPseudoCursor causes a scroll even when it is closed!
                        // Need to check isDead because BooklistPseudoCursor misbehaves when activity
                        // terminates and closes cursor
                        if (mLastTop != firstVisibleItem && !mIsDead && (showHeaderFlags != 0)) {
                            ListViewHolder holder = ViewTagger.getTag(view, R.id.TAG_HOLDER);
                            Objects.requireNonNull(holder);
                            updateListHeader(holder, firstVisibleItem, hasLevel1, hasLevel2, showHeaderFlags);
                        }
                    }

                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
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
            if (mList.getBuilder() != oldList.getBuilder()) {
                oldList.getBuilder().close();
            }
            oldList.close();
        }
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            System.out.println("displayList: " + (System.currentTimeMillis() - t0));
        }
    }

    /**
     * as per method name... called from {@link #displayList}
     */
    private void postRunnableToFixPositionOnceControlDrawn(@NonNull final ListView lv, @NonNull final ArrayList<BookRowInfo> targetRows) {
        getListView().post(new Runnable() {
            @Override
            public void run() {
                // Find the actual extend of the current view and get centre.
                int first = lv.getFirstVisiblePosition();
                int last = lv.getLastVisiblePosition();
                int centre = (last + first) / 2;
                if (DEBUG_SWITCHES.BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
                    System.out.println("New List: (" + first + ", " + last + ")<-" + centre);
                }
                // Get the first 'target' and make it 'best candidate'
                BookRowInfo best = targetRows.get(0);
                int dist = Math.abs(best.listPosition - centre);
                // Scan all other rows, looking for a nearer one
                for (int i = 1; i < targetRows.size(); i++) {
                    BookRowInfo ri = targetRows.get(i);
                    int newDist = Math.abs(ri.listPosition - centre);
                    if (newDist < dist) {
                        dist = newDist;
                        best = ri;
                    }
                }

                if (DEBUG_SWITCHES.BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
                    System.out.println("Best @" + best.listPosition);
                }
                // Try to put at top if not already visible, or only partially visible
                if (first >= best.listPosition || last <= best.listPosition) {
                    if (DEBUG_SWITCHES.BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
                        System.out.println("Adjusting position");
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

                    // smoothScrollToPosition is only available at API level 8.
                    // Without this call some positioning may be off by one row (see above).
                    final int newPos = best.listPosition;
                    getListView().post(new Runnable() {
                        @Override
                        public void run() {
                            lv.smoothScrollToPosition(newPos);
                        }
                    });

                    //int newTop = best.listPosition - (last-first)/2;
                    // if (BOOKSONBOOKSHELF && BuildConfig.DEBUG) {
                    //System.out.println("New Top @" + newTop );
                    //}
                    //lv.setSelection(newTop);
                }
            }
        });
    }

    /**
     * Update the list header to match the current top item.
     *
     * @param holder    Holder object for header
     * @param topItem   Top row
     * @param hasLevel1 flag indicating level 1 is present
     * @param hasLevel2 flag indicating level 2 is present
     */
    private void updateListHeader(@NonNull final ListViewHolder holder, int topItem, boolean hasLevel1, boolean hasLevel2, int flags) {
        if (topItem < 0) {
            topItem = 0;
        }

        mLastTop = topItem;
        if (hasLevel1 && (flags & BooklistStyle.SUMMARY_SHOW_LEVEL_1) != 0) {
            if (mList.moveToPosition(topItem)) {
                String s = mList.getRowView().getLevel1Data();
                holder.level1Text.setText(s);
                if (hasLevel2 && (flags & BooklistStyle.SUMMARY_SHOW_LEVEL_2) != 0) {
                    s = mList.getRowView().getLevel2Data();
                    holder.level2Text.setText(s);
                }
            }
        }
    }

    /**
     * Save current position information, including view nodes that are expanded.
     * <p>
     * ENHANCE: Handle positions a little better when books are deleted.
     * <p>
     * Deleting a book by 'n' authors from the last author in list results
     * in the list decreasing in length by, potentially, n*2 items. The
     * current 'savePosition()' code will return to the old position in the
     * list after such an operation...which will be too far down.
     */
    private void savePosition() {
        if (mIsDead) {
            return;
        }

        final Editor ed = BCPreferences.edit();

        // Save position in list
        if (mListHasBeenLoaded) {
            final ListView lv = getListView();
            mTopRow = lv.getFirstVisiblePosition();
            ed.putInt(PREF_TOP_ROW, mTopRow);
            View v = lv.getChildAt(0);
            mTopRowTop = v == null ? 0 : v.getTop();
            ed.putInt(PREF_TOP_ROW_TOP, mTopRowTop);
        }

        if (mCurrentStyle != null) {
            ed.putString(PREF_LIST_STYLE, mCurrentStyle.getCanonicalName());
            ed.putString(PREF_LIST_STYLE_FOR_BOOKSHELF + mCurrentBookshelf, mCurrentStyle.getCanonicalName());
        }

        ed.commit();
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
     */
    private void setupBookList(boolean isFullRebuild) {
        mTaskQueue.enqueue(new GetListTask(isFullRebuild));
        if (mListDialog == null) {
            mListDialog = ProgressDialog.show(this, "", getString(R.string.getting_books_ellipsis), true, true, new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    // Cancelling the list cancels the activity.
                    BooksOnBookshelf.this.finish();
                    dialog.dismiss();
                    mListDialog = null;
                }
            });
        }
    }

    private void setupSearch() {
        final Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Return the search results instead of all books (for the bookshelf)
            mSearchText = intent.getStringExtra(SearchManager.QUERY).trim();
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            mSearchText = intent.getDataString();
        }
        if (mSearchText == null || ".".equals(mSearchText)) {
            mSearchText = "";
        }

        final TextView searchTextView = findViewById(R.id.search_text);
        if (mSearchText.isEmpty()) {
            searchTextView.setVisibility(View.GONE);
        } else {
            searchTextView.setVisibility(View.VISIBLE);
            searchTextView.setText(getString(R.string.search_with_text, mSearchText));
        }
    }

    /**
     * Add both Click and LongClick to row items
     */
    private void setupListItemMenus() {

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) {
                // Move the cursor to the position
                mList.moveToPosition(position);

                // If it's a book, edit it.
                int rowKind = mList.getRowView().getKind();
                if (rowKind == RowKinds.ROW_KIND_BOOK) {
                    EditBookActivity.openBook(BooksOnBookshelf.this, mList.getRowView().getBookId(), mList.getBuilder(), position);
                } else {
                    // If it's level, expand/collapse. Technically, TODO: we could expand/collapse any level
                    // but storing and recovering the view becomes unmanageable.
                    if (mList.getRowView().getLevel() == 1) {
                        mList.getBuilder().toggleExpandNode(mList.getRowView().getAbsolutePosition());
                        mList.requery();
                        mAdapter.notifyDataSetChanged();
                    }
                }
            }
        });

        getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                mList.moveToPosition(position);
                ArrayList<SimpleDialogItem> menu = new ArrayList<>();
                mListHandler.buildContextMenu(mList.getRowView(), menu);

                if (menu.size() > 0) {
                    StandardDialogs.selectItemDialog(getLayoutInflater(), null,
                            menu, null, new SimpleDialogOnClickListener() {
                                @Override
                                public void onClick(@NonNull final SimpleDialogItem item) {
                                    mList.moveToPosition(position);
                                    int id = ((SimpleDialogMenuItem) item).getItemId();
                                    mListHandler.onContextItemSelected(mDb, mList.getRowView(), BooksOnBookshelf.this, id);

                                    // If data changed, we need to update display
                                    if (id == R.id.MENU_MARK_AS_UNREAD || id == R.id.MENU_MARK_AS_READ) {
                                        setupBookList(true);
                                    }
                                }
                            });
                }
                return true;
            }
        });
    }

    private void setupBookshelfSpinner() {
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);
        mBookshelfAdapter = new ArrayAdapter<>(this, R.layout.spinner_frontpage);
        mBookshelfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfAdapter);

        /*
         * This is fired whenever a bookshelf is selected. It is also fired when the
         * page is loaded with the default (or current) bookshelf.
         */
        mBookshelfSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                // Check to see if mBookshelfAdapter is null, which should only occur if
                // the activity is being torn down: see Issue 370.
                if (mBookshelfAdapter == null) {
                    return;
                }

                String new_bookshelf = mBookshelfAdapter.getItem(position);
                if (position == 0) {
                    new_bookshelf = "";
                }
                if (new_bookshelf != null && !new_bookshelf.equalsIgnoreCase(mCurrentBookshelf)) {
                    // save the current bookshelf and its style into the preferences
                    SharedPreferences.Editor ed = BCPreferences.edit();
                    ed.putString(PREF_BOOKSHELF, mCurrentBookshelf);
                    ed.putString(PREF_LIST_STYLE_FOR_BOOKSHELF + mCurrentBookshelf, mCurrentStyle.getCanonicalName());
                    ed.commit();

                    // and load the new style
                    mCurrentBookshelf = new_bookshelf;
                    mCurrentStyle = getBookshelfStyle();

                    setupBookList(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do Nothing
            }
        });

        // actual shelves
        populateBookShelfSpinner();

        /* {@link BookCatalogueClassic} only */
        TextView bookshelfNum = findViewById(R.id.bookshelf_num);
        if (bookshelfNum != null) {
            bookshelfNum.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mBookshelfSpinner.performClick();
                    return;
                }
            });
        }
    }

    public void setupHints() {
        HintManager.displayHint(this, R.string.hint_view_only_book_details, null);
        HintManager.displayHint(this, R.string.hint_book_list, null);
        if (StartupActivity.getShowAmazonHint() && HintManager.shouldBeShown(R.string.hint_amazon_links_blurb)) {
            HintManager.displayHint(this, R.string.hint_amazon_links_blurb, null,
                    getString(R.string.amazon_books_by_author),
                    getString(R.string.amazon_books_in_series),
                    getString(R.string.amazon_books_by_author_in_series),
                    getString(R.string.app_name));
        }
    }

    /**
     * setup/refresh the BookShelf list in the Spinner
     */
    private void populateBookShelfSpinner() {
        mBookshelfAdapter.clear();
        // Add the default All Books bookshelf
        mBookshelfAdapter.add(getString(R.string.all_books));
        int currentPos = 0;
        int position = 1;

        try (Cursor bookshelves = mDb.fetchAllBookshelves()) {
            if (bookshelves.moveToFirst()) {
                do {
                    String this_bookshelf = bookshelves.getString(1);
                    if (this_bookshelf.equals(mCurrentBookshelf)) {
                        currentPos = position;
                    }
                    position++;
                    mBookshelfAdapter.add(this_bookshelf);
                }
                while (bookshelves.moveToNext());
            }
        }
        // Set the current bookshelf. We use this to force the correct bookshelf after
        // the state has been restored.
        mBookshelfSpinner.setSelection(currentPos);
    }

    /**
     * Update and/or create the current style definition.
     *
     */
    private void refreshStyle() {
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);

        BooklistStyle style;
        if (mCurrentStyle == null) {
            style = getBookshelfStyle();
        } else {
            style = styles.findCanonical( mCurrentStyle.getCanonicalName());
            if (style == null) {
                style = styles.get(0);
            }
        }

        mCurrentStyle = style;
    }

    /**
     * get the dedicated style for our bookshelf, or the global one.
     */
    @NonNull
    private BooklistStyle getBookshelfStyle() {
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);

        // bookshelf overrides global which overrides default build-in
        String styleName = BCPreferences.getString(PREF_LIST_STYLE_FOR_BOOKSHELF + mCurrentBookshelf,
                BCPreferences.getString(PREF_LIST_STYLE, getString(R.string.sort_author_series)));

        @SuppressWarnings("ConstantConditions") // styleName is never null, see above, always a default available.
        BooklistStyle style= styles.findCanonical(styleName);

        if (style == null) {
            style = styles.get(0);
        }
        return style;
    }

    @Override
    public void onBooklistChange(final int flags) {
        if (flags != 0) {
            // Something changed. Just regenerate.
            savePosition();
            this.setupBookList(true);
        }
    }

    /**
     * Setup the sort options. This function will also call fillData when
     * complete having loaded the appropriate view.
     */
    private void doSortMenu(final boolean showAll) {
        LayoutInflater inf = this.getLayoutInflater();
        View v = inf.inflate(R.layout.booklist_style_menu, null);
        RadioGroup group = v.findViewById(R.id.radio_buttons);
        LinearLayout main = v.findViewById(R.id.menu);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(v)
                .setTitle(R.string.select_style)
                .create();
        dialog.show();

        Iterator<BooklistStyle> i;
        if (!showAll) {
            i = BooklistStyles.getPreferredStyles(mDb).iterator();
        } else {
            i = BooklistStyles.getAllStyles(mDb).iterator();
        }

        while (i.hasNext()) {
            BooklistStyle style = i.next();
            makeRadio(dialog, inf, group, style);
        }
        int moreOrLess = showAll ? R.string.show_fewer_ellipsis : R.string.show_more_ellipsis;

        makeText(main, inf, moreOrLess, new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                doSortMenu(!showAll);
            }
        });

        makeText(main, inf, R.string.customize_ellipsis, new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                BooklistStylesListActivity.startActivity(BooksOnBookshelf.this);
            }
        });
    }

    /**
     * Add a radio box to the sort options dialogue.
     */
    private void makeRadio(@NonNull final AlertDialog sortDialog,
                           @NonNull final LayoutInflater inf,
                           @NonNull RadioGroup group,
                           @NonNull final BooklistStyle style) {
        View v = inf.inflate(R.layout.booklist_style_menu_radio, null);
        RadioButton btn = (RadioButton) v;
        btn.setText(style.getDisplayName());

        if (mCurrentStyle.getCanonicalName().equalsIgnoreCase(style.getCanonicalName())) {
            btn.setChecked(true);
        } else {
            btn.setChecked(false);
        }
        group.addView(btn);

        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSelectedStyle(style.getCanonicalName());
                sortDialog.dismiss();
                return;
            }
        });
    }
    /**
     * Handle the style that a user has selected.
     *
     * @param name Name of the selected style
     */
    private void handleSelectedStyle(@NonNull final String name) {
        // Find the style, if no match warn user and exit
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        BooklistStyle style = styles.findCanonical(name);
        if (style == null) {
            Toast.makeText(this, "Could not find appropriate list", Toast.LENGTH_LONG).show();
            return;
        }

        // Set the rebuild state like this is the first time in, which it sort of is, given we are changing style.
        // There is very little ability to preserve position when going from a list sorted by author/series to
        // on sorted by unread/addedDate/publisher. Keeping the current row/pos is probably the most useful
        // thing we can do since we *may* come back to a similar list.
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
        mCurrentStyle = style;
        setupBookList(true);
    }

    /**
     * Add a text box to the sort options dialogue.
     */
    private void makeText(@NonNull final LinearLayout parent,
                          @NonNull final LayoutInflater inf,
                          final int stringId,
                          @NonNull OnClickListener listener) {
        TextView view = (TextView) inf.inflate(R.layout.booklist_style_menu_text, null);
        Typeface tf = view.getTypeface();
        view.setTypeface(tf, Typeface.ITALIC);
        view.setText(stringId);
        view.setOnClickListener(listener);
        parent.addView(view);
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private class GetListTask implements SimpleTask {
        /** Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data */
        private final boolean mIsFullRebuild;
        /** Resulting Cursor */
        BooklistPseudoCursor mTempList = null;
        /** used to determine new cursor position */
        ArrayList<BookRowInfo> mTargetRows = null;

        /**
         * Constructor.
         *
         * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
         */
        GetListTask(final boolean isFullRebuild) {
            if (!isFullRebuild && BuildConfig.DEBUG) {
                System.out.println("GetListTask constructor, isFullRebuild=false");
                Logger.printStackTrace();
            }
            //TOMF FIXME
            //mIsFullRebuild = isFullRebuild;
            mIsFullRebuild = true;
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            try {
                long t0 = System.currentTimeMillis();
                // Build the underlying data
                BooklistBuilder bookListBuilder = buildBooklist(mIsFullRebuild);
                long t1 = System.currentTimeMillis();
                // Try to sync the previously selected book ID
                if (mMarkBookId != 0) {
                    // get all positions of the book
                    mTargetRows = bookListBuilder.getBookAbsolutePositions(mMarkBookId);

                    if (mTargetRows != null && mTargetRows.size() > 0) {
                        // First, get the ones that are currently visible...
                        ArrayList<BookRowInfo> visRows = new ArrayList<>();
                        for (BookRowInfo i : mTargetRows) {
                            if (i.visible) {
                                visRows.add(i);
                            }
                        }
                        // If we have any visible rows, only consider them for the new position
                        if (visRows.size() > 0) {
                            mTargetRows = visRows;
                        } else {
                            // Make them ALL visible
                            for (BookRowInfo i : mTargetRows) {
                                if (!i.visible) {
                                    bookListBuilder.ensureAbsolutePositionVisible(i.absolutePosition);
                                }
                            }
                            // Recalculate all positions
                            for (BookRowInfo i : mTargetRows) {
                                i.listPosition = bookListBuilder.getPosition(i.absolutePosition);
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
                    mTargetRows = null;
                }
                long t2 = System.currentTimeMillis();

                // Now we have expanded groups as needed, get the list cursor
                mTempList = bookListBuilder.getList();

                // Clear it so it won't be reused.
                mMarkBookId = 0;

                long t3 = System.currentTimeMillis();

                // get a count() from the cursor in background task because the setAdapter() call
                // will do a count() and potentially block the UI thread while it pages through the
                // entire cursor. If we do it here, subsequent calls will be fast.
                @SuppressWarnings("UnusedAssignment")
                int count = mTempList.getCount();

                long t4 = System.currentTimeMillis();

                mUniqueBooks = mTempList.getUniqueBookCount();
                long t5 = System.currentTimeMillis();

                mTotalBooks = mTempList.getBookCount();
                long t6 = System.currentTimeMillis();

                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    System.out.println("Build: " + (t1 - t0));
                    System.out.println("Position: " + (t2 - t1));
                    System.out.println("Select: " + (t3 - t2));
                    System.out.println("Count(" + count + "): " + (t4 - t3) + "/" + (t5 - t4) + "/" + (t6 - t5));
                    System.out.println("====== ");
                    System.out.println("Total: " + (t6 - t0));
                }
                // Save a flag to say list was loaded at least once successfully
                mListHasBeenLoaded = true;

            } finally {
                if (taskContext.isTerminating()) {
                    // onFinish() will not be called, and we can discard our
                    // work...
                    if (mTempList != null && mTempList != mList) {
                        if (mList == null || mTempList.getBuilder() != mList.getBuilder()) {
                            try {
                                mTempList.getBuilder().close();
                            } catch (Exception ignore) {
                            }
                        }
                        try {
                            mTempList.close();
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
                mTempList.close();
                return;
            }
            // Dismiss the progress dialog, if present
            if (mListDialog != null && !mTaskQueue.hasActiveTasks()) {
                mListDialog.dismiss();
                mListDialog = null;
            }
            // Update the data
            if (mTempList != null) {
                displayList(mTempList, mTargetRows);
            }
            mTempList = null;
        }

    }

    /**
     * Build the underlying flattened list of books.
     *
     * @param isFullRebuild Indicates a complete structural rebuild is required
     *
     * @return The BooklistBuilder object used to build the data
     */
    private BooklistBuilder buildBooklist(final boolean isFullRebuild) {
        // If not a full rebuild then just use the current builder to re-query the underlying data
        if (mList != null && !isFullRebuild) {
            BooklistBuilder b = mList.getBuilder();
            b.rebuild();
            return b;
        } else {
            // Make sure we have a style chosen
            if (mCurrentStyle == null) {
                mCurrentStyle = getBookshelfStyle();
            }

            // get a new builder and add the required extra domains
            BooklistBuilder builder = new BooklistBuilder(mDb, mCurrentStyle);

            builder.requireDomain(DatabaseDefinitions.DOM_TITLE,
                    DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_TITLE), true);
            builder.requireDomain(DatabaseDefinitions.DOM_BOOK_READ,
                    DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_BOOK_READ), false);

            // Build based on our current criteria and return
            builder.build(mRebuildState, mMarkBookId, mCurrentBookshelf, "", "", "", "", mSearchText);

            // After first build, always preserve this object state
            mRebuildState = BooklistPreferencesActivity.BOOKLISTS_STATE_PRESERVED;

            return builder;
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
