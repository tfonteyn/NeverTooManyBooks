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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Field;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.CompoundButtonAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.LendBookDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentModel;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * Do NOT assume fields are empty by default when populating them manually.
 */
public class BookDetailsFragment
        extends BookBaseFragment
        implements CoverHandler.HostingFragment {

    /** Log tag. */
    public static final String TAG = "BookDetailsFragment";
    private static final String BKEY_M_CONTEXT_MENU_OPEN_INDEX = TAG + ":imgIndex";

    /** the covers. */
    private final ImageView[] mCoverView = new ImageView[2];
    /** Handles cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];

    private TextView mIsbnView;

    /** Label for anthologies. */
    private TextView mIsAnthologyLabelView;
    /** Checkbox for anthologies. Set manually. */
    private CompoundButton mIsAnthologyCbx;
    /** Switch the user can flick to display/hide the TOC (if present). */
    private CompoundButton mTocButton;
    /** We display/hide the TOC header label as needed. */
    private View mTocLabelView;
    /** The TOC list. */
    private LinearLayout mTocView;
    /** The loanee banner. */
    private TextView mLoanedToView;

    /** Listen for changes. */
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {
        if (data != null) {
            if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                populateLoanedToField(data.getString(DBDefinitions.KEY_LOANEE));
            } else {
                // we don't expect/implement any others.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "bookId=" + bookId + "|fieldsChanged=" + fieldsChanged);
                }
            }
        }
    };

    /** The scroll view encompassing the entire fragment page. */
    private NestedScrollView mTopScrollView;

    /** Registered with the Activity to deliver us gestures. */
    private View.OnTouchListener mOnTouchListener;

    /** Handle next/previous paging in the flattened booklist; called by mOnTouchListener. */
    private GestureDetector mGestureDetector;

    /** Contains the flattened booklist for next/previous paging and the Fields collection. */
    private BookDetailsFragmentModel mBookDetailsFragmentModel;

    /** Track on which cover view the context menu was used. */
    private int mCurrentCoverHandlerIndex = -1;

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (LendBookDialogFragment.TAG.equals(childFragment.getTag())) {
            ((LendBookDialogFragment) childFragment).setListener(mBookChangedListener);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(inflater.getContext());

        final View view = inflater.inflate(R.layout.fragment_book_details, container, false);

        mTopScrollView = view.findViewById(R.id.topScroller);

        mIsbnView = view.findViewById(R.id.isbn);

        // Anthology/TOC fields
        mIsAnthologyLabelView = view.findViewById(R.id.lbl_anthology);
        mIsAnthologyCbx = view.findViewById(R.id.cbx_anthology);
        mTocLabelView = view.findViewById(R.id.lbl_toc);
        mTocView = view.findViewById(R.id.toc);
        mTocButton = view.findViewById(R.id.toc_button);
        if (!App.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
            mIsAnthologyLabelView.setVisibility(View.GONE);
            mIsAnthologyCbx.setVisibility(View.GONE);
            mTocLabelView.setVisibility(View.GONE);
            mTocView.setVisibility(View.GONE);
            mTocButton.setVisibility(View.GONE);
        }

        // Covers
        mCoverView[0] = view.findViewById(R.id.coverImage0);
        mCoverView[1] = view.findViewById(R.id.coverImage1);
        if (!App.isUsed(prefs, UniqueId.BKEY_THUMBNAIL)) {
            mCoverView[0].setVisibility(View.GONE);
            mCoverView[1].setVisibility(View.GONE);
        }

        mLoanedToView = view.findViewById(R.id.loaned_to);
        if (!App.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            mLoanedToView.setVisibility(View.GONE);
        }

        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        mBookDetailsFragmentModel = new ViewModelProvider(this).get(BookDetailsFragmentModel.class);

        // The book will get loaded and fields will be initialised
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentCoverHandlerIndex =
                    savedInstanceState.getInt(BKEY_M_CONTEXT_MENU_OPEN_INDEX, -1);
        }

        mBookDetailsFragmentModel.init(mBookModel.getDb(), getArguments(),
                                       mBookModel.getBook().getId());

        //noinspection ConstantConditions
        final FloatingActionButton fabButton = getActivity().findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_edit);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> startEditBook());

        // ENHANCE: should be replaced by a ViewPager2/FragmentStateAdapter
        // enable the listener for flings
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        mOnTouchListener = (v, event) -> mGestureDetector.onTouchEvent(event);

        // show/hide the TOC as the user flips the switch.
        mTocButton.setOnClickListener(v -> {
            // note that the button is explicitly (re)set.
            // If user clicks to fast it seems to get out of sync.
            if (mTocView.getVisibility() == View.VISIBLE) {
                // force a scroll; a manual scroll is no longer possible after the TOC closes.
                mTopScrollView.fullScroll(View.FOCUS_UP);
                mTocView.setVisibility(View.GONE);
                mTocButton.setChecked(false);

            } else {
                mTocView.setVisibility(View.VISIBLE);
                mTocButton.setChecked(true);
            }
        });

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BKEY_M_CONTEXT_MENU_OPEN_INDEX, mCurrentCoverHandlerIndex);
    }

    @CallSuper
    @Override
    public void onResume() {
        // The parent will kick of the process that triggers {@link #onLoadFields}.
        super.onResume();
        // No ViewPager2 involved, override the parent (see google bug comment there)
        setHasOptionsMenu(true);

        //noinspection ConstantConditions
        ((BookDetailsActivity) getActivity()).registerOnTouchListener(mOnTouchListener);
    }

    @Override
    @CallSuper
    public void onPause() {
        //noinspection ConstantConditions
        ((BookDetailsActivity) getActivity()).unregisterOnTouchListener(mOnTouchListener);
        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET:
            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookModel.putResultData(data);
                    }
                    // onResume will display the changed book.
                    mBookModel.reload();
                }
                break;

            case UniqueId.REQ_BOOK_DUPLICATE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookModel.putResultData(data);

                        long id = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (id != 0) {
                            mBookModel.setBook(id);
                        }
                    }
                    // onResume will display the new book
                    mBookModel.reload();
                    //FIXME: swiping through the flattened booklist will not see
                    // the duplicated book until we go back to BoB.
                    // Easiest solution would be to remove the dup. option from this screen...
                }
                break;

            default: {
                // handle any cover image request codes
                if (mCurrentCoverHandlerIndex >= 0) {
                    boolean handled = mCoverHandler[mCurrentCoverHandlerIndex]
                            .onActivityResult(requestCode, resultCode, data);
                    mCurrentCoverHandlerIndex = -1;
                    if (handled) {
                        break;
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(final int cIdx) {
        mCurrentCoverHandlerIndex = cIdx;
    }

    @NonNull
    @Override
    Fields getFields() {
        return mBookDetailsFragmentModel.getFields();
    }

    @Override
    protected void initFields() {
        final Fields fields = mBookDetailsFragmentModel.getFields();
        // already initialised ?
        if (!fields.isEmpty()) {
            return;
        }

        // These FieldFormatter's can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();
        final FieldFormatter<String> htmlFormatter = new HtmlFormatter<>(true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter();
        final FieldFormatter<String> languageFormatter = new LanguageFormatter();

        // book fields
        fields.add(R.id.title, new TextAccessor<String>(), DBDefinitions.KEY_TITLE);

        fields.add(R.id.author, new TextAccessor<>(
                           new AuthorListFormatter(Author.Details.Full, true)),
                   UniqueId.BKEY_AUTHOR_ARRAY, DBDefinitions.KEY_FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author);

        // The Series field is a single String with line-breaks between multiple Series.
        // Each line will be prefixed with a "• "
        fields.add(R.id.series, new TextAccessor<>(
                           new SeriesListFormatter(Series.Details.Full, true)),
                   UniqueId.BKEY_SERIES_ARRAY, DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.isbn, new TextAccessor<String>(), DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.description, new TextAccessor<>(htmlFormatter),
                   DBDefinitions.KEY_DESCRIPTION)
              .setRelatedFields(R.id.lbl_description);

        fields.add(R.id.genre, new TextAccessor<String>(), DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        fields.add(R.id.language, new TextAccessor<>(languageFormatter), DBDefinitions.KEY_LANGUAGE)
              .setRelatedFields(R.id.lbl_language);

        fields.add(R.id.pages, new TextAccessor<>(new PagesFormatter()), DBDefinitions.KEY_PAGES);
        fields.add(R.id.format, new TextAccessor<String>(), DBDefinitions.KEY_FORMAT);
        fields.add(R.id.color, new TextAccessor<String>(), DBDefinitions.KEY_COLOR);
        fields.add(R.id.publisher, new TextAccessor<String>(), DBDefinitions.KEY_PUBLISHER);

        fields.add(R.id.date_published, new TextAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_PUBLISHED)
              .setRelatedFields(R.id.lbl_date_published);

        fields.add(R.id.first_publication, new TextAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
              .setRelatedFields(R.id.lbl_first_publication);

        fields.add(R.id.print_run, new TextAccessor<String>(), DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.price_listed, new TextAccessor<>(moneyFormatter),
                   DBDefinitions.KEY_PRICE_LISTED)
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        // Personal fields
        fields.add(R.id.bookshelves,
                   new EntityListChipGroupAccessor(new ArrayList<>(
                           mBookModel.getDb().getBookshelves()), false),
                   UniqueId.BKEY_BOOKSHELF_ARRAY,
                   DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        fields.add(R.id.date_acquired, new TextAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_ACQUIRED)
              .setRelatedFields(R.id.lbl_date_acquired);

        //noinspection ConstantConditions
        fields.add(R.id.edition, new BitmaskChipGroupAccessor(
                           Book.Edition.getEditions(getContext()), false),
                   DBDefinitions.KEY_EDITION_BITMASK);

        fields.add(R.id.location, new TextAccessor<String>(), DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.add(R.id.rating, new RatingBarAccessor(), DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.add(R.id.notes, new TextAccessor<>(htmlFormatter), DBDefinitions.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        fields.add(R.id.read_start, new TextAccessor<>(dateFormatter), DBDefinitions.KEY_READ_START)
              .setRelatedFields(R.id.lbl_read_start);
        fields.add(R.id.read_end, new TextAccessor<>(dateFormatter), DBDefinitions.KEY_READ_END)
              .setRelatedFields(R.id.lbl_read_end);

        fields.add(R.id.cbx_read, new CompoundButtonAccessor(), DBDefinitions.KEY_READ);

        fields.add(R.id.cbx_signed, new CompoundButtonAccessor(), DBDefinitions.KEY_SIGNED)
              .setRelatedFields(R.id.lbl_signed);

        fields.add(R.id.price_paid, new TextAccessor<>(moneyFormatter),
                   DBDefinitions.KEY_PRICE_PAID)
              .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);
    }

    /**
     * <p>
     * At this point we're told to load our local (to the fragment) fields from the Book.
     * </p>
     * <br>{@inheritDoc}
     *
     * @param book to load
     */
    @Override
    protected void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        //noinspection ConstantConditions
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // handle special fields
        if (App.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            populateLoanedToField(mBookModel.getLoanee());
        }

        if (App.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
            final boolean isAnthology =
                    book.isBitSet(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS);
            mIsAnthologyLabelView.setVisibility(isAnthology ? View.VISIBLE : View.GONE);
            mIsAnthologyCbx.setVisibility(isAnthology ? View.VISIBLE : View.GONE);
            mIsAnthologyCbx.setChecked(isAnthology);

            populateToc(book);
        }

        if (App.isUsed(prefs, UniqueId.BKEY_THUMBNAIL)) {
            // Hook up the indexed cover image.
            mCoverHandler[0] = new CoverHandler(this, mProgressBar,
                                                book, mIsbnView, 0, mCoverView[0],
                                                ImageUtils.SCALE_LARGE);

            mCoverHandler[1] = new CoverHandler(this, mProgressBar,
                                                book, mIsbnView, 1, mCoverView[1],
                                                ImageUtils.SCALE_SMALL);
        }

        // hide unwanted and empty fields
        //noinspection ConstantConditions
        getFields().resetVisibility(getView(), true, false);

        // Hide the Publication section label if none of the publishing fields are shown.
        setSectionLabelVisibility(R.id.lbl_publication_section,
                                  R.id.publisher,
                                  R.id.date_published,
                                  R.id.price_listed,
                                  R.id.first_publication,
                                  R.id.edition);
        // Hide the "Personal notes" label if none of the notes fields are shown.
        setSectionLabelVisibility(R.id.lbl_notes,
                                  R.id.notes,
                                  R.id.cbx_signed,
                                  R.id.date_acquired,
                                  R.id.price_paid,
                                  R.id.read_start,
                                  R.id.read_end,
                                  R.id.location);
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * Allows returning the book via a context menu.
     *
     * <strong>Note:</strong> we pass in the loanee and handle visibility local as this
     * method can be called from anywhere.
     *
     * @param loanee the one who shall not be mentioned.
     */
    private void populateLoanedToField(@Nullable final String loanee) {
        if (loanee != null && !loanee.isEmpty()) {
            mLoanedToView.setText(getString(R.string.lbl_loaned_to_name, loanee));
            mLoanedToView.setVisibility(View.VISIBLE);
            mLoanedToView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                /** TODO: convert to MenuPicker context menu.... if I can be bothered. */
                @Override
                @CallSuper
                public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                                @NonNull final View v,
                                                @NonNull
                                                final ContextMenu.ContextMenuInfo menuInfo) {
                    Resources r = getResources();
                    menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                             r.getInteger(R.integer.MENU_ORDER_LENDING),
                             R.string.menu_loan_return_book);
                }
            });
        } else {
            mLoanedToView.setVisibility(View.GONE);
            mLoanedToView.setText("");
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateToc(@NonNull final Book book) {
        // we can get called more than once (when user moves sideways to another book),
        // so clear and hide/disable the view before populating it.
        // Actual visibility is handled after building the list.
        mTocView.removeAllViews();
        mTocView.setVisibility(View.GONE);
        mTocButton.setChecked(false);

        final ArrayList<TocEntry> tocList =
                book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        if (!tocList.isEmpty()) {

            @SuppressWarnings("ConstantConditions")
            @NonNull
            final Context context = getContext();
            for (TocEntry tocEntry : tocList) {
                final View rowView = getLayoutInflater()
                        .inflate(R.layout.row_toc_entry_with_author, mTocView, false);

                final TextView titleView = rowView.findViewById(R.id.title);
                final TextView authorView = rowView.findViewById(R.id.author);
                final TextView firstPubView = rowView.findViewById(R.id.year);
                final CheckBox multipleBooksView = rowView.findViewById(R.id.cbx_multiple_books);

                titleView.setText(tocEntry.getLabel(context));

                if (multipleBooksView != null) {
                    final boolean isSet = tocEntry.getBookCount() > 1;
                    multipleBooksView.setChecked(isSet);
                    multipleBooksView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                }
                if (authorView != null) {
                    authorView.setText(tocEntry.getAuthor().getLabel(context));
                }
                if (firstPubView != null) {
                    final String date = tocEntry.getFirstPublication();
                    // "< 4" covers empty and illegal dates
                    if (date.length() < 4) {
                        firstPubView.setVisibility(View.GONE);
                    } else {
                        firstPubView.setVisibility(View.VISIBLE);
                        // show full date string (if available)
                        firstPubView.setText(context.getString(R.string.brackets, date));
                    }
                }
                mTocView.addView(rowView);
            }

            mTocLabelView.setVisibility(View.VISIBLE);
            mTocButton.setVisibility(View.VISIBLE);

        } else {
            mTocLabelView.setVisibility(View.GONE);
            mTocButton.setVisibility(View.GONE);
        }
    }

    /**
     * If all 'fields' are View.GONE, set 'sectionLabelId' to View.GONE as well.
     * Otherwise, set 'sectionLabelId' to View.VISIBLE.
     *
     * @param sectionLabelId field to set
     * @param fieldIds       to check
     */
    private void setSectionLabelVisibility(@IdRes final int sectionLabelId,
                                           @IdRes final int... fieldIds) {
        View parent = getView();
        //noinspection ConstantConditions
        final View fieldView = parent.findViewById(sectionLabelId);
        if (fieldView != null) {
            for (int fieldId : fieldIds) {
                View view = parent.findViewById(fieldId);
                if (view != null && view.getVisibility() != View.GONE) {
                    // at least one field was visible
                    fieldView.setVisibility(View.VISIBLE);
                    return;
                }
            }
            // all fields were gone.
            fieldView.setVisibility(View.GONE);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.book, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        Book book = mBookModel.getBook();

        final boolean isSaved = !book.isNew();
        final boolean isRead = book.getBoolean(DBDefinitions.KEY_READ);
        final boolean isAvailable = mBookModel.isAvailable();

        menu.findItem(R.id.MENU_BOOK_READ).setVisible(isSaved && !isRead);
        menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isSaved && isRead);

        //FIXME: swiping through the flattened booklist will not see
        // the duplicated book until we go back to BoB.
        // Easiest solution would be to remove the dup. option from this screen...
        menu.findItem(R.id.MENU_BOOK_DUPLICATE).setVisible(false);

        //noinspection ConstantConditions
        menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
            .setVisible(GoodreadsHandler.isShowSyncMenus(getContext()));

        // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
        final boolean useLending = App.isUsed(DBDefinitions.KEY_LOANEE);
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isSaved && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && isSaved && !isAvailable);

        MenuHandler.prepareOptionalMenus(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        final Book book = mBookModel.getBook();

        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT: {
                startEditBook();
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                final String title = book.getString(DBDefinitions.KEY_TITLE);
                final List<Author> authors =
                        book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                //noinspection ConstantConditions
                StandardDialogs.deleteBook(getContext(), title, authors, () -> {
                    mBookModel.deleteBook(getContext());

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mBookModel.getResultData());
                    getActivity().finish();
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Intent dupIntent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, UniqueId.REQ_BOOK_DUPLICATE);
                return true;
            }
            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle 'read' status of the book
                final Field<Boolean> field = getFields().getField(R.id.cbx_read);
                field.getAccessor().setValue(mBookModel.toggleRead());
                return true;
            }
            case R.id.MENU_BOOK_LOAN_ADD: {
                //noinspection ConstantConditions
                LendBookDialogFragment.newInstance(getContext(), book)
                                      .show(getChildFragmentManager(), LendBookDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mBookModel.deleteLoan();
                populateLoanedToField(null);
                return true;
            }
            case R.id.MENU_SHARE: {
                //noinspection ConstantConditions
                startActivity(Intent.createChooser(book.getShareBookIntent(getContext()),
                                                   getString(R.string.menu_share_this)));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG)
                        .show();
                //noinspection ConstantConditions
                new SendOneBookTask(book.getId(), mBookModel.getGoodreadsTaskListener(getContext()))
                        .execute();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startEditBook() {
        final Intent editIntent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(DBDefinitions.KEY_PK_ID, mBookModel.getBook().getId());
        startActivityForResult(editIntent, UniqueId.REQ_BOOK_EDIT);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_LOAN_DELETE:
                mBookModel.deleteLoan();
                populateLoanedToField(null);
                return true;

            default:
                return super.onContextItemSelected(menuItem);
        }
    }

    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     *
     * <a href="https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures">
     * detect-a-subset-of-supported-gestures</a>
     */
    private class FlingHandler
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(@NonNull final MotionEvent e1,
                               @NonNull final MotionEvent e2,
                               final float velocityX,
                               final float velocityY) {

            final FlattenedBooklist fbl = mBookDetailsFragmentModel.getFlattenedBooklist();
            if (fbl == null) {
                return false;
            }

            // Make sure we have considerably more X-velocity than Y-velocity;
            // otherwise it might be a scroll.
            if (Math.abs(velocityX / velocityY) > 2) {
                // Work out which way to move, and do it.
                if (fbl.move(velocityX <= 0)) {
                    final long bookId = fbl.getBookId();
                    // only reload if it's a different book
                    if (bookId != mBookModel.getBook().getId()) {
                        mBookModel.moveTo(bookId);
                        loadFields();
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean onDown(@NonNull final MotionEvent e) {
            return true;
        }
    }
}
