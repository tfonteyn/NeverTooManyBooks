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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.LendBookDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.FlattenedBooklistModel;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * Do NOT assume fields are empty by default when populating them manually.
 */
public class BookFragment
        extends BookBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "BookFragment";

    /** Table name of the {@link FlattenedBooklist}. */
    public static final String BKEY_FLAT_BOOKLIST_TABLE = TAG + ":FBL_Table";
    /** Position in the {@link FlattenedBooklist} of this book. Used for left/right swipes. */
    public static final String BKEY_FLAT_BOOKLIST_POSITION = TAG + ":FBL_Position";

    /** Size of the cover image to use. */
    @ImageUtils.Scale
    private static final int IMAGE_SCALE = ImageUtils.SCALE_LARGE;

    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {
        if (data != null) {
            if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                populateLoanedToField(data.getString(DBDefinitions.KEY_LOANEE));
            } else {
                // we don't expect/implement any others.
                //noinspection ConstantConditions
                Logger.warnWithStackTrace(getContext(), this, "bookId=" + bookId,
                                          "fieldsChanged=" + fieldsChanged);
            }
        }
    };

    /** Switch the user can flick to display/hide the TOC (if present). */
    private CompoundButton mTocButton;
    /** We display/hide the TOC header label as needed. */
    private View mTocLabelView;
    /** The TOC list. */
    private LinearLayout mTocView;

    /** The scroll view encompassing the entire fragment page. */
    private NestedScrollView mTopScrollView;

    /** Handles cover replacement, rotation, etc. */
    private CoverHandler mCoverHandler;

    /** Registered with the Activity. */
    private View.OnTouchListener mOnTouchListener;

    /** Handle next/previous paging in the flattened booklist; called by mOnTouchListener. */
    private GestureDetector mGestureDetector;

    /** Contains the flattened book list for next/previous paging. */
    private FlattenedBooklistModel mFlattenedBooklistModel;

    /** Hosting activity to handle FAB/result/touches. */
    private AppCompatActivity mHostActivity;

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mHostActivity = (AppCompatActivity) context;
    }

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
        View view = inflater.inflate(R.layout.fragment_book_details, container, false);
        mTopScrollView = view.findViewById(R.id.topScroller);

        mTocLabelView = view.findViewById(R.id.lbl_toc);
        mTocView = view.findViewById(R.id.toc);

        mTocButton = view.findViewById(R.id.toc_button);
        // show/hide the TOC as the user flips the switch.
        mTocButton.setOnClickListener(v -> {
            // note that the button is explicitly (re)set.
            // If user clicks to fast it gets out of sync.
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

        return view;
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * <ul>All storage interaction is done via:
     * <li>parent / model for loading the book</li>
     * <li>{@link #onLoadFieldsFromBook} from base class onResume</li>
     * </ul>
     * {@inheritDoc}
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        // the parent will tell the model to load the Book.
        // After that it takes care of initialising the Fields.
        // Transferring data from Book to fields is done in onResume.
        super.onActivityCreated(savedInstanceState);

        mFlattenedBooklistModel = new ViewModelProvider(this).get(FlattenedBooklistModel.class);
        mFlattenedBooklistModel.init(getArguments(), mBookModel.getBook().getId());

        FloatingActionButton fabButton = mHostActivity.findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_edit);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> startEditBook());

        // ENHANCE: could probably be replaced by a ViewPager
        // enable the listener for flings
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        mOnTouchListener = (v, event) -> mGestureDetector.onTouchEvent(event);
        //noinspection ConstantConditions
        LocaleUtils.insanityCheck(getContext());
        if (savedInstanceState == null) {
            TipManager.display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    @CallSuper
    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        // The parent will kick of the process that triggers {@link #onLoadFieldsFromBook}.
        super.onResume();
        ((BookDetailsActivity) mHostActivity).registerOnTouchListener(mOnTouchListener);

        Tracker.exitOnResume(this);
    }

    @Override
    @CallSuper
    public void onPause() {
        ((BookDetailsActivity) mHostActivity).unregisterOnTouchListener(mOnTouchListener);

        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        switch (requestCode) {
            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    mBookModel.reload();
                    // onResume will display the changed book.
                }
                break;

            case UniqueId.REQ_BOOK_DUPLICATE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        long id = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (id != 0) {
                            mBookModel.setBook(id);
                        }
                    }
                    mBookModel.reload();
                    // onResume will display the new book, but
                    // FIXME: swiping through the flattened booklist will not see the new book
                    // we basically would need to rebuild the booklist and subsequently
                    // rebuild the flattened book list.... huge overkill.
                }
                break;

            default:
                // handle any cover image request codes
                if (!mCoverHandler.onActivityResult(requestCode, resultCode, data)) {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
        }
    }

    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        // A DateFieldFormatter can be shared between multiple fields.
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        // book fields
        fields.addString(R.id.title, DBDefinitions.KEY_TITLE);

        // defined, but fetched manually
        fields.addString(R.id.author, "", DBDefinitions.KEY_FK_AUTHOR)
              .setShowHtml(true);

        // defined, but fetched manually
        fields.addString(R.id.series, "", DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        Field<String> isbnField = fields.addString(R.id.isbn, DBDefinitions.KEY_ISBN)
                                        .setRelatedFields(R.id.lbl_isbn);

        fields.addString(R.id.description, DBDefinitions.KEY_DESCRIPTION)
              .setShowHtml(true)
              .setRelatedFields(R.id.lbl_description);

        fields.addBoolean(R.id.cbx_anthology, Book.HAS_MULTIPLE_WORKS)
              .setRelatedFields(R.id.lbl_anthology);
        // not added here: actual TOC which is non-text

        fields.addString(R.id.genre, DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        fields.addString(R.id.language, DBDefinitions.KEY_LANGUAGE)
              .setFormatter(new Fields.LanguageFormatter())
              .setRelatedFields(R.id.lbl_language);

        fields.addString(R.id.pages, DBDefinitions.KEY_PAGES)
              .setFormatter(new Fields.PagesFormatter())
              .setRelatedFields(R.id.lbl_pages);

        fields.addString(R.id.format, DBDefinitions.KEY_FORMAT)
              .setFormatter(new Fields.FormatFormatter())
              .setRelatedFields(R.id.lbl_format);

        fields.addString(R.id.publisher, DBDefinitions.KEY_PUBLISHER);

        fields.addString(R.id.date_published, DBDefinitions.KEY_DATE_PUBLISHED)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_date_published);

        fields.addString(R.id.first_publication, DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_first_publication);

        fields.addString(R.id.print_run, DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        // defined, but fetched manually
        fields.addMonetary(R.id.price_listed, "", DBDefinitions.KEY_PRICE_LISTED)
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        Field<String> coverImageField =
                fields.addString(R.id.coverImage, DBDefinitions.KEY_BOOK_UUID, UniqueId.BKEY_IMAGE)
                      .setScale(IMAGE_SCALE);

        mCoverHandler = new CoverHandler(this, mBookModel.getDb(),
                                         mBookModel.getBook(),
                                         isbnField.getView(),
                                         coverImageField.getView(),
                                         IMAGE_SCALE);

        // Personal fields
        fields.addString(R.id.date_acquired, DBDefinitions.KEY_DATE_ACQUIRED)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.addLong(R.id.edition, DBDefinitions.KEY_EDITION_BITMASK)
              .setFormatter(new Fields.BitMaskFormatter(Book.EDITIONS))
              .setRelatedFields(R.id.lbl_edition);

        fields.addString(R.id.location, DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.addFloat(R.id.rating, DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.addString(R.id.notes, DBDefinitions.KEY_PRIVATE_NOTES)
              .setShowHtml(true)
              .setRelatedFields(R.id.lbl_notes);

        fields.addString(R.id.read_start, DBDefinitions.KEY_READ_START)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_read_start);

        fields.addString(R.id.read_end, DBDefinitions.KEY_READ_END)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_read_end);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        fields.addBoolean(R.id.cbx_read, DBDefinitions.KEY_READ);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        fields.addBoolean(R.id.cbx_signed, DBDefinitions.KEY_SIGNED)
              .setRelatedFields(R.id.lbl_signed);

        // defined, but fetched manually
        fields.addMonetary(R.id.price_paid, "", DBDefinitions.KEY_PRICE_PAID)
              .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);

        // defined, but fetched manually
        fields.addString(R.id.bookshelves, "", DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        // defined, but fetched manually
        fields.addString(R.id.loaned_to, "", DBDefinitions.KEY_LOANEE);
    }

    /**
     * <p>
     * At this point we're told to load our local (to the fragment) fields from the Book.
     * </p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        populateAuthorListField();
        populateSeriesListField();
        populateBookshelvesField();
        populatePriceFields();

        // handle non-text fields
        populateLoanedToField(mBookModel.getLoanee());
        populateToc();

        // hide unwanted and empty fields
        showOrHideFields(true);

        // Hide the Publication section label if none of the publishing fields are shown.
        setSectionLabelVisibility(R.id.lbl_publication_section,
                                  R.id.publisher,
                                  R.id.date_published,
                                  R.id.price_listed,
                                  R.id.first_publication);

        // Hide the Notes label if none of the notes fields are shown.
        setSectionLabelVisibility(R.id.lbl_notes,
                                  R.id.notes,
                                  R.id.lbl_edition,
                                  R.id.lbl_signed,
                                  R.id.lbl_date_acquired,
                                  R.id.lbl_price_paid,
                                  R.id.lbl_read_start,
                                  R.id.lbl_read_end,
                                  R.id.lbl_location);
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);
        menu.add(Menu.NONE, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate)
            .setIcon(R.drawable.ic_content_copy);

        // Only one of these two is made visible.
        menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read);
        menu.add(Menu.NONE, R.id.MENU_BOOK_UNREAD, 0, R.string.menu_set_unread);

        menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                 MenuHandler.ORDER_UPDATE_FIELDS, R.string.menu_update_fields)
            .setIcon(R.drawable.ic_cloud_download);

        if (App.isUsed(DBDefinitions.KEY_LOANEE)) {
            // Only one of these two is made visible.
            menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_ADD,
                     MenuHandler.ORDER_LENDING, R.string.menu_loan_lend_book);
            menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                     MenuHandler.ORDER_LENDING, R.string.menu_loan_return_book);
        }

        menu.add(Menu.NONE, R.id.MENU_SHARE, MenuHandler.ORDER_SHARE, R.string.menu_share_this)
            .setIcon(R.drawable.ic_share);

        menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS,
                 MenuHandler.ORDER_SEND_TO_GOODREADS, R.string.gr_menu_send_to_goodreads)
            .setIcon(R.drawable.ic_goodreads);

        MenuHandler.addViewBookSubMenu(menu);
        MenuHandler.addAmazonSearchSubMenu(menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        Book book = mBookModel.getBook();

        boolean isExistingBook = mBookModel.isExistingBook();
        boolean isRead = book.getBoolean(Book.IS_READ);

        menu.findItem(R.id.MENU_BOOK_READ).setVisible(isExistingBook && !isRead);
        menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isExistingBook && isRead);

        if (App.isUsed(DBDefinitions.KEY_LOANEE)) {
            boolean isAvailable = mBookModel.isAvailable();
            menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(isExistingBook && isAvailable);
            menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(isExistingBook && !isAvailable);
        }

        MenuHandler.prepareViewBookSubMenu(menu, book);
        MenuHandler.prepareAmazonSearchSubMenu(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        Book book = mBookModel.getBook();

        switch (item.getItemId()) {

            case R.id.MENU_DELETE:
                String title = book.getString(DBDefinitions.KEY_TITLE);
                List<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                //noinspection ConstantConditions
                StandardDialogs.deleteBookAlert(getContext(), title, authors, () -> {
                    mBookModel.getDb().deleteBook(book.getId());

                    mResultDataModel.putExtra(UniqueId.BKEY_SOMETHING_WAS_DELETED, true);
                    mHostActivity.setResult(Activity.RESULT_OK, mResultDataModel.getData());
                    mHostActivity.finish();
                });
                return true;

            case R.id.MENU_BOOK_DUPLICATE:
                Intent dupIntent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, UniqueId.REQ_BOOK_DUPLICATE);
                return true;

            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD:
                // toggle 'read' status of the book
                boolean isRead = mBookModel.toggleRead();
                getField(R.id.cbx_read).setValue(isRead);
                return true;

            case R.id.MENU_BOOK_LOAN_ADD:
                FragmentManager fm = getChildFragmentManager();
                LendBookDialogFragment lendBookDialogFragment =
                        (LendBookDialogFragment) fm.findFragmentByTag(LendBookDialogFragment.TAG);
                if (lendBookDialogFragment == null) {
                    //noinspection ConstantConditions
                    lendBookDialogFragment = LendBookDialogFragment.newInstance(getContext(), book);
                    lendBookDialogFragment.show(fm, LendBookDialogFragment.TAG);
                }
                return true;

            case R.id.MENU_BOOK_LOAN_DELETE:
                mBookModel.deleteLoan();
                populateLoanedToField(null);
                return true;

            case R.id.MENU_SHARE:
                //noinspection ConstantConditions
                startActivity(Intent.createChooser(book.getShareBookIntent(getContext()),
                                                   getString(R.string.menu_share_this)));
                return true;

            case R.id.MENU_BOOK_SEND_TO_GOODREADS:
                //noinspection ConstantConditions
                UserMessage.show(getView(), R.string.progress_msg_connecting);
                new SendOneBookTask(book.getId(), mBookModel.getGoodreadsTaskListener())
                        .execute();
                return true;

            default:
                //noinspection ConstantConditions
                if (MenuHandler.handleViewBookSubMenu(getContext(), item, book)) {
                    return true;
                }

                if (MenuHandler.handleAmazonSearchSubMenu(getContext(), item, book)) {
                    return true;
                }
                // MENU_BOOK_UPDATE_FROM_INTERNET handled in super
                return super.onOptionsItemSelected(item);
        }
    }

    private void startEditBook() {
        Intent editIntent = new Intent(getContext(), EditBookActivity.class)
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
     * The author field is a single csv String.
     */
    private void populateAuthorListField() {
        Book book = mBookModel.getBook();

        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        Field<String> field = getField(R.id.author);
        field.setValue(Csv.join("<br>", list, false, "• ",
                                this::formatAuthor));
    }

    /**
     * The formatter for the {@link Csv#join} used by {@link #populateAuthorListField()}.
     *
     * @param author to format
     *
     * @return HTML formatted author with optional type
     */
    private String formatAuthor(@NonNull final Author author) {
        final Context context = getContext();
        //noinspection ConstantConditions
        String authorLabel = author.getLabel(context);
        if (App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE)) {
            String type = author.getTypeLabels(context);
            if (!type.isEmpty()) {
                authorLabel += " <small><i>" + type + "</i></small>";
            }
        }

        return authorLabel;
    }

    /**
     * The Series field is a single String with line-breaks between multiple Series.
     * Each line will be prefixed with a "• "
     */
    private void populateSeriesListField() {
        Book book = mBookModel.getBook();

        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        //noinspection ConstantConditions
        getField(R.id.series).setValue(Csv.join("\n", list, false, "• ",
                                                series -> series.getLabel(getContext())));
    }

    /**
     * The bookshelves field is a single csv String.
     */
    private void populateBookshelvesField() {
        Book book = mBookModel.getBook();

        ArrayList<Bookshelf> list = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        getField(R.id.bookshelves).setValue(Csv.join(", ", list, Bookshelf::getName));
    }

    /**
     * We need to use the <strong>current</strong> currency code, so we cannot define (easily)
     * the formatter in {@link #initFields()}.
     * <p>
     * Using a formatter object is a little overkill, but this leaves future changes easier.
     */
    private void populatePriceFields() {
        Book book = mBookModel.getBook();

        Field<Double> field;

        Fields.MonetaryFormatter listedFormatter = new Fields.MonetaryFormatter()
                .setCurrencyCode(book.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        field = getField(R.id.price_listed);
        field.setFormatter(listedFormatter)
             .setValue(book.getDouble(DBDefinitions.KEY_PRICE_LISTED));

        Fields.MonetaryFormatter paidFormatter = new Fields.MonetaryFormatter()
                .setCurrencyCode(book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY));
        field = getField(R.id.price_paid);
        field.setFormatter(paidFormatter)
             .setValue(book.getDouble(DBDefinitions.KEY_PRICE_PAID));
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
        Field<String> field = getField(R.id.loaned_to);

        View fieldView = field.getView();
        if (loanee != null && !loanee.isEmpty()) {
            field.setValue(getString(R.string.lbl_loaned_to_name, loanee));
            fieldView.setVisibility(View.VISIBLE);
            fieldView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                /**
                 * yes, icons are not supported here, but:
                 * TODO: convert to MenuPicker context menu.... if I can be bothered.
                 */
                @Override
                @CallSuper
                public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                                @NonNull final View v,
                                                @NonNull
                                                final ContextMenu.ContextMenuInfo menuInfo) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                             MenuHandler.ORDER_LENDING, R.string.menu_loan_return_book)
                        .setIcon(R.drawable.ic_people);
                }
            });
        } else {
            fieldView.setVisibility(View.GONE);
            field.setValue("");
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateToc() {
        Book book = mBookModel.getBook();

        // we can get called more than once (when user moves sideways to another book),
        // so clear and hide/disable the view before populating it.
        // Actual visibility is handled after building the list.
        mTocView.removeAllViews();
        mTocView.setVisibility(View.GONE);
        mTocButton.setChecked(false);

        ArrayList<TocEntry> tocList = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        if (!tocList.isEmpty()) {
            Context context = getContext();
            for (TocEntry item : tocList) {
                View rowView = getLayoutInflater().inflate(R.layout.row_toc_entry_with_author,
                                                           mTocView, false);

                TextView titleView = rowView.findViewById(R.id.title);
                TextView authorView = rowView.findViewById(R.id.author);
                TextView firstPubView = rowView.findViewById(R.id.year);

                titleView.setText(item.getTitle());

                // optional
                if (authorView != null) {
                    //noinspection ConstantConditions
                    authorView.setText(item.getAuthor().getLabel(context));
                }
                // optional
                if (firstPubView != null) {
                    String year = item.getFirstPublication();
                    if (year.isEmpty()) {
                        firstPubView.setVisibility(View.GONE);
                    } else {
                        firstPubView.setVisibility(View.VISIBLE);
                        //noinspection ConstantConditions
                        firstPubView.setText(context.getString(R.string.brackets, year));
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
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     *
     * <a href="https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures">
     * https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures</a>
     */
    private class FlingHandler
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(@NonNull final MotionEvent e1,
                               @NonNull final MotionEvent e2,
                               final float velocityX,
                               final float velocityY) {

            FlattenedBooklist fbl = mFlattenedBooklistModel.getFlattenedBooklist();
            if (fbl == null) {
                return false;
            }

            // Make sure we have considerably more X-velocity than Y-velocity;
            // otherwise it might be a scroll.
            if (Math.abs(velocityX / velocityY) > 2) {
                boolean moved;
                // Work out which way to move, and do it.
                if (velocityX > 0) {
                    moved = fbl.movePrev();
                } else {
                    moved = fbl.moveNext();
                }

                if (moved) {
                    long bookId = fbl.getBookId();
                    // only reload if it's a new book
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
