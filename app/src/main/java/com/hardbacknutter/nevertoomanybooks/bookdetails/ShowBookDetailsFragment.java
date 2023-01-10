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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooksOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

/**
 * This Fragment is always hosted inside another Fragment.
 * <ul>
 * <li>{@link ShowBookDetailsViewModel#isEmbedded()} == {@code false}
 *      ==> {@link ShowBookPagerFragment}</li>
 * <li>{@link ShowBookDetailsViewModel#isEmbedded()} == {@code true}
 *      ==> {@link BooksOnBookshelf}</li>
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
    static final String BKEY_EMBEDDED = TAG + ":emb";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] coverHandler = new CoverHandler[2];
    private ToolbarMenuProvider toolbarMenuProvider;
    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler calibreHandler;
    private ShowBookDetailsActivityViewModel aVm;
    private ShowBookDetailsViewModel vm;
    /** Callback - used when we're running inside another component; e.g. the BoB. */
    @Nullable
    private BookChangedListener bookChangedListener;

    /** User edits a book. */
    private final ActivityResultLauncher<Long> editBookLauncher = registerForActivityResult(
            new EditBookByIdContract(), o -> o.ifPresent(this::onBookEditFinished));
    /** User updates a book with internet data. */
    private final ActivityResultLauncher<Book> updateBookLauncher = registerForActivityResult(
            new UpdateSingleBookContract(), o -> o.ifPresent(this::onBookAutoUpdateFinished));

    /** Handle the edit-lender dialog. */
    private final EditLenderDialogFragment.Launcher editLenderLauncher =
            new EditLenderDialogFragment.Launcher() {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    // The db was already updated, just update the book
                    vm.reloadBook();

                    aVm.updateFragmentResult();

                    if (bookChangedListener != null) {
                        bookChangedListener.onBookUpdated(vm.getBook(), DBKey.LOANEE_NAME);
                    }
                }
            };

    /** Gives access to the ViewPager2. Will be {@code null} when we're in embedded mode. */
    @Nullable
    private ShowBookPagerViewModel pagerVm;

    @NonNull
    public static Fragment create(@IntRange(from = 1) final long bookId,
                                  @NonNull final String styleUuid,
                                  final boolean embedded) {
        final Fragment fragment = new ShowBookDetailsFragment();
        final Bundle args = new Bundle(3);
        args.putLong(DBKey.FK_BOOK, bookId);
        args.putString(Style.BKEY_UUID, styleUuid);
        args.putBoolean(BKEY_EMBEDDED, embedded);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (context instanceof BookChangedListener) {
            bookChangedListener = (BookChangedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();

        //noinspection ConstantConditions
        aVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        //noinspection ConstantConditions
        aVm.init(getContext(), args);

        vm = new ViewModelProvider(this).get(ShowBookDetailsViewModel.class);
        vm.init(getContext(), args, aVm.getStyle());

        if (!vm.isEmbedded()) {
            pagerVm = new ViewModelProvider(getActivity()).get(ShowBookPagerViewModel.class);
            pagerVm.init(args);
        }

        editLenderLauncher.registerForFragmentResult(getChildFragmentManager(), RK_EDIT_LENDER,
                                                     this);
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

        // update all Fields with their current View instances
        vm.getFields().forEach(field -> field.setParentView(view));

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        createCoverDelegates();

        // We must create them here, even if the Book has no matching sync id,
        // because the menu setup uses them before the Book is loaded.
        createSyncDelegates();

        if (pagerVm != null) {
            // hook up the ViewPager so we can update the screen title after a swipe
            pagerVm.onCurrentBookUpdated().observe(getViewLifecycleOwner(), bookId -> {
                // all fragments in the ViewPager will be called, so only update
                // the toolbar if OUR book is the current one
                final Book book = vm.getBook();
                if (book.getId() == bookId) {
                    updateToolbarTitle(book);
                }
            });
        }

        vm.onBookLoaded().observe(getViewLifecycleOwner(), this::onBindBook);

        final Field<Boolean, CheckBox> cbxRead = vm.requireField(R.id.read);
        cbxRead.requireView().setOnClickListener(v -> toggleReadStatus());
    }

    /**
     * Create the optional sync delegates.
     */
    private void createSyncDelegates() {

        if (SyncServer.CalibreCS.isEnabled()) {
            try {
                //noinspection ConstantConditions
                calibreHandler = new CalibreHandler(getContext(), this)
                        .setProgressFrame(getProgressFrame());
                calibreHandler.onViewCreated(this);
            } catch (@NonNull final CertificateException ignore) {
                //ignore; the user would already have been warned on the BoB screen
            }
        }

//        if (SyncServer.StripInfo.isEnabled()) {
//
//        }
    }

    private void createCoverDelegates() {
        //noinspection ConstantConditions
        final CircularProgressIndicator progressView =
                getView().findViewById(R.id.cover_operation_progress_bar);

        final Resources res = getResources();
        final TypedArray width = res.obtainTypedArray(R.array.cover_details_width);
        final TypedArray height = res.obtainTypedArray(R.array.cover_details_height);
        try {
            for (int cIdx = 0; cIdx < width.length(); cIdx++) {
                if (aVm.getStyle().isShowField(Style.Screen.Detail, FieldVisibility.COVER[cIdx])) {
                    final int maxWidth = width.getDimensionPixelSize(cIdx, 0);
                    final int maxHeight = height.getDimensionPixelSize(cIdx, 0);

                    coverHandler[cIdx] = new CoverHandler(this, cIdx, maxWidth, maxHeight)
                            .setBookSupplier(() -> vm.getBook())
                            .setProgressView(progressView)
                            .onFragmentViewCreated(this);
                }
            }
        } finally {
            width.recycle();
            height.recycle();
        }
    }

    /**
     * Called when the Book was updated with internet data.
     *
     * @param data details
     */
    private void onBookAutoUpdateFinished(@NonNull final UpdateBooksOutput data) {
        // only override if 'true'; i.e. if we got an id back
        if (data.getBookModified() > 0) {
            aVm.updateFragmentResult();
        }

        vm.reloadBook();

        if (bookChangedListener != null) {
            bookChangedListener.onBookUpdated(vm.getBook(), (String) null);
        }
    }

    /**
     * Called when the Book was edited.
     *
     * @param data details
     */
    private void onBookEditFinished(@NonNull final EditBookOutput data) {
        if (data.isModified()) {
            // needed when running inside the ViewPager
            aVm.updateFragmentResult();

            vm.reloadBook();

            // needed when running in embedded mode
            if (bookChangedListener != null) {
                bookChangedListener.onBookUpdated(vm.getBook(), (String) null);
            }
        }
    }

    public void reloadBook(final long bookId) {
        vm.reloadBook(bookId);
    }

    @Override
    public void reloadImage(@IntRange(from = 0, to = 1) final int cIdx) {
        //TODO: don't reload the whole book, just use coverHandler[cIdx].onBindView(...);
        vm.reloadBook();

        if (bookChangedListener != null) {
            bookChangedListener.onBookUpdated(vm.getBook(), (String) null);
        }
    }

    private void toggleReadStatus() {
        final Book book = vm.getBook();
        final boolean read = book.toggleRead();
        aVm.updateFragmentResult();

        vm.requireField(R.id.read).setValue(read);

        vm.getField(R.id.read_end).ifPresent(field -> {
            field.setValue(book.getString(DBKey.READ_END__DATE));
            //noinspection ConstantConditions
            field.setVisibility(getView(), true, false);
        });

        toolbarMenuProvider.updateMenuReadOptions(getToolbar().getMenu());

        if (bookChangedListener != null) {
            bookChangedListener.onBookUpdated(book, DBKey.READ__BOOL, DBKey.READ_END__DATE);
        }
    }

    private void updateToolbarTitle(@NonNull final Book book) {
        final Toolbar toolbar = getToolbar();
        //noinspection ConstantConditions
        toolbar.setTitle(Author.getLabel(getContext(), book.getAuthors()));

        String bookTitle = book.getTitle();
        if (BuildConfig.DEBUG /* always */) {
            bookTitle = "[" + book.getId() + "] " + bookTitle;
        }
        toolbar.setSubtitle(bookTitle);
    }

    // Dev. Note: this will get called FOR EACH fragment currently existing
    // in the ViewPager ... so ALSO for the fragments off-screen.
    // DO NOT use a LiveDataEvent !
    private void onBindBook(@NonNull final Book book) {
        // The menu is entirely dependent on the book we're displaying
        final Toolbar toolbar = getToolbar();
        if (toolbarMenuProvider != null) {
            toolbar.removeMenuProvider(toolbarMenuProvider);
        }
        toolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(toolbarMenuProvider, getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        if (!vm.isEmbedded()) {
            updateToolbarTitle(book);
        }

        final List<Field<?, ? extends View>> fields = vm.getFields();

        // do NOT call notifyIfChanged, as this is the initial load
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(book));

        bindCoverImages();
        bindLoanee(book);
        bindToc(book);

        //noinspection ConstantConditions
        fields.forEach(field -> field.setVisibility(getView(), true, false));

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
                if (coverHandler[cIdx] != null) {
                    coverHandler[cIdx].onBindView(view);
                    coverHandler[cIdx].attachOnClickListeners(getChildFragmentManager(), view);
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
        if (aVm.getStyle().isShowField(Style.Screen.List, DBKey.LOANEE_NAME)) {
            final Optional<String> loanee =
                    book.getLoanee().map(s -> getString(R.string.lbl_lend_out_to_name, s));

            if (loanee.isPresent()) {
                lendTo.setText(loanee.get());
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
                lblAnthologyOrCollection.setText(R.string.lbl_book_type_collection);
                break;

            case Anthology:
                lblAnthologyOrCollection.setVisibility(View.VISIBLE);
                lblAnthologyOrCollection.setText(R.string.lbl_book_type_anthology);
                break;

            case Book:
            default:
                lblAnthologyOrCollection.setVisibility(View.GONE);
                break;
        }

        final Button btnShowToc = getView().findViewById(R.id.btn_show_toc);
        final FragmentContainerView tocFrame = getView().findViewById(R.id.toc_frame);
        if (aVm.getStyle().isShowField(Style.Screen.List, DBKey.FK_TOC_ENTRY)) {
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
     * @param showTocBtn the "Show TOC" button view
     * @param book       to load
     */
    private void bindTocButton(@NonNull final Button showTocBtn,
                               @NonNull final Book book) {

        if (book.getToc().isEmpty()) {
            showTocBtn.setVisibility(View.GONE);
        } else {
            showTocBtn.setVisibility(View.VISIBLE);
            showTocBtn.setOnClickListener(v -> {
                final Fragment fragment = TocFragment.create(book, false, aVm.getStyle());
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
     * @param tocFrame the TOC frame view
     * @param book     to load
     */
    private void bindTocFrame(@NonNull final FragmentContainerView tocFrame,
                              @NonNull final Book book) {
        final List<TocEntry> tocList = book.getToc();
        if (tocList.isEmpty()) {
            tocFrame.setVisibility(View.GONE);
        } else {
            tocFrame.setVisibility(View.VISIBLE);
            final FragmentManager fm = getChildFragmentManager();

            Fragment fragment = fm.findFragmentByTag(TocFragment.TAG);
            if (fragment == null) {
                fragment = TocFragment.create(book, true, aVm.getStyle());
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .replace(R.id.toc_frame, fragment, TocFragment.TAG)
                  .commit();
            } else {
                ((TocFragment) fragment).reload(book);
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

            menu.findItem(R.id.MENU_SYNC_LIST_WITH_DETAILS).setVisible(vm.isEmbedded());

            if (vm.isEmbedded()) {
                MenuCompat.setGroupDividerEnabled(menu, true);
            } else {
                //noinspection ConstantConditions
                MenuUtils.setupSearchActionView(getActivity(), inflater, menu);
            }

            if (calibreHandler != null) {
                calibreHandler.onCreateMenu(menu, inflater);
            }

            //noinspection ConstantConditions
            aVm.getMenuHandlers().forEach(h -> h.onCreateMenu(getContext(), menu, inflater));
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            updateMenuReadOptions(menu);
            updateMenuLendingOptions(menu);

            final Book book = vm.getBook();

            if (calibreHandler != null) {
                //noinspection ConstantConditions
                calibreHandler.onPrepareMenu(getContext(), menu, book);
            }

            aVm.getMenuHandlers().forEach(h -> h.onPrepareMenu(menu, book));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final Context context = getContext();
            final Book book = vm.getBook();

            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_BOOK_EDIT) {
                editBookLauncher.launch(book.getId());
                return true;

            } else if (itemId == R.id.MENU_BOOK_DELETE) {
                deleteBook(book);
                return true;

            } else if (itemId == R.id.MENU_BOOK_SET_READ || itemId == R.id.MENU_BOOK_SET_UNREAD) {
                toggleReadStatus();
                return true;

            } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
                editLenderLauncher.launch(book);
                return true;

            } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
                deleteLoanee(book);
                return true;

            } else if (itemId == R.id.MENU_SHARE) {
                //noinspection ConstantConditions
                startActivity(book.getShareIntent(context, aVm.getStyle()));
                return true;

            } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
                // Must use the Activity fm as the current fragment could be hosted by
                // ShowBookPagerFragment or embedded inside the BoB
                //noinspection ConstantConditions
                getActivity().getSupportFragmentManager()
                             .beginTransaction()
                             .setReorderingAllowed(true)
                             .addToBackStack(CalibrePreferencesFragment.TAG)
                             .replace(R.id.main_fragment,
                                      new CalibrePreferencesFragment(),
                                      CalibrePreferencesFragment.TAG)
                             .commit();
                return true;

            } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                updateBookLauncher.launch(book);
                return true;

            } else if (itemId == R.id.MENU_SYNC_LIST_WITH_DETAILS) {
                if (bookChangedListener != null) {
                    bookChangedListener.onSyncBook(book.getId());
                }
                return true;
            }

            //noinspection ConstantConditions
            if (calibreHandler != null
                && calibreHandler.onMenuItemSelected(context, menuItem, book)) {
                return true;
            }

            //noinspection ConstantConditions
            return aVm.getMenuHandlers()
                      .stream()
                      .anyMatch(h -> h.onMenuItemSelected(context, menuItem, book));
        }

        private void deleteLoanee(@NonNull final Book book) {
            vm.deleteLoan();
            aVm.updateFragmentResult();

            bindLoanee(book);
            if (vm.isEmbedded()) {
                updateMenuLendingOptions(getToolbar().getMenu());
            }

            if (bookChangedListener != null) {
                bookChangedListener.onBookUpdated(book, DBKey.LOANEE_NAME);
            }
        }

        private void deleteBook(@NonNull final Book book) {
            final String title = book.getTitle();
            final List<Author> authors = book.getAuthors();
            //noinspection ConstantConditions
            StandardDialogs.deleteBook(getContext(), title, authors, () -> {
                final long bookIdDeleted = book.getId();
                vm.deleteBook();
                aVm.updateFragmentResult();

                if (bookChangedListener != null) {
                    // explicitly tell the listener WHICH book we deleted.
                    bookChangedListener.onBookDeleted(bookIdDeleted);

                } else {
                    // set 0 as the repositionToBookId
                    final Intent resultIntent = EditBookOutput
                            .createResult(0, true);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            });
        }

        private void updateMenuReadOptions(@NonNull final Menu menu) {
            final boolean isRead = vm.getBook().getBoolean(DBKey.READ__BOOL);
            menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
            menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);
        }

        private void updateMenuLendingOptions(@NonNull final Menu menu) {
            // Always check LOANEE_NAME usage independent from the style in use.
            if (aVm.getStyle().isShowField(Style.Screen.List, DBKey.LOANEE_NAME)) {
                final boolean isLendOut = vm.getBook().getLoanee().isPresent();
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(!isLendOut);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(isLendOut);
            } else {
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(false);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(false);
            }
        }
    }
}
