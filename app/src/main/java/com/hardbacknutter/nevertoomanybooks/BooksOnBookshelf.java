/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.core.util.Pair;
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
import com.google.android.material.snackbar.Snackbar;

import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsFragment;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.widgets.SpinnerInteractionListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfHeaderBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditStringLauncher;
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
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

import org.intellij.lang.annotations.Language;

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
    private static final String RK_STYLE_PICKER = TAG + ":rk:" + StylePickerDialogFragment.TAG;
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
                    data -> vm.onBookEditFinished(data)));

    /** Update a list of Books with information from the internet. */
    private final ActivityResultLauncher<UpdateBooklistContract.Input> updateBookListLauncher =
            registerForActivityResult(new UpdateBooklistContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

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
    /**
     * Accept the result from the dialog.
     */
    private final EditLenderDialogFragment.Launcher editLenderLauncher =
            new EditLenderDialogFragment.Launcher(
                    DBKey.LOANEE_NAME,
                    (bookId, loanee) -> vm.updateBooklistOnBookLend(bookId, loanee));

    /** Encapsulates the FAB button/menu. */
    private FabMenu fabMenu;

    /** View Binding. */
    private BooksonbookshelfBinding vb;

    private final BookshelfFiltersDialogFragment.Launcher bookshelfFiltersLauncher =
            new BookshelfFiltersDialogFragment.Launcher(
                    RK_FILTERS, modified -> {
                if (modified) {
                    buildBookList();
                }
            });

    /** Delegate which will handle all positioning/scrolling. */
    private PositioningHelper positioningHelper;

    private final EditInPlaceParcelableLauncher<Bookshelf> editBookshelfLauncher =
            new EditInPlaceParcelableLauncher<>(DBKey.FK_BOOKSHELF,
                                                EditBookshelfDialogFragment::new,
                                                this::onEntityUpdate);

    private final EditInPlaceParcelableLauncher<Author> editAuthorLauncher =
            new EditInPlaceParcelableLauncher<>(DBKey.FK_AUTHOR,
                                                EditAuthorDialogFragment::new,
                                                this::onEntityUpdate);

    private final EditInPlaceParcelableLauncher<Series> editSeriesLauncher =
            new EditInPlaceParcelableLauncher<>(DBKey.FK_SERIES,
                                                EditSeriesDialogFragment::new,
                                                this::onEntityUpdate);

    private final EditInPlaceParcelableLauncher<Publisher> editPublisherLauncher =
            new EditInPlaceParcelableLauncher<>(DBKey.FK_PUBLISHER,
                                                EditPublisherDialogFragment::new,
                                                this::onEntityUpdate);

    private final EditStringLauncher editColorLauncher =
            new EditStringLauncher(DBKey.COLOR, EditColorDialogFragment::new,
                                   this::onInlineStringUpdate);

    private final EditStringLauncher editFormatLauncher =
            new EditStringLauncher(DBKey.FORMAT, EditFormatDialogFragment::new,
                                   this::onInlineStringUpdate);

    private final EditStringLauncher editGenreLauncher =
            new EditStringLauncher(DBKey.GENRE, EditGenreDialogFragment::new,
                                   this::onInlineStringUpdate);

    private final EditStringLauncher editLanguageLauncher =
            new EditStringLauncher(DBKey.LANGUAGE, EditLanguageDialogFragment::new,
                                   this::onInlineStringUpdate);

    private final EditStringLauncher editLocationLauncher =
            new EditStringLauncher(DBKey.LOCATION, EditLocationDialogFragment::new,
                                   this::onInlineStringUpdate);


    /** Listener for the Bookshelf Spinner. */
    private final SpinnerInteractionListener bookshelfSelectionChangedListener =
            new SpinnerInteractionListener(this::onBookshelfSelected);

    /**
     * React to the user selecting a style to apply.
     * <p>
     * We get here after the user SELECTED a style on the {@link StylePickerDialogFragment}.
     * We do NOT come here when the user decided to EDIT a style,
     * which is handled by {@link #editStyleLauncher}.
     */
    private final StylePickerDialogFragment.Launcher stylePickerLauncher =
            new StylePickerDialogFragment.Launcher(RK_STYLE_PICKER, this::onStyleSelected);


    /**
     * The adapter used to fill the Bookshelf selector.
     */
    private ExtArrayAdapter<Bookshelf> bookshelfAdapter;
    private HeaderAdapter headerAdapter;

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
        createFabMenu();

        // setup the list related stuff; the actual list data is generated in onResume
        createBooklistView();

        // After a transition from landscape to portrait,
        // we need to remove the embedded fragment manually.
        // Otherwise, even while not showing, it will be put in 'resumed' state by the system
        if (!hasEmbeddedDetailsFrame()) {
            final FragmentManager fm = getSupportFragmentManager();
            final Fragment fragment = fm.findFragmentByTag(ShowBookDetailsFragment.TAG);
            if (fragment != null) {
                fm.beginTransaction().remove(fragment).commit();
            }
        }

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

        editColorLauncher.registerForFragmentResult(fm, this);
        editFormatLauncher.registerForFragmentResult(fm, this);
        editGenreLauncher.registerForFragmentResult(fm, this);
        editLanguageLauncher.registerForFragmentResult(fm, this);
        editLocationLauncher.registerForFragmentResult(fm, this);

        editBookshelfLauncher.registerForFragmentResult(fm, this);
        editAuthorLauncher.registerForFragmentResult(fm, this);
        editSeriesLauncher.registerForFragmentResult(fm, this);
        editPublisherLauncher.registerForFragmentResult(fm, this);


        stylePickerLauncher.registerForFragmentResult(fm, this);
        editLenderLauncher.registerForFragmentResult(fm, this);
        bookshelfFiltersLauncher.registerForFragmentResult(fm, this);
    }

    private void createViewModel() {
        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        vm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        vm.init(this, getIntent().getExtras());

        vm.onCancelled().observe(this, this::onBuildCancelled);
        vm.onFailure().observe(this, this::onBuildFailed);
        vm.onFinished().observe(this, this::onBuildFinished);

        vm.onSelectAdapterPosition().observe(this, p ->
                positioningHelper.onSelectAdapterPosition(p.first, p.second));

        vm.getOnPositionsUpdated().observe(this, positions -> {
            // Protect against activity restarts where this can get called BEFORE
            // the adapter has been recreated.
            if (adapter != null) {
                adapter.requery(positions);
            }
        });
    }

    /**
     * Create the optional launcher and delegates.
     */
    private void createSyncDelegates() {

        // Reminder: this method cannot be called from onResume... registerForActivityResult
        // can only be called from onCreate

        if (SyncServer.CalibreCS.isEnabled(this)) {
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

        if (SyncServer.StripInfo.isEnabled(this)) {
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
        if (calibreHandler == null && SyncServer.CalibreCS.isEnabled(this)) {
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

    private void createFabMenu() {
        fabMenu = new FabMenu(vb.fab, vb.fabOverlay,
                              vb.fab0ScanBarcode,
                              vb.fab0ScanBarcodeBatch,
                              vb.fab1SearchIsbn,
                              vb.fab2SearchText,
                              vb.fab3AddManually,
                              vb.fab4SearchExternalId);

        fabMenu.setOnClickListener(view -> onFabMenuItemSelected(view.getId()));
        fabMenu.getItem(vb.fab4SearchExternalId.getId())
               .ifPresent(item -> item.setEnabled(EditBookExternalIdFragment.isShowTab(this)));
    }

    private void createBooklistView() {
        headerAdapter = new HeaderAdapter(this, () -> vm.getHeaderContent(this));

        positioningHelper = new PositioningHelper(vb.content.list, headerAdapter.getItemCount());

        // hide the view at creation time. onResume will provide the data and make it visible.
        vb.content.list.setVisibility(View.GONE);

        // Custom fastscroller which actually works (as opposed to the builtin android one).
        // Provides an optional overlay.
        final int overlayType = Prefs.getFastScrollerOverlayType(this);
        FastScroller.attach(vb.content.list, overlayType);

        // attach the scroll-listener
        fabMenu.attach(vb.content.list);

        vb.content.list.setItemViewCacheSize(OFFSCREEN_CACHE_SIZE);
        vb.content.list.setDrawingCacheEnabled(true);
        vb.content.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
    }

    /**
     * Show or hide the synchronization menu.
     */
    private void updateSyncMenuVisibility() {
        final boolean enable =
                SyncServer.CalibreCS.isEnabled(this) && calibreSyncLauncher != null
                ||
                SyncServer.StripInfo.isEnabled(this) && stripInfoSyncLauncher != null;

        //noinspection DataFlowIssue
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
        @Nullable
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
            editStylesLauncher.launch(vm.getStyle().getUuid());
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
    public void onSettingsChanged(@NonNull final Bundle result) {
        super.onSettingsChanged(result);

        if (result.getBoolean(SettingsContract.BKEY_REBUILD_BOOKLIST, false)) {
            vm.setForceRebuildInOnResume(true);
        }
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
               .ifPresent(item -> item.setEnabled(EditBookExternalIdFragment.isShowTab(this)));

        // Initialize/Update the list of bookshelves
        vm.reloadBookshelfList(this);
        bookshelfAdapter.notifyDataSetChanged();
        // and select the current shelf.
        vb.bookshelfSpinner.setSelection(vm.getSelectedBookshelfSpinnerPosition(this));


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

    /**
     * Preserve the adapter position of the top-most visible row
     * for the CURRENT bookshelf/style combination.
     * <ol>
     *     <li>The adapter position at the top of the screen.</li>
     *     <li>The pixel offset of that row from the top of the screen.</li>
     * </ol>
     * Note that we convert the list/layout position to the adapter position and store the latter.
     * <p>
     * TODO: we're calling this probably from places where we shouldn't
     *  example: should we call this from #onRowChanged or #onBookDeleted ?
     */
    private void saveListPosition() {
        if (!isDestroyed()) {
            final Pair<Integer, Integer> positionAndOffset =
                    positioningHelper.getAdapterPositionAndViewOffset();
            vm.saveListPosition(this, positionAndOffset.first, positionAndOffset.second);
        }
    }

    /**
     * Called by the embedded details frame to match-up the list position with the displayed book.
     *
     * @param bookId to scroll the list to.
     */
    @Override
    public void onSyncBook(final long bookId) {
        displayList(vm.getVisibleBookNodes(bookId));
    }

    /**
     * User clicked a row.
     * <ul>
     *      <li>Book: open the details page.</li>
     *      <li>Not a book: expand/collapse the section as appropriate.</li>
     * </ul>
     *
     * @param v               View clicked
     * @param adapterPosition The booklist adapter position.
     */
    private void onRowClicked(@NonNull final View v,
                              final int adapterPosition) {
        //noinspection DataFlowIssue
        final DataHolder rowData = adapter.readDataAt(adapterPosition);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return;
        }

        if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // It's a book, open the details page.
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // store the id as the current 'central' book for repositioning after a rebuild
            vm.setSelectedPosition(bookId, adapterPosition);

            if (hasEmbeddedDetailsFrame()) {
                //  On larger screens, opens the book details fragment embedded.
                openEmbeddedBookDetails(bookId);
            } else {
                //  On small screens, opens a ViewPager with the book details
                //  and swipe prev/next functionality.
                displayBookLauncher.launch(new ShowBookPagerContract.Input(
                        bookId,
                        vm.getStyle().getUuid(),
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
     * @param v               View clicked
     * @param adapterPosition The booklist adapter position
     */
    private void onCreateContextMenu(@NonNull final View v,
                                     final int adapterPosition) {
        //noinspection DataFlowIssue
        final DataHolder rowData = adapter.readDataAt(adapterPosition);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return;
        }

        final ExtPopupMenu contextMenu = new ExtPopupMenu(this)
                .setGroupDividerEnabled();
        final Menu menu = contextMenu.getMenu();

        @BooklistGroup.Id
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
                vm.getMenuHandlers().forEach(h -> h.onCreateMenu(this, menu, inflater));

                final boolean isRead = rowData.getBoolean(DBKey.READ__BOOL);
                menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

                // specifically check LOANEE_NAME independent from the style in use.
                final boolean useLending = ServiceLocator.getInstance().getGlobalFieldVisibility()
                                                         .isVisible(DBKey.LOANEE_NAME)
                                                         .orElse(true);
                final boolean isAvailable = vm.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                if (calibreHandler != null) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    calibreHandler.onPrepareMenu(this, menu, book);
                }

                vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(this, menu, rowData));
                break;
            }
            case BooklistGroup.AUTHOR: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.author, menu);
                vm.getMenuHandlers().forEach(h -> h.onCreateMenu(this, menu, inflater));

                final boolean complete = rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(this, menu, rowData));
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBKey.FK_SERIES) != 0) {
                    final MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.series, menu);
                    vm.getMenuHandlers().forEach(h -> h.onCreateMenu(this, menu, inflater));

                    final boolean complete = rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(this, menu, rowData));

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
                // Note that a "(No Bookshelf)" does NOT exist.
                // Books are always on a shelf.
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
            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR:
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH: {
                menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                         getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                         R.string.menu_update_books)
                    .setIcon(R.drawable.ic_baseline_cloud_download_24);
                break;
            }
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
            menu.add(R.id.MENU_GROUP_BOB_EXPANSION, R.id.MENU_LEVEL_EXPAND, menuOrder++,
                     R.string.option_level_expand)
                .setIcon(R.drawable.ic_baseline_unfold_more_24);
        }

        // If we actually have a menu, show it.
        if (menu.size() > 0) {
            // we have a menu to show, set the title according to the level.
            final int level = rowData.getInt(DBKey.BL_NODE_LEVEL);
            contextMenu.setTitle(adapter.getLevelText(level, adapterPosition));

            if (menu.size() < 5 || WindowSizeClass.getWidth(this) == WindowSizeClass.Medium) {
                // show it anchored
                contextMenu.showAsDropDown(v, menuItem ->
                        onRowContextMenuItemSelected(v, adapterPosition, menuItem));

            } else if (hasEmbeddedDetailsFrame()) {
                contextMenu.show(v, Gravity.START, menuItem ->
                        onRowContextMenuItemSelected(v, adapterPosition, menuItem));
            } else {
                contextMenu.show(v, Gravity.CENTER, menuItem ->
                        onRowContextMenuItemSelected(v, adapterPosition, menuItem));
            }
        }
    }

    /**
     * Handle the row/context menus.
     * <p>
     * <strong>Dev. note:</strong> this used to be simply "onMenuItemSelected",
     * but due to an R8 bug confusing it with "onMenuItemSelected(int, android.view.MenuItem)"
     * ended throwing a "java.lang.LinkageError" ... so the name had to be changed.
     *
     * @param v               View clicked; the anchor for a potential popup menu
     * @param adapterPosition The booklist adapter position
     * @param menuItem        that was selected
     *
     * @return {@code true} if handled.
     */
    private boolean onRowContextMenuItemSelected(@NonNull final View v,
                                                 final int adapterPosition,
                                                 @NonNull final MenuItem menuItem) {
        //noinspection DataFlowIssue
        final DataHolder rowData = adapter.readDataAt(adapterPosition);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return false;
        }

        @IdRes
        final int menuItemId = menuItem.getItemId();

        // Check for row-group independent options first.

        if (menuItemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
            searchMissingCover(nodeRowId);
            return true;

        } else if (menuItemId == R.id.MENU_LEVEL_EXPAND) {
            saveListPosition();

            final long nodeRowId = rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
            vm.setNode(nodeRowId, BooklistNode.NextState.Expand,
                       vm.getStyle().getGroupCount());
            // don't pass the node, we want the list to scroll back to
            // the exact same (saved) position.
            displayList(null);
            return true;

        } else if (menuItemId == R.id.MENU_CALIBRE_SETTINGS) {
            final Intent intent = FragmentHostActivity
                    .createIntent(this, CalibrePreferencesFragment.class);
            startActivity(intent);
            return true;
        }

        // Specific row-group options

        @BooklistGroup.Id
        final int rowGroupId = rowData.getInt(DBKey.BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                if (onRowMenuForBook(rowData, menuItemId)) {
                    return true;
                }
                break;
            }
            case BooklistGroup.AUTHOR: {
                if (onRowMenuForAuthor(v, rowData, menuItemId)) {
                    return true;
                }
                break;
            }
            case BooklistGroup.SERIES: {
                if (onRowMenuForSeries(v, rowData, menuItemId)) {
                    return true;
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (onRowMenuForPublisher(v, rowData, menuItemId)) {
                    return true;
                }
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                if (onRowMenuForBookshelf(rowData, menuItemId)) {
                    return true;
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (onRowMenuForLanguage(rowData, menuItemId)) {
                    return true;
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (menuItemId == R.id.MENU_LOCATION_EDIT) {
                    editLocationLauncher.launch(rowData.getString(DBKey.LOCATION));
                    return true;
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (menuItemId == R.id.MENU_GENRE_EDIT) {
                    editGenreLauncher.launch(rowData.getString(DBKey.GENRE));
                    return true;
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (menuItemId == R.id.MENU_FORMAT_EDIT) {
                    editFormatLauncher.launch(rowData.getString(DBKey.FORMAT));
                    return true;
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (menuItemId == R.id.MENU_COLOR_EDIT) {
                    editColorLauncher.launch(rowData.getString(DBKey.COLOR));
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
            case BooklistGroup.DATE_PUBLISHED_MONTH:
            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR:
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH: {
                if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
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

        return vm.getMenuHandlers()
                 .stream()
                 .anyMatch(h -> h.onMenuItemSelected(this, menuItem, rowData));
    }

    /**
     * Handle the row/context menu for a {@link Book}.
     *
     * @param rowData    the row data
     * @param menuItemId selected menu item
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuForBook(@NonNull final DataHolder rowData,
                                     @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_BOOK_SET_READ
            || menuItemId == R.id.MENU_BOOK_SET_UNREAD) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // toggle the read status
            final boolean status = !rowData.getBoolean(DBKey.READ__BOOL);
            vm.setBookRead(bookId, status);
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_EDIT) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            editByIdLauncher.launch(bookId);
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_DUPLICATE) {
            final Book book = DataHolderUtils.requireBook(rowData);
            duplicateLauncher.launch(book.duplicate(this));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_DELETE) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            final String title = rowData.getString(DBKey.TITLE);
            final List<Author> authors = vm.getAuthorsByBookId(bookId);
            StandardDialogs.deleteBook(this, title, authors, () -> {
                if (vm.deleteBook(bookId)) {
                    saveListPosition();
                    buildBookList();
                }
            });
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET_SINGLE_BOOK) {
            final Book book = DataHolderUtils.requireBook(rowData);
            updateBookLauncher.launch(book);
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_LOAN_ADD) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            editLenderLauncher.launch(bookId, rowData.getString(DBKey.TITLE));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_LOAN_DELETE) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            vm.lendBook(bookId, null);
            return true;

        } else if (menuItemId == R.id.MENU_SHARE) {
            final Book book = DataHolderUtils.requireBook(rowData);
            startActivity(book.getShareIntent(this, vm.getStyle()));
            return true;
        }
        return false;
    }

    /**
     * Handle the row/context menu for an {@link Author}.
     *
     * @param v          View clicked; the anchor for a potential popup menu
     * @param rowData    the row data
     * @param menuItemId selected menu item
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuForAuthor(@NonNull final View v,
                                       @NonNull final DataHolder rowData,
                                       @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER) {
            authorWorksLauncher.launch(new AuthorWorksContract.Input(
                    rowData.getLong(DBKey.FK_AUTHOR),
                    vm.getCurrentBookshelf().getId(),
                    vm.getStyle().getUuid()));
            return true;

        } else if (menuItemId == R.id.MENU_AUTHOR_SET_COMPLETE
                   || menuItemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
            final Author author = DataHolderUtils.requireAuthor(rowData);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
            vm.setAuthorComplete(author, status);
            return true;

        } else if (menuItemId == R.id.MENU_AUTHOR_EDIT) {
            final Author author = DataHolderUtils.requireAuthor(rowData);
            editAuthorLauncher.launch(author);
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            final String dialogTitle = rowData.getString(DBKey.AUTHOR_FORMATTED);
            updateBooksFromInternetData(v, rowData, dialogTitle);
            return true;
        }
        return false;
    }

    /**
     * Handle the row/context menu for a {@link Series}.
     *
     * @param v          View clicked; the anchor for a potential popup menu
     * @param rowData    the row data
     * @param menuItemId selected menu item
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuForSeries(@NonNull final View v,
                                       @NonNull final DataHolder rowData,
                                       @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_SERIES_SET_COMPLETE
            || menuItemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
            vm.setSeriesComplete(series, status);
            return true;

        } else if (menuItemId == R.id.MENU_SERIES_EDIT) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            editSeriesLauncher.launch(series);
            return true;

        } else if (menuItemId == R.id.MENU_SERIES_DELETE) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            StandardDialogs.deleteSeries(this, series, () -> {
                if (vm.delete(this, series)) {
                    saveListPosition();
                    buildBookList();
                }
            });
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            final String dialogTitle = rowData.getString(DBKey.SERIES_TITLE);
            updateBooksFromInternetData(v, rowData, dialogTitle);
            return true;
        }
        return false;
    }

    /**
     * Handle the row/context menu for a {@link Publisher}.
     *
     * @param v          View clicked; the anchor for a potential popup menu
     * @param rowData    the row data
     * @param menuItemId selected menu item
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuForPublisher(@NonNull final View v,
                                          @NonNull final DataHolder rowData,
                                          @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_PUBLISHER_EDIT) {
            final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
            editPublisherLauncher.launch(publisher);
            return true;

        } else if (menuItemId == R.id.MENU_PUBLISHER_DELETE) {
            final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
            StandardDialogs.deletePublisher(this, publisher, () -> {
                if (vm.delete(this, publisher)) {
                    saveListPosition();
                    buildBookList();
                }
            });
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            final String dialogTitle = rowData.getString(DBKey.PUBLISHER_NAME);
            updateBooksFromInternetData(v, rowData, dialogTitle);
            return true;
        }
        return false;
    }

    /**
     * Handle the row/context menu for a {@link Bookshelf}.
     *
     * @param rowData    the row data
     * @param menuItemId selected menu item
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuForBookshelf(@NonNull final DataHolder rowData,
                                          @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_BOOKSHELF_EDIT) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            editBookshelfLauncher.launch(bookshelf);
            return true;

        } else if (menuItemId == R.id.MENU_BOOKSHELF_DELETE) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            StandardDialogs.deleteBookshelf(this, bookshelf, () -> {
                if (vm.delete(bookshelf)) {
                    saveListPosition();
                    buildBookList();
                }
            });
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                    this, rowData, true));
            return true;
        }
        return false;
    }

    /**
     * Handle the row/context menu for a {@link Language}.
     *
     * @param rowData    the row data
     * @param menuItemId selected menu item
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuForLanguage(@NonNull final DataHolder rowData,
                                         @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_LANGUAGE_EDIT) {
            final String text = rowData.getString(DBKey.LANGUAGE);
            final String editLang;
            if (text.length() > 3) {
                editLang = text;
            } else {
                editLang = ServiceLocator.getInstance().getAppLocale()
                                         .getLocale(this, text)
                                         .map(Locale::getDisplayLanguage)
                                         .orElse(text);
            }
            editLanguageLauncher.launch(editLang);
            return true;
        }
        return false;
    }

    private void searchMissingCover(final long nodeRowId) {
        final Optional<BooklistNode> oNode = vm.getNextBookWithoutCover(nodeRowId);
        if (oNode.isPresent()) {
            final List<BooklistNode> list = new ArrayList<>();
            list.add(oNode.get());
            displayList(list);
        } else {
            if (nodeRowId > 1) {
                Snackbar.make(vb.getRoot(), R.string.confirm_no_missing_covers_search_from_top,
                              Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_search, v -> searchMissingCover(0))
                        .show();
            } else {
                Snackbar.make(vb.getRoot(), R.string.info_all_books_have_covers,
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void onFabMenuItemSelected(@IdRes final int menuItemId) {

        if (menuItemId == R.id.fab0_scan_barcode) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Scan);

        } else if (menuItemId == R.id.fab0_scan_barcode_batch) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.ScanBatch);

        } else if (menuItemId == R.id.fab1_search_isbn) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Isbn);

        } else if (menuItemId == R.id.fab2_search_text) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Text);

        } else if (menuItemId == R.id.fab3_add_manually) {
            editByIdLauncher.launch(0L);

        } else if (menuItemId == R.id.fab4_search_external_id) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.ExternalId);

        } else {
            throw new IllegalArgumentException(String.valueOf(menuItemId));
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
                .setVisible(SyncServer.CalibreCS.isEnabled(this) && calibreSyncLauncher != null);

            menu.findItem(R.id.MENU_SYNC_STRIP_INFO)
                .setVisible(SyncServer.StripInfo.isEnabled(this) && stripInfoSyncLauncher != null);
        }

        popupMenu.setTitle(menuItem.getTitle())
                 .showAsDropDown(anchor, this::onNavigationItemSelected);
    }

    /**
     * Allow the user to decide between books on "this bookshelf only" or on all bookshelves
     * and then update all the selected books.
     *
     * @param v           View clicked; the anchor for the popup menu
     * @param rowData     for the row which was selected
     * @param dialogTitle text to show to the user.
     */
    private void updateBooksFromInternetData(@NonNull final View v,
                                             @NonNull final DataHolder rowData,
                                             @NonNull final CharSequence dialogTitle) {
        new ExtPopupMenu(this)
                .inflate(R.menu.update_books)
                .setTitle(dialogTitle)
                .setMessage(getString(R.string.menu_update_books))
                .showAsDropDown(v, menuItem -> {
                    final int itemId = menuItem.getItemId();
                    Boolean onlyThisShelf = null;

                    if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_THIS_NODE_ONLY) {
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

    /**
     * The user picked a different style from the {@link StylePickerDialogFragment}.
     *
     * @param uuid of the style to apply
     */
    private void onStyleSelected(@NonNull final String uuid) {
        saveListPosition();
        vm.onStyleChanged(BooksOnBookshelf.this, uuid);
        vm.resetPreferredListRebuildMode(BooksOnBookshelf.this);
        buildBookList();
    }

    /**
     * Called from {@link StylePickerDialogFragment} when the user wants to edit
     * the selected style.
     *
     * @param style to edit
     */
    void editStyle(@NonNull final Style style) {
        editStyleLauncher.launch(EditStyleContract.edit(style, true));
    }

    private void onBookshelfSelected(final long bookshelfId) {
        if (bookshelfId != vm.getCurrentBookshelf().getId()) {
            vm.setCurrentBookshelf(BooksOnBookshelf.this, bookshelfId);
            saveListPosition();
            buildBookList();
        }
    }


    /**
     * Receives notifications that an inline-string column was updated.
     *
     * @param requestKey the request-key, a {@link DBKey}, from the update event
     * @param original   the original string
     * @param modified   the updated string
     */
    private void onInlineStringUpdate(@NonNull final String requestKey,
                                      @NonNull final String original,
                                      @NonNull final String modified) {
//        if (vm.getStyle(this).isShowField(Style.Screen.List, requestKey)) {
//            // The entity is shown on the book level, do a full rebuild
//            saveListPosition();
//            buildBookList();
//        } else {
        // Update only the levels, and trigger an adapter update
        // ENHANCE: update the modified row without a rebuild.
        saveListPosition();
        buildBookList();
//        }
    }

    /**
     * Receives notifications that an {@link Entity} (but NOT a Book) potentially was updated.
     *
     * @param requestKey the request-key, a {@link DBKey}, from the update event
     * @param entity     the entity that potentially was updated
     */
    private void onEntityUpdate(@NonNull final String requestKey,
                                @NonNull final Entity entity) {
//        if (vm.getStyle(this).isShowField(Style.Screen.List, requestKey)) {
//            // The entity is shown on the book level, do a full rebuild
//            saveListPosition();
//            buildBookList();
//        } else {
        // Update only the levels, and trigger an adapter update
        // ENHANCE: update the modified row without a rebuild.
        saveListPosition();
        buildBookList();
//        }
    }

    /**
     * Receives notifications that a {@link Book} potentially was updated.
     * <p>
     * For a limited set of keys, we directly update the list table which is very fast.
     * <p>
     * Other keys, or full books, will always trigger a list rebuild.
     *
     * @param book the book
     * @param keys the item(s) that potentially were changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    @Override
    public void onBookUpdated(@Nullable final Book book,
                              @Nullable final String... keys) {

        // Reminder: the actual Book table (and/or relations) are ALREADY UPDATED.
        // The only thing we are updating here is the temporary BookList table as needed
        // and/or the displayed data

        if (keys != null && Arrays.asList(keys).contains(DBKey.READ__BOOL)) {
            Objects.requireNonNull(book);
            vm.updateBooklistOnBookRead(book.getId(), book.getBoolean(DBKey.READ__BOOL));

        } else if (keys != null && Arrays.asList(keys).contains(DBKey.LOANEE_NAME)) {
            Objects.requireNonNull(book);
            vm.updateBooklistOnBookLend(book.getId(), book.getLoanee().orElse(null));

        } else if (keys != null && Arrays.asList(keys).contains(DBKey.COVER[0])) {
            Objects.requireNonNull(book);
            vm.updateBooklistOnBookCover(book.getId());

        } else {
            // ENHANCE: update the modified row without a rebuild.
            saveListPosition();
            buildBookList();
        }
    }

    @Override
    public void onBookDeleted(final long bookId) {
        vm.onBookDeleted(bookId);
        // We don't try to remove the row without a rebuild as this could quickly become complex...
        // e.g. if there is(was) only a single book on the level, we'd have to recursively
        // cleanup each level above the book
        saveListPosition();
        buildBookList();
    }


    /**
     * Start the list builder.
     */
    private void buildBookList() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            LoggerFactory.getLogger().d(TAG, "buildBookList",
                                        "vm.isBuilding()=" + vm.isBuilding(),
                                        "called from:",
                                        new Throwable());
        }

        if (!vm.isBuilding()) {
            vb.progressCircle.show();
            // Invisible... theoretically this means the page should not re-layout
            vb.content.list.setVisibility(View.INVISIBLE);

            // prevent quick users on slow devices to switch while building
            vb.bookshelfSpinner.setEnabled(false);

            // If the book details frame and fragment is present, remove the fragment
            if (hasEmbeddedDetailsFrame()) {
                final Fragment fragment = vb.content.detailsFrame.getFragment();
                if (fragment != null) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(fragment)
                            .commit();
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.startMethodTracing("trace-" + LocalDateTime
                        .now().withNano(0)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
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
     * Called when the list build was cancelled.
     *
     * @param message from the task
     */
    private void onBuildCancelled(@NonNull final LiveDataEvent<BoBTask.Outcome> message) {
        vb.progressCircle.hide();

        message.process(ignored -> {
            vm.onBuildCancelled();

            if (vm.isListLoaded()) {
                displayList(null);
            } else {
                recoverAfterFailedBuild();
            }
        });
    }

    /**
     * Called when the list build failed.
     *
     * @param message from the task
     */
    private void onBuildFailed(@NonNull final LiveDataEvent<Throwable> message) {
        vb.progressCircle.hide();
        message.process(e -> {
            LoggerFactory.getLogger().e(TAG, e);

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
        final Style style = vm.getStyle();
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
    private void onBuildFinished(@NonNull final LiveDataEvent<BoBTask.Outcome> message) {
        vb.progressCircle.hide();

        message.process(outcome -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.stopMethodTracing();
            }
            vm.onBuildFinished(outcome);
            displayList(outcome.getTargetNodes());
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
            LoggerFactory.getLogger().d(TAG, "displayList",
                                        System.nanoTime(),
                                        targetNodes != null ? targetNodes.toString() : "null",
                                        new Throwable());
        }

        adapter = new BooklistAdapter(this, vm.getStyle());
        adapter.setOnRowClickListener(this::onRowClicked);
        ShowContextMenu preferredMode = ShowContextMenu.getPreferredMode(this);
        if (preferredMode == ShowContextMenu.ButtonIfSpace && hasEmbeddedDetailsFrame()) {
            preferredMode = ShowContextMenu.NoButton;
        }
        adapter.setOnRowShowMenuListener(preferredMode, this::onCreateContextMenu);

        adapter.setBooklist(vm.getBooklist());

        // Combine the adapters for the list header and the actual list
        final ConcatAdapter concatAdapter = new ConcatAdapter(
                new ConcatAdapter.Config.Builder()
                        .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS)
                        .build(),
                headerAdapter, adapter);

        vb.content.list.setAdapter(concatAdapter);
        vb.content.list.setVisibility(View.VISIBLE);
        vb.bookshelfSpinner.setEnabled(true);

        if (adapter.getItemCount() > 0) {
            if (targetNodes == null || targetNodes.isEmpty()) {
                // There are no target nodes, just scroll to the saved position
                final Pair<Integer, Integer> savedListPosition = vm.getSavedListPosition();
                final int adapterPosition = savedListPosition.first;
                final int viewOffset = savedListPosition.second;
                positioningHelper.scrollTo(adapterPosition, viewOffset, adapter.getItemCount());
                // wait for layout cycle and display the book
                vb.content.list.post(() -> showBookDetailsIfWeCan(adapterPosition,
                                                                  vm.getSelectedBookId()));
            } else if (targetNodes.size() == 1) {
                // There is a single target node; scroll to it
                final BooklistNode node = positioningHelper.scrollTo(targetNodes);
                // wait for layout cycle and display the book
                vb.content.list.post(() -> showBookDetailsIfWeCan(node.getAdapterPosition(),
                                                                  node.getBookId()));

            } else {
                // We'll need to find the "best" node (from all target nodes).
                // First scroll to the saved position which will serve as the starting point
                // for finding the "best" node.
                final Pair<Integer, Integer> savedListPosition = vm.getSavedListPosition();
                final int adapterPosition = savedListPosition.first;
                final int viewOffset = savedListPosition.second;
                positioningHelper.scrollTo(adapterPosition, viewOffset, adapter.getItemCount());
                // wait for layout cycle
                vb.content.list.post(() -> {
                    // Now find the "best" node and scroll to it
                    final BooklistNode node = positioningHelper.scrollTo(targetNodes);
                    // wait again for layout cycle and display the book
                    vb.content.list.post(() -> showBookDetailsIfWeCan(node.getAdapterPosition(),
                                                                      node.getBookId()));
                });
            }
        }
    }

    /**
     * Display the given book in the embedded details fragment IF POSSIBLE.
     *
     * @param adapterPosition the booklist adapter position
     * @param bookId          of the book to open
     */
    @SuppressLint("Range")
    private void showBookDetailsIfWeCan(final int adapterPosition,
                                        @IntRange(from = 0) final long bookId) {
        if (bookId > 0 && hasEmbeddedDetailsFrame()) {
            vm.setSelectedPosition(bookId, adapterPosition);
            openEmbeddedBookDetails(bookId);
        } else {
            // Make sure to disable the current stored book id and position
            vm.setSelectedPosition(0, RecyclerView.NO_POSITION);
        }
    }

    /**
     * Check if there is an embedded details-frame in our current layout.
     *
     * @return {@code true} if there is.
     */
    private boolean hasEmbeddedDetailsFrame() {
        return vb.content.detailsFrame != null;
    }

    /**
     * Open the given book in the embedded details fragment.
     *
     * @param bookId of the book to open
     */
    private void openEmbeddedBookDetails(final long bookId) {
        final FragmentManager fm = getSupportFragmentManager();

        Fragment fragment = fm.findFragmentByTag(ShowBookDetailsFragment.TAG);
        if (fragment == null) {
            fragment = ShowBookDetailsFragment.create(
                    bookId, vm.getStyle().getUuid(), true);
            fm.beginTransaction()
              .setReorderingAllowed(true)
              .replace(R.id.details_frame, fragment, ShowBookDetailsFragment.TAG)
              .commit();
        } else {
            ((ShowBookDetailsFragment) fragment).displayBook(bookId);
        }
    }

    /**
     * A delegate/wrapper for the book-list view handling all things related to positioning.
     * <p>
     * Takes care of conversions between adapter-position and layout-position
     * due to the {@link ConcatAdapter} used with the {@link HeaderAdapter}
     */
    private static class PositioningHelper {

        @NonNull
        private final LinearLayoutManager layoutManager;
        private final int headerItemCount;
        @NonNull
        private final RecyclerView recyclerView;

        PositioningHelper(@NonNull final RecyclerView recyclerView,
                          final int headerItemCount) {
            this.recyclerView = recyclerView;
            //noinspection DataFlowIssue
            this.layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            this.headerItemCount = headerItemCount;
        }

        @Nullable
        View findViewByAdapterPosition(final int adapterPosition) {
            return layoutManager.findViewByPosition(adapterPosition + headerItemCount);
        }

        /**
         * LiveData callback to select (highlight) the current row.
         *
         * @param previousAdapterPosition to un-select
         * @param currentAdapterPosition  to select
         */
        void onSelectAdapterPosition(final int previousAdapterPosition,
                                     final int currentAdapterPosition) {
            View view;
            if (previousAdapterPosition != currentAdapterPosition) {
                view = layoutManager.findViewByPosition(previousAdapterPosition + headerItemCount);
                if (view != null) {
                    view.setSelected(false);
                }
            }
            view = layoutManager.findViewByPosition(currentAdapterPosition + headerItemCount);
            if (view != null) {
                view.setSelected(true);
            }
        }

        /**
         * Retrieve the current adapter position and view-offset of the top-most visible row.
         *
         * @return a {@link Pair} with (adapterPosition, viewOffset)
         */
        @NonNull
        Pair<Integer, Integer> getAdapterPositionAndViewOffset() {
            final int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
            if (firstVisiblePos == RecyclerView.NO_POSITION) {
                return new Pair<>(0, 0);
            }

            int adapterPosition = firstVisiblePos - headerItemCount;
            // can theoretically happen with an empty list which has a header
            if (adapterPosition < 0) {
                adapterPosition = 0;
            }

            final int viewOffset;
            // the list.getChildAt; not the layoutManager.getChildAt (not sure why...)
            final View topView = recyclerView.getChildAt(0);
            if (topView == null) {
                viewOffset = 0;
            } else {
                // currently our padding is 0, but this is future-proof
                final int paddingTop = recyclerView.getPaddingTop();
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        topView.getLayoutParams();
                viewOffset = topView.getTop() - lp.topMargin - paddingTop;
            }
            return new Pair<>(adapterPosition, viewOffset);
        }

        /**
         * Scroll to the "best" of the given target nodes.
         *
         * @param targetNodes candidates to scroll to.
         *
         * @return the node we ended up at.
         */
        @NonNull
        BooklistNode scrollTo(@NonNull final List<BooklistNode> targetNodes) {

            // the layout positions (i.e. including the header row)
            int firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition();
            int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();

            if (firstVisiblePosition > 0) {
                firstVisiblePosition = firstVisiblePosition - headerItemCount;
            }
            if (lastVisiblePosition > 0) {
                lastVisiblePosition = lastVisiblePosition - headerItemCount;
            }

            final BooklistNode best;
            if (targetNodes.size() == 1) {
                best = targetNodes.get(0);
            } else {
                best = findBestNode(targetNodes, firstVisiblePosition, lastVisiblePosition);
            }

            final int destPos = best.getAdapterPosition();

            if (destPos < firstVisiblePosition || destPos > lastVisiblePosition) {
                final int offset = recyclerView.getHeight() / 4;
                layoutManager.scrollToPositionWithOffset(destPos + headerItemCount, offset);
            }
            return best;
        }

        /**
         * Scroll the list to the given adapter-position/view-offset.
         *
         * @param adapterPosition to scroll to
         * @param offset          the view offset to apply
         * @param maxPosition     the last/maximum position to which we can scroll
         *                        (i.e. the length of the list)
         */
        void scrollTo(final int adapterPosition,
                      final int offset,
                      final int maxPosition) {
            final int position = adapterPosition + headerItemCount;

            // sanity check
            if (position <= headerItemCount) {
                layoutManager.scrollToPositionWithOffset(0, 0);

            } else if (position >= maxPosition) {
                // the list is shorter than it used to be, just scroll to the end
                layoutManager.scrollToPosition(position);
            } else {
                layoutManager.scrollToPositionWithOffset(position, offset);
            }
        }

        @NonNull
        private BooklistNode findBestNode(@NonNull final List<BooklistNode> targetNodes,
                                          final int firstVisibleAdapterPosition,
                                          final int lastVisibleAdapterPosition) {
            // Assume first is best
            BooklistNode best = targetNodes.get(0);
            // Position of the row in the (vertical) center of the screen
            final int centerAdapterPosition =
                    (lastVisibleAdapterPosition + firstVisibleAdapterPosition) / 2;
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
            return best;
        }
    }

    private static class HeaderViewHolder
            extends RecyclerView.ViewHolder
            implements BindableViewHolder<BooklistHeader> {

        @NonNull
        private final BooksonbookshelfHeaderBinding vb;

        HeaderViewHolder(@NonNull final BooksonbookshelfHeaderBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        @Override
        public void onBind(@NonNull final BooklistHeader headerContent) {
            String header;
            header = headerContent.getStyleName();
            vb.styleName.setText(header);
            vb.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getFilterText();
            vb.filterText.setText(header);
            vb.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getSearchText();
            vb.searchText.setText(header);
            vb.searchText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getBookCount();
            vb.bookCount.setText(header);
            vb.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
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
            holder.onBind(headerSupplier.get());
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
                    vm.getStyle().getExpansionLevel() > 1;
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
                stylePickerLauncher.launch(vm.getStyle(), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_PREFERRED_EXPANSION) {
                // URGENT: if we use last-saved position we're totally off from where we need to be
                expandAllNodes(vm.getStyle().getExpansionLevel(), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
                // position on the last-saved node
                expandAllNodes(1, true);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_COLLAPSE) {
                // position on the last-saved node
                expandAllNodes(1, false);
                return true;

            } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                        BooksOnBookshelf.this));
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
                vm.expandAllNodes(topLevel, expand);
                // position on the last-saved node
                displayList(null);
            }
        }
    }
}
