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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AddBookBySearchContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookFromBundleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.backup.ExportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
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
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrSendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfViewModel;
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
 *     <li>{@link SearchFtsFragment} is started</li>
 *     <li>{@link SearchFtsFragment} returns an id-list and the fts search terms</li>
 *     <li>{@link #mFtsSearchLauncher} sets the incoming fts criteria</li>
 *     <li>{@link #onResume} builds the list</li>
 * </ol>
 *
 * <ol>Standard:
 *     <li>User clicks option menu search icon</li>
 *     <li>shows the search widget, user types</li>
 *     <li>{@link #onNewIntent} gets called with the query data</li>
 *     <li>build the list</li>
 * </ol>
 * <p>
 * We check if we have search criteria, if not we just build and are done.<br>
 *
 * <ol>When we do have search criteria:
 *     <li>during display of the list, the action bar home icon is set to 'up'</li>
 *     <li>Allows the user to re-open the nav drawer and refine the search.</li>
 *     <li>any 'up/back' action will trigger {@link #onBackPressed}</li>
 *     <li>{@link #onBackPressed} checks if there are search criteria, if so, clears and
 *     rebuild and suppresses the 'back' action</li>
 * </ol>
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";

    /** {@link FragmentResultListener} request key. */
    private static final String RK_MENU_PICKER = TAG + ":rk:" + MenuPickerDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_STYLE_PICKER = TAG + ":rk:" + StylePickerDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;


    /** Bring up the Goodreads synchronization options. */
    private final ActivityResultLauncher<Void> mGoodreadsLauncher = registerForActivityResult(
            new GoodreadsAdminFragment.ResultContract(), data -> setGoodreadsMenuVisibility());
    /** Make a backup. */
    private final ActivityResultLauncher<Void> mExportLauncher = registerForActivityResult(
            new ExportFragment.ResultContract(), success -> {});


    /** Goodreads authorization task. */
    private GrAuthTask mGrAuthTask;
    /** Goodreads send-book task. */
    private GrSendOneBookTask mGrSendOneBookTask;
    /** Multi-type adapter to manage list connection to cursor. */
    private BooklistAdapter mAdapter;
    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel mVm;


    /** Display a Book. */
    private final ActivityResultLauncher<ShowBookActivity.ResultContract.Input>
            mDisplayBookLauncher = registerForActivityResult(
            new ShowBookActivity.ResultContract(), this::onBookEditingDone);
    /** Edit a Book. */
    private final ActivityResultLauncher<Long> mEditByIdLauncher = registerForActivityResult(
            new EditBookByIdContract(), this::onBookEditingDone);
    /** Duplicate and edit a Book. */
    private final ActivityResultLauncher<Bundle> mDuplicateLauncher = registerForActivityResult(
            new EditBookFromBundleContract(), this::onBookEditingDone);


    /** Do an import. */
    private final ActivityResultLauncher<Void> mImportLauncher = registerForActivityResult(
            new ImportFragment.ResultContract(), options -> {
                if (options != Options.NOTHING) {
                    if ((options & Options.STYLES) != 0) {
                        // Force a refresh of the cached styles
                        StyleDAO.clearCache();
                    }
                    if ((options & Options.PREFS) != 0) {
                        // Refresh the preferred bookshelf. This also refreshes its style.
                        mVm.reloadSelectedBookshelf(this);
                    }

                    // styles, prefs, books, covers,... it all requires a rebuild.
                    mVm.setForceRebuildInOnResume(true);
                }
            });


    /** Add a Book by doing a search on the internet. */
    private final ActivityResultLauncher<AddBookBySearchContract.By> mAddBookBySearchLauncher =
            registerForActivityResult(new AddBookBySearchContract(), this::onBookEditingDone);


    /** The local FTS based search. */
    private final ActivityResultLauncher<SearchCriteria> mFtsSearchLauncher =
            registerForActivityResult(new SearchFtsFragment.ResultContract(), data -> {
                if (mVm.setSearchCriteria(data, true)) {
                    //URGENT: switch bookshelf? all-books?
                    mVm.setForceRebuildInOnResume(true);
                }
            });


    /** Update an individual Book with information from the internet. */
    private final ActivityResultLauncher<Book> mUpdateBookLauncher =
            registerForActivityResult(new UpdateBookContract(), this::onBookEditingDone);
    /** Update a list of Books with information from the internet. */
    private final ActivityResultLauncher<UpdateBooklistContract.Input> mUpdateBookListLauncher =
            registerForActivityResult(new UpdateBooklistContract(), this::onBookEditingDone);


    /** View all works of an Author. */
    private final ActivityResultLauncher<AuthorWorksFragment.ResultContract.Input>
            mAuthorWorksLauncher = registerForActivityResult(
            new AuthorWorksFragment.ResultContract(), this::onBookEditingDone);


    /** Manage the book shelves. */
    private final ActivityResultLauncher<Long> mManageBookshelvesLauncher =
            registerForActivityResult(new EditBookshelvesFragment.ResultContract(), id -> {
                if (id != 0 && id != mVm.getSelectedBookshelf().getId()) {
                    mVm.setSelectedBookshelf(this, id);
                    mVm.setForceRebuildInOnResume(true);
                }
            });


    /** Manage the list of (preferred) styles. */
    private final ActivityResultLauncher<String> mEditStylesLauncher = registerForActivityResult(
            new PreferredStylesFragment.ResultContract(), data -> {
                if (data != null) {
                    // we get the UUID for the selected style back.
                    final String uuid = data.getString(BooklistStyle.BKEY_STYLE_UUID);
                    if (uuid != null) {
                        mVm.onStyleChanged(this, uuid);
                    }

                    // This is independent from the above style having been modified ot not.
                    if (data.getBoolean(BooklistStyle.BKEY_STYLE_MODIFIED, false)) {
                        mVm.setForceRebuildInOnResume(true);
                    }
                }
            });
    /** Edit an individual style. */
    private final ActivityResultLauncher<StyleFragment.ResultContract.Input>
            mEditStyleLauncher = registerForActivityResult(
            new StyleFragment.ResultContract(), data -> {
                if (data != null) {
                    // We get here from the StylePickerDialogFragment (i.e. the style menu)
                    // when the user choose to EDIT a style.
                    // We get the ACTUAL style back.
                    @Nullable
                    final BooklistStyle style = data.getParcelable(BooklistStyle.BKEY_STYLE);
                    if (style != null) {
                        mVm.onStyleEdited(this, style);

                        // ALWAYS rebuild here, even when the style was not modified
                        // as we're handling this as a style-change
                        // (we could do checks... but it's not worth the effort.)
                        // i.e. same as in mOnStylePickerListener
                        mVm.setForceRebuildInOnResume(true);
                    }
                }
            });

    /**
     * Full progress dialog to show while running a task.
     * Note that the {@link #mVm} does not use this dialog (i.e. never sends progress messages)
     * but just the light weight ProgressBar.
     */
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** Encapsulates the FAB button/menu. */
    private FabMenu mFabMenu;
    /** The adapter used to fill the mBookshelfSpinner. */
    private BookshelfSpinnerAdapter mBookshelfSpinnerAdapter;
    /** View Binding. */
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
                        if (view == null) {
                            return;
                        }

                        // check if the selection is actually different from the previous one
                        final boolean isChanged = id != mVm.getSelectedBookshelf().getId();
                        if (isChanged) {
                            saveListPosition();
                            // make the new shelf the current and rebuild
                            mVm.setSelectedBookshelf(parent.getContext(), id);
                            buildBookList();
                        }
                    }
                }
            };

    /** React to row changes made. */
    private final RowChangeListener mRowChangeListener = this::onRowChange;

    /**
     * React to the user selecting a style to apply.
     * <p>
     * We get here after the user SELECTED a style on the {@link StylePickerDialogFragment}.
     * We do NOT come here when the user decided to EDIT a style,
     * which is handled {@link #mEditStyleLauncher}.
     */
    private final StylePickerDialogFragment.Launcher mOnStylePickerLauncher =
            new StylePickerDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final String uuid) {
                    saveListPosition();
                    mVm.onStyleChanged(BooksOnBookshelf.this, uuid);
                    mVm.resetPreferredListRebuildState(BooksOnBookshelf.this);
                    // and do a rebuild
                    buildBookList();
                }
            };

    /** Accept the result from the dialog. */
    private final EditBookshelfDialogFragment.Launcher mEditBookshelfLauncher =
            new EditBookshelfDialogFragment.Launcher() {
                @Override
                public void onResult(final long bookshelfId) {
                    if (bookshelfId != mVm.getSelectedBookshelf().getId()) {
                        onRowChange(RowChangeListener.BOOKSHELF, bookshelfId);
                    }
                }
            };

    /** Accept the result from the dialog. */
    private final EditLenderDialogFragment.Launcher mEditLenderLauncher =
            new EditLenderDialogFragment.Launcher() {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    onBookChange(RowChangeListener.BOOK_LOANEE, bookId);
                }
            };
    /** React to the user selecting a context menu option. (MENU_PICKER_USES_FRAGMENT). */
    private final MenuPickerDialogFragment.Launcher mMenuLauncher =
            new MenuPickerDialogFragment.Launcher() {
                @Override
                public boolean onResult(@IdRes final int itemId,
                                        final int position) {
                    return onContextItemSelected(itemId, position);
                }
            };
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
                        mDisplayBookLauncher.launch(new ShowBookActivity.ResultContract.Input(
                                rowData.getLong(DBDefinitions.KEY_FK_BOOK),
                                mVm.getBookNavigationTableName(),
                                rowData.getLong(DBDefinitions.KEY_PK_ID),
                                mVm.getCurrentStyle(BooksOnBookshelf.this).getUuid()
                        ));

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

                    final Menu menu = MenuPicker.createMenu(BooksOnBookshelf.this);
                    if (onCreateContextMenu(menu, rowData)) {
                        // we have a menu to show, set the title according to the level.
                        final int level = rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
                        final String title = mAdapter.getLevelText(position, level);

                        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
                            mMenuLauncher.launch(title, menu, position);
                        } else {
                            new MenuPicker(BooksOnBookshelf.this, title, menu, position,
                                           BooksOnBookshelf.this::onContextItemSelected)
                                    .show();
                        }
                    }
                    return true;
                }
            };

    @Override
    protected void onSetContentView() {
        mVb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());

        mFabMenu = new FabMenu(mVb.fab, mVb.fabOverlay,
                               mVb.fab0ScanBarcode,
                               mVb.fab1SearchIsbn,
                               mVb.fab2SearchText,
                               mVb.fab3AddManually,
                               mVb.fab4SearchExternalId);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // remove the default title to make space for the bookshelf spinner.
        setTitle("");

        getSupportFragmentManager()
                .setFragmentResultListener(RowChangeListener.REQUEST_KEY, this, mRowChangeListener);

        mEditBookshelfLauncher.register(this, RK_EDIT_BOOKSHELF);
        mEditLenderLauncher.register(this, RK_EDIT_LENDER);
        mOnStylePickerLauncher.register(this, RK_STYLE_PICKER);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.register(this, RK_MENU_PICKER);
        }

        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        mVm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        mVm.init(this, getIntent().getExtras());
        mVm.onCancelled().observe(this, this::onBuildFailed);
        mVm.onFailure().observe(this, this::onBuildFailed);
        mVm.onFinished().observe(this, this::onBuildFinished);

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

        // show/hide the Goodreads menu
        setNavigationItemVisibility(R.id.nav_goodreads, GoodreadsManager.isShowSyncMenus(prefs));

        // The booklist.
        //noinspection ConstantConditions
        mLayoutManager = (LinearLayoutManager) mVb.list.getLayoutManager();
        mVb.list.addItemDecoration(new TopLevelItemDecoration(this));
        FastScroller.attach(mVb.list);

        // TEST: Number of views to cache offscreen: default is 2
        mVb.list.setItemViewCacheSize(20);
        mVb.list.setDrawingCacheEnabled(true);
        mVb.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);


        // initialise adapter without a cursor.
        // Not creating the adapter here creates issues with Android internals.
        createAdapter(null);


        // Setup the Bookshelf spinner;
        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        mBookshelfSpinnerAdapter = new BookshelfSpinnerAdapter(this, mVm.getBookshelfList());
        mVb.bookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);
        mVb.bookshelfSpinner.setOnTouchListener(mOnBookshelfSelectionChanged);
        mVb.bookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);


        mFabMenu.attach(mVb.list);
        mFabMenu.setOnClickListener(this::onFabMenuItemSelected);
        // mVb.fab4SearchExternalId
        mFabMenu.getItem(4).setEnabled(EditBookExternalIdFragment.showEditBookTabExternalId(prefs));

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
     * <p>
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
            mAdapter.setCursor(this, cursor, mVm.getCurrentStyle(this));
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
        mVm.setForceRebuildInOnResume(true);
    }

    /**
     * Handle the standard search intent / suggestions click.
     * <p>
     * See
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
        mVm.getSearchCriteria().setKeywords(query);
    }

    @Override
    protected boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();

        final int itemId = item.getItemId();

        if (itemId == R.id.nav_advanced_search) {
            mFtsSearchLauncher.launch(mVm.getSearchCriteria());
            return true;

        } else if (itemId == R.id.nav_manage_bookshelves) {
            // overridden, so we can pass the current bookshelf id.
            mManageBookshelvesLauncher.launch(mVm.getSelectedBookshelf().getId());
            return true;

        } else if (itemId == R.id.nav_manage_list_styles) {
            mEditStylesLauncher.launch(mVm.getCurrentStyle(this).getUuid());
            return true;

        } else if (itemId == R.id.nav_import) {
            mImportLauncher.launch(null);
            return true;

        } else if (itemId == R.id.nav_export) {
            mExportLauncher.launch(null);
            return true;

        } else if (itemId == R.id.nav_goodreads) {
            mGoodreadsLauncher.launch(null);
            return true;
        }

        return super.onNavigationItemSelected(item);
    }

    private void onFabMenuItemSelected(@NonNull final View view) {
        final int viewId = view.getId();

        if (viewId == R.id.fab0_scan_barcode) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.Scan);

        } else if (viewId == R.id.fab1_search_isbn) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.Isbn);

        } else if (viewId == R.id.fab2_search_text) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.Text);

        } else if (viewId == R.id.fab3_add_manually) {
            mEditByIdLauncher.launch(0L);

        } else if (viewId == R.id.fab4_search_external_id) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.ExternalId);

        } else {
            throw new IllegalArgumentException(String.valueOf(view));
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

        final boolean showECPreferred = mVm.getCurrentStyle(this).getTopLevel(this) > 1;
        menu.findItem(R.id.MENU_LEVEL_PREFERRED_COLLAPSE).setVisible(showECPreferred);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        mFabMenu.hideMenu();

        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_STYLE_PICKER) {
            mOnStylePickerLauncher.launch(mVm.getCurrentStyle(this), false);
            return true;

        } else if (itemId == R.id.MENU_LEVEL_PREFERRED_COLLAPSE) {
            expandAllNodes(mVm.getCurrentStyle(this).getTopLevel(this), false);
            return true;

        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            expandAllNodes(1, true);
            return true;

        } else if (itemId == R.id.MENU_LEVEL_COLLAPSE) {
            expandAllNodes(1, false);
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            // IMPORTANT: this is from an options menu selection.
            // We pass the book ID's for the currently displayed list.
            //TODO: add a fitting screen subtitle
            mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                    mVm.getCurrentBookIdList(), null));
            return true;
        }

        return super.onOptionsItemSelected(item);
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
                final boolean isAvailable = mVm.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
                    .setVisible(GoodreadsManager.isShowSyncMenus(prefs));

                MenuHelper.prepareViewBookOnWebsiteMenu(menu, rowData);
                MenuHelper.prepareOptionalMenus(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                getMenuInflater().inflate(R.menu.author, menu);
                getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                final boolean complete = rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                MenuHelper.prepareOptionalMenus(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBDefinitions.KEY_FK_SERIES) != 0) {
                    getMenuInflater().inflate(R.menu.series, menu);
                    getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                    final boolean complete =
                            rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    MenuHelper.prepareOptionalMenus(menu, rowData);
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER) != 0) {
                    getMenuInflater().inflate(R.menu.publisher, menu);
                }
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                if (!rowData.getString(DBDefinitions.KEY_FK_BOOKSHELF).isEmpty()) {
                    getMenuInflater().inflate(R.menu.bookshelf, menu);
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
            menu.add(Menu.NONE, R.id.MENU_DIVIDER, menuOrder++, "")
                .setEnabled(false);
            //noinspection UnusedAssignment
            menu.add(Menu.NONE, R.id.MENU_LEVEL_EXPAND, menuOrder++, R.string.lbl_level_expand)
                .setIcon(R.drawable.ic_unfold_more);
        }

        return menu.size() > 0;
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param itemId   that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean onContextItemSelected(@IdRes final int itemId,
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

        if (itemId == R.id.MENU_BOOK_EDIT) {
            mEditByIdLauncher.launch(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
            return true;

        } else if (itemId == R.id.MENU_BOOK_DELETE) {
            final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
            final String title = rowData.getString(DBDefinitions.KEY_TITLE);
            final List<Author> authors = mVm.getAuthorsByBookId(bookId);
            StandardDialogs.deleteBook(this, title, authors, () -> {
                if (mVm.deleteBook(this, bookId)) {
                    onBookChange(RowChangeListener.BOOK_DELETED, bookId);
                }
            });
            return true;

        } else if (itemId == R.id.MENU_BOOK_DUPLICATE) {
            final Book book = mVm.getBook(rowData);
            if (book != null) {
                mDuplicateLauncher.launch(book.duplicate());
            }
            return true;

        } else if (itemId == R.id.MENU_BOOK_READ
                   || itemId == R.id.MENU_BOOK_UNREAD) {
            // toggle the read status
            final boolean status = !rowData.getBoolean(DBDefinitions.KEY_READ);
            final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
            if (mVm.setBookRead(bookId, status)) {
                onBookChange(RowChangeListener.BOOK_READ, bookId);
            }
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
            final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
            mEditLenderLauncher.launch(bookId, rowData.getString(DBDefinitions.KEY_TITLE));
            return true;

        } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
            final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
            mVm.lendBook(bookId, null);
            onBookChange(RowChangeListener.BOOK_LOANEE, bookId);
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_SHARE) {
            final Book book = mVm.getBook(rowData);
            if (book != null) {
                startActivity(book.getShareIntent(this));
            }
            return true;

        } else if (itemId == R.id.MENU_BOOK_SEND_TO_GOODREADS) {
            Snackbar.make(mVb.list, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
            mGrSendOneBookTask.startTask(bookId);
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            // IMPORTANT: this is from a context click on a row.
            // We pass the book ID's which are suited for that row.
            switch (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP)) {

                case BooklistGroup.BOOK: {
                    mUpdateBookLauncher.launch(mVm.getBook(rowData));
                    break;
                }
                case BooklistGroup.AUTHOR: {
                    final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                    mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                            mVm.getBookIdsByAuthor(authorId),
                            rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED)));
                    break;
                }
                case BooklistGroup.SERIES: {
                    final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                    mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                            mVm.getBookIdsBySeries(seriesId),
                            rowData.getString(DBDefinitions.KEY_SERIES_TITLE)));
                    break;
                }
                case BooklistGroup.PUBLISHER: {
                    final long publisherId = rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER);
                    mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                            mVm.getBookIdsByPublisher(publisherId),
                            rowData.getString(DBDefinitions.KEY_PUBLISHER_NAME)));
                    break;
                }
                default: {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "onContextItemSelected"
                                   + "|MENU_UPDATE_FROM_INTERNET not supported"
                                   + "|Group=" + rowData
                                           .getInt(DBDefinitions.KEY_BL_NODE_GROUP));
                    }
                    return true;
                }
            }

            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_SERIES_EDIT) {
            final Series series = mVm.getSeries(rowData);
            if (series != null) {
                EditSeriesDialogFragment.launch(this, series);
            }
            return true;

        } else if (itemId == R.id.MENU_SERIES_SET_COMPLETE
                   || itemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
            final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
            if (mVm.setSeriesComplete(seriesId, status)) {
                onRowChange(RowChangeListener.SERIES, seriesId);
            }
            return true;

        } else if (itemId == R.id.MENU_SERIES_DELETE) {
            final Series series = mVm.getSeries(rowData);
            if (series != null) {
                StandardDialogs.deleteSeries(this, series, () -> {
                    mVm.delete(this, series);
                    onRowChange(RowChangeListener.SERIES, series.getId());
                });
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_AUTHOR_WORKS) {
            mAuthorWorksLauncher.launch(new AuthorWorksFragment.ResultContract.Input(
                    rowData.getLong(DBDefinitions.KEY_FK_AUTHOR),
                    mVm.getSelectedBookshelf().getId()));
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_EDIT) {
            final Author author = mVm.getAuthor(rowData);
            if (author != null) {
                EditAuthorDialogFragment.launch(this, author);
            }
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_SET_COMPLETE
                   || itemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
            final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
            if (mVm.setAuthorComplete(authorId, status)) {
                onRowChange(RowChangeListener.AUTHOR, authorId);
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_PUBLISHER_EDIT) {
            final Publisher publisher = mVm.getPublisher(rowData);
            if (publisher != null) {
                EditPublisherDialogFragment.launch(this, publisher);
            }
            return true;

        } else if (itemId == R.id.MENU_PUBLISHER_DELETE) {
            final Publisher publisher = mVm.getPublisher(rowData);
            if (publisher != null) {
                StandardDialogs.deletePublisher(this, publisher, () -> {
                    mVm.delete(this, publisher);
                    onRowChange(RowChangeListener.PUBLISHER, publisher.getId());
                });
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_BOOKSHELF_EDIT) {
            final Bookshelf bookshelf = mVm.getBookshelf(rowData);
            if (bookshelf != null) {
                mEditBookshelfLauncher.launch(bookshelf);
            }
            return true;

        } else if (itemId == R.id.MENU_BOOKSHELF_DELETE) {
            final Bookshelf bookshelf = mVm.getBookshelf(rowData);
            if (bookshelf != null) {
                StandardDialogs.deleteBookshelf(this, bookshelf, () -> {
                    mVm.delete(bookshelf);
                    onRowChange(RowChangeListener.BOOKSHELF, bookshelf.getId());
                });
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_FORMAT_EDIT) {
            EditFormatDialogFragment.launch(this, rowData.getString(DBDefinitions.KEY_FORMAT));
            return true;

        } else if (itemId == R.id.MENU_COLOR_EDIT) {
            EditColorDialogFragment.launch(this, rowData.getString(DBDefinitions.KEY_COLOR));
            return true;

        } else if (itemId == R.id.MENU_GENRE_EDIT) {
            EditGenreDialogFragment.launch(this, rowData.getString(DBDefinitions.KEY_GENRE));
            return true;

        } else if (itemId == R.id.MENU_LANGUAGE_EDIT) {
            EditLanguageDialogFragment.launch(this, rowData.getString(DBDefinitions.KEY_LANGUAGE));
            return true;

        } else if (itemId == R.id.MENU_LOCATION_EDIT) {
            EditLocationDialogFragment.launch(this, rowData.getString(DBDefinitions.KEY_LOCATION));
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR) {
            final Author author = mVm.getAuthor(rowData);
            if (author != null) {
                AmazonSearchEngine.startSearchActivity(this, author, null);
            }
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_IN_SERIES) {
            final Series series = mVm.getSeries(rowData);
            if (series != null) {
                AmazonSearchEngine.startSearchActivity(this, null, series);
            }
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES) {
            final Author author = mVm.getAuthor(rowData);
            final Series series = mVm.getSeries(rowData);
            if (author != null && series != null) {
                AmazonSearchEngine.startSearchActivity(this, author, series);
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            setNode(rowData, BooklistNode.NEXT_STATE_EXPANDED,
                    mVm.getCurrentStyle(this).getGroups().size());
            return true;

        } else if (itemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
            final BooklistNode node = mVm.getNextBookWithoutCover(this, nodeRowId);
            if (node != null) {
                final List<BooklistNode> target = new ArrayList<>();
                target.add(node);
                displayList(target);
            }
            return true;
        }

        return MenuHelper.handleViewBookOnWebsiteMenu(this, itemId, rowData);
    }

    public void editStyle(@NonNull final BooklistStyle style,
                          final long templateId) {
        mEditStyleLauncher.launch(new StyleFragment.ResultContract.Input(style, templateId));
    }

    /**
     * React to row changes made.
     *
     * @param change type of item that was modified
     * @param id     of the <strong>item</strong> changed,
     *               or {@code 0} for a global change or for an books-table inline item
     *               (example of the latter: format/location/...)
     */
    public void onRowChange(@RowChangeListener.Change final int change,
                            @IntRange(from = 0) final long id) {
        // ENHANCE: update the modified row without a rebuild.
        saveListPosition();
        buildBookList();
    }

    /**
     * React to book changes made.
     *
     * @param change one of the BookChange flags
     * @param bookId the book that was changed
     */
    public void onBookChange(@RowChangeListener.BookChange final int change,
                             @IntRange(from = 1) final long bookId) {
        // ENHANCE: update the modified row without a rebuild.
        saveListPosition();
        buildBookList();
    }

    /**
     * Called when the user has finished (and saved) editing a Book.
     * <p>
     * This method is called from a ActivityResultContract after the result intent is parsed.
     * After this method is executed, the flow will take us to #onResume.
     *
     * @param data returned from the editor Activity
     */
    private void onBookEditingDone(@Nullable final Bundle data) {
        if (data != null) {
            if (data.getBoolean(Entity.BKEY_DATA_MODIFIED, false)) {
                mVm.setForceRebuildInOnResume(true);
            }

            // If we got an id back, make any (potential) rebuild re-position to it.
            final long bookId = data.getLong(DBDefinitions.KEY_PK_ID, 0);
            if (bookId > 0) {
                mVm.setDesiredCentralBookId(bookId);
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
        if (isTaskRoot() && !mVm.getSearchCriteria().isEmpty()) {
            mVm.getSearchCriteria().clear();
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
            return;
        }

        // If we have search criteria enabled (i.e. we're filtering the current list)
        // then we should display the 'up' indicator. See #onBackPressed.
        updateActionBar(mVm.getSearchCriteria().isEmpty());


        // Initialize/Update the list of bookshelves
        mVm.reloadBookshelfList(this);
        mBookshelfSpinnerAdapter.notifyDataSetChanged();
        // and select the current shelf.
        final int selectedPosition = mVm.getSelectedBookshelfSpinnerPosition(this);
        mVb.bookshelfSpinner.setSelection(selectedPosition);


        final boolean forceRebuildInOnResume = mVm.isForceRebuildInOnResume();
        // always reset for next iteration.
        mVm.setForceRebuildInOnResume(false);

        if (forceRebuildInOnResume || !mVm.isListLoaded()) {
            buildBookList();

        } else {
            // no rebuild needed/done, just let the system redisplay the list state
            displayList(mVm.getTargetNodes());
        }
    }

    @Override
    @CallSuper
    public void onPause() {
        mFabMenu.hideMenu();
        saveListPosition();
        super.onPause();
    }

    /**
     * Start the list builder.
     */
    private void buildBookList() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "buildBookList"
                       + "|already running=" + mVm.isRunning()
                       + "|called from:", new Throwable());
        }

        if (!mVm.isRunning()) {
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
            mVm.buildBookList();
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
            if (mVm.isListLoaded()) {
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
        mVm.setNode(nodeRowId, nextState, relativeChildLevel);
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
            mVm.expandAllNodes(topLevel, expand);
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

            mVm.saveListPosition(this, position, topViewOffset);
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
        header = mVm.getHeaderStyleName(this);
        mVb.listHeader.styleName.setText(header);
        mVb.listHeader.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = mVm.getHeaderFilterText(this);
        mVb.listHeader.filterText.setText(header);
        mVb.listHeader.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = mVm.getHeaderBookCount(this);
        mVb.listHeader.bookCount.setText(header);
        mVb.listHeader.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);

        // just show the root container... if no fields in it are shown that's still fine.
        mVb.listHeader.getRoot().setVisibility(View.VISIBLE);

        mVb.list.setVisibility(View.VISIBLE);

        createAdapter(mVm.getNewListCursor());
        scrollToSavedPosition(targetNodes);
    }

    /**
     * Scroll to the saved position for the current Bookshelf.
     * Optionally re-position to the desired target node(s).
     *
     * @param targetNodes (optional) to re-position to
     */
    private void scrollToSavedPosition(@Nullable final List<BooklistNode> targetNodes) {
        final Bookshelf bookshelf = mVm.getSelectedBookshelf();
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
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
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
        final FragmentManager fm = getSupportFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);

        // not found? create it
        if (dialog == null) {
            if (taskId == R.id.TASK_ID_GR_REQUEST_AUTH) {
                dialog = ProgressDialogFragment.newInstance(
                        getString(R.string.lbl_registration, getString(R.string.site_goodreads)),
                        false, true);

            } else if (taskId == R.id.TASK_ID_GR_SEND_ONE_BOOK) {
                dialog = ProgressDialogFragment.newInstance(
                        getString(R.string.gr_title_send_book), false, true);

            } else {
                throw new IllegalArgumentException("id=" + taskId);
            }
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        if (taskId == R.id.TASK_ID_GR_REQUEST_AUTH) {
            dialog.setCanceller(mGrAuthTask);

        } else if (taskId == R.id.TASK_ID_GR_SEND_ONE_BOOK) {
            dialog.setCanceller(mGrSendOneBookTask);

        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }


    /**
     * Allows to be notified of changes made.
     * The bit number are not stored and can be changed.
     * <p>
     * If a book id is passed back, it should be available
     * in {@code data.getLong(DBDefinitions.KEY_FK_BOOK)}.
     */
    public interface RowChangeListener
            extends FragmentResultListener {

        String REQUEST_KEY = "rk:RowChangeListener";

        // Note that BOOK is missing here.
        // It's implied if a bookId is passed back, or if the context makes it clear anyhow.
        int AUTHOR = 1;
        int SERIES = 1 << 1;
        int PUBLISHER = 1 << 2;
        int BOOKSHELF = 1 << 3;
        int TOC_ENTRY = 1 << 4;

        int FORMAT = 1 << 5;
        int COLOR = 1 << 6;
        int GENRE = 1 << 7;
        int LANGUAGE = 1 << 8;
        int LOCATION = 1 << 9;

        /** A book was set to read/unread. */
        int BOOK_READ = 1 << 10;

        /**
         * A book was either lend out, or returned.
         * <p>
         * When lend out:  data.putString(DBDefinitions.KEY_LOANEE, mLoanee);
         * When returned: data == null
         */
        int BOOK_LOANEE = 1 << 11;

        /** A book was deleted. */
        int BOOK_DELETED = 1 << 12;

        /* private. */ String ITEM_ID = "item";
        /* private. */ String CHANGE = "change";

        /**
         * Notify changes where made.
         *
         * @param requestKey for use with the FragmentResultListener
         * @param change     what changed
         * @param id         the item being modified,
         *                   or {@code 0} for a global change or for an books-table inline item
         */
        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @Change final int change,
                               final long id) {
            final Bundle result = new Bundle(2);
            result.putInt(CHANGE, change);
            result.putLong(ITEM_ID, id);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onChange(result.getInt(CHANGE),
                     result.getLong(ITEM_ID));
        }

        /**
         * Called if changes were made.
         *
         * @param change what changed
         * @param id     the item being modified, or {@code 0} for a global change
         */
        void onChange(@Change int change,
                      @IntRange(from = 0) long id);

        @IntDef({AUTHOR, SERIES, PUBLISHER, BOOKSHELF, TOC_ENTRY,
                 FORMAT, COLOR, GENRE, LANGUAGE, LOCATION})
        @Retention(RetentionPolicy.SOURCE)
        @interface Change {

        }

        @IntDef({BOOK_READ, BOOK_LOANEE, BOOK_DELETED})
        @Retention(RetentionPolicy.SOURCE)
        @interface BookChange {

        }
    }

    private static class BookshelfSpinnerAdapter
            extends ArrayAdapter<Bookshelf> {

        /** Cached inflater. */
        @NonNull
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
