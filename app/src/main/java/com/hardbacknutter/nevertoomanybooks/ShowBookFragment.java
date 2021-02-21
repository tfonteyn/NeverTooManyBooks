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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CalibrePreferencesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsMergePublicationSectionBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsMergeTocSectionBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentShowBookBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowTocEntryWithAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BooleanIndicatorAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ShowBookViewModel;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * <p>
 * Do NOT assume fields are empty by default when populating them manually.
 */
public class ShowBookFragment
        extends Fragment
        implements CoverHandler.CoverHandlerHost {

    /** Log tag. */
    public static final String TAG = "ShowBookFragment";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];
    /** Delegate for Goodreads. */
    private final GoodreadsHandler mGoodreadsHandler = new GoodreadsHandler();

    /** Calibre preferences screen. */
    private final ActivityResultLauncher<Void> mCalibrePreferencesLauncher =
            registerForActivityResult(new CalibrePreferencesContract(), aVoid -> { });

    /** Delegate for Calibre. */
    @Nullable
    private CalibreHandler mCalibreHandler;
    /** View model. */
    private ShowBookViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
                    getActivity().finish();
                }
            };
    /** View Binding with the ViewPager2. */
    private FragmentShowBookBinding mVb;
    /** ViewPager2 adapter. */
    private ShowBookPagerAdapter mPagerAdapter;
    private Toolbar mToolbar;

    /** User edits a book. */
    private final ActivityResultLauncher<Long> mEditBookLauncher =
            registerForActivityResult(new EditBookByIdContract(), this::onBookEditingDone);

    /** User updates a book with internet data. */
    private final ActivityResultLauncher<Book> mUpdateBookLauncher = registerForActivityResult(
            new UpdateSingleBookContract(), this::onBookEditingDone);

    /** Handle the edit-lender dialog. */
    private final EditLenderDialogFragment.Launcher mEditLenderLauncher =
            new EditLenderDialogFragment.Launcher(RK_EDIT_LENDER) {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    // The db was already updated, just update the book
                    refreshCurrentBook();
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        mVb = FragmentShowBookBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @CallSuper
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //noinspection ConstantConditions
        mToolbar = getActivity().findViewById(R.id.toolbar);

        // Popup the search widget when the user starts to type.
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mEditLenderLauncher.registerForFragmentResult(getChildFragmentManager(), this);

        mVm = new ViewModelProvider(this).get(ShowBookViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());

        try {
            mCalibreHandler = new CalibreHandler(getContext());
            mCalibreHandler.onViewCreated(this);
        } catch (@NonNull final SSLException | CertificateException ignore) {
            // ignore
        }

        mGoodreadsHandler.onViewCreated(this);

        // The FAB lives in the activity.
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_baseline_edit_24);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> mEditBookLauncher.launch(
                mVm.getBookAtPosition(mVb.pager.getCurrentItem()).getId()));

        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        final Resources res = getResources();

        if (mVm.isCoverUsed(global, 0)) {
            final int maxWidth = res.getDimensionPixelSize(R.dimen.cover_details_0_width);
            final int maxHeight = res.getDimensionPixelSize(R.dimen.cover_details_0_height);

            mCoverHandler[0] = new CoverHandler(this, mVm.getBookDao(), 0, maxWidth, maxHeight);
            mCoverHandler[0].onViewCreated(this);
            mCoverHandler[0].setProgressView(mVb.coverOperationProgressBar);
            mCoverHandler[0].setBookSupplier(
                    () -> mVm.getBookAtPosition(mVb.pager.getCurrentItem()));
        }

        if (mVm.isCoverUsed(global, 1)) {
            final int maxWidth = res.getDimensionPixelSize(R.dimen.cover_details_1_width);
            final int maxHeight = res.getDimensionPixelSize(R.dimen.cover_details_1_height);

            mCoverHandler[1] = new CoverHandler(this, mVm.getBookDao(), 1, maxWidth, maxHeight);
            mCoverHandler[1].onViewCreated(this);
            mCoverHandler[1].setProgressView(mVb.coverOperationProgressBar);
            mCoverHandler[1].setBookSupplier(
                    () -> mVm.getBookAtPosition(mVb.pager.getCurrentItem()));
        }

        mPagerAdapter = new ShowBookPagerAdapter(getContext(), getChildFragmentManager(),
                                                 mVm, mCoverHandler);
        mVb.pager.setAdapter(mPagerAdapter);
        mVb.pager.setCurrentItem(mVm.getInitialPagerPosition(), false);
        mVb.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(final int position) {
                setActivityTitle(mVm.getBookAtPosition(position));
            }
        });

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance().display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // add the search action view
        inflater.inflate(R.menu.sav_search, menu);
        //noinspection ConstantConditions
        MenuHelper.setupSearchActionView(getActivity(), menu);

        inflater.inflate(R.menu.book, menu);

        // duplicating is not supported from inside this fragment
        menu.findItem(R.id.MENU_BOOK_DUPLICATE).setVisible(false);

        if (menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE) == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);
        }
        if (menu.findItem(R.id.SUBMENU_AMAZON_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search_on_amazon, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        final Book book = mVm.getBookAtPosition(mVb.pager.getCurrentItem());

        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        final boolean isSaved = !book.isNew();
        final boolean isRead = book.getBoolean(DBDefinitions.KEY_READ);
        menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(isSaved && !isRead);
        menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isSaved && isRead);

        // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
        final boolean useLending = DBDefinitions.isUsed(global, DBDefinitions.KEY_LOANEE);
        final boolean isAvailable = mVm.isAvailable(mVb.pager.getCurrentItem());
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isSaved && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && isSaved && !isAvailable);

        menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
            .setVisible(GoodreadsManager.isShowSyncMenus(global));

        if (mCalibreHandler != null) {
            mCalibreHandler.prepareMenu(menu, book, global);
        }

        MenuHelper.prepareViewBookOnWebsiteMenu(menu, book);
        MenuHelper.prepareOptionalMenus(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final Context context = getContext();
        final Book book = mVm.getBookAtPosition(mVb.pager.getCurrentItem());

        final int itemId = item.getItemId();
        if (itemId == R.id.MENU_BOOK_EDIT) {
            mEditBookLauncher.launch(book.getId());
            return true;

        } else if (itemId == R.id.MENU_BOOK_DELETE) {
            final String title = book.getString(DBDefinitions.KEY_TITLE);
            final List<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            //noinspection ConstantConditions
            StandardDialogs.deleteBook(context, title, authors, () -> {
                mVm.deleteBook(context, mVb.pager.getCurrentItem());

                //noinspection ConstantConditions
                getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
                getActivity().finish();
            });
            return true;

        } else if (itemId == R.id.MENU_BOOK_SET_READ || itemId == R.id.MENU_BOOK_SET_UNREAD) {
            // toggle 'read' status of the book
            mVm.toggleRead(mVb.pager.getCurrentItem());
            mPagerAdapter.notifyItemChanged(mVb.pager.getCurrentItem());
            return true;

        } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
            mEditLenderLauncher.launch(book);
            return true;

        } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
            mVm.deleteLoan(mVb.pager.getCurrentItem());
            mPagerAdapter.notifyItemChanged(mVb.pager.getCurrentItem());
            return true;

        } else if (itemId == R.id.MENU_SHARE) {
            //noinspection ConstantConditions
            startActivity(book.getShareIntent(context));
            return true;

        } else if (itemId == R.id.MENU_BOOK_SEND_TO_GOODREADS) {
            mGoodreadsHandler.sendBook(book.getId());
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_READ) {
            if (mCalibreHandler != null) {
                mCalibreHandler.read(book);
            }
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_DOWNLOAD) {
            if (mCalibreHandler != null) {
                // Reminder: no need to check the book as the menu is only shown for valid books.
                mCalibreHandler.download(book);
            }
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_SETTING) {
            mCalibrePreferencesLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            mUpdateBookLauncher.launch(book);
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR) {
            final Author author = book.getPrimaryAuthor();
            if (author != null) {
                //noinspection ConstantConditions
                AmazonSearchEngine.startSearchActivity(context, author, null);
            }
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_IN_SERIES) {
            final Series series = book.getPrimarySeries();
            if (series != null) {
                //noinspection ConstantConditions
                AmazonSearchEngine.startSearchActivity(context, null, series);
            }
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES) {
            final Author author = book.getPrimaryAuthor();
            final Series series = book.getPrimarySeries();
            if (author != null && series != null) {
                //noinspection ConstantConditions
                AmazonSearchEngine.startSearchActivity(context, author, series);
            }
            return true;
        }

        //noinspection ConstantConditions
        if (MenuHelper.handleViewBookOnWebsiteMenu(context, item.getItemId(), book)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
            mVm.deleteLoan(mVb.pager.getCurrentItem());
            mPagerAdapter.notifyItemChanged(mVb.pager.getCurrentItem());
            return true;
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Called when the user has finished editing (manual or internet update) a Book.
     *
     * @param data returned from the editor Activity
     */
    private void onBookEditingDone(@Nullable final Bundle data) {
        if (data != null) {
            // pass the data up
            mVm.getResultIntent().putExtras(data);
        }
        refreshCurrentBook();
    }

    @Override
    public void refresh(@IntRange(from = 0, to = 1) final int cIdx) {
        refreshCurrentBook();
    }

    private void refreshCurrentBook() {
        // refresh the book currently displayed
        final Book book = mVm.reloadBookAtPosition(mVb.pager.getCurrentItem());
        mPagerAdapter.notifyItemChanged(mVb.pager.getCurrentItem());
        setActivityTitle(book);
    }

    private void setActivityTitle(@NonNull final Book book) {

        // Set the activity title
        String title = book.getString(DBDefinitions.KEY_TITLE);
        if (BuildConfig.DEBUG /* always */) {
            title = "[" + book.getId() + "] " + title;
        }

        mToolbar.setTitle(title);
        //noinspection ConstantConditions
        mToolbar.setSubtitle(Author.getCondensedNames(
                getContext(), book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST)));
    }

    public static class ShowBookPagerAdapter
            extends RecyclerView.Adapter<ShowBookPagerAdapter.Holder> {

        /** Cached inflater. */
        @NonNull
        private final LayoutInflater mInflater;
        @NonNull
        private final ShowBookViewModel mVm;
        @NonNull
        private final CoverHandler[] mCoverHandler;

        /** The fields used. */
        @NonNull
        private final Fields mFieldsMap;

        @NonNull
        private final FragmentManager mFragmentManager;

        /**
         * Constructor.
         *
         * @param context       Current context
         * @param fm            FragmentManager
         * @param bookViewModel the view model from the fragment
         * @param coverHandler  the array of handlers
         */
        ShowBookPagerAdapter(@NonNull final Context context,
                             @NonNull final FragmentManager fm,
                             @NonNull final ShowBookViewModel bookViewModel,
                             @NonNull final CoverHandler[] coverHandler) {
            mInflater = LayoutInflater.from(context);
            mFragmentManager = fm;
            mVm = bookViewModel;
            mCoverHandler = coverHandler;

            mFieldsMap = initFields(context);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = mInflater.inflate(R.layout.fragment_book_details, parent, false);
            final Holder holder = new Holder(view);

            if (mCoverHandler[0] != null) {
                mCoverHandler[0].attachOnClickListeners(mFragmentManager, holder.mVb.coverImage0);
            } else {
                holder.mVb.coverImage0.setVisibility(View.GONE);
            }

            if (mCoverHandler[1] != null) {
                mCoverHandler[1].attachOnClickListeners(mFragmentManager, holder.mVb.coverImage1);
            } else {
                holder.mVb.coverImage1.setVisibility(View.GONE);
            }

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final Book book = mVm.getBookAtPosition(position);

            holder.onBindViewHolder(mFieldsMap, mVm.getBookDao(), book);

            if (mCoverHandler[0] != null) {
                mCoverHandler[0].onBindView(holder.mVb.coverImage0, book);
            }
            if (mCoverHandler[1] != null) {
                mCoverHandler[1].onBindView(holder.mVb.coverImage1, book);
            }
        }

        @Override
        public int getItemCount() {
            return mVm.getRowCount();
        }

        private Fields initFields(@NonNull final Context context) {

            final Fields fields = new Fields();

            final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
            // These FieldFormatter's can be shared between multiple fields.
            final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale);
            final FieldFormatter<String> htmlFormatter = new HtmlFormatter<>(true, true);
            final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
            final FieldFormatter<String> languageFormatter = new LanguageFormatter(userLocale);

            // book fields
            fields.add(R.id.title, new TextViewAccessor<>(), DBDefinitions.KEY_TITLE);

            fields.add(R.id.author, new TextViewAccessor<>(
                               new AuthorListFormatter(Author.Details.Full, false, true)),
                       Book.BKEY_AUTHOR_LIST, DBDefinitions.KEY_FK_AUTHOR)
                  .setRelatedFields(R.id.lbl_author);

            fields.add(R.id.series_title, new TextViewAccessor<>(
                               new SeriesListFormatter(Series.Details.Full, false, true)),
                       Book.BKEY_SERIES_LIST, DBDefinitions.KEY_SERIES_TITLE)
                  .setRelatedFields(R.id.lbl_series);

            fields.add(R.id.isbn, new TextViewAccessor<>(), DBDefinitions.KEY_ISBN)
                  .setRelatedFields(R.id.lbl_isbn);

            fields.add(R.id.description, new TextViewAccessor<>(htmlFormatter),
                       DBDefinitions.KEY_DESCRIPTION);

            fields.add(R.id.genre, new TextViewAccessor<>(), DBDefinitions.KEY_GENRE)
                  .setRelatedFields(R.id.lbl_genre);

            fields.add(R.id.language, new TextViewAccessor<>(languageFormatter),
                       DBDefinitions.KEY_LANGUAGE)
                  .setRelatedFields(R.id.lbl_language);

            fields.add(R.id.pages, new TextViewAccessor<>(new PagesFormatter()),
                       DBDefinitions.KEY_PAGES);
            fields.add(R.id.format, new TextViewAccessor<>(), DBDefinitions.KEY_FORMAT);
            fields.add(R.id.color, new TextViewAccessor<>(), DBDefinitions.KEY_COLOR);
            fields.add(R.id.publisher, new TextViewAccessor<>(new CsvFormatter()),
                       Book.BKEY_PUBLISHER_LIST, DBDefinitions.KEY_PUBLISHER_NAME);

            fields.add(R.id.date_published, new TextViewAccessor<>(dateFormatter),
                       DBDefinitions.KEY_BOOK_DATE_PUBLISHED)
                  .setRelatedFields(R.id.lbl_date_published);

            fields.add(R.id.first_publication, new TextViewAccessor<>(dateFormatter),
                       DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
                  .setRelatedFields(R.id.lbl_first_publication);

            fields.add(R.id.print_run, new TextViewAccessor<>(), DBDefinitions.KEY_PRINT_RUN)
                  .setRelatedFields(R.id.lbl_print_run);

            fields.add(R.id.price_listed, new TextViewAccessor<>(moneyFormatter),
                       DBDefinitions.KEY_PRICE_LISTED)
                  .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

            // Personal fields
            fields.add(R.id.bookshelves, new EntityListChipGroupAccessor(
                               () -> new ArrayList<>(mVm.getAllBookshelves()),
                               false), Book.BKEY_BOOKSHELF_LIST,
                       DBDefinitions.KEY_FK_BOOKSHELF)
                  .setRelatedFields(R.id.lbl_bookshelves);

            fields.add(R.id.date_acquired, new TextViewAccessor<>(dateFormatter),
                       DBDefinitions.KEY_DATE_ACQUIRED)
                  .setRelatedFields(R.id.lbl_date_acquired);

            fields.add(R.id.edition,
                       new BitmaskChipGroupAccessor(() -> Book.Edition.getEditions(context), false),
                       DBDefinitions.KEY_EDITION_BITMASK)
                  .setRelatedFields(R.id.lbl_edition);

            fields.add(R.id.location, new TextViewAccessor<>(), DBDefinitions.KEY_LOCATION)
                  .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

            fields.add(R.id.rating, new RatingBarAccessor(false), DBDefinitions.KEY_RATING)
                  .setRelatedFields(R.id.lbl_rating);

            fields.add(R.id.condition, new TextViewAccessor<>(
                               new StringArrayResFormatter(context, R.array.conditions_book)),
                       DBDefinitions.KEY_BOOK_CONDITION)
                  .setRelatedFields(R.id.lbl_condition);
            fields.add(R.id.condition_cover, new TextViewAccessor<>(
                               new StringArrayResFormatter(context, R.array.conditions_dust_cover)),
                       DBDefinitions.KEY_BOOK_CONDITION_COVER)
                  .setRelatedFields(R.id.lbl_condition_cover);

            fields.add(R.id.notes, new TextViewAccessor<>(htmlFormatter),
                       DBDefinitions.KEY_PRIVATE_NOTES)
                  .setRelatedFields(R.id.lbl_notes);

            fields.add(R.id.read_start, new TextViewAccessor<>(dateFormatter),
                       DBDefinitions.KEY_READ_START)
                  .setRelatedFields(R.id.lbl_read_start);
            fields.add(R.id.read_end, new TextViewAccessor<>(dateFormatter),
                       DBDefinitions.KEY_READ_END)
                  .setRelatedFields(R.id.lbl_read_end);

            fields.add(R.id.icon_read, new BooleanIndicatorAccessor(), DBDefinitions.KEY_READ);

            fields.add(R.id.icon_signed, new BooleanIndicatorAccessor(), DBDefinitions.KEY_SIGNED)
                  .setRelatedFields(R.id.lbl_signed);

            fields.add(R.id.price_paid, new TextViewAccessor<>(moneyFormatter),
                       DBDefinitions.KEY_PRICE_PAID)
                  .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);

            return fields;
        }

        public static class Holder
                extends RecyclerView.ViewHolder {

            /** View Binding. */
            @NonNull
            final FragmentBookDetailsBinding mVb;
            /** View Binding. */
            @NonNull
            private final FragmentBookDetailsMergePublicationSectionBinding mVbPub;
            /** View Binding. */
            @NonNull
            private final FragmentBookDetailsMergeTocSectionBinding mVbToc;
            /** Cached inflater. */
            @NonNull
            private final LayoutInflater mLayoutInflater;

            private final boolean mUseToc;
            private final boolean mUseLoanee;

            Holder(@NonNull final View itemView) {
                super(itemView);

                mLayoutInflater = LayoutInflater.from(itemView.getContext());

                mVb = FragmentBookDetailsBinding.bind(itemView);
                mVbPub = FragmentBookDetailsMergePublicationSectionBinding
                        .bind(mVb.publicationSection);
                mVbToc = FragmentBookDetailsMergeTocSectionBinding.bind(mVb.tocSection);

                final SharedPreferences global = PreferenceManager
                        .getDefaultSharedPreferences(itemView.getContext());
                mUseLoanee = DBDefinitions.isUsed(global, DBDefinitions.KEY_LOANEE);
                mUseToc = DBDefinitions.isUsed(global, DBDefinitions.KEY_TOC_BITMASK);

                if (!mUseLoanee) {
                    mVb.lendTo.setVisibility(View.GONE);
                }

                // Anthology/TOC fields
                if (!mUseToc) {
                    mVbToc.lblAnthology.setVisibility(View.GONE);
                    mVbToc.lblToc.setVisibility(View.GONE);
                    mVbToc.toc.setVisibility(View.GONE);
                    mVbToc.btnShowToc.setVisibility(View.GONE);

                } else {
                    // show/hide the TOC as the user flips the switch.
                    mVbToc.btnShowToc.setOnClickListener(v -> {
                        // note that the button is explicitly (re)set.
                        // If user clicks to fast it seems to get out of sync.
                        if (mVbToc.toc.getVisibility() == View.VISIBLE) {
                            // Force a scroll
                            // A manual scroll seems not possible after the TOC closes?
                            mVb.rootScroller.fullScroll(View.FOCUS_UP);
                            mVbToc.toc.setVisibility(View.GONE);
                            mVbToc.btnShowToc.setChecked(false);

                        } else {
                            mVbToc.toc.setVisibility(View.VISIBLE);
                            mVbToc.btnShowToc.setChecked(true);
                        }
                    });
                }
            }

            /**
             * At this point we're told to load our local (to the fragment) fields from the Book.
             *
             * @param fields  to populate
             * @param bookDao Database Access
             * @param book    to load
             */
            void onBindViewHolder(@NonNull final Fields fields,
                                  @NonNull final BookDao bookDao,
                                  @NonNull final Book book) {
                fields.setParentView(mVb.getRoot());
                fields.setAll(book);

                if (mUseLoanee) {
                    onBindLoanee(book.getLoanee());
                }

                if (mUseToc) {
                    onBindToc(book);
                }

                fields.setVisibility(mVb.getRoot(), true, false);

                // Hide the Publication section label if none of the publishing fields are shown.
                setSectionVisibility(mVbPub.lblPublication,
                                     mVbPub.publisher,
                                     mVbPub.datePublished,
                                     mVbPub.priceListed,
                                     mVbPub.format,
                                     mVbPub.color,
                                     mVbPub.language,
                                     mVbPub.pages);

                // All views should now have proper visibility set, so fix their focus order.
                ViewFocusOrder.fix(mVb.getRoot());
            }

            /**
             * Inflates 'lend-to' field showing a person the book was lend to.
             * Allows returning the book via a context menu.
             *
             * <strong>Note:</strong> we pass in the loanee and handle visibility local as this
             * method can be called from anywhere.
             *
             * @param loanee the one who shall not be mentioned.
             */
            private void onBindLoanee(@Nullable final String loanee) {
                if (loanee != null && !loanee.isEmpty()) {
                    mVb.lendTo.setText(
                            itemView.getContext().getString(R.string.lbl_lend_out_to_name, loanee));
                    mVb.lendTo.setVisibility(View.VISIBLE);
                    //TODO: convert to MenuPicker context menu.... if I can be bothered. */
                    mVb.lendTo.setOnCreateContextMenuListener((menu, v, menuInfo) -> menu.add(
                            Menu.NONE,
                            R.id.MENU_BOOK_LOAN_DELETE,
                            itemView.getResources().getInteger(R.integer.MENU_ORDER_LENDING),
                            R.string.menu_lend_return_book));
                } else {
                    mVb.lendTo.setVisibility(View.GONE);
                    mVb.lendTo.setText("");
                }
            }

            /**
             * Show or hide the Table Of Content section.
             *
             * @param book to load from
             */
            private void onBindToc(@NonNull final Book book) {
                final boolean isAnthology = book.isBitSet(DBDefinitions.KEY_TOC_BITMASK,
                                                          Book.TOC_MULTIPLE_WORKS);
                mVbToc.lblAnthology.setVisibility(isAnthology ? View.VISIBLE : View.GONE);

                mVbToc.toc.removeAllViews();
                mVbToc.toc.setVisibility(View.GONE);
                mVbToc.btnShowToc.setChecked(false);

                final List<TocEntry> tocList = book.getParcelableArrayList(Book.BKEY_TOC_LIST);

                if (!tocList.isEmpty()) {
                    final Context context = itemView.getContext();

                    for (final TocEntry tocEntry : tocList) {
                        final RowTocEntryWithAuthorBinding rowVb = RowTocEntryWithAuthorBinding
                                .inflate(mLayoutInflater, mVbToc.toc, false);

                        rowVb.title.setText(tocEntry.getLabel(context));
                        rowVb.author.setText(tocEntry.getPrimaryAuthor().getLabel(context));

                        final boolean isSet = tocEntry.getBookCount() > 1;
                        if (isSet) {
                            rowVb.cbxMultipleBooks.setVisibility(View.VISIBLE);
                            rowVb.cbxMultipleBooks.setOnClickListener(v -> {
                                final String titles = tocEntry
                                        .getBookTitles()
                                        .stream()
                                        .map(bt -> context
                                                .getString(R.string.list_element, bt.second))
                                        .collect(Collectors.joining("\n"));
                                StandardDialogs.infoPopup(rowVb.cbxMultipleBooks, titles);
                            });
                        }

                        final PartialDate date = tocEntry.getFirstPublicationDate();
                        if (date.isEmpty()) {
                            rowVb.year.setVisibility(View.GONE);
                        } else {
                            rowVb.year.setVisibility(View.VISIBLE);
                            // show full date string (if available)
                            rowVb.year.setText(context.getString(R.string.brackets,
                                                                 date.getIsoString()));
                        }
                        mVbToc.toc.addView(rowVb.getRoot());
                    }

                    mVbToc.lblToc.setVisibility(View.VISIBLE);
                    mVbToc.btnShowToc.setVisibility(View.VISIBLE);

                } else {
                    mVbToc.lblToc.setVisibility(View.GONE);
                    mVbToc.btnShowToc.setVisibility(View.GONE);
                }
            }

            /**
             * If all field Views are View.GONE, set the section View to View.GONE as well.
             * Otherwise, set the section View to View.VISIBLE.
             *
             * @param sectionView field to set
             * @param fieldViews  to check
             */
            private void setSectionVisibility(@NonNull final View sectionView,
                                              @NonNull final View... fieldViews) {
                for (final View view : fieldViews) {
                    if (view != null && view.getVisibility() != View.GONE) {
                        // at least one field was visible
                        sectionView.setVisibility(View.VISIBLE);
                        return;
                    }
                }
                // all fields were gone.
                sectionView.setVisibility(View.GONE);
            }
        }
    }

}
