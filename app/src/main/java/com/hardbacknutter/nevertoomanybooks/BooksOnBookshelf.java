/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainer;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditColorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditFormatDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditGenreDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLanguageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLocationDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditPublisherDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ExportTaskModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ImportTaskModel;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.fastscroller.FastScroller;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * <p>
 * Notes on the local-search:
 * <ol>Advanced:
 *     <li>User clicks navigation panel menu search option</li>
 *     <li>FTSSearch Activity is started</li>
 *     <li>FTS activity returns an id-list and the fts search terms</li>
 *     <li>#onActivityResult sets the incoming fts criteria</li>
 *     <li>#onResume builds the list</li>
 * </ol>
 *
 * <ol>Standard:
 *     <li>User clicks option menu search iconF</li>
 *     <li>shows the search widget, user types</li>
 *     <li>#onNewIntent() gets called with the query data</li>
 *     <li>build the list</li>
 * </ol>
 * <p>
 * We check if we have search criteria, if not we just build and are done.<br>
 *
 * <ol>When we do have search criteria:
 *     <li>during display of the list, the action bar home icon is set to 'up'</li>
 *     <li>Allows the user to re-open the nav drawer and refine the search.</li>
 *     <li>any 'up/back' action will trigger #onBackPressed</li>
 *     <li>#onBackPressed checks if there are search criteria, if so, clears and
 *     rebuild and suppresses the 'back' action</li>
 * </ol>
 * <p>
 * ENHANCE: turn the list + list header components into a proper fragment.
 * That will allow us to have a central FragmentContainer, where we can swap in
 * other fragments without leaving the activity. e.g. import/export/... etc...
 * Something for version 1.1 presumably.
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";
    public static final String BKEY_START_BACKUP = TAG + ":startBackup";

    /** simple indeterminate progress spinner to show while getting the list of books. */
    private ProgressBar mProgressBar;
    /** The dropdown button to select a Bookshelf. */
    private Spinner mBookshelfSpinner;
    /** Listener for clicks on the list. */
    private final BooklistAdapter.OnRowClickedListener mOnRowClickedListener =
            new BooklistAdapter.OnRowClickedListener() {

                /**
                 * User clicked a row.
                 * <ul>
                 *      <li>Book: open the details screen.</li>
                 *      <li>Not a book: expand/collapse the section as appropriate.</li>
                 * </ul>
                 */
                @Override
                public void onItemClick(final int position) {
                    final Cursor cursor = mModel.getListCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return;
                    }
                    final DataHolder rowData = new CursorRow(cursor);

                    // If it's a book, open the details screen.
                    if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        final long rowId = rowData.getLong(DBDefinitions.KEY_PK_ID);
                        final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                        // Note we (re)create the flat table *every time* the user click a book.
                        // This guarantees an exact match in rowId'
                        // (which turns out tricky if we cache the table)
                        // ENHANCE: re-implement flat table caching
                        final String navTableName = mModel.createFlattenedBooklist();
                        final Intent intent = new Intent(BooksOnBookshelf.this,
                                                         BookDetailsActivity.class)
                                .putExtra(DBDefinitions.KEY_PK_ID, bookId)
                                .putExtra(BookDetailsFragmentViewModel.BKEY_NAV_TABLE, navTableName)
                                .putExtra(BookDetailsFragmentViewModel.BKEY_NAV_ROW_ID, rowId);
                        startActivityForResult(intent, RequestCode.BOOK_VIEW);

                    } else {
                        // it's a level, expand/collapse.
                        final int nodeRowId =
                                rowData.getInt(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                        final RowStateDAO.Node node = mModel.toggleNode(
                                nodeRowId, RowStateDAO.Node.NEXT_STATE_TOGGLE, 1);
                        refreshNodePosition(node);
                    }
                }

                /**
                 * User long-clicked on a row. Bring up a context menu as appropriate.
                 */
                @Override
                public boolean onItemLongClick(final int position) {
                    final Cursor cursor = mModel.getListCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return false;
                    }
                    final DataHolder rowData = new CursorRow(cursor);

                    // build the menu for this row
                    final Menu menu = MenuPicker.createMenu(BooksOnBookshelf.this);
                    if (onCreateContextMenu(menu, rowData)) {
                        // we have a menu to show, set the title according to the level.
                        final int level = rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
                        final String title = mAdapter.getLevelText(position, level);

                        // bring up the context menu
                        new MenuPicker(BooksOnBookshelf.this, title, menu, position,
                                       BooksOnBookshelf.this::onContextItemSelected)
                                .show();
                    }
                    return true;
                }
            };

    /** List header. */
    private TextView mHeaderStyleNameView;
    /** List header. */
    private TextView mHeaderFilterTextView;
    /** List header: The number of books in the current list. */
    private TextView mHeaderBookCountView;
    /** The View for the list. */
    private RecyclerView mListView;
    private LinearLayoutManager mLayoutManager;
    /** Multi-type adapter to manage list connection to cursor. */
    private BooklistAdapter mAdapter;
    /** The adapter used to fill the mBookshelfSpinner. */
    private ArrayAdapter<BooksOnBookshelfModel.BookshelfSpinnerEntry> mBookshelfSpinnerAdapter;
    /** The ViewModel. */
    private BooksOnBookshelfModel mModel;
    /** Listener for the Bookshelf Spinner. */
    private final OnItemSelectedListener mOnBookshelfSelectionChanged =
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(@NonNull final AdapterView<?> parent,
                                           @NonNull final View view,
                                           final int position,
                                           final long id) {
                    @Nullable
                    final BooksOnBookshelfModel.BookshelfSpinnerEntry selected =
                            (BooksOnBookshelfModel.BookshelfSpinnerEntry)
                                    parent.getItemAtPosition(position);

                    if (selected != null) {
                        saveListPosition();

                        // make the new shelf the current, and build the new list
                        mModel.setCurrentBookshelf(BooksOnBookshelf.this,
                                                   selected.getBookshelf());
                        mModel.buildBookList(BooksOnBookshelf.this);
                    }
                }

                @Override
                public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                    // Do Nothing
                }
            };
    // ENHANCE: update the modified row without a rebuild.
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "onBookChanged"
                       + "|bookId=" + bookId
                       + "|fieldsChanged=0b" + Integer.toBinaryString(fieldsChanged)
                       + "|data=" + data);
        }
        saveListPosition();
        // go create
        mModel.buildBookList(this);

        // changes were made to a single book
