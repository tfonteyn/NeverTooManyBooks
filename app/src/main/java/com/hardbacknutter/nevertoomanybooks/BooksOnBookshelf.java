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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchFtsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsFragment;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
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
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
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
        extends BaseActivity
        implements BookChangedListener {

    private static final int FAB_4_SEARCH_EXTERNAL_ID = 4;
    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";
    /** {@link FragmentResultListener} request key. */
    private static final String RK_STYLE_PICKER = TAG + ":rk:" + StylePickerDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;

    /** Make a backup. */
    private final ActivityResultLauncher<Void> mExportLauncher =
            registerForActivityResult(new ExportContract(), success -> {
            });

    /** Display a Book. */
    private final ActivityResultLauncher<ShowBookPagerContract.Input> mDisplayBookLauncher =
            registerForActivityResult(new ShowBookPagerContract(), this::onBookEditFinished);

    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> mStripInfoSyncLauncher;

    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> mCalibreSyncLauncher;

    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler mCalibreHandler;

    /** Multi-type adapter to manage list connection to cursor. */
    @Nullable
    private BooklistAdapter mAdapter;

    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel mVm;

    /** Do an import. */
    private final ActivityResultLauncher<Void> mImportLauncher =
            registerForActivityResult(new ImportContract(), this::onImportFinished);
    /** Manage the list of (preferred) styles. */
    private final ActivityResultLauncher<String> mEditStylesLauncher =
            registerForActivityResult(new PreferredStylesContract(), data -> {
                if (data != null) {
                    // we get the UUID for the selected style back.
                    if (data.uuid != null) {
                        mVm.onStyleChanged(this, data.uuid);
                    }

                    // This is independent from the above style having been modified ot not.
                    if (data.isModified) {
                        mVm.setForceRebuildInOnResume(true);
                    }
                }
            });

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

    private ToolbarMenuProvider mToolbarMenuProvider;
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
                        onRowChanged(DBKey.FK_BOOKSHELF, bookshelfId);
                    }
                }
            };

    /** Listener for the Bookshelf Spinner. */
    private final SpinnerInteractionListener mOnBookshelfSelectionChanged =
            new SpinnerInteractionListener() {
                @Override
                public void onItemSelected(final long id) {
                    if (id != mVm.getCurrentBookshelf().getId()) {
                        saveListPosition();
                        mVm.setCurrentBookshelf(BooksOnBookshelf.this, id);
                        buildBookList();
                    }
                }
            };

    /** React to row changes made. */
    private final RowChangedListener mRowChangedListener = this::onRowChanged;

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
                    onBookUpdated(mVm.getBook(bookId), DBKey.KEY_LOANEE);
                }
            };
    private final BooklistAdapter.OnRowClickedListener mOnRowClickedListener =
            new BooklistAdapter.OnRowClickedListener() {

                /**
                 * User clicked a row.
                 * <ul>
                 *      <li>Book: open the details page.</li>
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

                    if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        // It's a book, open the details page.
                        final long bookId = rowData.getLong(DBKey.FK_BOOK);
                        // store the id as the current 'central' book for repositioning
                        mVm.setCurrentCenteredBookId(bookId);

                        if (hasEmbeddedDetailsFrame()) {
                            //  On larger screens, opens the book details fragment embedded.
                            openEmbeddedBookDetails(bookId);
                        } else {
                            //  On small screens, opens a ViewPager with the book details
                            //  and swipe prev/next functionality.
                            mDisplayBookLauncher.launch(new ShowBookPagerContract.Input(
                                    bookId,
                                    mVm.getStyle(BooksOnBookshelf.this).getUuid(),
                                    mVm.getBookNavigationTableName(),
                                    rowData.getLong(DBKey.PK_ID)));
                        }
                    } else {
                        // it's a level, expand/collapse.
                        setNodeState(rowData, BooklistNode.NextState.Toggle, 1);
                    }
                }

                @Override
                public boolean onItemLongClick(@NonNull final View v,
                                               final int position) {
                    return onCreateContextMenu(v, position);
                }
            };

    /**
     * The adapter used to fill the Bookshelf selector.
     */
    private ExtArrayAdapter<Bookshelf> mBookshelfAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        createFragmentResultListeners();
        createViewModel();
        createSyncDelegates(global);
        createHandlers(global);

        initNavDrawer();
        initToolbar();

        createBookshelfSpinner();
        // setup the list related stuff; the actual list data is generated in onResume
        createBooklistView(global);

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

    private void initToolbar() {
        setNavIcon();

        mVb.toolbar.setNavigationOnClickListener(v -> {
            if (isRootActivity()) {
                mVb.drawerLayout.openDrawer(GravityCompat.START);
            } else {
                onBackPressed();
            }
        });

        mToolbarMenuProvider = new ToolbarMenuProvider();
        mVb.toolbar.addMenuProvider(mToolbarMenuProvider, this);
    }

    private void setNavIcon() {
        if (isRootActivity()) {
            mVb.toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24);
        } else {
            mVb.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        }
    }

    boolean isRootActivity() {
        return isTaskRoot() && mVm.getSearchCriteria().isEmpty();
    }

    private void createFragmentResultListeners() {
        final FragmentManager fm = getSupportFragmentManager();

        fm.setFragmentResultListener(RowChangedListener.REQUEST_KEY, this, mRowChangedListener);

        mEditBookshelfLauncher.registerForFragmentResult(fm, this);
        mEditLenderLauncher.registerForFragmentResult(fm, this);
        mOnStylePickerLauncher.registerForFragmentResult(fm, this);
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

        // Reminder: this method cannot be called from onResume... registerForActivityResult
        // can only be called from onCreate

        if (SyncServer.CalibreCS.isEnabled(global)) {
            if (mCalibreSyncLauncher == null) {
                mCalibreSyncLauncher = registerForActivityResult(
                        new CalibreSyncContract(), result -> {
                            // If we imported anything at all... rebuild
                            if (result == CalibreSyncContract.RESULT_READ_DONE) {
                                mVm.setForceRebuildInOnResume(true);
                            }
                        });
            }
        }

        if (SyncServer.StripInfo.isEnabled(global)) {
            if (mStripInfoSyncLauncher == null) {
                mStripInfoSyncLauncher = registerForActivityResult(
                        new StripInfoSyncContract(), result -> {
                            // If we imported anything at all... rebuild
                            if (result == StripInfoSyncContract.RESULT_READ_DONE) {
                                mVm.setForceRebuildInOnResume(true);
                            }
                        });
            }
        }
    }

    /**
     * Create the (optional) handlers.
     *
     * @param global Global preferences
     */
    private void createHandlers(@NonNull final SharedPreferences global) {
        if (mCalibreHandler == null && SyncServer.CalibreCS.isEnabled(global)) {
            try {
                mCalibreHandler = new CalibreHandler(this, this)
                        .setProgressFrame(findViewById(R.id.progress_frame));

                mCalibreHandler.onViewCreated(this, mVb.getRoot());
            } catch (@NonNull final SSLException | CertificateException ignore) {
                // ignore
            }
        }
    }

    private void createBooklistView(@NonNull final SharedPreferences global) {
        //noinspection ConstantConditions
        mLayoutManager = (LinearLayoutManager) mVb.content.list.getLayoutManager();
        mVb.content.list.addItemDecoration(new TopLevelItemDecoration(this));

        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(global);
        FastScroller.attach(mVb.content.list, overlayType);

        // Number of views to cache offscreen arbitrarily set to 20; the default is 2.
        mVb.content.list.setItemViewCacheSize(20);
        mVb.content.list.setDrawingCacheEnabled(true);
        mVb.content.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
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
    private int createListAdapter(final boolean display) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "createListAdapter|display=" + display, new Throwable());
        }

        if (display) {
            mAdapter = new BooklistAdapter(this);
            // install single and long-click listeners
            mAdapter.setOnRowClickedListener(mOnRowClickedListener);
            // hookup the cursor
            mAdapter.setCursor(this, mVm.getNewListCursor(), mVm.getStyle(this));

            // Combine the adapters for the list header and the actual list
            final ConcatAdapter concatAdapter = new ConcatAdapter(
                    new ConcatAdapter.Config.Builder()
                            .setIsolateViewTypes(true)
                            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                            .build(),
                    new HeaderAdapter(this), mAdapter);

            mVb.content.list.setAdapter(concatAdapter);

            // make the list view visible!
            mVb.content.list.setVisibility(View.VISIBLE);

            return mAdapter.getItemCount();

        } else {
            mVb.content.list.setVisibility(View.GONE);
            return 0;
        }
    }

    /**
     * Listener for clicks on the list.
     */

    private void createBookshelfSpinner() {
        // remove the default title to make space for the spinner.
        setTitle("");

        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        mBookshelfAdapter = new EntityArrayAdapter<>(this, mVm.getBookshelfList());

        mVb.bookshelfSpinner.setAdapter(mBookshelfAdapter);
        mOnBookshelfSelectionChanged.attach(mVb.bookshelfSpinner);
    }

    private void createFabMenu(@NonNull final SharedPreferences global) {
        mFabMenu = new FabMenu(mVb.fab, mVb.fabOverlay,
                               mVb.fab0ScanBarcode,
                               mVb.fab1SearchIsbn,
                               mVb.fab2SearchText,
                               mVb.fab3AddManually,
                               mVb.fab4SearchExternalId);

        mFabMenu.attach(mVb.content.list);
        mFabMenu.setOnClickListener(view -> onFabMenuItemSelected(view.getId()));
        mFabMenu.getItem(FAB_4_SEARCH_EXTERNAL_ID)
                .setEnabled(EditBookExternalIdFragment.isShowTab(global));
    }

    /**
     * Show or hide the synchronization menu.
     */
    private void updateSyncMenuVisibility(@NonNull final SharedPreferences global) {
        final boolean enable =
                (SyncServer.CalibreCS.isEnabled(global) && mCalibreSyncLauncher != null)
                ||
                (SyncServer.StripInfo.isEnabled(global) && mStripInfoSyncLauncher != null);

        //noinspection ConstantConditions
        getNavigationMenuItem(R.id.SUBMENU_SYNC).setVisible(enable);
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
        mVm.getSearchCriteria().setFtsKeywords(query);
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
            mFtsSearchLauncher.launch(mVm.getSearchCriteria());
            return true;

        } else if (itemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // overridden, so we can pass the current bookshelf id.
            mManageBookshelvesLauncher.launch(mVm.getCurrentBookshelf().getId());
            return true;

        } else if (itemId == R.id.MENU_MANAGE_LIST_STYLES) {
            mEditStylesLauncher.launch(mVm.getStyle(this).getUuid());
            return true;

        } else if (itemId == R.id.MENU_FILE_IMPORT) {
            mImportLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_FILE_EXPORT) {
            mExportLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_SYNC_CALIBRE && mCalibreSyncLauncher != null) {
            mCalibreSyncLauncher.launch(null);
            return false;

        } else if (itemId == R.id.MENU_SYNC_STRIP_INFO && mStripInfoSyncLauncher != null) {
            mStripInfoSyncLauncher.launch(null);
            return false;
        }

        return super.onNavigationItemSelected(menuItem);
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

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        updateSyncMenuVisibility(global);
        mFabMenu.getItem(FAB_4_SEARCH_EXTERNAL_ID)
                .setEnabled(EditBookExternalIdFragment.isShowTab(global));

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
        final Cursor cursor = mAdapter.getCursor();
        // Move the cursor, so we can read the data for this row.
        // Paranoia: if the user can click it, then this move should be fine.
        if (!cursor.moveToPosition(position)) {
            return false;
        }

        final DataHolder rowData = new CursorRow(cursor);

        final ExtPopupMenu contextMenu = new ExtPopupMenu(this)
                .setGroupDividerEnabled();
        final Menu menu = contextMenu.getMenu();

        final int rowGroupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.book, menu);
                if (mCalibreHandler != null) {
                    mCalibreHandler.onCreateMenu(menu, inflater);
                }
                mVm.getViewBookHandler().onCreateMenu(menu, inflater);
                mVm.getAmazonHandler().onCreateMenu(menu, inflater);

                final SharedPreferences global = PreferenceManager
                        .getDefaultSharedPreferences(this);

                final boolean isRead = rowData.getBoolean(DBKey.BOOL_READ);
                menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

                // specifically check KEY_LOANEE independent from the style in use.
                final boolean useLending = DBKey.isUsed(global, DBKey.KEY_LOANEE);
                final boolean isAvailable = mVm.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                if (mCalibreHandler != null) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    mCalibreHandler.onPrepareMenu(this, menu, book);
                }

                mVm.getViewBookHandler().onPrepareMenu(menu, rowData);
                mVm.getAmazonHandler().onPrepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.author, menu);
                mVm.getAmazonHandler().onCreateMenu(menu, inflater);

                final boolean complete = rowData.getBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                mVm.getAmazonHandler().onPrepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBKey.FK_SERIES) != 0) {
                    final MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.series, menu);
                    mVm.getAmazonHandler().onCreateMenu(menu, inflater);

                    final boolean complete = rowData.getBoolean(DBKey.BOOL_SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    mVm.getAmazonHandler().onPrepareMenu(menu, rowData);
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

        // forms its own group
        menu.add(R.id.MENU_NEXT_MISSING_COVER, R.id.MENU_NEXT_MISSING_COVER, menuOrder++,
                 R.string.lbl_next_book_without_cover)
            .setIcon(R.drawable.ic_baseline_broken_image_24);

        // if it's a level, add the expand option
        if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) != BooklistGroup.BOOK) {
            //noinspection UnusedAssignment
            menu.add(R.id.MENU_GROUP_BOB_EXPANSION, R.id.MENU_LEVEL_EXPAND, menuOrder++,
                     R.string.lbl_level_expand)
                .setIcon(R.drawable.ic_baseline_unfold_more_24);
        }

        // If we actually have a menu, show it.
        if (menu.size() > 0) {
            // we have a menu to show, set the title according to the level.
            final int level = rowData.getInt(DBKey.KEY_BL_NODE_LEVEL);
            contextMenu.setTitle(mAdapter.getLevelText(position, level));

            if (menu.size() < 5) {
                // small menu, show it anchored to the row
                contextMenu.showAsDropDown(v, menuItem -> onMenuItemSelected(menuItem, position));

            } else if (hasEmbeddedDetailsFrame()) {
                contextMenu.show(v, Gravity.START,
                                 menuItem -> onMenuItemSelected(menuItem, position));
            } else {
                contextMenu.show(v, Gravity.CENTER,
                                 menuItem -> onMenuItemSelected(menuItem, position));
            }
            return true;
        }

        return false;
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

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
                    onBookDeleted(bookId);
                }
            });
            return true;

        } else if (itemId == R.id.MENU_BOOK_DUPLICATE) {
            final Book book = DataHolderUtils.requireBook(rowData);
            mDuplicateLauncher.launch(book.duplicate());
            return true;

        } else if (itemId == R.id.MENU_BOOK_SET_READ
                   || itemId == R.id.MENU_BOOK_SET_UNREAD) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // toggle the read status
            final boolean status = !rowData.getBoolean(DBKey.BOOL_READ);
            if (mVm.setBookRead(bookId, status)) {
                onBookUpdated(mVm.getBook(bookId), DBKey.BOOL_READ);
            }
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
            final Intent intent = SettingsHostActivity
                    .createIntent(this, CalibrePreferencesFragment.class);
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
            onBookUpdated(mVm.getBook(bookId), DBKey.KEY_LOANEE);
            return true;

            /* ********************************************************************************** */

        } else if (itemId == R.id.MENU_SHARE) {
            final Book book = DataHolderUtils.requireBook(rowData);
            startActivity(book.getShareIntent(this));
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                final Book book = DataHolderUtils.requireBook(rowData);
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
            final Series series = DataHolderUtils.requireSeries(rowData);
            EditSeriesDialogFragment.launch(getSupportFragmentManager(), series);
            return true;

        } else if (itemId == R.id.MENU_SERIES_SET_COMPLETE
                   || itemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
            final long seriesId = rowData.getLong(DBKey.FK_SERIES);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.BOOL_SERIES_IS_COMPLETE);
            if (mVm.setSeriesComplete(seriesId, status)) {
                onRowChanged(DBKey.FK_SERIES, seriesId);
            }
            return true;

        } else if (itemId == R.id.MENU_SERIES_DELETE) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            StandardDialogs.deleteSeries(this, series, () -> {
                mVm.delete(this, series);
                onRowChanged(DBKey.FK_SERIES, series.getId());
            });
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_AUTHOR_WORKS) {
            mAuthorWorksLauncher.launch(new AuthorWorksContract.Input(
                    rowData.getLong(DBKey.FK_AUTHOR),
                    mVm.getCurrentBookshelf().getId(),
                    mVm.getStyle(this).getUuid()));
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_EDIT) {
            final Author author = DataHolderUtils.requireAuthor(rowData);
            EditAuthorDialogFragment.launch(getSupportFragmentManager(), author);
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_SET_COMPLETE
                   || itemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
            final long authorId = rowData.getLong(DBKey.FK_AUTHOR);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE);
            if (mVm.setAuthorComplete(authorId, status)) {
                onRowChanged(DBKey.FK_AUTHOR, authorId);
            }
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_PUBLISHER_EDIT) {
            final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
            EditPublisherDialogFragment.launch(getSupportFragmentManager(), publisher);
            return true;

        } else if (itemId == R.id.MENU_PUBLISHER_DELETE) {
            final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
            StandardDialogs.deletePublisher(this, publisher, () -> {
                mVm.delete(this, publisher);
                onRowChanged(DBKey.FK_PUBLISHER, publisher.getId());
            });

            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_BOOKSHELF_EDIT) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            mEditBookshelfLauncher.launch(bookshelf);
            return true;

        } else if (itemId == R.id.MENU_BOOKSHELF_DELETE) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            StandardDialogs.deleteBookshelf(this, bookshelf, () -> {
                mVm.delete(bookshelf);
                onRowChanged(DBKey.FK_BOOKSHELF, bookshelf.getId());
            });
            return true;

            /* ********************************************************************************** */
        } else if (itemId == R.id.MENU_FORMAT_EDIT) {
            EditFormatDialogFragment.launch(getSupportFragmentManager(),
                                            rowData.getString(DBKey.KEY_FORMAT));
            return true;

        } else if (itemId == R.id.MENU_COLOR_EDIT) {
            EditColorDialogFragment.launch(getSupportFragmentManager(),
                                           rowData.getString(DBKey.KEY_COLOR));
            return true;

        } else if (itemId == R.id.MENU_GENRE_EDIT) {
            EditGenreDialogFragment.launch(getSupportFragmentManager(),
                                           rowData.getString(DBKey.KEY_GENRE));
            return true;

        } else if (itemId == R.id.MENU_LANGUAGE_EDIT) {
            EditLanguageDialogFragment.launch(getSupportFragmentManager(),
                                              this, rowData.getString(DBKey.KEY_LANGUAGE)
                                             );
            return true;

        } else if (itemId == R.id.MENU_LOCATION_EDIT) {
            EditLocationDialogFragment.launch(getSupportFragmentManager(),
                                              rowData.getString(DBKey.KEY_LOCATION));
            return true;

        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            setNodeState(rowData, BooklistNode.NextState.Expand,
                         mVm.getStyle(this).getGroups().size());
            return true;

        } else if (itemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
            final BooklistNode node = mVm.getNextBookWithoutCover(nodeRowId);
            if (node != null) {
                displayList(node);
            }
            return true;
        }

        if (mCalibreHandler != null
            && mCalibreHandler.onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }

        if (mVm.getAmazonHandler().onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }

        if (mVm.getViewBookHandler().onMenuItemSelected(this, menuItem, rowData)) {
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

    @SuppressWarnings("SameParameterValue")
    private void showNavigationSubMenu(@IdRes final int anchorMenuItemId,
                                       @NonNull final MenuItem menuItem,
                                       @MenuRes final int menuRes) {

        final View anchor = getNavigationMenuItemView(anchorMenuItemId);

        final ExtPopupMenu popupMenu = new ExtPopupMenu(this)
                .inflate(menuRes);
        final Menu menu = popupMenu.getMenu();
        if (menuItem.getItemId() == R.id.SUBMENU_SYNC) {
            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);
            menu.findItem(R.id.MENU_SYNC_CALIBRE)
                .setVisible(SyncServer.CalibreCS.isEnabled(global)
                            && mCalibreSyncLauncher != null);

            menu.findItem(R.id.MENU_SYNC_STRIP_INFO)
                .setVisible(SyncServer.StripInfo.isEnabled(global)
                            && mStripInfoSyncLauncher != null);
        }

        popupMenu.setTitle(menuItem.getTitle())
                 .showAsDropDown(anchor, this::onNavigationItemSelected);
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

        final View anchor = mLayoutManager.findViewByPosition(position);

        final String title = getString(R.string.menu_update_books);

        //noinspection ConstantConditions
        new ExtPopupMenu(this)
                .inflate(R.menu.update_books)
                .setTitle(title)
                .setMessage(message)
                .showAsDropDown(anchor, menuItem -> onMenuItemSelected(menuItem, position));
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
        mEditStyleLauncher.launch(EditStyleContract.edit(style, setAsPreferred));
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
        // ENHANCE: remove the row without a rebuild but this could quickly become complex...
        // e.g. if there is(was) only a single book on the level above
        saveListPosition();
        buildBookList();
    }

    @Override
    public void onSyncBook(final long bookId) {
        final List<BooklistNode> all = mVm.getVisibleBookNodes(bookId);
        scrollTo(findBestNode(all));
    }

    /**
     * Receive notifications that a Book was updated.
     * <p>
     * For a limited set of keys, we directly update the list table which is very fast.
     * <p>
     * Other keys, or full books, will always trigger a list rebuild.
     *
     * @param book the book that was changed,
     *             or {@code null} to indicate multiple books were potentially changed.
     * @param key  the item that was changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    @Override
    public void onBookUpdated(@Nullable final Book book,
                              @Nullable final String key) {

        // Reminder: the actual Book table (and/or relations) are ALREADY UPDATED.
        // The only thing we are updating here is the temporary BookList table
        // and the displayed data
        int[] positions = null;

        if (DBKey.BOOL_READ.equals(key)) {
            Objects.requireNonNull(book);
            positions = mVm.onBookRead(book.getId(), book.getBoolean(DBKey.BOOL_READ));

        } else if (DBKey.KEY_LOANEE.equals(key)) {
            Objects.requireNonNull(book);
            positions = mVm.onBookLend(book.getId(), book.getLoanee());

        } else {
            // ENHANCE: update the modified row without a rebuild.
            saveListPosition();
            buildBookList();
        }

        // Refresh the list data for the given positions only.
        if (positions != null) {
            // Yes, requery() is deprecated;
            // but check BooklistCursor were we do the right thing.
            //noinspection ConstantConditions,deprecation
            mAdapter.getCursor().requery();

            for (final int pos : positions) {
                mAdapter.notifyItemChanged(pos);
            }
        }
    }

    /**
     * This method is called from a ActivityResultContract after the result intent is parsed.
     * After this method is executed, the flow will take us to #onResume.
     *
     * @param data returned from the view/edit Activity
     */
    private void onBookEditFinished(@Nullable final EditBookOutput data) {
        if (data != null) {
            if (data.modified) {
                mVm.setForceRebuildInOnResume(true);
            }

            // If we got an id back, make any (potential) rebuild re-position to it.
            if (data.bookId > 0) {
                mVm.setCurrentCenteredBookId(data.bookId);
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
            mVb.content.list.setVisibility(View.INVISIBLE);

            // If the book details frame is present, remove it
            final Fragment fragment = getEmbeddedDetailsFrame();
            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(fragment)
                        .commit();
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                final SimpleDateFormat dateFormat =
                        new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.getDefault());
                //noinspection UseOfObsoleteDateTimeApi
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
    private void onBuildFinished(
            @NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        mVb.progressCircle.hide();

        message.getData().map(TaskResult::requireResult).ifPresent(result -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.stopMethodTracing();
            }
            mVm.onBuildFinished(result);
            displayList(result.getTargetNodes());
        });
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    private void onBuildCancelled(
            @NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        mVb.progressCircle.hide();

        message.getData().ifPresent(data -> {
            mVm.onBuildCancelled();

            if (mVm.isListLoaded()) {
                displayList();
            } else {
                recoverAfterFailedBuild();
            }
        });
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    private void onBuildFailed(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        mVb.progressCircle.hide();
        message.getData().ifPresent(data -> {
            mVm.onBuildFailed();

            if (mVm.isListLoaded()) {
                displayList();
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
        final ListStyle style = mVm.getStyle(this);
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
                              @NonNull final BooklistNode.NextState nextState,
                              final int relativeChildLevel) {
        saveListPosition();

        final long nodeRowId = rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
        final BooklistNode node = mVm.setNode(nodeRowId, nextState, relativeChildLevel);

        displayList(node);
    }

    /**
     * Display the list based on the given cursor, and scroll to the last saved position.
     */
    private void displayList() {
        if (createListAdapter(true) > 0) {
            mVb.content.list.post(() -> {
                scrollToSavedPosition();
                mVb.content.list.post(this::saveListPosition);
            });
        }
    }

    /**
     * Display the list based on the given cursor, and scroll to the desired node.
     *
     * @param node to show
     */
    private void displayList(@NonNull final BooklistNode node) {
        final List<BooklistNode> list = new ArrayList<>();
        list.add(node);
        displayList(list);
    }

    /**
     * Display the list based on the given cursor, and either scroll to the desired
     * target node(s) or, if none, to the last saved position.
     *
     * @param targetNodes (optional) to re-position to
     */
    private void displayList(@NonNull final List<BooklistNode> targetNodes) {
        if (createListAdapter(true) > 0) {

            // we can get here after a style change (or as initial build obviously)
            // so make sure the menu reflects the style
            mToolbarMenuProvider.onPrepareMenu(mVb.toolbar.getMenu());

            // Notice the "post()" usage. This is needed because the list
            // will potentially have moved after each step.
            mVb.content.list.post(() -> {
                // scroll to the saved position - this should get us close to where we need to be
                scrollToSavedPosition();

                if (targetNodes.isEmpty()) {
                    mVb.content.list.post(() -> {
                        // We're on the precise position
                        saveListPosition();
                        // Now show the book details if we can.
                        final long bookId = mVm.getCurrentCenteredBookId();
                        if (bookId != 0 && hasEmbeddedDetailsFrame()) {
                            // We know exactly where we want to be,
                            // do NOT reset the stored book id positioning
                            openEmbeddedBookDetails(bookId);
                        } else {
                            // We didn't have visible embedded book detail,
                            // so make sure to disabled stored book id positioning
                            mVm.setCurrentCenteredBookId(0);
                        }
                    });
                } else {
                    // We have target nodes, so make sure to disabled stored book id positioning
                    mVm.setCurrentCenteredBookId(0);

                    // find the closest node showing the book, and scroll to it
                    final BooklistNode node = findBestNode(targetNodes);
                    mVb.content.list.post(() -> {
                        scrollTo(node);
                        // after layout, save the final position
                        mVb.content.list.post(() -> {
                            saveListPosition();
                            // and lastly, show the book details if we can.
                            final long bookId = node.getBookId();
                            if (bookId != 0 && hasEmbeddedDetailsFrame()) {
                                openEmbeddedBookDetails(bookId);
                            }
                        });
                    });
                }
            });
        }
    }

    @Nullable
    private Fragment getEmbeddedDetailsFrame() {
        return mVb.content.detailsFrame == null ? null : mVb.content.detailsFrame.getFragment();
    }

    private boolean hasEmbeddedDetailsFrame() {
        return mVb.content.detailsFrame != null;
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
        if (targetNodes.size() == 1) {
            return targetNodes.get(0);
        }

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
        final View topView = mVb.content.list.getChildAt(0);
        if (topView == null) {
            return 0;

        } else {
            // currently our padding is 0, but this is future-proof
            final int paddingTop = mVb.content.list.getPaddingTop();
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                    topView.getLayoutParams();
            return topView.getTop() - lp.topMargin - paddingTop;
        }
    }

    /**
     * Scroll the list to the position saved in {@link #saveListPosition}.
     * Saves the potentially changed position after the scrolling is done.
     */
    private void scrollToSavedPosition() {
        Objects.requireNonNull(mAdapter, "mAdapter");

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
        final int position = 1 + node.getAdapterPosition();

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

        //URGENT: more fine-tuning needed
        // If the node is not in view, or at the edge, scroll it into view.
        if ((firstVisiblePos < position) && (position <= lastVisiblePos)
            && node.isExpanded()) {
            mLayoutManager.scrollToPosition(position + 1);
        } else {
            mLayoutManager.scrollToPosition(position);
        }
    }

    private void openEmbeddedBookDetails(final long bookId) {
        final FragmentManager fm = getSupportFragmentManager();

        Fragment fragment = fm.findFragmentByTag(ShowBookDetailsFragment.TAG);
        if (fragment == null) {
            fragment = ShowBookDetailsFragment.create(
                    bookId, mVm.getStyle(this).getUuid(), true);
            fm.beginTransaction()
              .replace(R.id.details_frame, fragment, ShowBookDetailsFragment.TAG)
              .commit();
        } else {
            ((ShowBookDetailsFragment) fragment).reloadBook(bookId);
        }
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

    private static class HeaderViewHolder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView styleName;
        final TextView filterText;
        final TextView bookCount;

        HeaderViewHolder(@NonNull final View itemView) {
            super(itemView);
            styleName = itemView.findViewById(R.id.style_name);
            filterText = itemView.findViewById(R.id.filter_text);
            bookCount = itemView.findViewById(R.id.book_count);
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.bob, menu);
            MenuHelper.setupSearchActionView(BooksOnBookshelf.this, menu);

            onPrepareMenu(menu);
        }

        private void onPrepareMenu(@NonNull final Menu menu) {
            final boolean showPreferredOption =
                    mVm.getStyle(BooksOnBookshelf.this).getTopLevel() > 1;
            menu.findItem(R.id.MENU_LEVEL_PREFERRED_EXPANSION).setVisible(showPreferredOption);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            mFabMenu.hideMenu();

            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_STYLE_PICKER) {
                mOnStylePickerLauncher.launch(mVm.getStyle(BooksOnBookshelf.this), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_PREFERRED_EXPANSION) {
                expandAllNodes(mVm.getStyle(BooksOnBookshelf.this).getTopLevel(), false);
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
                //FIXME: currently disabled in the menu xml file.
                mUpdateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        mVm.getCurrentBookIdList(), null, null));
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
            if (mAdapter != null && mAdapter.getItemCount() > 0) {
                saveListPosition();
                mVm.expandAllNodes(topLevel, expand);
                displayList();
            }
        }
    }

    private class HeaderAdapter
            extends RecyclerView.Adapter<HeaderViewHolder> {

        @NonNull
        private final LayoutInflater mInflater;

        HeaderAdapter(@NonNull final Context context) {
            mInflater = LayoutInflater.from(context);
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public HeaderViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
            final View view = mInflater.inflate(R.layout.booksonbookshelf_header, parent, false);
            return new HeaderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final HeaderViewHolder holder,
                                     final int position) {
            final Context context = holder.itemView.getContext();
            String header;
            header = mVm.getHeaderStyleName(context);
            holder.styleName.setText(header);
            holder.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = mVm.getHeaderFilterText(context);
            holder.filterText.setText(header);
            holder.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = mVm.getHeaderBookCount(context);
            holder.bookCount.setText(header);
            holder.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }
}
