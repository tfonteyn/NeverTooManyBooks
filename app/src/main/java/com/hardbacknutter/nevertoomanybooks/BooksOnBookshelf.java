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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO.ListRowDetails;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditPublisherDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.LendBookDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.simplestring.EditColorDialog;
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
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesActivity;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;
import com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay;
import com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSRecyclerView;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";

    /**
     * List header.
     * Views for the current row level-text.
     * These are shown in the header of the list (just below the bookshelf spinner) while scrolling.
     */
    private final TextView[] mHeaderTextView = new TextView[2];
    /** List header. */
    private TextView mStyleNameView;
    /** List header. */
    private TextView mFilterTextView;
    /** List header: The number of books in the current list. */
    private TextView mBookCountView;

    /** Array with the submenu FAB buttons. Element 0 shows at the bottom. */
    private ExtendedFloatingActionButton[] mFabMenuItems;

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
                        Log.d(TAG, "mOnBookshelfSelectionChanged"
                                   + "|previous=" + previous
                                   + "|selected=" + selected);
                    }

                    if (selected != null && !selected.equalsIgnoreCase(previous)) {
                        // make the new shelf the current
                        mModel.setCurrentBookshelf(BooksOnBookshelf.this, selected);
                        // new shelf selected, so we need a new list.
                        initBookList();
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
        initBookList();

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

    /** Apply the style that a user has selected. */
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
                    initBookList();
                }
            };

    /** Listener for clicks on the list. */
    private final BooklistAdapter.OnRowClickedListener mOnRowClickedListener
            = new BooklistAdapter.OnRowClickedListener() {

        /**
         * User clicked on a row.
         * <ul>
         * <li>Book: open the details screen.</li>
         * <li>Not a book: expand/collapse the section as appropriate.</li>
         * </ul>
         *
         * {@inheritDoc}
         */
        @Override
        public void onItemClick(final int position) {

            // Move the cursor, so we can read the data for this row.
            // No need to check the position first, i.e. if the user can click it, then it's there.
            final Cursor cursor = mModel.getListCursor();
            //noinspection ConstantConditions
            cursor.moveToPosition(position);
            final CursorRow cursorRow = new CursorRow(cursor);

            // If it's a book, open the details screen.
            if (cursorRow.getInt(DBDefinitions.KEY_BL_NODE_KIND) == BooklistGroup.RowKind.BOOK) {
                long bookId = cursorRow.getLong(DBDefinitions.KEY_FK_BOOK);
                String navTableName = mModel.createFlattenedBooklist();
                Intent intent = new Intent(BooksOnBookshelf.this, BookDetailsActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookId)
                        .putExtra(BookDetailsFragmentModel.BKEY_FLAT_BOOKLIST_TABLE, navTableName);
                startActivityForResult(intent, UniqueId.REQ_BOOK_VIEW);

            } else {
                // Else it's a level, expand/collapse recursively.
                long rowId = cursorRow.getInt(DBDefinitions.KEY_BL_LIST_VIEW_ROW_ID);
                boolean isExpanded = mModel.toggleNode(rowId);

                // make sure the cursor has valid rows for the new position.
                //noinspection ConstantConditions
                mModel.getListCursor().requery();
                mAdapter.notifyDataSetChanged();

                if (isExpanded) {
                    // if the user expanded the line at the bottom of the screen,
                    int lastPos = mLayoutManager.findLastCompletelyVisibleItemPosition();
                    if ((position + 1 == lastPos) || (position == lastPos)) {
                        // then we move the list a minimum of 2 positions upwards
                        // to make the expanded rows visible. Using 3 for comfort.
                        mListView.scrollToPosition(position + 3);
                    }
                }
            }
        }

        /**
         * User long-clicked on a row. Bring up a context menu as appropriate.
         *
         * {@inheritDoc}
         */
        @Override
        public boolean onItemLongClick(final int position) {

            // Move the cursor, so we can read the data for this row.
            // No need to check the position first, i.e. if the user can click it, then it's there.
            final Cursor cursor = mModel.getListCursor();
            //noinspection ConstantConditions
            cursor.moveToPosition(position);
            final CursorRow cursorRow = new CursorRow(cursor);

            final Menu menu = MenuPicker.createMenu(BooksOnBookshelf.this);
            // build/check the menu for this row
            if (onCreateContextMenu(menu, cursorRow)) {
                // we have a menu to show, set the title according to the level.
                int level = cursorRow.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
                String title = mAdapter.getLevelText(BooksOnBookshelf.this, level);
                // bring up the context menu
                new MenuPicker<>(BooksOnBookshelf.this, title, menu, position,
                                 BooksOnBookshelf.this::onContextItemSelected)
                        .show();
            }
            return true;
        }
    };
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
                    //FIXME: this is not called when the fast scroller stops scrolling
                    //but it's not unusable... the slighted swipe of the user brings the FAB back.
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
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(BooksOnBookshelfModel.class);
        mModel.init(this, getIntent().getExtras(), savedInstanceState);

        mModel.getUserMessage().observe(this, message -> {
            if (message != null) {
                Snackbar.make(mListView, message, Snackbar.LENGTH_LONG).show();
            }
        });
        mModel.getNeedsGoodreads().observe(this, needs -> {
            if (needs != null && needs) {
                RequestAuthTask.needsRegistration(this, mModel.getGoodreadsTaskListener());
            }
        });
        mModel.restoreCurrentBookshelf(this);

        // listen for the booklist being ready to display.
        mModel.getBooklist().observe(this, this::onDisplayList);

        // set the search capability to local (application) search, see:
        // https://developer.android.com/guide/topics/search/search-dialog#InvokingTheSearchDialog
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        // check & get search text coming from a system search intent
        handleStandardSearchIntent();

        mProgressBar = findViewById(R.id.progressBar);

        // enable the navigation menu
        setNavigationItemVisibility(R.id.nav_manage_list_styles, true);
        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);
        setNavigationItemVisibility(R.id.nav_import_export, true);
        setNavigationItemVisibility(R.id.nav_goodreads,
                                    GoodreadsManager.isShowSyncMenus(this));
        // Setup the list view.
        initListView();

        // details for the header.
        initHeader();
        // Setup the FAB button and the linked menu.
        initFAB();

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_book_list, null);
        }
    }

    /**
     * Called from {@link #onCreate}.
     * <p>
     * Reminder: we DO NOT init the adapter here. See {@link #initAdapter}.
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
        mStyleNameView = findViewById(R.id.style_name);
        mFilterTextView = findViewById(R.id.filter_text);
        mBookCountView = findViewById(R.id.book_count);
        mHeaderTextView[0] = findViewById(R.id.level_1_text);
        mHeaderTextView[1] = findViewById(R.id.level_2_text);

        // Setup the bookshelf spinner and adapter.
        mBookshelfSpinner = findViewById(R.id.bookshelf_spinner);

        // note that the list of names is empty right now, we'l populate it in onResume
        mBookshelfSpinnerAdapter = new ArrayAdapter<>(this,
                                                      R.layout.booksonbookshelf_bookshelf_spinner,
                                                      mModel.getBookshelfNameList());
        // use a different view when the spinner is open
        mBookshelfSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);
    }

    /**
     * Called from {@link #onCreate}.
     */
    private void initFAB() {
        // Make SURE that the array length fits the options list below.
        mFabMenuItems = new ExtendedFloatingActionButton[5];
        mFabMenuItems[0] = findViewById(R.id.fab0);
        mFabMenuItems[0].setOnClickListener(v -> addBySearchIsbn(true));
        mFabMenuItems[1] = findViewById(R.id.fab1);
        mFabMenuItems[1].setOnClickListener(v -> addBySearchIsbn(false));
        mFabMenuItems[2] = findViewById(R.id.fab2);
        mFabMenuItems[2].setOnClickListener(v -> addBySearch(BookSearchByTextFragment.TAG));
        mFabMenuItems[3] = findViewById(R.id.fab3);
        mFabMenuItems[3].setOnClickListener(v -> startAddManually());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showTabNativeId = prefs.getBoolean(Prefs.pk_edit_book_tabs_native_id, false);
        if (showTabNativeId) {
            mFabMenuItems[4] = findViewById(R.id.fab4);
            mFabMenuItems[4].setOnClickListener(v -> addBySearch(BookSearchByNativeIdFragment.TAG));
        }

        mFabButton = findViewById(R.id.fab);
        mFabButton.setOnClickListener(v -> showFABMenu(!mFabMenuItems[0].isShown()));
        mFabOverlay = findViewById(R.id.fabOverlay);
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

        float baseY = getResources().getDimension(R.dimen.fab_menu_translationY_base);
        float deltaY = getResources().getDimension(R.dimen.fab_menu_translationY_delta);

        float baseX = getResources().getDimension(R.dimen.fab_menu_translationX);
        float deltaX = getResources().getDimension(R.dimen.fab_menu_translationX_delta);

        // Test for split-screen layouts (or really small devices?)
        // Having more then 4 FAB buttons is not really a good UI design
        // But this just about fits our 5...
        //TODO: use resource qualifiers instead.
        boolean smallScreen = getResources().getConfiguration().screenHeightDp < 400;

        for (int i = 0; i < mFabMenuItems.length; i++) {
            ExtendedFloatingActionButton fab = mFabMenuItems[i];
            // allow for null items
            if (fab != null) {
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
        getMenuInflater().inflate(R.menu.o_bob, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {

        boolean showECPreferred = (mModel.getCurrentStyle(this).getTopLevel() > 1);
        menu.findItem(R.id.MENU_LEVEL_PREFERRED_COLLAPSE).setVisible(showECPreferred);

        menu.findItem(R.id.MENU_CLEAR_FILTERS).setEnabled(!mModel.getSearchCriteria().isEmpty());

        hideFABMenu();

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        hideFABMenu();

        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                TipManager.display(this, R.string.tip_booklist_style_menu, this::showStylePicker);
                return true;

            case R.id.MENU_LEVEL_PREFERRED_COLLAPSE:
                expandAllNodes(mModel.getCurrentStyle(this).getTopLevel(), false);
                return true;

            case R.id.MENU_LEVEL_EXPAND:
                expandAllNodes(1, true);
                return true;

            case R.id.MENU_LEVEL_COLLAPSE:
                expandAllNodes(1, false);
                return true;

            case R.id.MENU_CLEAR_FILTERS: {
                mModel.getSearchCriteria().clear();
                initBookList();
                return true;
            }
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from an options menu selection.
                // We pass the book ID's for the currently displayed list.
                ArrayList<Long> bookIds = mModel.getCurrentBookIdList();
                Intent intent = new Intent(this, BookSearchActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                        .putExtra(UniqueId.BKEY_ID_LIST, bookIds);
                startActivityForResult(intent, UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    @Override
    protected boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();
        switch (item.getItemId()) {
            case R.id.nav_manage_bookshelves: {
                Intent intent = new Intent(this, EditBookshelvesActivity.class)
                        .putExtra(EditBookshelvesModel.BKEY_CURRENT_BOOKSHELF,
                                  mModel.getCurrentBookshelf().getId());
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES);
                return true;
            }
            case R.id.nav_manage_list_styles: {
                Intent intent = new Intent(this, PreferredStylesActivity.class)
                        .putExtra(UniqueId.BKEY_STYLE_ID, mModel.getCurrentStyle(this).getId());
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
                return true;
            }

            default:
                return super.onNavigationItemSelected(item);
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET:
            case UniqueId.REQ_BOOK_VIEW:
            case UniqueId.REQ_BOOK_EDIT:
            case UniqueId.REQ_BOOK_DUPLICATE:
            case UniqueId.REQ_BOOK_SEARCH:
            case UniqueId.REQ_ADVANCED_LOCAL_SEARCH:
            case UniqueId.REQ_AUTHOR_WORKS: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        // modified status includes creation and duplication of books
                        if (extras.getBoolean(UniqueId.BKEY_BOOK_MODIFIED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.getBoolean(UniqueId.BKEY_BOOK_DELETED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }

                        if (mModel.getSearchCriteria().from(extras, true)) {
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
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // the last edited/inserted shelf
                    long bookshelfId = data.getLongExtra(DBDefinitions.KEY_PK_ID,
                                                         Bookshelf.DEFAULT_ID);
                    if (bookshelfId != mModel.getCurrentBookshelf().getId()) {
                        mModel.setCurrentBookshelf(bookshelfId);
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }
            // from BaseActivity Nav Panel or from sort menu dialog
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);

                    BooklistStyle style = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                    if (style != null) {
                        // save the new bookshelf/style combination
                        mModel.getCurrentBookshelf().setAsPreferred(this);
                        mModel.setCurrentStyle(this, style);
                    }

                    if (data.getBooleanExtra(UniqueId.BKEY_STYLE_MODIFIED, false)) {
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }
            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_IMP_EXP: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (data.hasExtra(UniqueId.BKEY_IMPORT_RESULT)) {
                        int options = data.getIntExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                       Options.NOTHING);
                        if (options != 0) {
                            if ((options & Options.BOOK_LIST_STYLES) != 0) {
                                // Force a refresh of the list of all user styles.
                                BooklistStyle.Helper.clear();
                            }
                            if ((options & Options.PREFERENCES) != 0) {
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

            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    /**
     * If the FAB menu is showing, hide it.
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

        // This Activity can be the (as normal) the real root activity,
        // but it can also have been started to show a filtered list.
        // Only clear and re-init if we are indeed the root.
        // Otherwise drop-through to onBackPressed.
        if (isTaskRoot() && !mModel.getSearchCriteria().isEmpty()) {
            mModel.getSearchCriteria().clear();
            initBookList();
            return;
        }

        super.onBackPressed();
    }

    @Override
    @CallSuper
    public void onResume() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "ENTER|onResume");
        }
        super.onResume();

        // don't build the list needlessly
        if (isRecreating() || isFinishing() || isDestroyed()) {
            return;
        }

        // clear the adapter; we'll prepare a new one and meanwhile the view/adapter
        // should obviously NOT try to display the old list.
        // Note we do not clear the cursor on the model here,
        // so we have the option of re-using it.
        mListView.setAdapter(null);
        mAdapter = null;

        // Update the list of bookshelves + set the current bookshelf.
        boolean bookshelfChanged = populateBookShelfSpinner();

        // This long if/else is to be able to debug/log *why* we're rebuilding
        if (bookshelfChanged) {
            // bookshelf changed, we need a new list
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|bookshelf changed, we need a new list");
            }
            initBookList();

        } else if (mModel.isForceRebuildInOnResume()) {
            // onActivityResult told us to rebuild
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|onActivityResult told us to rebuild");
            }
            initBookList();

        } else if (!mModel.isListLoaded()) {
            // we never did a build before
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|we never did a build before");
            }
            initBookList();

        } else {
            // no rebuild needed
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|reusing existing list");
            }

            //Snackbar.make(mListView, "reusing existing list", Snackbar.LENGTH_LONG);
            //initBookList();
            displayList(mModel.getNewListCursor(), mModel.getTargetRows());
        }

        // always reset for next iteration.
        mModel.setForceRebuildInOnResume(false);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "EXIT|onResume");
        }
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
        saveListPosition();
        super.onPause();
    }

    private void addBySearch(@NonNull final String tag) {
        Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, tag);
        startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
    }

    private void addBySearchIsbn(final boolean scanMode) {
        Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG)
                .putExtra(BookSearchByIsbnFragment.BKEY_SCAN_MODE, scanMode);
        startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
    }

    private void startAddManually() {
        Intent intent = new Intent(this, EditBookActivity.class);
        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
    }

    /** Syntax sugar. */
    private void showStylePicker() {
        StylePickerDialogFragment.newInstance(getSupportFragmentManager(),
                                              mModel.getCurrentStyle(this), false);
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
            displayList(mModel.getNewListCursor(), null);
        }
    }

    /**
     * Populate the BookShelf list in the Spinner and set the current bookshelf/style.
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
            Log.d(TAG, "populateBookShelfSpinner"
                       + "|previous=" + previous
                       + "|selected=" + selected);
        }

        // Flag up if the selection was different.
        return !selected.equalsIgnoreCase(previous);
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     * <p>
     * This is a wrapper for calling {@link BooksOnBookshelfModel#initBookList}
     */
    private void initBookList() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            // with stack trace, so we know who called us.
            Log.d(TAG, "initBookList", new Throwable());
        }

        // go create
        mModel.initBookList(this);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Called when the booklist builder was done building the cursor.
     * <p>
     * Does some elementary checking, then hands of to {@link #displayList}
     * <p>
     * The incoming holder object is in fact NonNull. But we check regardless sanity/paranoia.
     *
     * @param holder the results to display.
     */
    private void onDisplayList(@Nullable final BooksOnBookshelfModel.BuilderHolder holder) {
        // *always* ...
        mProgressBar.setVisibility(View.GONE);

        if (holder != null) {
            // check if we have a valid cursor before using it.
            BooklistCursor resultListCursor = holder.getResultListCursor();
            if (resultListCursor != null && !resultListCursor.isClosed()) {
                displayList(resultListCursor, holder.getResultTargetRows());
                return;
            }
        }

        // If we did not get a new cursor, recreate the adapter using the old one.
        // See #onResume: we cleared the adapter, but not the old cursor.
        // The adapter will be set on the view, which will trigger a refresh.
        initAdapter(mModel.getListCursor());
    }

    /**
     * Display the passed cursor in the ListView.
     *
     * @param newListCursor New cursor to use
     * @param targetRows    if set, change the position to targetRows.
     */
    private void displayList(@NonNull final BooklistCursor newListCursor,
                             @Nullable final List<ListRowDetails> targetRows) {

        // remove the default title to make space for the bookshelf spinner.
        setTitle("");

        mProgressBar.setVisibility(View.GONE);

        // Save the old list so we can close it later; will be null at app start.
        @Nullable
        final BooklistCursor oldCursor = mModel.getListCursor();

        // and set the new list
        mModel.setListCursor(newListCursor);
        // create and hookup the list adapter.
        initAdapter(newListCursor);

        final int count = newListCursor.getCount();
        // Scroll to the saved position
        if (mModel.getItemPosition() >= count) {
            // the list is shorter than it used to be, just scroll to the end
            mLayoutManager.scrollToPosition(mModel.getItemPosition());
        } else {
            // the desired values are related to the 'old' adapter/cursor.
            int desiredPosition = mModel.getItemPosition();
            long desiredRowId = mModel.getTopRowRowId();
            // need to map those to the new adapter/cursor. Ideally they will be the same.
            long actualRowId = mAdapter.getItemId(desiredPosition);
            int actualPosition = desiredPosition;

            // but if they are not equal,
            if (actualRowId != desiredRowId) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "desiredPosition=" + desiredPosition
                               + "|desiredRowId=" + desiredRowId
                               + "|actualRowId=" + actualRowId);
                }
                // URGENT: the intention is to FIND the correct position obviously;
                //  --/++ are placeholders
                if (actualRowId < desiredRowId) {
                    actualPosition++;
                } else {
                    // actualRowId > desiredRowId
                    actualPosition--;
                }
            }
            if (actualPosition < 0) {
                actualPosition = 0;
            }
            mLayoutManager.scrollToPositionWithOffset(actualPosition, mModel.getTopViewOffset());
        }

        // and save the updated position
        saveListPosition();

        // If a target position array is set, then queue a runnable to scroll to the target
        // best suited depending on what rows are (by then) currently visible
        if (targetRows != null) {
            mListView.post(() -> scrollToTarget(targetRows));
        }

        // Prepare the list header fields.
        mShowLevelHeaders = initHeaders();
        // Set the initial details to the current first visible row (if any).
        if (count > 0 && mShowLevelHeaders) {
            setHeaderLevelText();
        }

        // all set, we can close the old list
        if (oldCursor != null) {
            mModel.safeClose(oldCursor);
        }
    }

    /**
     * A new adapter is created each time the list is prepared,
     * as the underlying data can be very different from list to list.
     *
     * @param cursor with list of items
     */
    private void initAdapter(@Nullable final Cursor cursor) {
        // sanity check
        if (cursor == null) {
            Logger.warn(this, TAG, "initAdapter", "Cursor was NULL");
            return;
        }
        mAdapter = new BooklistAdapter(this, mModel.getCurrentStyle(this), mModel.getDb(), cursor);

        mAdapter.setOnRowClickedListener(mOnRowClickedListener);
        mListView.setAdapter(mAdapter);
    }

    /**
     * Set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #displayList}
     *
     * @param targetRows list of rows of which we want one to be visible to the user.
     */
    private void scrollToTarget(@NonNull final List<RowStateDAO.ListRowDetails> targetRows) {
        // Find the actual extend of the current view and get centre.
        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        final int lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition();
        final int centre = (lastVisibleItemPosition + firstVisibleItemPosition) / 2;

        // Get the first 'target' and make it 'best candidate'
        int best = targetRows.get(0).listPosition;
        // distance from currently visible centre row
        int dist = Math.abs(best - centre);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetRows.size(); i++) {
            int ri = targetRows.get(i).listPosition;
            int newDist = Math.abs(ri - centre);
            if (newDist < dist) {
                dist = newDist;
                best = ri;
            }
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
            Log.d(TAG, "scrollToTarget"
                       + "|firstVisibleItemPosition=" + firstVisibleItemPosition
                       + "|centre=" + centre
                       + "|lastVisibleItemPosition=" + lastVisibleItemPosition
                       + "|best=" + best);
        }

        // If the 'best' row is not in view, or at the edge, scroll it into view.
        if (best <= firstVisibleItemPosition || lastVisibleItemPosition <= best) {
            mLayoutManager.scrollToPosition(best);

            // Without this call some positioning may be off by one row.
            final int newPos = best;
            mListView.post(() -> mListView.smoothScrollToPosition(newPos));
        }
    }

    /**
     * Create a context menu based on row kind.
     *
     * @param menu      to populate
     * @param cursorRow current cursorRow
     *
     * @return {@code true} if there actually is a menu to show.
     * {@code false} if not OR if the only menus would be the 'search Amazon' set.
     */
    private boolean onCreateContextMenu(@NonNull final Menu menu,
                                        @NonNull final CursorRow cursorRow) {
        menu.clear();

        final int rowKind = cursorRow.getInt(DBDefinitions.KEY_BL_NODE_KIND);
        switch (rowKind) {
            case BooklistGroup.RowKind.BOOK: {
                getMenuInflater().inflate(R.menu.co_book, menu);

                boolean isRead = cursorRow.getBoolean(DBDefinitions.KEY_READ);
                menu.findItem(R.id.MENU_BOOK_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isRead);

                // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
                boolean lendingIsUsed = App.isUsed(DBDefinitions.KEY_LOANEE);
                boolean isAvailable = mModel.isAvailable(cursorRow);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(lendingIsUsed && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(lendingIsUsed && !isAvailable);

                menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
                    .setVisible(GoodreadsManager.isShowSyncMenus(this));

                MenuHandler.prepareOptionalMenus(menu, cursorRow);
                break;
            }
            case BooklistGroup.RowKind.AUTHOR: {
                getMenuInflater().inflate(R.menu.c_author, menu);

                boolean complete = cursorRow.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                MenuHandler.prepareOptionalMenus(menu, cursorRow);
                break;
            }
            case BooklistGroup.RowKind.SERIES: {
                if (cursorRow.getLong(DBDefinitions.KEY_FK_SERIES) != 0) {
                    getMenuInflater().inflate(R.menu.c_series, menu);

                    boolean complete =
                            cursorRow.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    MenuHandler.prepareOptionalMenus(menu, cursorRow);
                }
                break;
            }
            case BooklistGroup.RowKind.PUBLISHER: {
                if (!cursorRow.getString(DBDefinitions.KEY_PUBLISHER).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.LANGUAGE: {
                if (!cursorRow.getString(DBDefinitions.KEY_LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.LOCATION: {
                if (!cursorRow.getString(DBDefinitions.KEY_LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.GENRE: {
                if (!cursorRow.getString(DBDefinitions.KEY_GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.FORMAT: {
                if (!cursorRow.getString(DBDefinitions.KEY_FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.RowKind.COLOR: {
                if (!cursorRow.getString(DBDefinitions.KEY_COLOR).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_COLOR_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            default: {
                break;
            }
        }

        // if there are no specific menus for the current row.
        return menu.size() != 0;
    }

    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                         @NonNull final Integer position) {

        //noinspection ConstantConditions
        final CursorRow cursorRow = new CursorRow(mModel.getListCursor());

        FragmentManager fm = getSupportFragmentManager();

        final long bookId = cursorRow.getLong(DBDefinitions.KEY_FK_BOOK);

        switch (menuItem.getItemId()) {

            case R.id.MENU_BOOK_EDIT: {
                Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookId);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                String title = cursorRow.getString(DBDefinitions.KEY_TITLE);
                List<Author> authors = mModel.getDb().getAuthorsByBookId(bookId);
                StandardDialogs.deleteBookAlert(this, title, authors, () -> {
                    mModel.getDb().deleteBook(this, bookId);
                    mBookChangedListener
                            .onBookChanged(bookId, BookChangedListener.BOOK_DELETED, null);
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                Book book = new Book(bookId, mModel.getDb());
                Intent dupIntent = new Intent(this, EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, UniqueId.REQ_BOOK_DUPLICATE);
                return true;
            }

            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle the read status
                if (mModel.getDb()
                          .setBookRead(bookId, !cursorRow.getBoolean(DBDefinitions.KEY_READ))) {
                    mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                LendBookDialogFragment.newInstance(bookId,
                                                   cursorRow.getLong(DBDefinitions.KEY_FK_AUTHOR),
                                                   cursorRow.getString(DBDefinitions.KEY_TITLE))
                                      .show(fm, LendBookDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mModel.getDb().deleteLoan(bookId);
                mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                Book book = new Book(bookId, mModel.getDb());
                startActivity(Intent.createChooser(book.getShareBookIntent(this),
                                                   getString(R.string.menu_share_this)));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                Snackbar.make(mListView, R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                new SendOneBookTask(bookId, mModel.getGoodreadsTaskListener())
                        .execute();
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from a context click on a row.
                // We pass the book ID's which are suited for that row.
                Intent intent = new Intent(this, BookSearchActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG);

                ArrayList<Long> bookIds;
                switch (cursorRow.getInt(DBDefinitions.KEY_BL_NODE_KIND)) {

                    case BooklistGroup.RowKind.BOOK: {
                        bookIds = new ArrayList<>();
                        bookIds.add(bookId);
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE,
                                        cursorRow.getString(DBDefinitions.KEY_TITLE));
                        break;
                    }
                    case BooklistGroup.RowKind.AUTHOR: {
                        bookIds = mModel.getDb().getBookIdsByAuthor(
                                cursorRow.getLong(DBDefinitions.KEY_FK_AUTHOR));
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE,
                                        cursorRow.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                        break;
                    }
                    case BooklistGroup.RowKind.SERIES: {
                        bookIds = mModel.getDb().getBookIdsBySeries(
                                cursorRow.getLong(DBDefinitions.KEY_FK_SERIES));
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE,
                                        cursorRow.getString(DBDefinitions.KEY_SERIES_TITLE));
                        break;
                    }
                    case BooklistGroup.RowKind.PUBLISHER: {
                        String publisher = cursorRow.getString(DBDefinitions.KEY_PUBLISHER);
                        bookIds = mModel.getDb().getBookIdsByPublisher(publisher);
                        intent.putExtra(UniqueId.BKEY_DIALOG_TITLE, publisher);
                        break;
                    }
                    default: {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "onContextItemSelected"
                                       + "|MENU_BOOK_UPDATE_FROM_INTERNET not supported"
                                       + "|RowKind=" + cursorRow
                                               .getInt(DBDefinitions.KEY_BL_NODE_KIND));
                        }
                        return true;
                    }
                }

                intent.putExtra(UniqueId.BKEY_ID_LIST, bookIds);
                startActivityForResult(intent, UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT: {
                long seriesId = cursorRow.getLong(DBDefinitions.KEY_FK_SERIES);
                //noinspection ConstantConditions
                EditSeriesDialogFragment.newInstance(mModel.getDb().getSeries(seriesId))
                                        .show(fm, EditSeriesDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_SERIES_SET_COMPLETE:
            case R.id.MENU_SERIES_SET_INCOMPLETE: {
                long seriesId = cursorRow.getLong(DBDefinitions.KEY_FK_SERIES);
                // toggle the complete status
                boolean status = !cursorRow.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                if (mModel.getDb().setSeriesComplete(seriesId, status)) {
                    mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                Series series = mModel.getDb()
                                      .getSeries(cursorRow.getLong(DBDefinitions.KEY_FK_SERIES));
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(this, series, () -> {
                        mModel.getDb().deleteSeries(this, series.getId());
                        mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
                    });
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_WORKS: {
                Intent intent = new Intent(this, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID,
                                  cursorRow.getLong(DBDefinitions.KEY_FK_AUTHOR));
                startActivityForResult(intent, UniqueId.REQ_AUTHOR_WORKS);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT: {
                long authorId = cursorRow.getLong(DBDefinitions.KEY_FK_AUTHOR);
                //noinspection ConstantConditions
                EditAuthorDialogFragment.newInstance(mModel.getDb().getAuthor(authorId))
                                        .show(fm, EditAuthorDialogFragment.TAG);

                return true;
            }
            case R.id.MENU_AUTHOR_SET_COMPLETE:
            case R.id.MENU_AUTHOR_SET_INCOMPLETE: {
                long authorId = cursorRow.getLong(DBDefinitions.KEY_FK_AUTHOR);
                // toggle the complete status
                boolean status = !cursorRow.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                if (mModel.getDb().setAuthorComplete(authorId, status)) {
                    mBookChangedListener.onBookChanged(0, BookChangedListener.AUTHOR, null);
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT: {
                Publisher publisher = new Publisher(
                        cursorRow.getString(DBDefinitions.KEY_PUBLISHER));
                EditPublisherDialogFragment.newInstance(publisher)
                                           .show(fm, EditPublisherDialogFragment.TAG);

                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT: {
                new EditFormatDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(cursorRow.getString(DBDefinitions.KEY_FORMAT));
                return true;
            }
            case R.id.MENU_COLOR_EDIT: {
                new EditColorDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(cursorRow.getString(DBDefinitions.KEY_COLOR));
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                new EditGenreDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(cursorRow.getString(DBDefinitions.KEY_GENRE));
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                new EditLanguageDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(cursorRow.getString(DBDefinitions.KEY_LANGUAGE));
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                new EditLocationDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(cursorRow.getString(DBDefinitions.KEY_LOCATION));
                return true;
            }

            /* ********************************************************************************** */
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                AmazonManager.openWebsite(this, mModel.getAuthorFromRow(this, cursorRow), null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                AmazonManager.openWebsite(this, null, mModel.getSeriesFromRow(cursorRow));
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                AmazonManager.openWebsite(this,
                                          mModel.getAuthorFromRow(this, cursorRow),
                                          mModel.getSeriesFromRow(cursorRow));
                return true;
            }

            default:
                return MenuHandler.handleOpenOnWebsiteMenus(this, menuItem, cursorRow);
        }
    }

    /**
     * Save current position information.
     */
    private void saveListPosition() {
        if (!isDestroyed()) {
            int position = mLayoutManager.findFirstVisibleItemPosition();
            long rowId = mAdapter.getItemId(position);

            // This is just the number of pixels offset of the first visible row.
            int topViewOffset;
            View topView = mListView.getChildAt(0);
            if (topView != null) {
                topViewOffset = topView.getTop();
            } else {
                topViewOffset = 0;
            }

            mModel.saveListPosition(this, position, rowId, topViewOffset);
        }
    }

    /**
     * Handle the standard search intent / suggestions click.
     *
     * <a href="https://developer.android.com/guide/topics/search/search-dialog#ReceivingTheQuery">
     *     ReceivingTheQuery</a>
     */
    private void handleStandardSearchIntent() {
        String keywords = "";
        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            // Handle the standard search intent.
            keywords = getIntent().getStringExtra(SearchManager.QUERY);
//            // see {@link SearchSuggestionProvider} We don't actually use the suggestions.
//            SearchRecentSuggestions suggestions =
//                    new SearchRecentSuggestions(this,
//                                                SearchSuggestionProvider.AUTHORITY,
//                                                SearchSuggestionProvider.MODE);
//            suggestions.saveRecentQuery(keywords, null);

        } else if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            // Handle a suggestions click.
            // The ACTION_VIEW as set in res/xml/searchable.xml/searchSuggestIntentAction
            keywords = getIntent().getDataString();
        }
        mModel.getSearchCriteria().setKeywords(keywords);
    }

    @Override
    protected boolean onAdvancedSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        mModel.getSearchCriteria().to(intent);
        startActivityForResult(intent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
        return true;
    }

    /**
     * Display or hide the style name field in the header.
     */
    private void setHeaderStyleName() {
        if (mModel.getCurrentStyle(this).showHeader(BooklistStyle.SUMMARY_SHOW_STYLE_NAME)) {
            mStyleNameView.setText(mModel.getCurrentStyle(this).getLabel(this));
            mStyleNameView.setVisibility(View.VISIBLE);
        } else {
            mStyleNameView.setVisibility(View.GONE);
        }
    }

    /**
     * Display or hide the search/filter text field in the header.
     */
    private void setHeaderFilterText() {
        if (mModel.getCurrentStyle(this).showHeader(BooklistStyle.SUMMARY_SHOW_FILTER)) {
            Collection<String> filterText = new ArrayList<>();
            Collection<Filter> filters = mModel.getCurrentBookshelf()
                                               .getStyle(this, mModel.getDb())
                                               .getFilters();
            for (Filter f : filters) {
                if (f.isActive()) {
                    filterText.add(f.getLabel(this));
                }
            }

            String ftsSearchText = mModel.getSearchCriteria().getFtsSearchText();
            if (!ftsSearchText.isEmpty()) {
                filterText.add('"' + ftsSearchText + '"');
            }

            if (!filterText.isEmpty()) {
                mFilterTextView.setText(getString(R.string.lbl_search_filtered_on_x,
                                                  TextUtils.join(", ", filterText)));
                mFilterTextView.setVisibility(View.VISIBLE);
                return;
            }
        }

        mFilterTextView.setVisibility(View.GONE);
    }

    /**
     * Display or hide the number of books in the current list.
     */
    private void setHeaderBookCount() {
        if (mModel.getCurrentStyle(this).showHeader(BooklistStyle.SUMMARY_SHOW_BOOK_COUNT)) {
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
            mBookCountView.setText(stringArgs);
            mBookCountView.setVisibility(View.VISIBLE);
        } else {
            mBookCountView.setVisibility(View.GONE);
        }
    }

    /**
     * Prepare visibility for the header lines and set the fixed header fields.
     *
     * @return {@code true} if level-texts are used.
     */
    private boolean initHeaders() {
        setHeaderStyleName();
        setHeaderFilterText();
        setHeaderBookCount();

        BooklistStyle style = mModel.getCurrentStyle(this);

        boolean atLeastOne = false;

        // for each level, set the visibility of the views.
        for (int i = 0; i < mHeaderTextView.length; i++) {
            if (i < style.groupCount() && style.showHeader(BooklistStyle.HEADER_LEVELS[i])) {
                // the actual text will be filled in as/when the user scrolls
                mHeaderTextView[i].setText("");
                mHeaderTextView[i].setVisibility(View.VISIBLE);
                atLeastOne = true;
            } else {
                mHeaderTextView[i].setVisibility(View.GONE);
            }
        }

        return atLeastOne;
    }

    /**
     * Update the list header to match the current top item.
     */
    private void setHeaderLevelText() {
        int adapterPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (adapterPosition != RecyclerView.NO_POSITION) {
            mModel.setPreviousFirstVisibleItemPosition(adapterPosition);
        }

        // use visibility which was set in {@link #initHeaders}
        if (mHeaderTextView[0].getVisibility() == View.VISIBLE
            || mHeaderTextView[1].getVisibility() == View.VISIBLE) {

            Cursor cursor = mModel.getListCursor();
            //noinspection ConstantConditions
            if (cursor.moveToPosition(adapterPosition)) {
                String[] lines = mAdapter.getLevelText(this);
                for (int i = 0; i < lines.length; i++) {
                    if (mHeaderTextView[i].getVisibility() == View.VISIBLE) {
                        mHeaderTextView[i].setText(lines[i]);
                    }
                }
            }
        }
    }

}
