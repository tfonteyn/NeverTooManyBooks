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
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.adapters.TOCAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.LendBookDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.widgets.CoverHandler;

/**
 * Class for representing read-only book details.
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
    private CoverHandler mCoverHandler;

    private BaseActivity mActivity;

    private FlattenedBooklist mFlattenedBooklist;
    private GestureDetector mGestureDetector;

    /* ------------------------------------------------------------------------------------------ */

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

    /**
     * We're read only.
     *
     * @return <tt>false</tt>
     */
    public boolean isDirty() {
        return false;
    }

    /**
     * We're read only.
     *
     * @param isDirty ignored.
     */
    public void setDirty(final boolean isDirty) {
        // ignore
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

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
        Intent data = new Intent()
                .putExtra(UniqueId.KEY_ID, getBook().getId());
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
        mFields.add(R.id.title, UniqueId.KEY_TITLE);
        mFields.add(R.id.isbn, UniqueId.KEY_ISBN);
        mFields.add(R.id.description, UniqueId.KEY_DESCRIPTION)
               .setShowHtml(true);
        mFields.add(R.id.genre, UniqueId.KEY_GENRE);
        mFields.add(R.id.language, UniqueId.KEY_LANGUAGE)
               .setFormatter(new Fields.LanguageFormatter());
        mFields.add(R.id.pages, UniqueId.KEY_PAGES)
               .setFormatter(new Fields.FieldFormatter() {
                   @NonNull
                   @Override
                   public String format(@NonNull final Field field,
                                        @Nullable final String source) {
                       if (source != null && !source.isEmpty() && !"0".equals(source)) {
                           try {
                               int pages = Integer.parseInt(source);
                               return getString(R.string.lbl_x_pages, pages);
                           } catch (NumberFormatException ignore) {
                           }
                           // stored pages was alphanumeric.
                           return source;
                       }
                       return "";
                   }

                   @NonNull
                   @Override
                   public String extract(@NonNull final Field field,
                                         @NonNull final String source) {
                       throw new UnsupportedOperationException();
                   }
               });
        mFields.add(R.id.format, UniqueId.KEY_FORMAT);
        mFields.add(R.id.price_listed, UniqueId.KEY_PRICE_LISTED)
               .setFormatter(new Fields.PriceFormatter());
        mFields.add(R.id.first_publication, UniqueId.KEY_DATE_FIRST_PUBLISHED)
               .setFormatter(dateFormatter);

        // defined, but handled manually
        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR);
        // defined, but handled manually
        mFields.add(R.id.series, "", UniqueId.KEY_SERIES);

        // populated, but manually re-populated.
        mFields.add(R.id.publisher, UniqueId.KEY_PUBLISHER);
        // not a field on the screen, but used in re-population of publisher.
        mFields.add(R.id.date_published, UniqueId.KEY_DATE_PUBLISHED)
               .setFormatter(dateFormatter);

        // ENHANCE: {@link Fields.ImageViewAccessor}
//        Field field = mFields.add(R.id.coverImage, UniqueId.KEY_BOOK_UUID, UniqueId.BKEY_COVER_IMAGE);
        Field field = mFields.add(R.id.coverImage, "", UniqueId.BKEY_COVER_IMAGE);
        ImageUtils.ImageSize imageSize = ImageUtils.getImageSizes(mActivity);
