/*
 * @Copyright 2019 HardBackNutter
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
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.BookRowInfo;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistMappedCursorRow;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistPseudoCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditPublisherDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.LendBookDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.simplestring.EditFormatDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.simplestring.EditGenreDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.simplestring.EditLanguageDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.simplestring.EditLocationDialog;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSuggestionProvider;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay;
import com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSRecyclerView;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 */
public class BooksOnBookshelf
        extends BaseActivity {

    private static final int FAB_ITEMS = 4;
    /**
     * Views for the current row level-text.
     * These are shown in the header of the list (just below the bookshelf spinner) while scrolling.
     */
    private final TextView[] mHeaderTextView = new TextView[2];
    /** Array with the submenu FAB buttons. Element 0 shows at the bottom. */
    private final ExtendedFloatingActionButton[] mFabMenuItems =
            new ExtendedFloatingActionButton[FAB_ITEMS];

    private TextView mFilterTextView;
    /** The View for the list. */
    private RecyclerView mListView;
    private LinearLayoutManager mLayoutManager;
    /** Multi-type adapter to manage list connection to cursor. */
    private BooklistAdapter mAdapter;
    /** simple indeterminate progress spinner to show while getting the list of books. */
    private ProgressBar mProgressBar;
    /** The dropdown button to select a Bookshelf. */
    private Spinner mBookshelfSpinner;
    /** The adapter used to fill the mBookshelfSpinner. */
    private ArrayAdapter<String> mBookshelfSpinnerAdapter;
    /** The number of books in the current list. */
    private TextView mBookCountView;
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
                    String selected = (String) parent.getItemAtPosition(position);
                    String previous = mModel.getCurrentBookshelf().getName();

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debug(this, "mOnBookshelfSelectionChanged",
                                     "previous=" + previous, "selected=" + selected);
                    }

                    if (selected != null && !selected.equalsIgnoreCase(previous)) {
                        // make the new shelf the current
                        mModel.setCurrentBookshelf(BooksOnBookshelf.this, selected);
                        // new shelf selected, so we need a new list.
                        initBookList(true);
                    }
                }

                @Override
                public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                    // Do Nothing
                }
            };
    /**
     * Sure, this could al be condensed to initBookList,
     * but the intention is to make the rebuild fine grained.
     */
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {

        // changes were made to a single book
        if (bookId > 0) {
            //ENHANCE: update the modified row without a rebuild
            if ((fieldsChanged & BookChangedListener.BOOK_READ) != 0) {
                savePosition();
                initBookList(true);

//            } else if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
//                we don't display the lend-status in the list for now.
//                if (data != null) {
//                    data.getString(DBDefinitions.KEY_LOANEE);
//                    ...
//                }

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
    };
    /**
     * Apply the style that a user has selected.
     */
    private final StylePickerDialogFragment.StyleChangedListener mStyleChangedListener =
            new StylePickerDialogFragment.StyleChangedListener() {
                public void onStyleChanged(@NonNull final BooklistStyle style) {
                    // store the new data
                    mModel.onStyleChanged(BooksOnBookshelf.this,
                                          style,
                                          mLayoutManager.findFirstVisibleItemPosition(),
                                          mListView);
                    // and do a rebuild
                    initBookList(true);
                }
            };
    /** Whether to show header texts - this depends on the current style. */
    private boolean mShowHeaderTexts;
    /** Define a scroller to update header detail when the top row changes. */
    private final RecyclerView.OnScrollListener mUpdateHeaderScrollListener =
            new RecyclerView.OnScrollListener() {
                public void onScrolled(@NonNull final RecyclerView recyclerView,
                                       final int dx,
                                       final int dy) {
                    int currentTopRow = mLayoutManager.findFirstVisibleItemPosition();
                    // Need to check isDestroyed() because BooklistPseudoCursor misbehaves when
                    // activity terminates and closes cursor
                    if (mModel.getLastTopRow() != currentTopRow
                        && !isDestroyed()
                        && mShowHeaderTexts) {
                        setHeaderText(currentTopRow);
                    }
                }
            };
    /** The normal FAB button; opens or closes the FAB menu. */
    private FloatingActionButton mFabButton;
    /** Overlay enabled while the FAB menu is shown to intercept clicks and close the FAB menu. */
    private View mFabOverlay;

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
                    //URGENT: this is not called when the fast scroller stops scrolling
                    if (newState == RecyclerView.SCROLL_STATE_IDLE
                        || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        showFABMenu(false);
                        mFabButton.show();
                    }
                    super.onScrollStateChanged(recyclerView, newState);
                }
            };

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(BooksOnBookshelfModel.class);
        mModel.init(this, getIntent().getExtras(), savedInstanceState);

        mModel.getUserMessage().observe(this, this::showUserMessage);
        mModel.getNeedsGoodreads().observe(this, this::needsGoodreads);
        mModel.restoreCurrentBookshelf(this);

        // listen for the booklist being ready to display.
        mModel.getBuilderResult().observe(this, this::builderResultsAreReadyToDisplay);

        // set the search capability to local (application) search
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        // check & get search text coming from a system search intent
        handleStandardSearchIntent();

        mProgressBar = findViewById(R.id.progressBar);

        // Setup the list view.
        initListView();
        // create and hookup the list adapter; initially without a cursor (item list)
        initAdapter(null);
        // details for the header.
        initHeader();
        // Setup the FAB button and the linked menu.
        initFAB();

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_book_list, null);
        }

        Tracker.exitOnCreate(this);
    }

    /**
     * Called from {@link #onCreate}.
     */
    private void initListView() {
        mLayoutManager = new LinearLayoutManager(this);
        mListView = findViewById(android.R.id.list);
        mListView.setLayoutManager(mLayoutManager);
        mListView.addOnScrollListener(mUpdateHeaderScrollListener);
        mListView.addOnScrollListener(mUpdateFABVisibility);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, mLayoutManager.getOrientation()));

        // see class docs for FastScrollerOverlay
        if (!(mListView instanceof CFSRecyclerView)) {
            mListView.addItemDecoration(
                    new FastScrollerOverlay(this, R.drawable.fast_scroll_overlay));
        }
    }

    /**
     * Called from {@link #onCreate}.
     */
    private void initHeader() {
        mFilterTextView = findViewById(R.id.search_text);
        mBookCountView = findViewById(R.id.book_count);
        mHeaderTextView[0] = findViewById(R.id.level_1_text);
        mHeaderTextView[1] = findViewById(R.id.level_2_text);

        // Setup the bookshelf spinner and adapter.
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);
        // note that the list of names is empty right now, we'l populate it in onResume
        mBookshelfSpinnerAdapter = new ArrayAdapter<>(this,
                                                      R.layout.booksonbookshelf_bookshelf_spinner,
                                                      mModel.getBookshelfNameList());
        mBookshelfSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);
    }

    /**
     * Called from {@link #onCreate}.
     */
    private void initFAB() {
        mFabButton = findViewById(R.id.fab);
        mFabButton.setOnClickListener(v -> showFABMenu(!mFabMenuItems[0].isShown()));
        mFabOverlay = findViewById(R.id.fabOverlay);
        // modify FAB_ITEMS if adding more options.
        mFabMenuItems[0] = findViewById(R.id.fab1);
        mFabMenuItems[0].setOnClickListener(v -> startAddByScan());
        mFabMenuItems[1] = findViewById(R.id.fab2);
        mFabMenuItems[1].setOnClickListener(v -> startAddBySearch(BookSearchByIsbnFragment.TAG));
        mFabMenuItems[2] = findViewById(R.id.fab3);
        mFabMenuItems[2].setOnClickListener(v -> startAddBySearch(BookSearchByTextFragment.TAG));
        mFabMenuItems[3] = findViewById(R.id.fab4);
        mFabMenuItems[3].setOnClickListener(v -> startAddManually());
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

        // negative -> move them upwards.
        float base = -getResources().getDimension(R.dimen.fab_menu_offset_base);
        float offset = -getResources().getDimension(R.dimen.fab_menu_offset);

        for (int i = 0; i < mFabMenuItems.length; i++) {
            ExtendedFloatingActionButton fab = mFabMenuItems[i];
            if (show) {
                fab.show();
                fab.animate().translationY(base + ((i + 1) * offset));
            } else {
                fab.animate().translationY(0);
                fab.hide();
            }
        }
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (StylePickerDialogFragment.TAG.equals(fragment.getTag())) {
            ((StylePickerDialogFragment) fragment).setListener(mStyleChangedListener);

        } else if (EditAuthorDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditAuthorDialogFragment) fragment).setListener(mBookChangedListener);

        } else if (EditPublisherDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditPublisherDialogFragment) fragment).setListener(mBookChangedListener);

        } else if (EditSeriesDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditSeriesDialogFragment) fragment).setListener(mBookChangedListener);

        } else if (LendBookDialogFragment.TAG.equals(fragment.getTag())) {
            ((LendBookDialogFragment) fragment).setListener(mBookChangedListener);
        }
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        // Using a FAB now. Need to decide making a user option to use FAB or options menu.
//        SubMenu addBookSubMenu = menu.addSubMenu(R.id.SUBMENU_BOOK_ADD, R.id.SUBMENU_BOOK_ADD,
//                                                 0, R.string.menu_add_book)
//                                     .setIcon(R.drawable.ic_add);
//        addBookSubMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//
//        addBookSubMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SCAN, 0,
//                           R.string.menu_add_book_by_barcode_scan)
//                      .setIcon(R.drawable.ic_add_a_photo);
//        addBookSubMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN, 0,
//                           R.string.menu_add_book_by_isbn)
//                      .setIcon(R.drawable.ic_zoom_in);
//        addBookSubMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT, 0,
//                           R.string.menu_add_book_by_internet_search)
//                      .setIcon(R.drawable.ic_zoom_in);
//        addBookSubMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_MANUALLY, 0,
//                           R.string.menu_add_book_manually)
//                      .setIcon(R.drawable.ic_keyboard);

        menu.add(Menu.NONE, R.id.MENU_SORT, 0, R.string.menu_sort_and_style_ellipsis)
            .setIcon(R.drawable.ic_sort_by_alpha)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_EXPAND, 0, R.string.menu_expand_all)
            .setIcon(R.drawable.ic_unfold_more);

        menu.add(Menu.NONE, R.id.MENU_COLLAPSE, 0, R.string.menu_collapse_all)
            .setIcon(R.drawable.ic_unfold_less);

        // Disabled for now. It's a bit to easy for the user to select this from here,
        // This will use the currently displayed book list (the book ID's)
