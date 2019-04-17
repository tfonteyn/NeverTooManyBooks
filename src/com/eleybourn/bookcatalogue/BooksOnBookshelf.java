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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
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

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.PopupMenuDialog;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.searches.FTSSearchActivity;
import com.eleybourn.bookcatalogue.settings.BooklistStyleSettingsFragment;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf
        extends BaseActivity
        implements
        BookChangedListener,
        ProgressDialogFragment.OnTaskFinishedListener {

    /** Preference name - Saved position of last top row. */
    public static final String PREF_BOB_TOP_ROW = "BooksOnBookshelf.TopRow";
    /** Preference name - Saved position of last top row offset from view top. */
    public static final String PREF_BOB_TOP_ROW_OFFSET = "BooksOnBookshelf.TopRowOffset";
    /**
     * Set to true to enable true rebuild for debugging. See {@link #initBookList(boolean)}.
     */
    private static final boolean __DEBUG_THE_REBUILD_ISSUE = false;
    /** Activity Request Code. */
    private static final int REQ_ADVANCED_LOCAL_SEARCH = 11;

    /** Holder for all (semi)supported search criteria. See {@link SearchCriteria} for more info. */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();

    /** Flag (potentially) set in {@link #onActivityResult}. Indicates if list rebuild is needed.*/
    @Nullable
    private Boolean mAfterOnActivityResultDoFullRebuild;
    /** Flag to indicate that a list has been successfully loaded. Affects the way we save state. */
    private boolean mListHasBeenLoaded;
    /** Stores the book id for the current list position, e.g. while a book is viewed/edited. */
    private long mCurrentPositionedBookId;
    /** Currently selected bookshelf. */
    private Bookshelf mCurrentBookshelf;
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
    /** The View for the list. */
    private ListView mListView;
    /** Handler to manage all Views on the list. */
    private BooksMultiTypeListHandler mListHandler;
    /** The database. */
    private DBA mDb;
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
    /** The dropdown button to select a Bookshelf. */
    private Spinner mBookshelfSpinner;
    /** The adapter used to fill the mBookshelfSpinner. */
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
            mSearchCriteria.from(savedInstanceState);
            // Always preserve state when rebuilding/recreating etc
            mRebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
        }

        mDb = new DBA(this);

        // Restore bookshelf
        mCurrentBookshelf = Bookshelf.getPreferred(mDb);
        // Restore list position on bookshelf
        mTopRow = App.getPrefs().getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = App.getPrefs().getInt(PREF_BOB_TOP_ROW_OFFSET, 0);


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

        mListView = findViewById(android.R.id.list);
        // use the custom fast scroller (the ListView in the XML is our custom version).
        mListView.setFastScrollEnabled(true);
        mListView.setOnItemClickListener(this::onItemClick);
        mListView.setOnItemLongClickListener(this::onItemLongClick);

