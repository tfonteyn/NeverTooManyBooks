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
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
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
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ExportTaskModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ImportTaskModel;
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
 * URGENT: turn the list + list header components into a proper fragment.
 * That will allow us to have a central FragmentContainer, where we can swap in
 * other fragments without leaving the activity. e.g. import/export/... etc...
 * Something for version 1.1 presumably.
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";
    public static final String BKEY_START_BACKUP = TAG + ":startBackup";

    /**
     * List header.
     * Views for the current row level-text.
     * These are shown in the header of the list (just below the bookshelf spinner) while scrolling.
     */
    private final TextView[] mHeaderRowLevelTextView = new TextView[2];
    /** simple indeterminate progress spinner to show while getting the list of books. */
    private ProgressBar mProgressBar;
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
    /** Listener for clicks on the list. */
    private final BooklistAdapter.OnRowClickedListener mOnRowClickedListener =
            new BooklistAdapter.OnRowClickedListener() {

                /**
                 * User clicked on a row.
                 * <ul>
                 *      <li>Book: open the details screen.</li>
                 *      <li>Not a book: expand/collapse the section as appropriate.</li>
                 * </ul>
                 *
                 * <br><br>{@inheritDoc}
                 */
                @Override
                public void onItemClick(final int position) {
                    final Cursor cursor = mModel.getListCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return;
                    }
                    final RowDataHolder rowData = new CursorRow(cursor);

                    // If it's a book, open the details screen.
                    if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        final long rowId = rowData.getLong(DBDefinitions.KEY_PK_ID);
                        final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                        // Note we (re)create the flat table *every time* the user click a book.
                        // This guarantees an exact match in rowId'
                        // (which turns out tricky if we cache the table - ENHANCE: re-implement caching)
                        final String navTableName = mModel.createFlattenedBooklist();
                        final Intent intent = new Intent(BooksOnBookshelf.this,
                                                         BookDetailsActivity.class)
                                .putExtra(DBDefinitions.KEY_PK_ID, bookId)
                                .putExtra(BookDetailsFragmentViewModel.BKEY_NAV_TABLE, navTableName)
                                .putExtra(BookDetailsFragmentViewModel.BKEY_NAV_ROW_ID, rowId);
                        startActivityForResult(intent, RequestCode.BOOK_VIEW);

                    } else {
                        // it's a level, expand/collapse.
                        final long rowId = rowData.getInt(DBDefinitions.KEY_BL_LIST_VIEW_ROW_ID);
                        toggleNode(position, rowId, RowStateDAO.DesiredNodeState.Toggle, 1);
                    }
                }

                /**
                 * User long-clicked on a row. Bring up a context menu as appropriate.
                 *
                 * <br><br>{@inheritDoc}
                 */
                @Override
                public boolean onItemLongClick(final int position) {
                    final Cursor cursor = mModel.getListCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return false;
                    }
                    final RowDataHolder rowData = new CursorRow(cursor);

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
    private final OptionsDialogBase.OptionsListener<ImportManager> mImportOptionsListener =
            new OptionsDialogBase.OptionsListener<ImportManager>() {
                @Override
                public void onOptionsSet(@NonNull final ImportManager options) {
                    mImportModel.startArchiveImportTask(options);
                }
            };
    /** The dropdown button to select a Bookshelf. */
    private Spinner mBookshelfSpinner;
    /** Whether to show level-header - this depends on the current style. */
    private boolean mShowLevelHeaders;
    /** Define a scroller to update header detail when the top row changes. */
    private final RecyclerView.OnScrollListener mUpdateHeaderScrollListener =
            new RecyclerView.OnScrollListener() {
                public void onScrolled(@NonNull final RecyclerView recyclerView,
                                       final int dx,
                                       final int dy) {
                    // Need to check isDestroyed() because BooklistCursor misbehaves when
                    // activity terminates and closes cursor
                    if (mModel.getPreviousFirstVisibleItemPosition()
                        != mLayoutManager.findFirstVisibleItemPosition()
                        && !isDestroyed()
                        && mShowLevelHeaders) {
                        setHeaderLevelText();
                    }
                }
            };
    /** The normal FAB button; opens or closes the FAB menu. */
    private FloatingActionButton mFabButton;
    /** Array with the submenu FAB buttons. Element 0 shows at the bottom. */
    private ExtendedFloatingActionButton[] mFabMenuItems;
    /** Overlay enabled while the FAB menu is shown to intercept clicks and close the FAB menu. */
    private View mFabOverlay;