//        menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET, 0, R.string.lbl_update_fields)
//            .setIcon(R.drawable.ic_cloud_download);

        menu.add(Menu.NONE, R.id.MENU_CLEAR_FILTERS, 0, R.string.menu_clear_search_filters)
            .setIcon(R.drawable.ic_undo);

        if (BuildConfig.DEBUG /* always */) {
            SubMenu debugSubMenu = menu.addSubMenu(R.id.SUBMENU_DEBUG, R.id.SUBMENU_DEBUG,
                                                   0, R.string.debug);

            debugSubMenu.add(Menu.NONE, R.id.MENU_DEBUG_PREFS, 0, R.string.lbl_settings);
            debugSubMenu.add(Menu.NONE, R.id.MENU_DEBUG_STYLE, 0, R.string.lbl_style);
            debugSubMenu.add(Menu.NONE, R.id.MENU_DEBUG_TRACKER, 0, R.string.debug_history);
            debugSubMenu.add(Menu.NONE, R.id.MENU_DEBUG_TABLES, 0, R.string.debug_bob_tables);

            debugSubMenu.add(Menu.NONE, R.id.MENU_DEBUG_UNMANGLE, 0, "unmangle");

        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        menu.findItem(R.id.MENU_CLEAR_FILTERS).setEnabled(!mModel.getSearchCriteria().isEmpty());

        hideFABMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Hide the FAB menu if it's showing. Does not affect the FAB button itself.
     */
    private void hideFABMenu() {
        if (mFabMenuItems[0].isShown()) {
            showFABMenu(false);
        }
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        hideFABMenu();

        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                TipManager.display(this, R.string.tip_booklist_style_menu, this::showStylePicker);
                return true;

            case R.id.MENU_EXPAND:
                expandOrCollapseAllNodes(true);
                return true;

            case R.id.MENU_COLLAPSE:
                expandOrCollapseAllNodes(false);
                return true;

            case R.id.MENU_CLEAR_FILTERS: {
                mModel.getSearchCriteria().clear();
                initBookList(true);
                return true;
            }
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from an options menu selection.
                // We pass the book ID's for the currently displayed list.
                ArrayList<Long> bookIds = mModel.getCurrentBookIdList();
                Intent intent = new Intent(this, UpdateFieldsFromInternetActivity.class)
                        .putExtra(UniqueId.BKEY_ID_LIST, bookIds);
                startActivityForResult(intent, UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            case R.id.MENU_BOOK_ADD_BY_SCAN: {
                startAddByScan();
                return true;
            }
            case R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN: {
                startAddBySearch(BookSearchByIsbnFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT: {
                startAddBySearch(BookSearchByTextFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_ADD_MANUALLY: {
                startAddManually();
                return true;
            }

            default: {
                if (BuildConfig.DEBUG  /* always */) {
                    switch (item.getItemId()) {
                        case R.id.MENU_DEBUG_PREFS:
                            Prefs.dumpPreferences(this, null);
                            return true;

                        case R.id.MENU_DEBUG_STYLE:
                            Logger.debug(this, "onOptionsItemSelected",
                                         mModel.getCurrentStyle());
                            return true;

                        case R.id.MENU_DEBUG_TRACKER:
                            Logger.debug(this, "onOptionsItemSelected",
                                         Tracker.getEventsInfo());
                            return true;

                        case R.id.MENU_DEBUG_TABLES:
                            Logger.debug(this, "onOptionsItemSelected",
                                         mModel.debugBuilderTables());
                            return true;

                        case R.id.MENU_DEBUG_UNMANGLE:
                            mModel.getDb().tempUnMangle();
                            return true;

                        default:
                            break;
                    }
                }

                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void startAddByScan() {
        Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG)
                .putExtra(BookSearchByIsbnFragment.BKEY_IS_SCAN_MODE, true);
        startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
    }

    private void startAddBySearch(@NonNull final String tag) {
        Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, tag);
        startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
    }

    private void startAddManually() {
        Intent intent = new Intent(this, EditBookActivity.class);
        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
    }

    /** Syntax sugar. */
    private void showStylePicker() {
        StylePickerDialogFragment.newInstance(getSupportFragmentManager(),
                                              mModel.getCurrentStyle(), false);
    }

    /**
     * Expand/Collapse the current position in the list.
     *
     * @param expand {@code true} to expand, {@code false} to collapse
     */
    private void expandOrCollapseAllNodes(final boolean expand) {

        int layoutPosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        // It is possible that the list will be empty, if so, ignore
        if (layoutPosition != RecyclerView.NO_POSITION) {
            BooklistAdapter.RowViewHolder holder =
                    (BooklistAdapter.RowViewHolder)
                            mListView.findViewHolderForLayoutPosition(layoutPosition);

            @SuppressWarnings("ConstantConditions")
            int oldAbsPos = holder.absolutePosition;

            savePosition();

            // get the builder from the current cursor.
            BooklistBuilder booklistBuilder = mModel.getBuilder();
            // do the work, and re-position.
            //noinspection ConstantConditions
            booklistBuilder.expandAll(expand);
            savePosition(booklistBuilder.getPosition(oldAbsPos));

            // pass in a new cursor and display the list.
            // the old cursor will get closed afterwards.
            displayList(booklistBuilder.getNewListCursor(), null);
        }
    }


    /**
     * Reminder: don't do any commits on the fragment manager.
     * This includes showing fragments, or starting tasks that show fragments.
     * Do this in {@link #onResume} which will be called after onActivityResult.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        mModel.setCurrentPositionedBookId(0);

        switch (requestCode) {
            case UniqueId.REQ_BOOK_VIEW:
            case UniqueId.REQ_BOOK_EDIT: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);

                    if (data.getBooleanExtra(UniqueId.BKEY_DELETED_SOMETHING, false)) {
                        // one or more books were deleted.
                        // TODO: handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the id 'above' the deleted one];
                        mModel.setFullRebuild(false);

                    } else {
                        // a book was changed/added.
                        long newId = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (newId != 0) {
                            mModel.setCurrentPositionedBookId(newId);
                        }
                        mModel.setFullRebuild(false);
                    }
                }
                break;
            }
            case UniqueId.REQ_AUTHOR_WORKS: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);

                    if (data.getBooleanExtra(UniqueId.BKEY_DELETED_SOMETHING, false)) {
                        // one or more books were deleted.
                        // TODO: handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the id 'above' the deleted one];
                        mModel.setFullRebuild(false);
                    }
                }
                break;
            }
            case UniqueId.REQ_BOOK_SEARCH: {
                if (resultCode == Activity.RESULT_OK) {
                    // don't enforce having an ID. We might not have found or added anything.
                    // but if we do, the data will be what EditBookActivity returns.
                    if (data != null) {
                        long newId = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (newId != 0) {
                            mModel.setCurrentPositionedBookId(newId);
                        }
                    }
                    // regardless, do a rebuild just in case
                    mModel.setFullRebuild(false);
                }
                break;
            }
            case UniqueId.REQ_ADVANCED_LOCAL_SEARCH: {
                // no changes made, but we might have data to act on.
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Bundle extras = data.getExtras();
                        if (extras != null) {
                            mModel.getSearchCriteria().from(extras, true);
//                            setFilterTextField();
                        }
                        mModel.setFullRebuild(true);
                    }
                }
                break;
            }
            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    // the last edited/inserted shelf
                    long bookshelfId = data.getLongExtra(DBDefinitions.KEY_PK_ID,
                                                         Bookshelf.DEFAULT_ID);
                    mModel.setCurrentBookshelf(bookshelfId);
                    mModel.setFullRebuild(true);
                }
                break;
            }
            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_ADMIN: {
                if (resultCode == Activity.RESULT_OK) {

                    if ((data != null) && data.hasExtra(UniqueId.BKEY_IMPORT_RESULT)) {
                        int options = data.getIntExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                       Options.NOTHING);
                        if (options != 0) {
                            if ((options & Options.BOOK_LIST_STYLES) != 0) {
                                // Force a refresh of the list of user styles.
                                BooklistStyle.Helper.reload();
                            }
                            if ((options & Options.PREFERENCES) != 0) {
                                // Refresh the preferred bookshelf. This also refreshes its style.
                                mModel.reloadCurrentBookshelf(this);
                            }

                            // styles, prefs, books, covers,... it all requires a rebuild.
                            mModel.setFullRebuild(true);
                        }
                    }
                    //else if ((data != null) && data.hasExtra(UniqueId.BKEY_EXPORT_RESULT)) {
                    // int options = data.getIntExtra(UniqueId.BKEY_EXPORT_RESULT, Options.NOTHING);
                    // nothing to do
                    //}

//                    if ((data != null) && data.hasExtra(UniqueId.ZZZZ)) {
//                        // AdminActivity has results of it's own,, but no action needed for them.
//                        // child-activities results:
//                        // SearchAdminActivity:
//                    }
                }
                break;
            }
            // from BaseActivity Nav Panel or from sort menu dialog
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);

                    if (data.getBooleanExtra(UniqueId.BKEY_DELETED_SOMETHING, false)) {
                        mModel.setFullRebuild(true);
                    }

                    if (data.getBooleanExtra(UniqueId.BKEY_PREFERRED_STYLES_MODIFIED, false)) {
                        mModel.setFullRebuild(true);
                    }

                    if (data.getBooleanExtra(UniqueId.BKEY_STYLE_MODIFIED, false)) {
                        BooklistStyle style = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                        if (style != null) {
                            // save the new bookshelf/style combination
                            mModel.getCurrentBookshelf().setAsPreferred(this);
                            mModel.setCurrentStyle(this, style);
                        }
                        mModel.setFullRebuild(true);
                    }
                }
                break;
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }

        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        // get out, nothing to do.
        if (isFinishing() || isDestroyed()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "onResume",
                                 "isFinishing=" + isFinishing(),
                                 "isDestroyed=" + isDestroyed());
            }
            return;
        }

        // don't build the list needlessly
        if (App.isRecreating()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit("onResume", "isRecreating",
                                 LanguageUtils.toDebugString(this));
            }
            return;
        }

        // clear the cursor; we'll prepare a new one and meanwhile the view/adapter should
        // obviously NOT try to display the old list.
        // Note we do not clear the cursor on the model here, so we have the option of re-using it.
        mAdapter.setCursor(null);

        // If we got here after onActivityResult, see if we need to force a rebuild.
        Boolean fullOrPartialRebuild = mModel.isForceFullRebuild();

        // Update the list of bookshelves + set the current bookshelf.
        boolean bookshelfChanged = populateBookShelfSpinner();

        if (bookshelfChanged) {
            //  bookshelf changed, we need a whole new list
            initBookList(true);

        } else if (fullOrPartialRebuild != null) {
            // onActivityResult told us to refresh or create the list
            initBookList(fullOrPartialRebuild);

        } else if (mModel.isListLoaded()) {
            // a list has been build previously and we should re-use it.

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "onResume", "reusing existing list");
            }
            //noinspection ConstantConditions
            displayList(mModel.getBuilder().getNewListCursor(), null);

        } else {
            // rebuild for other reason... this should not be reached anyhow.
            initBookList(true);
        }

        // always reset for next iteration.
        mModel.setFullRebuild(null);

        Tracker.exitOnResume(this);
    }

    /**
     * Populate the BookShelf list in the Spinner and set the current bookshelf/style.
     * <p>
     * <strong>Note:</strong> no longer triggers a rebuild, as it was getting messy who/when/where.
     * Caller takes care now.
     *
     * @return {@code true} if the selected shelf was changed (or set for the first time).
     */
    private boolean populateBookShelfSpinner() {

        @Nullable
        String previous = (String) mBookshelfSpinner.getSelectedItem();

        // disable the listener while we add the names.
        mBookshelfSpinner.setOnItemSelectedListener(null);
        // (re)load the list of names
        int currentPos = mModel.initBookshelfNameList(this);
        // and tell the adapter about it.
        mBookshelfSpinnerAdapter.notifyDataSetChanged();
        // Set the current bookshelf.
        mBookshelfSpinner.setSelection(currentPos);
        // (re-)enable the listener
        mBookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);

        String selected = mModel.getCurrentBookshelf().getName();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debug(this, "populateBookShelfSpinner",
                         "previous=" + previous, "selected=" + selected);
        }

        // Flag up if the selection was different.
        return previous == null || !previous.equalsIgnoreCase(selected);
    }

    /**
     * Save position when paused.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        hideFABMenu();
        if (mModel.getSearchCriteria().isEmpty()) {
            savePosition();
        }
        super.onPause();
    }

    /**
     * If the FAB is showing, hide it.
     * If the current list is has any search criteria enabled, clear them and rebuild the list.
     * <p>
     * Otherwise handle the back-key as normal.
     */
    @Override
    public void onBackPressed() {
        if (mFabMenuItems[0].isShown()) {
            showFABMenu(false);
            return;
        }

        if (!mModel.getSearchCriteria().isEmpty()) {
            mModel.getSearchCriteria().clear();
            initBookList(true);
            return;
        }

        super.onBackPressed();
    }

    /**
     * Queue a rebuild of the underlying cursor and data. This is a wrapper for calling
     * {@link BooksOnBookshelfModel#initBookList}
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    private void initBookList(final boolean isFullRebuild) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            // with stack trace, so we know who called us.
            Logger.debugWithStackTrace(this, "initBookList",
                                       "isFullRebuild=" + isFullRebuild);
        }

        LocaleUtils.insanityCheck(this);
        // go create
        mModel.initBookList(this, isFullRebuild);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Called when the booklist builder was done building the cursor.
     * <p>
     * Does some elementary checking, then hands of to {@link #displayList}
     * <p>
     * The incoming holder object is in fact NonNull. But let's keep the check here for sanity.
     *
     * @param holder the results to display.
     */
    private void builderResultsAreReadyToDisplay(
            @Nullable final BooksOnBookshelfModel.BuilderHolder holder) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugEnter(this, "builderResultsAreReadyToDisplay",
                              "holder=" + holder);
        }

        // *always* ...
        mProgressBar.setVisibility(View.GONE);

        if (holder != null) {
            // check if we have a valid cursor before using it.
            BooklistPseudoCursor resultListCursor = holder.getResultListCursor();
            if (resultListCursor != null && !resultListCursor.isClosed()) {
                displayList(resultListCursor, holder.getResultTargetRows());
            }
        } else {
            // no new list; restore the adapter to use the old list. See #onResume
            mAdapter.setCursor(mModel.getListCursor());
        }
    }

    /**
     * Display the passed cursor in the ListView.
     * <ul>Called from two places:
     * <li>After the builder is done.</li>
     * <li>When the user clicked expand/collapse.</li>
     * </ul>
     *
     * @param newListCursor New cursor to use
     * @param targetRows    if set, change the position to targetRow.
     */
    private void displayList(@NonNull final BooklistPseudoCursor newListCursor,
                             @Nullable final ArrayList<BookRowInfo> targetRows) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugEnter(this, "displayList",
                              "newListCursor=" + newListCursor);
        }

        // Update the activity title using the current style name.
        setTitle(mModel.getCurrentStyle().getLabel(this));

        mProgressBar.setVisibility(View.GONE);

        // Save the old list so we can close it later
        final BooklistPseudoCursor oldCursor = mModel.getListCursor();

        // and set the new list
        mModel.setListCursor(newListCursor);

        // create and hookup the list adapter.
        initAdapter(mModel.getListCursor());

        // Restore saved position
        //noinspection ConstantConditions
        final int count = mModel.getListCursor().getCount();
        if (mModel.getTopRow() >= count) {
            // the list is shorter then it used to be, just scroll to the end
            savePosition(count - 1);
            mLayoutManager.scrollToPosition(mModel.getTopRow());
        } else {
            mLayoutManager.scrollToPositionWithOffset(mModel.getTopRow(), mModel.getTopRowOffset());
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and we can tell
        // if it is already in the view.
        if (targetRows != null) {
            mListView.post(() -> fixPositionWhenDrawn(targetRows));
        }

        // setup the list header
        mShowHeaderTexts = setupListHeader(count > 0);

        // all set, we can close the old list
        if (oldCursor != null) {
            //noinspection ConstantConditions
            if (!mModel.getBuilder().equals(oldCursor.getBuilder())) {
                oldCursor.getBuilder().close();
            }
            oldCursor.close();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugExit(this, "displayList");
        }
    }

    /**
     * FIXME: we should not create the adapter twice.
     * Need a reliable way of creating it in {@link #onCreate} and loading in {@link #displayList}.
     */
    private void initAdapter(@Nullable final Cursor cursor) {

        //This turned out not to work, or at least not reliably.

        // make sure any old views with potentially incorrect layout are removed
//        mListView.getRecycledViewPool().clear();
//        // (re)set the adapter with the current style
//        mAdapter.setStyle(mModel.getCurrentStyle());
//        // set the list, this will trigger the adapter to refresh.
//        mAdapter.setCursor(mModel.getListCursor());

        mAdapter = new BooklistAdapter(this, mModel.getCurrentStyle(),
                                       mModel.getDb(), cursor);
        mAdapter.setOnItemClickListener(this::onItemClick);
        mAdapter.setOnItemLongClickListener(this::onItemLongClick);
        mListView.setAdapter(mAdapter);
    }

    /**
     * Set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #displayList}
     */
    private void fixPositionWhenDrawn(@NonNull final ArrayList<BookRowInfo> targetRows) {
        // Find the actual extend of the current view and get centre.
        final int first = mLayoutManager.findFirstVisibleItemPosition();
        final int last = mLayoutManager.findLastVisibleItemPosition();
        final int centre = (last + first) / 2;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
            Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                         " Best listPosition @" + best.listPosition);
        }
        // Try to put at top if not already visible, or only partially visible
        if (first >= best.listPosition || last <= best.listPosition) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
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
            // Book will be off bottom of screen without the smoothScroll in the second Runnable.
            //
            mLayoutManager.scrollToPositionWithOffset(best.listPosition, 0);
            // Code below does not behave as expected.
            // Results in items often being near bottom.
            //lv.setSelectionFromTop(best.listPosition, lv.getHeight() / 2);

            // Without this call some positioning may be off by one row (see above).
            final int newPos = best.listPosition;
            mListView.post(() -> mListView.smoothScrollToPosition(newPos));

            //int newTop = best.listPosition - (last-first)/2;
            // if (BuildConfig.DEBUG && BOB_FIX_POSITION) {
            //Logger.info(this, "fixPositionWhenDrawn", "New Top @" + newTop );
            //}
            //lv.setSelection(newTop);
        }
    }

    /**
     * Called when the user clicked on a row.
     * <ul>
     * <li>Book: open the details screen.</li>
     * <li>Not a book: expand/collapse the section as appropriate.</li>
     * </ul>
     */
    private void onItemClick(@NonNull final View view) {
        int position = (int) view.getTag(R.id.TAG_POSITION);

        final BooklistPseudoCursor cursor = mModel.getListCursor();
        //noinspection ConstantConditions
        cursor.moveToPosition(position);

        CursorMapper mapper = cursor.getCursorMapper();

        //noinspection SwitchStatementWithTooFewBranches
        switch (mapper.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND)) {
            // If it's a book, view or edit it.
            case BooklistGroup.RowKind.BOOK:
                final long bookId = mapper.getLong(DBDefinitions.KEY_FK_BOOK);
                if (mModel.isReadOnly(view.getContext())) {
                    final String listTableName = cursor.getBuilder().createFlattenedBooklist();
                    final Intent intent =
                            new Intent(this, BookDetailsActivity.class)
                                    .putExtra(DBDefinitions.KEY_PK_ID, bookId)
                                    .putExtra(BookFragment.BKEY_FLAT_BOOKLIST_TABLE, listTableName)
                                    .putExtra(BookFragment.BKEY_FLAT_BOOKLIST_POSITION, position);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_VIEW);

                } else {
                    final Intent intent = new Intent(this, EditBookActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, bookId);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                }
                break;

            // if's not a book, expand/collapse as needed
            default:
                // If it's a level, expand/collapse. Technically, we could expand/collapse any level
                // but storing and recovering the view becomes unmanageable.
                // ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/542
                // we don't prohibit other levels any longer, but we don't store/recover them.