//            FloatingActionButton floatingAddButton = findViewById(R.id.floatingAddButton);
//            floatingAddButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(@NonNull final View v) {
//
//                }
//            });
//            floatingAddButton.show();

        // Handle item click events

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;

        initBookshelfSpinner();

        if (savedInstanceState == null) {
            initHints();
        }

        // populating the spinner and loading the list is done in onResume.
        Tracker.exitOnCreate(this);
    }

    /**
     * Update the activity title from the current style name.
     */
    private void setActivityTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mCurrentBookshelf.getStyle(mDb).getDisplayName(this));
            actionBar.setSubtitle(null);
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (App.isRecreating()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit("onResume",
                                 "isRecreating",
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
            if (BuildConfig.DEBUG) {
                Logger.debugExit(this, "onResume",
                                 "isFinishing=" + isFinishing(),
                                 "isDestroyed=" + isDestroyed());
            }
            return;
        }

        setActivityTitle();
        populateBookShelfSpinner();

        // mAfterOnActivityResultDoFullRebuild may be set in onActivityResult
        if (mAfterOnActivityResultDoFullRebuild == null) {
            // if not set, take no chances, do a full rebuild.
            initBookList(true);
        } else {
            initBookList(mAfterOnActivityResultDoFullRebuild);
            // reset for next iteration.
            mAfterOnActivityResultDoFullRebuild = null;
        }

        Tracker.exitOnResume(this);
    }

    /**
     * Save position when paused.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        if (mSearchCriteria.isEmpty()) {
            savePosition();
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        mSearchCriteria.to(outState);
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
        if (mListHasBeenLoaded) {
            mTopRow = mListView.getFirstVisiblePosition();
            View v = mListView.getChildAt(0);
            mTopRowOffset = v == null ? 0 : v.getTop();

            App.getPrefs().edit()
               .putInt(PREF_BOB_TOP_ROW, mTopRow)
               .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
               .apply();
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mListCursor != null) {
            mListCursor.getBuilder().close();
            mListCursor.close();
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

    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        mSearchCriteria.to(intent);
        startActivityForResult(intent, REQ_ADVANCED_LOCAL_SEARCH);
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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SHOW_DEBUG_MENU) {
            SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_DEBUG, R.id.SUBMENU_DEBUG,
                                              0, R.string.debug);

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
                mSearchCriteria.clear();
                initBookList(true);
                return true;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SHOW_DEBUG_MENU) {
                    switch (item.getItemId()) {
                        case R.id.MENU_DEBUG_DUMP_PREFS:
                            Prefs.dumpPreferences(null);
                            return true;

                        case R.id.MENU_DEBUG_DUMP_STYLE:
                            Logger.debug(this,
                                         "onOptionsItemSelected",
                                         mCurrentBookshelf.getStyle(mDb));
                            return true;

                        case R.id.MENU_DEBUG_DUMP_TRACKER:
                            Logger.debug(this, "onOptionsItemSelected", Tracker.getEventsInfo());
                            return true;

                        case R.id.MENU_DEBUG_EXPORT_DATABASE:
                            StorageUtils.exportDatabaseFiles();
                            UserMessage.showUserMessage(this, R.string.progress_end_backup_success);
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
     * @param expand <tt>true</tt> to expand, <tt>false</tt> to collapse
     */
    private void expandNode(final boolean expand) {
        // It is possible that the list will be empty, if so, ignore
        if (mListView.getChildCount() != 0) {
            int oldAbsPos = mListHandler.getAbsolutePosition(mListView.getChildAt(0));
            savePosition();
            mListCursor.getBuilder().expandAll(expand);
            mTopRow = mListCursor.getBuilder().getPosition(oldAbsPos);
            displayList(mListCursor.getBuilder().getList(), null);
        }
    }

    /**
     * The user clicked on a row:
     * - Book: open the details screen.
     * - expand/collapse the section as appropriate.
     *
     * @param position in the list that was clicked.
     */
    public void onItemClick(@NonNull final AdapterView<?> parent,
                            @NonNull final View view,
                            final int position,
                            final long id) {
        mListCursor.moveToPosition(position);

        switch (mListCursor.getCursorRow().getRowKind()) {
            // If it's a book, view or edit it.
            case BooklistGroup.RowKind.BOOK:
                long bookId = mListCursor.getCursorRow().getBookId();
                boolean openInReadOnly =
                        App.getPrefs().getBoolean(Prefs.pk_bob_open_book_read_only, true);

                if (openInReadOnly) {
                    String listTable = mListCursor.getBuilder()
                                                  .createFlattenedBooklist()
                                                  .getTable()
                                                  .getName();

                    Intent intent = new Intent(this,
                                               BookDetailsActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, bookId)
                            .putExtra(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST, listTable)
                            .putExtra(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST_POSITION, position);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_VIEW);

                } else {
                    Intent intent = new Intent(this,
                                               EditBookActivity.class)
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
                if (mListCursor.getCursorRow().getLevel() == 1) {
                    mListCursor.getBuilder()
                               .toggleExpandNode(mListCursor.getCursorRow().getAbsolutePosition());
                    mListCursor.requery();
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
    private boolean onItemLongClick(@NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id) {
        mListCursor.moveToPosition(position);

        BooklistCursorRow cursorRow = mListCursor.getCursorRow();
        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(view.getContext(), null).getMenu();
        // build/check the menu for this row
        if (mListHandler.prepareListViewContextMenu(menu, cursorRow)) {
            // we have a menu to show
            int level = cursorRow.getLevel();
            String menuTitle;
            if (cursorRow.getStyle().groupCount() < level) {
                // it's a book (level 3 or 4)
                menuTitle = cursorRow.getTitle();
            } else {
                // it's a group (level 1,2,3)
                menuTitle = cursorRow.getLevelText(level);
            }
            // bring up the context menu
            PopupMenuDialog.onCreateListViewContextMenu(
                    view.getContext(), position, menuTitle, menu,
                    (menuItem, pos) -> {
                        mListCursor.moveToPosition(pos);
                        return mListHandler.onContextItemSelected(menuItem,
                                                                  mDb, mListCursor.getCursorRow(),
                                                                  BooksOnBookshelf.this);
                    });
        }
        return true;
    }

    /**
     * Called when an activity launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * <p>
     * Reminder: don't do any commits on the fragment manager.
     * This includes showing fragments, or starting tasks that show fragments.
     * Do this in {@link #onResume} which will be called after onActivityResult.
     * <p>
     * Note: not tested to destruction, but it certainly was correct for starting a task
     * with a fragment (for showing progress).
     * <p>
     * See:
     * https://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
     * https://stackoverflow.com/questions/16265733/failure-delivering-result-onactivityforresult
     * Quoting:
     * Calling commitAllowingStateLoss() is more of a hack than a fix. State loss is bad and
     * should be avoided at all costs. At the time that onActivityResult() is called, the
     * activity/fragment's state may not yet have been restored, and therefore any transactions
     * that happen during this time will be lost as a result. This is a very important bug which
     * must be addressed! (Note that the bug only happens when your Activity is coming back after
     * having been killed by the system... which, depending on how much memory the device has,
     * can sometimes be rare... so this sort of bug is not something that is very easy to catch
     * while testing).
     * <p>
     * Try moving your transactions into onPostResume() instead (note that onPostResume() is
     * always called after onResume() and onResume() is always called after onActivityResult()):
     * => onResume works equally well.
     */
    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        mCurrentPositionedBookId = 0;

        switch (requestCode) {
            case UniqueId.REQ_BOOK_VIEW:
            case UniqueId.REQ_BOOK_EDIT:
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                        // handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the ID 'above' the deleted one;
                        mAfterOnActivityResultDoFullRebuild = false;
                        break;

                    case Activity.RESULT_OK:
                        //noinspection ConstantConditions
                        long newId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                        if (newId != 0) {
                            mCurrentPositionedBookId = newId;
                        }
                        mAfterOnActivityResultDoFullRebuild = false;
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
                            mCurrentPositionedBookId = newId;
                        }
                    }
                    // regardless, do a rebuild just in case
                    mAfterOnActivityResultDoFullRebuild = false;
                }
                break;

            case REQ_ADVANCED_LOCAL_SEARCH:
                // no changes made, but we might have data to act on.
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Bundle extras = data.getExtras();
                        if (extras != null) {
                            mSearchCriteria.from(extras);
                        }
                        mAfterOnActivityResultDoFullRebuild = true;
                    }
                }
                break;

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (resultCode == Activity.RESULT_OK) {
                    // the last edited/inserted shelf
                    //noinspection ConstantConditions
                    long bookshelfId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                    mCurrentBookshelf = mDb.getBookshelf(bookshelfId);

                    // bookshelves modified, update everything
                    initBookshelfSpinner();
                    mAfterOnActivityResultDoFullRebuild = true;
                }
                break;

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (resultCode == Activity.RESULT_OK) {
                    if ((data != null) && data.hasExtra(UniqueId.BKEY_IMPORT_RESULT)) {
                        // BackupAndRestoreActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                       ImportSettings.NOTHING);

                        if ((options & ImportSettings.PREFERENCES) != 0) {
                            // the imported prefs could have a different preferred bookshelf.
                            Bookshelf newBookshelf = Bookshelf.getPreferred(mDb);
                            if (!mCurrentBookshelf.equals(newBookshelf)) {
                                // if it was.. then switch to it.
                                mCurrentBookshelf = newBookshelf;
                                mAfterOnActivityResultDoFullRebuild = true;
                            }
                        }
                    } else if ((data != null) && data.hasExtra(UniqueId.BKEY_EXPORT_RESULT)) {
                        // BackupAndRestoreActivity:
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
                        mAfterOnActivityResultDoFullRebuild = true;
                        break;

                    case UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE:
                        //noinspection ConstantConditions
                        BooklistStyle style = data.getParcelableExtra(
                                BooklistStyleSettingsFragment.REQUEST_BKEY_STYLE);
                        // can be null if a style was deleted.
                        if (style != null) {
                            // save the new bookshelf/style combination
                            mCurrentBookshelf.setAsPreferred();
                            mCurrentBookshelf.setStyle(mDb, style);
                        }
                        mAfterOnActivityResultDoFullRebuild = true;
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            //noinspection UnusedAssignment
            t0 = System.nanoTime();
        }

        // Save the old list so we can close it later, and set the new list locally
        BooklistPseudoCursor oldList = mListCursor;
        mListCursor = newList;

        // Get new handler and adapter since list may be radically different structure
        mListHandler = new BooksMultiTypeListHandler(mDb);
        mAdapter = new MultiTypeListCursorAdapter(this, mListCursor, mListHandler);
        mListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of ListView
        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mListCursor.getCount();
        if (mTopRow >= count) {
            mTopRow = count - 1;
            mListView.setSelection(mTopRow);
        } else {
            mListView.setSelectionFromTop(mTopRow, mTopRowOffset);
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and we can tell
        // if it is already in the view.
        if (targetRows != null) {
            fixPositionWhenDrawn(mListView, targetRows);
        }

        // setup the row holder
        final ScrollingInfo scrollingInfo = new ScrollingInfo();
        for (int level = 1; level <= ScrollingInfo.MAX; level++) {
            scrollingInfo.setVisible(level,
                                     mListCursor.levels() > level
                                             && style.hasSummaryForLevel(level));
        }
        mListView.setTag(scrollingInfo);

        final boolean showScrollingInfo = (headersToShow
                & (BooklistStyle.SUMMARY_SHOW_LEVEL_1 ^ BooklistStyle.SUMMARY_SHOW_LEVEL_2)) != 0;

        // Set the initial 'level' details
        if (count > 0 && showScrollingInfo) {
            scrollingInfo.update(mTopRow);
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
                        if (mLastTopRow != firstVisibleItem
                                && !isDestroyed()
                                && showScrollingInfo) {
                            ScrollingInfo scrollingInfo = (ScrollingInfo) view.getTag();
                            scrollingInfo.update(firstVisibleItem);
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
            if (mListCursor.getBuilder() != oldList.getBuilder()) {
                oldList.getBuilder().close();
            }
            oldList.close();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.debugExit("displayList",
                             (System.nanoTime() - t0) + "nano");
        }
    }

    /**
     * Display the number of books in the current list.
     *
     * @param showHeaderFlags bitmask options on which fields to show.
     */
    private void populateBookCountField(final int showHeaderFlags) {
        final TextView bookCounts = findViewById(R.id.bookshelf_count);
        if ((showHeaderFlags & BooklistStyle.SUMMARY_SHOW_COUNT) != 0) {
            if (mUniqueBooks != mTotalBooks) {
                bookCounts.setText(getString(R.string.brackets,
                                             getString(
                                                     R.string.info_displaying_n_books_in_m_entries,
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
     *
     * @return the search text as we might have 'cleaned' it
     */
    @NonNull
    private String initSearchField(@Nullable final String searchText) {
        TextView searchTextView = findViewById(R.id.search_text);

        if (searchText == null || searchText.isEmpty() || ".".equals(searchText)) {
            searchTextView.setVisibility(View.GONE);
            return "";
        } else {
            searchTextView.setVisibility(View.VISIBLE);
            searchTextView.setText(getString(R.string.search_with_text, searchText));
            return searchText;
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
     * Setup the bookshelf spinner.
     * <p>
     * The spinner listener will set the style associated with the newly selected Bookshelf
     * and call {@link #initBookList}.
     */
    private void initBookshelfSpinner() {
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);
        // create, but do not populate here.
        mBookshelfAdapter = new ArrayAdapter<>(this, R.layout.booksonbookshelf_bookshelf_spinner);
        mBookshelfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfAdapter);

        mBookshelfSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            /**
             * Called when a bookshelf is selected. Set new current + rebuild the list.
             */
            @Override
            public void onItemSelected(@NonNull final AdapterView<?> parent,
                                       @NonNull final View view,
                                       final int position,
                                       final long id) {

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                    Logger.debugEnter(this, "mBookshelfSpinner onItemSelected",
                                      "pos=" + position);
                }

                String bsName = (String) parent.getItemAtPosition(position);
                if (bsName != null && !bsName.equalsIgnoreCase(mCurrentBookshelf.getName())) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                        Logger.debug(this,
                                     "mBookshelfSpinner onItemSelected",
                                     "spinning to shelf: " + bsName);
                    }

                    // make the new shelf the current
                    mCurrentBookshelf = mDb.getBookshelfByName(bsName);
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
        mBookshelfAdapter.add(getString(R.string.bookshelf_all_books));
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
            Logger.debugExit("populateBookShelfSpinner",
                             "mBookshelfSpinner.setSelection pos=" + currentPos);
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
                initBookList(false);

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
     * @param showAll if <tt>true</tt> show all styles, otherwise only the preferred ones.
     */
    private void doSortMenu(final boolean showAll) {
        // always create a new fragment. We only 'leave' it with a dismiss.
        SortMenuFragment.newInstance(showAll)
                        .show(getSupportFragmentManager(), SortMenuFragment.TAG);
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
        mTopRow = mListView.getFirstVisiblePosition();
        View view = mListView.getChildAt(0);
        mTopRowOffset = (view != null) ? view.getTop() : 0;

        // New style, so use the user-pref for rebuild
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
    private BooklistBuilder createBooklistBuilder() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debug(this,
                         "createBooklistBuilder",
                         mCurrentBookshelf.getStyle(mDb));
        }

        // get a new builder and add the required extra domains
        BooklistBuilder builder = new BooklistBuilder(this, mCurrentBookshelf.getStyle(mDb));

        builder.requireDomain(DBDefinitions.DOM_TITLE,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                              true);

        builder.requireDomain(DBDefinitions.DOM_BOOK_READ,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
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
        boolean fullRebuild;
        if (__DEBUG_THE_REBUILD_ISSUE) {
            fullRebuild = isFullRebuild;
        } else {
            fullRebuild = true;
        }

        BooklistBuilder bookListBuilder;
        // If not a full rebuild then just use the current builder to re-query the underlying data
        if (mListCursor != null && !fullRebuild) {
            bookListBuilder = mListCursor.getBuilder();
        } else {
            bookListBuilder = createBooklistBuilder();
        }

        BuilderHolder h = new BuilderHolder(mCurrentPositionedBookId, mListCursor, mRebuildState);
        GetBookListTask.start(getSupportFragmentManager(), h, fullRebuild,
                              bookListBuilder, mCurrentBookshelf.getId(), mSearchCriteria);
    }

    /**
     * Called when a task finishes.
     *
     * @param taskId  a task identifier
     * @param success <tt>true</tt> for success
     * @param result  BuilderHolder
     */
    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @Nullable final Object result) {

        if (taskId == R.id.TASK_ID_GET_BOOKLIST && result != null) {
            // Save a flag to say list was loaded at least once successfully (or not)
            mListHasBeenLoaded = success;

            if (mListHasBeenLoaded) {
                BuilderHolder h = (BuilderHolder) result;
                // always copy modified fields.
                mCurrentPositionedBookId = h.currentPositionedBookId;
                mListCursor = h.listCursor;
                mRebuildState = h.rebuildState;
                // always copy these results
                mTotalBooks = h.resultTotalBooks;
                mUniqueBooks = h.resultUniqueBooks;

                // check if we have a new list before using it.
                if (h.resultList != null) {
                    displayList(h.resultList, h.resultTargetRows);
                }
            }
        }
    }

    public static class SortMenuFragment
            extends DialogFragment {

        /** Fragment manager t. */
        public static final String TAG = SortMenuFragment.class.getSimpleName();

        private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";

        private boolean mShowAllStyles;
        private long mCurrentStyleId;

        /** The sort menu is tied to the main class, so might as well give full access. */
        private BooksOnBookshelf mActivity;

        static SortMenuFragment newInstance(final boolean showAllStyles) {
            SortMenuFragment frag = new SortMenuFragment();
            Bundle args = new Bundle();
            args.putBoolean(BKEY_SHOW_ALL_STYLES, showAllStyles);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            mActivity = (BooksOnBookshelf) requireActivity();

            mShowAllStyles = requireArguments().getBoolean(BKEY_SHOW_ALL_STYLES);
            mCurrentStyleId = mActivity.mCurrentBookshelf.getStyle(mActivity.mDb).getId();

            LayoutInflater inflater = mActivity.getLayoutInflater();
            @SuppressLint("InflateParams")
            View root = inflater.inflate(R.layout.dialog_styles_menu, null);

            // first section is a list of the styles
            ViewGroup viewGroup = root.findViewById(R.id.radio_buttons);
            for (BooklistStyle style : BooklistStyles.getStyles(mActivity.mDb,
                                                                mShowAllStyles).values()) {
                addStyleButtonMenuItem(inflater, viewGroup, style);
            }

            // second section are options to do 'more things then listed'
            ViewGroup menu = root.findViewById(R.id.menu);
            @StringRes
            int moreOrLess = mShowAllStyles ? R.string.menu_show_fewer_ellipsis
                                            : R.string.menu_show_more_ellipsis;

            addStyleTextMenuItem(
                    inflater, menu, moreOrLess, v -> {
                        dismiss();
                        mActivity.doSortMenu(!mShowAllStyles);
                    });

            addStyleTextMenuItem(
                    inflater, menu, R.string.menu_customize_ellipsis,
                    v -> {
                        dismiss();
                        Intent intent = new Intent(mActivity, PreferredStylesActivity.class);
                        startActivityForResult(intent,
                                               UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES);
                    });

            return new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.title_select_style)
                    .setView(root)
                    .create();
        }

        /**
         * Add a radio box to the sort options dialogue.
         *
         * @param inf        the inflater to use
         * @param radioGroup group to add the radio button to.
         * @param style      the style to be applied
         */
        private void addStyleButtonMenuItem(@NonNull final LayoutInflater inf,
                                            @NonNull final ViewGroup radioGroup,
                                            @NonNull final BooklistStyle style) {
            CompoundButton btn = (CompoundButton) inf.inflate(R.layout.row_style_menu_radio,
                                                              radioGroup, false);
            btn.setText(style.getDisplayName(inf.getContext()));
            btn.setChecked(mCurrentStyleId == style.getId());
            btn.setOnClickListener(v -> {
                mActivity.onStyleSelected(style);
                dismiss();
            });

            radioGroup.addView(btn);
        }

        /**
         * Add a text menu-item to the sort options dialog.
         *
         * @param inf       the inflater to use
         * @param viewGroup group to add the menu-item to
         * @param stringId  the text for this menu item
         * @param listener  to call when the item is clicked
         */
        private void addStyleTextMenuItem(@NonNull final LayoutInflater inf,
                                          @NonNull final ViewGroup viewGroup,
                                          @StringRes final int stringId,
                                          @NonNull final OnClickListener listener) {

            TextView textView = (TextView) inf.inflate(R.layout.row_style_menu_text,
                                                       viewGroup, false);
            Typeface tf = textView.getTypeface();
            textView.setTypeface(tf, Typeface.ITALIC);
            textView.setText(stringId);
            textView.setOnClickListener(listener);

            viewGroup.addView(textView);
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private static class GetBookListTask
            extends AsyncTask<Void, Object, BuilderHolder> {

        /** Fragment manager t. */
        private static final String TAG = GetBookListTask.class.getSimpleName();
        /** Generic identifier. */
        private static final int M_TASK_ID = R.id.TASK_ID_GET_BOOKLIST;
        @NonNull
        protected final ProgressDialogFragment<BuilderHolder> mFragment;
        /**
         * Indicates whole table structure needs rebuild,
         * versus just do a reselect of underlying data.
         */
        private final boolean mIsFullRebuild;
        /** the builder. */
        @NonNull
        private final BooklistBuilder mBooklistBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderHolder mHolder;
        /**
         * {@link #doInBackground} should catch exceptions, and set this field.
         * {@link #onPostExecute} can then check it.
         */
        @Nullable
        protected Exception mException;
        /** Resulting Cursor. */
        private BooklistPseudoCursor tempList;
        /** used to determine new cursor position. */
        @Nullable
        private ArrayList<BookRowInfo> targetRows;

        /**
         * Constructor.
         *
         * @param fragment        ProgressDialogFragment
         * @param builderHolder   holder class with input fields / results.
         * @param isFullRebuild   Indicates whole table structure needs rebuild,
         *                        versus just do a reselect of underlying data
         * @param bookListBuilder the builder
         */
        @UiThread
        private GetBookListTask(@NonNull final ProgressDialogFragment<BuilderHolder> fragment,
                                @NonNull final BuilderHolder builderHolder,
                                final boolean isFullRebuild,
                                @NonNull final BooklistBuilder bookListBuilder) {

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                Logger.debug(this, "constructor", "mIsFullRebuild=" + isFullRebuild);
            }

            mFragment = fragment;
            mIsFullRebuild = isFullRebuild;
            mBooklistBuilder = bookListBuilder;
            mHolder = builderHolder;
        }

        /**
         * @param fm              fragment manager
         * @param builderHolder   holder class with input fields / results.
         * @param isFullRebuild   Indicates whole table structure needs rebuild,
         *                        versus just do a reselect of underlying data
         * @param bookListBuilder the builder
         */
        @UiThread
        public static void start(@NonNull final FragmentManager fm,
                                 @NonNull final BuilderHolder builderHolder,
                                 final boolean isFullRebuild,
                                 @NonNull final BooklistBuilder bookListBuilder,
                                 final long bookshelfId,
                                 @NonNull final SearchCriteria searchCriteria) {
            if (fm.findFragmentByTag(TAG) == null) {
                ProgressDialogFragment<BuilderHolder> frag =
                        ProgressDialogFragment.newInstance(R.string.progress_msg_getting_books,
                                                           true, 0);

                // always limit to one bookshelf.
                bookListBuilder.setFilterOnBookshelfId(bookshelfId);

                // Use current criteria
                bookListBuilder.setFilterOnText(searchCriteria.text);
                bookListBuilder.setFilterOnTitle(searchCriteria.title);
                bookListBuilder.setFilterOnAuthorName(searchCriteria.author);
                bookListBuilder.setFilterOnSeriesName(searchCriteria.series);
                bookListBuilder.setFilterOnLoanedToPerson(searchCriteria.loanee);

                bookListBuilder.setFilterOnBookIdList(searchCriteria.bookList);

                GetBookListTask task =
                        new GetBookListTask(frag, builderHolder, isFullRebuild, bookListBuilder);
                frag.setTask(M_TASK_ID, task);
                frag.show(fm, TAG);
                task.execute();
            }
        }

        @UiThread
        @Override
        protected void onCancelled(final BuilderHolder result) {
            cleanup();
        }

        /** Try to sync the previously selected book ID. */
        private void syncPreviouslySelectedBookId() {

            if (mHolder.currentPositionedBookId != 0) {
                // get all positions of the book
                targetRows = mBooklistBuilder
                        .getBookAbsolutePositions(mHolder.currentPositionedBookId);

                if (targetRows != null && !targetRows.isEmpty()) {
                    // First, get the ones that are currently visible...
                    ArrayList<BookRowInfo> visRows = new ArrayList<>();
                    for (BookRowInfo i : targetRows) {
                        if (i.visible) {
                            visRows.add(i);
                        }
                    }
                    // If we have any visible rows, only consider them for the new position
                    if (!visRows.isEmpty()) {
                        targetRows = visRows;
                    } else {
                        // Make them ALL visible
                        for (BookRowInfo rowInfo : targetRows) {
                            if (!rowInfo.visible) {
                                mBooklistBuilder.ensureAbsolutePositionVisible(
                                        rowInfo.absolutePosition);
                            }
                        }
                        // Recalculate all positions
                        for (BookRowInfo rowInfo : targetRows) {
                            rowInfo.listPosition = mBooklistBuilder.getPosition(
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
        }

        @Override
        @NonNull
        @WorkerThread
        protected BuilderHolder doInBackground(final Void... params) {
            try {
                long t0;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t0 = System.nanoTime();
                }
                // Build the underlying data
                if (mHolder.listCursor != null && !mIsFullRebuild) {
                    mBooklistBuilder.rebuild();
                } else {
                    mBooklistBuilder.build(mHolder.rebuildState, mHolder.currentPositionedBookId);
                    // After first build, always preserve this object state
                    mHolder.rebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
                }

                if (isCancelled()) {
                    return mHolder;
                }

                long t1;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t1 = System.nanoTime();
                }

                syncPreviouslySelectedBookId();

                if (isCancelled()) {
                    return mHolder;
                }

                long t2;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t2 = System.nanoTime();
                }

                // Now we have the expanded groups as needed, get the list cursor
                tempList = mBooklistBuilder.getList();
                // Clear it so it won't be reused.
                mHolder.currentPositionedBookId = 0;

                long t3;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t3 = System.nanoTime();
                }
                // get a count() from the cursor in background task because the setAdapter() call
                // will do a count() and potentially block the UI thread while it pages through the
                // entire cursor. If we do it here, subsequent calls will be fast.
                @SuppressWarnings("UnusedAssignment")
                int count = tempList.getCount();

                long t4;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t4 = System.nanoTime();
                }
                mHolder.resultUniqueBooks = tempList.getUniqueBookCount();

                if (isCancelled()) {
                    return mHolder;
                }

                long t5;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t5 = System.nanoTime();
                }
                mHolder.resultTotalBooks = tempList.getBookCount();

                long t6;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    //noinspection UnusedAssignment
                    t6 = System.nanoTime();
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    Logger.debug("doInBackground",
                                 "\n Build: " + (t1 - t0),
                                 "\n Position: " + (t2 - t1),
                                 "\n Select: " + (t3 - t2),
                                 "\n Count(" + count + "): " + (t4 - t3)
                                         + '/' + (t5 - t4) + '/' + (t6 - t5),
                                 "\n ====== ",
                                 "\n Total time: " + (t6 - t0) + "nano");
                }

                if (isCancelled()) {
                    return mHolder;
                }

                // Set the results.
                mHolder.resultList = tempList;
                mHolder.resultTargetRows = targetRows;

            } catch (RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                cleanup();
            }

            return mHolder;
        }

        @AnyThread
        private void cleanup() {
            if (BuildConfig.DEBUG) {
                Logger.debugWithStackTrace(this, "cleanup");
            }
            if (tempList != null && tempList != mHolder.listCursor) {
                if (mHolder.listCursor == null
                        || (tempList.getBuilder() != mHolder.listCursor.getBuilder())) {
                    tempList.getBuilder().close();
                }
                tempList.close();
            }
            tempList = null;
            // close the progress dialog
            mFragment.dismiss();
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final BuilderHolder result) {
            mFragment.onTaskFinished(mException == null, result);
        }
    }

    /** value class for the Builder. */
    private static class BuilderHolder {

        /** input/output field for the activity. */
        final BooklistPseudoCursor listCursor;
        /** input/output field for the activity. */
        long currentPositionedBookId;
        /** input/output field for the activity. */
        int rebuildState;

        /** output field for the activity. */
        int resultTotalBooks;
        /** output field for the activity. */
        int resultUniqueBooks;

        /** Resulting Cursor. */
        BooklistPseudoCursor resultList;
        /** used to determine new cursor position. */
        ArrayList<BookRowInfo> resultTargetRows;

        /**
         * Constructor: these are the fields we need as input.
         */
        BuilderHolder(final long currentPositionedBookId,
                      @NonNull final BooklistPseudoCursor listCursor,
                      final int rebuildState) {
            this.currentPositionedBookId = currentPositionedBookId;
            this.listCursor = listCursor;
            this.rebuildState = rebuildState;
        }
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     * <p>
     * All individual criteria are supported by the {@link BooklistBuilder},
     * but not necessarily in {@link BooksOnBookshelf}.
     * <p>
     * Only some are supported by {@link #onSearchRequested()}.
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
            if (bundle.containsKey(DBDefinitions.KEY_TITLE)) {
                title = bundle.getString(DBDefinitions.KEY_TITLE);
            }
            if (bundle.containsKey(DBDefinitions.KEY_SERIES)) {
                series = bundle.getString(DBDefinitions.KEY_SERIES);
            }
            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                bookList = bundle.getIntegerArrayList(UniqueId.BKEY_ID_LIST);
            }
        }

        /**
         * @param intent which will be used for a {@link #startActivityForResult}
         */
        void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, text)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, author)
                  .putExtra(DBDefinitions.KEY_TITLE, title)
                  .putExtra(DBDefinitions.KEY_SERIES, series)
                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)
                  .putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        /**
         * @param outState from {@link #onSaveInstanceState}
         */
        void to(final Bundle outState) {
            outState.putString(UniqueId.BKEY_SEARCH_TEXT, text);
            outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, author);
            outState.putString(DBDefinitions.KEY_TITLE, title);
            outState.putString(DBDefinitions.KEY_SERIES, series);
            outState.putString(DBDefinitions.KEY_LOANEE, loanee);
            outState.putIntegerArrayList(UniqueId.BKEY_ID_LIST, bookList);
        }

        boolean isEmpty() {
            return (text == null || text.isEmpty())
                    && (author == null || author.isEmpty())
                    && (title == null || title.isEmpty())
                    && (series == null || series.isEmpty())
                    && (loanee == null || loanee.isEmpty())
                    && (bookList == null || bookList.isEmpty());
        }
    }

    /**
     * Hold the current row details to be shown at the top of the list (just below the
     * bookshelf spinner) when scrolling.
     * <p>
     * the API of this class accepts level in the range of 1..2
     */
    private class ScrollingInfo {

        /** Support for two levels. */
        private static final int MAX = 2;

        /** Views for the levels. */
        private final TextView[] levelTextView = new TextView[MAX];
        /** Visibility of the levels. */
        private final boolean[] isVisible = new boolean[MAX];

        /**
         * Constructor.
         */
        ScrollingInfo() {
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
            // sanity check.
            if (levelTextView.length < level) {
                throw new IllegalArgumentException(
                        "levelTextView.length=" + levelTextView.length + " < level=" + level);
            }

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
        void update(@IntRange(from = 0) final int firstVisibleItem) {

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
