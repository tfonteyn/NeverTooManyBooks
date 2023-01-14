/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AddBookBySearchContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CalibreSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchFtsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsFragment;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfHeaderBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
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
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.SpinnerInteractionListener;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * <p>
 * TODO: This class is littered with ActivityResultLauncher and *DialogFragment.Launcher
 * objects etc... Refactor to sharing the VM is becoming VERY urgent.
 *
 * <p>
 * Notes on the local-search:
 * <ol>Advanced:
 *     <li>User clicks navigation panel menu search option</li>
 *     <li>{@link SearchFtsFragment} is started</li>
 *     <li>{@link SearchFtsFragment} returns an id-list and the fts search terms</li>
 *     <li>{@link #ftsSearchLauncher} sets the incoming fts criteria</li>
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
        extends BaseActivity
        implements BookChangedListener {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";

    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_STYLE_PICKER = TAG + ":rk:" + StylePickerDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;

    private static final String RK_FILTERS = TAG + ":rk:" + BookshelfFiltersDialogFragment.TAG;

    /** Number of views to cache offscreen arbitrarily set to 20; the default is 2. */
    private static final int OFFSCREEN_CACHE_SIZE = 20;

    /** Make a backup. */
    private final ActivityResultLauncher<Void> exportLauncher =
            registerForActivityResult(new ExportContract(), success -> {
            });
    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> stripInfoSyncLauncher;
    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> calibreSyncLauncher;
    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler calibreHandler;
    /** Multi-type adapter to manage list connection to cursor. */
    @Nullable
    private BooklistAdapter adapter;
    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel vm;

    /** Do an import. */
    private final ActivityResultLauncher<Void> importLauncher =
            registerForActivityResult(new ImportContract(), o -> o.ifPresent(
                    data -> vm.onImportFinished(this, data)));

    /** Manage the list of (preferred) styles. */
    private final ActivityResultLauncher<String> editStylesLauncher =
            registerForActivityResult(new PreferredStylesContract(), o -> o.ifPresent(
                    data -> vm.onEditStylesFinished(this, data)));

    /** Edit an individual style. */
    private final ActivityResultLauncher<EditStyleContract.Input> editStyleLauncher =
            registerForActivityResult(new EditStyleContract(), o -> o.ifPresent(
                    data -> vm.onEditStyleFinished(this, data)));

    /**
     * Display a Book. We still call
     * {@link BooksOnBookshelfViewModel#onBookEditFinished(EditBookOutput)}
     * as the user might have done so from the displaying fragment.
     */
    private final ActivityResultLauncher<ShowBookPagerContract.Input> displayBookLauncher =
            registerForActivityResult(new ShowBookPagerContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** Add a Book by doing a search on the internet. */
    private final ActivityResultLauncher<AddBookBySearchContract.By> addBookBySearchLauncher =
            registerForActivityResult(new AddBookBySearchContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** Edit a Book. */
    private final ActivityResultLauncher<Long> editByIdLauncher =
            registerForActivityResult(new EditBookByIdContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** Duplicate and edit a Book. */
    private final ActivityResultLauncher<Book> duplicateLauncher =
            registerForActivityResult(new EditBookContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** Update an individual Book with information from the internet. */
    private final ActivityResultLauncher<Book> updateBookLauncher =
            registerForActivityResult(new UpdateSingleBookContract(), o -> o.ifPresent(
                    data -> vm.onBookAutoUpdateFinished(data)));

    /** Update a list of Books with information from the internet. */
    private final ActivityResultLauncher<UpdateBooklistContract.Input> updateBookListLauncher =
            registerForActivityResult(new UpdateBooklistContract(), o -> o.ifPresent(
                    data -> vm.onBookAutoUpdateFinished(data)));

    /** View all works of an Author. */
    private final ActivityResultLauncher<AuthorWorksContract.Input> authorWorksLauncher =
            registerForActivityResult(new AuthorWorksContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** The local FTS based search. */
    private final ActivityResultLauncher<SearchCriteria> ftsSearchLauncher =
            registerForActivityResult(new SearchFtsContract(), o -> o.ifPresent(
                    criteria -> vm.onFtsSearchFinished(criteria)));

    /** Manage the book shelves. */
    private final ActivityResultLauncher<Long> manageBookshelvesLauncher =
            registerForActivityResult(new EditBookshelvesContract(),
                                      bookshelfId -> vm.onManageBookshelvesFinished(this,
                                                                                    bookshelfId));

    /** Encapsulates the FAB button/menu. */
    private FabMenu fabMenu;

    /** View Binding. */
    private BooksonbookshelfBinding vb;
    private final BookshelfFiltersDialogFragment.Launcher bookshelfFiltersLauncher =
            new BookshelfFiltersDialogFragment.Launcher() {
                @Override
                public void onResult(final boolean modified) {
                    if (modified) {
                        buildBookList();
                    }
                }
            };
    /** List layout manager. */
    private LinearLayoutManager layoutManager;

    /**
     * Accept the result from the dialog.
     */
    private final EditBookshelfDialogFragment.Launcher editBookshelfLauncher =
            new EditBookshelfDialogFragment.Launcher() {
                @Override
                public void onResult(final long bookshelfId) {
                    if (bookshelfId != vm.getCurrentBookshelf().getId()) {
                        onRowChanged(DBKey.FK_BOOKSHELF, bookshelfId);
                    }
                }
            };

    /** Listener for the Bookshelf Spinner. */
    private final SpinnerInteractionListener bookshelfSelectionChangedListener =
            new SpinnerInteractionListener() {
                @Override
                public void onItemSelected(final long bookshelfId) {
                    if (bookshelfId != vm.getCurrentBookshelf().getId()) {
                        saveListPosition();
                        vm.setCurrentBookshelf(BooksOnBookshelf.this, bookshelfId);
                        buildBookList();
                    }
                }
            };

    /**
     * React to row changes made.
     * <p>
     * A number of dialogs use this common listener to report their changes back to us.
     * {@link EditAuthorDialogFragment},
     * {@link EditSeriesDialogFragment}
     * {@link EditPublisherDialogFragment}
     * and others.
     *
     * @see #onRowContextMenuItemSelected(MenuItem, int)
     */
    private final RowChangedListener rowChangedListener = new RowChangedListener() {
        @Override
        public void onChange(@NonNull final String key,
                             final long id) {
            onRowChanged(key, id);
        }
    };

    /**
     * React to the user selecting a style to apply.
     * <p>
     * We get here after the user SELECTED a style on the {@link StylePickerDialogFragment}.
     * We do NOT come here when the user decided to EDIT a style,
     * which is handled by {@link #editStyleLauncher}.
     */
    private final StylePickerDialogFragment.Launcher stylePickerLauncher =
            new StylePickerDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final String uuid) {
                    saveListPosition();
                    vm.onStyleChanged(BooksOnBookshelf.this, uuid);
                    vm.resetPreferredListRebuildMode(BooksOnBookshelf.this);
                    buildBookList();
                }
            };

    /**
     * Accept the result from the dialog.
     */
    private final EditLenderDialogFragment.Launcher editLenderLauncher =
            new EditLenderDialogFragment.Launcher() {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    onBookUpdated(vm.getBook(bookId), DBKey.LOANEE_NAME);
                }
            };

    /**
     * The adapter used to fill the Bookshelf selector.
     */
    private ExtArrayAdapter<Bookshelf> bookshelfAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        createFragmentResultListeners();
        createViewModel();
        createSyncDelegates();
        createHandlers();

        initNavDrawer();
        initToolbar();

        createBookshelfSpinner();

        // setup the list related stuff; the actual list data is generated in onResume
        createBooklistView();

        createFabMenu();
        updateSyncMenuVisibility();

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        if (savedInstanceState == null) {
            TipManager.getInstance().display(this, R.string.tip_book_list, null);

            if (vm.isProposeBackup()) {
                new MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.warning_backup_request)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                exportLauncher.launch(null))
                        .create()
                        .show();
            }
        }
    }

    private void createFragmentResultListeners() {
        final FragmentManager fm = getSupportFragmentManager();

        rowChangedListener.registerForFragmentResult(fm, this);

        editBookshelfLauncher.registerForFragmentResult(fm, RK_EDIT_BOOKSHELF, this);
        stylePickerLauncher.registerForFragmentResult(fm, RK_STYLE_PICKER, this);
        editLenderLauncher.registerForFragmentResult(fm, RK_EDIT_LENDER, this);
        bookshelfFiltersLauncher.registerForFragmentResult(fm, RK_FILTERS, this);
    }

    private void createViewModel() {
        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        vm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        vm.init(this, getIntent().getExtras());

        vm.onCancelled().observe(this, this::onBuildCancelled);
        vm.onFailure().observe(this, this::onBuildFailed);
        vm.onFinished().observe(this, this::onBuildFinished);
    }

    /**
     * Create the optional launcher and delegates.
     */
    private void createSyncDelegates() {

        // Reminder: this method cannot be called from onResume... registerForActivityResult
        // can only be called from onCreate

        if (SyncServer.CalibreCS.isEnabled()) {
            if (calibreSyncLauncher == null) {
                calibreSyncLauncher = registerForActivityResult(
                        new CalibreSyncContract(), result -> {
                            // If we imported anything at all... rebuild
                            if (result.contains(SyncContractBase.Outcome.Read)) {
                                vm.setForceRebuildInOnResume(true);
                            }
                        });
            }
        }

        if (SyncServer.StripInfo.isEnabled()) {
            if (stripInfoSyncLauncher == null) {
                stripInfoSyncLauncher = registerForActivityResult(
                        new StripInfoSyncContract(), result -> {
                            // If we imported anything at all... rebuild
                            if (result.contains(SyncContractBase.Outcome.Read)) {
                                vm.setForceRebuildInOnResume(true);
                            }
                        });
            }
        }
    }

    /**
     * Create the (optional) handlers.
     */
    private void createHandlers() {
        if (calibreHandler == null && SyncServer.CalibreCS.isEnabled()) {
            try {
                calibreHandler = new CalibreHandler(this, this)
                        .setProgressFrame(findViewById(R.id.progress_frame));
                calibreHandler.onViewCreated(this, vb.getRoot());
            } catch (@NonNull final CertificateException ignore) {
                // TipManager.getInstance().display(this, R.string.tip_calibre, null);
                // ignore
            }
        }
    }

    private void initToolbar() {
        setNavIcon();

        vb.toolbar.setNavigationOnClickListener(v -> {
            if (isRootActivity()) {
                vb.drawerLayout.openDrawer(GravityCompat.START);
            } else {
                onBackPressed();
            }
        });

        vb.toolbar.addMenuProvider(new ToolbarMenuProvider(), this);
    }

    private void createBookshelfSpinner() {
        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        bookshelfAdapter = new EntityArrayAdapter<>(this, vm.getBookshelfList());

        vb.bookshelfSpinner.setAdapter(bookshelfAdapter);
        bookshelfSelectionChangedListener.attach(vb.bookshelfSpinner);
    }

    private void createBooklistView() {
        //noinspection ConstantConditions
        layoutManager = (LinearLayoutManager) vb.content.list.getLayoutManager();

        // hide the view at creation time. onResume will provide the data and make it visible.
        vb.content.list.setVisibility(View.GONE);

        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(this);
        FastScroller.attach(vb.content.list, overlayType);


        vb.content.list.setItemViewCacheSize(OFFSCREEN_CACHE_SIZE);
        vb.content.list.setDrawingCacheEnabled(true);
        vb.content.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
    }

    private void createFabMenu() {
        fabMenu = new FabMenu(vb.fab, vb.fabOverlay,
                              vb.fab0ScanBarcode,
                              vb.fab0ScanBarcodeBatch,
                              vb.fab1SearchIsbn,
                              vb.fab2SearchText,
                              vb.fab3AddManually,
                              vb.fab4SearchExternalId);

        fabMenu.attach(vb.content.list);
        fabMenu.setOnClickListener(view -> onFabMenuItemSelected(view.getId()));
        fabMenu.getItem(vb.fab4SearchExternalId.getId())
               .ifPresent(item -> item.setEnabled(EditBookExternalIdFragment.isShowTab()));
    }

    /**
     * Show or hide the synchronization menu.
     */
    private void updateSyncMenuVisibility() {
        final boolean enable =
                SyncServer.CalibreCS.isEnabled() && calibreSyncLauncher != null
                ||
                SyncServer.StripInfo.isEnabled() && stripInfoSyncLauncher != null;

        //noinspection ConstantConditions
        getNavigationMenuItem(R.id.SUBMENU_SYNC).setVisible(enable);
    }


    private void setNavIcon() {
        if (isRootActivity()) {
            vb.toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24);
        } else {
            vb.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        }
    }

    private boolean isRootActivity() {
        return isTaskRoot() && vm.getSearchCriteria().isEmpty();
    }

    /**
     * Entry point for the system search request.
     *
     * @param intent The new intent that was started for the activity.
     */
    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        // make this the Activity intent.
        setIntent(intent);

        handleStandardSearchIntent(intent);
        vm.setForceRebuildInOnResume(true);
    }

    /**
     * Handle the standard search intent / suggestions click.
     * <p>
     * See
     * <a href="https://developer.android.com/guide/topics/search/search-dialog#ReceivingTheQuery">
     * ReceivingTheQuery</a>
     *
     * @param intent potentially containing the action
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
        vm.getSearchCriteria().setFtsKeywords(query);
    }

    @Override
    boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.SUBMENU_SYNC) {
            showNavigationSubMenu(R.id.SUBMENU_SYNC, menuItem, R.menu.sync);
            return false;
        }

        closeNavigationDrawer();

        if (itemId == R.id.MENU_ADVANCED_SEARCH) {
            ftsSearchLauncher.launch(vm.getSearchCriteria());
            return true;

        } else if (itemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // overridden, so we can pass the current bookshelf id.
            manageBookshelvesLauncher.launch(vm.getCurrentBookshelf().getId());
            return true;

        } else if (itemId == R.id.MENU_MANAGE_LIST_STYLES) {
            editStylesLauncher.launch(vm.getStyle(this).getUuid());
            return true;

        } else if (itemId == R.id.MENU_FILE_IMPORT) {
            importLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_FILE_EXPORT) {
            exportLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_SYNC_CALIBRE && calibreSyncLauncher != null) {
            calibreSyncLauncher.launch(null);
            return false;

        } else if (itemId == R.id.MENU_SYNC_STRIP_INFO && stripInfoSyncLauncher != null) {
            stripInfoSyncLauncher.launch(null);
            return false;
        }

        return super.onNavigationItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        // If the FAB menu is showing, hide it and suppress the back key.
        if (fabMenu.hideMenu()) {
            return;
        }

        // If the current list has any search criteria enabled, clear them and rebuild the list.
        if (isTaskRoot() && !vm.getSearchCriteria().isEmpty()) {
            vm.getSearchCriteria().clear();
            setNavIcon();
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
        setNavIcon();

        updateSyncMenuVisibility();
        fabMenu.getItem(vb.fab4SearchExternalId.getId())
               .ifPresent(item -> item.setEnabled(EditBookExternalIdFragment.isShowTab()));

        // Initialize/Update the list of bookshelves
        vm.reloadBookshelfList(this);
        bookshelfAdapter.notifyDataSetChanged();
        // and select the current shelf.
        final int selectedPosition = vm.getSelectedBookshelfSpinnerPosition(this);
        vb.bookshelfSpinner.setSelection(selectedPosition);


        final boolean forceRebuildInOnResume = vm.isForceRebuildInOnResume();
        // always reset for next iteration.
        vm.setForceRebuildInOnResume(false);

        if (forceRebuildInOnResume || !vm.isListLoaded()) {
            buildBookList();

        } else {
            // no rebuild needed/done, just let the system redisplay the list state
            displayList(vm.getTargetNodes());
        }
    }

    @Override
    @CallSuper
    public void onPause() {
        fabMenu.hideMenu();
        saveListPosition();
        super.onPause();
    }

    @Override
    public void onSyncBook(final long bookId) {
        scrollTo(vm.getVisibleBookNodes(bookId));
    }

    /**
     * User clicked a row.
     * <ul>
     *      <li>Book: open the details page.</li>
     *      <li>Not a book: expand/collapse the section as appropriate.</li>
     * </ul>
     *
     * @param position The position of the item within the adapter's data set.
     */
    private void onRowClicked(final int position) {
        //noinspection ConstantConditions
        final DataHolder rowData = adapter.readDataAt(position);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return;
        }

        if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // It's a book, open the details page.
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // store the id as the current 'central' book for repositioning
            vm.setCurrentCenteredBookId(bookId);

            if (hasEmbeddedDetailsFrame()) {
                //  On larger screens, opens the book details fragment embedded.
                openEmbeddedBookDetails(bookId);
            } else {
                //  On small screens, opens a ViewPager with the book details
                //  and swipe prev/next functionality.
                displayBookLauncher.launch(new ShowBookPagerContract.Input(
                        bookId,
                        vm.getStyle(BooksOnBookshelf.this).getUuid(),
                        vm.getBookNavigationTableName(),
                        rowData.getLong(DBKey.PK_ID)));
            }
        } else {
            // it's a level, expand/collapse.
            saveListPosition();

            final long nodeRowId = rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
            vm.setNode(nodeRowId, BooklistNode.NextState.Toggle, 1);
            // don't pass the node, we want the list to scroll back to
            // the exact same (saved) position.
            displayList(null);
        }
    }

    /**
     * Create a context menu based on row group.
     *
     * @param v        View clicked
     * @param position The position of the item within the adapter's data set.
     *
     * @return {@code true} if there is a menu to show
     */
    private boolean onCreateContextMenu(@NonNull final View v,
                                        final int position) {
        //noinspection ConstantConditions
        final DataHolder rowData = adapter.readDataAt(position);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return false;
        }

        final ExtPopupMenu contextMenu = new ExtPopupMenu(this)
                .setGroupDividerEnabled();
        final Menu menu = contextMenu.getMenu();

        final int rowGroupId = rowData.getInt(DBKey.BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.book, menu);

                if (!hasEmbeddedDetailsFrame()) {
                    // explicitly hide; but otherwise leave it to the details-frame menu handler.
                    menu.findItem(R.id.MENU_SYNC_LIST_WITH_DETAILS).setVisible(false);
                }

                if (calibreHandler != null) {
                    calibreHandler.onCreateMenu(menu, inflater);
                }
                vm.getViewBookHandler().onCreateMenu(this, menu, inflater);
                vm.getAmazonHandler().onCreateMenu(this, menu, inflater);

                final boolean isRead = rowData.getBoolean(DBKey.READ__BOOL);
                menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

                // specifically check LOANEE_NAME independent from the style in use.
                final boolean useLending = GlobalFieldVisibility.isUsed(DBKey.LOANEE_NAME);
                final boolean isAvailable = vm.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                if (calibreHandler != null) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    calibreHandler.onPrepareMenu(this, menu, book);
                }

                vm.getViewBookHandler().onPrepareMenu(menu, rowData);
                vm.getAmazonHandler().onPrepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.author, menu);
                vm.getAmazonHandler().onCreateMenu(this, menu, inflater);

                final boolean complete = rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                vm.getAmazonHandler().onPrepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBKey.FK_SERIES) != 0) {
                    final MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.series, menu);
                    vm.getAmazonHandler().onCreateMenu(this, menu, inflater);

                    final boolean complete = rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    vm.getAmazonHandler().onPrepareMenu(menu, rowData);

                } else {
                    // It's a "(No Series)" node
                    menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                             getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                             R.string.menu_update_books)
                        .setIcon(R.drawable.ic_baseline_cloud_download_24);
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (rowData.getLong(DBKey.FK_PUBLISHER) != 0) {
                    getMenuInflater().inflate(R.menu.publisher, menu);
                } else {
                    // It's a "(No Publisher)" node
                    menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                             getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                             R.string.menu_update_books)
                        .setIcon(R.drawable.ic_baseline_cloud_download_24);
                }
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                if (!rowData.getString(DBKey.FK_BOOKSHELF).isEmpty()) {
                    getMenuInflater().inflate(R.menu.bookshelf, menu);
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (!rowData.getString(DBKey.LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (!rowData.getString(DBKey.LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (!rowData.getString(DBKey.GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (!rowData.getString(DBKey.FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (!rowData.getString(DBKey.COLOR).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_COLOR_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            // year/month/day all resolve to the same date string yyyy-mm-dd
            case BooklistGroup.DATE_ACQUIRED_YEAR:
            case BooklistGroup.DATE_ACQUIRED_MONTH:
            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ADDED_YEAR:
            case BooklistGroup.DATE_ADDED_MONTH:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_PUBLISHED_YEAR:
            case BooklistGroup.DATE_PUBLISHED_MONTH:
                menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                         getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                         R.string.menu_update_books)
                    .setIcon(R.drawable.ic_baseline_cloud_download_24);
                break;


            default: {
                break;
            }
        }

        int menuOrder = getResources().getInteger(R.integer.MENU_ORDER_NEXT_MISSING_COVER);

        // forms its own group
        menu.add(R.id.MENU_NEXT_MISSING_COVER, R.id.MENU_NEXT_MISSING_COVER, menuOrder++,
                 R.string.option_goto_next_book_without_cover)
            .setIcon(R.drawable.ic_baseline_broken_image_24);

        // if it's a level, add the expand option
        if (rowData.getInt(DBKey.BL_NODE_GROUP) != BooklistGroup.BOOK) {
            //noinspection UnusedAssignment
            menu.add(R.id.MENU_GROUP_BOB_EXPANSION, R.id.MENU_LEVEL_EXPAND, menuOrder++,
                     R.string.option_level_expand)
                .setIcon(R.drawable.ic_baseline_unfold_more_24);
        }

        // If we actually have a menu, show it.
        if (menu.size() > 0) {
            // we have a menu to show, set the title according to the level.
            final int level = rowData.getInt(DBKey.BL_NODE_LEVEL);
            contextMenu.setTitle(adapter.getLevelText(level, position));

            if (menu.size() < 5) {
                // small menu, show it anchored to the row
                contextMenu.showAsDropDown(v, menuItem ->
                        onRowContextMenuItemSelected(menuItem, position));

            } else if (hasEmbeddedDetailsFrame()) {
                contextMenu.show(v, Gravity.START, menuItem ->
                        onRowContextMenuItemSelected(menuItem, position));
            } else {
                contextMenu.show(v, Gravity.CENTER, menuItem ->
                        onRowContextMenuItemSelected(menuItem, position));
            }
            return true;
        }

        return false;
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     * <p>
     * Dev. note: this used to be simply "onMenuItemSelected",
     * but due to an R8 bug confusing it with "onMenuItemSelected(int, android.view.MenuItem)"
     * ended throwing a "java.lang.LinkageError" ... so the name had to be changed.
     *
     * @param menuItem that was selected
     * @param position The position of the item within the adapter's data set.
     *
     * @return {@code true} if handled.
     */
    private boolean onRowContextMenuItemSelected(@NonNull final MenuItem menuItem,
                                                 final int position) {
        //noinspection ConstantConditions
        final DataHolder rowData = adapter.readDataAt(position);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return false;
        }

        final int itemId = menuItem.getItemId();

        // Check for row-group independent options first.

        if (itemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
            vm.getNextBookWithoutCover(nodeRowId).ifPresent(node -> {
                final List<BooklistNode> list = new ArrayList<>();
                list.add(node);
                displayList(list);
            });
            return true;

        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            saveListPosition();

            final long nodeRowId = rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
            vm.setNode(nodeRowId, BooklistNode.NextState.Expand,
                       vm.getStyle(this).getGroupCount());
            // don't pass the node, we want the list to scroll back to
            // the exact same (saved) position.
            displayList(null);
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
            final Intent intent = FragmentHostActivity
                    .createIntent(this, CalibrePreferencesFragment.class);
            startActivity(intent);
            return true;
        }

        // Specific row-group options

        final int rowGroupId = rowData.getInt(DBKey.BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                if (itemId == R.id.MENU_BOOK_SET_READ
                    || itemId == R.id.MENU_BOOK_SET_UNREAD) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    // toggle the read status
                    final boolean status = !rowData.getBoolean(DBKey.READ__BOOL);
                    if (vm.setBookRead(bookId, status)) {
                        onBookUpdated(vm.getBook(bookId), DBKey.READ__BOOL);
                    }
                    return true;

                } else if (itemId == R.id.MENU_BOOK_EDIT) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    editByIdLauncher.launch(bookId);
                    return true;

                } else if (itemId == R.id.MENU_BOOK_DUPLICATE) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    duplicateLauncher.launch(book.duplicate());
                    return true;

                } else if (itemId == R.id.MENU_BOOK_DELETE) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    final String title = rowData.getString(DBKey.TITLE);
                    final List<Author> authors = vm.getAuthorsByBookId(bookId);
                    StandardDialogs.deleteBook(this, title, authors, () -> {
                        if (vm.deleteBook(bookId)) {
                            onBookDeleted(bookId);
                        }
                    });
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    updateBookLauncher.launch(book);
                    return true;

                } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    editLenderLauncher.launch(bookId, rowData.getString(DBKey.TITLE));
                    return true;

                } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    vm.lendBook(bookId, null);
                    onBookUpdated(vm.getBook(bookId), DBKey.LOANEE_NAME);
                    return true;

                } else if (itemId == R.id.MENU_SHARE) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    startActivity(book.getShareIntent(this, vm.getStyle(this)));
                    return true;
                }
                break;
            }
            case BooklistGroup.AUTHOR: {
                if (itemId == R.id.MENU_AUTHOR_WORKS_FILTER) {
                    authorWorksLauncher.launch(new AuthorWorksContract.Input(
                            rowData.getLong(DBKey.FK_AUTHOR),
                            vm.getCurrentBookshelf().getId(),
                            vm.getStyle(this).getUuid()));
                    return true;

                } else if (itemId == R.id.MENU_AUTHOR_SET_COMPLETE
                           || itemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
                    final long authorId = rowData.getLong(DBKey.FK_AUTHOR);
                    // toggle the complete status
                    final boolean status = !rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
                    if (vm.setAuthorComplete(authorId, status)) {
                        onRowChanged(DBKey.FK_AUTHOR, authorId);
                    }
                    return true;

                } else if (itemId == R.id.MENU_AUTHOR_EDIT) {
                    final Author author = DataHolderUtils.requireAuthor(rowData);
                    // results come back in mRowChangedListener
                    EditAuthorDialogFragment.launch(getSupportFragmentManager(), author);
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    updateBooksFromInternetData(position, rowData, DBKey.AUTHOR_FORMATTED);
                    return true;
                }
                break;
            }
            case BooklistGroup.SERIES: {
                if (itemId == R.id.MENU_SERIES_SET_COMPLETE
                    || itemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
                    final long seriesId = rowData.getLong(DBKey.FK_SERIES);
                    // toggle the complete status
                    final boolean status = !rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
                    if (vm.setSeriesComplete(seriesId, status)) {
                        onRowChanged(DBKey.FK_SERIES, seriesId);
                    }
                    return true;

                } else if (itemId == R.id.MENU_SERIES_EDIT) {
                    final Series series = DataHolderUtils.requireSeries(rowData);
                    // results come back in mRowChangedListener
                    EditSeriesDialogFragment.launch(getSupportFragmentManager(), series);
                    return true;

                } else if (itemId == R.id.MENU_SERIES_DELETE) {
                    final Series series = DataHolderUtils.requireSeries(rowData);
                    StandardDialogs.deleteSeries(this, series, () -> {
                        vm.delete(this, series);
                        onRowChanged(DBKey.FK_SERIES, series.getId());
                    });
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    updateBooksFromInternetData(position, rowData, DBKey.SERIES_TITLE);
                    return true;
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (itemId == R.id.MENU_PUBLISHER_EDIT) {
                    final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
                    // results come back in mRowChangedListener
                    EditPublisherDialogFragment.launch(getSupportFragmentManager(), publisher);
                    return true;

                } else if (itemId == R.id.MENU_PUBLISHER_DELETE) {
                    final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
                    StandardDialogs.deletePublisher(this, publisher, () -> {
                        vm.delete(this, publisher);
                        onRowChanged(DBKey.FK_PUBLISHER, publisher.getId());
                    });
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    updateBooksFromInternetData(position, rowData, DBKey.PUBLISHER_NAME);
                    return true;
                }
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                if (itemId == R.id.MENU_BOOKSHELF_EDIT) {
                    final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
                    editBookshelfLauncher.launch(bookshelf);
                    return true;

                } else if (itemId == R.id.MENU_BOOKSHELF_DELETE) {
                    final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
                    StandardDialogs.deleteBookshelf(this, bookshelf, () -> {
                        vm.delete(bookshelf);
                        onRowChanged(DBKey.FK_BOOKSHELF, bookshelf.getId());
                    });
                    return true;
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (itemId == R.id.MENU_LANGUAGE_EDIT) {
                    EditLanguageDialogFragment.launch(getSupportFragmentManager(),
                                                      this, rowData.getString(DBKey.LANGUAGE));
                    return true;
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (itemId == R.id.MENU_LOCATION_EDIT) {
                    EditLocationDialogFragment.launch(getSupportFragmentManager(),
                                                      rowData.getString(DBKey.LOCATION));
                    return true;
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (itemId == R.id.MENU_GENRE_EDIT) {
                    EditGenreDialogFragment.launch(getSupportFragmentManager(),
                                                   rowData.getString(DBKey.GENRE));
                    return true;
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (itemId == R.id.MENU_FORMAT_EDIT) {
                    EditFormatDialogFragment.launch(getSupportFragmentManager(),
                                                    rowData.getString(DBKey.FORMAT));
                    return true;
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (itemId == R.id.MENU_COLOR_EDIT) {
                    EditColorDialogFragment.launch(getSupportFragmentManager(),
                                                   rowData.getString(DBKey.COLOR));
                    return true;
                }
                break;
            }
            case BooklistGroup.DATE_ACQUIRED_YEAR:
            case BooklistGroup.DATE_ACQUIRED_MONTH:
            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ADDED_YEAR:
            case BooklistGroup.DATE_ADDED_MONTH:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_PUBLISHED_YEAR:
            case BooklistGroup.DATE_PUBLISHED_MONTH: {
                if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    updateBookListLauncher.launch(
                            vm.createDateRowUpdateBooklistContractInput(this, rowData));
                    return true;
                }
                break;
            }
            default:
                break;
        }

        // other handlers.
        if (calibreHandler != null
            && calibreHandler.onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }
        if (vm.getAmazonHandler().onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }
        if (vm.getViewBookHandler().onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }
        return false;
    }

    @SuppressLint("Range")
    private void showBookDetailsIfWeCan(@IntRange(from = 0) final long bookId) {
        if (bookId > 0 && hasEmbeddedDetailsFrame()) {
            vm.setCurrentCenteredBookId(bookId);
            openEmbeddedBookDetails(bookId);
        } else {
            // Make sure to disable the current stored book id positioning
            vm.setCurrentCenteredBookId(0);
        }
    }

    private void onFabMenuItemSelected(@IdRes final int itemId) {

        if (itemId == R.id.fab0_scan_barcode) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Scan);

        } else if (itemId == R.id.fab0_scan_barcode_batch) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.ScanBatch);

        } else if (itemId == R.id.fab1_search_isbn) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Isbn);

        } else if (itemId == R.id.fab2_search_text) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Text);

        } else if (itemId == R.id.fab3_add_manually) {
            editByIdLauncher.launch(0L);

        } else if (itemId == R.id.fab4_search_external_id) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.ExternalId);

        } else {
            throw new IllegalArgumentException(String.valueOf(itemId));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void showNavigationSubMenu(@IdRes final int anchorMenuItemId,
                                       @NonNull final MenuItem menuItem,
                                       @MenuRes final int menuRes) {

        final View anchor = getNavigationMenuItemView(anchorMenuItemId);

        final ExtPopupMenu popupMenu = new ExtPopupMenu(this)
                .inflate(menuRes);
        final Menu menu = popupMenu.getMenu();
        if (menuItem.getItemId() == R.id.SUBMENU_SYNC) {
            menu.findItem(R.id.MENU_SYNC_CALIBRE)
                .setVisible(SyncServer.CalibreCS.isEnabled() && calibreSyncLauncher != null);

            menu.findItem(R.id.MENU_SYNC_STRIP_INFO)
                .setVisible(SyncServer.StripInfo.isEnabled() && stripInfoSyncLauncher != null);
        }

        popupMenu.setTitle(menuItem.getTitle())
                 .showAsDropDown(anchor, this::onNavigationItemSelected);
    }

    /**
     * Allow the user to decide between books on "this bookshelf only" or on all bookshelves
     * and then update all the selected books.
     *
     * @param position The position of the item within the adapter's data set.
     * @param rowData  for the row which was selected
     * @param labelKey key into the rowData for the row-item text to show to the user.
     */
    private void updateBooksFromInternetData(final int position,
                                             @NonNull final DataHolder rowData,
                                             @NonNull final String labelKey) {
        final View anchor = layoutManager.findViewByPosition(position);
        final String dialogTitle = rowData.getString(labelKey);
        //noinspection ConstantConditions
        new ExtPopupMenu(this)
                .inflate(R.menu.update_books)
                .setTitle(dialogTitle)
                .setMessage(getString(R.string.menu_update_books))
                .showAsDropDown(anchor, menuItem -> {
                    final int itemId = menuItem.getItemId();
                    Boolean onlyThisShelf = null;

                    if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_THIS_SHELF_ONLY) {
                        onlyThisShelf = true;
                    } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_ALL_SHELVES) {
                        onlyThisShelf = false;
                    }
                    if (onlyThisShelf != null) {
                        updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                                this, rowData, onlyThisShelf));
                        return true;
                    }
                    return false;
                });
    }

    public void editStyle(@NonNull final Style style,
                          final boolean setAsPreferred) {
        editStyleLauncher.launch(EditStyleContract.edit(style, setAsPreferred));
    }

    /**
     * React to (non-Book) row changes made.
     */
    private void onRowChanged(@NonNull final String key,
                              @IntRange(from = 0) final long id) {
        // ENHANCE: update the modified row without a rebuild.
        saveListPosition();
        buildBookList();
    }

    @Override
    public void onBookDeleted(final long bookId) {
        if (bookId == 0 || bookId == vm.getCurrentCenteredBookId()) {
            vm.setCurrentCenteredBookId(0);
        }
        // We don't try to remove the row without a rebuild as this could quickly become complex...
        // e.g. if there is(was) only a single book on the level, we'd have to recursively
        // cleanup each level above the book
        saveListPosition();
        buildBookList();
    }

    @NonNull
    private BooklistNode scrollTo(@NonNull final List<BooklistNode> targetNodes) {

        // 2022-06-26: yet another attempt to tune the repositioning...

        int firstVisibleIPosition = layoutManager.findFirstCompletelyVisibleItemPosition();
        int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();

        final BooklistNode best = findBestNode(targetNodes,
                                               firstVisibleIPosition, lastVisiblePosition);

        final int destPos = best.getAdapterPosition();

        //FIXME: The position we get from find*VisibleItemPosition() is one more than
        // what we have on the BooklistNode - whose bug this is no idea for now.
        // Workaround: adjust manually
        if (firstVisibleIPosition > 0) {
            firstVisibleIPosition--;
        }
        if (lastVisiblePosition > 0) {
            lastVisiblePosition--;
        }

        if (destPos < firstVisibleIPosition || destPos > lastVisiblePosition) {
            final int offset = vb.content.list.getHeight() / 4;
            layoutManager.scrollToPositionWithOffset(destPos, offset);
        }
        return best;
    }

    @NonNull
    private BooklistNode findBestNode(@NonNull final List<BooklistNode> targetNodes,
                                      final int firstVisibleIPosition,
                                      final int lastVisiblePosition) {
        BooklistNode best = targetNodes.get(0);
        if (targetNodes.size() > 1) {
            // Position of the row in the (vertical) center of the screen
            final int centerAdapterPosition =
                    (lastVisiblePosition + firstVisibleIPosition) / 2;
            // distance from currently visible center row
            int distance = Math.abs(best.getAdapterPosition() - centerAdapterPosition);
            // Loop all other rows, looking for a nearer one
            int row = 1;
            while (distance > 0 && row < targetNodes.size()) {
                final BooklistNode node = targetNodes.get(row);
                final int newDist = Math.abs(node.getAdapterPosition() - centerAdapterPosition);
                if (newDist < distance) {
                    distance = newDist;
                    best = node;
                }
                row++;
            }
        }
        return best;
    }

    /**
     * Start the list builder.
     */
    private void buildBookList() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "buildBookList"
                       + "| isBuilding()=" + vm.isBuilding()
                       + "|called from:", new Throwable());
        }

        if (!vm.isBuilding()) {
            vb.progressCircle.show();
            // Invisible... theoretically this means the page should not re-layout
            vb.content.list.setVisibility(View.INVISIBLE);

            // If the book details frame is present, remove it
            final Fragment fragment = getEmbeddedDetailsFragment();
            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .remove(fragment)
                        .commit();
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                final SimpleDateFormat dateFormat =
                        new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.getDefault());
                //noinspection UseOfObsoleteDateTimeApi
                Debug.startMethodTracing("trace-" + dateFormat.format(new Date()));
            }
            // force the adapter to stop displaying by disabling the list.
            // DO NOT REMOVE THE ADAPTER FROM FROM THE VIEW;
            // i.e. do NOT call mVb.list.setAdapter(null)... crashes assured when doing so.
            if (adapter != null) {
                adapter.setBooklist(null);
            }
            vm.buildBookList();
        }
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    private void onBuildCancelled(
            @NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        vb.progressCircle.hide();

        message.getData().ifPresent(data -> {
            vm.onBuildCancelled();

            if (vm.isListLoaded()) {
                displayList(null);
            } else {
                recoverAfterFailedBuild();
            }
        });
    }

    /**
     * Receive notifications that a Book was updated.
     * <p>
     * For a limited set of keys, we directly update the list table which is very fast.
     * <p>
     * Other keys, or full books, will always trigger a list rebuild.
     *
     * @param book the book that changed,
     *             or {@code null} to indicate multiple books were potentially changed.
     * @param keys the item(s) that changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    @Override
    public void onBookUpdated(@Nullable final Book book,
                              @Nullable final String... keys) {

        // Reminder: the actual Book table (and/or relations) are ALREADY UPDATED.
        // The only thing we are updating here is the temporary BookList table
        // and the displayed data

        if (keys != null && Arrays.asList(keys).contains(DBKey.READ__BOOL)) {
            Objects.requireNonNull(book);
            updateListPositions(vm.onBookRead(book.getId(), book.getBoolean(DBKey.READ__BOOL)));

        } else if (keys != null && Arrays.asList(keys).contains(DBKey.LOANEE_NAME)) {
            Objects.requireNonNull(book);
            updateListPositions(vm.onBookLend(book.getId(), book.getLoanee().orElse(null)));

        } else {
            // ENHANCE: update the modified row without a rebuild.
            saveListPosition();
            buildBookList();
        }
    }

    /**
     * Refresh the list data for the given positions only.
     *
     * @param positions to update
     */
    private void updateListPositions(@NonNull final int[] positions) {
        //noinspection ConstantConditions
        adapter.requery();
        for (final int pos : positions) {
            adapter.notifyItemChanged(pos);
        }
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    private void onBuildFailed(@NonNull final LiveDataEvent<TaskResult<Throwable>> message) {
        vb.progressCircle.hide();
        message.getData().ifPresent(data -> {
            Logger.error(TAG, data.getResult());

            vm.onBuildFailed();

            if (vm.isListLoaded()) {
                displayList(null);
            } else {
                recoverAfterFailedBuild();
            }
        });
    }

    private void recoverAfterFailedBuild() {
        // Something is REALLY BAD
        // This is usually (BUT NOT ALWAYS) due to the developer making an oopsie
        // with the Styles. i.e. the style used to build is very likely corrupt.
        // Another reason can be during development when the database structure
        // was changed...
        final Style style = vm.getStyle(this);
        // so we reset the style to recover.. and restarting the app will work.
        vm.onStyleChanged(this, BuiltinStyle.DEFAULT_UUID);
        // but we STILL FORCE A CRASH, SO WE CAN COLLECT DEBUG INFORMATION!
        throw new IllegalStateException("Style=" + style);
    }

    /**
     * Called when the list build succeeded.
     *
     * @param message from the task; contains the (optional) target rows.
     */
    private void onBuildFinished(
            @NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        vb.progressCircle.hide();

        message.getData().map(TaskResult::requireResult).ifPresent(result -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.stopMethodTracing();
            }
            vm.onBuildFinished(result);
            displayList(result.getTargetNodes());
        });
    }

    /**
     * Display the list based on the current cursor, and either scroll to the desired
     * target node(s) or, if none, to the last saved position.
     *
     * @param targetNodes (optional) to re-position to
     */
    private void displayList(@Nullable final List<BooklistNode> targetNodes) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG + "|displayList-" + System.nanoTime(),
                  targetNodes != null ? targetNodes.toString() : "null",
                  new Throwable());
        }

        adapter = new BooklistAdapter(this);
        adapter.setRowClickListener(this::onRowClicked);
        adapter.setRowLongClickListener(this::onCreateContextMenu);

        adapter.setStyle(this, vm.getStyle(this));
        adapter.setBooklist(vm.getBooklist());

        // Combine the adapters for the list header and the actual list
        final ConcatAdapter concatAdapter = new ConcatAdapter(
                new ConcatAdapter.Config.Builder()
                        .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS)
                        .build(),
                new HeaderAdapter(this, () -> vm.getHeaderContent(this)), adapter);

        vb.content.list.setAdapter(concatAdapter);
        vb.content.list.setVisibility(View.VISIBLE);

        if (adapter.getItemCount() > 0) {
            // scroll to the saved position - this should get us close to where we need to be
            scrollToSavedPosition();
            // and wait for layout cycle
            vb.content.list.post(() -> {
                if (targetNodes == null || targetNodes.isEmpty()) {
                    // We're on the precise position
                    final long bookId = vm.getCurrentCenteredBookId();
                    showBookDetailsIfWeCan(bookId);
                } else {
                    // We have target nodes;
                    final BooklistNode node = scrollTo(targetNodes);
                    final long bookId = node.getBookId();
                    vb.content.list.post(() -> showBookDetailsIfWeCan(bookId));
                }
            });
        }
    }

    @Nullable
    private Fragment getEmbeddedDetailsFragment() {
        return vb.content.detailsFrame == null ? null : vb.content.detailsFrame.getFragment();
    }

    private boolean hasEmbeddedDetailsFrame() {
        return WindowSizeClass.getWidth(this) == WindowSizeClass.EXPANDED;
    }

    /**
     * Preserve the {@link LinearLayoutManager#findFirstVisibleItemPosition()}
     * for the CURRENT bookshelf/style combination.
     * <ol>
     *     <li>The row number at the top of the screen.</li>
     *     <li>The pixel offset of that row from the top of the screen.</li>
     * </ol>
     */
    private void saveListPosition() {
        if (!isDestroyed()) {
            final int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
            if (firstVisiblePos != RecyclerView.NO_POSITION) {
                final int viewOffset;
                // the list.getChildAt; not the layoutManager.getChildAt (not sure why...)
                final View topView = vb.content.list.getChildAt(0);
                if (topView == null) {
                    viewOffset = 0;

                } else {
                    // currently our padding is 0, but this is future-proof
                    final int paddingTop = vb.content.list.getPaddingTop();
                    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                            topView.getLayoutParams();
                    viewOffset = topView.getTop() - lp.topMargin - paddingTop;
                }
                vm.saveListPosition(this, firstVisiblePos, viewOffset);
            }
        }
    }

    /**
     * Scroll the list to the position saved in {@link #saveListPosition}.
     * Saves the potentially changed position after the scrolling is done.
     */
    private void scrollToSavedPosition() {
        Objects.requireNonNull(adapter, "adapter");

        final Bookshelf bookshelf = vm.getCurrentBookshelf();
        final int position = bookshelf.getFirstVisibleItemPosition();

        // sanity check
        if (position < 0) {
            layoutManager.scrollToPositionWithOffset(0, 0);

        } else if (position >= adapter.getItemCount()) {
            // the list is shorter than it used to be, just scroll to the end
            layoutManager.scrollToPosition(position);

        } else {
            layoutManager.scrollToPositionWithOffset(position,
                                                     bookshelf.getFirstVisibleItemViewOffset());
        }
    }

    private void openEmbeddedBookDetails(final long bookId) {
        final FragmentManager fm = getSupportFragmentManager();

        Fragment fragment = fm.findFragmentByTag(ShowBookDetailsFragment.TAG);
        if (fragment == null) {
            fragment = ShowBookDetailsFragment.create(
                    bookId, vm.getStyle(this).getUuid(), true);
            fm.beginTransaction()
              .setReorderingAllowed(true)
              .replace(R.id.details_frame, fragment, ShowBookDetailsFragment.TAG)
              .commit();
        } else {
            ((ShowBookDetailsFragment) fragment).reloadBook(bookId);
        }
    }

    private static class HeaderViewHolder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final BooksonbookshelfHeaderBinding vb;

        HeaderViewHolder(@NonNull final BooksonbookshelfHeaderBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private static class HeaderAdapter
            extends RecyclerView.Adapter<HeaderViewHolder> {

        @NonNull
        private final LayoutInflater inflater;
        @NonNull
        private final Supplier<BooklistHeader> headerSupplier;

        /**
         * Constructor.
         *
         * @param context        Current context
         * @param headerSupplier a supplier to get the current header values from
         */
        HeaderAdapter(@NonNull final Context context,
                      @NonNull final Supplier<BooklistHeader> headerSupplier) {
            inflater = LayoutInflater.from(context);
            this.headerSupplier = headerSupplier;
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public HeaderViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
            return new HeaderViewHolder(
                    BooksonbookshelfHeaderBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final HeaderViewHolder holder,
                                     final int position) {
            final BooklistHeader headerContent = headerSupplier.get();

            String header;
            header = headerContent.getStyleName();
            holder.vb.styleName.setText(header);
            holder.vb.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getFilterText();
            holder.vb.filterText.setText(header);
            holder.vb.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getSearchText();
            holder.vb.searchText.setText(header);
            holder.vb.searchText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getBookCount();
            holder.vb.bookCount.setText(header);
            holder.vb.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        @Override
        public long getItemId(final int position) {
            // we only have one row; return a dummy value which is not a row-id in the list-table
            return Integer.MAX_VALUE;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.bob, menu);
            MenuUtils.setupSearchActionView(BooksOnBookshelf.this, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final boolean showPreferredOption =
                    vm.getStyle(BooksOnBookshelf.this).getExpansionLevel() > 1;
            menu.findItem(R.id.MENU_LEVEL_PREFERRED_EXPANSION).setVisible(showPreferredOption);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            fabMenu.hideMenu();

            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_FILTERS) {
                bookshelfFiltersLauncher.launch(vm.getCurrentBookshelf());
                return true;

            } else if (itemId == R.id.MENU_STYLE_PICKER) {
                stylePickerLauncher.launch(vm.getStyle(BooksOnBookshelf.this), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_PREFERRED_EXPANSION) {
                expandAllNodes(vm.getStyle(BooksOnBookshelf.this).getExpansionLevel(), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
                expandAllNodes(1, true);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_COLLAPSE) {
                expandAllNodes(1, false);
                return true;
            }

            return false;
        }

        /**
         * Expand/Collapse the entire list <strong>starting</strong> from the given level.
         * <p>
         * This is called from the options menu:
         * <ul>
         *     <li>Preferred level</li>
         *     <li>expand all</li>
         *     <li>collapse all</li>
         * </ul>
         *
         * @param topLevel the desired top-level which must be kept visible
         * @param expand   desired state
         */
        private void expandAllNodes(@IntRange(from = 1) final int topLevel,
                                    final boolean expand) {
            // It is possible that the list will be empty, if so, ignore
            if (adapter != null && adapter.getItemCount() > 0) {
                saveListPosition();
                vm.expandAllNodes(topLevel, expand);
                displayList(null);
            }
        }
    }
}