//        Fields.ImageViewAccessor iva = field.getFieldDataAccessor();
//        iva.setMaxSize(imageSize.small, imageSize.standard);
        mCoverHandler = new CoverHandler(this, mDb, getBookManager(),
                                         mFields.getField(R.id.isbn), field,
                                         imageSize.small, imageSize.standard);

        // Personal fields
        mFields.add(R.id.date_acquired, UniqueId.KEY_DATE_ACQUIRED)
               .setFormatter(dateFormatter);
        mFields.add(R.id.price_paid, UniqueId.KEY_PRICE_PAID)
               .setFormatter(new Fields.PriceFormatter());
        mFields.add(R.id.edition, UniqueId.KEY_EDITION_BITMASK)
               .setFormatter(new Fields.BookEditionsFormatter());
        mFields.add(R.id.location, UniqueId.KEY_LOCATION);
        mFields.add(R.id.rating, UniqueId.KEY_RATING);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES)
               .setShowHtml(true);
        mFields.add(R.id.read_start, UniqueId.KEY_READ_START)
               .setFormatter(dateFormatter);
        mFields.add(R.id.read_end, UniqueId.KEY_READ_END)
               .setFormatter(dateFormatter);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.read, UniqueId.KEY_READ);
        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.signed, UniqueId.KEY_SIGNED)
               .setFormatter(new Fields.BinaryYesNoEmptyFormatter(requireContext()));

        // defined, but handled manually
        mFields.add(R.id.bookshelves, "", UniqueId.KEY_BOOKSHELF);

        // defined, but handled manually
        mFields.add(R.id.loaned_to, "", UniqueId.KEY_LOANEE);
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
        // this will kick of the process that triggers onLoadFieldsFromBook.
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
        Tracker.enterOnLoadFieldsFromBook(this, book.getId());

        // pass the CURRENT currency code to the price formatters
        //TODO: this defeats the ease of use of the formatter... populate manually or something...
        ((Fields.PriceFormatter) mFields.getField(R.id.price_listed).getFormatter())
                .setCurrencyCode(book.getString(UniqueId.KEY_PRICE_LISTED_CURRENCY));
        ((Fields.PriceFormatter) mFields.getField(R.id.price_paid).getFormatter())
                .setCurrencyCode(book.getString(UniqueId.KEY_PRICE_PAID_CURRENCY));

        super.onLoadFieldsFromBook(book, setAllFrom);

        populateAuthorListField(book);
        populateSeriesListField(book);

        // ENHANCE: {@link Fields.ImageViewAccessor}
        // allow the field to known the uuid of the book, so it can load 'itself'
        mFields.getField(R.id.coverImage)
               .getView()
               .setTag(R.id.TAG_UUID, book.get(UniqueId.KEY_BOOK_UUID));
        mCoverHandler.updateCoverView();

        // handle 'text' DoNotFetch fields
        ArrayList<Bookshelf> bsList = book.getList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        mFields.getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));
        populateLoanedToField(mDb.getLoaneeByBookId(book.getId()));

        // handle non-text fields
        populateTOC(book);

        // hide unwanted and empty text fields
        showHideFields(true);

        // non-text fields:

        // hide publishing header if no fields populated.
        if (mFields.getField(R.id.publisher).isVisible()
                || mFields.getField(R.id.date_published).isVisible()
                || mFields.getField(R.id.price_listed).isVisible()
                || mFields.getField(R.id.first_publication).isVisible()) {

            requireView().findViewById(R.id.lbl_publishing).setVisibility(View.VISIBLE);
        } else {
            requireView().findViewById(R.id.lbl_publishing).setVisibility(View.GONE);
        }

        // hide baseline view for publisher/date_published if neither visible
        if (mFields.getField(R.id.publisher).isVisible()
                || mFields.getField(R.id.date_published).isVisible()) {
            // use 'invisible' as we need it as a baseline only.
            requireView().findViewById(R.id.lbl_publisher_baseline).setVisibility(View.INVISIBLE);
        } else {
            requireView().findViewById(R.id.lbl_publisher_baseline).setVisibility(View.GONE);
        }

        // can't use showHideFields as the field could contain "0"
        Field editionsField = mFields.getField(R.id.edition);
        if ("0".equals(editionsField.getValue().toString())) {
            requireView().findViewById(R.id.lbl_edition).setVisibility(View.GONE);
            requireView().findViewById(R.id.edition).setVisibility(View.GONE);
        }

        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Init the flat booklist & fling handler">

    /**
     * If we are passed a flat book list, get it and validate it.
     */
    private void initBooklist(@Nullable final Bundle savedInstanceState) {

        if (getArguments() == null) {
            return;
        }
        String list = requireArguments().getString(REQUEST_BKEY_FLAT_BOOKLIST);
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

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
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
        requireView().setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(@NonNull final View v,
                                   @NonNull final MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Populate">

    /**
     * The author field is a single csv String.
     */
    private void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> authors = book.getList(UniqueId.BKEY_AUTHOR_ARRAY);
        int authorsCount = authors.size();
        boolean visible = authorsCount != 0;
        if (visible) {
            StringBuilder builder = new StringBuilder();
            builder.append(getString(R.string.lbl_by_author));
            builder.append(' ');
            for (int i = 0; i < authorsCount; i++) {
                builder.append(authors.get(i).getDisplayName());
                if (i != authorsCount - 1) {
                    builder.append(", ");
                }
            }
            mFields.getField(R.id.author).setValue(builder.toString());
        }
        requireView().findViewById(R.id.author).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * The series field is a single String with line-breaks between multiple series.
     */
    void populateSeriesListField(@NonNull final Book book) {
        ArrayList<Series> list = book.getList(UniqueId.BKEY_SERIES_ARRAY);
        int seriesCount = list.size();
        boolean visible = seriesCount != 0 && Fields.isVisible(UniqueId.KEY_SERIES);
        if (visible) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < seriesCount; i++) {
                builder.append(list.get(i).getDisplayName());
                if (i != seriesCount - 1) {
                    builder.append('\n');
                }
            }

            mFields.getField(R.id.series).setValue(builder.toString());
        }
        View view = requireView();
        view.findViewById(R.id.lbl_series).setVisibility(visible ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.series).setVisibility(visible ? View.VISIBLE : View.GONE);
    }


    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * Allows returning the book via a context menu.
     *
     * @param loanee the one who shall not be mentioned.
     */
    private void populateLoanedToField(@Nullable final String loanee) {
        Field field = mFields.getField(R.id.loaned_to);
        if (loanee == null || loanee.isEmpty()) {
            field.setValue("");
            field.getView().setVisibility(View.GONE);
        } else {
            field.setValue(getString(R.string.lbl_loaned_to_name, loanee));
            field.getView().setVisibility(View.VISIBLE);

            field.getView()
                 .setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                     /**
                      * (yes, icons are not supported and won't show.
                      * Still leaving the setIcon calls in for now.)
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
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateTOC(@NonNull final Book book) {
        //ENHANCE: add to mFields?
        ArrayList<TocEntry> list = book.getList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        // only show if: field in use + it's flagged as having a toc + the toc actually has titles
        boolean visible = Fields.isVisible(UniqueId.KEY_TOC_BITMASK)
                && book.isBitSet(UniqueId.KEY_TOC_BITMASK, TocEntry.Type.MULTIPLE_WORKS)
                && !list.isEmpty();

        View tocLabel = requireView().findViewById(R.id.lbl_toc);
        View tocButton = requireView().findViewById(R.id.toc_button);
        final ListView tocList = requireView().findViewById(R.id.toc);

        if (visible) {
            ArrayAdapter<TocEntry> adapter =
                    new TOCAdapter(mActivity, R.layout.row_toc_entry_with_author, list);
            tocList.setAdapter(adapter);

            tocButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    if (tocList.getVisibility() == View.VISIBLE) {
                        tocList.setVisibility(View.GONE);
                    } else {
                        tocList.setVisibility(View.VISIBLE);
                        ViewUtils.justifyListViewHeightBasedOnChildren(tocList);
                    }
                }
            });
        }

        tocLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
        tocButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     * <p>
     * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
     * cleaned up, then the underlying SQLiteDatabase gets double-dereference'd, resulting in
     * the database being closed by the deeply dodgy auto-close code in Android.
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

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu handlers">
    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_LOAN_RETURNED:
                mDb.deleteLoan(getBook().getId());
                populateLoanedToField(null);
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit_book)
            .setIcon(R.drawable.ic_edit)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        /*
         * Only one of these two is made visible (or none if the book is not persisted yet).
         */
        menu.add(R.id.MENU_BOOK_READ, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read);
        menu.add(R.id.MENU_BOOK_UNREAD, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        boolean bookExists = getBook().getId() != 0;

        boolean isRead = getBook().getBoolean(Book.IS_READ);
        menu.setGroupVisible(R.id.MENU_BOOK_READ, bookExists && !isRead);
        menu.setGroupVisible(R.id.MENU_BOOK_UNREAD, bookExists && isRead);

        if (Fields.isVisible(UniqueId.KEY_LOANEE)) {
            boolean isAvailable = null == mDb.getLoaneeByBookId(getBook().getId());
            menu.setGroupVisible(R.id.MENU_BOOK_EDIT_LOAN, bookExists && isAvailable);
            menu.setGroupVisible(R.id.MENU_BOOK_LOAN_RETURNED, bookExists && !isAvailable);
        }

        super.onPrepareOptionsMenu(menu);
    }

    /**
     * Called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT:
                Intent intent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(UniqueId.KEY_ID, getBook().getId())
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;

            case R.id.MENU_BOOK_READ:
                // toggle 'read' status
                boolean isRead = getBook().getBoolean(Book.IS_READ);
                if (getBook().setRead(mDb, !isRead)) {
                    // reverse value obv.
                    mFields.getField(R.id.read).setValue(isRead ? "0" : "1");
                }
                return true;

            case R.id.MENU_BOOK_EDIT_LOAN:
                FragmentManager fm = requireFragmentManager();
                if (fm.findFragmentByTag(LendBookDialogFragment.TAG) == null) {
                    LendBookDialogFragment.newInstance(getBook())
                                          .show(fm, LendBookDialogFragment.TAG);
                }
                return true;

            case R.id.MENU_BOOK_LOAN_RETURNED:
                mDb.deleteLoan(getBook().getId());
                populateLoanedToField(null);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }

    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        switch (requestCode) {
            case UniqueId.REQ_BOOK_DUPLICATE:
            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    getBook().reload(mDb);
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
                populateLoanedToField(data.getString(UniqueId.KEY_LOANEE));
            } else {
                Logger.error("bookId=" + bookId + ", fieldsChanged=" + fieldsChanged);
            }
        }
    }

    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
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
