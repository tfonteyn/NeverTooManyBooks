/*
 * @Copyright 2020 HardBackNutter
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
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
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrSendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.SpinnerInteractionListener;
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
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";

    /** FragmentResultListener request key. */
    private static final String RK_MENU_PICKER = MenuPickerDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_STYLE_PICKER = StylePickerDialogFragment.TAG + ":rk";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LENDER = EditLenderDialogFragment.TAG + ":rk";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_AUTHOR = EditAuthorDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_SERIES = EditSeriesDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_PUBLISHER = EditPublisherDialogFragment.TAG + ":rk";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_COLOR = EditColorDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_FORMAT = EditFormatDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_GENRE = EditGenreDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LANGUAGE = EditLanguageDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LOCATION = EditLocationDialogFragment.TAG + ":rk";

    /** Goodreads authorization task. */
    private GrAuthTask mGrAuthTask;

    /** Multi-type adapter to manage list connection to cursor. */
    private BooklistAdapter mAdapter;

    /** The Activity ViewModel. */
    private BooksOnBookshelfModel mModel;

    /** Goodreads send-book task. */
    private GrSendOneBookTask mGrSendOneBookTask;

    /**
     * Full progress dialog to show while running a task.
     * Note that the {@link #mModel} does not use this dialog (i.e. never sends progress messages)
     * but just the light weight ProgressBar.
     */
    @Nullable
    private ProgressDialogFragment mProgressDialog;

    /** View binding. */
    private BooksonbookshelfBinding mVb;

    /** List layout manager. */
    private LinearLayoutManager mLayoutManager;

    /** Listener for the Bookshelf Spinner. */
    private final SpinnerInteractionListener mOnBookshelfSelectionChanged =
            new SpinnerInteractionListener() {
                private boolean userInteraction;

                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(@NonNull final View v,
                                       @NonNull final MotionEvent event) {
                    userInteraction = true;
                    return false;
                }

                @Override
                public void onItemSelected(@NonNull final AdapterView<?> parent,
                                           @Nullable final View view,
                                           final int position,
                                           final long id) {
                    if (userInteraction) {
                        userInteraction = false;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                            Log.d(TAG, "OnItemSelectedListener|onItemSelected"
                                       + "|position=" + position
                                       + "|id=" + id
                                       + "|view=" + view);
                        }

                        if (view == null) {
                            return;
                        }

                        // check if the selection is actually different from the previous one
                        final boolean isChanged = id != mModel.getSelectedBookshelf().getId();
                        if (isChanged) {
                            saveListPosition();
                            // make the new shelf the current and rebuild
                            mModel.setSelectedBookshelf(parent.getContext(), id);
                            buildBookList();
                        }
                    }
                }
            };

    /** React to row changes made. ENHANCE: update the modified row without a rebuild. */
    private final BookChangedListener mBookChangedListener = this::onBookChange;

    /** React to the user selecting a style to apply. */
    private final StylePickerDialogFragment.OnResultListener mOnStylePickerListener =
            this::onStyleChanged;

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
                    final Cursor cursor = mAdapter.getCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return;
                    }

                    final DataHolder rowData = new CursorRow(cursor);

                    // If it's a book, open the details screen.
                    if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        openBookDetails(rowData);

                    } else {
                        // it's a level, expand/collapse.
                        setNode(rowData, BooklistNode.NEXT_STATE_TOGGLE, 1);
                    }
                }

                /**
                 * User long-clicked on a row. Bring up a context menu as appropriate.
                 */
                @Override
                public boolean onItemLongClick(final int position) {
                    final Cursor cursor = mAdapter.getCursor();
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

                        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
                            MenuPickerDialogFragment
                                    .newInstance(RK_MENU_PICKER, title, menu, position)
                                    .show(getSupportFragmentManager(),
                                          MenuPickerDialogFragment.TAG);
                        } else {
                            new MenuPicker(BooksOnBookshelf.this, title, menu, position,
                                           BooksOnBookshelf.this::onContextItemSelected)
                                    .show();
                        }
                    }
                    return true;
                }
            };

    /** React to the user selecting a context menu option. (MENU_PICKER_USES_FRAGMENT). */
    private final MenuPickerDialogFragment.OnResultListener mMenuPickerListener =
            this::onContextItemSelected;

    /** Encapsulates the FAB button/menu. */
    private FabMenu mFabMenu;
    /** The adapter used to fill the mBookshelfSpinner. */
    private BookshelfSpinnerAdapter mBookshelfSpinnerAdapter;

    /**
     * Open the {@link BookDetailsActivity} for the given book.
     *
     * @param rowData with book data
     */
    public void openBookDetails(@NonNull final DataHolder rowData) {
        final Intent intent = new Intent(this, BookDetailsActivity.class)
                // the book to display
                .putExtra(DBDefinitions.KEY_PK_ID,
                          rowData.getLong(DBDefinitions.KEY_FK_BOOK))
                // the current list table, so the user can swipe
                // to the next/previous book
                .putExtra(BookDetailsFragmentViewModel.BKEY_LIST_TABLE_NAME,
                          mModel.getBooklistTableName())
                // The row id in the list table of the given book.
                // Keep in mind a book can occur multiple times,
                // so we need to pass the specific one.
                .putExtra(BookDetailsFragmentViewModel.BKEY_LIST_TABLE_ROW_ID,
                          rowData.getLong(DBDefinitions.KEY_PK_ID))
                // some style elements are applicable for the details screen
                .putExtra(BooklistStyle.BKEY_STYLE_UUID,
                          mModel.getCurrentStyle(this).getUuid());
        startActivityForResult(intent, RequestCode.BOOK_VIEW);
    }

    @Override
    protected void onSetContentView() {
        mVb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());

        mFabMenu = new FabMenu(mVb.fab, mVb.fabOverlay,
                               mVb.fab0, mVb.fab1, mVb.fab2, mVb.fab3, mVb.fab4);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // remove the default title to make space for the bookshelf spinner.
        setTitle("");

        final FragmentManager fm = getSupportFragmentManager();

        fm.setFragmentResultListener(RK_EDIT_AUTHOR, this, mBookChangedListener);
        fm.setFragmentResultListener(RK_EDIT_SERIES, this, mBookChangedListener);
        fm.setFragmentResultListener(RK_EDIT_PUBLISHER, this, mBookChangedListener);

        fm.setFragmentResultListener(RK_EDIT_COLOR, this, mBookChangedListener);
        fm.setFragmentResultListener(RK_EDIT_FORMAT, this, mBookChangedListener);
        fm.setFragmentResultListener(RK_EDIT_GENRE, this, mBookChangedListener);
        fm.setFragmentResultListener(RK_EDIT_LANGUAGE, this, mBookChangedListener);
        fm.setFragmentResultListener(RK_EDIT_LOCATION, this, mBookChangedListener);

        fm.setFragmentResultListener(RK_EDIT_LENDER, this, mBookChangedListener);

        fm.setFragmentResultListener(RK_STYLE_PICKER, this, mOnStylePickerListener);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            fm.setFragmentResultListener(RK_MENU_PICKER, this, mMenuPickerListener);
        }

        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        mModel = new ViewModelProvider(this).get(BooksOnBookshelfModel.class);
        mModel.init(this, getIntent().getExtras(), savedInstanceState);
        mModel.onCancelled().observe(this, this::onBuildFailed);
        mModel.onFailure().observe(this, this::onBuildFailed);
        mModel.onFinished().observe(this, this::onBuildFinished);

        mGrAuthTask = new ViewModelProvider(this).get(GrAuthTask.class);
        mGrAuthTask.onProgressUpdate().observe(this, this::onProgress);
        mGrAuthTask.onCancelled().observe(this, this::onCancelled);
        mGrAuthTask.onFailure().observe(this, this::onGrFailure);
        mGrAuthTask.onFinished().observe(this, this::onGrFinished);

        mGrSendOneBookTask = new ViewModelProvider(this).get(GrSendOneBookTask.class);
        mGrSendOneBookTask.onProgressUpdate().observe(this, this::onProgress);
        mGrSendOneBookTask.onCancelled().observe(this, this::onCancelled);
        mGrSendOneBookTask.onFailure().observe(this, this::onGrFailure);
        mGrSendOneBookTask.onFinished().observe(this, this::onGrFinished);

        // enable the navigation menus
        setNavigationItemVisibility(R.id.nav_manage_list_styles, true);
        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);
        setNavigationItemVisibility(R.id.nav_export, true);
        setNavigationItemVisibility(R.id.nav_import, true);
        setNavigationItemVisibility(R.id.nav_goodreads, GoodreadsManager.isShowSyncMenus(prefs));

        // The booklist.
        mLayoutManager = (LinearLayoutManager) mVb.list.getLayoutManager();
        mVb.list.addItemDecoration(new TopLevelItemDecoration(this));
        FastScroller.attach(mVb.list);

        // TEST: Number of views to cache offscreen: default is 2
        mVb.list.setItemViewCacheSize(20);
        mVb.list.setDrawingCacheEnabled(true);
        mVb.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);


        // initialise adapter without a cursor.
        // Not creating and setting it in here, creates issues with Android internals.
        createAdapter(null);


        // Setup the Bookshelf spinner;
        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        mBookshelfSpinnerAdapter = new BookshelfSpinnerAdapter(this, mModel.getBookshelfList());
        mVb.bookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);
        mVb.bookshelfSpinner.setOnTouchListener(mOnBookshelfSelectionChanged);
        mVb.bookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);


        mFabMenu.attach(mVb.list);
        mFabMenu.setOnClickListener(this::onFabMenuItemSelected);
        // mVb.fab4
        mFabMenu.getItem(4).setEnabled(Prefs.showEditBookTabExternalId(prefs));

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_book_list, null);
        }
    }

    /**
     * Create the adapter and (optionally) set the cursor.
     *
     * <strong>Developer note:</strong>
     * There seems to be no other solution but to always create the adapter
     * in {@link #onCreate} (with null cursor) and RECREATE it when we have a valid cursor.
     * Tested several strategies, but it seems to be impossible to RELIABLY
     * flush the adapter cache of View/ViewHolder.
     * i.e. {@link RecyclerView#getRecycledViewPool()} .clear() is not enough!
     * <p>
     * Not setting an adapter at all in {@link #onCreate} is not a solution either...
     * crashes assured! Also see {@link #buildBookList}.
     *
     * @param cursor to use, or {@code null} for initial creation.
     */
    public void createAdapter(@Nullable final Cursor cursor) {
        mAdapter = new BooklistAdapter(this);
        if (cursor != null) {
            mAdapter.setOnRowClickedListener(mOnRowClickedListener);
            mAdapter.setCursor(this, cursor, mModel.getCurrentStyle(this));
        }

        // No, we do NOT have a fixed size for each row
        //mVb.list.setHasFixedSize(false);
        mVb.list.setAdapter(mAdapter);
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
        mModel.setForceRebuildInOnResume(true);
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
            // The ACTION_VIEW as set in src/main/res/xml/searchable.xml/searchSuggestIntentAction
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
                final Intent intent = new Intent(this, FTSSearchActivity.class);
                mModel.getSearchCriteria().to(intent);
                startActivityForResult(intent, RequestCode.ADVANCED_LOCAL_SEARCH);
                return true;
            }

            case R.id.nav_manage_bookshelves: {
                // overridden, so we can pass the current bookshelf id.
                final Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(BKEY_FRAGMENT_TAG, EditBookshelvesFragment.TAG)
                        .putExtra(EditBookshelvesModel.BKEY_CURRENT_BOOKSHELF,
                                  mModel.getSelectedBookshelf().getId());
                startActivityForResult(intent, RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES);
                return true;
            }

            case R.id.nav_manage_list_styles: {
                // overridden, so we can pass the current style uuid.
                final Intent intent = new Intent(this, PreferredStylesActivity.class)
                        .putExtra(BooklistStyle.BKEY_STYLE_UUID,
                                  mModel.getCurrentStyle(this).getUuid());
                startActivityForResult(intent, RequestCode.NAV_PANEL_MANAGE_STYLES);
                return true;
            }

            default:
                return super.onNavigationItemSelected(item);
        }
    }

    private void onFabMenuItemSelected(@NonNull final View view) {
        switch (view.getId()) {
            case R.id.fab0:
                addByIsbn(true);
                break;

            case R.id.fab1:
                addByIsbn(false);
                break;

            case R.id.fab2:
                addBySearch(BookSearchByTextFragment.TAG);
                break;

            case R.id.fab3:
                addManually();
                break;

            case R.id.fab4:
                addBySearch(BookSearchByExternalIdFragment.TAG);
                break;

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + view);
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
        mFabMenu.hideMenu();

        final boolean showECPreferred = mModel.getCurrentStyle(this).getTopLevel(this) > 1;
        menu.findItem(R.id.MENU_LEVEL_PREFERRED_COLLAPSE).setVisible(showECPreferred);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        mFabMenu.hideMenu();

        switch (item.getItemId()) {
            case R.id.MENU_SORT: {
                StylePickerDialogFragment
                        .newInstance(RK_STYLE_PICKER,
                                     mModel.getCurrentStyle(this), false)
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
                        .putExtra(Book.BKEY_BOOK_ID_LIST, bookIdList);
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
    boolean onCreateContextMenu(@NonNull final Menu menu,
                                @NonNull final DataHolder rowData) {
        menu.clear();

        final int rowGroupId = rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                getMenuInflater().inflate(R.menu.book, menu);
                getMenuInflater().inflate(R.menu.sm_view_on_site, menu);
                getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                final SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(this);

                final boolean isRead = rowData.getBoolean(DBDefinitions.KEY_READ);
                menu.findItem(R.id.MENU_BOOK_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isRead);

                // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
                final boolean useLending = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE);
                final boolean isAvailable = mModel.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
                    .setVisible(GoodreadsManager.isShowSyncMenus(prefs));

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
                if (rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER) != 0) {
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

        int menuOrder = getResources().getInteger(R.integer.MENU_ORDER_NEXT_MISSING_COVER);
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

        final Cursor cursor = mAdapter.getCursor();
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
                final List<Author> authors = mModel.getAuthorsByBookId(bookId);
                StandardDialogs.deleteBook(this, title, authors, () -> {
                    if (mModel.deleteBook(this, bookId)) {
                        onBookChange(bookId, BookChangedListener.BOOK_DELETED, null);
                    }
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Book book = mModel.getBook(rowData);
                if (book != null) {
                    final Intent intent = new Intent(this, EditBookActivity.class)
                            .putExtra(Book.BKEY_DATA_BUNDLE, book.duplicate());
                    startActivityForResult(intent, RequestCode.BOOK_DUPLICATE);
                }
                return true;
            }

            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle the read status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_READ);
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                if (mModel.setBookRead(bookId, status)) {
                    onBookChange(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                EditLenderDialogFragment
                        .newInstance(RK_EDIT_LENDER,
                                     bookId, rowData.getString(DBDefinitions.KEY_TITLE))
                        .show(getSupportFragmentManager(), EditLenderDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                mModel.lendBook(bookId, null);
                onBookChange(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                final Book book = mModel.getBook(rowData);
                if (book != null) {
                    startActivity(book.getShareIntent(this));
                }
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                Snackbar.make(mVb.list, R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                mGrSendOneBookTask.startTask(bookId);
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
                              .putExtra(Book.BKEY_BOOK_ID_LIST,
                                        bookIdList);
                        break;
                    }
                    case BooklistGroup.AUTHOR: {
                        final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED))
                              .putExtra(Book.BKEY_BOOK_ID_LIST,
                                        mModel.getBookIdsByAuthor(authorId));
                        break;
                    }
                    case BooklistGroup.SERIES: {
                        final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_SERIES_TITLE))
                              .putExtra(Book.BKEY_BOOK_ID_LIST,
                                        mModel.getBookIdsBySeries(seriesId));
                        break;
                    }
                    case BooklistGroup.PUBLISHER: {
                        final long publisherId = rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_PUBLISHER_NAME))
                              .putExtra(Book.BKEY_BOOK_ID_LIST,
                                        mModel.getBookIdsByPublisher(publisherId));
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
                final Series series = mModel.getSeries(rowData);
                if (series != null) {
                    EditSeriesDialogFragment
                            .newInstance(RK_EDIT_SERIES, series)
                            .show(getSupportFragmentManager(), EditSeriesDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_SERIES_SET_COMPLETE:
            case R.id.MENU_SERIES_SET_INCOMPLETE: {
                final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                // toggle the complete status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                if (mModel.setSeriesComplete(seriesId, status)) {
                    onBookChange(0, BookChangedListener.SERIES, null);
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                final Series series = mModel.getSeries(rowData);
                if (series != null) {
                    StandardDialogs.deleteSeries(this, series, () -> {
                        mModel.delete(this, series);
                        onBookChange(0, BookChangedListener.SERIES, null);
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
                                  mModel.getSelectedBookshelf().getId());
                startActivityForResult(intent, RequestCode.AUTHOR_WORKS);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT: {
                final Author author = mModel.getAuthor(rowData);
                if (author != null) {
                    EditAuthorDialogFragment
                            .newInstance(RK_EDIT_AUTHOR, author)
                            .show(getSupportFragmentManager(), EditAuthorDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_AUTHOR_SET_COMPLETE:
            case R.id.MENU_AUTHOR_SET_INCOMPLETE: {
                final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                // toggle the complete status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                if (mModel.setAuthorComplete(authorId, status)) {
                    onBookChange(0, BookChangedListener.AUTHOR, null);
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT: {
                final Publisher publisher = mModel.getPublisher(rowData);
                if (publisher != null) {
                    EditPublisherDialogFragment
                            .newInstance(RK_EDIT_PUBLISHER, publisher)
                            .show(getSupportFragmentManager(), EditPublisherDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_PUBLISHER_DELETE: {
                final Publisher publisher = mModel.getPublisher(rowData);
                if (publisher != null) {
                    StandardDialogs.deletePublisher(this, publisher, () -> {
                        mModel.delete(this, publisher);
                        onBookChange(0, BookChangedListener.PUBLISHER, null);
                    });
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT: {
                EditFormatDialogFragment
                        .newInstance(RK_EDIT_FORMAT, rowData.getString(DBDefinitions.KEY_FORMAT))
                        .show(getSupportFragmentManager(), EditFormatDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_COLOR_EDIT: {
                EditColorDialogFragment
                        .newInstance(RK_EDIT_COLOR, rowData.getString(DBDefinitions.KEY_COLOR))
                        .show(getSupportFragmentManager(), EditColorDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                EditGenreDialogFragment
                        .newInstance(RK_EDIT_GENRE, rowData.getString(DBDefinitions.KEY_GENRE))
                        .show(getSupportFragmentManager(), EditGenreDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                EditLanguageDialogFragment
                        .newInstance(RK_EDIT_LANGUAGE, this,
                                     rowData.getString(DBDefinitions.KEY_LANGUAGE))
                        .show(getSupportFragmentManager(), EditLanguageDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                EditLocationDialogFragment
                        .newInstance(RK_EDIT_LOCATION,
                                     rowData.getString(DBDefinitions.KEY_LOCATION))
                        .show(getSupportFragmentManager(), EditLocationDialogFragment.TAG);
                return true;
            }

            /* ********************************************************************************** */
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                final Author author = mModel.getAuthor(rowData);
                if (author != null) {
                    final String url = AmazonSearchEngine.createUrl(
                            this, author.getLabel(this), null);
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                final Series series = mModel.getSeries(rowData);
                if (series != null) {
                    final String url = AmazonSearchEngine.createUrl(
                            this, null, series.getTitle());
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                final Author author = mModel.getAuthor(rowData);
                final Series series = mModel.getSeries(rowData);
                if (author != null && series != null) {
                    final String url = AmazonSearchEngine.createUrl(
                            this, author.getLabel(this), series.getTitle());
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }

            case R.id.MENU_LEVEL_EXPAND: {
                setNode(rowData, BooklistNode.NEXT_STATE_EXPANDED,
                        mModel.getCurrentStyle(this).getGroups().size());
                return true;
            }

            case R.id.MENU_NEXT_MISSING_COVER: {
                final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                final BooklistNode node = mModel.getNextBookWithoutCover(this, nodeRowId);
                if (node != null) {
                    final List<BooklistNode> target = new ArrayList<>();
                    target.add(node);
                    displayList(target);
                }
                return true;
            }

            default:
                return MenuHandler.handleOpenOnWebsiteMenus(this, menuItem, rowData);
        }
    }

    private void onStyleChanged(@NonNull final String uuid) {
        saveListPosition();
        mModel.onStyleChanged(this, uuid);
        // Set the rebuild state like this is the first time in,
        // which it sort of is, given we are changing style.
        mModel.setPreferredListRebuildState(this);
        // and do a rebuild
        buildBookList();
    }

    /**
     * React to row changes made. ENHANCE: update the modified row without a rebuild.
     *
     * @param bookId        the book that was changed, or 0 if the change was global
     * @param fieldsChanged a bitmask build from the flags
     * @param data          bundle with custom data, can be {@code null}
     */
    public void onBookChange(final long bookId,
                             final int fieldsChanged,
                             @Nullable final Bundle data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "onBookChanged"
                       + "|bookId=" + bookId
                       + "|fieldsChanged=0b" + Integer.toBinaryString(fieldsChanged)
                       + "|data=" + data);
        }

        saveListPosition();
        buildBookList();

        // changes were made to a single book
//        if (bookId > 0) {
//            if ((fieldsChanged & BookChangedListener.BOOK_READ) != 0) {
//                saveListPosition();
//                buildBookList();
//
//          } else if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
//                if (data != null) {
//                    String loanee = data.getString(DBDefinitions.KEY_LOANEE);
//                }
//                saveListPosition();
//                buildBookList();
//
//            } else if ((fieldsChanged & BookChangedListener.BOOK_DELETED) != 0) {
//                saveListPosition();
//                buildBookList();
//            }
//        } else {
//            // changes (Author, Series, ...) were made to (potentially) the whole list
//            if (fieldsChanged != 0) {
//                saveListPosition();
//                buildBookList();
//            }
//        }
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
                                                      Booklist.PREF_REBUILD_SAVED_STATE);
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
            case RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // the last edited/inserted shelf
                    final long bookshelfId = data.getLongExtra(DBDefinitions.KEY_PK_ID,
                                                               Bookshelf.DEFAULT);
                    if (bookshelfId != mModel.getSelectedBookshelf().getId()) {
                        mModel.setSelectedBookshelf(this, bookshelfId);
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }

            case RequestCode.NAV_PANEL_MANAGE_STYLES: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    // we get the UUID for the selected style back.
                    final String styleUuid = data.getStringExtra(BooklistStyle.BKEY_STYLE_UUID);
                    if (styleUuid != null) {
                        mModel.onStyleChanged(this, styleUuid);
                    }

                    if (data.getBooleanExtra(BooklistStyle.BKEY_STYLE_MODIFIED, false)) {
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }

            case RequestCode.EDIT_STYLE: {
                // We get here from the StylePickerDialogFragment (i.e. the style menu)
                // when the user choose to EDIT a style.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    // We get the ACTUAL style back.
                    // This style might be new (id==0) or already existing (id!=0).
                    @Nullable
                    final BooklistStyle style = data.getParcelableExtra(BooklistStyle.BKEY_STYLE);
                    if (style != null) {
                        mModel.onStyleChanged(this, style);
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
                                BooklistStyle.StyleDAO.clearCache();
                            }
                            if ((options & Options.PREFS) != 0) {
                                // Refresh the preferred bookshelf. This also refreshes its style.
                                mModel.reloadSelectedBookshelf(this);
                            }

                            // styles, prefs, books, covers,... it all requires a rebuild.
                            mModel.setForceRebuildInOnResume(true);
                        }
                    }
                }
                break;
            }

            // from BaseActivity Nav Panel
            case RequestCode.NAV_PANEL_ABOUT:
                if (resultCode == AboutActivity.RESULT_ALL_DATA_DESTROYED) {
                    BooklistStyle.StyleDAO.clearCache();
                    mModel.reloadSelectedBookshelf(this);
                    mModel.setForceRebuildInOnResume(true);
                }
                break;

            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        // If the FAB menu is showing, hide it and suppress the back key.
        if (mFabMenu.hideMenu()) {
            return;
        }

        // If the current list is has any search criteria enabled, clear them and rebuild the list.
        if (isTaskRoot() && !mModel.getSearchCriteria().isEmpty()) {
            mModel.getSearchCriteria().clear();
            buildBookList();
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
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|don't build the list needlessly");
            }
            return;
        }

        // If we have search criteria enabled (i.e. we're filtering the current list)
        // then we should display the 'up' indicator. See #onBackPressed.
        updateActionBar(mModel.getSearchCriteria().isEmpty());

        // Initialize/Update the list of bookshelves
        mModel.reloadBookshelfList(this);
        mBookshelfSpinnerAdapter.notifyDataSetChanged();
        // and select the current shelf.
        final int selectedPosition = mModel.getSelectedBookshelfSpinnerPosition(this);
        mVb.bookshelfSpinner.setSelection(selectedPosition);


        final boolean forceRebuildInOnResume = mModel.isForceRebuildInOnResume();
        // always reset for next iteration.
        mModel.setForceRebuildInOnResume(false);

        // This if/else is to be able to debug/log *why* we're rebuilding
        if (!mModel.isListLoaded()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|initial load");
            }
            buildBookList();

        } else if (forceRebuildInOnResume) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|isForceRebuildInOnResume");
            }
            buildBookList();

        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|reusing existing list");
            }
            // no rebuild needed/done, just let the system redisplay the list state
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
        mFabMenu.hideMenu();
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
     * Start the list builder.
     */
    private void buildBookList() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "buildBookList"
                       + "|already running=" + mModel.isRunning()
                       + "|called from:", new Throwable());
        }

        if (!mModel.isRunning()) {
            mVb.progressBar.setVisibility(View.VISIBLE);
            // Invisible... theoretically this means the page should not re-layout
            mVb.listHeader.getRoot().setVisibility(View.INVISIBLE);
            mVb.list.setVisibility(View.INVISIBLE);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                final SimpleDateFormat dateFormat =
                        new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.getDefault());
                Debug.startMethodTracing("trace-" + dateFormat.format(new Date()));
            }
            // force the adapter to stop displaying by disabling its cursor.
            // DO NOT REMOVE THE ADAPTER FROM FROM THE VIEW;
            // i.e. do NOT call mVb.list.setAdapter(null)... crashes assured when doing so.
            mAdapter.clearCursor();
            mModel.buildBookList();
        }
    }

    /**
     * Called when the list build succeeded.
     *
     * @param message from the task; contains the (optional) target rows.
     */
    private void onBuildFinished(@NonNull final FinishedMessage<List<BooklistNode>> message) {
        mVb.progressBar.setVisibility(View.GONE);
        if (message.isNewEvent()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.stopMethodTracing();
            }
            displayList(message.result);
        }
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    public void onBuildFailed(@NonNull final LiveDataEvent message) {
        mVb.progressBar.setVisibility(View.GONE);
        if (message.isNewEvent()) {
            if (mModel.isListLoaded()) {
                displayList(null);
            } else {
                // Something is REALLY BAD
                throw new IllegalStateException();
            }
        }
    }

    private void setNode(@NonNull final DataHolder rowData,
                         @BooklistNode.NextState final int nextState,
                         final int relativeChildLevel) {
        saveListPosition();
        final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
        mModel.setNode(nodeRowId, nextState, relativeChildLevel);
        displayList(null);
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
            saveListPosition();
            // set new states
            mModel.expandAllNodes(topLevel, expand);
            displayList(null);
        }
    }

    /**
     * Preserve the list position for the CURRENT bookshelf/style combination.
     * <p>
     * TODO: https://guides.codepath.com/android/Handling-Configuration-Changes#recyclerview
     * but we'd still need to do some manual stuff to keep the position in between
     * app restarts.
     */
    void saveListPosition() {
        if (!isDestroyed()) {
            final int position = mLayoutManager.findFirstVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            // The number of pixels offset for the first visible row.
            final int topViewOffset;
            final View topView = mVb.list.getChildAt(0);
            if (topView != null) {
                topViewOffset = topView.getTop();
            } else {
                topViewOffset = 0;
            }

            mModel.saveListPosition(this, position, topViewOffset);
        }
    }


    /**
     * Display the list based on the given cursor, and update the list headers.
     * Optionally re-position to the desired target node(s).
     *
     * @param targetNodes (optional) to re-position to
     */
    private void displayList(@Nullable final List<BooklistNode> targetNodes) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "displayList|called from:", new Throwable());
        }

        String header;
        header = mModel.getHeaderStyleName(this);
        mVb.listHeader.styleName.setText(header);
        mVb.listHeader.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = mModel.getHeaderFilterText(this);
        mVb.listHeader.filterText.setText(header);
        mVb.listHeader.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = mModel.getHeaderBookCount(this);
        mVb.listHeader.bookCount.setText(header);
        mVb.listHeader.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);

        // just show the root container... if no fields in it are shown that's still fine.
        mVb.listHeader.getRoot().setVisibility(View.VISIBLE);

        mVb.list.setVisibility(View.VISIBLE);

        createAdapter(mModel.getNewListCursor());
        scrollToSavedPosition(targetNodes);
    }

    /**
     * Scroll to the saved position for the current Bookshelf.
     * Optionally re-position to the desired target node(s).
     *
     * @param targetNodes (optional) to re-position to
     */
    private void scrollToSavedPosition(@Nullable final List<BooklistNode> targetNodes) {
        final Bookshelf bookshelf = mModel.getSelectedBookshelf();
        int position = bookshelf.getTopItemPosition();

        if (position >= mAdapter.getItemCount()) {
            // the list is shorter than it used to be, just scroll to the end
            mLayoutManager.scrollToPosition(position);

        } else if (position != RecyclerView.NO_POSITION) {
            // sanity check
            if (position < 0) {
                position = 0;
            }
            mLayoutManager.scrollToPositionWithOffset(position, bookshelf.getTopViewOffset());
        }

        // Note that an above scroll position change will not be reflected
        // until the next layout call hence we need to post any additional scroll
        if (targetNodes != null) {
            mVb.list.post(() -> scrollTo(targetNodes));
        } else {
            // we're at the final position
            mVb.list.post(this::saveListPosition);
        }
    }

    /**
     * Scroll to the 'best' position in the given list of targets.
     *
     * @param targetNodes list of rows of which we want one to be visible to the user.
     */
    private void scrollTo(@NonNull final List<BooklistNode> targetNodes) {
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
        BooklistNode best = targetNodes.get(0);
        // distance from currently visible middle row
        int distance = Math.abs(best.getListPosition() - middle);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetNodes.size(); i++) {
            final BooklistNode node = targetNodes.get(i);
            final int newDist = Math.abs(node.getListPosition() - middle);
            if (newDist < distance) {
                distance = newDist;
                best = node;
            }
        }

        scrollTo(best);
    }

    /**
     * Scroll the given node into user view.
     *
     * @param node to scroll to
     */
    private void scrollTo(@NonNull final BooklistNode node) {

        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // empty list
            return;
        }

        // If the node is not in view, or at the edge, scroll it into view.
        final int pos = node.getListPosition();
        if (pos <= firstVisibleItemPosition) {
            // We always scroll up 1 more than needed for comfort.
            mLayoutManager.scrollToPosition(pos - 1);
            mVb.list.post(this::saveListPosition);

        } else if (pos >= mLayoutManager.findLastVisibleItemPosition()) {
            // first scroll to the requested position
            mLayoutManager.scrollToPosition(pos);
            mVb.list.post(() -> {
                if (node.isExpanded()) {
                    int position = node.getListPosition();
                    // if we are at the bottom of the screen,
                    if (position == mLayoutManager.findLastCompletelyVisibleItemPosition()) {
                        // and it's not displaying a book,
                        if (mAdapter.getItemViewType(position) != BooklistGroup.BOOK) {
                            // scroll an additional line to make it clear this line was expanded.
                            position += 1;
                        }
                        // scroll 1 line extra, i.e. 1 or 2 lines extra to the requested position
                        mLayoutManager.scrollToPosition(position + 1);
                        mVb.list.post(this::saveListPosition);
                        return;
                    }
                }

                // we're at the final position
                saveListPosition();
            });
        }
    }


    private void onCancelled(@NonNull final LiveDataEvent message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Snackbar.make(mVb.list, R.string.warning_task_cancelled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Snackbar.make(mVb.list, GrStatus.getMessage(this, message.result),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                mGrAuthTask.prompt(this);
            } else {
                Snackbar.make(mVb.list, message.result.getMessage(this),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog(message.taskId);
        }
        mProgressDialog.onProgress(message);
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog(@IdRes final int taskId) {
        FragmentManager fm = getSupportFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);

        // not found? create it
        if (dialog == null) {
            switch (taskId) {
                case R.id.TASK_ID_GR_REQUEST_AUTH:
                    dialog = ProgressDialogFragment.newInstance(
                            getString(R.string.lbl_registration,
                                      getString(R.string.site_goodreads)),
                            false, true);
                    break;
                case R.id.TASK_ID_GR_SEND_ONE_BOOK:
                    dialog = ProgressDialogFragment.newInstance(
                            getString(R.string.gr_title_send_book), false, true);
                    break;

                default:
                    throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + "id=" + taskId);
            }
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        switch (taskId) {
            case R.id.TASK_ID_GR_REQUEST_AUTH:
                dialog.setCanceller(mGrAuthTask);
                break;
            case R.id.TASK_ID_GR_SEND_ONE_BOOK:
                dialog.setCanceller(mGrSendOneBookTask);
                break;

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + "taskId=" + taskId);
        }
        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private static class BookshelfSpinnerAdapter
            extends ArrayAdapter<Bookshelf> {

        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param list    of bookshelves
         */
        BookshelfSpinnerAdapter(@NonNull final Context context,
                                @NonNull final List<Bookshelf> list) {
            // 0: see getView() below.
            super(context, 0, list);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public long getItemId(final int position) {
            //noinspection ConstantConditions
            return getItem(position).getId();
        }

        @NonNull
        @Override
        public View getView(final int position,
                            @Nullable final View convertView,
                            @NonNull final ViewGroup parent) {
            return getPopulatedView(position, convertView, parent,
                                    R.layout.bookshelf_spinner_selected);
        }

        @Override
        @NonNull
        public View getDropDownView(final int position,
                                    @Nullable final View convertView,
                                    @NonNull final ViewGroup parent) {
            return getPopulatedView(position, convertView, parent,
                                    R.layout.dropdown_menu_popup_item);
        }

        @NonNull
        private View getPopulatedView(final int position,
                                      @Nullable final View convertView,
                                      @NonNull final ViewGroup parent,
                                      @LayoutRes final int layoutId) {
            final View view;
            if (convertView == null) {
                view = mInflater.inflate(layoutId, parent, false);
            } else {
                view = convertView;
            }

            //noinspection ConstantConditions
            ((TextView) view).setText(getItem(position).getLabel(getContext()));
            return view;
        }
    }
}
