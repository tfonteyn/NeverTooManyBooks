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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.MenuHelper;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsMergePublicationSectionBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;

public class ShowBookDetailsFragment
        extends BaseFragment
        implements CoverHandler.CoverHandlerHost {

    private static final String TAG = "ShowBookDetailsFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];

    /** Delegate for Calibre. */
    @Nullable
    private CalibreHandler mCalibreHandler;

    @Nullable
    private AmazonHandler mAmazonHandler;
    @Nullable
    private ViewBookOnWebsiteHandler mViewBookHandler;

    private FragmentBookDetailsBinding mVb;

    private ShowBookDetailsViewModel mVm;

    /** User edits a book. */
    private final ActivityResultLauncher<Long> mEditBookLauncher = registerForActivityResult(
            new EditBookByIdContract(), this::onBookEditingDone);
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
                    mVm.reloadBook();
                }
            };


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mVm = new ViewModelProvider(this).get(ShowBookDetailsViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());

        mEditLenderLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        mVb = FragmentBookDetailsBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        final Context context = getContext();

        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);

        createSyncDelegates(global);
        mAmazonHandler = new AmazonHandler(context);
        mViewBookHandler = new ViewBookOnWebsiteHandler(context);

        createCoverDelegates(global);

        mVm.onBookLoaded().observe(getViewLifecycleOwner(), this::onBindBook);
    }

    @Override
    public void onResume() {
        super.onResume();
        bindBook(mVm.getBook());
    }

    /**
     * Create the optional launcher and delegates.
     *
     * @param global Global preferences
     */
    private void createSyncDelegates(@NonNull final SharedPreferences global) {

        if (SyncServer.CalibreCS.isEnabled(global)) {
            try {
                //noinspection ConstantConditions
                mCalibreHandler = new CalibreHandler(getContext(), this);
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
        final Resources res = getResources();

        if (mVm.isCoverUsed(global, 0)) {
            final int maxWidth = res.getDimensionPixelSize(R.dimen.cover_details_0_width);
            final int maxHeight = res.getDimensionPixelSize(R.dimen.cover_details_0_height);

            mCoverHandler[0] = new CoverHandler(this, this, 0, maxWidth, maxHeight);
            mCoverHandler[0].onViewCreated(this);
            mCoverHandler[0].setProgressView(mVb.coverOperationProgressBar);
            mCoverHandler[0].setBookSupplier(() -> mVm.getBook());
        }

        if (mVm.isCoverUsed(global, 1)) {
            final int maxWidth = res.getDimensionPixelSize(R.dimen.cover_details_1_width);
            final int maxHeight = res.getDimensionPixelSize(R.dimen.cover_details_1_height);

            mCoverHandler[1] = new CoverHandler(this, this, 1, maxWidth, maxHeight);
            mCoverHandler[1].onViewCreated(this);
            mCoverHandler[1].setProgressView(mVb.coverOperationProgressBar);
            mCoverHandler[1].setBookSupplier(() -> mVm.getBook());
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
        final Book book = mVm.getBook();

        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        final boolean isSaved = !book.isNew();
        final boolean isRead = book.getBoolean(DBKey.BOOL_READ);
        menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(isSaved && !isRead);
        menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isSaved && isRead);

        // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
        final boolean useLending = DBKey.isUsed(global, DBKey.KEY_LOANEE);
        final boolean isAvailable = mVm.isAvailable();
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isSaved && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && isSaved && !isAvailable);

        if (SyncServer.CalibreCS.isEnabled(global)) {
            //noinspection ConstantConditions
            mCalibreHandler.prepareMenu(menu, book);
        }

        //noinspection ConstantConditions
        mViewBookHandler.prepareMenu(menu, book);

        //noinspection ConstantConditions
        mAmazonHandler.prepareMenu(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final Context context = getContext();
        final Book book = mVm.getBook();

        final int itemId = item.getItemId();
        if (itemId == R.id.MENU_BOOK_EDIT) {
            mEditBookLauncher.launch(book.getId());
            return true;

        } else if (itemId == R.id.MENU_BOOK_DELETE) {
            final String title = book.getString(DBKey.KEY_TITLE);
            final List<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            //noinspection ConstantConditions
            StandardDialogs.deleteBook(context, title, authors, () -> {
                mVm.deleteBook();

                //noinspection ConstantConditions
                getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
                getActivity().finish();
            });
            return true;

        } else if (itemId == R.id.MENU_BOOK_SET_READ || itemId == R.id.MENU_BOOK_SET_UNREAD) {
            // toggle 'read' status of the book
            mVm.toggleRead();
            return true;

        } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
            mEditLenderLauncher.launch(book);
            return true;

        } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
            mVm.deleteLoan();
            return true;

        } else if (itemId == R.id.MENU_SHARE) {
            //noinspection ConstantConditions
            startActivity(book.getShareIntent(context));
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
            //noinspection ConstantConditions
            final Intent intent = SettingsHostActivity
                    .createIntent(getContext(), CalibrePreferencesFragment.class);
            startActivity(intent);
            return true;

        } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
            mUpdateBookLauncher.launch(book);
            return true;

        }

        if (mCalibreHandler != null && mCalibreHandler.onItemSelected(itemId, book)) {
            return true;
        }

        if (mAmazonHandler != null && mAmazonHandler.onItemSelected(itemId, book)) {
            return true;
        }

        if (mViewBookHandler != null && mViewBookHandler.onItemSelected(item.getItemId(), book)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
            mVm.deleteLoan();
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
        mVm.reloadBook();
    }

    @Override
    public void refresh(@IntRange(from = 0, to = 1) final int cIdx) {
        mVm.reloadBook();
    }

    private void setActivityTitle(@NonNull final Book book) {
        //noinspection ConstantConditions
        String authors = Author.getCondensedNames(
                getContext(), book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST));

        if (BuildConfig.DEBUG /* always */) {
            authors = "[" + book.getId() + "] " + authors;
        }
        setTitle(authors);
        setSubtitle(book.getString(DBKey.KEY_TITLE));
    }

    private void onBindBook(@NonNull final ShowBookDetailsViewModel.BookMessage message) {

        final boolean isNewEvent = message.isNewEvent();
        if (isNewEvent) {
            bindBook(message.book);
        }
    }

    private void bindBook(@NonNull final Book book) {
        // update all Fields with their current View instances
        final Fields fields = mVm.getFields();
        fields.setParentView(mVb.getRoot());
        fields.setAll(book);

        if (mCoverHandler[0] != null) {
            mCoverHandler[0].onBindView(mVb.coverImage0, book);
        }
        if (mCoverHandler[1] != null) {
            mCoverHandler[1].onBindView(mVb.coverImage1, book);
        }

        switch (book.getContentType()) {
            case Collection:
                mVb.lblAnthologyOrCollection.setVisibility(View.VISIBLE);
                mVb.lblAnthologyOrCollection.setText(R.string.lbl_collection);
                break;

            case Anthology:
                mVb.lblAnthologyOrCollection.setVisibility(View.VISIBLE);
                mVb.lblAnthologyOrCollection.setText(R.string.lbl_anthology);
                break;

            case Book:
            default:
                mVb.lblAnthologyOrCollection.setVisibility(View.GONE);
                break;
        }

        if (mVm.useLoanee()) {
            bindLoanee(book);
        } else {
            mVb.lendTo.setVisibility(View.GONE);
        }

        if (mVm.useToc()) {
            if (mVb.btnShowToc != null) {
                bindTocButton(book);
            } else if (mVb.tocFrame != null) {
                bindTocFrame(book);
            }
        } else {
            if (mVb.btnShowToc != null) {
                mVb.btnShowToc.setVisibility(View.GONE);
            } else if (mVb.tocFrame != null) {
                //noinspection ConstantConditions
                mVb.lblToc.setVisibility(View.GONE);
                mVb.tocFrame.setVisibility(View.GONE);
            }
        }

        mVm.getFields().setVisibility(mVb.getRoot(), true, false);

        // Hide the Publication section label if none of the publishing fields are shown.
        final FragmentBookDetailsMergePublicationSectionBinding vbPub =
                FragmentBookDetailsMergePublicationSectionBinding.bind(mVb.publicationSection);
        setSectionVisibility(vbPub.lblPublication,
                             vbPub.publisher,
                             vbPub.datePublished,
                             vbPub.priceListed,
                             vbPub.format,
                             vbPub.color,
                             vbPub.language,
                             vbPub.pages);

        // All views should now have proper visibility set, so fix their focus order.
        ViewFocusOrder.fix(mVb.getRoot());

        setActivityTitle(book);
    }

    /**
     * Inflates 'lend-to' field showing a person the book was lend to.
     * Allows returning the book via a context menu.
     *
     * @param book to load
     */
    private void bindLoanee(@NonNull final Book book) {
        final String loanee = book.getLoanee();
        if (loanee.isEmpty()) {
            mVb.lendTo.setText("");
            mVb.lendTo.setVisibility(View.GONE);
        } else {
            mVb.lendTo.setText(getString(R.string.lbl_lend_out_to_name, loanee));
            mVb.lendTo.setVisibility(View.VISIBLE);
            //TODO: convert to ExtPopupMenu context menu.... if I can be bothered. */
            mVb.lendTo.setOnCreateContextMenuListener((menu, v, menuInfo) -> menu.add(
                    Menu.NONE,
                    R.id.MENU_BOOK_LOAN_DELETE,
                    getResources().getInteger(R.integer.MENU_ORDER_LENDING),
                    R.string.menu_lend_return_book));
        }
    }

    /**
     * Smaller displays have a button to show the TOC in the current Activity.
     *
     * @param book to load
     */
    @SuppressWarnings("ConstantConditions")
    private void bindTocButton(@NonNull final Book book) {
        final ArrayList<TocEntry> tocList = book.getParcelableArrayList(Book.BKEY_TOC_LIST);
        if (tocList.isEmpty()) {
            mVb.btnShowToc.setVisibility(View.GONE);
        } else {
            mVb.btnShowToc.setVisibility(View.VISIBLE);
            mVb.btnShowToc.setOnClickListener(v -> {
                final Fragment fragment = new TocFragment();
                final Bundle args = new Bundle();
                args.putLong(DBKey.PK_ID, book.getId());
                args.putParcelableArrayList(Book.BKEY_AUTHOR_LIST,
                                            book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST));
                args.putParcelableArrayList(Book.BKEY_TOC_LIST, tocList);
                fragment.setArguments(args);

                getActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
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
    @SuppressWarnings("ConstantConditions")
    private void bindTocFrame(@NonNull final Book book) {
        final ArrayList<TocEntry> tocList = book.getParcelableArrayList(Book.BKEY_TOC_LIST);
        if (tocList.isEmpty()) {
            mVb.lblToc.setVisibility(View.GONE);
            mVb.tocFrame.setVisibility(View.GONE);
        } else {
            mVb.lblToc.setVisibility(View.VISIBLE);
            mVb.tocFrame.setVisibility(View.VISIBLE);

            final TocFragment fragment = new TocFragment();
            final Bundle args = new Bundle();
            args.putParcelableArrayList(Book.BKEY_TOC_LIST, tocList);
            fragment.setArguments(args);
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.toc_frame, fragment, TocFragment.TAG)
                    .commit();
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

        final boolean visible = Arrays.stream(fieldViews)
                                      .filter(Objects::nonNull)
                                      .anyMatch(v -> v.getVisibility() != View.GONE);
        sectionView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