//                if (row.getRowLevel() == 1) {
                cursor.getBuilder()
                      .toggleExpandNode(mapper.getInt(DBDefinitions.KEY_BL_ABSOLUTE_POSITION));
                cursor.requery();
                mAdapter.notifyDataSetChanged();
//                }
                break;
        }
    }

    /**
     * User long-clicked on a row. Bring up a context menu as appropriate.
     */
    private boolean onItemLongClick(@NonNull final View view) {
        final int position = (int) view.getTag(R.id.TAG_POSITION);

        final BooklistPseudoCursor cursor = mModel.getListCursor();
        //noinspection ConstantConditions
        cursor.moveToPosition(position);

        final BooklistMappedCursorRow row = cursor.getCursorRow();
        final CursorMapper mapper = row.getCursorMapper();

        final Menu menu = MenuPicker.createMenu(this);
        // build/check the menu for this row
        if (onCreateContextMenu(menu, mapper)) {
            // we have a menu to show
            String title;

            if (mapper.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND) == BooklistGroup.RowKind.BOOK) {
                title = mapper.getString(DBDefinitions.KEY_TITLE);
            } else {
                title = row.getLevelText(this, mapper.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
            }
            // bring up the context menu
            new MenuPicker<>(this, title, null, menu, position, (menuItem, pos) -> {
                //noinspection ConstantConditions
                cursor.moveToPosition(pos);
                return onContextItemSelected(menuItem, cursor.getCursorMapper());
            }).show();
        }
        return true;
    }

    /**
     * Adds 'standard' menu options based on row type.
     *
     * @param row Row view pointing to current row for this context menu
     *
     * @return {@code true} if there actually is a menu to show.
     * {@code false} if not OR if the only menus would be the 'search Amazon' set.
     */
    private boolean onCreateContextMenu(@NonNull final Menu /* in/out */ menu,
                                        @NonNull final CursorMapper row) {
        menu.clear();

        final int rowKind = row.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND);
        switch (rowKind) {
            case BooklistGroup.RowKind.BOOK: {
                if (row.getInt(DBDefinitions.KEY_READ) != 0) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read)
                        .setIcon(R.drawable.ic_check_box);
                }

                menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete)
                    .setIcon(R.drawable.ic_delete);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit)
                    .setIcon(R.drawable.ic_edit);

                if (App.isUsed(DBDefinitions.KEY_LOANEE)) {
                    if (mModel.isAvailable(row)) {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_ADD,
                                 MenuHandler.ORDER_LENDING, R.string.menu_loan_lend_book)
                            .setIcon(R.drawable.ic_people);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                                 MenuHandler.ORDER_LENDING, R.string.menu_loan_return_book)
                            .setIcon(R.drawable.ic_people);
                    }
                }

                menu.add(Menu.NONE, R.id.MENU_SHARE,
                         MenuHandler.ORDER_SHARE, R.string.menu_share_this)
                    .setIcon(R.drawable.ic_share);

                menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS,
                         MenuHandler.ORDER_SEND_TO_GOODREADS, R.string.gr_menu_send_to_goodreads)
                    .setIcon(R.drawable.ic_goodreads);

                menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                         MenuHandler.ORDER_UPDATE_FIELDS, R.string.menu_update_fields)
                    .setIcon(R.drawable.ic_cloud_download);

                boolean hasIsfdbId = 0 != row.getLong(DBDefinitions.KEY_ISFDB_ID);
                boolean hasGoodreadsId = 0 != row.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID);
                boolean hasLibraryThingId = 0 != row.getLong(DBDefinitions.KEY_LIBRARY_THING_ID);
                boolean hasOpenLibraryId = !row.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID)
                                               .isEmpty();

                if (hasIsfdbId || hasGoodreadsId || hasLibraryThingId || hasOpenLibraryId) {
                    SubMenu subMenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_VIEW_BOOK_AT_SITE,
                                                      MenuHandler.ORDER_VIEW_BOOK_AT_SITE,
                                                      R.string.menu_view_book_at)
                                          .setIcon(R.drawable.ic_link);
                    if (hasIsfdbId) {
                        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_ISFDB, 0,
                                    R.string.isfdb)
                               .setIcon(R.drawable.ic_link);
                    }
                    if (hasGoodreadsId) {
                        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_GOODREADS, 0,
                                    R.string.goodreads)
                               .setIcon(R.drawable.ic_link);
                    }
                    if (hasLibraryThingId) {
                        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING, 0,
                                    R.string.library_thing)
                               .setIcon(R.drawable.ic_link);
                    }
                    if (hasOpenLibraryId) {
                        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY, 0,
                                    R.string.open_library)
                               .setIcon(R.drawable.ic_link);
                    }
                }
                break;
            }
            case BooklistGroup.RowKind.AUTHOR: {
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_WORKS, 0, R.string.menu_author_details)
                    .setIcon(R.drawable.ic_details);
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT, 0, R.string.menu_edit)
                    .setIcon(R.drawable.ic_edit);
                if (row.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE)) {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_incomplete)
                        .setIcon(R.drawable.ic_check_box);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_complete)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                }
                menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                         MenuHandler.ORDER_UPDATE_FIELDS, R.string.menu_update_books)
                    .setIcon(R.drawable.ic_cloud_download);
                break;
            }
            case BooklistGroup.RowKind.SERIES: {
                if (row.getLong(DBDefinitions.KEY_FK_SERIES) != 0) {
                    menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE, 0, R.string.menu_delete)
                        .setIcon(R.drawable.ic_delete);
                    menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                    if (row.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE)) {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_incomplete)
                            .setIcon(R.drawable.ic_check_box);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_complete)
                            .setIcon(R.drawable.ic_check_box_outline_blank);
                    }
                    menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                             MenuHandler.ORDER_UPDATE_FIELDS, R.string.menu_update_books)
                        .setIcon(R.drawable.ic_cloud_download);
                }
                break;
            }
            case BooklistGroup.RowKind.PUBLISHER: {
                if (!row.getString(DBDefinitions.KEY_PUBLISHER).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.LANGUAGE: {
                if (!row.getString(DBDefinitions.KEY_LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.LOCATION: {
                if (!row.getString(DBDefinitions.KEY_LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.GENRE: {
                if (!row.getString(DBDefinitions.KEY_GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.FORMAT: {
                if (!row.getString(DBDefinitions.KEY_FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            default: {
                Logger.warnWithStackTrace(this, "rowKind=" + rowKind);
                break;
            }
        }

        // if there are no specific menus for the current row.
        if (menu.size() == 0) {
            return false;
        }

        // There is at least one other menu item.
        // Add Amazon menus if applicable.
        boolean hasAuthor = row.contains(DBDefinitions.KEY_FK_AUTHOR)
                            && row.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
        boolean hasSeries = row.contains(DBDefinitions.KEY_FK_SERIES)
                            && row.getLong(DBDefinitions.KEY_FK_SERIES) > 0;

        if (hasAuthor || hasSeries) {
            SubMenu subMenu = MenuHandler.addAmazonSearchSubMenu(menu);
            subMenu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
                   .setVisible(hasAuthor);
            subMenu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
                   .setVisible(hasAuthor && hasSeries);
            subMenu.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
                   .setVisible(hasSeries);
        }

        return true;
    }

    /**
     * @param menuItem that was selected
     * @param row      Row view for selected cursor row
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final CursorMapper row) {

        FragmentManager fm = getSupportFragmentManager();

        long bookId;

        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_DELETE: {
                String title = row.getString(DBDefinitions.KEY_TITLE);
                bookId = row.getLong(DBDefinitions.KEY_FK_BOOK);
                List<Author> authors = mModel.getDb().getAuthorsByBookId(bookId);
                StandardDialogs.deleteBookAlert(this, title, authors, () -> {
                    mModel.getDb().deleteBook(bookId);
                    mBookChangedListener
                            .onBookChanged(bookId, BookChangedListener.BOOK_WAS_DELETED, null);
                });
                return true;
            }
            case R.id.MENU_BOOK_READ: {
                // toggle the read status
                bookId = row.getLong(DBDefinitions.KEY_FK_BOOK);
                if (mModel.getDb().setBookRead(bookId, !row.getBoolean(DBDefinitions.KEY_READ))) {
                    mBookChangedListener
                            .onBookChanged(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;
            }
            case R.id.MENU_BOOK_EDIT: {
                bookId = row.getLong(DBDefinitions.KEY_FK_BOOK);
                Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookId);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }

            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from a context click on a row.
                // We pass the book ID's which are suited for that row.
                Intent intent = new Intent(this, UpdateFieldsFromInternetActivity.class);
                ArrayList<Long> bookIds;
                switch (row.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND)) {
                    case BooklistGroup.RowKind.BOOK: {
                        bookId = row.getLong(DBDefinitions.KEY_FK_BOOK);
                        bookIds = new ArrayList<>();
                        bookIds.add(bookId);
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE,
                                        row.getString(DBDefinitions.KEY_TITLE));
                        break;
                    }
                    case BooklistGroup.RowKind.AUTHOR: {
                        bookIds = mModel.getDb().getBookIdsByAuthor(
                                row.getLong(DBDefinitions.KEY_FK_AUTHOR));
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE,
                                        row.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                        break;
                    }
                    case BooklistGroup.RowKind.SERIES: {
                        bookIds = mModel.getDb().getBookIdsBySeries(
                                row.getLong(DBDefinitions.KEY_FK_SERIES));
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE,
                                        row.getString(DBDefinitions.KEY_SERIES_TITLE));
                        break;
                    }
                    case BooklistGroup.RowKind.PUBLISHER: {
                        String publisher = row.getString(DBDefinitions.KEY_PUBLISHER);
                        bookIds = mModel.getDb().getBookIdsByPublisher(publisher);
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE, publisher);
                        break;
                    }
                    default: {
                        Logger.warnWithStackTrace(this, "onContextItemSelected",
                                                  "MENU_BOOK_UPDATE_FROM_INTERNET not supported",
                                                  "RowKind="
                                                  + row.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND));
                        return true;
                    }
                }

                intent.putExtra(UniqueId.BKEY_ID_LIST, bookIds);
                startActivityForResult(intent, UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                if (fm.findFragmentByTag(LendBookDialogFragment.TAG) == null) {
                    LendBookDialogFragment.newInstance(row.getLong(DBDefinitions.KEY_FK_BOOK),
                                                       row.getLong(DBDefinitions.KEY_FK_AUTHOR),
                                                       row.getString(DBDefinitions.KEY_TITLE))
                                          .show(fm, LendBookDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mModel.getDb().deleteLoan(row.getLong(DBDefinitions.KEY_FK_BOOK));
                mBookChangedListener.onBookChanged(row.getLong(DBDefinitions.KEY_FK_BOOK),
                                                   BookChangedListener.BOOK_LOANEE, null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                Book book = new Book(row.getLong(DBDefinitions.KEY_FK_BOOK), mModel.getDb());
                startActivity(Intent.createChooser(book.getShareBookIntent(this),
                                                   getString(R.string.menu_share_this)));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                UserMessage.show(this, R.string.progress_msg_connecting);
                new SendOneBookTask(row.getLong(DBDefinitions.KEY_FK_BOOK),
                                    mModel.getGoodreadsTaskListener())
                        .execute();
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT: {
                if (fm.findFragmentByTag(EditSeriesDialogFragment.TAG) == null) {
                    long seriesId = row.getLong(DBDefinitions.KEY_FK_SERIES);
                    //noinspection ConstantConditions
                    EditSeriesDialogFragment.newInstance(mModel.getDb().getSeries(seriesId))
                                            .show(fm, EditSeriesDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_SERIES_COMPLETE: {
                long seriesId = row.getLong(DBDefinitions.KEY_FK_SERIES);
                // toggle the complete status
                boolean seriesComplete = !row.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                if (mModel.getDb().setSeriesComplete(seriesId, seriesComplete)) {
                    mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                Series series = mModel.getDb().getSeries(row.getLong(DBDefinitions.KEY_FK_SERIES));
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(this, series, () -> {
                        mModel.getDb().deleteSeries(series.getId());
                        mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
                    });
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_WORKS: {
                Intent intent = new Intent(this, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID,
                                  row.getLong(DBDefinitions.KEY_FK_AUTHOR));
                startActivityForResult(intent, UniqueId.REQ_AUTHOR_WORKS);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT: {
                if (fm.findFragmentByTag(EditAuthorDialogFragment.TAG) == null) {
                    long authorId = row.getLong(DBDefinitions.KEY_FK_AUTHOR);
                    //noinspection ConstantConditions
                    EditAuthorDialogFragment.newInstance(mModel.getDb().getAuthor(authorId))
                                            .show(fm, EditAuthorDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_AUTHOR_COMPLETE: {
                long authorId = row.getLong(DBDefinitions.KEY_FK_AUTHOR);
                // toggle the complete status
                boolean authorComplete = !row.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                if (mModel.getDb().setAuthorComplete(authorId, authorComplete)) {
                    mBookChangedListener.onBookChanged(0, BookChangedListener.AUTHOR, null);
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT: {
                if (fm.findFragmentByTag(EditPublisherDialogFragment.TAG) == null) {
                    Publisher publisher = new Publisher(row.getString(DBDefinitions.KEY_PUBLISHER));
                    EditPublisherDialogFragment.newInstance(publisher)
                                               .show(fm, EditPublisherDialogFragment.TAG);
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT: {
                new EditFormatDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getString(DBDefinitions.KEY_FORMAT));
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                new EditGenreDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getString(DBDefinitions.KEY_GENRE));
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                new EditLanguageDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getString(DBDefinitions.KEY_LANGUAGE));
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                new EditLocationDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getString(DBDefinitions.KEY_LOCATION));
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_VIEW_BOOK_AT_ISFDB: {
                IsfdbManager.openWebsite(this, row.getLong(DBDefinitions.KEY_ISFDB_ID));
                return true;
            }
            case R.id.MENU_VIEW_BOOK_AT_GOODREADS: {
                IsfdbManager.openWebsite(this, row.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID));
                return true;
            }
            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING: {
                IsfdbManager.openWebsite(this, row.getLong(DBDefinitions.KEY_LIBRARY_THING_ID));
                return true;
            }
            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY: {
                IsfdbManager.openWebsite(this, row.getLong(DBDefinitions.KEY_OPEN_LIBRARY_ID));
                return true;
            }
            /* ********************************************************************************** */
            case R.id.SUBMENU_AMAZON_SEARCH: {
                // after the user selects the submenu, we make individual items visible/hidden.
                boolean hasAuthor = row.contains(DBDefinitions.KEY_FK_AUTHOR)
                                    && row.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
                boolean hasSeries = row.contains(DBDefinitions.KEY_FK_SERIES)
                                    && row.getLong(DBDefinitions.KEY_FK_SERIES) > 0;

                SubMenu amazonSubMenu = menuItem.getSubMenu();
                amazonSubMenu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
                             .setVisible(hasAuthor);
                amazonSubMenu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
                             .setVisible(hasAuthor && hasSeries);
                amazonSubMenu.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
                             .setVisible(hasSeries);
                // let the normal call flow go on, it will display the submenu
                return false;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                AmazonManager.openWebsite(this, mModel.getAuthorFromRow(this, row), null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                AmazonManager.openWebsite(this, null, mModel.getSeriesFromRow(row));
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                AmazonManager.openWebsite(this,
                                          mModel.getAuthorFromRow(this, row),
                                          mModel.getSeriesFromRow(row));
                return true;
            }
            default:
                return false;
        }
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
    private void savePosition(final int topRow) {
        if (!isDestroyed()) {
            mModel.savePosition(topRow, mListView);
        }
    }

    /** Convenience method for {@link #savePosition(int)}. */
    private void savePosition() {
        if (!isDestroyed()) {
            mModel.savePosition(mLayoutManager.findFirstVisibleItemPosition(), mListView);
        }
    }

    /**
     * android.intent.action.SEARCH.
     */
    private void handleStandardSearchIntent() {
        String searchText = "";
        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            // Return the search results instead of all books (for the bookshelf)
            searchText = getIntent().getStringExtra(SearchManager.QUERY);

        } else if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            searchText = getIntent().getDataString();
        }
        mModel.getSearchCriteria().setKeywords(searchText);
    }

    /**
     * There was a search requested by the user.
     *
     * @return Returns {@code true} if search launched, and {@code false} if the activity does
     * not respond to search.
     * <p>
     * <strong>Note:</strong> uses the 'advanced' FTS search activity. To use the standard search,
     * comment this method out. The system will use {@link SearchSuggestionProvider}
     * as configured in res/xml/searchable.xml
     * <p>
     * FIXME: https://developer.android.com/guide/topics/search/search-dialog
     * the way this is implemented is a bit of a shoehorn... to be revisited.
     */
    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        mModel.getSearchCriteria().to(intent);
        startActivityForResult(intent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
        return true;
    }


    /**
     * Convenience wrapper method that handles the 4 steps of preparing the list header.
     *
     * @param listHasItems Flag to indicate there are in fact items in the list
     *
     * @return {@code true} if the header should display the 'level' texts.
     */
    private boolean setupListHeader(final boolean listHasItems) {
        setFilterTextField();
        setBookCountField();
        boolean showHeaderTexts = setHeaderTextVisibility();
        // Set the initial details to the current first visible row.
        if (listHasItems && showHeaderTexts) {
            setHeaderText(mModel.getTopRow());
        }
        return showHeaderTexts;
    }

    /**
     * display or hide the search text field in the header.
     */
    private void setFilterTextField() {
        String filterText = mModel.getSearchCriteria().getDisplayString();
        if (filterText.isEmpty()) {
            mFilterTextView.setVisibility(View.GONE);
        } else {
            mFilterTextView.setVisibility(View.VISIBLE);
            mFilterTextView.setText(getString(R.string.lbl_search_filtered_on_x, filterText));
        }
    }

    /**
     * Display the number of books in the current list.
     */
    private void setBookCountField() {
        if (mModel.getCurrentStyle().showBookCount()) {
            int totalBooks = mModel.getTotalBooks();
            int uniqueBooks = mModel.getUniqueBooks();
            String stringArgs;
            if (uniqueBooks != totalBooks) {
                stringArgs = getString(R.string.info_displaying_n_books_in_m_entries,
                                       uniqueBooks, totalBooks);
            } else {
                stringArgs = getResources().getQuantityString(R.plurals.displaying_n_books,
                                                              uniqueBooks, uniqueBooks);
            }
            mBookCountView.setText(getString(R.string.brackets, stringArgs));
            mBookCountView.setVisibility(View.VISIBLE);
        } else {
            mBookCountView.setVisibility(View.GONE);
        }
    }

    /**
     * Checks and sets visibility for the header lines.
     *
     * @return {@code true} if the style supports any level at all.
     */
    private boolean setHeaderTextVisibility() {

        BooklistStyle style = mModel.getCurrentStyle();
        // for level, set the visibility of the views.
        for (int level = 1; level <= 2; level++) {
            int index = level - 1;

            // a header is visible if
            // 1. the cursor provides the data for this level, and
            // 2. the style defined the level.
            //noinspection ConstantConditions
            if (mModel.getBuilder().levels() > level
                && style.hasHeaderForLevel(level)) {
                mHeaderTextView[index].setVisibility(View.VISIBLE);
                mHeaderTextView[index].setText("");
            } else {
                mHeaderTextView[index].setVisibility(View.GONE);
            }
        }
        // are we showing any levels?
        return style.hasHeaderForLevel(1) || style.hasHeaderForLevel(2);
    }

    /**
     * Update the list header to match the current top item.
     *
     * @param currentTopRow Top row which is visible
     */
    private void setHeaderText(@IntRange(from = 0) final int currentTopRow) {
        if (currentTopRow >= 0) {
            mModel.setLastTopRow(currentTopRow);
        } else {
            mModel.setLastTopRow(0);
        }

        // use visibility which was set in {@link #setHeaderTextVisibility}
        if (mHeaderTextView[0].getVisibility() == View.VISIBLE
            || mHeaderTextView[1].getVisibility() == View.VISIBLE) {

            BooklistPseudoCursor cursor = mModel.getListCursor();
            //noinspection ConstantConditions
            if (cursor.moveToPosition(mModel.getLastTopRow())) {
                BooklistMappedCursorRow row = cursor.getCursorRow();

                String[] lines = row.getLevelText(this);
                for (int i = 0; i < lines.length; i++) {
                    if (mHeaderTextView[i].getVisibility() == View.VISIBLE) {
                        mHeaderTextView[i].setText(row.getLevelText(this, i + 1));
                    }
                }
            }
        }
    }

    /**
     * Called if an interaction with Goodreads failed due to authorization issues.
     * Prompts the user to register.
     *
     * @param needs {@code true} if registration is needed
     */
    private void needsGoodreads(@Nullable final Boolean needs) {
        if (needs != null && needs) {
            RequestAuthTask.needsRegistration(this, mModel.getGoodreadsTaskListener());
        }
    }

    /**
     * Allows the ViewModel to send us a message to display to the user.
     * <p>
     * If the type is {@code Integer} we assume it's a {@code StringRes}
     * else we do a toString() it.
     *
     * @param message to display, either a {@code Integer (StringRes)} or a {@code String}
     */
    private void showUserMessage(@Nullable final Object message) {
        if (message instanceof Integer) {
            UserMessage.show(mListView, (int) message);
        } else if (message != null) {
            UserMessage.show(mListView, message.toString());
        }
    }
}
