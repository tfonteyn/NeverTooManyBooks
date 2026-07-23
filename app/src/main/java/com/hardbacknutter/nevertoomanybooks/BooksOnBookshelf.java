/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.fastscroller.OnFastScrollStateChangeListener;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AddBookBySearchContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CalibreSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GithubIntentFactory;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchFtsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsFragment;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.TopRowListPosition;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.PositioningHelper;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.header.HeaderAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMAuthor;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMBook;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMBookshelf;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMColor;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMFormat;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMLanguage;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMLocation;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMPublisher;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMSeries;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RMTag;
import com.hardbacknutter.nevertoomanybooks.booklist.rowmenu.RowMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ScreenLayout;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.core.widgets.SpinnerInteractionListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelffilters.BookshelfFiltersLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.autocomplete.AutoCompletePickerLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice.MultiChoiceLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.localsearch.SearchFtsFragment;
import com.hardbacknutter.nevertoomanybooks.localsearch.SearchViewHelper;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.settings.FastScrollerMode;
import com.hardbacknutter.nevertoomanybooks.settings.Tuning;
import com.hardbacknutter.nevertoomanybooks.settings.identifiers.IdentifiersEditorContract;
import com.hardbacknutter.nevertoomanybooks.settings.styles.EditPreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.settings.styles.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * <p>
 * TODO: This class is littered with ActivityResultLauncher and *DialogFragment.Launcher
 * objects etc... Refactor to sharing the VM is becoming VERY urgent.
 * <p>
 * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
 * Each time we format the code, methods and variables jump around.
 * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
 * => fixed in IDEA 2026.2 EAP 1
 * <p>
 * Notes on the local-search:
 * <ol>Advanced:
 *     <li>User clicks navigation panel menu search option</li>
 *     <li>{@link SearchFtsFragment} is started</li>
 *     <li>As the user types in criteria, the search is done in real-time and
 *         the <strong>number of results</strong> is shown to the user</li>
 *     <li>When the user taps the 'display' button, {@link SearchFtsFragment} returns
 *         an id-list and the fts search terms</li>
 *     <li>{@link #ftsSearchLauncher} sets the incoming fts criteria</li>
 *     <li>{@link #onResume} builds the list</li>
 * </ol>
 *
 * <ol>Standard:
 *     <li>User clicks option menu search icon</li>
 *     <li>shows the search widget, user types keywords and <strong>a real-time search</strong>
 *         is done while displaying titles only.</li>
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
 * <p>
 * As an alternative to advanced/standard search, the user can also define filters on per-bookshelf
 * basis. These are static and applied at the time the list is build.
 */
public class BooksOnBookshelf
        extends BaseActivity
        implements BookChangedListener {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";

    /** Row Menu as a BottomSheet {@link FragmentResultListener} request key. */
    private static final String RK_MENU = TAG + ":rk:menu";

    private static final String RK_SET_BOOKSHELVES = TAG + ":rk:setBookshelves";
    private static final String RK_SET_LOCATION = TAG + ":rk:setLocation";

    /** The adapter used to fill the Bookshelf selector. */
    private ExtArrayAdapter<Bookshelf> bookshelfAdapter;
    /** The adapter showing the list header. */
    private HeaderAdapter headerAdapter;
    /** Multi-type adapter to manage list connection to cursor. */
    @Nullable
    private BooklistAdapter adapter;
    /** Delegate which will handle all positioning/scrolling. */
    private PositioningHelper positioningHelper;
    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler calibreHandler;
    /** Encapsulates the FAB button/menu. */
    private FabMenu fabMenu;
    private SearchViewHelper searchViewHelper;
    private ToolbarMenuProvider toolbarMenuProvider;

    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel vm;
    /** View Binding. */
    private BooksonbookshelfBinding vb;

    /** Encapsulate all row menus for {@link BooklistGroup}s. */
    private RowGroupMenuHelper rowGroupMenuHelper;

    /** Edit the app settings. */
    private ActivityResultLauncher<String> editSettingsLauncher;
    /** Do an import. */
    private ActivityResultLauncher<Void> importLauncher;
    /** Make a backup. */
    private ActivityResultLauncher<Void> exportLauncher;
    /** Manage the list of (preferred) styles. */
    private ActivityResultLauncher<String> editStylesLauncher;
    /** Edit an individual style. */
    private ActivityResultLauncher<EditStyleContract.Input> editStyleLauncher;
    /** Manage the bookshelves. */
    private ActivityResultLauncher<Long> manageBookshelvesLauncher;
    /** Display a Book. */
    private ActivityResultLauncher<ShowBookPagerContract.Input> displayBookLauncher;
    /** Add a Book by doing a search on the internet. */
    private ActivityResultLauncher<AddBookBySearchContract.Input> addBookBySearchLauncher;
    /** Edit a Book. */
    private ActivityResultLauncher<EditBookContract.Input> editBookLauncher;
    /** Update an individual Book with information from the internet. */
    private ActivityResultLauncher<Book> updateBookLauncher;
    /** Update a list of Books with information from the internet. */
    private ActivityResultLauncher<UpdateBooklistContract.Input> updateBookListLauncher;
    /** View all works of an Author. */
    private ActivityResultLauncher<AuthorWorksContract.Input> authorWorksLauncher;
    /** The local FTS based search. */
    private ActivityResultLauncher<SearchFtsContract.Input> ftsSearchLauncher;
    /** Bring up the synchronisation options. */
    @Nullable
    private ActivityResultLauncher<Void> stripInfoSyncLauncher;
    /** Bring up the synchronisation options. */
    @Nullable
    private ActivityResultLauncher<Void> calibreSyncLauncher;

    /** Row menu launcher displaying the menu as a BottomSheet. */
    private ExtMenuLauncher menuLauncher;
    private StylePickerLauncher stylePickerLauncher;
    private BookshelfFiltersLauncher bookshelfFiltersLauncher;
    /** Row menu launcher to add/move a set of Books to the selected Bookshelves. */
    private MultiChoiceLauncher<Bookshelf> bulkSetBookshelvesLauncher;
    /** Row menu launcher to set the location of a set of Books. */
    private AutoCompletePickerLauncher bulkSetLocationLauncher;

    private OnBackPressedCallback backClearsSearchCriteria;
    private OnBackPressedCallback backClosesFabMenu;

    private boolean isSyncMenuExpanded;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        // fitsSystemWindows is not used:
        // If we have it on the CoordinatorLayout
        // we end up with the status bar being transparent as expected,
        // but the background of it set to the same as the vb.content.list,
        // which then of course does NOT match the toolbar.
        //
        // The solution applied here:
        // - The CoordinatorLayout will NOT adjust for the status bar, but only
        //   for cutouts (and ime, N/A for this screen but no harm done)
        // - adjust toolbar/fab as needed
        // - Set insets on the list so we get the padding only at the end of the list.
        //
        // The status bar will still be transparent, but the background will be the same
        // as the toolbar.
        InsetsListenerBuilder.apply(vb.coordinatorContainer, vb.toolbar, vb.fab);
        // REMINDER: the FastScroller sets an Insets listener on the RecyclerView!

        if (useFixedHeaderAndFooter()) {
            new InsetsListenerBuilder(vb.contentFrame)
                    .systemBars()
                    .margins(Side.Bottom)
                    .apply();
        }

        createActivityLaunchers();
        createFragmentLaunchers();
        createViewModel();

        createSyncDelegates();
        createCalibreServerHandler();

        initToolbar();

        createBookshelfSpinner();
        createFabMenu();

        createLayoutManager();

        // Set up the list related stuff; the actual list data is generated in onResume
        createBooklistView();

        // Remove the potentially embedded fragment and its children.
        // Otherwise, even while not showing, it will be put in 'resumed' state by the system
        removeEmbeddedDetailsFragment();

        // Create the various OnBackHandlers and setup their listener/observers
        createOnBackHandlers();

        createSearchViewHelper();

        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        if (savedInstanceState == null) {
            TipManager.getInstance().show(this, Tip.BOOK_LIST, () -> {
                if (vm.isProposeBackup()) {
                    new MaterialAlertDialogBuilder(this)
                            .setIcon(R.drawable.warning_24px)
                            .setTitle(R.string.app_name)
                            .setMessage(R.string.warning_backup_request)
                            .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                            .setPositiveButton(R.string.ok, (d, w) ->
                                    exportLauncher.launch(null))
                            .create()
                            .show();
                }
            });
        }
    }

    /**
     * Create the OnBackPressedDispatcher.
     *
     * @see <a href="https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#best-practices">
     *         predictive-back-gesture</a>
     */
    private void createOnBackHandlers() {
        final OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();

        backClosesFabMenu = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Paranoia... the FabMenu onOpenListener should/will disable us
                backClosesFabMenu.setEnabled(false);
                fabMenu.hideMenu();
            }
        };
        dispatcher.addCallback(this, backClosesFabMenu);
        fabMenu.setOnOpenListener(backClosesFabMenu::setEnabled);

        backClearsSearchCriteria = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                vm.clearSearchCriteria();
                setNavIcon();
                buildBookList();
            }
        };
        dispatcher.addCallback(this, backClearsSearchCriteria);
        vm.getSearchCriteriaAreActive().observe(this, this::updateBackActionForSearchCriteria);
    }

    private void updateBackActionForSearchCriteria(final boolean enabled) {
        // Adjust the icon depending on whether we have search-criteria active or not.
        setNavIcon();

        // update the back-handler depending on the presence of search-criteria.
        backClearsSearchCriteria.setEnabled(enabled);
    }

    private void createActivityLaunchers() {
        editSettingsLauncher = registerForActivityResult(
                new SettingsContract(), o -> o.ifPresent(
                        this::onSettingsChanged));

        importLauncher = registerForActivityResult(
                new ImportContract(), o -> o.ifPresent(
                        this::onImportFinished));

        exportLauncher = registerForActivityResult(
                new ExportContract(), success -> { /* Nothing to do */ });

        editStylesLauncher = registerForActivityResult(
                new EditPreferredStylesContract(), o -> o.ifPresent(
                        data -> vm.onEditStylesFinished(this, data)));

        editStyleLauncher = registerForActivityResult(
                new EditStyleContract(), o -> o.ifPresent(
                        data -> vm.onEditStyleFinished(this, data)));

        // The return value is the selected bookshelf id
        // and will always be present but can be 0 for none.
        manageBookshelvesLauncher = registerForActivityResult(
                new EditBookshelvesContract(), o -> o.ifPresent(
                        id -> vm.onManageBookshelvesFinished(this, id)));

        // We still call {@link BooksOnBookshelfViewModel#onBookEditFinished(EditBookOutput)}
        // as the user might have done so from the displaying fragment.
        displayBookLauncher = registerForActivityResult(
                new ShowBookPagerContract(), o -> o.ifPresent(
                        data -> vm.onBookEditFinished(data)));

        addBookBySearchLauncher = registerForActivityResult(
                new AddBookBySearchContract(), o -> o.ifPresent(
                        data -> vm.onBookEditFinished(data)));

        editBookLauncher = registerForActivityResult(
                new EditBookContract(), o -> o.ifPresent(
                        data -> vm.onBookEditFinished(data)));

        updateBookLauncher = registerForActivityResult(
                new UpdateSingleBookContract(), o -> o.ifPresent(
                        data -> vm.onBookEditFinished(data)));

        updateBookListLauncher = registerForActivityResult(
                new UpdateBooklistContract(),
                o -> o.ifPresent(
                        data -> vm.onBookEditFinished(data)));

        authorWorksLauncher = registerForActivityResult(
                new AuthorWorksContract(), o -> o.ifPresent(
                        data -> vm.onBookEditFinished(data)));

        ftsSearchLauncher = registerForActivityResult(
                new SearchFtsContract(), o -> o.ifPresent(
                        criteria -> vm.onFtsSearch(this, criteria)));
    }

    private void createFragmentLaunchers() {
        final FragmentManager fm = getSupportFragmentManager();
        final LifecycleOwner lifecycleOwner = this;

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onSomeMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, lifecycleOwner);

        stylePickerLauncher = new StylePickerLauncher(this::onStyleSelected);
        stylePickerLauncher.registerForFragmentResult(fm, lifecycleOwner);

        bulkSetBookshelvesLauncher = new MultiChoiceLauncher<>(
                RK_SET_BOOKSHELVES, (previous, newSelection, extras)
                -> vm.setBookshelves(this, newSelection, extras));
        bulkSetBookshelvesLauncher.registerForFragmentResult(fm, lifecycleOwner);

        bulkSetLocationLauncher = new AutoCompletePickerLauncher(
                RK_SET_LOCATION, (previous, newSelection, extras)
                -> vm.setLocation(newSelection, extras));
        bulkSetLocationLauncher.registerForFragmentResult(fm, lifecycleOwner);

        bookshelfFiltersLauncher = new BookshelfFiltersLauncher(this::onFiltersUpdate);
        bookshelfFiltersLauncher.registerForFragmentResult(fm, lifecycleOwner);
    }

    private void createViewModel() {
        // Does not use the full progress dialog. Instead, uses the overlay progress bar.
        vm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        vm.init(this, getIntent().getExtras());

        vm.onCancelled().observe(this, message -> {
            vb.progressCircle.hide();
            message.process(ignored -> {
                if (vm.isListAvailable()) {
                    // we can auto-recover.
                    displayList(null);
                } else {
                    // report in more detail
                    recoverAfterFailedBuild(null);
                }
            });
        });
        vm.onFailure().observe(this, message -> {
            vb.progressCircle.hide();
            message.process(e -> {
                if (vm.isListAvailable()) {
                    // we can auto-recover, log it and redisplay
                    LoggerFactory.getLogger().e(TAG, e);
                    displayList(null);
                } else {
                    // report in more detail
                    recoverAfterFailedBuild(e);
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

                if (hasEmbeddedDetailsFrame()) {
                    // Check if the currently displayed book is affected by the updated postions
                    final ShowBookDetailsViewModel childVm = new ViewModelProvider(this)
                            .get(ShowBookDetailsViewModel.class);
                    final long currentlyDisplayedBookId = childVm.getBook().getId();

                    // If it was, then refresh the embedded fragment as well
                    //noinspection DataFlowIssue
                    positions.stream()
                             .map(p -> adapter.readDataAt(p))
                             .filter(Objects::nonNull)
                             .mapToLong(dataHolder -> dataHolder.getInt(DBKey.FK_BOOK))
                             .filter(id -> id == currentlyDisplayedBookId)
                             .findFirst()
                             .ifPresent(this::openEmbeddedBookDetails);
                }
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
     * Something is TERRIBLY wrong.
     * This is usually (BUT NOT ALWAYS) due to the developer making an oopsie
     * with the Styles. i.e. the style used to build is very likely corrupt.
     * Another reason can be during development when the database structure
     * was changed...
     * We have seen this ONCE with a real user on 2025-02-09.
     *
     * @param e exception
     *
     * @throws RuntimeException trigger ACRA
     */
    private void recoverAfterFailedBuild(@Nullable final Throwable e) {
        final Throwable report =
                e != null ? e : new IllegalStateException("recoverAfterFailedBuild");

        LoggerFactory.getLogger().e(TAG, report,
                                    "Bookshelf=" + vm.getBookshelf(),
                                    "Style=" + vm.getStyle(),
                                    "Filters=" + vm.getBookshelf().getFilters());

        // Reset the style to hopefully recover... restarting the app should work now.
        // This may fail if the user added Filters to the hard-default style
        // but only if those filters ALSO contain bugs.
        vm.onStyleChanged(this, BuiltinStyle.HARD_DEFAULT_UUID);

        // URGENT: CatastropheDialog needs more work
        //        CatastropheDialog.show(this, report,
        //                               null,
        //                               null);
        // Leave it to ACRA for now
        throw new RuntimeException(report);
    }

    /**
     * Create the optional synchronisation launchers and delegates.
     * <p>
     * Reminder: this method <strong>cannot be called from onResume</strong>.
     * registerForActivityResult can only be called from onCreate
     */
    private void createSyncDelegates() {

        if (SyncServer.CalibreCS.isEnabled()) {
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

        if (SyncServer.StripInfo.isEnabled()) {
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
     * Create the Calibre handler which deals with a Calibre enabled book.
     * i.e. books which exist in the optional Calibre Content Server.
     */
    private void createCalibreServerHandler() {
        if (SyncServer.CalibreCS.isEnabled()) {
            try {
                calibreHandler = new CalibreHandler(vb.getRoot(), this)
                        .setProgressFrame(findViewById(R.id.progress_frame))
                        .registerForActivityResult(this, this);
            } catch (@NonNull final CertificateException ignore) {
                // TipManager.getInstance().display(this, R.string.tip_calibre, null);
                // ignore
            }
        }
    }

    private void initToolbar() {
        applyScrollFlags(vb.toolbar);
        setNavIcon();
        vb.toolbar.setNavigationOnClickListener(this::onNavButton);

        toolbarMenuProvider = new ToolbarMenuProvider();
        vb.toolbar.addMenuProvider(toolbarMenuProvider, this);
    }

    private void createBookshelfSpinner() {
        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        bookshelfAdapter = new EntityArrayAdapter<>(this, vm.getBookshelfList());

        vb.bookshelfSpinner.setAdapter(bookshelfAdapter);

        new SpinnerInteractionListener(this::onBookshelfSelected)
                .attach(vb.bookshelfSpinner);
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
        fabMenu.getItem(R.id.fab4_search_external_id)
               .ifPresent(item -> item.setEnabled(IdentifiersEditorContract.isShowExternalIdTab()));
    }

    /**
     * Create or recreate the {@link RecyclerView.LayoutManager}.
     *
     * @throws IllegalArgumentException when there is a bug with the enums
     */
    private void createLayoutManager() {
        //TODO: show a 'tip' when running in grid-mode + embedded-frame
        // and explain that embedded mode forces list-mode
        final ScreenLayout layout = vm.getNewLayout(hasEmbeddedDetailsFrame());
        final RecyclerView.LayoutManager layoutManager;
        switch (layout) {
            case List: {
                layoutManager = new LinearLayoutManager(this);
                break;
            }
            case Grid: {
                layoutManager = createGridLayoutManager();
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(layout));
        }
        vb.content.list.setLayoutManager(layoutManager);
        // make sure the menu matches (the button is "ifRoom"); null check for paranoia
        if (toolbarMenuProvider != null) {
            toolbarMenuProvider.onPrepareMenu(vb.toolbar.getMenu());
        }
    }

    @NonNull
    private GridLayoutManager createGridLayoutManager() {
        final int spanCount = vm.getStyle().getCoverScale().getGridSpanCount(this);
        final GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);

        layoutManager.setSpanSizeLookup(new GridSpanSizeLookup(spanCount));
        return layoutManager;
    }

    private void createBooklistView() {
        headerAdapter = new HeaderAdapter(() -> vm.getHeaderContent(this));

        positioningHelper = new PositioningHelper(vb.content.list, headerAdapter.getItemCount());

        rowGroupMenuHelper = new RowGroupMenuHelper();
        rowGroupMenuHelper.registerForFragmentResult(getSupportFragmentManager(), this);

        // hide the view at creation time. onResume will provide the data and make it visible.
        vb.content.list.setVisibility(View.GONE);

        // Custom fastscroller which actually works (as opposed to the built-in android one).
        // Provides an optional overlay.
        if (vb.content.list.getLayoutManager() instanceof LinearLayoutManager) {
            final FastScroller fastScroller =
                    FastScrollerMode.create(this).attach(vb.content.list);
            fastScroller.setOnFastScrollStateChangeListener(new OnFastScrollStateChangeListener() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFastScrollStarted() {
                    if (adapter != null) {
                        adapter.setDragging(true);
                        adapter.notifyDataSetChanged();
                    }
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFastScrollEnded() {
                    if (adapter != null) {
                        adapter.setDragging(false);
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }
        // attach the FAB scroll-listener which will hide the FAB while scrolling
        fabMenu.attach(vb.content.list);

        vb.content.list.setItemViewCacheSize(Tuning.getOffscreenCacheSize());
        vb.content.list.setHasFixedSize(true);

        // 2025-05-23: experiment for GitHub #147; rapid scrolling.
        // The defaults are '5' for each viewType. We may want to add other groups later.
        //   final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
        //   pool.setMaxRecycledViews(BooklistGroup.BOOK, 20);
        //   pool.setMaxRecycledViews(BooklistGroup.AUTHOR, 20);
        //   vb.content.list.setRecycledViewPool(pool);
    }

    private void createSearchViewHelper() {
        // Reminder: do not apply insets to the searchView/resultsView.
        // The SearchView takes care of them already.
        searchViewHelper = new SearchViewHelper(
                vb.searchView, vb.searchResults,
                id -> displayBookLauncher.launch(new ShowBookPagerContract.Input(
                        id, vm.getBookshelf())),
                query -> {
                    vm.onFtsSearch(query);
                    buildBookList();
                });

        // Enable popup for the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
    }

    @Override
    public void startSearch(@Nullable final String initialQuery,
                            final boolean selectInitialQuery,
                            @Nullable final Bundle appSearchData,
                            final boolean globalSearch) {
        searchViewHelper.show(initialQuery);
    }

    private void setNavIcon() {
        if (isRootActivity()) {
            vb.toolbar.setNavigationIcon(R.drawable.menu_24px);
        } else {
            // If we have search criteria enabled (i.e. we're filtering the current list)
            // we will NOT be the root Activity.
            vb.toolbar.setNavigationIcon(R.drawable.arrow_back_24px);
        }
    }

    // We could override isTaskRoot() but that might be risky
    // as the call is also used by Android internals
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
        // {@link #getIntent} would still return the original Intent.
        // Update it to this new Intent.
        setIntent(intent);

        super.onNewIntent(intent);

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
     * Called after coming back from {@link #editSettingsLauncher},
     * if the user changed anything significant.
     *
     * @param result changes
     */
    private void onSettingsChanged(@NonNull final SettingsOutput result) {
        if (result.isRecreateActivity()) {
            ActivityRestarter.recreate();
        }

        if (result.isForceRebuildBooklist()) {
            vm.setForceRebuildInOnResume();
        }
    }

    /**
     * Called after coming back from {@link #importLauncher}.
     *
     * @param result from the import
     */
    private void onImportFinished(@NonNull final ImportResults result) {
        vm.onImportFinished(this, result);

        // URGENT: calling recreate() causes a crash if the current style is different enough.
        // The problem is that the recreate happens before the rebuild is fully done,
        // which means the recreate picks up the PREVIOUS booklist table,
        // but the CURRENT style. Needs further investigation.
        // Not recreating here is not crucial, as this is really only to apply
        // any UI setting changes coming from the imported preferences.
        // The downside:
        // - an imported style set as the new default is not applied
        //   => either restart or set manually
        // - the sync menu is not updated/visible.
        //   => restart needed
        if (result.preferences > 0) {
            //ActivityRestarter.recreate();
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

        updateBackActionForSearchCriteria(!vm.getSearchCriteria().isEmpty());

        // update the fab menu visibility depending on current user settings
        fabMenu.getItem(R.id.fab4_search_external_id)
               .ifPresent(item -> item.setEnabled(IdentifiersEditorContract.isShowExternalIdTab()));

        // Always update the list of bookshelves
        // This will be redundant if the user just came back from
        // managing the bookshelf list, but we cannot risk not doing it.
        vm.reloadBookshelfList(this);
        bookshelfAdapter.notifyDataSetChanged();
        // and select the current shelf.
        vb.bookshelfSpinner.setSelection(vm.getSelectedBookshelfSpinnerPosition(this));

        if (vm.isForceRebuildInOnResume() || !vm.isListAvailable()) {
            // Recreate the layout-manager ONLY if the style was changed
            // to use a different Layout. Otherwise DO NOT recreate it.
            if (vm.hasLayoutChanged(hasEmbeddedDetailsFrame())) {
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
     * {@link #onSomeMenuItemSelected(int, int)}
     * {@link #onNavigationItemSelected(int)}
     */
    private void saveListPosition() {
        if (!isDestroyed() && !vm.isBuilding()) {
            vm.saveBookshelfTopRowPosition(this, positioningHelper.getTopRowPosition());
        }
    }

    private void onNavButton(@NonNull final View anchor) {
        if (!isRootActivity()) {
            // Simulate the user pressing the 'back' key.
            getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        final Menu menu = MenuUtils.create(this, R.menu.bob_nav_view);
        // Show or hide the synchronisation menu.
        // Note this is only effective for the actual sync switches.
        // The launchers MUST have been created at Activity startup,
        // due to how "registerForActivityResult" works.
        final boolean enableSync =
                SyncServer.CalibreCS.isEnabled() && calibreSyncLauncher != null
                ||
                SyncServer.StripInfo.isEnabled() && stripInfoSyncLauncher != null;
        menu.findItem(R.id.SUBMENU_SYNC).setVisible(enableSync);
        if (enableSync) {
            menu.findItem(R.id.MENU_SYNC_CALIBRE)
                .setVisible(SyncServer.CalibreCS.isEnabled() && calibreSyncLauncher != null);

            menu.findItem(R.id.MENU_SYNC_STRIP_INFO)
                .setVisible(SyncServer.StripInfo.isEnabled() && stripInfoSyncLauncher != null);

        }

        menuLauncher.launch(anchor, null, null, R.menu.bob_nav_view, menu);
    }

    /**
     * Called by the embedded details frame to match up the list position with the displayed book.
     *
     * @param bookId to scroll the list to.
     */
    @Override
    public void onSyncBook(final long bookId) {
        displayList(vm.getVisibleBookNodes(bookId));
    }

    @Override
    public void onBookUpdated(@NonNull final Book book,
                              @Nullable final String... keys) {
        vm.onAfterUpdate(book, keys);
    }

    @Override
    public void onBookDeleted(final long bookId) {
        saveListPosition();
        vm.onBookDeleted(bookId);
    }

    /**
     * User clicked a row.
     *
     * @param v               View clicked
     * @param adapterPosition The {@link #adapter} position of the row clicked.
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

        if (v.getId() == R.id.author) {
            // User clicked an Author in a Book row, open the AuthorWorks
            authorWorksLauncher.launch(new AuthorWorksContract.Input(
                    rowData.getInt(DBKey.FK_AUTHOR),
                    vm.getBookshelf()));

        } else if (rowData.getInt(DBKey.BL_NODE.GROUP) == BooklistGroup.BOOK) {
            // User clicked a book, open the details page.
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
                        vm.getBookshelf(),
                        vm.getNavigationTablePosition(rowData.getLong(DBKey.PK_ID)),
                        vm.getNavigationTableName()
                ));
            }
        } else {
            // User clicked another BooklistGroup, expand/collapse
            final long nodeRowId = rowData.getLong(DBKey.BL_NODE.ROW_ID);
            vm.setNode(nodeRowId, BooklistNode.NextState.Toggle, 1);
            // don't pass the node, we want the list to scroll back to
            // the exact same (saved) position.
            displayList(null);
        }
    }

    /**
     * Create AND show a context menu based on row group.
     *
     * @param v               View clicked
     * @param adapterPosition The {@link #adapter} position of the row menu to show.
     */
    private void onCreateContextMenu(@NonNull final View v,
                                     final int adapterPosition) {
        final Context context = v.getContext();

        //noinspection DataFlowIssue
        final DataHolder rowData = adapter.readDataAt(adapterPosition);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return;
        }

        final Menu menu = MenuUtils.create(context);

        rowGroupMenuHelper.onCreateContextMenu(context, rowData, menu);

        int menuOrder = getResources().getInteger(R.integer.MENU_ORDER_NEXT_MISSING_COVER);

        // forms its own group
        menu.add(R.id.MENU_NEXT_MISSING_COVER, R.id.MENU_NEXT_MISSING_COVER, menuOrder,
                 R.string.option_goto_next_book_without_cover)
            .setIcon(R.drawable.broken_image_24px);

        // if it's a level, i.e. NOT a Book...
        if (rowData.getInt(DBKey.BL_NODE.GROUP) != BooklistGroup.BOOK) {
            // add the expand option
            menu.add(R.id.MENU_GROUP_BOB_EXPANSION, R.id.MENU_LEVEL_EXPAND, ++menuOrder,
                     R.string.option_level_expand)
                .setIcon(R.drawable.unfold_more_24px);
        }

        // If we have a menu, show it.
        if (menu.size() > 0) {
            // Set the title according to the level.
            final CharSequence menuTitle = adapter
                    .getLevelText(rowData.getInt(DBKey.BL_NODE.LEVEL), adapterPosition);

            menuLauncher.launch(v, menuTitle, null, adapterPosition, menu);
        }
    }

    /**
     * Handle the Navigation menu.
     *
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if the menuItem was handled.
     */
    private boolean onNavigationItemSelected(@IdRes final int menuItemId) {
        saveListPosition();

        if (menuItemId == R.id.MENU_ADVANCED_SEARCH) {
            ftsSearchLauncher.launch(new SearchFtsContract.Input(vm.getBookshelf(),
                                                                 vm.getSearchCriteria()));
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
            startActivity(GithubIntentFactory.help(this));
            return true;

        } else if (menuItemId == R.id.MENU_ABOUT) {
            startActivity(FragmentHostActivityLauncher.createIntent(this, AboutFragment.class));
            return true;
        }

        return false;
    }

    /**
     * Handle the row/context menus.
     * We're getting here for
     * - popup menu for a specific row
     * - bottom-sheet menu for a specific row
     * <p>
     * <strong>Dev. note:</strong> this used to be simply "onMenuItemSelected",
     * but due to an R8 bug confusing it with "onMenuItemSelected(int, android.view.MenuItem)"
     * ended throwing a "java.lang.LinkageError" ... so the name had to be changed.
     *
     * @param adapterPosition The {@link #adapter} position of the row menu from which
     *                        the user made a selection.
     * @param menuItemId      The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onSomeMenuItemSelected(final int adapterPosition,
                                           @IdRes final int menuItemId) {

        // check for nav menu FIRST
        if (onNavigationItemSelected(menuItemId)) {
            return true;
        }

        View view = positioningHelper.findViewByAdapterPosition(adapterPosition);
        // Paranoia check to protect from the adapterPosition having
        // scrolled off-screen.
        if (view == null) {
            // While we never should get a null here, tests have shown that
            // using the list view as a substitute works OK,
            // as the bottom-sheet does not need that view as an anchor anyhow.
            view = vb.content.list;
        }
        saveListPosition();

        //noinspection DataFlowIssue
        final DataHolder rowData = adapter.readDataAt(adapterPosition);
        // Paranoia: if the user can click it, then the row exists.
        if (rowData == null) {
            return false;
        }

        // Check for row-group independent options first.

        if (menuItemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.BL_NODE.ROW_ID);
            searchMissingCover(nodeRowId);
            return true;

        } else if (menuItemId == R.id.MENU_LEVEL_EXPAND) {
            final long nodeRowId = rowData.getLong(DBKey.BL_NODE.ROW_ID);
            vm.setNode(nodeRowId, BooklistNode.NextState.Expand,
                       vm.getStyle().getGroupCount());
            // don't pass the node, we want the list to scroll back to
            // the exact same (saved) position.
            displayList(null);
            return true;

        } else if (menuItemId == R.id.MENU_SET_BOOKSHELVES) {
            return onRowMenuGroupSetBookshelves(view, rowData);

        } else if (menuItemId == R.id.MENU_SET_LOCATION) {
            return onRowMenuGroupSetLocation(view, rowData);

        } else if (menuItemId == R.id.MENU_UPDATE_BOOKS_BY_SEARCH) {
            // This is the 1st step in the updateBooksFromInternet process.
            return onRowMenuGroupUpdateFromInternet(view, adapterPosition, rowData);

        } else if (menuItemId == R.id.MENU_UPDATE_BOOKS_BY_SEARCH_ALL_BOOKSHELVES
                   || menuItemId == R.id.MENU_UPDATE_BOOKS_BY_SEARCH_THIS_NODE_ONLY) {
            // We get here after the user choose one of these two options
            // from the dialog menu.
            return updateBooksFromInternetData(menuItemId, rowData);

        } else if (menuItemId == R.id.MENU_CALIBRE_SETTINGS) {
            // From the CalibreHandler.
            final Intent intent = FragmentHostActivityLauncher
                    .createIntent(this, CalibrePreferencesFragment.class);
            startActivity(intent);
            return true;
        }

        final Context context = view.getContext();

        // Finally check for specific row-group options
        return rowGroupMenuHelper.onMenuItemSelected(context, menuItemId, rowData, adapterPosition);
    }

    /**
     * Handle {@link R.id#MENU_SET_BOOKSHELVES}.
     *
     * @param v       View clicked; the anchor for a potential popup menu
     * @param rowData the row data
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean onRowMenuGroupSetBookshelves(@NonNull final View v,
                                                 @NonNull final DataHolder rowData) {

        final String nodeKey = rowData.getString(DBKey.BL_NODE.KEY);
        final int level = rowData.getInt(DBKey.BL_NODE.LEVEL);

        //noinspection DataFlowIssue
        final List<Long> bookIds = adapter.getBookIds(nodeKey, level);
        if (bookIds.isEmpty()) {
            // We should never get here... flw
            // Theoretically this can happen as we set the menu visibility
            // depending on books being under the node at the adapter position (or not).
            Snackbar.make(v, getString(R.string.warning_no_matching_book_found),
                          Snackbar.LENGTH_LONG).show();
            return true;
        }

        final String dialogTitle = vm.getRowLabel(this, rowData);
        final String dialogMessage = getString(R.string.info_bulk_set_bookshelves);

        // We simply grab the FIRST book to get the pre-selected bookshelves.
        final List<Bookshelf> bookshelves = Book.from(bookIds.get(0)).getBookshelves();

        // We're using the extras to pass the set of book ids
        final Bundle extras = new Bundle(1);
        extras.putParcelable(BooksOnBookshelfViewModel.BKEY_BOOK_IDS, ParcelUtils.wrap(bookIds));

        bulkSetBookshelvesLauncher.launch(this, dialogTitle, dialogMessage,
                                          vm.getBookshelvesList(), bookshelves,
                                          extras);
        return true;
    }

    /**
     * Handle {@link R.id#MENU_SET_LOCATION}.
     *
     * @param v       View clicked; the anchor for a potential popup menu
     * @param rowData the row data
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean onRowMenuGroupSetLocation(@NonNull final View v,
                                              @NonNull final DataHolder rowData) {
        final String nodeKey = rowData.getString(DBKey.BL_NODE.KEY);
        final int level = rowData.getInt(DBKey.BL_NODE.LEVEL);

        //noinspection DataFlowIssue
        final List<Long> bookIds = adapter.getBookIds(nodeKey, level);
        if (bookIds.isEmpty()) {
            // We should never get here... flw
            // Theoretically this can happen as we do set the menu visibility
            // depending on books being under the node at the adapter position (or not).
            Snackbar.make(v, getString(R.string.warning_no_matching_book_found),
                          Snackbar.LENGTH_LONG).show();
            return true;
        }

        final String dialogTitle = vm.getRowLabel(this, rowData);
        final String dialogMessage = getString(R.string.info_bulk_set_location);

        // We simply grab the FIRST book to get the pre-selected location.
        final String currentLocation = Book.from(bookIds.get(0)).getString(DBKey.LOCATION);

        // We're using the extras to pass the set of book ids
        final Bundle extras = new Bundle(1);
        extras.putParcelable(BooksOnBookshelfViewModel.BKEY_BOOK_IDS, ParcelUtils.wrap(bookIds));

        bulkSetLocationLauncher.launch(this, dialogTitle, dialogMessage,
                                       vm.getLocationList(), currentLocation,
                                       extras);
        return true;
    }

    /**
     * Handle {@link R.id#MENU_UPDATE_BOOKS_BY_SEARCH}.
     *
     * @param v               View clicked; the anchor for a potential popup menu
     * @param adapterPosition The {@link #adapter} position of the row menu from which
     *                        the user made a selection.
     * @param rowData         the row data
     *
     * @return {@code true} if handled.
     */
    private boolean onRowMenuGroupUpdateFromInternet(@NonNull final View v,
                                                     final int adapterPosition,
                                                     @NonNull final DataHolder rowData) {
        @BooklistGroup.Id
        final int rowGroupId = rowData.getInt(DBKey.BL_NODE.GROUP);
        switch (rowGroupId) {
            case BooklistGroup.AUTHOR:
            case BooklistGroup.SERIES:
            case BooklistGroup.PUBLISHER: {
                // Show a menu to select "all bookshelves" or "This node only"
                final String dialogTitle = vm.getRowLabel(this, rowData);
                final CharSequence message = getString(R.string.menu_update_books);

                final Menu menu = MenuUtils.create(this, R.menu.update_books);
                menuLauncher.launch(v, dialogTitle, message, adapterPosition, menu);
                return true;
            }
            case BooklistGroup.BOOKSHELF: {
                // Hardcoded to "this shelf only"
                updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                        this, rowData, true));
                return true;
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
                // Hardcoded to "this shelf only"
                updateBookListLauncher.launch(vm.createDateRowUpdateBooklistContractInput(
                        this, rowData));
                return true;
            }
            default:
                return false;
        }
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
                    AddBookBySearchContract.By.ProductCode,
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

    /**
     * This is the 3rd step in the updateBooksFromInternet process.
     * We get here after the user has selected to update a set of books on "this bookshelf only"
     * or on all bookshelves.
     *
     * @param menuItemId {@link R.id#MENU_UPDATE_BOOKS_BY_SEARCH_THIS_NODE_ONLY}
     *                   or {@link R.id#MENU_UPDATE_BOOKS_BY_SEARCH_ALL_BOOKSHELVES}
     * @param rowData    for the row which was selected
     *
     * @return {@code true} if handled.
     *
     * @see #onSomeMenuItemSelected(int, int)
     */
    private boolean updateBooksFromInternetData(final int menuItemId,
                                                @NonNull final DataHolder rowData) {
        if (menuItemId == R.id.MENU_UPDATE_BOOKS_BY_SEARCH_THIS_NODE_ONLY) {
            updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                    this, rowData, true));
            return true;
        } else if (menuItemId == R.id.MENU_UPDATE_BOOKS_BY_SEARCH_ALL_BOOKSHELVES) {
            updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                    this, rowData, false));
            return true;
        }
        return false;
    }

    /**
     * Called from {@link StylePickerDialogFragment} when the user wants
     * to <strong>apply</strong> the selected style.
     *
     * @param uuid of the style to apply
     */
    private void onStyleSelected(@NonNull final String uuid) {
        // TEST 2025-03-12: we're now calling saveListPosition from the
        // ToolbarMenuProvider#onMenuItemSelected
        // so this one here can likely be removed
        // This **MAY** provide better accuracy when switching style...
        saveListPosition();

        vm.resetPreferredListRebuildMode();
        vm.onStyleChanged(this, uuid);

        // New style, so the layout might have changed
        createLayoutManager();
        buildBookList();
    }

    /**
     * Called from {@link StylePickerDialogFragment} when the user wants
     * to <strong>edit</strong> the selected style.
     *
     * @param style to edit
     */
    void editStyle(@NonNull final Style style) {
        editStyleLauncher.launch(EditStyleContract.edit(style, true));
    }

    /**
     * Called after the user closed the filters' dialog.
     *
     * @param modified {@code true} when the filters were updated
     */
    private void onFiltersUpdate(final boolean modified) {
        if (modified) {
            // After applying filters, we always start the list at the top.
            vm.setSelectedBook(0, RecyclerView.NO_POSITION);
            buildBookList();
        }
    }

    /**
     * The user selected a Bookshelf from the spinner.
     *
     * @param bookshelfId of the Bookshelf
     */
    private void onBookshelfSelected(final long bookshelfId) {
        if (bookshelfId == vm.getBookshelf().getId()) {
            // No change, do nothing
            return;
        }

        // Save for the soon-to-be previous bookshelf
        saveListPosition();

        vm.selectBookshelf(this, bookshelfId);
        // New style, so the layout might have changed
        createLayoutManager();
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

        // Paranoia
        if (vm.isBuilding()) {
            return;
        }

        vb.progressCircle.show();
        // Make invisible... theoretically this means the page should not re-layout
        vb.content.list.setVisibility(View.INVISIBLE);

        // prevent quick users on slow devices to switch while building
        vb.bookshelfSpinner.setEnabled(false);

        // Remove the potentially embedded fragment and its children.
        removeEmbeddedDetailsFragment();

        // force the adapter to stop displaying by disabling the list.
        // DO NOT REMOVE THE ADAPTER FROM THE VIEW;
        // i.e. do NOT call vb.content.list.setAdapter(null)... crashes assured when doing so.
        if (adapter != null) {
            adapter.setBooklist(null);
        }
        vm.buildBookList(this);
    }

    /**
     * Display the list based on the current cursor, and either scroll to the desired
     * target node(s) or, if none, to the last saved position.
     *
     * @param targetNodes (optional) to re-position to
     */
    private void displayList(@Nullable final List<BooklistNode> targetNodes) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            final Logger logger = LoggerFactory.getLogger();
            logger.d(TAG, "displayList", System.nanoTime(),
                     targetNodes != null ? targetNodes.toString() : "null",
                     new Throwable());
            final Booklist booklist = vm.getBooklist();
            logger.d(TAG, booklist == null ? "booklist=null" : booklist.toString());
        }

        adapter = vm.createBooklistAdapter(this, hasEmbeddedDetailsFrame());
        adapter.setOnRowClickListener(this::onRowClicked);
        adapter.setOnRowShowMenuListener(
                vm.getShowContextMenuMode(hasEmbeddedDetailsFrame()),
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
        // (re)allow the user to select a different Bookshelf (which will trigger a new list build)
        vb.bookshelfSpinner.setEnabled(true);

        if (adapter.getItemCount() > 0) {
            // Make SURE the RecyclerView has populated at least one child view
            // before we start scrolling to the target-nodes
            vb.content.list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(@NonNull final View v,
                                           final int left,
                                           final int top,
                                           final int right,
                                           final int bottom,
                                           final int oldLeft,
                                           final int oldTop,
                                           final int oldRight,
                                           final int oldBottom) {
                    if (vb.content.list.getChildCount() > 0) {
                        scrollToTarget(targetNodes);
                        // remove the now unneeded listener
                        vb.content.list.removeOnLayoutChangeListener(this);
                    }
                }
            });
        }
    }

    private void scrollToTarget(@Nullable final List<BooklistNode> targetNodes) {
        // FIXME: scrolling not always correct
        // no "targetNodes":
        // we'll scroll to the correct position BEFORE the covers have been loaded.
        // Subsequent loading of the covers will push the rows down, and the row
        // we originally scrolled to will now be out of view, "below" the screen.
        //
        // "targetNodes" present:
        // 1. Best node is already visible => no scrolling will be done.
        // 2. Best node is "before-the-first" => scrolling always correct.
        // 3. Best node is "after-the last" and NO COVERS are shown
        //    =>  scrolling always correct.
        // 4. Best node is "after-the last" and COVERS ARE shown
        //   => due to the covers being loaded asynchronously,
        //      the amount of visible rows will be higher than expected.
        //      The result being that the scroll amount will be LESS than needed,
        //      and the desired node will STILL be "below" the screen.

        final long delay = vm.calculateScrollDelay();

        final TopRowListPosition topRowPos = vm.getBookshelfTopRowPosition();
        // 2025-03-12: the newest approach...  scroll TWICE to the same position.
        // ... at least in the emulator this produces somewhat better results.
        // TEST/URGENT: add a temp scroll-listener in which we check if we reached
        //  the desired position, if not, scroll again, repeat till ok,
        //  then remove scroll-listener? (madness...)
        vb.content.list.post(() -> {
            // 1st scroll to previously stored position
            //noinspection DataFlowIssue
            positioningHelper.scrollTo(topRowPos.getAdapterPosition(),
                                       topRowPos.getViewOffset(),
                                       adapter.getItemCount());
            vb.content.list.postDelayed(() -> {
                // 2nd scroll to previously stored position
                positioningHelper.scrollTo(topRowPos.getAdapterPosition(),
                                           topRowPos.getViewOffset(),
                                           adapter.getItemCount());
                // wait for layout cycle after the above scroll action
                vb.content.list.post(() -> {
                    if (targetNodes == null || targetNodes.isEmpty()) {
                        // There are no target nodes,
                        // display the embedded book details if applicable
                        showBookDetailsAfterScrolling(vm.getSelectedBookId(),
                                                      topRowPos.getAdapterPosition());
                    } else {
                        // Use the target nodes to find the "best" node and scroll it into view.
                        vb.content.list.post(() -> {
                            // 1st scroll to the 'best' position
                            final BooklistNode node = positioningHelper.scrollTo(targetNodes);
                            vb.content.list.postDelayed(() -> {
                                // 2nd scroll to the 'best' position
                                positioningHelper.scrollTo(List.of(node));

                                // We don't need to wait for the next layout cycle,
                                // as the node will not change even if further scrolling is done
                                // Display the embedded book details if applicable
                                showBookDetailsAfterScrolling(node.getBookId(),
                                                              node.getAdapterPosition());
                            }, delay);
                        });
                    }
                });
            }, delay);
        });
    }

    /**
     * Display the given book in the embedded details fragment IF POSSIBLE.
     *
     * @param bookId          of the book to open
     * @param adapterPosition the {@link #adapter} position
     *
     * @see #scrollToTarget(List)
     */
    @SuppressLint("Range")
    private void showBookDetailsAfterScrolling(@IntRange(from = 0) final long bookId,
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
        // details/edit.. screens which allow access to the global Settings
        // can trigger an Activity recreation and we could get here
        // during such recreation... which would crash the fragment transaction.
        if (!(getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))) {
            return;
        }

        final FragmentManager fm = getSupportFragmentManager();

        // After a deep-sleep of the device, we can hit
        // java.lang.IllegalStateException: FragmentManager has been destroyed
        // Hence, check if the FragmentManager has already been destroyed
        // or if the Activity is finishing
        if (fm.isDestroyed() || isFinishing() || isDestroyed()) {
            return;
        }

        Fragment fragment = fm.findFragmentByTag(ShowBookDetailsFragment.TAG);
        if (fragment == null) {
            fragment = ShowBookDetailsFragment.create(bookId, vm.getBookshelf(), true);
            fm.beginTransaction()
              .setReorderingAllowed(true)
              .replace(R.id.details_frame, fragment, ShowBookDetailsFragment.TAG)
              // As a secondary guard against the "FragmentManager has been destroyed"
              // allow the transaction to be abandoned if the fm was destroyed between
              // our above check and this line...   Android... URG....
              .commitAllowingStateLoss();
        } else {
            // In embedded mode, the above ShowBookDetailsFragment will
            // have created its vm in the Activity scope
            final ShowBookDetailsViewModel childVm = new ViewModelProvider(this)
                    .get(ShowBookDetailsViewModel.class);
            childVm.displayBook(bookId);
        }
    }

    /**
     * This is a bit of a halfway/halfcooked solution to split off and encapsulate
     * the logic to handle a popup menu on a row in the list.
     * <p>
     * The booklist groups have been cleanly moved to {@link RowMenu} classes
     * which reduced the complexity quite a bit and makes their code easier to read.
     * <p>
     * The Date based rows menus are in fact NOT related to the groups
     * but offer a small set of group independent options.
     * Building those menus is handled here, but acting on the selection
     * is done on {@link #onSomeMenuItemSelected(int, int)}.
     */
    private final class RowGroupMenuHelper {
        private final Map<Integer, RowMenu> map = new HashMap<>();

        RowGroupMenuHelper() {
            map.put(BooklistGroup.BOOK, new RMBook(BooksOnBookshelf.this,
                                                   BooksOnBookshelf.this,
                                                   vm, editBookLauncher, updateBookLauncher,
                                                   calibreHandler));

            map.put(BooklistGroup.AUTHOR, new RMAuthor(vm, authorWorksLauncher));
            map.put(BooklistGroup.BOOKSHELF, new RMBookshelf(vm));
            map.put(BooklistGroup.COLOR, new RMColor(vm));
            map.put(BooklistGroup.FORMAT, new RMFormat(vm));
            map.put(BooklistGroup.LANGUAGE, new RMLanguage(vm));
            map.put(BooklistGroup.LOCATION, new RMLocation(vm));
            map.put(BooklistGroup.PUBLISHER, new RMPublisher(vm));
            map.put(BooklistGroup.SERIES, new RMSeries(vm));
            map.put(BooklistGroup.TAGS_GENRE, new RMTag(vm));
        }

        void registerForFragmentResult(@NonNull final FragmentManager fm,
                                       @NonNull final LifecycleOwner lifecycleOwner) {

            map.values().forEach(rm -> rm.registerForFragmentResult(fm, lifecycleOwner));
        }

        void onCreateContextMenu(@NonNull final Context context,
                                 @NonNull final DataHolder rowData,
                                 @NonNull final Menu menu) {
            @BooklistGroup.Id
            final int rowGroupId = rowData.getInt(DBKey.BL_NODE.GROUP);

            final RowMenu rowMenu = map.get(rowGroupId);
            if (rowMenu != null) {
                rowMenu.onCreateMenu(context, getMenuInflater(), menu, rowData);
                return;
            }

            switch (rowGroupId) {
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

                    menu.add(Menu.NONE, R.id.MENU_SET_BOOKSHELVES,
                             getResources().getInteger(R.integer.MENU_ORDER_SET_BOOKSHELVES),
                             R.string.lbl_assign_bookshelves)
                        .setIcon(R.drawable.library_books_24px);
                    menu.add(Menu.NONE, R.id.MENU_SET_LOCATION,
                             getResources().getInteger(R.integer.MENU_ORDER_SET_LOCATION),
                             R.string.lbl_assign_location)
                        .setIcon(R.drawable.edit_location_24px);

                    menu.add(Menu.NONE, R.id.MENU_UPDATE_BOOKS_BY_SEARCH,
                             getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                             R.string.menu_update_books)
                        .setIcon(R.drawable.cloud_download_24px);
                    break;
                }
                default: {
                    // For now, we do NOT provide the below options for unlisted groups.
                    // - MENU_SET_BOOKSHELVES
                    // - MENU_UPDATE_FIELDS_BY_SEARCH
                    break;
                }
            }
        }

        /**
         * Handle the row/context menus.
         *
         * @param context         Current context
         * @param menuItemId      The menu item that was invoked.
         * @param rowData         the row data
         * @param adapterPosition The {@link #adapter} position of the row menu from which
         *                        the user made a selection.
         *
         * @return {@code true} if handled.
         */
        boolean onMenuItemSelected(@NonNull final Context context,
                                   final int menuItemId,
                                   @NonNull final DataHolder rowData,
                                   final int adapterPosition) {

            @BooklistGroup.Id
            final int rowGroupId = rowData.getInt(DBKey.BL_NODE.GROUP);

            final RowMenu rowMenu = map.get(rowGroupId);
            if (rowMenu != null) {
                return rowMenu.onMenuItemSelected(context, menuItemId, rowData, adapterPosition);
            }
            return false;
        }
    }

    private final class GridSpanSizeLookup
            extends GridLayoutManager.SpanSizeLookup {
        private final int spanCount;

        private GridSpanSizeLookup(final int spanCount) {
            this.spanCount = spanCount;
        }

        @Override
        public int getSpanSize(final int position) {
            final int dataPosition = position - headerAdapter.getItemCount();
            if (dataPosition >= 0) {
                //noinspection DataFlowIssue
                if (adapter.readDataAt(dataPosition).getInt(DBKey.BL_NODE.GROUP)
                    == BooklistGroup.BOOK) {
                    // A book is always 1 cell.
                    return 1;
                }
            }
            // The header and all other BooklistGroup's use the full width.
            return spanCount;
        }
    }

    /**
     * Dev note: once again we must combat the Android inconsistencies....
     * When, as here, the menu offers an option with
     * {@code showAsAction="ifRoom"} or {@code showAsAction="always"}
     * then {@link #onPrepareMenu(Menu)} is useless... as it won't get called
     * unless the user clicks the options menu overflow 3dot button....
     * So we end up calling it manually from all locations which depend on it.
     */
    private final class ToolbarMenuProvider
            implements MenuProvider {

        // reference to use in #onMenuItemSelected
        private Menu menu;

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            this.menu = menu;
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.bob, menu);

            onPrepareMenu(menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final boolean showPreferredExpansion = vm.getStyle().getExpansionLevel() > 1;
            menu.findItem(R.id.MENU_LEVEL_PREFERRED_EXPANSION).setVisible(showPreferredExpansion);

            if (hasEmbeddedDetailsFrame()) {
                menu.findItem(R.id.MENU_STYLE_SHORTCUT_LAYOUT_LIST).setVisible(false);
                menu.findItem(R.id.MENU_STYLE_SHORTCUT_LAYOUT_GRID).setVisible(false);
            } else {
                final boolean isListLayout = vm.getStyle().getLayout() == ScreenLayout.List;
                menu.findItem(R.id.MENU_STYLE_SHORTCUT_LAYOUT_LIST).setVisible(!isListLayout);
                menu.findItem(R.id.MENU_STYLE_SHORTCUT_LAYOUT_GRID).setVisible(isListLayout);
            }
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            fabMenu.hideMenu();
            saveListPosition();

            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_SEARCH) {
                searchViewHelper.show(null);
                return true;

            } else if (menuItemId == R.id.MENU_FILTERS) {
                bookshelfFiltersLauncher.launch(BooksOnBookshelf.this, vm.getBookshelf());
                return true;

            } else if (menuItemId == R.id.MENU_STYLE_SHORTCUT_LAYOUT_LIST) {
                vm.setStyleLayout(BooksOnBookshelf.this, ScreenLayout.List);
                onPrepareMenu(menu);
                return true;

            } else if (menuItemId == R.id.MENU_STYLE_SHORTCUT_LAYOUT_GRID) {
                vm.setStyleLayout(BooksOnBookshelf.this, ScreenLayout.Grid);
                onPrepareMenu(menu);
                return true;

            } else if (menuItemId == R.id.MENU_STYLE_EDIT) {
                editStyle(vm.getStyle());
                return true;

            } else if (menuItemId == R.id.MENU_STYLE_PICKER) {
                stylePickerLauncher.launch(BooksOnBookshelf.this, vm.getStyle(), false);
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

            } else if (menuItemId == R.id.MENU_UPDATE_BOOKS_BY_SEARCH) {
                updateBookListLauncher.launch(vm.createUpdateBooklistContractInput(
                        BooksOnBookshelf.this));
                return true;
            }

            return false;
        }

        /**
         * Expand/Collapse the entire list <strong>starting</strong> from the given level.
         * <p>
         * This is called from the options' menu:
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