//    private BooksonbookshelfBinding mVb;
    /** Define a scroller to show, or collapse/hide the FAB. */
    private final RecyclerView.OnScrollListener mUpdateFABVisibility =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull final RecyclerView recyclerView,
                                       final int dx,
                                       final int dy) {
                    if (dy > 0 || dy < 0 && mFabButton.isShown()) {
                        hideFABMenu();
                        mFabButton.hide();
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull final RecyclerView recyclerView,
                                                 final int newState) {
                    // This method is not called when the fast scroller stops scrolling but
                    // we can ignore that as in practice a minuscule swipe brings the FAB back.
                    if (newState == RecyclerView.SCROLL_STATE_IDLE
                        || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        showFABMenu(false);
                        mFabButton.show();
                    }
                    super.onScrollStateChanged(recyclerView, newState);
                }
            };

    @Override
    protected void onSetContentView() {
//        mVb = BooksonbookshelfBinding.inflate(getLayoutInflater());
//        setContentView(mVb.getRoot());
        setContentView(R.layout.booksonbookshelf);

        mBookshelfSpinner = findViewById(R.id.bookshelf_spinner);
        mHeaderStyleNameView = findViewById(R.id.style_name);
        mHeaderFilterTextView = findViewById(R.id.filter_text);
        mHeaderBookCountView = findViewById(R.id.book_count);
        mHeaderRowLevelTextView[0] = findViewById(R.id.level_1_text);
        mHeaderRowLevelTextView[1] = findViewById(R.id.level_2_text);
        mListView = findViewById(android.R.id.list);

        mProgressBar = findViewById(R.id.progressBar);

        mFabButton = findViewById(R.id.fab);
        mFabOverlay = findViewById(R.id.fabOverlay);
        // Make SURE that the array length fits the options list below.
        mFabMenuItems = new ExtendedFloatingActionButton[5];
        mFabMenuItems[0] = findViewById(R.id.fab0);
        mFabMenuItems[1] = findViewById(R.id.fab1);
        mFabMenuItems[2] = findViewById(R.id.fab2);
        mFabMenuItems[3] = findViewById(R.id.fab3);
        mFabMenuItems[4] = findViewById(R.id.fab4);
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
        mListView.addOnScrollListener(mUpdateHeaderScrollListener);
        mListView.addOnScrollListener(mUpdateFABVisibility);
        mListView.addItemDecoration(new TopLevelItemDecoration(this));
        FastScroller.init(mListView);

        // initialize but do not populate the list;  the latter is done in setBookShelfSpinner
        mBookshelfSpinnerAdapter = new ArrayAdapter<>(
                this, R.layout.bookshelf_spinner_selected, mModel.getBookshelfSpinnerList());
        mBookshelfSpinnerAdapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item);
        mBookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);

        mFabButton.setOnClickListener(v -> showFABMenu(!mFabMenuItems[0].isShown()));
        mFabMenuItems[0].setOnClickListener(v -> addByIsbn(true));
        mFabMenuItems[1].setOnClickListener(v -> addByIsbn(false));
        mFabMenuItems[2].setOnClickListener(v -> addBySearch(BookSearchByTextFragment.TAG));
        mFabMenuItems[3].setOnClickListener(v -> addManually());
        if (Prefs.showEditBookTabNativeId(this)) {
            mFabMenuItems[4].setEnabled(true);
            mFabMenuItems[4].setOnClickListener(v -> addBySearch(BookSearchByNativeIdFragment.TAG));
        } else {
            mFabMenuItems[4].setEnabled(false);
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

    /**
     * Hide the FAB menu if it's showing. Does not affect the FAB button itself.
     */
    private void hideFABMenu() {
        if (mFabMenuItems[0].isShown()) {
            showFABMenu(false);
        }
    }

    /**
     * When the user clicks the FAB button, we open/close the FAB menu and change the FAB icon.
     *
     * @param show flag
     */
    private void showFABMenu(final boolean show) {
        if (show) {
            mFabButton.setImageResource(R.drawable.ic_close);
            // mFabOverlay overlaps the whole screen and intercepts clicks.
            // This does not include the ToolBar.
            mFabOverlay.setVisibility(View.VISIBLE);
            mFabOverlay.setOnClickListener(v -> hideFABMenu());
        } else {
            mFabButton.setImageResource(R.drawable.ic_add);
            mFabOverlay.setVisibility(View.GONE);
            mFabOverlay.setOnClickListener(null);
        }

        final float baseY = getResources().getDimension(R.dimen.fab_menu_translationY_base);
        final float deltaY = getResources().getDimension(R.dimen.fab_menu_translationY_delta);

        final float baseX = getResources().getDimension(R.dimen.fab_menu_translationX);
        final float deltaX = getResources().getDimension(R.dimen.fab_menu_translationX_delta);

        // Test for split-screen layouts (or really small devices?)
        // Having more then 4 FAB buttons is not really a good UI design
        // But this just about fits our 5...
        //TODO: use resource qualifiers instead.
        final boolean smallScreen = getResources().getConfiguration().screenHeightDp < 400;

        int i = 0;
        for (ExtendedFloatingActionButton fab : mFabMenuItems) {
            // allow for null items
            if (fab != null && fab.isEnabled()) {
                if (show) {
                    fab.show();
                    if (!smallScreen) {
                        // on top of base FAB
                        fab.animate().translationX(baseX);
                        fab.animate().translationY(baseY + ((i + 1) * deltaY));
                    } else {
                        // to the left of FAB and up
                        fab.animate().translationX(baseX + deltaX);
                        fab.animate().translationY(i * deltaY);
                    }
                } else {
                    fab.animate().translationX(0);
                    fab.animate().translationY(0);
                    fab.hide();
                }
                i++;
            }
        }
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

        final boolean showECPreferred = mModel.getCurrentStyle(this).getTopLevel(this) > 1;
        menu.findItem(R.id.MENU_LEVEL_PREFERRED_COLLAPSE).setVisible(showECPreferred);

        hideFABMenu();

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        hideFABMenu();

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
                                        @NonNull final RowDataHolder rowData) {
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

        // if it's a level, add the expand option
        if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) != BooklistGroup.BOOK) {
            int menuOrder = getResources().getInteger(R.integer.MENU_ORDER_LEVEL_TOGGLE);
            if (menu.size() > 0) {
                menu.add(Menu.NONE, R.id.MENU_DIVIDER, menuOrder++, "")
                    .setEnabled(false);
            }
            menu.add(Menu.NONE, R.id.MENU_LEVEL_EXPAND, menuOrder, R.string.lbl_level_expand)
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

        final RowDataHolder rowData = new CursorRow(cursor);

        final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);

        switch (menuItem) {
            case R.id.MENU_BOOK_EDIT: {
                final Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookId);
                startActivityForResult(intent, RequestCode.BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                final String title = rowData.getString(DBDefinitions.KEY_TITLE);
                final List<Author> authors = mModel.getDb().getAuthorsByBookId(bookId);
                StandardDialogs.deleteBook(this, title, authors, () -> {
                    mModel.getDb().deleteBook(this, bookId);
                    mBookChangedListener
                            .onBookChanged(bookId, BookChangedListener.BOOK_DELETED, null);
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Book book = new Book(bookId, mModel.getDb());
                final Intent dupIntent = new Intent(this, EditBookActivity.class)
                        .putExtra(Book.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, RequestCode.BOOK_DUPLICATE);
                return true;
            }

            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle the read status
                if (mModel.getDb()
                          .setBookRead(bookId, !rowData.getBoolean(DBDefinitions.KEY_READ))) {
                    mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                EditLenderDialogFragment
                        .newInstance(bookId, rowData.getString(DBDefinitions.KEY_TITLE))
                        .show(getSupportFragmentManager(), EditLenderDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mModel.getDb().lendBook(bookId, null);
                mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                final Book book = new Book(bookId, mModel.getDb());
                startActivity(book.getShareBookIntent(this));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                Snackbar.make(mListView, R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
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

                ArrayList<Long> bookIdList;
                switch (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP)) {

                    case BooklistGroup.BOOK: {
                        bookIdList = new ArrayList<>();
                        bookIdList.add(bookId);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_TITLE));
                        break;
                    }
                    case BooklistGroup.AUTHOR: {
                        bookIdList = mModel.getDb().getBookIdsByAuthor(
                                rowData.getLong(DBDefinitions.KEY_FK_AUTHOR));
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                        break;
                    }
                    case BooklistGroup.SERIES: {
                        bookIdList = mModel.getDb().getBookIdsBySeries(
                                rowData.getLong(DBDefinitions.KEY_FK_SERIES));
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_SERIES_TITLE));
                        break;
                    }
                    case BooklistGroup.PUBLISHER: {
                        String publisher = rowData.getString(DBDefinitions.KEY_PUBLISHER);
                        bookIdList = mModel.getDb().getBookIdsByPublisher(publisher);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE, publisher);
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

                intent.putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList);
                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT: {
                final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                final Series series = mModel.getDb().getSeries(seriesId);
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
                    mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                final Series series =
                        mModel.getDb().getSeries(rowData.getLong(DBDefinitions.KEY_FK_SERIES));
                if (series != null) {
                    StandardDialogs.deleteSeries(this, series, () -> {
                        mModel.getDb().deleteSeries(this, series.getId());
                        mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
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
                final Author author = mModel.getDb().getAuthor(authorId);
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
                    mBookChangedListener.onBookChanged(0, BookChangedListener.AUTHOR, null);
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
                final long rowId = rowData.getInt(DBDefinitions.KEY_BL_LIST_VIEW_ROW_ID);
                toggleNode(position, rowId, RowStateDAO.DesiredNodeState.Expanded,
                           mModel.getCurrentStyle(this).getGroupCount());
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
                        // always save a new style to the database
                        if (style.getId() == 0) {
                            mModel.saveStyle(style);
                        }
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
                                BooklistStyle.Helper.clear();
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
                        mExportModel.startArchiveExportTask(uri);
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
        // If the FAB menu is showing, hide it.
        if (mFabMenuItems[0].isShown()) {
            showFABMenu(false);
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
            displayList(mModel.getTargetRows());
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
        hideFABMenu();
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
     * Set the desired state on the given node.
     * Called when the user taps on a level-row, or from the row context menu.
     *
     * @param position           of the row in the list view
     * @param rowId              of the node in the list
     * @param desiredNodeState   the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     */
    public void toggleNode(final int position,
                           final long rowId,
                           final RowStateDAO.DesiredNodeState desiredNodeState,
                           final int relativeChildLevel) {

        // update the row DAO table
        final boolean isExpanded = mModel.toggleNode(rowId, desiredNodeState, relativeChildLevel);

        // make sure the cursor has valid rows for the new position.
        final Cursor cursor = mModel.getListCursor();
        if (cursor.requery()) {
            mAdapter.notifyDataSetChanged();
            if (isExpanded) {
                // if the user expanded the line at the bottom of the screen,
                final int lastPos = mLayoutManager.findLastCompletelyVisibleItemPosition();
                if ((position + 1 == lastPos) || (position == lastPos)) {
                    // then we move the list a minimum of 2 positions upwards
                    // to make the expanded rows visible. Using 3 for comfort.
                    mListView.scrollToPosition(position + 3);
                }
            }
        } else {
            if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("requery() failed");
            }
        }
    }

    /**
     * Display the current cursor in the ListView.
     *
     * @param targetRows (optional) change the position to targetRows.
     */
    private void displayList(@Nullable final List<RowStateDAO.Node> targetRows) {

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
                    Logger.warn(this, TAG, "position=" + position
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
        if (targetRows != null) {
            mListView.post(() -> scrollToTarget(targetRows));

        } else {
            // we're at the final position, save it.
            saveListPosition();
        }

        // Prepare the list header fields.
        mShowLevelHeaders = setHeaders();
        // Set the initial details to the current first visible row (if any).
        if (count > 0 && mShowLevelHeaders) {
            setHeaderLevelText();
        }

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
     * Set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #displayList}
     *
     * @param targetRows list of rows of which we want one to be visible to the user.
     */
    private void scrollToTarget(@NonNull final List<RowStateDAO.Node> targetRows) {
        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // empty list
            return;
        }

        final int lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition();
        final int centre = (lastVisibleItemPosition + firstVisibleItemPosition) / 2;

        // Get the first 'target' and make it 'best candidate'
        int best = targetRows.get(0).getListPosition();
        // distance from currently visible centre row
        int dist = Math.abs(best - centre);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetRows.size(); i++) {
            int ri = targetRows.get(i).getListPosition();
            int newDist = Math.abs(ri - centre);
            if (newDist < dist) {
                dist = newDist;
                best = ri;
            }
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "scrollToTarget"
                       + "|targetRows=" + targetRows
                       + "|best=" + best);
        }

        // If the 'best' row is not in view, or at the edge, scroll it into view.
        if (best <= firstVisibleItemPosition) {
            mLayoutManager.scrollToPosition(best - 1);
        } else if (best >= lastVisibleItemPosition) {
            mLayoutManager.scrollToPosition(best + 1);
        }
        saveListPosition();

//            // Without this call some positioning may be off by one row.
//            final int newPos = best;
//            mListView.post(() -> {
//                mListView.smoothScrollToPosition(newPos);
//                // not entirely sure this is needed
//                mModel.saveAllNodes();
//                // but this is
//                saveListPosition();
//            });
//        }
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
     *
     * @return {@code true} if level-texts are used.
     */
    private boolean setHeaders() {

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

        final BooklistStyle style = mModel.getCurrentStyle(this);

        boolean atLeastOne = false;

        // for each level, set the visibility of the views.
        for (int i = 0; i < mHeaderRowLevelTextView.length; i++) {
            if (i < style.getGroupCount()
                && style.showHeader(this, BooklistStyle.HEADER_LEVELS[i])) {
                // the actual text will be filled in as/when the user scrolls
                mHeaderRowLevelTextView[i].setText("");
                mHeaderRowLevelTextView[i].setVisibility(View.VISIBLE);
                atLeastOne = true;
            } else {
                mHeaderRowLevelTextView[i].setVisibility(View.GONE);
            }
        }

        return atLeastOne;
    }

    /**
     * Update the list header to match the current top item.
     */
    private void setHeaderLevelText() {
        final int position = mLayoutManager.findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        mModel.setPreviousFirstVisibleItemPosition(position);
        // use visibility which was set in {@link #initHeaders}
        if (mHeaderRowLevelTextView[0].getVisibility() == View.VISIBLE
            || mHeaderRowLevelTextView[1].getVisibility() == View.VISIBLE) {
            final String[] lines = mAdapter.getPopupText(position);
            if (lines != null) {
                for (int i = 0; i < lines.length; i++) {
                    if (mHeaderRowLevelTextView[i].getVisibility() == View.VISIBLE) {
                        mHeaderRowLevelTextView[i].setText(lines[i]);
                    }
                }
            }
        }
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
                    throw new UnexpectedValueException("taskId=" + taskId);
            }
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        switch (taskId) {
            case R.id.TASK_ID_EXPORT:
                dialog.setCancellable(mExportModel.getTask());
                break;

            case R.id.TASK_ID_IMPORT:
                dialog.setCancellable(mImportModel.getTask());
                break;

            default:
                throw new UnexpectedValueException("taskId=" + taskId);
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
                onImportFinished(R.string.progress_end_import_complete,
                                 message.result.getOptions(),
                                 message.result.getResults());
                break;
            }
            case Cancelled: {
                onImportFinished(R.string.progress_end_import_partially_complete,
                                 message.result.getOptions(),
                                 message.result.getResults());
                break;
            }
            case Failed: {
                String msg = message.result.createExceptionReport(this, message.exception);
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
                            BooklistStyle.Helper.clear();
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
