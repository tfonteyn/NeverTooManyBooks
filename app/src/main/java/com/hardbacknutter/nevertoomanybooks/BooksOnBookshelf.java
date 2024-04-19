/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AddBookBySearchContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CalibreSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.IntentFactory;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchFtsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsFragment;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditAction;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.TopRowListPosition;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.header.HeaderAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.widgets.SpinnerInteractionListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
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
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditStringDialogFragment;
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
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.NavDrawer;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.BottomSheetMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtPopupMenu;

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
    private static final String RK_MENU = TAG + ":rk" + BottomSheetMenu.TAG;

    /** Number of views to cache offscreen arbitrarily set to 20; the default is 2. */
    private static final int OFFSCREEN_CACHE_SIZE = 20;

    /** Make a backup. */
    private final ActivityResultLauncher<Void> exportLauncher =
            registerForActivityResult(new ExportContract(), success -> {
                // Nothing to do
            });

    /** Multi-type adapter to manage list connection to cursor. */
    @Nullable
    private BooklistAdapter adapter;

    /** View Binding. */
    private BooksonbookshelfBinding vb;

    /** Delegate which will handle all positioning/scrolling. */
    private PositioningHelper positioningHelper;

    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> stripInfoSyncLauncher;

    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> calibreSyncLauncher;

    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler calibreHandler;

    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel vm;

    /**
     * Edit the app settings.
     */
    private final ActivityResultLauncher<String> editSettingsLauncher =
            registerForActivityResult(new SettingsContract(), o -> o.ifPresent(
                    this::onSettingsChanged));

    /** Do an import. */
    private final ActivityResultLauncher<Void> importLauncher =
            registerForActivityResult(new ImportContract(), o -> o.ifPresent(
                    data -> vm.onImportFinished(this, data)));

    /** Manage the list of (preferred) styles. */
    private final ActivityResultLauncher<String> editStylesLauncher =
            registerForActivityResult(new PreferredStylesContract(), o -> o.ifPresent(
                    data -> vm.onEditStylesFinished(this, data)));

    private final BottomSheetMenu.Launcher menuLauncher =
            new BottomSheetMenu.Launcher(RK_MENU, (adapterPosition, menuItemId) -> {
                final View view = positioningHelper.findViewByAdapterPosition(adapterPosition);
                Objects.requireNonNull(view, "No view");
                onRowContextMenuItemSelected(view, adapterPosition, menuItemId);
            });

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
    private final ActivityResultLauncher<AddBookBySearchContract.Input> addBookBySearchLauncher =
            registerForActivityResult(new AddBookBySearchContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** Edit a Book. */
    private final ActivityResultLauncher<EditBookContract.Input> editBookLauncher =
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
            registerForActivityResult(new EditBookshelvesContract(), o -> o.ifPresent(
                    bookshelfId -> vm.onManageBookshelvesFinished(this, bookshelfId)));

    private final EditLenderDialogFragment.Launcher editLenderLauncher =
            new EditLenderDialogFragment.Launcher(
                    DBKey.LOANEE_NAME,
                    (bookId, loanee) -> vm.onBookLoaneeChanged(bookId, loanee));
    private final EditStringDialogFragment.Launcher editColorLauncher =
            new EditStringDialogFragment.Launcher(
                    DBKey.COLOR, EditColorDialogFragment::new, (original, modified)
                    -> vm.onInlineStringUpdate(DBKey.COLOR, original, modified));
    private final EditStringDialogFragment.Launcher editFormatLauncher =
            new EditStringDialogFragment.Launcher(
                    DBKey.FORMAT, EditFormatDialogFragment::new, (original, modified)
                    -> vm.onInlineStringUpdate(DBKey.FORMAT, original, modified));
    private final EditStringDialogFragment.Launcher editGenreLauncher =
            new EditStringDialogFragment.Launcher(
                    DBKey.GENRE, EditGenreDialogFragment::new, (original, modified)
                    -> vm.onInlineStringUpdate(DBKey.GENRE, original, modified));
    private final EditStringDialogFragment.Launcher editLanguageLauncher =
            new EditStringDialogFragment.Launcher(
                    DBKey.LANGUAGE, EditLanguageDialogFragment::new, (original, modified)
                    -> vm.onInlineStringUpdate(DBKey.LANGUAGE, original, modified));
    private final EditStringDialogFragment.Launcher editLocationLauncher =
            new EditStringDialogFragment.Launcher(
                    DBKey.LOCATION, EditLocationDialogFragment::new, (original, modified)
                    -> vm.onInlineStringUpdate(DBKey.LOCATION, original, modified));
    private final ParcelableDialogLauncher<Bookshelf> editBookshelfLauncher =
            new ParcelableDialogLauncher<>(
                    DBKey.FK_BOOKSHELF, EditBookshelfDialogFragment::new,
                    bookshelf -> vm.onEntityUpdate(DBKey.FK_BOOKSHELF, bookshelf));
    private final ParcelableDialogLauncher<Author> editAuthorLauncher =
            new ParcelableDialogLauncher<>(
                    DBKey.FK_AUTHOR, EditAuthorDialogFragment::new,
                    author -> vm.onEntityUpdate(DBKey.FK_AUTHOR, author));
    private final ParcelableDialogLauncher<Series> editSeriesLauncher =
            new ParcelableDialogLauncher<>(
                    DBKey.FK_SERIES, EditSeriesDialogFragment::new,
                    series -> vm.onEntityUpdate(DBKey.FK_SERIES, series));
    private final ParcelableDialogLauncher<Publisher> editPublisherLauncher =
            new ParcelableDialogLauncher<>(
                    DBKey.FK_PUBLISHER, EditPublisherDialogFragment::new,
                    publisher -> vm.onEntityUpdate(DBKey.FK_PUBLISHER, publisher));
    private final BookshelfFiltersDialogFragment.Launcher bookshelfFiltersLauncher =
            new BookshelfFiltersDialogFragment.Launcher(
                    RK_FILTERS, modified -> {
                if (modified) {
                    // After applying filters, we always start the list at the top.
                    vm.setSelectedBook(0, RecyclerView.NO_POSITION);
                    buildBookList();
                }
            });
    /** Encapsulates the FAB button/menu. */
    private FabMenu fabMenu;

    /**
     * The adapter used to fill the Bookshelf selector.
     */
    private ExtArrayAdapter<Bookshelf> bookshelfAdapter;

    private HeaderAdapter headerAdapter;

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

    private NavDrawer navDrawer;
    /**
     * The single back-press handler.
     * <p>
     * <a href="https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#best-practices">Best practices</a>
     * states that we should have multiple handlers and enable/disable them as needed.
     * e.g. have a callback to close the Navigation drawer solely.
     * and trigger the enable/disable using an observable. That's all very well if the
     * state is held in the ViewModel, but for all 3 situation in our handler
     * the state is directly dependent on another UI element. It would be absurd to
     * duplicate state in the VM. So... sticking with conditionals in this single handler.
     * <p>
     * Notes:
     * - a {@link DrawerLayout.SimpleDrawerListener} could be used for the nav-drawer.
     * - we could implement a similar listener on the FABMenu.
     * - the SearchCriteria could be handled as per above link.
     * ... so code in 3 different places as compared to all of it centralized here
     */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // It's possible to have both FAB and NAV open if the
                    // user first opened the FAB, then swiped the NAV into visibility.
                    // So make sure to close both before deciding we're done here.
                    final boolean navClosed = navDrawer.close();
                    final boolean fabClosed = fabMenu.hideMenu();

                    // If either was actually closed, we're done here
                    if (navClosed || fabClosed) {
                        return;
                    }

                    // Secondly, after an "Advanced Local Search", the BoB
                    // will be displaying a filtered list.
                    // i.e. the current list will have search criteria present,
                    // If the user taps 'back' we clear the search criteria and rebuild the list.
                    if (isTaskRoot() && !vm.getSearchCriteria().isEmpty()) {
                        vm.getSearchCriteria().clear();
                        setNavIcon();
                        buildBookList();
                        return;
                    }

                    // Prevent looping
                    this.setEnabled(false);
                    // Simulate the user pressing the 'back' key,
                    // which minimize the app.
                    getOnBackPressedDispatcher().onBackPressed();
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        createFragmentResultListeners();
        createViewModel();
        createSyncDelegates();
        createHandlers();

        // Always present
        navDrawer = NavDrawer.create(this, menuItem ->
                onNavigationItemSelected(menuItem.getItemId()));

        initToolbar();

        createBookshelfSpinner();
        createFabMenu();

        createLayoutManager();

        // setup the list related stuff; the actual list data is generated in onResume
        createBooklistView();

        // Remove the potentially embedded fragment and its children.
        // Otherwise, even while not showing, it will be put in 'resumed' state by the system
        removeEmbeddedDetailsFragment();

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

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

        menuLauncher.registerForFragmentResult(fm, this);
        stylePickerLauncher.registerForFragmentResult(fm, this);
        editLenderLauncher.registerForFragmentResult(fm, this);
        bookshelfFiltersLauncher.registerForFragmentResult(fm, this);
    }

    private void createViewModel() {
        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        vm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        vm.init(this, getIntent().getExtras());

        vm.onCancelled().observe(this, message -> {
            vb.progressCircle.hide();
            message.process(ignored -> {
                if (vm.isListLoaded()) {
                    displayList(null);
                } else {
                    vm.recoverAfterFailedBuild(this);
                }
            });
        });
        vm.onFailure().observe(this, message -> {
            vb.progressCircle.hide();
            message.process(e -> {
                LoggerFactory.getLogger().e(TAG, e);
                if (vm.isListLoaded()) {
                    displayList(null);
                } else {
                    vm.recoverAfterFailedBuild(this);
                }
            });
        });
        vm.onFinished().observe(this, message -> {
            vb.progressCircle.hide();
            message.process(outcome -> {
                vm.onBuildFinished(outcome);
                displayList(outcome.getTargetNodes());
            });
        });

        vm.onHighlightSelection().observe(this, p ->
                positioningHelper.highlightSelection(p.first, p.second));

        vm.onPositionsUpdated().observe(this, positions -> {
            // Protect against activity restarts where this can get called BEFORE
            // the adapter has been recreated.
            if (adapter != null) {
                adapter.requery(positions);
            }
        });

        vm.onTriggerRebuildList().observe(this, message ->
                message.process(recreateLayoutManager -> {
                    if (!vm.isBuilding()) {
                        if (recreateLayoutManager) {
                            createLayoutManager();
                        }
                        buildBookList();
                    }
                }));
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
                                vm.setForceRebuildInOnResume();
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
                                vm.setForceRebuildInOnResume();
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
                // Show or hide the synchronization menu.
                // Note this is only effective for the actual sync switches.
                // The launchers MUST have been created at Activity startup,
                // due to how "registerForActivityResult" works.
                //
                // TODO: enabling the Calibre server in preferences will not trigger
                //  the sync menu to be shown. The app MUST be restarted for that.
                final boolean enable =
                        SyncServer.CalibreCS.isEnabled(this) && calibreSyncLauncher != null
                        ||
                        SyncServer.StripInfo.isEnabled(this) && stripInfoSyncLauncher != null;

                //noinspection DataFlowIssue
                navDrawer.getMenuItem(R.id.SUBMENU_SYNC).setVisible(enable);
                navDrawer.open();
            } else {
                // Simulate the user pressing the 'back' key.
                getOnBackPressedDispatcher().onBackPressed();
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

    /**
     * Create or recreate the {@link RecyclerView.LayoutManager}.
     *
     * @throws IllegalArgumentException when there is a bug with the enums
     */
    private void createLayoutManager() {
        //TODO: show a 'tip' when running in grid-mode + embedded-frame
        // and explain that embedded mode forces list-mode
        final Style.Layout layout = vm.getStyle().getLayout(hasEmbeddedDetailsFrame());
        // and remember it. See #onResume where we need to check/compare it again
        vm.setCurrentLayout(layout);

        switch (layout) {
            case List: {
                final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
                vb.content.list.setLayoutManager(layoutManager);
                break;
            }
            case Grid: {
                final int spanCount = vm.getStyle().getCoverScale().getGridSpanCount(this);
                final GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
                layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(final int position) {
                        final int dataPosition = position - headerAdapter.getItemCount();
                        if (dataPosition >= 0) {
                            //noinspection DataFlowIssue
                            final DataHolder rowData = adapter.readDataAt(dataPosition);
                            //noinspection DataFlowIssue
                            if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
                                // A book, i.e. a cover, is always 1 cell.
                                return 1;
                            }
                        }
                        // all other BooklistGroup's use the full width.
                        return spanCount;
                    }
                });

                vb.content.list.setLayoutManager(layoutManager);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(layout));
        }
    }

    private void createBooklistView() {
        headerAdapter = new HeaderAdapter(this, () -> vm.getHeaderContent(this));

        positioningHelper = new PositioningHelper(vb.content.list, headerAdapter.getItemCount());

        // hide the view at creation time. onResume will provide the data and make it visible.
        vb.content.list.setVisibility(View.GONE);

        // Custom fastscroller which actually works (as opposed to the builtin android one).
        // Provides an optional overlay.
        if (vb.content.list.getLayoutManager() instanceof LinearLayoutManager) {
            final int overlayType = Prefs.getFastScrollerOverlayType(this);
            FastScroller.attach(vb.content.list, overlayType);
        }
        // attach the scroll-listener
        fabMenu.attach(vb.content.list);

        vb.content.list.setItemViewCacheSize(OFFSCREEN_CACHE_SIZE);
        vb.content.list.setDrawingCacheEnabled(true);
        vb.content.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
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
        vm.setForceRebuildInOnResume();
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

    /**
     * Handle the {@link NavigationView} menu.
     *
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if the menuItem was handled.
     */
    private boolean onNavigationItemSelected(@IdRes final int menuItemId) {
        saveListPosition();

        if (menuItemId == R.id.SUBMENU_SYNC) {
            showNavigationSubMenu(R.id.SUBMENU_SYNC, R.string.action_synchronize,
                                  menuItemId, R.menu.sync);
            return false;
        }

        navDrawer.close();

        if (menuItemId == R.id.MENU_ADVANCED_SEARCH) {
            ftsSearchLauncher.launch(vm.getSearchCriteria());
            return true;

        } else if (menuItemId == R.id.MENU_MANAGE_LIST_STYLES) {
            editStylesLauncher.launch(vm.getStyle().getUuid());
            return true;

        } else if (menuItemId == R.id.MENU_FILE_IMPORT) {
            importLauncher.launch(null);
            return true;

        } else if (menuItemId == R.id.MENU_FILE_EXPORT) {
            exportLauncher.launch(null);
            return true;

        } else if (menuItemId == R.id.MENU_SYNC_CALIBRE && calibreSyncLauncher != null) {
            calibreSyncLauncher.launch(null);
            return true;

        } else if (menuItemId == R.id.MENU_SYNC_STRIP_INFO && stripInfoSyncLauncher != null) {
            stripInfoSyncLauncher.launch(null);
            return true;

        } else if (menuItemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            manageBookshelvesLauncher.launch(vm.getBookshelf().getId());
            return true;

        } else if (menuItemId == R.id.MENU_SETTINGS) {
            editSettingsLauncher.launch(null);
            return true;

        } else if (menuItemId == R.id.MENU_HELP) {
            startActivity(IntentFactory.createGithubHelpIntent(this));
            return true;

        } else if (menuItemId == R.id.MENU_ABOUT) {
            startActivity(IntentFactory.createAboutIntent(this));
            return true;
        }

        return false;
    }

    @Override
    public void onSettingsChanged(@NonNull final SettingsContract.Output result) {
        super.onSettingsChanged(result);

        if (result.isForceRebuildBooklist()) {
            vm.setForceRebuildInOnResume();
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        if (isFinishing() || isDestroyed()) {
            // don't build the list needlessly
            return;
        }

        if (isRecreating()) {
            // If this turn round in onResume() we're recreating, then force a rebuild
            // for when we get back here in onResume() after the recreation.
            vm.setForceRebuildInOnResume();
            // don't build the list needlessly
            return;
        }

        // If we have search criteria enabled (i.e. we're filtering the current list)
        // then we should display the 'up' indicator.
        setNavIcon();

        fabMenu.getItem(vb.fab4SearchExternalId.getId())
               .ifPresent(item -> item.setEnabled(EditBookExternalIdFragment.isShowTab(this)));

        // Initialize/Update the list of bookshelves
        vm.reloadBookshelfList(this);
        bookshelfAdapter.notifyDataSetChanged();
        // and select the current shelf.
        vb.bookshelfSpinner.setSelection(vm.getSelectedBookshelfSpinnerPosition(this));

        if (vm.isForceRebuildInOnResume() || !vm.isListLoaded()) {
            // This is only needed if the style was changed to use a different Layout.
            // We must NOT recreate it here otherwise.
            if (vm.getStyle().getLayout(hasEmbeddedDetailsFrame()) != vm.getCurrentLayout()) {
                createLayoutManager();
            }
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
     * <p>
     * This should be called each time the user starts a potentially list-changing action.
     * Examples:
     * {@link #onRowClicked(View, int)},
     * {@link #onRowContextMenuItemSelected(View, int, int)}
     * {@link #onNavigationItemSelected(int)}
     */
    private void saveListPosition() {
        if (!isDestroyed() && !vm.isBuilding()) {
            vm.saveBookshelfTopRowPosition(this, positioningHelper.getTopRowPosition());
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

    @Override
    public void onBookUpdated(@Nullable final Book book,
                              @Nullable final String... keys) {
        vm.onBookUpdated(book, keys);
    }

    @Override
    public void onBookDeleted(final long bookId) {
        saveListPosition();
        vm.onBookDeleted(bookId);
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
        saveListPosition();

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
            vm.setSelectedBook(bookId, adapterPosition);

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
            // it's a level, expand/collapse
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

        final Menu menu = MenuUtils.create(this);

        @BooklistGroup.Id
        final int rowGroupId = rowData.getInt(DBKey.BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                createRowMenuForBook(rowData, menu);
                break;
            }
            case BooklistGroup.AUTHOR: {
                createRowMenuForAuthor(rowData, menu);
                break;
            }
            case BooklistGroup.SERIES: {
                createRowMenuForSeries(rowData, menu);
                break;
            }
            case BooklistGroup.PUBLISHER: {
                createRowMenuForPublisher(rowData, menu);
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                createRowMenuForBookshelf(rowData, menu);
                break;
            }
            case BooklistGroup.LANGUAGE: {
                createRowMenuForLanguage(rowData, menu);
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
        menu.add(R.id.MENU_NEXT_MISSING_COVER, R.id.MENU_NEXT_MISSING_COVER, menuOrder,
                 R.string.option_goto_next_book_without_cover)
            .setIcon(R.drawable.ic_baseline_broken_image_24);

        // if it's a level, add the expand option
        if (rowData.getInt(DBKey.BL_NODE_GROUP) != BooklistGroup.BOOK) {
            menu.add(R.id.MENU_GROUP_BOB_EXPANSION, R.id.MENU_LEVEL_EXPAND, ++menuOrder,
                     R.string.option_level_expand)
                .setIcon(R.drawable.ic_baseline_unfold_more_24);
        }

        // If we actually have a menu, show it.
        if (menu.size() > 0) {
            // we have a menu to show, set the title according to the level.
            final CharSequence menuTitle = adapter
                    .getLevelText(rowData.getInt(DBKey.BL_NODE_LEVEL), adapterPosition);


            if (menu.size() < 5 || WindowSizeClass.getWidth(this) == WindowSizeClass.Medium) {
                new ExtPopupMenu(this, true)
                        .setTitle(menuTitle)
                        .setListener(menuItemId -> onRowContextMenuItemSelected(v, adapterPosition,
                                                                                menuItemId))
                        .setMenu(menu)
                        .show(v, ExtPopupMenu.Location.Anchored);

            } else if (hasEmbeddedDetailsFrame()) {
                new ExtPopupMenu(this, true)
                        .setTitle(menuTitle)
                        .setListener(menuItemId -> onRowContextMenuItemSelected(v, adapterPosition,
                                                                                menuItemId))
                        .setMenu(menu)
                        .show(v, ExtPopupMenu.Location.Start);
            } else {
//                new ExtPopupMenu(this, true)
//                        .setTitle(menuTitle)
//                        .setListener(menuItemId -> onRowContextMenuItemSelected(v, adapterPosition,
//                                                                                menuItemId))
//                        .setMenu(menu)
//                        .show(v, ExtPopupMenu.Location.Center);

                menuLauncher.launch(menuTitle, null, true, menu, adapterPosition);
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
     * @param menuItemId      The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onRowContextMenuItemSelected(@NonNull final View v,
                                                 final int adapterPosition,
                                                 @IdRes final int menuItemId) {
        saveListPosition();

        //noinspection DataFlowIssue
        final DataHolder rowData = adapter.readDataAt(adapterPosition);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return false;
        }

        // Check for row-group independent options first.

        if (menuItemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
            searchMissingCover(nodeRowId);
            return true;

        } else if (menuItemId == R.id.MENU_LEVEL_EXPAND) {
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
                if (onRowMenuForBook(adapterPosition, rowData, menuItemId)) {
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
        if (calibreHandler != null) {
            final Book book = Book.from(rowData.getLong(DBKey.FK_BOOK));
            if (calibreHandler.onMenuItemSelected(this, menuItemId, book)) {
                return true;
            }
        }

        return vm.getMenuHandlers()
                 .stream()
                 .anyMatch(h -> h.onMenuItemSelected(this, menuItemId, rowData));
    }

    /**
     * Create the row/context menu for a {@link Book}.
     *
     * @param rowData the row data
     * @param menu    to attach to
     *
     * @see #onRowMenuForBook(int, DataHolder, int)
     */
    private void createRowMenuForBook(final DataHolder rowData,
                                      final Menu menu) {
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
        final boolean useLending = ServiceLocator.getInstance()
                                                 .isFieldEnabled(DBKey.LOANEE_NAME);
        final boolean isAvailable = vm.isAvailable(rowData);
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

        if (calibreHandler != null) {
            final Book book = Book.from(rowData.getLong(DBKey.FK_BOOK));
            calibreHandler.onPrepareMenu(this, menu, book);
        }

        vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(this, menu, rowData));
    }

    /**
     * Handle the row/context menu for a {@link Book}.
     *
     * @param adapterPosition the row where the menu item was selected
     * @param rowData         the row data
     * @param menuItemId      The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @see #createRowMenuForBook(DataHolder, Menu)
     */
    private boolean onRowMenuForBook(final int adapterPosition,
                                     @NonNull final DataHolder rowData,
                                     @IdRes final int menuItemId) {

        final long bookId = rowData.getLong(DBKey.FK_BOOK);
        vm.setSelectedBook(bookId, adapterPosition);

        if (menuItemId == R.id.MENU_BOOK_SET_READ
            || menuItemId == R.id.MENU_BOOK_SET_UNREAD) {
            // toggle the read status
            final boolean status = !rowData.getBoolean(DBKey.READ__BOOL);
            vm.setBookRead(bookId, status);
            if (hasEmbeddedDetailsFrame()) {
                // refresh the entire fragment.
                // We could send a message so only the child-fragment
                // with the read-status is updated but it's complicated enough already
                openEmbeddedBookDetails(bookId);
            }
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_EDIT) {
            editBookLauncher.launch(new EditBookContract.Input(bookId, vm.getStyle()));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_DUPLICATE) {
            final Book book = Book.from(bookId);
            editBookLauncher.launch(new EditBookContract.Input(book.duplicate(this),
                                                               vm.getStyle()));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_DELETE) {
            final String title = rowData.getString(DBKey.TITLE);
            final List<Author> authors = vm.getAuthorsByBookId(bookId);
            StandardDialogs.deleteBook(this, title, authors, () -> vm.deleteBook(bookId));
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET_SINGLE_BOOK) {
            final Book book = Book.from(bookId);
            updateBookLauncher.launch(book);
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_LOAN_ADD) {
            editLenderLauncher.launch(bookId, rowData.getString(DBKey.TITLE));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_LOAN_DELETE) {
            vm.deleteLoan(bookId);
            return true;

        } else if (menuItemId == R.id.MENU_SHARE) {
            final Book book = Book.from(bookId);
            startActivity(book.getShareIntent(this, vm.getStyle()));
            return true;
        }
        return false;
    }

    /**
     * Create the row/context menu for an {@link Author}.
     *
     * @param rowData the row data
     * @param menu    to attach to
     *
     * @see #onRowMenuForAuthor(View, DataHolder, int)
     */
    private void createRowMenuForAuthor(final DataHolder rowData,
                                        final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.author, menu);
        vm.getMenuHandlers().forEach(h -> h.onCreateMenu(this, menu, inflater));

        final boolean complete = rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
        menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
        menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

        vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(this, menu, rowData));
    }

    /**
     * Handle the row/context menu for an {@link Author}.
     *
     * @param v          View clicked; the anchor for a potential popup menu
     * @param rowData    the row data
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @see #createRowMenuForAuthor(DataHolder, Menu)
     */
    private boolean onRowMenuForAuthor(@NonNull final View v,
                                       @NonNull final DataHolder rowData,
                                       @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER) {
            authorWorksLauncher.launch(new AuthorWorksContract.Input(
                    rowData.getLong(DBKey.FK_AUTHOR),
                    vm.getBookshelf().getId(),
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
            editAuthorLauncher.launch(EditAction.EditInPlace, author);
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            final String dialogTitle = rowData.getString(DBKey.AUTHOR_FORMATTED);
            updateBooksFromInternetData(v, rowData, dialogTitle);
            return true;
        }
        return false;
    }

    /**
     * Create the row/context menu for a {@link Series}.
     *
     * @param rowData the row data
     * @param menu    to attach to
     *
     * @see #onRowMenuForSeries(View, DataHolder, int)
     */
    private void createRowMenuForSeries(final DataHolder rowData,
                                        final Menu menu) {
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
    }

    /**
     * Handle the row/context menu for a {@link Series}.
     *
     * @param v          View clicked; the anchor for a potential popup menu
     * @param rowData    the row data
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @see #createRowMenuForSeries(DataHolder, Menu)
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
            editSeriesLauncher.launch(EditAction.EditInPlace, series);
            return true;

        } else if (menuItemId == R.id.MENU_SERIES_DELETE) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            StandardDialogs.deleteSeries(this, series, () -> vm.delete(this, series));
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            final String dialogTitle = rowData.getString(DBKey.SERIES_TITLE);
            updateBooksFromInternetData(v, rowData, dialogTitle);
            return true;
        }
        return false;
    }

    /**
     * Create the row/context menu for a {@link Publisher}.
     *
     * @param rowData the row data
     * @param menu    to attach to
     *
     * @see #onRowMenuForPublisher(View, DataHolder, int)
     */
    private void createRowMenuForPublisher(final DataHolder rowData,
                                           final Menu menu) {
        if (rowData.getLong(DBKey.FK_PUBLISHER) != 0) {
            getMenuInflater().inflate(R.menu.publisher, menu);
        } else {
            // It's a "(No Publisher)" node
            menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                     getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                     R.string.menu_update_books)
                .setIcon(R.drawable.ic_baseline_cloud_download_24);
        }
    }

    /**
     * Handle the row/context menu for a {@link Publisher}.
     *
     * @param v          View clicked; the anchor for a potential popup menu
     * @param rowData    the row data
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @see #createRowMenuForPublisher(DataHolder, Menu)
     */
    private boolean onRowMenuForPublisher(@NonNull final View v,
                                          @NonNull final DataHolder rowData,
                                          @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_PUBLISHER_EDIT) {
            final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
            editPublisherLauncher.launch(EditAction.EditInPlace, publisher);
            return true;

        } else if (menuItemId == R.id.MENU_PUBLISHER_DELETE) {
            final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
            StandardDialogs.deletePublisher(this, publisher, () -> vm.delete(this, publisher));
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            final String dialogTitle = rowData.getString(DBKey.PUBLISHER_NAME);
            updateBooksFromInternetData(v, rowData, dialogTitle);
            return true;
        }
        return false;
    }

    /**
     * Create the row/context menu for a {@link Bookshelf}.
     *
     * @param rowData the row data
     * @param menu    to attach to
     *
     * @see #onRowMenuForBookshelf(DataHolder, int)
     */
    private void createRowMenuForBookshelf(final DataHolder rowData,
                                           final Menu menu) {
        if (!rowData.getString(DBKey.FK_BOOKSHELF).isEmpty()) {
            getMenuInflater().inflate(R.menu.bookshelf, menu);
        }
        // Note that a "(No Bookshelf)" does NOT exist.
        // Books are always on a shelf.
    }

    /**
     * Handle the row/context menu for a {@link Bookshelf}.
     *
     * @param rowData    the row data
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @see #createRowMenuForBookshelf(DataHolder, Menu)
     */
    private boolean onRowMenuForBookshelf(@NonNull final DataHolder rowData,
                                          @IdRes final int menuItemId) {
        if (menuItemId == R.id.MENU_BOOKSHELF_EDIT) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            editBookshelfLauncher.launch(EditAction.EditInPlace, bookshelf);
            return true;

        } else if (menuItemId == R.id.MENU_BOOKSHELF_DELETE) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            StandardDialogs.deleteBookshelf(this, bookshelf, () -> vm.delete(this, bookshelf));
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                    this, rowData, true));
            return true;
        }
        return false;
    }

    /**
     * Create the row/context menu for a {@link BooklistGroup#LANGUAGE}.
     *
     * @param rowData the row data
     * @param menu    to attach to
     *
     * @see #onRowMenuForLanguage(DataHolder, int)
     */
    private void createRowMenuForLanguage(final DataHolder rowData,
                                          final Menu menu) {
        if (!rowData.getString(DBKey.LANGUAGE).isEmpty()) {
            menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                     getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.ic_baseline_edit_24);
        }
    }

    /**
     * Handle the row/context menu for a {@link BooklistGroup#LANGUAGE}.
     *
     * @param rowData    the row data
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @see #createRowMenuForLanguage(DataHolder, Menu)
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
            addBookBySearchLauncher.launch(new AddBookBySearchContract.Input(
                    AddBookBySearchContract.By.Scan,
                    vm.getStyle()));

        } else if (menuItemId == R.id.fab0_scan_barcode_batch) {
            addBookBySearchLauncher.launch(new AddBookBySearchContract.Input(
                    AddBookBySearchContract.By.ScanBatch,
                    vm.getStyle()));

        } else if (menuItemId == R.id.fab1_search_isbn) {
            addBookBySearchLauncher.launch(new AddBookBySearchContract.Input(
                    AddBookBySearchContract.By.Isbn,
                    vm.getStyle()));

        } else if (menuItemId == R.id.fab2_search_text) {
            addBookBySearchLauncher.launch(new AddBookBySearchContract.Input(
                    AddBookBySearchContract.By.Text,
                    vm.getStyle()));

        } else if (menuItemId == R.id.fab3_add_manually) {
            editBookLauncher.launch(new EditBookContract.Input(0L, vm.getStyle()));

        } else if (menuItemId == R.id.fab4_search_external_id) {
            addBookBySearchLauncher.launch(new AddBookBySearchContract.Input(
                    AddBookBySearchContract.By.ExternalId,
                    vm.getStyle()));

        } else {
            throw new IllegalArgumentException(String.valueOf(menuItemId));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void showNavigationSubMenu(@IdRes final int subMenuId,
                                       @StringRes final int subMenuTitleId,
                                       @IdRes final int menuItemId,
                                       @MenuRes final int menuRes) {

        final View anchor = navDrawer.getMenuItemView(subMenuId);

        final Menu menu = MenuUtils.create(this, menuRes);

        if (menuItemId == R.id.SUBMENU_SYNC) {
            menu.findItem(R.id.MENU_SYNC_CALIBRE)
                .setVisible(SyncServer.CalibreCS.isEnabled(this) && calibreSyncLauncher != null);

            menu.findItem(R.id.MENU_SYNC_STRIP_INFO)
                .setVisible(SyncServer.StripInfo.isEnabled(this) && stripInfoSyncLauncher != null);
        }

        new ExtPopupMenu(this, true)
                .setTitle(getString(subMenuTitleId))
                .setListener(this::onNavigationItemSelected)
                .setMenu(menu)
                .show(anchor, ExtPopupMenu.Location.Anchored);
    }

    /**
     * Allow the user to decide between books on "this bookshelf only" or on all bookshelves
     * and then update all the selected books.
     *
     * @param anchor      View clicked; the anchor for the popup menu
     * @param rowData     for the row which was selected
     * @param dialogTitle text to show to the user.
     */
    private void updateBooksFromInternetData(@NonNull final View anchor,
                                             @NonNull final DataHolder rowData,
                                             @NonNull final CharSequence dialogTitle) {

        final Menu menu = MenuUtils.create(this, R.menu.update_books);

        new ExtPopupMenu(this, true)
                .setTitle(dialogTitle)
                .setMessage(getString(R.string.menu_update_books))
                .setListener(menuItemId -> {
                    Boolean onlyThisShelf = null;

                    if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET_THIS_NODE_ONLY) {
                        onlyThisShelf = true;
                    } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET_ALL_SHELVES) {
                        onlyThisShelf = false;
                    }
                    if (onlyThisShelf != null) {
                        updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                                this, rowData, onlyThisShelf));
                        return true;
                    }
                    return false;
                })
                .setMenu(menu)
                .show(anchor, ExtPopupMenu.Location.Anchored);
    }

    /**
     * The user picked a different style from the {@link StylePickerDialogFragment}.
     *
     * @param uuid of the style to apply
     */
    private void onStyleSelected(@NonNull final String uuid) {
        saveListPosition();

        vm.resetPreferredListRebuildMode(this);
        vm.onStyleChanged(this, uuid);

        // New style, so the layout might have changed
        createLayoutManager();
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

    /**
     * The user picked a different Bookshelf from the spinner.
     *
     * @param bookshelfId of the Bookshelf to use
     */
    private void onBookshelfSelected(final long bookshelfId) {
        if (bookshelfId != vm.getBookshelf().getId()) {
            // Save for the soon-to-be previous bookshelf
            saveListPosition();

            vm.selectBookshelf(this, bookshelfId);
            // New style, so the layout might have changed
            createLayoutManager();
            buildBookList();
        }
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

            // Remove the potentially embedded fragment and its children.
            removeEmbeddedDetailsFragment();

            // force the adapter to stop displaying by disabling the list.
            // DO NOT REMOVE THE ADAPTER FROM FROM THE VIEW;
            // i.e. do NOT call vb.content.list.setAdapter(null)... crashes assured when doing so.
            if (adapter != null) {
                adapter.setBooklist(null);
            }
            vm.buildBookList();
        }
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

        adapter = vm.createBooklistAdapter(this, hasEmbeddedDetailsFrame());
        adapter.setOnRowClickListener(this::onRowClicked);
        adapter.setOnRowShowMenuListener(
                vm.getShowContextMenuMode(this, hasEmbeddedDetailsFrame()),
                this::onCreateContextMenu);

        // Combine the adapters for the list header and the actual list
        final ConcatAdapter concatAdapter = new ConcatAdapter(
                new ConcatAdapter.Config.Builder()
                        .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS)
                        .build(),
                headerAdapter, adapter);

        vb.content.list.setAdapter(concatAdapter);
        // Set visible before we do any scrolling,
        // as we need the (internal) requestLayout() call to do its work
        vb.content.list.setVisibility(View.VISIBLE);
        vb.bookshelfSpinner.setEnabled(true);

        if (adapter.getItemCount() > 0) {
            // Scroll to the previously stored position
            final TopRowListPosition topRowPos = vm.getBookshelfTopRowPosition();
            positioningHelper.scrollTo(topRowPos.getAdapterPosition(),
                                       topRowPos.getViewOffset(),
                                       adapter.getItemCount());

            // wait for layout cycle so the list will have valid first/last visible row.
            vb.content.list.post(() -> {
                if (targetNodes == null || targetNodes.isEmpty()) {
                    // There are no target nodes, display the book details if possible
                    showBookDetailsIfWeCan(vm.getSelectedBookId(), topRowPos.getAdapterPosition());
                } else {
                    // Use the target nodes to find the "best" node
                    // and scroll it to the center of the screen
                    final BooklistNode node = positioningHelper.scrollTo(targetNodes);
                    // Sanity check, should never happen... flw
                    if (node != null) {
                        // again wait for layout cycle and display the book details if possible
                        vb.content.list.post(() -> showBookDetailsIfWeCan(
                                node.getBookId(),
                                node.getAdapterPosition()));
                    }
                }
            });
        }
    }

    /**
     * Display the given book in the embedded details fragment IF POSSIBLE.
     *
     * @param bookId          of the book to open
     * @param adapterPosition the booklist adapter position
     */
    @SuppressLint("Range")
    private void showBookDetailsIfWeCan(@IntRange(from = 0) final long bookId,
                                        final int adapterPosition) {
        if (bookId > 0 && hasEmbeddedDetailsFrame()) {
            vm.setSelectedBook(bookId, adapterPosition);
            openEmbeddedBookDetails(bookId);
        } else {
            // Make sure to disable the current stored position
            vm.setSelectedBook(0, RecyclerView.NO_POSITION);
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
     * If present, remove the embedded fragment and its children fragments.
     */
    private void removeEmbeddedDetailsFragment() {
        @Nullable
        final Fragment fragment;

        if (vb.content.detailsFrame != null) {
            fragment = vb.content.detailsFrame.getFragment();
        } else {
            // We STILL try to find it, as it could be existing in the FM
            // but not hooked-up with the frame
            fragment = getSupportFragmentManager()
                    .findFragmentByTag(ShowBookDetailsFragment.TAG);
        }

        if (fragment != null) {
            final FragmentManager childFm = fragment.getChildFragmentManager();
            childFm.getFragments().forEach(child -> childFm.beginTransaction()
                                                           .setReorderingAllowed(true)
                                                           .remove(child)
                                                           .commit());

            getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .remove(fragment)
                    .commit();
        }
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

        private final int headerItemCount;
        @NonNull
        private final RecyclerView recyclerView;

        PositioningHelper(@NonNull final RecyclerView recyclerView,
                          final int headerItemCount) {
            this.recyclerView = recyclerView;
            this.headerItemCount = headerItemCount;
        }

        @Nullable
        View findViewByAdapterPosition(final int adapterPosition) {
            //noinspection DataFlowIssue
            return recyclerView.getLayoutManager()
                               .findViewByPosition(adapterPosition + headerItemCount);
        }

        /**
         * Highlight (select) the current row and unhighlight (unselect) the previous one.
         *
         * @param previousAdapterPosition to un-select, can be {@code RecyclerView.NO_POSITION}
         * @param currentAdapterPosition  to select, can be {@code RecyclerView.NO_POSITION}
         */
        void highlightSelection(final int previousAdapterPosition,
                                final int currentAdapterPosition) {
            final LinearLayoutManager layoutManager =
                    (LinearLayoutManager) recyclerView.getLayoutManager();

            if (previousAdapterPosition != RecyclerView.NO_POSITION) {
                if (previousAdapterPosition != currentAdapterPosition) {
                    //noinspection DataFlowIssue
                    final View view = layoutManager.findViewByPosition(
                            previousAdapterPosition + headerItemCount);
                    if (view != null) {
                        view.setSelected(false);
                    }
                }
            }

            if (currentAdapterPosition != RecyclerView.NO_POSITION) {
                //noinspection DataFlowIssue
                final View view = layoutManager.findViewByPosition(currentAdapterPosition
                                                                   + headerItemCount);
                if (view != null) {
                    view.setSelected(true);
                }
            }
        }

        /**
         * Retrieve the current adapter position and view-offset of the top-most visible row.
         *
         * @return adapter position of the top row
         */
        @NonNull
        TopRowListPosition getTopRowPosition() {
            final LinearLayoutManager layoutManager =
                    (LinearLayoutManager) recyclerView.getLayoutManager();

            //noinspection DataFlowIssue
            final int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
            if (firstVisiblePos == RecyclerView.NO_POSITION) {
                return new TopRowListPosition(0, 0);
            }

            // convert the layout position to the BookList adapter position.
            int adapterPosition = firstVisiblePos - headerItemCount;
            // can theoretically happen with an empty list which has a header
            if (adapterPosition < 0) {
                adapterPosition = 0;
            }

            final int viewOffset = getViewOffset(0);
            return new TopRowListPosition(adapterPosition, viewOffset);
        }

        private int getViewOffset(final int index) {
            final int viewOffset;
            // the list.getChildAt; not the layoutManager.getChildAt (not sure why...)
            final View topView = recyclerView.getChildAt(index);
            if (topView == null) {
                viewOffset = 0;
            } else {
                // currently our padding is 0, but this is future-proof
                final int paddingTop = recyclerView.getPaddingTop();
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        topView.getLayoutParams();
                viewOffset = topView.getTop() - lp.topMargin - paddingTop;
            }
            return viewOffset;
        }

        /**
         * Scroll the list to the given adapter-position/view-offset.
         *
         * @param adapterPosition to scroll to
         * @param viewOffset      to set
         * @param maxPosition     the last/maximum position to which we can scroll
         *                        (i.e. the length of the list)
         */
        void scrollTo(final int adapterPosition,
                      final int viewOffset,
                      final int maxPosition) {
            final LinearLayoutManager layoutManager =
                    (LinearLayoutManager) recyclerView.getLayoutManager();

            final int position = adapterPosition + headerItemCount;

            // sanity check
            if (position <= headerItemCount) {
                // Scroll to the top
                //noinspection DataFlowIssue
                layoutManager.scrollToPosition(0);

            } else if (position >= maxPosition) {
                // The list is shorter than it used to be,
                // scroll to the end disregarding the offset
                //noinspection DataFlowIssue
                layoutManager.scrollToPosition(position);
            } else {
                //noinspection DataFlowIssue
                layoutManager.scrollToPositionWithOffset(position, viewOffset);
            }
        }

        /**
         * Scroll to the "best" of the given target nodes.
         * <p>
         * The {@link #recyclerView} <strong>MUST></strong> have been through a layout phase.
         * <p>
         * If the best node is currently on-screen, no scrolling will be done,
         * otherwise the list will be scrolled <strong>centering</strong> the best node.
         *
         * @param targetNodes candidates to scroll to.
         *
         * @return the node we ended up at.
         *
         * @throws IllegalArgumentException debug only: if the list had less then 2 nodes
         */
        @Nullable
        BooklistNode scrollTo(@NonNull final List<BooklistNode> targetNodes) {
            final LinearLayoutManager layoutManager =
                    (LinearLayoutManager) recyclerView.getLayoutManager();

            // the LAYOUT positions; i.e. including the header row
            //noinspection DataFlowIssue
            final int firstLayoutPos = layoutManager.findFirstCompletelyVisibleItemPosition();
            final int lastLayoutPos = layoutManager.findLastCompletelyVisibleItemPosition();

            // Sanity check, should never happen... flw
            if (firstLayoutPos == RecyclerView.NO_POSITION) {
                return null;
            }

            final int firstAdapterPos = Math.max(0, firstLayoutPos - headerItemCount);
            final int lastAdapterPos = Math.max(0, lastLayoutPos - headerItemCount);

            BooklistNode best;
            if (targetNodes.size() == 1) {
                best = targetNodes.get(0);

            } else {
                final int centerPos = Math.max(0, (firstAdapterPos + lastAdapterPos) / 2);

                // Assume first is best
                best = targetNodes.get(0);
                // distance from currently visible center row
                int distance = Math.abs(best.getAdapterPosition() - centerPos);
                // Loop all other rows, looking for a nearer one
                int row = 1;
                while (distance > 0 && row < targetNodes.size()) {
                    final BooklistNode node = targetNodes.get(row);
                    final int newDist = Math.abs(node.getAdapterPosition() - centerPos);
                    if (newDist < distance) {
                        distance = newDist;
                        best = node;
                    }
                    row++;
                }
            }

            final int destPos = best.getAdapterPosition();
            // If the destination is off-screen, scroll to it.
            if (destPos < firstAdapterPos || destPos > lastAdapterPos) {
                // back to LAYOUT position by adding the header
                layoutManager.scrollToPosition(destPos + headerItemCount);
            }
            return best;
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

            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_FILTERS) {
                bookshelfFiltersLauncher.launch(vm.getBookshelf());
                return true;

            } else if (menuItemId == R.id.MENU_STYLE_PICKER) {
                stylePickerLauncher.launch(vm.getStyle(), false);
                return true;

            } else if (menuItemId == R.id.MENU_LEVEL_PREFERRED_EXPANSION) {
                // URGENT: if we use last-saved position we're totally off from where we need to be
                expandAllNodes(vm.getStyle().getExpansionLevel(), false);
                return true;

            } else if (menuItemId == R.id.MENU_LEVEL_EXPAND) {
                // position on the last-saved node
                expandAllNodes(1, true);
                return true;

            } else if (menuItemId == R.id.MENU_LEVEL_COLLAPSE) {
                // position on the last-saved node
                expandAllNodes(1, false);
                return true;

            } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET) {
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
            // It is possible that the list will be empty, if so, ignore.
            // Note we're getting the count from the adapter; i.e. from the current Cursor
            // only... (which is != total count) but we're only checking the > 0
            // so that's perfectly fine (and faster)
            if (adapter != null && adapter.getItemCount() > 0) {
                vm.expandAllNodes(topLevel, expand);
                // position on the last-saved node
                displayList(null);
            }
        }
    }
}
