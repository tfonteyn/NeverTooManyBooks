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
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadStatusFragmentFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;

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
        extends BaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "ShowBookDetailsFragment";

    /**
     * Whether to run this fragment in embedded mode (i.e. inside a frame on the BoB screen).
     * We could (should?) use a boolean resource in "sw800-land" instead.
     */
    static final String BKEY_EMBEDDED = TAG + ":emb";

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] coverHandler = new CoverHandler[2];
    private ToolbarMenuProvider toolbarMenuProvider;
    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler calibreHandler;
    private ShowBookDetailsActivityViewModel aVm;
    private ShowBookDetailsViewModel vm;

    /** Gives access to the ViewPager2. Will be {@code null} when we're in embedded mode. */
    @Nullable
    private ShowBookPagerViewModel pagerVm;
    private RealNumberParser realNumberParser;

    /**
     * Callback - used when we're running inside another component;
     * e.g. when running on a tablet (or other bigger screen) the BoB is showing
     * the details as an embedded fragment.
     */
    @Nullable
    private BookChangedListener bookChangedListener;

    /** User edits a book. */
    private final ActivityResultLauncher<EditBookContract.Input> editBookLauncher =
            registerForActivityResult(
                    new EditBookContract(), o -> o.ifPresent(data -> {
                        if (data.isModified()) {
                            onBookEditFinished((String) null);
                        }
                    }));

    /** User updates a book with internet data. */
    private final ActivityResultLauncher<Book> updateBookLauncher =
            registerForActivityResult(
                    new UpdateSingleBookContract(), o -> o.ifPresent(data -> {
                        if (data.isModified()) {
                            onBookEditFinished((String) null);
                        }
                    }));

    /** Handle the edit-lender dialog. */
    private EditLenderLauncher editLenderLauncher;

    /**
     * Constructor.
     *
     * @param bookId    to open
     * @param styleUuid to use
     * @param embedded  flag, whether we're running embedded in a BoB activity
     *
     * @return new instance
     */
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

        //noinspection DataFlowIssue
        aVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        aVm.init(args);

        // MUST be in the Fragment scope, as we'll have multiple copies of this Fragment
        // object in the {@link ShowBookPagerFragment} and each showing a different book obv.
        vm = new ViewModelProvider(this).get(ShowBookDetailsViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), args, aVm.getStyle());

        if (!vm.isEmbedded()) {
            pagerVm = new ViewModelProvider(getActivity()).get(ShowBookPagerViewModel.class);
            pagerVm.init(args);
        }

        createFragmentResultListeners();
    }

    private void createFragmentResultListeners() {
        final FragmentManager fm = getChildFragmentManager();

        editLenderLauncher = new EditLenderLauncher(
                (bookId, loanee) -> onBookEditFinished(DBKey.LOANEE_NAME));


        editLenderLauncher.registerForFragmentResult(fm, this);
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
        // Effectively disable edge-to-edge for the root view.
        InsetsListenerBuilder.create()
                             .padding()
                             .sides(Side.Left, Side.Right, Side.Bottom)
                             .applyTo(view);

        final List<Locale> locales = LocaleListUtils.asList(view.getContext());
        realNumberParser = new RealNumberParser(locales);

        // update all Fields with their current View instances
        vm.getFields().forEach(field -> field.setParentView(view));

        // Popup the search widget when the user starts to type.
        //noinspection DataFlowIssue
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        createCoverDelegates();

        // We must create them here, even if the Book has no matching sync id,
        // because the menu setup uses them before the Book is loaded.
        createSyncDelegates();

        if (pagerVm != null) {
            // hook up the ViewPager so we can react to swipes
            pagerVm.onCurrentBookUpdated().observe(getViewLifecycleOwner(),
                                                   bookId -> vm.updateUIAfterPagerUpdate(bookId));
        }

        vm.onBookLoaded().observe(getViewLifecycleOwner(), this::onBindBook);
        vm.onUpdateToolbar().observe(getViewLifecycleOwner(), this::onUpdateToolbar);
        vm.onReadStatusChanged().observe(getViewLifecycleOwner(), aVoid -> onReadStatusChanged());
    }

    /**
     * Create the optional sync delegates.
     */
    private void createSyncDelegates() {
        //noinspection DataFlowIssue
        if (SyncServer.CalibreCS.isEnabled(getContext())) {
            try {
                calibreHandler = new CalibreHandler(getContext(), this)
                        .setProgressFrame(getProgressFrame());
                calibreHandler.onViewCreated(this);
            } catch (@NonNull final CertificateException ignore) {
                //ignore; the user would already have been warned on the BoB screen
            }
        }

        //  if (SyncServer.StripInfo.isEnabled()) {
        //
        //  }
    }

    private void createCoverDelegates() {
        //noinspection DataFlowIssue
        final CircularProgressIndicator progressView =
                getView().findViewById(R.id.cover_operation_progress_bar);

        final Context context = getContext();
        //noinspection DataFlowIssue
        final Resources res = context.getResources();
        final TypedArray width = res.obtainTypedArray(R.array.cover_details_max_width);
        try {
            final Style style = aVm.getStyle();
            for (int cIdx = 0; cIdx < width.length(); cIdx++) {
                if (style.isShowField(FieldVisibility.Screen.Detail, DBKey.COVER[cIdx])) {
                    final int maxWidth = width.getDimensionPixelSize(cIdx, 0);
                    final int maxHeight = (int) (maxWidth / CoverScale.HW_RATIO);

                    coverHandler[cIdx] = new CoverHandler(this, cIdx, this::reloadImage,
                                                          maxWidth, maxHeight)
                            .setBookSupplier(() -> vm.getBook())
                            .setProgressView(progressView)
                            .onFragmentViewCreated(this);
                }
            }
        } finally {
            width.recycle();
        }
    }

    /**
     * Entry point after the current Book was edited; either manually by the user,
     * or automatically with an internet update action.
     *
     * @param keys optional list of keys which were updated.
     *             {@code null} to indicate the entire Book was potentially updated.
     */
    private void onBookEditFinished(@Nullable final String... keys) {
        // needed when running inside the ViewPager to update the activity result data
        aVm.setDataModified();

        vm.displayBook();

        // needed when running in embedded mode to update the BoB list
        if (bookChangedListener != null) {
            bookChangedListener.onBookUpdated(vm.getBook(), keys);
        }
    }

    /**
     * Entry point for {@link BooksOnBookshelf} when running in embedded mode.
     * Called when the user taps a book in the BoB list.
     *
     * @param bookId to display
     */
    public void displayBook(final long bookId) {
        vm.displayBook(bookId);
    }

    /**
     * Callback passed to the {@link CoverHandler}; will be called after changing a cover image.
     *
     * @param cIdx 0..n image index
     */
    private void reloadImage(@IntRange(from = 0, to = 1) final int cIdx) {
        // needed when running inside the ViewPager to update the activity result data
        aVm.setDataModified();

        // don't reload the whole book, just rebind the images
        bindCoverImages();

        // needed when running in embedded mode to update the BoB list
        if (bookChangedListener != null && cIdx == 0) {
            bookChangedListener.onBookUpdated(vm.getBook(), DBKey.COVER[0]);
        }
    }

    private void onReadStatusChanged() {
        aVm.setDataModified();

        final Book book = vm.getBook();

        // Refresh the read_end value displayed
        vm.getField(R.id.read_end).ifPresent(field -> {
            field.setValue(book.getString(DBKey.READ_END__DATE));
            //noinspection DataFlowIssue
            field.setVisibility(getView(), true, false);
        });

        if (bookChangedListener != null) {
            bookChangedListener.onBookUpdated(book, DBKey.READ__BOOL, DBKey.READ_PROGRESS,
                                              DBKey.READ_END__DATE);
        }
    }

    private void onUpdateToolbar(@NonNull final Book book) {
        final Toolbar toolbar = getToolbar();

        //noinspection DataFlowIssue
        toolbar.setTitle(Author.getLabel(getContext(), book.getAuthors()));

        String bookTitle = book.getTitle();
        if (BuildConfig.DEBUG /* always */) {
            bookTitle = "[" + book.getId() + "] " + bookTitle;
        }
        toolbar.setSubtitle(bookTitle);
    }

    // Dev. note: this will get called FOR EACH fragment currently existing
    // in the ViewPager ... so ALSO for the fragments off-screen.
    private void onBindBook(@NonNull final Book book) {
        // The menu is entirely dependent on the book we're displaying,
        // so we need to remove the old menu if present,
        // and construct a new menu for each book we're binding.
        final Toolbar toolbar = getToolbar();
        if (toolbarMenuProvider != null) {
            toolbar.removeMenuProvider(toolbarMenuProvider);
        }
        toolbarMenuProvider = new ToolbarMenuProvider();
        // add it, but ONLY display it when THIS fragment is resumed.
        toolbar.addMenuProvider(toolbarMenuProvider, getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        final List<Field<?, ? extends View>> fields = vm.getFields();

        final Context context = getContext();
        // do NOT call notifyIfChanged, as this is the initial load
        //noinspection DataFlowIssue
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(context, book, realNumberParser));

        ReadStatusFragmentFactory.bind(getChildFragmentManager(), R.id.fragment_read,
                                       aVm.getStyle(),
                                       ShowBookDetailsViewModel.class);
        bindCoverImages();
        bindLoanee(book);
        bindToc(book);

        final View parentView = getView();
        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(parentView, true, false));

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
        //noinspection DataFlowIssue
        ViewFocusOrder.fix(parentView);
    }

    private void bindCoverImages() {
        final View parentView = getView();

        final TypedArray coverResIds = getResources().obtainTypedArray(R.array.cover_images);
        try {
            for (int cIdx = 0; cIdx < coverResIds.length(); cIdx++) {
                //noinspection DataFlowIssue
                final ImageView view = parentView.findViewById(coverResIds.getResourceId(cIdx, 0));
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
        //noinspection DataFlowIssue
        final TextView lendTo = getView().findViewById(R.id.lend_to);
        // Use the global setting! The user may disabled the field on the list screen,
        // but if lending is enabled globally, we MUST display the status on the details screen.
        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.LOANEE_NAME)) {
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
        final View parentView = getView();

        //noinspection DataFlowIssue
        final TextView lblAnthologyOrCollection = parentView.findViewById(R.id.lbl_anthology);
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

        final Button btnShowToc = parentView.findViewById(R.id.btn_show_toc);
        final FragmentContainerView tocFrame = parentView.findViewById(R.id.toc_frame);
        if (aVm.getStyle().isShowField(FieldVisibility.Screen.List, DBKey.FK_TOC_ENTRY)) {
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
                // This also means the TocFragment is replacing the ViewPager!
                //noinspection DataFlowIssue
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
                //URGENT: this is a hack.... it works but.... it's bound to break some day.
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

        final View parentView = getView();
        //noinspection DataFlowIssue
        final boolean visible = Arrays.stream(fieldViews)
                                      .map(parentView::findViewById)
                                      .map(v -> ((View) v).getVisibility())
                                      .anyMatch(vis -> vis != View.GONE);
        parentView.findViewById(sectionLabel).setVisibility(visible ? View.VISIBLE : View.GONE);
    }


    private final class ToolbarMenuProvider
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
                //noinspection DataFlowIssue
                MenuUtils.setupSearchActionView(getActivity(), inflater, menu);
            }

            if (calibreHandler != null) {
                calibreHandler.onCreateMenu(menu, inflater);
            }

            final Context context = getContext();
            //noinspection DataFlowIssue
            aVm.getMenuHandlers().forEach(h -> h.onCreateMenu(context, menu, inflater));
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final Book book = vm.getBook();

            final boolean isRead = book.isRead();
            menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
            menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

            // Always check LOANEE_NAME usage independent from the style in use.
            if (ServiceLocator.getInstance().isFieldEnabled(DBKey.LOANEE_NAME)) {
                final boolean isLendOut = book.getLoanee().isPresent();
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(!isLendOut);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(isLendOut);
            } else {
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(false);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(false);
            }

            final Context context = getContext();

            if (calibreHandler != null) {
                //noinspection DataFlowIssue
                calibreHandler.onPrepareMenu(context, menu, book);
            }

            //noinspection DataFlowIssue
            aVm.getMenuHandlers().forEach(h -> h.onPrepareMenu(context, menu, book));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final Context context = getContext();
            final Book book = vm.getBook();

            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_BOOK_EDIT) {
                editBookLauncher.launch(new EditBookContract.Input(book.getId(),
                                                                   aVm.getStyle()));
                return true;

            } else if (menuItemId == R.id.MENU_BOOK_DELETE) {
                deleteBook(book);
                return true;

            } else if (menuItemId == R.id.MENU_BOOK_SET_READ
                       || menuItemId == R.id.MENU_BOOK_SET_UNREAD) {
                // toggle the status
                vm.setReadNow(!book.isRead());
                return true;

            } else if (menuItemId == R.id.MENU_BOOK_LOAN_ADD) {
                //noinspection DataFlowIssue
                editLenderLauncher.launch(getActivity(), book);
                return true;

            } else if (menuItemId == R.id.MENU_BOOK_LOAN_DELETE) {
                deleteLoanee(book);
                return true;

            } else if (menuItemId == R.id.MENU_SHARE) {
                //noinspection DataFlowIssue
                startActivity(book.getShareIntent(context, aVm.getStyle()));
                return true;

            } else if (menuItemId == R.id.MENU_CALIBRE_SETTINGS) {
                // Must use the Activity fm as the current fragment could be hosted by
                // ShowBookPagerFragment or embedded inside the BoB
                //noinspection DataFlowIssue
                getActivity().getSupportFragmentManager()
                             .beginTransaction()
                             .setReorderingAllowed(true)
                             .addToBackStack(CalibrePreferencesFragment.TAG)
                             .replace(R.id.main_fragment,
                                      new CalibrePreferencesFragment(),
                                      CalibrePreferencesFragment.TAG)
                             .commit();
                return true;

            } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET_SINGLE_BOOK) {
                updateBookLauncher.launch(book);
                return true;

            } else if (menuItemId == R.id.MENU_SYNC_LIST_WITH_DETAILS) {
                if (bookChangedListener != null) {
                    bookChangedListener.onSyncBook(book.getId());
                }
                return true;
            }

            //noinspection DataFlowIssue
            if (calibreHandler != null
                && calibreHandler.onMenuItemSelected(context, menuItemId, book)) {
                return true;
            }

            //noinspection DataFlowIssue
            return aVm.getMenuHandlers()
                      .stream()
                      .anyMatch(h -> h.onMenuItemSelected(context, menuItemId, book));
        }

        private void deleteLoanee(@NonNull final Book book) {
            vm.deleteLoan();
            aVm.setDataModified();

            bindLoanee(book);

            // needed when running in embedded mode to update the BoB list
            if (bookChangedListener != null) {
                bookChangedListener.onBookUpdated(book, DBKey.LOANEE_NAME);
            }
        }

        private void deleteBook(@NonNull final Book book) {
            final String title = book.getTitle();
            final List<Author> authors = book.getAuthors();
            //noinspection DataFlowIssue
            StandardDialogs.deleteBook(getContext(), title, authors, () -> {
                final long bookIdDeleted = book.getId();
                vm.deleteBook();
                aVm.setDataModified();

                // needed when running in embedded mode to update the BoB list
                if (bookChangedListener != null) {
                    bookChangedListener.onBookDeleted(bookIdDeleted);

                } else {
                    // set 0 as the repositionToBookId
                    final Intent resultIntent = EditBookOutput.createResultIntent(true, 0);
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            });
        }

    }
}
