/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLException;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AddBookBySearchContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CalibreSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookFromBundleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchFtsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfHeaderBinding;
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
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleViewModel;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.SpinnerInteractionListener;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * <p>
 * FIXME: This class is becoming increasingly overloaded with ActivityResultLauncher
 * and *DialogFragment.Launcher objects. Need to find a way to refactor all these into
 * something more manageable.
 *
 *
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

    public static final int FAB_4_SEARCH_EXTERNAL_ID = 4;
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

    /** Make a backup. */
    private final ActivityResultLauncher<Void> mExportLauncher =
            registerForActivityResult(new ExportContract(), success -> {});

    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> mStripInfoSyncLauncher;
    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> mCalibreSyncLauncher;

    /** Delegate for Calibre. */
    @Nullable
    private CalibreHandler mCalibreHandler;
    /** Delegate for Amazon. */
    @Nullable
    private AmazonHandler mAmazonHandler;

    /** Multi-type adapter to manage list connection to cursor. */
    @Nullable
    private BooklistAdapter mAdapter;
    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel mVm;

    /** Do an import. */
    private final ActivityResultLauncher<Void> mImportLauncher =
            registerForActivityResult(new ImportContract(), this::onImportFinished);
    /** Display a Book. */
    private final ActivityResultLauncher<ShowBookContract.Input> mDisplayBookLauncher =
            registerForActivityResult(new ShowBookContract(), this::onBookEditFinished);
    /** Edit a Book. */
    private final ActivityResultLauncher<Long> mEditByIdLauncher =
            registerForActivityResult(new EditBookByIdContract(), this::onBookEditFinished);
    /** Duplicate and edit a Book. */
    private final ActivityResultLauncher<Bundle> mDuplicateLauncher =
            registerForActivityResult(new EditBookFromBundleContract(), this::onBookEditFinished);
    /** Update an individual Book with information from the internet. */
    private final ActivityResultLauncher<Book> mUpdateBookLauncher =
            registerForActivityResult(new UpdateSingleBookContract(), this::onBookEditFinished);

    /** Add a Book by doing a search on the internet. */
    private final ActivityResultLauncher<AddBookBySearchContract.By> mAddBookBySearchLauncher =
            registerForActivityResult(new AddBookBySearchContract(), this::onBookEditFinished);
    /** Update a list of Books with information from the internet. */
    private final ActivityResultLauncher<UpdateBooklistContract.Input> mUpdateBookListLauncher =
            registerForActivityResult(new UpdateBooklistContract(), this::onBookEditFinished);
    /** View all works of an Author. */
    private final ActivityResultLauncher<AuthorWorksContract.Input> mAuthorWorksLauncher =
            registerForActivityResult(new AuthorWorksContract(), this::onBookEditFinished);
    /** Manage the list of (preferred) styles. */
    private final ActivityResultLauncher<String> mEditStylesLauncher =
            registerForActivityResult(new PreferredStylesContract(), data -> {
                if (data != null) {
                    // we get the UUID for the selected style back.
                    final String uuid = data.getString(ListStyle.BKEY_STYLE_UUID);
                    if (uuid != null) {
                        mVm.onStyleChanged(this, uuid);
                    }

                    // This is independent from the above style having been modified ot not.
                    if (data.getBoolean(EditStyleContract.BKEY_STYLE_MODIFIED, false)) {
                        mVm.setForceRebuildInOnResume(true);
                    }
                }
            });

    /** The local FTS based search. */
    private final ActivityResultLauncher<SearchCriteria> mFtsSearchLauncher =
            registerForActivityResult(new SearchFtsContract(), data -> {
                if (mVm.setSearchCriteria(data, true)) {
                    //URGENT: switch bookshelf? all-books?
                    mVm.setForceRebuildInOnResume(true);
                }
            });

    /** Manage the book shelves. */
    private final ActivityResultLauncher<Long> mManageBookshelvesLauncher =
            registerForActivityResult(new EditBookshelvesContract(), bookshelfId -> {
                if (bookshelfId != 0 && bookshelfId != mVm.getCurrentBookshelf().getId()) {
                    mVm.setCurrentBookshelf(this, bookshelfId);
                    mVm.setForceRebuildInOnResume(true);
                }
            });
    /** Edit an individual style. */
    private final ActivityResultLauncher<EditStyleContract.Input> mEditStyleLauncher =
            registerForActivityResult(new EditStyleContract(), data -> {
                if (data != null) {
                    // We get here from the StylePickerDialogFragment (i.e. the style menu)
                    // when the user choose to EDIT a style.
                    if (data.uuid != null && !data.uuid.isEmpty()) {
                        mVm.onStyleEdited(this, data.uuid);

                        // ALWAYS rebuild here, even when the style was not modified
                        // as we're handling this as a style-change
                        // (we could do checks... but it's not worth the effort.)
                        // i.e. same as in mOnStylePickerListener
                        mVm.setForceRebuildInOnResume(true);
                    }
                }
            });
    @Nullable
    private ViewBookOnWebsiteHandler mViewBookHandler;

    /** Encapsulates the FAB button/menu. */
    private FabMenu mFabMenu;
    /** View Binding. */
    private BooksonbookshelfBinding mVb;
    /** List layout manager. */
    private LinearLayoutManager mLayoutManager;
    /**
     * Accept the result from the dialog.
     */
    private final EditBookshelfDialogFragment.Launcher mEditBookshelfLauncher =
            new EditBookshelfDialogFragment.Launcher(RK_EDIT_BOOKSHELF) {
                @Override
                public void onResult(final long bookshelfId) {
                    if (bookshelfId != mVm.getCurrentBookshelf().getId()) {
                        onRowChange(RowChangeListener.BOOKSHELF, bookshelfId);
                    }
                }
            };
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
                        final boolean isChanged = id != mVm.getCurrentBookshelf().getId();
                        if (isChanged) {
                            saveListPosition();
                            mVm.setCurrentBookshelf(parent.getContext(), id);
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
            new StylePickerDialogFragment.Launcher(RK_STYLE_PICKER) {
                @Override
                public void onResult(@NonNull final String uuid) {
                    saveListPosition();
                    mVm.onStyleChanged(BooksOnBookshelf.this, uuid);
                    mVm.resetPreferredListRebuildMode(BooksOnBookshelf.this);
                    buildBookList();
                }
            };
    /**
     * Accept the result from the dialog.
     */
    private final EditLenderDialogFragment.Launcher mEditLenderLauncher =
            new EditLenderDialogFragment.Launcher(RK_EDIT_LENDER) {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    onBookChange(RowChangeListener.BOOK_LOANEE, bookId);
                }
            };
    /**
     * Listener for clicks on the list.
     */
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
                    if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        mDisplayBookLauncher.launch(new ShowBookContract.Input(
                                rowData.getLong(DBKey.FK_BOOK),
                                mVm.getBookNavigationTableName(),
                                rowData.getLong(DBKey.PK_ID),
                                mVm.getCurrentStyle(BooksOnBookshelf.this).getUuid()
                        ));

                    } else {
                        // it's a level, expand/collapse.
                        setNodeState(rowData, BooklistNode.NEXT_STATE_TOGGLE, 1);
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
                        final int level = rowData.getInt(DBKey.KEY_BL_NODE_LEVEL);
                        final String title = mAdapter.getLevelText(position, level);

                        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
                            mMenuLauncher.launch(title, null, menu, position);
                        } else {
                            new MenuPicker(BooksOnBookshelf.this, title, null, menu, position,
                                           BooksOnBookshelf.this::onContextItemSelected)
                                    .show();
                        }
                    }
                    return true;
                }
            };
    /**
     * React to the user selecting a context menu option. (MENU_PICKER_USES_FRAGMENT).
     */
    private final MenuPickerDialogFragment.Launcher mMenuLauncher =
            new MenuPickerDialogFragment.Launcher(RK_MENU_PICKER) {
                @Override
                public boolean onResult(@IdRes final int itemId,
                                        final int position) {
                    return onContextItemSelected(itemId, position);
                }
            };
    /**
     * The adapter used to fill the Bookshelf selector.
     */
    private ExtArrayAdapter<Bookshelf> mBookshelfAdapter;

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

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        createFragmentResultListeners();
        createViewModel();
        createSyncDelegates(global);
        mAmazonHandler = new AmazonHandler(this);
        mViewBookHandler = new ViewBookOnWebsiteHandler(this);

        createBookshelfSpinner();
        createBooklist(global);

        // Initialise adapter without a cursor. We'll recreate it with a cursor when
        // we're ready to display the book-list.
        // If we don't create it here then some Android internals cause problems.
        createListAdapter(false);

        createFabMenu(global);
        updateSyncMenuVisibility(global);

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        if (savedInstanceState == null) {
            TipManager.getInstance().display(this, R.string.tip_book_list, null);
        }
    }

    private void createFragmentResultListeners() {
        final FragmentManager fm = getSupportFragmentManager();

        fm.setFragmentResultListener(RowChangeListener.REQUEST_KEY, this, mRowChangeListener);

        mEditBookshelfLauncher.registerForFragmentResult(fm, this);
        mEditLenderLauncher.registerForFragmentResult(fm, this);
        mOnStylePickerLauncher.registerForFragmentResult(fm, this);
        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.registerForFragmentResult(fm, this);
        }
    }

    private void createViewModel() {
        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        mVm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        mVm.init(this, getIntent().getExtras());
        mVm.onCancelled().observe(this, this::onBuildCancelled);
        mVm.onFailure().observe(this, this::onBuildFailed);
        mVm.onFinished().observe(this, this::onBuildFinished);
    }

    /**
     * Create the optional launcher and delegates.
     *
     * @param global Global preferences
     */
    private void createSyncDelegates(@NonNull final SharedPreferences global) {

        if (SyncServer.CalibreCS.isEnabled(global)) {
            mCalibreSyncLauncher = registerForActivityResult(new CalibreSyncContract(), data -> {
                if (data != null && data.containsKey(ImportResults.BKEY_IMPORT_RESULTS)) {
                    mVm.setForceRebuildInOnResume(true);
                }
            });

            try {
                mCalibreHandler = new CalibreHandler(this);
                mCalibreHandler.onViewCreated(this, mVb.getRoot());
            } catch (@NonNull final SSLException | CertificateException ignore) {
                // ignore
            }
        }

        if (SyncServer.StripInfo.isEnabled(global)) {
            mStripInfoSyncLauncher = registerForActivityResult(
                    new StripInfoSyncContract(), data -> {
                        if (data != null && data.containsKey(ImportResults.BKEY_IMPORT_RESULTS)) {
                            mVm.setForceRebuildInOnResume(true);
                        }
                    });
        }
    }

    private void createBooklist(@NonNull final SharedPreferences global) {
        //noinspection ConstantConditions
        mLayoutManager = (LinearLayoutManager) mVb.list.getLayoutManager();
        mVb.list.addItemDecoration(new TopLevelItemDecoration(this));

        // Optional overlay
        final int overlayType = Prefs.getIntListPref(
                global, Prefs.pk_booklist_fastscroller_overlay,
                FastScroller.OverlayProvider.STYLE_MD2);
        FastScroller.attach(mVb.list, overlayType);

        // Number of views to cache offscreen arbitrarily set to 20; the default is 2.
        mVb.list.setItemViewCacheSize(20);
        mVb.list.setDrawingCacheEnabled(true);
        mVb.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
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
     * @param display set to {@code false} for initial creation!
     *
     * @return the item count of the list adapter.
     */
    @IntRange(from = 0)
    public int createListAdapter(final boolean display) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "createListAdapter|display=" + display, new Throwable());
        }

        if (display) {
            mAdapter = new BooklistAdapter(this);
            // install single and long-click listeners
            mAdapter.setOnRowClickedListener(mOnRowClickedListener);
            // hookup the cursor
            mAdapter.setCursor(this, mVm.getNewListCursor(), mVm.getCurrentStyle(this));

            // Combine the adapters for the list header and the actual list
            final ConcatAdapter concatAdapter = new ConcatAdapter(
                    new ConcatAdapter.Config.Builder()
                            .setIsolateViewTypes(true)
                            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                            .build(),
                    new HeaderAdapter(), mAdapter);

            mVb.list.setAdapter(concatAdapter);

            // make the list view visible!
            mVb.list.setVisibility(View.VISIBLE);

            return mAdapter.getItemCount();

        } else {
            mVb.list.setVisibility(View.GONE);
            return 0;
        }
    }

    private void createBookshelfSpinner() {
        // remove the default title to make space for the spinner.
        setTitle("");

        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        mBookshelfAdapter = new EntityArrayAdapter<>(this, mVm.getBookshelfList());

        mVb.bookshelfSpinner.setAdapter(mBookshelfAdapter);
        mVb.bookshelfSpinner.setOnTouchListener(mOnBookshelfSelectionChanged);
        mVb.bookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);
    }

    private void createFabMenu(@NonNull final SharedPreferences global) {
        mFabMenu.attach(mVb.list);
        mFabMenu.setOnClickListener(view -> onFabMenuItemSelected(view.getId()));
        mFabMenu.getItem(FAB_4_SEARCH_EXTERNAL_ID).setEnabled(
                EditBookExternalIdFragment.isShowTab(global));
    }

    /**
     * Show or hide the synchronization menu.
     */
    private void updateSyncMenuVisibility(@NonNull final SharedPreferences global) {
        //noinspection ConstantConditions
        getNavigationMenuItem(R.id.nav_sync).setVisible(SyncServer.isAnyEnabled(global));
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
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        getMenuInflater().inflate(R.menu.bob, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        mFabMenu.hideMenu();

        final boolean showECPreferred = mVm.getCurrentStyle(this).getTopLevel() > 1;
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
            expandAllNodes(mVm.getCurrentStyle(this).getTopLevel(), false);
            return true;

        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            expandAllNodes(1, true);
            return true;

        } else if (itemId == R.id.MENU_LEVEL_COLLAPSE) {
            expandAllNodes(1, false);
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            // IMPORTANT: this is from the options menu selection.
            // We pass the book ID's for the currently displayed list.
            //TODO: add a fitting screen subtitle
            mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                    mVm.getCurrentBookIdList(), null, null));
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

        final int rowGroupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                getMenuInflater().inflate(R.menu.book, menu);
                getMenuInflater().inflate(R.menu.sm_view_on_site, menu);
                getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                final SharedPreferences global = PreferenceManager
                        .getDefaultSharedPreferences(this);

                final boolean isRead = rowData.getBoolean(DBKey.BOOL_READ);
                menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

                // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
                final boolean useLending = DBKey.isUsed(global, DBKey.KEY_LOANEE);
                final boolean isAvailable = mVm.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                if (SyncServer.CalibreCS.isEnabled(global)) {
                    final Book book = Objects.requireNonNull(DataHolderUtils.getBook(rowData));
                    //noinspection ConstantConditions
                    mCalibreHandler.prepareMenu(menu, book);
                }

                //noinspection ConstantConditions
                mViewBookHandler.prepareMenu(menu, rowData);

                //noinspection ConstantConditions
                mAmazonHandler.prepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                getMenuInflater().inflate(R.menu.author, menu);
                getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                final boolean complete = rowData.getBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                //noinspection ConstantConditions
                mAmazonHandler.prepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBKey.FK_SERIES) != 0) {
                    getMenuInflater().inflate(R.menu.series, menu);
                    getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                    final boolean complete =
                            rowData.getBoolean(DBKey.BOOL_SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    //noinspection ConstantConditions
                    mAmazonHandler.prepareMenu(menu, rowData);
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (rowData.getLong(DBKey.FK_PUBLISHER) != 0) {
                    getMenuInflater().inflate(R.menu.publisher, menu);
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
                if (!rowData.getString(DBKey.KEY_LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (!rowData.getString(DBKey.KEY_LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (!rowData.getString(DBKey.KEY_GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (!rowData.getString(DBKey.KEY_FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (!rowData.getString(DBKey.KEY_COLOR).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_COLOR_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
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
            .setIcon(R.drawable.ic_baseline_broken_image_24);

        // if it's a level, add the expand option
        if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) != BooklistGroup.BOOK) {
            menu.add(Menu.NONE, R.id.MENU_DIVIDER, menuOrder++, "")
                .setEnabled(false);
            //noinspection UnusedAssignment
            menu.add(Menu.NONE, R.id.MENU_LEVEL_EXPAND, menuOrder++, R.string.lbl_level_expand)
                .setIcon(R.drawable.ic_baseline_unfold_more_24);
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

        // first check the non-row menus.

        if (itemId == R.id.MENU_SYNC_CALIBRE) {
            //noinspection ConstantConditions
            mCalibreSyncLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_SYNC_STRIP_INFO) {
            //noinspection ConstantConditions
            mStripInfoSyncLauncher.launch(null);
            return true;
        }

        // Next handle the real context menus.

        // Move the cursor, so we can read the data for this row.
        // The majority of the time this is not needed, but a fringe case (toggle node)
        // showed it should indeed be done.
        // Paranoia: if the user can click it, then this should be fine.
        Objects.requireNonNull(mAdapter, "mAdapter");
        final Cursor cursor = mAdapter.getCursor();
        if (!cursor.moveToPosition(position)) {
            return false;
        }

        final DataHolder rowData = new CursorRow(cursor);


        if (itemId == R.id.MENU_BOOK_EDIT) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            mEditByIdLauncher.launch(bookId);
            return true;

        } else if (itemId == R.id.MENU_BOOK_DELETE) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            final String title = rowData.getString(DBKey.KEY_TITLE);
            final List<Author> authors = mVm.getAuthorsByBookId(bookId);
            StandardDialogs.deleteBook(this, title, authors, () -> {
                if (mVm.deleteBook(bookId)) {
                    onBookChange(RowChangeListener.BOOK_DELETED, bookId);
                }
            });
            return true;

        } else if (itemId == R.id.MENU_BOOK_DUPLICATE) {
            final Book book = Objects.requireNonNull(DataHolderUtils.getBook(rowData));
            mDuplicateLauncher.launch(book.duplicate());
            return true;

        } else if (itemId == R.id.MENU_BOOK_SET_READ
                   || itemId == R.id.MENU_BOOK_SET_UNREAD) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // toggle the read status
            final boolean status = !rowData.getBoolean(DBKey.BOOL_READ);
            if (mVm.setBookRead(bookId, status)) {
                onBookChange(RowChangeListener.BOOK_READ, bookId);
            }
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
            final Intent intent = new Intent(this, SettingsHostActivity.class)
                    .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG,
                              CalibrePreferencesFragment.TAG);
            startActivity(intent);
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            mEditLenderLauncher.launch(bookId, rowData.getString(DBKey.KEY_TITLE));
            return true;

        } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            mVm.lendBook(bookId, null);
            onBookChange(RowChangeListener.BOOK_LOANEE, bookId);
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_SHARE) {
            final Book book = Objects.requireNonNull(DataHolderUtils.getBook(rowData));
            startActivity(book.getShareIntent(this));
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                final Book book = Objects.requireNonNull(DataHolderUtils.getBook(rowData));
                mUpdateBookLauncher.launch(book);
                return true;
            }

            // Show the sub menu
            updateBooksFromInternetData(position, rowData);
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_THIS_SHELF_ONLY) {
            // selection from R.id.MENU_UPDATE_FROM_INTERNET sub menu
            updateBooksFromInternetData(rowData, true);
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_ALL_SHELVES) {
            // selection from R.id.MENU_UPDATE_FROM_INTERNET sub menu
            updateBooksFromInternetData(rowData, false);
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_SERIES_EDIT) {
            final Series series = Objects.requireNonNull(DataHolderUtils.getSeries(rowData));
            EditSeriesDialogFragment.launch(this, series);
            return true;

        } else if (itemId == R.id.MENU_SERIES_SET_COMPLETE
                   || itemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
            final long seriesId = rowData.getLong(DBKey.FK_SERIES);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.BOOL_SERIES_IS_COMPLETE);
            if (mVm.setSeriesComplete(seriesId, status)) {
                onRowChange(RowChangeListener.SERIES, seriesId);
            }
            return true;

        } else if (itemId == R.id.MENU_SERIES_DELETE) {
            final Series series = Objects.requireNonNull(DataHolderUtils.getSeries(rowData));
            StandardDialogs.deleteSeries(this, series, () -> {
                mVm.delete(this, series);
                onRowChange(RowChangeListener.SERIES, series.getId());
            });
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_AUTHOR_WORKS) {
            mAuthorWorksLauncher.launch(new AuthorWorksContract.Input(
                    rowData.getLong(DBKey.FK_AUTHOR),
                    mVm.getCurrentBookshelf().getId()));
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_EDIT) {
            final Author author = Objects.requireNonNull(DataHolderUtils.getAuthor(rowData));
            EditAuthorDialogFragment.launch(this, author);
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_SET_COMPLETE
                   || itemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
            final long authorId = rowData.getLong(DBKey.FK_AUTHOR);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE);
            if (mVm.setAuthorComplete(authorId, status)) {
                onRowChange(RowChangeListener.AUTHOR, authorId);
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_PUBLISHER_EDIT) {
            final Publisher publisher = Objects.requireNonNull(
                    DataHolderUtils.getPublisher(rowData));
            EditPublisherDialogFragment.launch(this, publisher);
            return true;

        } else if (itemId == R.id.MENU_PUBLISHER_DELETE) {
            final Publisher publisher = Objects.requireNonNull(
                    DataHolderUtils.getPublisher(rowData));
            StandardDialogs.deletePublisher(this, publisher, () -> {
                mVm.delete(this, publisher);
                onRowChange(RowChangeListener.PUBLISHER, publisher.getId());
            });

            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_BOOKSHELF_EDIT) {
            final Bookshelf bookshelf = Objects.requireNonNull(
                    DataHolderUtils.getBookshelf(rowData));
            mEditBookshelfLauncher.launch(bookshelf);
            return true;

        } else if (itemId == R.id.MENU_BOOKSHELF_DELETE) {
            final Bookshelf bookshelf = Objects.requireNonNull(
                    DataHolderUtils.getBookshelf(rowData));
            StandardDialogs.deleteBookshelf(this, bookshelf, () -> {
                mVm.delete(bookshelf);
                onRowChange(RowChangeListener.BOOKSHELF, bookshelf.getId());
            });
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_FORMAT_EDIT) {
            EditFormatDialogFragment.launch(this, rowData.getString(DBKey.KEY_FORMAT));
            return true;

        } else if (itemId == R.id.MENU_COLOR_EDIT) {
            EditColorDialogFragment.launch(this, rowData.getString(DBKey.KEY_COLOR));
            return true;

        } else if (itemId == R.id.MENU_GENRE_EDIT) {
            EditGenreDialogFragment.launch(this, rowData.getString(DBKey.KEY_GENRE));
            return true;

        } else if (itemId == R.id.MENU_LANGUAGE_EDIT) {
            EditLanguageDialogFragment.launch(this, rowData.getString(DBKey.KEY_LANGUAGE));
            return true;

        } else if (itemId == R.id.MENU_LOCATION_EDIT) {
            EditLocationDialogFragment.launch(this, rowData.getString(DBKey.KEY_LOCATION));
            return true;

        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            setNodeState(rowData, BooklistNode.NEXT_STATE_EXPANDED,
                         mVm.getCurrentStyle(this).getGroups().size());
            return true;

        } else if (itemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
            final BooklistNode node = mVm.getNextBookWithoutCover(nodeRowId);
            if (node != null) {
                displayList(node);
            }
            return true;
        }

        if (mCalibreHandler != null && mCalibreHandler.onItemSelected(itemId, rowData)) {
            return true;
        }

        if (mAmazonHandler != null && mAmazonHandler.onItemSelected(itemId, rowData)) {
            return true;
        }

        if (mViewBookHandler != null && mViewBookHandler.onItemSelected(itemId, rowData)) {
            return true;
        }

        return false;
    }

    private void onFabMenuItemSelected(@IdRes final int itemId) {

        if (itemId == R.id.fab0_scan_barcode) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.Scan);

        } else if (itemId == R.id.fab1_search_isbn) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.Isbn);

        } else if (itemId == R.id.fab2_search_text) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.Text);

        } else if (itemId == R.id.fab3_add_manually) {
            mEditByIdLauncher.launch(0L);

        } else if (itemId == R.id.fab4_search_external_id) {
            mAddBookBySearchLauncher.launch(AddBookBySearchContract.By.ExternalId);

        } else {
            throw new IllegalArgumentException(String.valueOf(itemId));
        }
    }

    @Override
    protected boolean onNavigationItemSelected(@IdRes final int itemId) {
        closeNavigationDrawer();

        if (itemId == R.id.nav_advanced_search) {
            mFtsSearchLauncher.launch(mVm.getSearchCriteria());
            return true;

        } else if (itemId == R.id.nav_manage_bookshelves) {
            // overridden, so we can pass the current bookshelf id.
            mManageBookshelvesLauncher.launch(mVm.getCurrentBookshelf().getId());
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

        } else if (itemId == R.id.nav_sync) {
            showSyncMenu();
            return true;
        }

        return super.onNavigationItemSelected(itemId);
    }

    private void showSyncMenu() {
        final Menu menu = MenuPicker.createMenu(this);
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        if (SyncServer.CalibreCS.isEnabled(global)) {
            menu.add(Menu.NONE, R.id.MENU_SYNC_CALIBRE, 0, R.string.site_calibre)
                .setIcon(R.drawable.ic_baseline_cloud_24);
        }

        if (SyncServer.StripInfo.isEnabled(global)) {
            menu.add(Menu.NONE, R.id.MENU_SYNC_STRIP_INFO, 0, R.string.site_stripinfo_be)
                .setIcon(R.drawable.ic_stripinfo);
        }

        final String title = getString(R.string.action_synchronize);
        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.launch(title, null, menu, 0);
        } else {
            new MenuPicker(this, title, null, menu, 0, this::onContextItemSelected)
                    .show();
        }
    }

    /**
     * IMPORTANT: this is from a context click on a row.
     * We pass the book ID's which are suited for that row.
     *
     * @param position in the list
     * @param rowData  for the row which was selected
     */
    private void updateBooksFromInternetData(final int position,
                                             @NonNull final DataHolder rowData) {
        final int groupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);

        final String message;
        switch (groupId) {
            case BooklistGroup.AUTHOR: {
                message = rowData.getString(DBKey.KEY_AUTHOR_FORMATTED);
                break;
            }
            case BooklistGroup.SERIES: {
                message = rowData.getString(DBKey.KEY_SERIES_TITLE);
                break;
            }
            case BooklistGroup.PUBLISHER: {
                message = rowData.getString(DBKey.KEY_PUBLISHER_NAME);
                break;
            }
            default: {
                if (BuildConfig.DEBUG /* always */) {
                    throw new IllegalArgumentException(
                            "updateBooksFromInternetData|not supported|groupId=" + groupId);
                }
                return;
            }
        }

        final String title = getString(R.string.menu_update_books);

        final Menu menu = MenuPicker.createMenu(this);
        getMenuInflater().inflate(R.menu.update_books, menu);
        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.launch(title, message, menu, position);
        } else {
            new MenuPicker(this, title, message, menu, position, this::onContextItemSelected)
                    .show();
        }
    }

    private void updateBooksFromInternetData(@NonNull final DataHolder rowData,
                                             final boolean justThisBookshelf) {
        final int groupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);

        switch (groupId) {
            case BooklistGroup.AUTHOR: {
                final long id = rowData.getLong(DBKey.FK_AUTHOR);
                final String name = rowData.getString(DBKey.KEY_AUTHOR_FORMATTED);
                mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        mVm.getBookIdsByAuthor(id, justThisBookshelf),
                        getString(R.string.lbl_author), name));
                break;
            }
            case BooklistGroup.SERIES: {
                final long id = rowData.getLong(DBKey.FK_SERIES);
                final String name = rowData.getString(DBKey.KEY_SERIES_TITLE);
                mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        mVm.getBookIdsBySeries(id, justThisBookshelf),
                        getString(R.string.lbl_series), name));
                break;
            }
            case BooklistGroup.PUBLISHER: {
                final long id = rowData.getLong(DBKey.FK_PUBLISHER);
                final String name = rowData.getString(DBKey.KEY_PUBLISHER_NAME);
                mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        mVm.getBookIdsByPublisher(id, justThisBookshelf),
                        getString(R.string.lbl_publisher), name));
                break;
            }
            default: {
                if (BuildConfig.DEBUG /* always */) {
                    throw new IllegalArgumentException(
                            "updateBooksFromInternetData|not supported|groupId=" + groupId);
                }
                break;
            }
        }
    }

    public void editStyle(@NonNull final ListStyle style,
                          final boolean setAsPreferred) {
        mEditStyleLauncher.launch(new EditStyleContract.Input(
                StyleViewModel.BKEY_ACTION_EDIT, style.getUuid(), setAsPreferred));
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
    private void onBookEditFinished(@Nullable final Bundle data) {
        if (data != null) {
            if (data.getBoolean(Entity.BKEY_DATA_MODIFIED, false)) {
                mVm.setForceRebuildInOnResume(true);
            }

            // If we got an id back, make any (potential) rebuild re-position to it.
            final long bookId = data.getLong(DBKey.PK_ID, 0);
            if (bookId > 0) {
                mVm.setDesiredCentralBookId(bookId);
            }
        }
    }

    /**
     * Called when the user has finished an Import.
     * <p>
     * This method is called from a ActivityResultContract after the result intent is parsed.
     * After this method is executed, the flow will take us to #onResume.
     *
     * @param importResults returned from the import
     */
    private void onImportFinished(@Nullable final ImportResults importResults) {
        if (importResults != null) {
            if (importResults.styles > 0) {
                // Force a refresh of the cached styles
                ServiceLocator.getInstance().getStyles().clearCache();
            }
            if (importResults.preferences > 0) {
                // Refresh the preferred bookshelf. This also refreshes its style.
                mVm.reloadSelectedBookshelf(this);
            }

            // styles, prefs, books, covers,... it all requires a rebuild.
            mVm.setForceRebuildInOnResume(true);
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

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        updateSyncMenuVisibility(global);
        mFabMenu.getItem(FAB_4_SEARCH_EXTERNAL_ID).setEnabled(
                EditBookExternalIdFragment.isShowTab(global));

        // Initialize/Update the list of bookshelves
        mVm.reloadBookshelfList(this);
        mBookshelfAdapter.notifyDataSetChanged();
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
                       + "| mVm.isBuilding()=" + mVm.isBuilding()
                       + "|called from:", new Throwable());
        }

        if (!mVm.isBuilding()) {
            mVb.progressCircle.show();
            // Invisible... theoretically this means the page should not re-layout
            mVb.list.setVisibility(View.INVISIBLE);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                final SimpleDateFormat dateFormat =
                        new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.getDefault());
                Debug.startMethodTracing("trace-" + dateFormat.format(new Date()));
            }
            // force the adapter to stop displaying by disabling its cursor.
            // DO NOT REMOVE THE ADAPTER FROM FROM THE VIEW;
            // i.e. do NOT call mVb.list.setAdapter(null)... crashes assured when doing so.
            if (mAdapter != null) {
                mAdapter.clearCursor();
            }
            mVm.buildBookList();
        }
    }

    /**
     * Called when the list build succeeded.
     *
     * @param message from the task; contains the (optional) target rows.
     */
    private void onBuildFinished(@NonNull final FinishedMessage<BoBTask.Outcome> message) {
        mVb.progressCircle.hide();
        if (message.isNewEvent()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.stopMethodTracing();
            }

            mVm.onBuildFinished(message);

            displayList(message.requireResult().targetRows);
        }
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    public void onBuildCancelled(@NonNull final FinishedMessage<BoBTask.Outcome> message) {
        mVb.progressCircle.hide();
        if (message.isNewEvent()) {
            mVm.onBuildCancelled(message);

            if (mVm.isListLoaded()) {
                displayList();
            } else {
                recoverAfterFailedBuild();
            }
        }
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    public void onBuildFailed(@NonNull final FinishedMessage<Exception> message) {
        mVb.progressCircle.hide();
        if (message.isNewEvent()) {
            mVm.onBuildFailed(message);

            if (mVm.isListLoaded()) {
                displayList();
            } else {
                recoverAfterFailedBuild();
            }
        }
    }

    private void recoverAfterFailedBuild() {
        // Something is REALLY BAD
        // This is usually (BUT NOT ALWAYS) due to the developer making an oopsie
        // with the Styles. i.e. the style used to build is very likely corrupt.
        // Another reason can be during development when the database structure
        // was changed...
        final ListStyle style = mVm.getCurrentStyle(this);
        // so we reset the style to recover.. and restarting the app will work.
        mVm.onStyleChanged(this, BuiltinStyle.DEFAULT_UUID);
        // but we STILL FORCE A CRASH, SO WE CAN COLLECT DEBUG INFORMATION!
        throw new IllegalStateException("Style=" + style);
    }

    /**
     * Expand/Collapse the given position.
     *
     * @param rowData            for the book/level at the given position
     * @param nextState          the required next state of this node
     * @param relativeChildLevel how many child levels below the node should be modified likewise
     */
    private void setNodeState(@NonNull final DataHolder rowData,
                              @BooklistNode.NextState final int nextState,
                              final int relativeChildLevel) {
        saveListPosition();

        final long nodeRowId = rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
        final BooklistNode node = mVm.setNode(nodeRowId, nextState, relativeChildLevel);

        displayList(node);
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
        if (mAdapter != null && mAdapter.getItemCount() > 0) {
            saveListPosition();
            mVm.expandAllNodes(topLevel, expand);
            displayList();
        }
    }

    /**
     * Display the list based on the given cursor, and scroll to the last saved position.
     */
    private void displayList() {
        if (createListAdapter(true) > 0) {
            mVb.list.post(() -> scrollToSavedPosition(true));
        }
    }

    /**
     * Display the list based on the given cursor, and scroll to the desired node.
     *
     * @param node to show
     */
    private void displayList(@NonNull final BooklistNode node) {
        if (createListAdapter(true) > 0) {
            mVb.list.post(() -> {
                scrollToSavedPosition(false);
                mVb.list.post(() -> scrollTo(node));
            });
        }
    }

    /**
     * Display the list based on the given cursor, and either scroll to the desired
     * target node(s) or, if none, to the last saved position.
     *
     * @param targetNodes (optional) to re-position to
     */
    private void displayList(@Nullable final List<BooklistNode> targetNodes) {
        if (createListAdapter(true) > 0) {
            if (targetNodes != null) {
                mVb.list.post(() -> {
                    scrollToSavedPosition(false);
                    final BooklistNode node;
                    if (targetNodes.size() == 1) {
                        node = targetNodes.get(0);
                    } else {
                        node = findBestNode(targetNodes);
                    }
                    mVb.list.post(() -> scrollTo(node));
                });
            } else {
                mVb.list.post(() -> scrollToSavedPosition(true));
            }
        }
    }

    /**
     * Find the node which is physically closest to the current visible position.
     *
     * @param targetNodes to select from
     *
     * @return 'best' node
     */
    @NonNull
    private BooklistNode findBestNode(@NonNull final List<BooklistNode> targetNodes) {
        // Position of the row in the (vertical) center of the screen
        final int center = (mLayoutManager.findLastVisibleItemPosition()
                            + mLayoutManager.findFirstVisibleItemPosition()) / 2;

        BooklistNode best = targetNodes.get(0);
        // distance from currently visible center row
        int distance = Math.abs(best.getAdapterPosition() - center);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetNodes.size(); i++) {
            final BooklistNode node = targetNodes.get(i);
            final int newDist = Math.abs(node.getAdapterPosition() - center);
            if (newDist < distance) {
                distance = newDist;
                best = node;
            }
        }
        return best;
    }

    /**
     * Preserve the {@link LinearLayoutManager#findFirstVisibleItemPosition()}
     * for the CURRENT bookshelf/style combination.
     * <ol>
     *     <li>The row number at the top of the screen.</li>
     *     <li>The pixel offset of that row from the top of the screen.</li>
     * </ol>
     * <p>
     */
    private void saveListPosition() {
        if (!isDestroyed()) {
            final int firstVisiblePos = mLayoutManager.findFirstVisibleItemPosition();
            if (firstVisiblePos == RecyclerView.NO_POSITION) {
                return;
            }
            mVm.saveListPosition(this, firstVisiblePos, getViewOffset());
        }
    }

    /**
     * Get the number of pixels offset for the first visible View, can be negative.
     *
     * @return pixels
     */
    private int getViewOffset() {
        // the list.getChildAt; not the layoutManager.getChildAt (not sure why...)
        final View topView = mVb.list.getChildAt(0);
        if (topView == null) {
            return 0;

        } else {
            // currently our padding is 0, but this is future-proof
            final int paddingTop = mVb.list.getPaddingTop();
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                    topView.getLayoutParams();
            return topView.getTop() - lp.topMargin - paddingTop;
        }
    }

    /**
     * Scroll the list to the position saved in {@link #saveListPosition}.
     * Saves the potentially changed position after the scrolling is done.
     *
     * @param save {@code true} if we should save the final position
     */
    private void scrollToSavedPosition(final boolean save) {
        Objects.requireNonNull(mAdapter, "mAdapter");

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            dbgDumpPositions("scrollToSavedListPosition", -666);
        }

        final Bookshelf bookshelf = mVm.getCurrentBookshelf();
        final int position = bookshelf.getFirstVisibleItemPosition();

        // sanity check
        if (position < 0) {
            mLayoutManager.scrollToPositionWithOffset(0, 0);

        } else if (position >= mAdapter.getItemCount()) {
            // the list is shorter than it used to be, just scroll to the end
            mLayoutManager.scrollToPosition(position);

        } else {
            mLayoutManager.scrollToPositionWithOffset(position,
                                                      bookshelf.getFirstVisibleItemViewOffset());
        }
        // after layout, save the final position
        if (save) {
            mVb.list.post(this::saveListPosition);
        }
    }

    /**
     * Scroll the given node into user view.
     *
     * @param node to scroll to
     */
    private void scrollTo(@NonNull final BooklistNode node) {
        Objects.requireNonNull(mAdapter, "mAdapter");

        final int firstVisiblePos = mLayoutManager.findFirstVisibleItemPosition();
        // sanity check, we should never get here
        if (firstVisiblePos == RecyclerView.NO_POSITION) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "scrollTo: empty list");
            }
            return;
        }

        // The mLayoutManager has the header at position 0, and the booklist rows starting at 1
        final int layoutPositionWanted = 1 + node.getAdapterPosition();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            dbgDumpPositions("*", layoutPositionWanted);
        }

        // Dev notes...
        // The recycler list will in fact extent at the top/bottom beyond the screen edge
        // It does this due to the CoordinatorLayout with the AppBar behaviour config.
        // We can't simply add some padding to the RV (i.e. ?attr/actionBarSize)
        // as that would initially show a good result, but as soon as we scroll
        // would show up as a blank bit at the bottom.
        // Other people have the same issue:
        // https://stackoverflow.com/questions/38073272
        //
        // findLastVisibleItemPosition will find the CORRECT position...
        // ... except that it will be outside/below the screen by some 2-4 lines
        // and hence in practice NOT visible. Same to a lesser extent for the top.
        //
        // The logic/math here for the top of the screen works well.
        // Handling the bottom is harder. It works good enough, but not perfect.
        // It depends on the fact if you just scrolled the page up or down, and then
        // expanded or collapsed the last row. There are more combinations... to many.
        final int lastVisiblePos = mLayoutManager.findLastVisibleItemPosition();

        // If the node is not in view, or at the edge, scroll it into view.
        if (layoutPositionWanted <= firstVisiblePos) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                dbgDumpPositions("<= firstVisiblePos", layoutPositionWanted);
            }
            mLayoutManager.scrollToPosition(layoutPositionWanted);

        } else if (layoutPositionWanted <= lastVisiblePos) {
            if (node.isExpanded()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                    dbgDumpPositions("< expanded", layoutPositionWanted);
                }
                mLayoutManager.scrollToPosition(layoutPositionWanted + 1);

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                    dbgDumpPositions("< collapsed", layoutPositionWanted);
                }
                mLayoutManager.scrollToPosition(layoutPositionWanted);
            }
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                dbgDumpPositions("> beyond bottom", layoutPositionWanted);
            }
            mLayoutManager.scrollToPosition(layoutPositionWanted);

        }

        // after layout, save the final position
        mVb.list.post(this::saveListPosition);
    }

    @SuppressLint("LogConditional")
    private void dbgDumpPositions(@NonNull final String method,
                                  final int pos) {
        Log.d(method, String.format(" |savedPosition= %4d"
                                    + " |firstVisiblePos= %4d"
                                    + " |lastVisiblePos= %4d"
                                    + " |pos= %4d",
                                    mVm.getCurrentBookshelf().getFirstVisibleItemPosition(),
                                    mLayoutManager.findFirstVisibleItemPosition(),
                                    mLayoutManager.findLastVisibleItemPosition(),
                                    pos));
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
        static void setResult(@NonNull final Fragment fragment,
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

    private static class HeaderViewHolder
            extends RecyclerView.ViewHolder {

        @NonNull
        BooksonbookshelfHeaderBinding mVb;

        HeaderViewHolder(@NonNull final BooksonbookshelfHeaderBinding vb) {
            super(vb.getRoot());
            mVb = vb;
        }
    }

    private class HeaderAdapter
            extends RecyclerView.Adapter<HeaderViewHolder> {

        HeaderAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public HeaderViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
            final BooksonbookshelfHeaderBinding vb = BooksonbookshelfHeaderBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new HeaderViewHolder(vb);
        }

        @Override
        public void onBindViewHolder(@NonNull final HeaderViewHolder holder,
                                     final int position) {
            final Context context = holder.itemView.getContext();
            String header;
            header = mVm.getHeaderStyleName(context);
            holder.mVb.styleName.setText(header);
            holder.mVb.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = mVm.getHeaderFilterText(context);
            holder.mVb.filterText.setText(header);
            holder.mVb.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = mVm.getHeaderBookCount(context);
            holder.mVb.bookCount.setText(header);
            holder.mVb.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }
}
