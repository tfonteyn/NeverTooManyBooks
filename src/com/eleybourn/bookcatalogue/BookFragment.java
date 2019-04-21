package com.eleybourn.bookcatalogue;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.adapters.TocAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.LendBookDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * Do NOT assume they are empty by default when populating fields manually.
 */
public class BookFragment
        extends BookBaseFragment
        implements BookManager, BookChangedListener {

    /** Fragment manager tag. */
    public static final String TAG = BookFragment.class.getSimpleName();

    static final String REQUEST_BKEY_FLAT_BOOKLIST_POSITION = "FBLP";
    static final String REQUEST_BKEY_FLAT_BOOKLIST = "FBL";

    /**
     * The one and only book we're viewing.
     * Always use {@link #getBook()} and {@link #setBook(Book)} to access.
     * We've had to move the book object before... this makes it easier if we do again.
     */
    private Book mBook;

    /** Handles cover replacement, rotation, etc. */
    private CoverHandler mCoverHandler;

    private BaseActivity mActivity;

    private FlattenedBooklist mFlattenedBooklist;
    private GestureDetector mGestureDetector;

    //<editor-fold desc="BookManager interface">

    @Override
    @NonNull
    public BookManager getBookManager() {
        return this;
    }

    @Override
    @NonNull
    public Book getBook() {
        return mBook;
    }

    @Override
    public void setBook(@NonNull final Book book) {
        mBook = book;
    }

    //</editor-fold>

    //<editor-fold desc="Fragment startup">

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * {@link BookManager#getBook()}
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        mActivity = (BaseActivity) requireActivity();
        // parent takes care of loading the book.
        super.onActivityCreated(savedInstanceState);

        initBooklist(savedInstanceState);

        if (savedInstanceState == null) {
            HintManager.displayHint(mActivity.getLayoutInflater(),
                                    R.string.hint_view_only_help,
                                    null);
        }
    }

    /**
     * Set the current visible book id int the result code.
     */
    private void setDefaultActivityResult() {
        Intent data = new Intent().putExtra(DBDefinitions.KEY_ID, getBook().getId());
        mActivity.setResult(Activity.RESULT_OK, data);
    }

    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();
        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        // not added here: non-text TOC

        // book fields
        mFields.add(R.id.title, DBDefinitions.KEY_TITLE);
        mFields.add(R.id.isbn, DBDefinitions.KEY_ISBN);
        mFields.add(R.id.description, DBDefinitions.KEY_DESCRIPTION)
               .setShowHtml(true);
        mFields.add(R.id.genre, DBDefinitions.KEY_GENRE);
        mFields.add(R.id.language, DBDefinitions.KEY_LANGUAGE)
               .setFormatter(new Fields.LanguageFormatter());
        mFields.add(R.id.pages, DBDefinitions.KEY_PAGES)
               .setFormatter((field, source) -> {
                   if (source != null && !source.isEmpty() && !"0".equals(source)) {
                       try {
                           int pages = Integer.parseInt(source);
                           return getString(R.string.lbl_x_pages, pages);
                       } catch (NumberFormatException ignore) {
                           // don't log, both formats are valid.
                       }
                       // stored pages was alphanumeric.
                       return source;
                   }
                   return "";
               });
        mFields.add(R.id.format, DBDefinitions.KEY_FORMAT);
        mFields.add(R.id.price_listed, DBDefinitions.KEY_PRICE_LISTED)
               .setFormatter(new Fields.PriceFormatter());
        mFields.add(R.id.first_publication, DBDefinitions.KEY_DATE_FIRST_PUBLISHED)
               .setFormatter(dateFormatter);

        // defined, but handled manually
        mFields.add(R.id.author, "", DBDefinitions.KEY_AUTHOR);
        // defined, but handled manually
        mFields.add(R.id.series, "", DBDefinitions.KEY_SERIES);

        // populated, but manually re-populated.
        mFields.add(R.id.publisher, DBDefinitions.KEY_PUBLISHER);
        // not a field on the screen, but used in re-population of publisher.
        mFields.add(R.id.date_published, DBDefinitions.KEY_DATE_PUBLISHED)
               .setFormatter(dateFormatter);

        // ENHANCE: {@link Fields.ImageViewAccessor}
//        Field field = mFields.add(R.id.coverImage, UniqueId.KEY_BOOK_UUID, UniqueId.BKEY_COVER_IMAGE);
        Field field = mFields.add(R.id.coverImage, "", UniqueId.BKEY_COVER_IMAGE);
        ImageUtils.DisplaySizes displaySizes = ImageUtils.getDisplaySizes(mActivity);
//        Fields.ImageViewAccessor iva = field.getFieldDataAccessor();
//        iva.setMaxSize(imageSize.standard, imageSize.standard);
        mCoverHandler = new CoverHandler(this, mDb, getBookManager(),
                                         mFields.getField(R.id.isbn), field,
                                         displaySizes.standard, displaySizes.standard);

        // Personal fields
        mFields.add(R.id.date_acquired, DBDefinitions.KEY_DATE_ACQUIRED)
               .setFormatter(dateFormatter);
        mFields.add(R.id.price_paid, DBDefinitions.KEY_PRICE_PAID)
               .setFormatter(new Fields.PriceFormatter());
        mFields.add(R.id.edition, DBDefinitions.KEY_EDITION_BITMASK)
               .setFormatter(new Book.BookEditionsFormatter());
        mFields.add(R.id.location, DBDefinitions.KEY_LOCATION);
        mFields.add(R.id.rating, DBDefinitions.KEY_RATING);
        mFields.add(R.id.notes, DBDefinitions.KEY_NOTES)
               .setShowHtml(true);
        mFields.add(R.id.read_start, DBDefinitions.KEY_READ_START)
               .setFormatter(dateFormatter);
        mFields.add(R.id.read_end, DBDefinitions.KEY_READ_END)
               .setFormatter(dateFormatter);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.read, DBDefinitions.KEY_READ);
        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.signed, DBDefinitions.KEY_SIGNED)
               .setFormatter(new Fields.BinaryYesNoEmptyFormatter(requireContext()));

        // defined, but handled manually
        mFields.add(R.id.bookshelves, "", DBDefinitions.KEY_BOOKSHELF);

        // defined, but handled manually
        mFields.add(R.id.loaned_to, "", DBDefinitions.KEY_LOANEE);
    }

    @CallSuper
    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        // returning here from somewhere else (e.g. from editing the book) and have an ID...reload!
        long bookId = getBook().getId();
        if (bookId != 0) {
            getBook().reload(mDb, bookId);
        }
        // the super will kick of the process that triggers onLoadFieldsFromBook.
        super.onResume();
        Tracker.exitOnResume(this);
    }

    /**
     * At this point we're told to load our local (to the fragment) fields from the Book.
     *
     * @param book       to load from
     * @param setAllFrom flag indicating {@link Fields#setAllFrom(DataManager)}
     *                   has already been called or not
     */
    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {

        // pass the CURRENT currency code to the price formatters
        //TODO: this defeats the ease of use of the formatter... populate manually or something...
        //noinspection ConstantConditions
        ((Fields.PriceFormatter) mFields.getField(R.id.price_listed).getFormatter())
                .setCurrencyCode(book.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        //noinspection ConstantConditions
        ((Fields.PriceFormatter) mFields.getField(R.id.price_paid).getFormatter())
                .setCurrencyCode(book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY));

        super.onLoadFieldsFromBook(book, setAllFrom);

        populateAuthorListField(book);
        populateSeriesListField(book);

        // ENHANCE: {@link Fields.ImageViewAccessor}
        // allow the field to known the uuid of the book, so it can load 'itself'
        mFields.getField(R.id.coverImage).getView().setTag(R.id.TAG_UUID,
                                                           book.get(DBDefinitions.KEY_BOOK_UUID));
        mCoverHandler.updateCoverView();

        // handle 'text' DoNotFetch fields
        ArrayList<Bookshelf> bsList = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        mFields.getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));
        populateLoanedToField(mDb.getLoaneeByBookId(book.getId()));

        // handle non-text fields
        populateToc(book);

        // hide unwanted and empty fields
        showHideFields(true);

        // can't use showHideFields as the field could contain "0" (as a String)
        Field editionsField = mFields.getField(R.id.edition);
        // only bother when it's in use
        if (editionsField.isUsed()) {
            if ("0".equals(editionsField.getValue().toString())) {
                setVisibility(View.GONE, R.id.edition, R.id.lbl_edition);
            } else {
                setVisibility(View.VISIBLE, R.id.edition, R.id.lbl_edition);
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="Init the flat booklist & fling handler">

    /**
     * If we are passed a flat book list, get it and validate it.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void initBooklist(@Nullable final Bundle savedInstanceState) {
        // no arguments ? -> no list!
        if (getArguments() == null) {
            return;
        }
        String list = getArguments().getString(REQUEST_BKEY_FLAT_BOOKLIST);
        if (list == null || list.isEmpty()) {
            return;
        }

        // looks like we have a list, but...
        mFlattenedBooklist = new FlattenedBooklist(mDb, list);
        // Check to see it really exists. The underlying table disappeared once in testing
        // which is hard to explain; it theoretically should only happen if the app closes
        // the database or if the activity pauses with 'isFinishing()' returning true.
        if (!mFlattenedBooklist.exists()) {
            mFlattenedBooklist.close();
            mFlattenedBooklist = null;
            return;
        }

        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;
        // ok, we absolutely have a list, get the position we need to be on.
        int pos = args.getInt(REQUEST_BKEY_FLAT_BOOKLIST_POSITION, 0);

        mFlattenedBooklist.moveTo(pos);
        // the book might have moved around. So see if we can find it.
        while (mFlattenedBooklist.getBookId() != getBook().getId()) {
            if (!mFlattenedBooklist.moveNext()) {
                break;
            }
        }

        if (mFlattenedBooklist.getBookId() != getBook().getId()) {
            // book not found ? eh? give up...
            mFlattenedBooklist.close();
            mFlattenedBooklist = null;
            return;
        }

        //ENHANCE: could probably be replaced by a ViewPager
        // finally, enable the listener for flings
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        requireView().setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
    }
    //</editor-fold>

    //<editor-fold desc="Populate">

    /**
     * The author field is a single csv String.
     */
    private void populateAuthorListField(@NonNull final Book book) {
        Field field = mFields.getField(R.id.author);
        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        int authorsCount = list.size();
        // yes, there should not be a book without authors. But let's keep this 'proper'
        boolean visible = authorsCount != 0;
        if (visible) {
            field.setValue(Csv.join(", ", list, Author::getLabel));
            field.getView().setVisibility(View.VISIBLE);
        } else {
            field.setValue("");
            field.getView().setVisibility(View.GONE);
        }
    }

    /**
     * The series field is a single String with line-breaks between multiple series.
     */
    private void populateSeriesListField(@NonNull final Book book) {
        Field field = mFields.getField(R.id.series);
        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        int seriesCount = list.size();

        boolean visible = seriesCount != 0 && Fields.isUsed(DBDefinitions.KEY_SERIES);
        if (visible) {
            field.setValue(Csv.join("\n", list, Series::getLabel));
            setVisibility(View.VISIBLE, R.id.series, R.id.lbl_series);
        } else {
            field.setValue("");
            setVisibility(View.GONE, R.id.series, R.id.lbl_series);
        }
    }


    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * Allows returning the book via a context menu.
     *
     * @param loanee the one who shall not be mentioned.
     */
    private void populateLoanedToField(@Nullable final String loanee) {
        Field field = mFields.getField(R.id.loaned_to);
        if (loanee != null && !loanee.isEmpty()) {
            field.setValue(getString(R.string.lbl_loaned_to_name, loanee));
            field.getView().setVisibility(View.VISIBLE);
            field.getView().setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                /**
                 * yes, icons are not supported here, but:
                 * TODO: convert to SimpleDialog context menu.... if I can be bothered.
                 */
                @Override
                @CallSuper
                public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                                @NonNull final View v,
                                                @NonNull final ContextMenu.ContextMenuInfo menuInfo) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_RETURNED, 0,
                             R.string.menu_loan_return_book)
                        .setIcon(R.drawable.ic_people);
                }
            });
        } else {
            field.setValue("");
            field.getView().setVisibility(View.GONE);
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateToc(@NonNull final Book book) {
        //ENHANCE: add to mFields?
        ArrayList<TocEntry> tocList = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        // only show if: field in use + it's flagged as having a toc + the toc actually has titles
        boolean visible = Fields.isUsed(DBDefinitions.KEY_TOC_BITMASK)
                && book.isBitSet(DBDefinitions.KEY_TOC_BITMASK, TocEntry.Type.MULTIPLE_WORKS)
                && !tocList.isEmpty();

        // tocLabel, tocButton and tocView are View.GONE in the layout xml.
        View tocLabel = requireView().findViewById(R.id.lbl_toc);
        View tocButton = requireView().findViewById(R.id.toc_button);

        if (visible) {
            ListView tocView = requireView().findViewById(R.id.toc);
            tocView.setAdapter(new TocAdapter(mActivity,
                                              R.layout.row_toc_entry_with_author, tocList));

            tocButton.setOnClickListener(v -> {
                if (tocView.getVisibility() == View.VISIBLE) {
                    tocView.setVisibility(View.GONE);
                } else {
                    tocView.setVisibility(View.VISIBLE);
                    ViewUtils.adjustListViewHeightBasedOnChildren(tocView);
                }
            });

            ViewUtils.adjustListViewHeightBasedOnChildren(tocView);
            tocLabel.setVisibility(View.VISIBLE);
            tocButton.setVisibility(View.VISIBLE);

        } else {
            tocLabel.setVisibility(View.GONE);
            tocButton.setVisibility(View.GONE);
        }
    }
    //</editor-fold>

    //<editor-fold desc="Fragment shutdown">

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     * <p>
     * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
     * cleaned up, then the underlying SQLiteDatabase gets double-dereference'd, resulting in
     * the database being closed by the deeply dodgy auto-close code in Android.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
            if (mActivity.isFinishing()) {
                mFlattenedBooklist.deleteData();
            }
        }

        mCoverHandler.dismissCoverBrowser();

        // set the current visible book id
        setDefaultActivityResult();

        super.onPause();
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mFlattenedBooklist != null) {
            outState.putInt(REQUEST_BKEY_FLAT_BOOKLIST_POSITION,
                            (int) mFlattenedBooklist.getPosition());
        }
    }

    //</editor-fold>

    //<editor-fold desc="Menu handlers">

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_LOAN_RETURNED:
                mDb.deleteLoan(getBook().getId());
                populateLoanedToField(null);
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit_book)
            .setIcon(R.drawable.ic_edit)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);
        menu.add(Menu.NONE, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate)
            .setIcon(R.drawable.ic_content_copy);

        // Only one of these two is made visible.
        menu.add(R.id.MENU_BOOK_READ, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read);
        menu.add(R.id.MENU_BOOK_UNREAD, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread);

        if (Fields.isUsed(DBDefinitions.KEY_LOANEE)) {
            menu.add(R.id.MENU_BOOK_EDIT_LOAN,
                     R.id.MENU_BOOK_EDIT_LOAN, MenuHandler.MENU_ORDER_LENDING,
                     R.string.menu_loan_lend_book);
            menu.add(R.id.MENU_BOOK_LOAN_RETURNED,
                     R.id.MENU_BOOK_LOAN_RETURNED, MenuHandler.MENU_ORDER_LENDING,
                     R.string.menu_loan_return_book);
        }

        menu.add(Menu.NONE, R.id.MENU_SHARE, MenuHandler.MENU_ORDER_SHARE, R.string.menu_share_this)
            .setIcon(R.drawable.ic_share);

        MenuHandler.addAmazonSearchSubMenu(menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        boolean bookExists = getBook().getId() != 0;

        boolean isRead = getBook().getBoolean(Book.IS_READ);
        menu.setGroupVisible(R.id.MENU_BOOK_READ, bookExists && !isRead);
        menu.setGroupVisible(R.id.MENU_BOOK_UNREAD, bookExists && isRead);

        if (Fields.isUsed(DBDefinitions.KEY_LOANEE)) {
            boolean isAvailable = mDb.getLoaneeByBookId(getBook().getId()) == null;
            menu.setGroupVisible(R.id.MENU_BOOK_EDIT_LOAN, bookExists && isAvailable);
            menu.setGroupVisible(R.id.MENU_BOOK_LOAN_RETURNED, bookExists && !isAvailable);
        }

        MenuHandler.prepareAmazonSearchSubMenu(menu, getBook());

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        switch (item.getItemId()) {

            case R.id.MENU_BOOK_EDIT:
                Intent intent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, getBook().getId())
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;

            case R.id.MENU_BOOK_DELETE:
                StandardDialogs.deleteBookAlert(mActivity, mDb, getBook().getId(), () -> {
                    mActivity.setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
                    mActivity.finish();
                });
                return true;

            case R.id.MENU_BOOK_DUPLICATE:
                Intent intentDup = new Intent(mActivity, EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, getBook().duplicate());
                startActivityForResult(intentDup, UniqueId.REQ_BOOK_DUPLICATE);
                return true;

            case R.id.MENU_BOOK_READ:
                boolean isRead = getBook().getBoolean(Book.IS_READ);
                // toggle 'read' status
                if (getBook().setRead(mDb, !isRead)) {
                    mFields.getField(R.id.read).setValue(isRead ? "0" : "1");
                }
                return true;

            case R.id.MENU_BOOK_EDIT_LOAN:
                LendBookDialogFragment.show(requireFragmentManager(), getBook());
                return true;

            case R.id.MENU_BOOK_LOAN_RETURNED:
                mDb.deleteLoan(getBook().getId());
                populateLoanedToField(null);
                return true;

            case R.id.MENU_SHARE:
                startActivity(Intent.createChooser(getBook().getShareBookIntent(mActivity),
                                                   getString(R.string.menu_share_this)));
                return true;

            default:
                if (MenuHandler.handleAmazonSearchSubMenu(mActivity, item, getBook())) {
                    return true;
                }
                return super.onOptionsItemSelected(item);
        }

    }
    //</editor-fold>

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        switch (requestCode) {
            case UniqueId.REQ_BOOK_DUPLICATE:
            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    getBook().reload(mDb);
                    // onResume will display the changed book.
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
    public void onBookChanged(final long bookId,
                              final int fieldsChanged,
                              @Nullable final Bundle data) {
        if (data != null) {
            if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                populateLoanedToField(data.getString(DBDefinitions.KEY_LOANEE));
            } else {
                Logger.warnWithStackTrace(this, "bookId=" + bookId,
                                          "fieldsChanged=" + fieldsChanged);
            }
        }
    }

    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     * <p>
     * ENHANCE: use ViewPager?
     */
    private class FlingHandler
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(@NonNull final MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(@NonNull final MotionEvent e1,
                               @NonNull final MotionEvent e2,
                               final float velocityX,
                               final float velocityY) {
            if (mFlattenedBooklist == null) {
                return false;
            }

            // Make sure we have considerably more X-velocity than Y-velocity;
            // otherwise it might be a scroll.
            if (Math.abs(velocityX / velocityY) > 2) {
                boolean moved;
                // Work out which way to move, and do it.
                if (velocityX > 0) {
                    moved = mFlattenedBooklist.movePrev();
                } else {
                    moved = mFlattenedBooklist.moveNext();
                }

                if (moved) {
                    long bookId = mFlattenedBooklist.getBookId();
                    // only reload if it's a new book
                    if (bookId != getBook().getId()) {
                        getBook().reload(mDb, bookId);
                        populateFieldsFromBook();
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }
}
