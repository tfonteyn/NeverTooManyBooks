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
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler.BooklistChangeListener;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStylePropertiesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.BooklistStylesActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogMenuItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf extends BaseActivity implements BooklistChangeListener {
    /** Is book info opened in read-only mode. */
    public static final String PREF_OPEN_BOOK_READ_ONLY = "App.OpenBookReadOnly";
    /** Prefix used in preferences for this activity */
    private final static String TAG = "BooksOnBookshelf";
    /** Preference name for the bookshelf to load next time we startup */
    public final static String PREF_BOOKSHELF = TAG + ".BOOKSHELF";
    /** Preference name */
    private final static String PREF_TOP_ROW = TAG + ".TOP_ROW";
    /** Preference name */
    private final static String PREF_TOP_ROW_TOP = TAG + ".TOP_ROW_TOP";
    /** Preference name for the style to use */
    private final static String PREF_LIST_STYLE = TAG + ".LIST_STYLE";
    /** Preference name for the style to use specific to a bookshelf */
    private final static String PREF_LIST_STYLE_FOR_BOOKSHELF = TAG + ".LIST_STYLE.BOOKSHELF." /* + name of shelf */;

    /** Task queue to get book lists in background */
    private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("BoB-List", 1);
    /** Currently selected list style */
    private BooklistStyle mCurrentStyle = null;
    /** Currently selected bookshelf */
    @Nullable
    private String mCurrentBookshelf = "";
    /** Options indicating activity has been destroyed. Used for background tasks */
    private boolean mIsDead = false;
    /** Options to indicate that a list has been successfully loaded -- affects the way we save state */
    private boolean mListHasBeenLoaded = false;
    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTop = -1;
    /** ProgressDialog used to display "Getting books...". Needed here so we can dismiss it on close. */
    @Nullable
    private ProgressDialog mListDialog = null;
    /** A book ID used for keeping/updating current list position, eg. when a book is viewed/edited. */
    private long mPreviouslySelectedBookId = 0;
    /** Text to use in search query */
    @Nullable
    private String mSearchText = "";
    /** Saved position of last top row */
    private int mTopRow = 0;
    /** Saved position of last top row offset from view top */
    private int mTopRowTop = 0;
    /** Database connection */
    private CatalogueDBAdapter mDb;
    /** Handler to manage all Views on the list */
    private BooksMultiTypeListHandler mListHandler;
    /** Current displayed list cursor */
    private BooklistPseudoCursor mList;
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
    private MenuHandler mMenuHandler;

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
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
                mRebuildState = BooklistPreferencesActivity.BOOK_LIST_STATE_PRESERVED;
            }

            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            // Restore bookshelf and position
            mCurrentBookshelf = getPrefs().getString(PREF_BOOKSHELF, mCurrentBookshelf);
            mTopRow = getPrefs().getInt(PREF_TOP_ROW, 0);
            mTopRowTop = getPrefs().getInt(PREF_TOP_ROW_TOP, 0);

            // Restore view style
            refreshStyle();

            // set the search capability to local (application) search
            setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
            initSearch();

            initListItemMenus();

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
            mPreviouslySelectedBookId = -1;

            initBookshelfSpinner();
            populateBookShelfSpinner();

            initBookList(true);

            if (savedInstanceState == null) {
                initHints();
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
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
    @CallSuper
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
                    Logger.error(e);
                }
                mList.close();
            }
            mDb.close();
        } catch (Exception e) {
            Logger.error(e);
        }
        mListHandler = null;
        mAdapter = null;
        mBookshelfSpinner = null;
        mBookshelfAdapter = null;
        if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
            TrackedCursor.dumpCursors();
        }
        Tracker.exitOnDestroy(this);
    }

    /**
     * Handle selections from context menu
     */
    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        mList.moveToPosition(info.position);

        return mListHandler.onContextItemSelected(mDb, info.targetView, mList.getRowView(), this, item.getItemId())
                || super.onContextItemSelected(item);
    }

    /**
     * Runs each time the menu button is pressed. This will setup the options menu
     */
    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        mMenuHandler = new MenuHandler(menu);
        mMenuHandler.addCreateBookSubMenu(menu);

        mMenuHandler.addItem(menu, R.id.MENU_SORT, R.string.sort_and_style_ellipsis, R.drawable.ic_sort_by_alpha)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        mMenuHandler.addItem(menu, R.id.MENU_EXPAND, R.string.menu_expand_all, R.drawable.ic_expand_more);

        mMenuHandler.addItem(menu, R.id.MENU_COLLAPSE, R.string.menu_collapse_all, R.drawable.ic_expand_less);

        return super.onPrepareOptionsMenu(menu);
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
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                HintManager.displayHint(this, R.string.hint_booklist_style_menu, new Runnable() {
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
                    mList.getBuilder().expandAll(true);
                    mTopRow = mList.getBuilder().getPosition(oldAbsPos);
                    displayList(mList.getBuilder().getList(), null);
                }
                return true;
            }
            case R.id.MENU_COLLAPSE: {
                // It is possible that the list will be empty, if so, ignore
                if (getListView().getChildCount() != 0) {
                    int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                    savePosition();
                    mList.getBuilder().expandAll(false);
                    mTopRow = mList.getBuilder().getPosition(oldAbsPos);
                    displayList(mList.getBuilder().getList(), null);
                }
                return true;
            }
        }

        if (mMenuHandler != null) {
            boolean handled = mMenuHandler.onOptionsItemSelected(this, item);
            if (handled) {
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when an activity launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     */
    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        mPreviouslySelectedBookId = 0;

        switch (requestCode) {
            case BookSearchActivity.REQUEST_CODE_SCAN:
            case BookSearchActivity.REQUEST_CODE_SEARCH:
            case EditBookActivity.REQUEST_CODE:
            case BookDetailsActivity.REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        setPreviouslySelectedBookId(data);
                        // Always rebuild, even after a cancelled edit because there might have been global edits
                        // ENHANCE: Allow detection of global changes to avoid unnecessary rebuilds
                        this.initBookList(false);
                    } catch (Exception e) {
                        Logger.error(e);
                    }
                }
                break;

            case BooklistStylePropertiesActivity.REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        if (data != null && data.hasExtra(BooklistStylePropertiesActivity.REQUEST_KEY_STYLE)) {
                            BooklistStyle style = (BooklistStyle) data.getSerializableExtra(BooklistStylePropertiesActivity.REQUEST_KEY_STYLE);
                            if (style != null) {
                                mCurrentStyle = style;
                            }
                        }
                    } catch (Exception e) {
                        Logger.error(e);
                    }
                    this.savePosition();
                    this.initBookList(true);
                }
                break;

            case PreferencesActivity.REQUEST_CODE:
                // no action needed for now
                break;

            case EditBookshelvesActivity.REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    // bookshelves modified
                    //TODO: what to do ?
                }
                break;

            case BooklistPreferencesActivity.REQUEST_CODE:
            case BooklistStylesActivity.REQUEST_CODE:
            case AdministrationFunctions.REQUEST_CODE:
                // modified?
                if (resultCode == RESULT_OK) {
                    refreshStyle();
                    this.savePosition();
                    this.initBookList(true);
                }
                break;
        }
        Tracker.exitOnActivityResult(this, requestCode, resultCode);
    }

    private void setPreviouslySelectedBookId(final @Nullable Intent data) {
        if (data != null && data.hasExtra(UniqueId.KEY_ID)) {
            long newId = data.getLongExtra(UniqueId.KEY_ID, 0);
            if (newId != 0) {
                mPreviouslySelectedBookId = newId;
            }
        }
    }

    /**
     * Support routine now that this activity is no longer a ListActivity
     */
    @NonNull
    private ListView getListView() {
        return (ListView) findViewById(android.R.id.list);
    }

    /**
     * Display the passed cursor in the ListView, and change the position to targetRow.
     *
     * @param newList    New cursor to use
     * @param targetRows if set, change the position to targetRow.
     */
    private void displayList(@NonNull final BooklistPseudoCursor newList, @Nullable final ArrayList<BookRowInfo> targetRows) {

        final int showHeaderFlags = (mCurrentStyle == null ? BooklistStyle.SUMMARY_SHOW_ALL : mCurrentStyle.getShowHeaderInfo());

        populateBookCountField(showHeaderFlags);

        long t0;
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            //noinspection UnusedAssignment
            t0 = System.currentTimeMillis();
        }

        // Save the old list so we can close it later, and set the new list locally
        BooklistPseudoCursor oldList = mList;
        mList = newList;

        // Get new handler and adapter since list may be radically different structure
        mListHandler = new BooksMultiTypeListHandler();
        mAdapter = new MultiTypeListCursorAdapter(this, mList, mListHandler);

        // Get the ListView and set it up
        final ListView listView = getListView();
        final ListViewHolder lvHolder = new ListViewHolder();
        ViewTagger.setTag(listView, R.id.TAG_HOLDER, lvHolder);

        listView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of ListView
        listView.setFastScrollEnabled(false);
        listView.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mList.getCount();
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
        // once we know how many items appear in a typical view and once we can tell
        // if it is already in the view.
        if (targetRows != null) {
            postRunnableToFixPositionOnceControlDrawn(listView, targetRows);
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
        listView.setOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScroll(@NonNull final AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
                        // TODO: Investigate why BooklistPseudoCursor causes a scroll even when it is closed!
                        // Need to check isDead because BooklistPseudoCursor misbehaves when activity
                        // terminates and closes cursor
                        if (mLastTop != firstVisibleItem && !mIsDead && (showHeaderFlags != 0)) {
                            ListViewHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_HOLDER);
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
            if (mList.getBuilder() != oldList.getBuilder()) {
                oldList.getBuilder().close();
            }
            oldList.close();
        }
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info("displayList: " + (System.currentTimeMillis() - t0));
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
                        this.getString(R.string.displaying_n_books, mUniqueBooks)));
            }
            bookCounts.setVisibility(View.VISIBLE);
        } else {
            bookCounts.setVisibility(View.GONE);
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
                if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                    Logger.info("New List: (" + first + ", " + last + ")<-" + centre);
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

                if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                    Logger.info("Best @" + best.listPosition);
                }
                // Try to put at top if not already visible, or only partially visible
                if (first >= best.listPosition || last <= best.listPosition) {
                    if (DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF && BuildConfig.DEBUG) {
                        Logger.info("Adjusting position");
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
                    //Logger.info("New Top @" + newTop );
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
    private void updateListHeader(@NonNull final ListViewHolder holder, int topItem, final boolean hasLevel1, final boolean hasLevel2, final int flags) {
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

        final SharedPreferences.Editor ed = getPrefs().edit();

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

        ed.apply();
    }

    private void initSearch() {
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
    private void initListItemMenus() {

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long rowId) {
                // Move the cursor to the position
                mList.moveToPosition(position);

                // If it's a book, view or edit it.
                int kind = mList.getRowView().getRowKind();
                if (kind == RowKinds.ROW_KIND_BOOK) {
                    long bookId = mList.getRowView().getBookId();
                    boolean readOnly = getPrefs().getBoolean(PREF_OPEN_BOOK_READ_ONLY, true);

                    if (readOnly) {
                        String listTable = mList.getBuilder().createFlattenedBooklist().getTable().getName();
                        Intent intent = new Intent(BooksOnBookshelf.this, BookDetailsActivity.class);
                        intent.putExtra(UniqueId.KEY_ID, bookId);
                        intent.putExtra(BookDetailsActivity.REQUEST_KEY_FLATTENED_BOOKLIST, listTable);
                        intent.putExtra(BookDetailsActivity.REQUEST_KEY_FLATTENED_BOOKLIST_POSITION, position);
                        startActivityForResult(intent, BookDetailsActivity.REQUEST_CODE);

                    } else {
                        EditBookActivity.startActivityForResult(BooksOnBookshelf.this, bookId, EditBookActivity.TAB_EDIT);
                    }
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
            public boolean onItemLongClick(final AdapterView<?> parent, @NonNull final View view, final int position, final long id) {
                mList.moveToPosition(position);
                List<SimpleDialogItem> menu = new ArrayList<>();
                mListHandler.buildContextMenu(mList.getRowView(), menu);

                if (menu.size() > 0) {
                    StandardDialogs.selectItemDialog(getLayoutInflater(),
                            null, menu, null,
                            new SimpleDialogOnClickListener() {
                                @Override
                                public void onClick(@NonNull final SimpleDialogItem item) {
                                    mList.moveToPosition(position);
                                    int id = ((SimpleDialogMenuItem) item).getItemId();

                                    mListHandler.onContextItemSelected(mDb, view, mList.getRowView(),
                                            BooksOnBookshelf.this, id);
                                }
                            });
                }
                return true;
            }
        });
    }

    private void initHints() {
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
     * Setup the bookshelf spinner. This function will also call fillData when
     * complete having loaded the appropriate bookshelf.
     */
    private void initBookshelfSpinner() {
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
            public void onItemSelected(final AdapterView<?> parentView, final View view, final int position, final long id) {
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
                    // make the new shelf the current, and get it's preferred style
                    mCurrentBookshelf = new_bookshelf;
                    mCurrentStyle = getBookshelfStyle();

                    // save the new bookshelf/style into the preferences for next app start
                    SharedPreferences.Editor ed = getPrefs().edit();
                    ed.putString(PREF_BOOKSHELF, mCurrentBookshelf);
                    ed.putString(PREF_LIST_STYLE_FOR_BOOKSHELF + mCurrentBookshelf, mCurrentStyle.getCanonicalName());
                    ed.apply();

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
     * setup/refresh the BookShelf list in the Spinner
     */
    private void populateBookShelfSpinner() {
        mBookshelfAdapter.clear();
        // Add the default All Books bookshelf
        mBookshelfAdapter.add(getString(R.string.all_books));
        int currentPos = 0;
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.name.equals(mCurrentBookshelf)) {
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
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
     */
    private void initBookList(final boolean isFullRebuild) {
        mTaskQueue.enqueue(new GetListTask(isFullRebuild));
        if (mListDialog == null) {
            mListDialog = ProgressDialog.show(this,
                    "",
                    getString(R.string.getting_books_ellipsis),
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
     * Build the underlying flattened list of books.
     *
     * @param isFullRebuild Indicates a complete structural rebuild is required
     *
     * @return The BooklistBuilder object used to build the data
     */
    @NonNull
    private BooklistBuilder buildBooklist(final boolean isFullRebuild) {
        // If not a full rebuild then just use the current builder to re-query the underlying data
        if (mList != null && !isFullRebuild) {
            BooklistBuilder builder = mList.getBuilder();
            builder.rebuild();
            return builder;
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
            builder.build(mRebuildState, mPreviouslySelectedBookId, mCurrentBookshelf, "", "", "", "", mSearchText);

            // After first build, always preserve this object state
            mRebuildState = BooklistPreferencesActivity.BOOK_LIST_STATE_PRESERVED;

            return builder;
        }
    }

    /**
     * @param flags bitmask
     */
    @Override
    public void onBooklistChange(final int flags) {
        if (flags != 0) {
            //Something changed. Just regenerate. FIXME: rather brutal; check the flags and see what can be done to avoid full rebuild?
            savePosition();
            this.initBookList(true);
        }
    }

    //<editor-fold desc="Booklist Style operations">

    /**
     * get the dedicated style for our bookshelf, or the global one.
     *
     * @see #getBookshelfStyle(BooklistStyles styles)
     */
    @NonNull
    private BooklistStyle getBookshelfStyle() {
        return getBookshelfStyle(BooklistStyles.getAllStyles(mDb));
    }

    /**
     * get the dedicated style for our bookshelf, or the global one.
     *
     * @see #getBookshelfStyle()
     */
    @NonNull
    private BooklistStyle getBookshelfStyle(BooklistStyles styles) {
        String styleName = BookCatalogueApp.Prefs.getString(
                PREF_LIST_STYLE_FOR_BOOKSHELF + mCurrentBookshelf,
                BookCatalogueApp.Prefs.getString(PREF_LIST_STYLE, getString(R.string.sort_author_series)));

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
            style = getBookshelfStyle(styles);
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
                .setTitle(R.string.select_style)
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
        int moreOrLess = showAll ? R.string.show_fewer_ellipsis : R.string.show_more_ellipsis;
        addStyleTextMenuItem(menu, inf, moreOrLess, new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                doSortMenu(!showAll);
            }
        });
        addStyleTextMenuItem(menu, inf, R.string.customize_ellipsis, new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent intent = new Intent(BooksOnBookshelf.this, BooklistStylesActivity.class);
                startActivityForResult(intent, BooklistStylesActivity.REQUEST_CODE);
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
        CompoundButton btn = (CompoundButton) inf.inflate(R.layout.booklist_style_menu_radio, dialog.getListView());
        btn.setText(style.getDisplayName());

        if (mCurrentStyle.getCanonicalName().equalsIgnoreCase(style.getCanonicalName())) {
            btn.setChecked(true);
        } else {
            btn.setChecked(false);
        }
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
    private void addStyleTextMenuItem(@NonNull final ViewGroup parent,
                                      @NonNull final LayoutInflater inf,
                                      @StringRes final int stringId,
                                      @NonNull final OnClickListener listener) {
        @SuppressLint("InflateParams") // it's a dialog -> root==null
                TextView view = (TextView) inf.inflate(R.layout.booklist_style_menu_text, null);

        Typeface tf = view.getTypeface();
        view.setTypeface(tf, Typeface.ITALIC);
        view.setText(stringId);
        view.setOnClickListener(listener);
        parent.addView(view);
    }

    /**
     * Handle the style that a user has selected.
     *
     * @param name Name of the selected style
     */
    private void onStyleSelected(@NonNull final String name) {
        // Find the style, if no match warn user and exit
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        BooklistStyle style = styles.findCanonical(name);
        if (style == null) {
            StandardDialogs.showBriefMessage(this, R.string.no_style_found);
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
        initBookList(true);
    }
    //</editor-fold>


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
        @Nullable
        ArrayList<BookRowInfo> mTargetRows = null;

        /**
         * Constructor.
         *
         * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
         */
        GetListTask(final boolean isFullRebuild) {
            if (!isFullRebuild && BuildConfig.DEBUG) {
                Logger.info("GetListTask constructor, isFullRebuild=false");
            }
            //TOMF FIXME
            //mIsFullRebuild = isFullRebuild;
            mIsFullRebuild = true;
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
                BooklistBuilder bookListBuilder = buildBooklist(mIsFullRebuild);
                long t1;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t1 = System.currentTimeMillis();
                }
                // Try to sync the previously selected book ID
                if (mPreviouslySelectedBookId != 0) {
                    // get all positions of the book
                    mTargetRows = bookListBuilder.getBookAbsolutePositions(mPreviouslySelectedBookId);

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
                            for (BookRowInfo rowInfo : mTargetRows) {
                                if (!rowInfo.visible) {
                                    bookListBuilder.ensureAbsolutePositionVisible(rowInfo.absolutePosition);
                                }
                            }
                            // Recalculate all positions
                            for (BookRowInfo rowInfo : mTargetRows) {
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
                    mTargetRows = null;
                }
                long t2;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t2 = System.currentTimeMillis();
                }

                // Now we have expanded groups as needed, get the list cursor
                mTempList = bookListBuilder.getList();

                // Clear it so it won't be reused.
                mPreviouslySelectedBookId = 0;

                long t3;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t3 = System.currentTimeMillis();
                }

                // get a count() from the cursor in background task because the setAdapter() call
                // will do a count() and potentially block the UI thread while it pages through the
                // entire cursor. If we do it here, subsequent calls will be fast.
                @SuppressWarnings("UnusedAssignment")
                int count = mTempList.getCount();

                long t4;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t4 = System.currentTimeMillis();
                }

                mUniqueBooks = mTempList.getUniqueBookCount();
                long t5;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t5 = System.currentTimeMillis();
                }

                mTotalBooks = mTempList.getBookCount();
                long t6;
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    t6 = System.currentTimeMillis();
                }

                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info("Build: " + (t1 - t0));
                    Logger.info("Position: " + (t2 - t1));
                    Logger.info("Select: " + (t3 - t2));
                    Logger.info("Count(" + count + "): " + (t4 - t3) + "/" + (t5 - t4) + "/" + (t6 - t5));
                    Logger.info("====== ");
                    Logger.info("Total: " + (t6 - t0));
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
