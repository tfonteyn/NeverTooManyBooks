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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.MenuHelper;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This Fragment is always hosted inside another Fragment.
 * <ul>
 * <li>{@link #mEmbedded} == {@code false} ==> {@link ShowBookPagerFragment}</li>
 * <li>{@link #mEmbedded} == {@code true} ==> {@link BooksOnBookshelf}</li>
 * </ul>
 * <p>
 * Hence there is NO OnBackPressedCallback in this Fragment.
 */
public class ShowBookDetailsFragment
        extends BaseFragment
        implements CoverHandler.CoverHandlerOwner {

    public static final String TAG = "ShowBookDetailsFragment";

    /**
     * Whether to run this fragment in embedded mode (i.e. inside a frame on the BoB screen).
     * We could (should?) use a boolean resource in "sw800-land" instead.
     */
    private static final String BKEY_EMBEDDED = TAG + ":emb";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];
    private ToolbarMenuProvider mToolbarMenuProvider;
    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler mCalibreHandler;
    private ShowBookDetailsActivityViewModel mAVm;
    private ShowBookDetailsViewModel mVm;
    /** Callback - used when we're running inside another component; e.g. the BoB. */
    @Nullable
    private BookChangedListener mBookChangedListener;

    /** User edits a book. */
    private final ActivityResultLauncher<Long> mEditBookLauncher = registerForActivityResult(
            new EditBookByIdContract(), this::onBookUpdated);
    /** User updates a book with internet data. */
    private final ActivityResultLauncher<Book> mUpdateBookLauncher = registerForActivityResult(
            new UpdateSingleBookContract(), this::onBookUpdated);

    /** Handle the edit-lender dialog. */
    private final EditLenderDialogFragment.Launcher mEditLenderLauncher =
            new EditLenderDialogFragment.Launcher(RK_EDIT_LENDER) {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    // The db was already updated, just update the book
                    mVm.reloadBook();

                    mAVm.updateFragmentResult();

                    if (mBookChangedListener != null) {
                        mBookChangedListener.onBookUpdated(mVm.getBook(), DBKey.KEY_LOANEE);
                    }
                }
            };

    private boolean mEmbedded;

    @NonNull
    public static Fragment create(@IntRange(from = 1) final long bookId,
                                  @NonNull final String styleUuid,
                                  final boolean embedded) {
        final Fragment fragment = new ShowBookDetailsFragment();
        final Bundle args = new Bundle(3);
        args.putLong(DBKey.FK_BOOK, bookId);
        args.putString(ListStyle.BKEY_UUID, styleUuid);
        args.putBoolean(BKEY_EMBEDDED, embedded);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (context instanceof BookChangedListener) {
            mBookChangedListener = (BookChangedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();

        // mEmbedded = getResources().getBoolean(R.bool.book_details_embedded)
        mEmbedded = args.getBoolean(BKEY_EMBEDDED, false);

        //noinspection ConstantConditions
        mAVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        mAVm.init(getActivity(), args);

        mVm = new ViewModelProvider(this).get(ShowBookDetailsViewModel.class);
        mVm.init(args);

        mEditLenderLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();
        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);

        // update all Fields with their current View instances
        mAVm.getFields().forEach(field -> field.setParentView(global, view));

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        createCoverDelegates(global);

        // We must create them here, even if the Book has no matching sync id,
        // because the menu setup uses them before the Book is loaded.
        createSyncDelegates(global);

        mVm.onBookLoaded().observe(getViewLifecycleOwner(), this::onBindBook);

        final Field<Boolean, CheckBox> cbxRead = mAVm.requireField(R.id.read);
        cbxRead.requireView().setOnClickListener(v -> toggleReadStatus(mVm.getBook()));
    }

    /**
     * Create the optional sync delegates.
     *
     * @param global Global preferences
     */
    private void createSyncDelegates(@NonNull final SharedPreferences global) {

        if (SyncServer.CalibreCS.isEnabled(global)) {
            try {
                //noinspection ConstantConditions
                mCalibreHandler = new CalibreHandler(getContext(), this)
                        .setProgressFrame(getProgressFrame());
                mCalibreHandler.onViewCreated(this);
            } catch (@NonNull final SSLException | CertificateException ignore) {
                // ignore
            }
        }

//        if (SyncServer.StripInfo.isEnabled(global)) {
//
//        }
    }

    private void createCoverDelegates(@NonNull final SharedPreferences global) {
        //noinspection ConstantConditions
        final CircularProgressIndicator progressView =
                getView().findViewById(R.id.cover_operation_progress_bar);

        final Resources res = getResources();
        final TypedArray width = res.obtainTypedArray(R.array.cover_details_width);
        final TypedArray height = res.obtainTypedArray(R.array.cover_details_height);
        try {
            for (int cIdx = 0; cIdx < width.length(); cIdx++) {
                if (mAVm.isCoverUsed(global, cIdx)) {
                    final int maxWidth = width.getDimensionPixelSize(cIdx, 0);
                    final int maxHeight = height.getDimensionPixelSize(cIdx, 0);

                    mCoverHandler[cIdx] = new CoverHandler(this, cIdx, maxWidth, maxHeight)
                            .setBookSupplier(() -> mVm.getBook())
                            .setProgressView(progressView)
                            .onFragmentViewCreated(this);
                }
            }
        } finally {
            width.recycle();
            height.recycle();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        bindBook(mVm.getBook());
    }

    /**
     * Called when the Book was changed somehow.
     *
     * @param data (optional) details
     */
    private void onBookUpdated(@Nullable final EditBookOutput data) {
        if (data != null) {
            // only override if 'true'
            if (data.modified) {
                mAVm.updateFragmentResult();
            }
        }
        mVm.reloadBook();

        if (mBookChangedListener != null) {
            mBookChangedListener.onBookUpdated(mVm.getBook(), null);
        }
    }

    public void reloadBook(final long bookId) {
        mVm.reloadBook(bookId);
    }

    @Override
    public void reloadImage(@IntRange(from = 0, to = 1) final int cIdx) {
        //TODO: don't reload the whole book, just use mCoverHandler[cIdx].onBindView(...);
        mVm.reloadBook();

        if (mBookChangedListener != null) {
            mBookChangedListener.onBookUpdated(mVm.getBook(), null);
        }
    }

    private void toggleReadStatus(@NonNull final Book book) {
        final boolean read = mVm.toggleRead();
        mAVm.updateFragmentResult();

        mAVm.requireField(R.id.read).setValue(read);
        if (mEmbedded) {
            mToolbarMenuProvider.updateMenuReadOptions(getToolbar().getMenu());
        }

        if (mBookChangedListener != null) {
            mBookChangedListener.onBookUpdated(book, DBKey.BOOL_READ);
        }
    }

    private void onBindBook(@NonNull final LiveDataEvent<Book> message) {
        message.getData().ifPresent(this::bindBook);
    }

    private void bindBook(@NonNull final Book book) {
        // The menu is entirely dependent on the book we're displaying
        final Toolbar toolbar = getToolbar();
        if (mToolbarMenuProvider != null) {
            toolbar.removeMenuProvider(mToolbarMenuProvider);
        }
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        if (!mEmbedded) {
            //noinspection ConstantConditions
            String authors = Author.getCondensedNames(getContext(), book.getAuthors());

            if (BuildConfig.DEBUG /* always */) {
                authors = "[" + book.getId() + "] " + authors;
            }
            toolbar.setTitle(authors);
            toolbar.setSubtitle(book.getString(DBKey.KEY_TITLE));
        }

        final List<Field<?, ? extends View>> fields = mAVm.getFields();

        // do NOT call onChanged, as this is the initial load
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(book));

        bindCoverImages();
        bindLoanee(book);
        bindToc(book);

        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        //noinspection ConstantConditions
        fields.forEach(field -> field.setVisibility(global, getView(), true, false));

        // Hide the 'Edition' label if neither edition chips or print-run fields are shown
        setSectionVisibility(R.id.lbl_edition,
                             R.id.edition,
                             R.id.print_run);

        // Hide the 'Publication' label if none of the publishing fields are shown.
        setSectionVisibility(R.id.lbl_publication,
                             R.id.publisher,
                             R.id.date_published,
                             R.id.price_listed,
                             R.id.format,
                             R.id.color,
                             R.id.language,
                             R.id.pages);

        // All views should now have proper visibility set, so fix their focus order.
        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    private void bindCoverImages() {
        final TypedArray coverResIds = getResources().obtainTypedArray(R.array.cover_images);
        try {
            for (int cIdx = 0; cIdx < coverResIds.length(); cIdx++) {
                //noinspection ConstantConditions
                final ImageView view = getView().findViewById(coverResIds.getResourceId(cIdx, 0));
                if (mCoverHandler[cIdx] != null) {
                    mCoverHandler[cIdx].onBindView(view);
                    mCoverHandler[cIdx].attachOnClickListeners(getChildFragmentManager(), view);
                } else if (view != null) {
                    view.setVisibility(View.GONE);
                }
            }
        } finally {
            coverResIds.recycle();
        }
    }

    /**
     * Inflates 'lend-to' field showing a person the book was lend to.
     *
     * @param book to load
     */
    private void bindLoanee(@NonNull final Book book) {
        //noinspection ConstantConditions
        final TextView lendTo = getView().findViewById(R.id.lend_to);
        //noinspection ConstantConditions
        if (mAVm.useLoanee(getContext())) {
            final String loanee = book.getLoanee()
                                      .map(s -> getString(R.string.lbl_lend_out_to_name, s))
                                      .orElse(null);
            if (loanee != null) {
                lendTo.setText(loanee);
                lendTo.setVisibility(View.VISIBLE);
                return;
            }
        }
        lendTo.setVisibility(View.GONE);
    }

    private void bindToc(@NonNull final Book book) {
        //noinspection ConstantConditions
        final TextView lblAnthologyOrCollection = getView().findViewById(R.id.lbl_anthology);
        switch (book.getContentType()) {
            case Collection:
                lblAnthologyOrCollection.setVisibility(View.VISIBLE);
                lblAnthologyOrCollection.setText(R.string.lbl_collection);
                break;

            case Anthology:
                lblAnthologyOrCollection.setVisibility(View.VISIBLE);
                lblAnthologyOrCollection.setText(R.string.lbl_anthology);
                break;

            case Book:
            default:
                lblAnthologyOrCollection.setVisibility(View.GONE);
                break;
        }

        final Button btnShowToc = getView().findViewById(R.id.btn_show_toc);
        final FragmentContainerView tocFrame = getView().findViewById(R.id.toc_frame);
        //noinspection ConstantConditions
        if (mAVm.useToc(getContext())) {
            if (btnShowToc != null) {
                bindTocButton(btnShowToc, book);
            } else if (tocFrame != null) {
                bindTocFrame(tocFrame, book);
            }
        } else {
            if (btnShowToc != null) {
                btnShowToc.setVisibility(View.GONE);
            } else if (tocFrame != null) {
                tocFrame.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Smaller displays have a button to show the TOC in the current Activity.
     *
     * @param book to load
     */
    private void bindTocButton(@NonNull final Button showTocBtn,
                               @NonNull final Book book) {

        final ArrayList<TocEntry> tocList = book.getToc();
        if (tocList.isEmpty()) {
            showTocBtn.setVisibility(View.GONE);
        } else {
            showTocBtn.setVisibility(View.VISIBLE);
            showTocBtn.setOnClickListener(v -> {
                final Fragment fragment = TocFragment.create(tocList, book);
                // yes, it must be the Activity FragmentManager,
                // as that is where the R.id.main_fragment View is located.
                //noinspection ConstantConditions
                final FragmentManager fm = getActivity().getSupportFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(TocFragment.TAG)
                  .replace(R.id.main_fragment, fragment, TocFragment.TAG)
                  .commit();
            });
        }
    }

    /**
     * Larger displays show the TOC in a child Fragment of this one.
     *
     * @param book to load
     */
    private void bindTocFrame(@NonNull final FragmentContainerView tocFrame,
                              @NonNull final Book book) {
        final ArrayList<TocEntry> tocList = book.getToc();
        if (tocList.isEmpty()) {
            tocFrame.setVisibility(View.GONE);
        } else {
            tocFrame.setVisibility(View.VISIBLE);
            final FragmentManager fm = getChildFragmentManager();

            Fragment fragment = fm.findFragmentByTag(TocFragment.TAG);
            if (fragment == null) {
                fragment = TocFragment.createEmbedded(tocList);
                fm.beginTransaction()
                  .replace(R.id.toc_frame, fragment, TocFragment.TAG)
                  .commit();
            } else {
                ((TocFragment) fragment).reload(tocList);
            }
        }
    }

    /**
     * If all field Views are View.GONE, set the section View to View.GONE as well.
     * Otherwise, set the section View to View.VISIBLE.
     *
     * @param sectionLabel field to set
     * @param fieldViews   to check
     */
    private void setSectionVisibility(final int sectionLabel,
                                      @NonNull final Integer... fieldViews) {

        final View parent = getView();
        //noinspection ConstantConditions
        final boolean visible = Arrays.stream(fieldViews)
                                      .map(parent::findViewById)
                                      .map(v -> ((View) v).getVisibility())
                                      .anyMatch(vis -> vis != View.GONE);
        parent.findViewById(sectionLabel).setVisibility(visible ? View.VISIBLE : View.GONE);
    }


    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater inflater) {
            inflater.inflate(R.menu.book, menu);
            // duplicating is not supported from inside this fragment
            menu.findItem(R.id.MENU_BOOK_DUPLICATE).setVisible(false);

            menu.findItem(R.id.MENU_SYNC_LIST_WITH_DETAILS).setVisible(mEmbedded);

            if (mEmbedded) {
                MenuCompat.setGroupDividerEnabled(menu, true);
            } else {
                //noinspection ConstantConditions
                MenuHelper.setupSearchActionView(getActivity(), inflater, menu);
            }

            if (mCalibreHandler != null) {
                mCalibreHandler.onCreateMenu(menu, inflater);
            }

            mAVm.getMenuHandlers().forEach(h -> h.onCreateMenu(menu, inflater));

            onPrepareMenu(menu);
        }

        private void onPrepareMenu(@NonNull final Menu menu) {
            updateMenuReadOptions(menu);
            updateMenuLendingOptions(menu);

            final Book book = mVm.getBook();

            if (mCalibreHandler != null) {
                //noinspection ConstantConditions
                mCalibreHandler.onPrepareMenu(getContext(), menu, book);
            }

            mAVm.getMenuHandlers().forEach(h -> h.onPrepareMenu(menu, book));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final Context context = getContext();
            final Book book = mVm.getBook();

            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_BOOK_EDIT) {
                mEditBookLauncher.launch(book.getId());
                return true;

            } else if (itemId == R.id.MENU_BOOK_DELETE) {
                deleteBook(book);
                return true;

            } else if (itemId == R.id.MENU_BOOK_SET_READ || itemId == R.id.MENU_BOOK_SET_UNREAD) {
                toggleReadStatus(book);
                return true;

            } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
                mEditLenderLauncher.launch(book);
                return true;

            } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
                deleteLoanee(book);
                return true;

            } else if (itemId == R.id.MENU_SHARE) {
                //noinspection ConstantConditions
                startActivity(book.getShareIntent(context));
                return true;

            } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
                //noinspection ConstantConditions
                final Intent intent = SettingsHostActivity
                        .createIntent(context, CalibrePreferencesFragment.class);
                startActivity(intent);
                return true;

            } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                mUpdateBookLauncher.launch(book);
                return true;

            } else if (itemId == R.id.MENU_SYNC_LIST_WITH_DETAILS) {
                if (mBookChangedListener != null) {
                    mBookChangedListener.onSyncBook(book.getId());
                }
                return true;
            }

            //noinspection ConstantConditions
            if (mCalibreHandler != null
                && mCalibreHandler.onMenuItemSelected(context, menuItem, book)) {
                return true;
            }

            //noinspection ConstantConditions
            return mAVm.getMenuHandlers()
                       .stream()
                       .anyMatch(h -> h.onMenuItemSelected(context, menuItem, book));
        }

        private void deleteLoanee(@NonNull final Book book) {
            mVm.deleteLoan();
            mAVm.updateFragmentResult();

            bindLoanee(book);
            if (mEmbedded) {
                updateMenuLendingOptions(getToolbar().getMenu());
            }

            if (mBookChangedListener != null) {
                mBookChangedListener.onBookUpdated(book, DBKey.KEY_LOANEE);
            }
        }

        private void deleteBook(@NonNull final Book book) {
            final String title = book.getTitle();
            final List<Author> authors = book.getAuthors();
            //noinspection ConstantConditions
            StandardDialogs.deleteBook(getContext(), title, authors, () -> {
                mVm.deleteBook();
                mAVm.updateFragmentResult();

                if (mBookChangedListener != null) {
                    mBookChangedListener.onBookDeleted(book.getId());

                } else {
                    final Intent resultIntent = EditBookOutput
                            .createResultIntent(0, true);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            });
        }

        private void updateMenuReadOptions(@NonNull final Menu menu) {
            final boolean isRead = mVm.getBook().getBoolean(DBKey.BOOL_READ);
            menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
            menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);
        }

        private void updateMenuLendingOptions(@NonNull final Menu menu) {
            // Always check KEY_LOANEE usage independent from the style in use.
            //noinspection ConstantConditions
            if (mAVm.useLoanee(getContext())) {
                final boolean isLendOut = mVm.getBook().getLoanee().isPresent();
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(!isLendOut);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(isLendOut);
            } else {
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(false);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(false);
            }
        }
    }
}