//        if (bookId > 0) {
//            if ((fieldsChanged & BookChangedListener.BOOK_READ) != 0) {
//                saveListPosition();
//                initBookList();
//
//          } else if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
//                if (data != null) {
//                    String loanee = data.getString(DBDefinitions.KEY_LOANEE);
//                }
//                saveListPosition();
//                initBookList();
//
//            } else if ((fieldsChanged & BookChangedListener.BOOK_DELETED) != 0) {
//                saveListPosition();
//                initBookList();
//            }
//        } else {
//            // changes (Author, Series, ...) were made to (potentially) the whole list
//            if (fieldsChanged != 0) {
//                saveListPosition();
//                initBookList();
//            }
//        }
    };
    //    private BooksonbookshelfBinding mVb;
    private final OptionsDialogBase.OptionsListener<ImportManager> mImportOptionsListener =
            new OptionsDialogBase.OptionsListener<ImportManager>() {
                @Override
                public void onOptionsSet(@NonNull final ImportManager options) {
                    mImportModel.startArchiveImportTask(options);
                }
            };
    /**
     * Apply the style that a user has selected.
     * Called from {@link StylePickerDialogFragment}.
     */
    private final StylePickerDialogFragment.StyleChangedListener mStyleChangedListener =
            new StylePickerDialogFragment.StyleChangedListener() {
                public void onStyleChanged(@NonNull final BooklistStyle style) {
                    // store the new data
                    mModel.onStyleChanged(BooksOnBookshelf.this, style);
                    // There is very little ability to preserve position when going from
                    // a list sorted by Author/Series to on sorted by unread/addedDate/publisher.
                    // Keeping the current row/pos is probably the most useful thing we can
                    // do since we *may* come back to a similar list.
                    saveListPosition();
                    // and do a rebuild
                    mModel.buildBookList(BooksOnBookshelf.this);
                }
            };
    /** Full progress dialog to show while exporting/importing. */
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** Export. */
    private ExportTaskModel mExportModel;
    private final OptionsDialogBase.OptionsListener<ExportManager> mExportOptionsListener =
            this::exportPickUri;
    /** Import. */
    private ImportTaskModel mImportModel;
    /** Encapsulates the FAB button/menu. */
    private FabMenu mFabMenu;

    @Override
    protected void onSetContentView() {
//        mVb = BooksonbookshelfBinding.inflate(getLayoutInflater());
//        setContentView(mVb.getRoot());
        setContentView(R.layout.booksonbookshelf);

        mBookshelfSpinner = findViewById(R.id.bookshelf_spinner);
        mHeaderStyleNameView = findViewById(R.id.style_name);
        mHeaderFilterTextView = findViewById(R.id.filter_text);
        mHeaderBookCountView = findViewById(R.id.book_count);
        mListView = findViewById(android.R.id.list);

        mProgressBar = findViewById(R.id.progressBar);

        mFabMenu = new FabMenu(findViewById(R.id.fab),
                               findViewById(R.id.fabOverlay),
                               findViewById(R.id.fab0),
                               findViewById(R.id.fab1),
                               findViewById(R.id.fab2),
                               findViewById(R.id.fab3),
                               findViewById(R.id.fab4));
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
            Log.d(getClass().getName(), "onAttachFragment: " + fragment.getTag());
        }
        super.onAttachFragment(fragment);

        if (fragment instanceof BookChangedListenerOwner) {
            ((BookChangedListenerOwner) fragment).setListener(mBookChangedListener);

        } else if (fragment instanceof StylePickerDialogFragment) {
            ((StylePickerDialogFragment) fragment).setListener(mStyleChangedListener);

        } else if (fragment instanceof ExportHelperDialogFragment) {
            ((ExportHelperDialogFragment) fragment).setListener(mExportOptionsListener);

        } else if (fragment instanceof ImportHelperDialogFragment) {
            ((ImportHelperDialogFragment) fragment).setListener(mImportOptionsListener);
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(BooksOnBookshelfModel.class);
        mModel.init(this, getIntent().getExtras(), savedInstanceState);
        mModel.onUserMessage().observe(this, message -> {
            if (message != null) {
                Snackbar.make(mListView, message, Snackbar.LENGTH_LONG).show();
            }
        });
        mModel.onNeedsGoodreads().observe(this, needs -> {
            if (needs != null && needs) {
                RequestAuthTask.prompt(this, mModel.getGoodreadsTaskListener(this));
            }
        });
        mModel.onBuilderSuccess().observe(this, this::displayList);
        mModel.onBuilderFailed().observe(this, this::initAdapter);
        mModel.onShowProgressBar().observe(this, show ->
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE));

        mExportModel = new ViewModelProvider(this).get(ExportTaskModel.class);
        mExportModel.onTaskProgress().observe(this, this::onTaskProgress);
        mExportModel.onTaskFinished().observe(this, this::onExportFinished);

        mImportModel = new ViewModelProvider(this).get(ImportTaskModel.class);
        mImportModel.onTaskProgress().observe(this, this::onTaskProgress);
        mImportModel.onTaskFinished().observe(this, this::onImportFinished);

        // enable the navigation menu
        setNavigationItemVisibility(R.id.nav_manage_list_styles, true);
        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);
        setNavigationItemVisibility(R.id.nav_export, true);
        setNavigationItemVisibility(R.id.nav_import, true);
        setNavigationItemVisibility(R.id.nav_goodreads, GoodreadsHandler.isShowSyncMenus(this));

        // initialize but do not populate the list; the latter is done in onResume
        mLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(mLayoutManager);
        mListView.addItemDecoration(new TopLevelItemDecoration(this));
        FastScroller.attach(mListView);

        // initialize but do not populate the list;  the latter is done in setBookShelfSpinner
        mBookshelfSpinnerAdapter = new ArrayAdapter<>(
                this, R.layout.bookshelf_spinner_selected, mModel.getBookshelfSpinnerList());
        mBookshelfSpinnerAdapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item);
        mBookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);

        mFabMenu.attach(mListView);
        mFabMenu.getItem(0).setOnClickListener(v -> addByIsbn(true));
        mFabMenu.getItem(1).setOnClickListener(v -> addByIsbn(false));
        mFabMenu.getItem(2).setOnClickListener(v -> addBySearch(BookSearchByTextFragment.TAG));
        mFabMenu.getItem(3).setOnClickListener(v -> addManually());
        if (Prefs.showEditBookTabNativeId(this)) {
            mFabMenu.getItem(4).setEnabled(true);
            mFabMenu.getItem(4).setOnClickListener(
                    v -> addBySearch(BookSearchByNativeIdFragment.TAG));
        } else {
            mFabMenu.getItem(4).setEnabled(false);
        }

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        // auto-start a backup if required.
        if (getIntent().getBooleanExtra(BKEY_START_BACKUP, false)) {
            exportShowOptions();

        } else if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_book_list, null);
        }
    }

    /**
     * Entry point for the system search request.
     *
     * @param intent to use
     */
    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        // make this the Activity intent.
        setIntent(intent);

        handleStandardSearchIntent(intent);
        mModel.buildBookList(this);
    }

    /**
     * Handle the standard search intent / suggestions click.
     *
     * <a href="https://developer.android.com/guide/topics/search/search-dialog#ReceivingTheQuery">
     * ReceivingTheQuery</a>
     */
    private void handleStandardSearchIntent(@NonNull final Intent intent) {
        final String query;
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Handle the standard search intent.
            query = intent.getStringExtra(SearchManager.QUERY);

        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // Handle a suggestions click.
            // The ACTION_VIEW as set in res/xml/searchable.xml/searchSuggestIntentAction
            query = intent.getDataString();
        } else {
            query = null;
        }
        // actioning on the criteria wil happen automatically at list building time.
        mModel.getSearchCriteria().setKeywords(query);
    }

    @Override
    protected boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();
        switch (item.getItemId()) {
            case R.id.nav_advanced_search: {
                // overridden, so we can pass the current criteria
                Intent searchIntent = new Intent(this, FTSSearchActivity.class);
                mModel.getSearchCriteria().to(searchIntent);
                startActivityForResult(searchIntent, RequestCode.ADVANCED_LOCAL_SEARCH);
                return true;
            }
            case R.id.nav_manage_bookshelves: {
                // overridden, so we can pass the current bookshelf id.
                final Intent intent = new Intent(this, EditBookshelvesActivity.class)
                        .putExtra(EditBookshelvesModel.BKEY_CURRENT_BOOKSHELF,
                                  mModel.getCurrentBookshelf().getId());
                startActivityForResult(intent, RequestCode.NAV_PANEL_EDIT_BOOKSHELVES);
                return true;
            }
            case R.id.nav_manage_list_styles: {
                final Intent intent = new Intent(this, PreferredStylesActivity.class)
                        .putExtra(BooklistStyle.BKEY_STYLE_ID,
                                  mModel.getCurrentStyle(this).getId());
                startActivityForResult(intent, RequestCode.NAV_PANEL_EDIT_STYLES);
                return true;
            }

            case R.id.nav_import: {
//                Intent intent = new Intent(this, AdminActivity.class)
//                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, ImportFragment.TAG);
//                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_IMPORT);
                importPickUri();
                return true;
            }
            case R.id.nav_export: {
//                Intent intent = new Intent(this, AdminActivity.class)
//                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, ExportFragment.TAG);
//                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EXPORT);
                exportShowOptions();
                return true;
            }

            default:
                return super.onNavigationItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        getMenuInflater().inflate(R.menu.bob, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        mFabMenu.hide();

        final boolean showECPreferred = mModel.getCurrentStyle(this).getTopLevel(this) > 1;
        menu.findItem(R.id.MENU_LEVEL_PREFERRED_COLLAPSE).setVisible(showECPreferred);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        mFabMenu.hide();

        switch (item.getItemId()) {
            case R.id.MENU_SORT: {
                StylePickerDialogFragment
                        .newInstance(mModel.getCurrentStyle(this), false)
                        .show(getSupportFragmentManager(), StylePickerDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LEVEL_PREFERRED_COLLAPSE: {
                expandAllNodes(mModel.getCurrentStyle(this).getTopLevel(this), false);
                return true;
            }
            case R.id.MENU_LEVEL_EXPAND: {
                expandAllNodes(1, true);
                return true;
            }
            case R.id.MENU_LEVEL_COLLAPSE: {
                expandAllNodes(1, false);
                return true;
            }
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from an options menu selection.
                // We pass the book ID's for the currently displayed list.
                final ArrayList<Long> bookIdList = mModel.getCurrentBookIdList();
                final Intent intent = new Intent(this, BookSearchActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                        .putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList);
                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Create a context menu based on row group.
     *
     * @param menu    to populate
     * @param rowData current cursorRow
     *
     * @return {@code true} if there is a menu to show
     */
    private boolean onCreateContextMenu(@NonNull final Menu menu,
                                        @NonNull final DataHolder rowData) {
        menu.clear();

        final int rowGroupId = rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                getMenuInflater().inflate(R.menu.book, menu);
                getMenuInflater().inflate(R.menu.sm_view_on_site, menu);
                getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                final boolean isRead = rowData.getBoolean(DBDefinitions.KEY_READ);
                menu.findItem(R.id.MENU_BOOK_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isRead);

                // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
                final boolean useLending = DBDefinitions.isUsed(this, DBDefinitions.KEY_LOANEE);
                final boolean isAvailable = mModel.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
                    .setVisible(GoodreadsHandler.isShowSyncMenus(this));

                MenuHandler.prepareOptionalMenus(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                getMenuInflater().inflate(R.menu.author, menu);

                final boolean complete = rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                MenuHandler.prepareOptionalMenus(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBDefinitions.KEY_FK_SERIES) != 0) {
                    getMenuInflater().inflate(R.menu.series, menu);

                    final boolean complete =
                            rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    MenuHandler.prepareOptionalMenus(menu, rowData);
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (!rowData.getString(DBDefinitions.KEY_PUBLISHER).isEmpty()) {
                    getMenuInflater().inflate(R.menu.publisher, menu);
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (!rowData.getString(DBDefinitions.KEY_LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (!rowData.getString(DBDefinitions.KEY_LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (!rowData.getString(DBDefinitions.KEY_GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (!rowData.getString(DBDefinitions.KEY_FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (!rowData.getString(DBDefinitions.KEY_COLOR).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_COLOR_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            default: {
                break;
            }
        }

        int menuOrder = getResources().getInteger(R.integer.MENU_NEXT_MISSING_COVER);
        if (menu.size() > 0) {
            menu.add(Menu.NONE, R.id.MENU_DIVIDER, menuOrder++, "")
                .setEnabled(false);
        }
        menu.add(Menu.NONE, R.id.MENU_NEXT_MISSING_COVER, menuOrder++,
                 R.string.lbl_next_book_without_cover)
            .setIcon(R.drawable.ic_broken_image);

        // if it's a level, add the expand option
        if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) != BooklistGroup.BOOK) {
            //noinspection UnusedAssignment
            menu.add(Menu.NONE, R.id.MENU_LEVEL_EXPAND, menuOrder++, R.string.lbl_level_expand)
                .setIcon(R.drawable.ic_unfold_more);
        }

        return menu.size() > 0;
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {

        final Cursor cursor = mModel.getListCursor();
        // Move the cursor, so we can read the data for this row.
        // The majority of the time this is not needed, but a fringe case (toggle node)
        // showed it should indeed be done.
        // Paranoia: if the user can click it, then this move should be fine.
        if (!cursor.moveToPosition(position)) {
            return false;
        }

        final DataHolder rowData = new CursorRow(cursor);

        switch (menuItem) {
            case R.id.MENU_BOOK_EDIT: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                final Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookId);
                startActivityForResult(intent, RequestCode.BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                final String title = rowData.getString(DBDefinitions.KEY_TITLE);
                final List<Author> authors = mModel.getDb().getAuthorsByBookId(bookId);
                StandardDialogs.deleteBook(this, title, authors, () -> {
                    mModel.getDb().deleteBook(this, bookId);
                    mBookChangedListener.onChange(bookId, BookChangedListener.BOOK_DELETED, null);
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Book book = mModel.getBook(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
                final Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(Book.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(intent, RequestCode.BOOK_DUPLICATE);
                return true;
            }

            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle the read status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_READ);
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                if (mModel.getDb().setBookRead(bookId, status)) {
                    mBookChangedListener.onChange(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                EditLenderDialogFragment
                        .newInstance(bookId, rowData.getString(DBDefinitions.KEY_TITLE))
                        .show(getSupportFragmentManager(), EditLenderDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                mModel.getDb().lendBook(bookId, null);
                mBookChangedListener.onChange(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                final Book book = mModel.getBook(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
                startActivity(book.getShareBookIntent(this));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                Snackbar.make(mListView, R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                new SendOneBookTask(bookId, mModel.getGoodreadsTaskListener(this))
                        .execute();
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from a context click on a row.
                // We pass the book ID's which are suited for that row.
                final Intent intent = new Intent(this, BookSearchActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG);

                switch (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP)) {

                    case BooklistGroup.BOOK: {
                        final ArrayList<Long> bookIdList = new ArrayList<>();
                        bookIdList.add(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_TITLE))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        bookIdList);
                        break;
                    }
                    case BooklistGroup.AUTHOR: {
                        final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        mModel.getDb().getBookIdsByAuthor(authorId));
                        break;
                    }
                    case BooklistGroup.SERIES: {
                        final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_SERIES_TITLE))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        mModel.getDb().getBookIdsBySeries(seriesId));
                        break;
                    }
                    case BooklistGroup.PUBLISHER: {
                        String publisher = rowData.getString(DBDefinitions.KEY_PUBLISHER);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE, publisher)
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        mModel.getDb().getBookIdsByPublisher(publisher));
                        break;
                    }
                    default: {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "onContextItemSelected"
                                       + "|MENU_BOOK_UPDATE_FROM_INTERNET not supported"
                                       + "|Group=" + rowData
                                               .getInt(DBDefinitions.KEY_BL_NODE_GROUP));
                        }
                        return true;
                    }
                }

                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT: {
                final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                final Series series = mModel.getSeries(seriesId);
                if (series != null) {
                    EditSeriesDialogFragment
                            .newInstance(series)
                            .show(getSupportFragmentManager(), EditSeriesDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_SERIES_SET_COMPLETE:
            case R.id.MENU_SERIES_SET_INCOMPLETE: {
                final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                // toggle the complete status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                if (mModel.getDb().setSeriesComplete(seriesId, status)) {
                    mBookChangedListener.onChange(0, BookChangedListener.SERIES, null);
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                final Series series =
                        mModel.getSeries(rowData.getLong(DBDefinitions.KEY_FK_SERIES));
                if (series != null) {
                    StandardDialogs.deleteSeries(this, series, () -> {
                        mModel.getDb().deleteSeries(this, series.getId());
                        mBookChangedListener.onChange(0, BookChangedListener.SERIES, null);
                    });
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_WORKS: {
                final Intent intent = new Intent(this, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID,
                                  rowData.getLong(DBDefinitions.KEY_FK_AUTHOR))
                        .putExtra(DBDefinitions.KEY_FK_BOOKSHELF,
                                  mModel.getCurrentBookshelf().getId());
                startActivityForResult(intent, RequestCode.AUTHOR_WORKS);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT: {
                final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                final Author author = mModel.getAuthor(authorId);
                if (author != null) {
                    EditAuthorDialogFragment
                            .newInstance(author)
                            .show(getSupportFragmentManager(), EditAuthorDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_AUTHOR_SET_COMPLETE:
            case R.id.MENU_AUTHOR_SET_INCOMPLETE: {
                final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                // toggle the complete status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                if (mModel.getDb().setAuthorComplete(authorId, status)) {
                    mBookChangedListener.onChange(0, BookChangedListener.AUTHOR, null);
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT: {
                final Publisher publisher = new Publisher(
                        rowData.getString(DBDefinitions.KEY_PUBLISHER));
                EditPublisherDialogFragment
                        .newInstance(publisher)
                        .show(getSupportFragmentManager(), EditPublisherDialogFragment.TAG);

                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT: {
                EditFormatDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_FORMAT))
                        .show(getSupportFragmentManager(), EditFormatDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_COLOR_EDIT: {
                EditColorDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_COLOR))
                        .show(getSupportFragmentManager(), EditColorDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                EditGenreDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_GENRE))
                        .show(getSupportFragmentManager(), EditGenreDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                EditLanguageDialogFragment
                        .newInstance(this, rowData.getString(DBDefinitions.KEY_LANGUAGE))
                        .show(getSupportFragmentManager(), EditLanguageDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                EditLocationDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_LOCATION))
                        .show(getSupportFragmentManager(), EditLocationDialogFragment.TAG);
                return true;
            }

            /* ********************************************************************************** */
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                AmazonSearchEngine.openWebsite(this,
                                               mModel.getAuthorFromRow(this, rowData),
                                               null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                AmazonSearchEngine.openWebsite(this,
                                               null,
                                               mModel.getSeriesFromRow(rowData));
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                AmazonSearchEngine.openWebsite(this,
                                               mModel.getAuthorFromRow(this, rowData),
                                               mModel.getSeriesFromRow(rowData));
                return true;
            }

            case R.id.MENU_LEVEL_EXPAND: {
                final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                final int relativeChildLevel = mModel.getCurrentStyle(this).getGroupCount();
                final RowStateDAO.Node node = mModel.toggleNode(
                        nodeRowId, RowStateDAO.Node.NEXT_STATE_EXPANDED, relativeChildLevel);
                refreshNodePosition(node);
                return true;
            }

            case R.id.MENU_NEXT_MISSING_COVER: {
                final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                final RowStateDAO.Node node = mModel.getNextBookWithoutCover(this, nodeRowId);
                if (node != null) {
                    scrollTo(node);
                    refreshNodePosition(node);
                }
                return true;
            }

            default:
                return MenuHandler.handleOpenOnWebsiteMenus(this, menuItem, rowData);
        }
    }

    /**
     * Reminder: don't do any commits on the fragment manager.
     * This includes showing fragments, or starting tasks that show fragments.
     * Do this in {@link #onResume} which will be called after onActivityResult.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case RequestCode.ADVANCED_LOCAL_SEARCH:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (mModel.setSearchCriteria(data.getExtras(), true)) {
                        //URGENT: switch bookshelf? all-books?
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;

            case RequestCode.UPDATE_FIELDS_FROM_INTERNET:
            case RequestCode.BOOK_VIEW:
            case RequestCode.BOOK_EDIT:
            case RequestCode.BOOK_DUPLICATE:
            case RequestCode.BOOK_SEARCH:
            case RequestCode.AUTHOR_WORKS: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    final Bundle extras = data.getExtras();
                    if (extras != null) {
                        if (extras.getBoolean(BookViewModel.BKEY_BOOK_CREATED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.getBoolean(BookViewModel.BKEY_BOOK_MODIFIED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.getBoolean(BookViewModel.BKEY_BOOK_DELETED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.containsKey(BooksOnBookshelfModel.BKEY_LIST_STATE)) {
                            int state = extras.getInt(BooksOnBookshelfModel.BKEY_LIST_STATE,
                                                      BooklistBuilder.PREF_REBUILD_SAVED_STATE);
                            mModel.setRebuildState(state);
                            mModel.setForceRebuildInOnResume(true);
                        }

                        // if we got an id back, make any rebuild re-position to it.
                        long bookId = extras.getLong(DBDefinitions.KEY_PK_ID, 0);
                        if (bookId != 0) {
                            mModel.setDesiredCentralBookId(bookId);
                        }
                    }
                }
                break;
            }
            // from BaseActivity Nav Panel
            case RequestCode.NAV_PANEL_EDIT_BOOKSHELVES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // the last edited/inserted shelf
                    final long bookshelfId = data.getLongExtra(DBDefinitions.KEY_PK_ID,
                                                               Bookshelf.DEFAULT);
                    if (bookshelfId != mModel.getCurrentBookshelf().getId()) {
                        mModel.setCurrentBookshelf(this, bookshelfId);
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }
            // from BaseActivity Nav Panel or from sort menu dialog
            case RequestCode.NAV_PANEL_EDIT_STYLES:
                // or directly from the style edit screen
            case RequestCode.EDIT_STYLE: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);

                    @Nullable
                    final BooklistStyle style = data.getParcelableExtra(BooklistStyle.BKEY_STYLE);
                    if (style != null) {
                        // save the new bookshelf/style combination
                        mModel.getCurrentBookshelf().setAsPreferred(this);
                        mModel.setCurrentStyle(this, style);
                    }

                    if (data.getBooleanExtra(BooklistStyle.BKEY_STYLE_MODIFIED, false)) {
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }

            // from BaseActivity Nav Panel
            case RequestCode.NAV_PANEL_EXPORT:
                break;

            // from BaseActivity Nav Panel
            case RequestCode.NAV_PANEL_IMPORT: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (data.hasExtra(ImportResults.BKEY_IMPORT_RESULTS)) {
                        final int options = data.getIntExtra(ImportResults.BKEY_IMPORT_RESULTS,
                                                             Options.NOTHING);
                        if (options != 0) {
                            if ((options & Options.STYLES) != 0) {
                                // Force a refresh of the list of all user styles.
                                BooklistStyle.StyleDAO.clear();
                            }
                            if ((options & Options.PREFS) != 0) {
                                // Refresh the preferred bookshelf. This also refreshes its style.
                                mModel.reloadCurrentBookshelf(this);
                            }

                            // styles, prefs, books, covers,... it all requires a rebuild.
                            mModel.setForceRebuildInOnResume(true);
                        }
                    }
                }
                break;
            }

            case RequestCode.EXPORT_PICK_URI: {
                // The user selected a file to backup to. Next step starts the export task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        mExportModel.startArchiveExportTask(this, uri);
                    }
                }
                break;
            }

            case RequestCode.IMPORT_PICK_URI: {
                // The user selected a file to import from. Next step asks for the options.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        importShowOptions(uri);
                    }
                }
                break;
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        // If the FAB menu is showing, hide it and suppress the back key.
        if (mFabMenu.isShown()) {
            mFabMenu.show(false);
            return;
        }

        // If the current list is has any search criteria enabled, clear them and rebuild the list.
        if (isTaskRoot() && !mModel.getSearchCriteria().isEmpty()) {
            mModel.getSearchCriteria().clear();
            // go create
            mModel.buildBookList(this);
            return;
        }

        // Otherwise handle the back-key as normal.
        super.onBackPressed();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        // don't build the list needlessly
        if (isRecreating() || isFinishing() || isDestroyed()) {
            return;
        }

        // clear the adapter; we'll prepare a new one and meanwhile the view/adapter
        // should obviously NOT try to display the old list.
        // We don't clear the cursor on the model, so we have the option of re-using it.
        mListView.setAdapter(null);
        mAdapter = null;

        // Update the list of bookshelves + set the current bookshelf.
        // If the shelf was changed, it will have triggered a rebuild.
        // This also takes care of the initial build.
        final boolean bookshelfChanged = setBookShelfSpinner();

        final boolean forceRebuildInOnResume = mModel.isForceRebuildInOnResume();
        // always reset for next iteration.
        mModel.setForceRebuildInOnResume(false);

        // This if/else is to be able to debug/log *why* we're rebuilding
        if (bookshelfChanged) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|bookshelfChanged");
            }
            // DO NOTHING. THE CHANGE IN BOOKSHELF ALREADY TRIGGERED A REBUILD.

        } else if (forceRebuildInOnResume) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|isForceRebuildInOnResume");
            }
            // go create
            mModel.buildBookList(this);

        } else if (!mModel.isListLoaded()) {
            //TEST: this branch is almost certainly never reached.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onResume|initial build");
            }
            // go create
            mModel.buildBookList(this);

        } else {
            // no rebuild needed/done, just re-display
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|reusing existing list");
            }
            mModel.createNewListCursor();
            displayList(mModel.getTargetNodes());
        }
    }

    /**
     * Save position when paused.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        mFabMenu.hide();
        saveListPosition();
        super.onPause();
    }

    private void addBySearch(@NonNull final String tag) {
        final Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, tag);
        startActivityForResult(intent, RequestCode.BOOK_SEARCH);
    }

    private void addByIsbn(final boolean scanMode) {
        final Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG)
                .putExtra(BookSearchByIsbnFragment.BKEY_SCAN_MODE, scanMode);
        startActivityForResult(intent, RequestCode.BOOK_SEARCH);
    }

    private void addManually() {
        final Intent intent = new Intent(this, EditBookActivity.class);
        startActivityForResult(intent, RequestCode.BOOK_EDIT);
    }

    /**
     * Display the current cursor in the ListView.
     *
     * @param targetNodes (optional) change the position to the 'best' of these nodes.
     */
    private void displayList(@Nullable final List<RowStateDAO.Node> targetNodes) {

        // create and hookup the list adapter.
        initAdapter(mModel.getListCursor());
        final int count = mModel.getListCursor().getCount();

        final Bookshelf currentBookshelf = mModel.getCurrentBookshelf();
        int position = currentBookshelf.getTopItemPosition();

        // Scroll to the saved position
        if (position >= count) {
            // the list is shorter than it used to be, just scroll to the end
            mLayoutManager.scrollToPosition(position);

        } else if (position != RecyclerView.NO_POSITION) {
            // need to map the row id to the new adapter/cursor. Ideally they will be the same.
            final long actualRowId = mAdapter.getItemId(position);

            // but if they are not equal,
            final long desiredRowId = currentBookshelf.getTopRowId();
            if (actualRowId != desiredRowId) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "position=" + position
                               + "|desiredRowId=" + desiredRowId
                               + "|actualRowId=" + actualRowId);
                }
//                // TODO: the intention is to TRY to FIND the correct position obviously;
//                //  --/++ are placeholders and do not work
//                if (actualRowId < desiredRowId) {
//                    position++;
//                } else {
//                    position--;
//                }
            }

            if (position < 0) {
                position = 0;
            }
            mLayoutManager.scrollToPositionWithOffset(position,
                                                      currentBookshelf.getTopViewOffset());
        }

        // If a target position array is set, then queue a runnable to scroll to the target
        if (targetNodes != null) {
            mListView.post(() -> scrollTo(targetNodes));

        } else {
            // we're at the final position, save it.
            saveListPosition();
        }

        // Prepare the list header fields.
        setHeaders();

        // If we have search criteria enabled (i.e. we're filtering the current list)
        // then we should display the 'up' indicator. See #onBackPressed.
        updateActionBar(mModel.getSearchCriteria().isEmpty());
    }

    /**
     * A new adapter is created each time the list is prepared,
     * as the underlying data can be very different from list to list.
     *
     * @param cursor with list of items
     */
    private void initAdapter(@NonNull final Cursor cursor) {
        mAdapter = new BooklistAdapter(this, mModel.getCurrentStyle(this), cursor);
        mAdapter.setOnRowClickedListener(mOnRowClickedListener);
        mListView.setAdapter(mAdapter);
    }

    /**
     * Save current position information.
     * <p>
     * TODO: https://guides.codepath.com/android/Handling-Configuration-Changes#recyclerview
     * but we'd still need to do some manual stuff to keep the position in between
     * app restarts.
     */
    private void saveListPosition() {
        if (!isDestroyed()) {
            final int position = mLayoutManager.findFirstVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            // The number of pixels offset for the first visible row.
            final int topViewOffset;
            final View topView = mListView.getChildAt(0);
            if (topView != null) {
                topViewOffset = topView.getTop();
            } else {
                topViewOffset = 0;
            }

            mModel.saveListPosition(this, position, topViewOffset, mAdapter.getItemId(position));
        }
    }

    /**
     * Expand/Collapse the current position in the list.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   desired state
     */
    private void expandAllNodes(@IntRange(from = 1) final int topLevel,
                                final boolean expand) {
        // It is possible that the list will be empty, if so, ignore
        if (mLayoutManager.findFirstCompletelyVisibleItemPosition() != RecyclerView.NO_POSITION) {
            // save current position in case anything goes wrong during expanding
            saveListPosition();
            // set new states
            mModel.expandAllNodes(topLevel, expand);
            // Save the new top row position.
            saveListPosition();
            // Finally pass in a new cursor and display the list.
            mModel.createNewListCursor();
            displayList(null);
        }
    }

    /**
     * Refresh the cursor/adapter as needed to make the node visible to the user.
     *
     * @param node to put into view.
     */
    public void refreshNodePosition(@NonNull final RowStateDAO.Node node) {
        // make sure the cursor has valid rows for the new position.
        final Cursor cursor = mModel.getListCursor();
        if (cursor.requery()) {
            mAdapter.notifyDataSetChanged();
            if (node.isExpanded) {
                int position = node.getListPosition();
                // if the user expanded the line at the bottom of the screen,
                final int lastPos = mLayoutManager.findLastCompletelyVisibleItemPosition();
                if ((position + 1 == lastPos) || (position == lastPos)) {
                    // then we move the list a minimum of 2 positions upwards
                    // to make the expanded rows visible. Using 3 for comfort.
                    mLayoutManager.scrollToPosition(position + 3);
                }
            }
        } else {
            if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("requery() failed");
            }
        }
    }

    /**
     * Set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #displayList}
     *
     * @param targetNodes list of rows of which we want one to be visible to the user.
     */
    private void scrollTo(@NonNull final List<RowStateDAO.Node> targetNodes) {
        // sanity check
        if (targetNodes.isEmpty()) {
            return;
        }

        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // empty list
            return;
        }

        final int lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition();
        final int middle = (lastVisibleItemPosition + firstVisibleItemPosition) / 2;

        // Get the first 'target' and make it 'best candidate'
        RowStateDAO.Node best = targetNodes.get(0);
        // distance from currently visible middle row
        int dist = Math.abs(best.getListPosition() - middle);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetNodes.size(); i++) {
            final RowStateDAO.Node node = targetNodes.get(i);
            final int newDist = Math.abs(node.getListPosition() - middle);
            if (newDist < dist) {
                dist = newDist;
                best = node;
            }
        }

        // If the 'best' row is not in view, or at the edge, scroll it into view.
        scrollTo(best);
    }

    /**
     * Scroll the given node into view.
     *
     * @param node to scroll to
     */
    private void scrollTo(@NonNull final RowStateDAO.Node node) {
        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // empty list
            return;
        }

        final int pos = node.getListPosition();
        // We always scroll 1 more then needed for comfort.
        if (pos <= firstVisibleItemPosition) {
            mLayoutManager.scrollToPosition(pos - 1);
        } else if (pos >= mLayoutManager.findLastVisibleItemPosition()) {
            mLayoutManager.scrollToPosition(pos + 1);
        }
        saveListPosition();

        //   // Without this call some positioning may be off by one row.
        //   final int newPos = pos;
        //   mListView.post(() -> {
        //     mListView.smoothScrollToPosition(newPos);
        //     // not entirely sure this is needed
        //     mModel.saveAllNodes();
        //     // but this is
        //     saveListPosition();
        //   });
    }

    /**
     * Populate the BookShelf list in the Spinner and set the current bookshelf/style.
     *
     * @return {@code true} if the selected shelf was changed (or set for the first time).
     */
    private boolean setBookShelfSpinner() {
        @Nullable
        final BooksOnBookshelfModel.BookshelfSpinnerEntry previous =
                (BooksOnBookshelfModel.BookshelfSpinnerEntry) mBookshelfSpinner.getSelectedItem();

        // disable the listener while we add the list.
        mBookshelfSpinner.setOnItemSelectedListener(null);
        // (re)load the list of names
        final int currentPos = mModel.initBookshelfNameList(this);
        // and tell the adapter about it.
        mBookshelfSpinnerAdapter.notifyDataSetChanged();
        // Set the current bookshelf.
        mBookshelfSpinner.setSelection(currentPos);
        // See onResume: the listener WILL get triggered!!
        // (re-)enable the listener
        mBookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);

        final long selected = mModel.getCurrentBookshelf().getId();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "populateBookShelfSpinner"
                       + "|previous=" + previous
                       + "|selected=" + selected);
        }

        // Flag up if the selection was different.
        return previous == null || selected != previous.getBookshelf().getId();
    }

    /**
     * Prepare visibility for the header lines and set the fixed header fields.
     */
    private void setHeaders() {

        // remove the default title to make space for the bookshelf spinner.
        setTitle("");

        String text;
        text = mModel.getHeaderStyleName(this);
        mHeaderStyleNameView.setText(text);
        mHeaderStyleNameView.setVisibility(text != null ? View.VISIBLE : View.GONE);

        text = mModel.getHeaderFilterText(this);
        mHeaderFilterTextView.setText(text);
        mHeaderFilterTextView.setVisibility(text != null ? View.VISIBLE : View.GONE);

        text = mModel.getHeaderBookCount(this);
        mHeaderBookCountView.setText(text);
        mHeaderBookCountView.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    private void onTaskProgress(@NonNull final TaskListener.ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog(message.taskId);
        }
        mProgressDialog.onProgress(message);
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog(final int taskId) {
        FragmentManager fm = getSupportFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);

        // not found? create it
        if (dialog == null) {
            switch (taskId) {
                case R.id.TASK_ID_EXPORT:
                    dialog = ProgressDialogFragment
                            .newInstance(R.string.lbl_backing_up, false, true, 0);
                    break;

                case R.id.TASK_ID_IMPORT:
                    dialog = ProgressDialogFragment
                            .newInstance(R.string.lbl_importing, false, true, 0);
                    break;

                default:
                    throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + "id=" + taskId);
            }
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        switch (taskId) {
            case R.id.TASK_ID_EXPORT:
                dialog.setCanceller(mExportModel.getTask());
                break;

            case R.id.TASK_ID_IMPORT:
                dialog.setCanceller(mImportModel.getTask());
                break;

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + "taskId=" + taskId);
        }
        return dialog;
    }

    /**
     * Export Step 1: show the options to the user.
     */
    private void exportShowOptions() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.txt_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, w) -> ExportHelperDialogFragment
                        .newInstance()
                        .show(getSupportFragmentManager(), ExportHelperDialogFragment.TAG))
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        exportPickUri(new ExportManager(Options.ALL)))
                .create()
                .show();
    }

    /**
     * Export Step 2: prompt the user for a uri to export to.
     *
     * @param helper export configuration
     */
    private void exportPickUri(@NonNull final ExportManager helper) {
        // save the configured helper
        mExportModel.setHelper(helper);
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, mExportModel.getDefaultUriName(this));
        startActivityForResult(intent, RequestCode.EXPORT_PICK_URI);
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final TaskListener.FinishMessage<ExportManager>
                                          message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.status) {
            case Success: {
                // sanity check
                Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);

                final MaterialAlertDialogBuilder dialogBuilder =
                        new MaterialAlertDialogBuilder(this)
                                .setIcon(R.drawable.ic_info)
                                .setTitle(R.string.progress_end_backup_success)
                                .setPositiveButton(R.string.done, (d, which) -> d.dismiss());

                final Uri uri = message.result.getUri();
                final Pair<String, Long> uriInfo = FileUtils.getUriInfo(this, uri);
                String msg = message.result.getResults().createReport(this, uriInfo);
                if (message.result.offerEmail(uriInfo)) {
                    msg += "\n\n" + getString(R.string.confirm_email_export);
                    dialogBuilder.setNeutralButton(R.string.btn_email,
                                                   (d, which) -> onExportEmail(uri));
                }

                dialogBuilder
                        .setMessage(msg)
                        .create()
                        .show();
                break;
            }
            case Cancelled: {
                Snackbar.make(mListView, R.string.progress_end_cancelled,
                              Snackbar.LENGTH_LONG).show();
                break;
            }
            case Failed: {
                // sanity check
                Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
                String msg = message.result.createExceptionReport(this, message.exception);
                new MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_error)
                        .setTitle(R.string.error_backup_failed)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                        .create()
                        .show();
                break;
            }
        }
    }

    /**
     * Create and send an email with the specified Uri.
     *
     * @param uri for the file to email
     */
    private void onExportEmail(@NonNull final Uri uri) {

        final String subject = '[' + getString(R.string.app_name) + "] "
                               + getString(R.string.lbl_books);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("plain/text")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.lbl_send_mail)));

        } catch (@NonNull final NullPointerException e) {
            Logger.error(this, TAG, e);
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setMessage(R.string.error_email_failed)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }

    /**
     * Import Step 1: prompt the user for a uri to export to.
     */
    private void importPickUri() {
        // Import
        // This does not allow multiple saved files like "foo.tar (1)", "foo.tar (2)"
//        String[] mimeTypes = {"application/x-tar", "text/csv"};
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
//                .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                .setType("*/*");
        startActivityForResult(intent, RequestCode.IMPORT_PICK_URI);
    }

    /**
     * Import Step 2: show the options to the user.
     *
     * @param uri file to read from
     */
    private void importShowOptions(@NonNull final Uri uri) {
        // options will be overridden if the import is a CSV.
        ImportManager helper = new ImportManager(Options.ALL, uri);

        final ArchiveContainer container = helper.getContainer(this);
        if (!helper.isSupported(container)) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setMessage(R.string.error_cannot_import)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
            return;
        }

        if (ArchiveContainer.CsvBooks.equals(container)) {
            // use more prudent default options for Csv files.
            helper.setOptions(Options.BOOKS | ImportManager.IMPORT_ONLY_NEW_OR_UPDATED);

            //URGENT: make a backup before ANY csv import!

            // Verify - this can be a dangerous operation
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.lbl_import_book_data)
                    .setMessage(R.string.warning_import_be_cautious)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> ImportHelperDialogFragment
                            .newInstance(helper)
                            .show(getSupportFragmentManager(), ImportHelperDialogFragment.TAG))
                    .create()
                    .show();

        } else {
            // Show a quick-options dialog first.
            // The user can divert to the full options dialog if needed.
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.lbl_import)
                    .setMessage(R.string.txt_import_option_all_books)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setNeutralButton(R.string.btn_options, (d, w) -> ImportHelperDialogFragment
                            .newInstance(helper)
                            .show(getSupportFragmentManager(), ImportHelperDialogFragment.TAG))
                    .setPositiveButton(android.R.string.ok, (d, w) -> mImportModel
                            .startArchiveImportTask(helper))
                    .create()
                    .show();
        }
    }

    /**
     * Import finished/failed: Step 1: Process the result.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final TaskListener.FinishMessage<ImportManager>
                                          message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.status) {
            case Success: {
                // sanity check
                Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
                onImportFinished(R.string.progress_end_import_complete,
                                 message.result.getOptions(),
                                 message.result.getResults());
                break;
            }
            case Cancelled: {
                if (message.result != null) {
                    onImportFinished(R.string.progress_end_import_partially_complete,
                                     message.result.getOptions(),
                                     message.result.getResults());
                } else {
                    Snackbar.make(mListView, R.string.progress_end_cancelled,
                                  Snackbar.LENGTH_LONG).show();
                }
                break;
            }
            case Failed: {
                // sanity check
                Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
                String msg = message.result.createErrorReport(this, message.exception);
                new MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_error)
                        .setTitle(R.string.error_import_failed)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                        .create()
                        .show();
                break;
            }
        }
    }

    /**
     * Import finished: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     * @param options what was actually imported
     * @param results details of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  final int options,
                                  @NonNull final ImportResults results) {

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_info)
                .setTitle(titleId)
                .setMessage(results.createReport(this))
                .setPositiveButton(R.string.done, (d, w) -> {
                    if (options != 0) {
                        if ((options & Options.STYLES) != 0) {
                            // Force a refresh of the list of all user styles.
                            BooklistStyle.StyleDAO.clear();
                        }
                        if ((options & Options.PREFS) != 0) {
                            // Refresh the preferred bookshelf. This also refreshes its style.
                            mModel.reloadCurrentBookshelf(this);
                        }

                        // styles, prefs, books, covers,... it all requires a rebuild.
                        setBookShelfSpinner();
                        mListView.setAdapter(null);
                        mAdapter = null;
                        mModel.buildBookList(this);
                    }

                })
                .create()
                .show();
    }
}
