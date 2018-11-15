package com.eleybourn.bookcatalogue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.CoverHandler;

import java.util.ArrayList;

import static android.view.GestureDetector.SimpleOnGestureListener;

/**
 * Class for representing read-only book details.
 */
public class BookFragment extends BookAbstractFragment implements BookManager {

    public static final String TAG = "BookFragment";

    public static final String REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION = "FBLP";
    public static final String REQUEST_BKEY_FLATTENED_BOOKLIST = "FBL";

    private Book mBook;
    private CoverHandler mCoverHandler;

    private BaseActivity mActivity;

    private FlattenedBooklist mFlattenedBooklist = null;
    private GestureDetector mGestureDetector;

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="BookManager interface">
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
    public void setBook(final @NonNull Book book) {
        mBook = book;
    }

    public boolean isDirty() {
        return mActivity.isDirty();
    }

    public void setDirty(final boolean isDirty) {
        mActivity.setDirty(isDirty);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);

        mActivity = (BaseActivity) context;
    }

//    @Override
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    /**
     * has no specific Arguments or savedInstanceState as all is done via
     * {@link BookManager#getBook()}
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initBooklist(getArguments(), savedInstanceState);

        if (savedInstanceState == null) {
            HintManager.displayHint(mActivity.getLayoutInflater(), R.string.hint_view_only_help, null);
        }
    }

    /**
     * Some fields are only present (or need specific handling) on {@link EditBookFieldsFragment}
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();
        // not added here are the two non-text fields: TOC & Read.

        // book fields
        // ENHANCE: simplify the SQL and use a formatter instead.
        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR_FORMATTED);

        mFields.add(R.id.filename, UniqueId.KEY_SERIES_NAME);
        mFields.add(R.id.title, UniqueId.KEY_TITLE);
        mFields.add(R.id.isbn, UniqueId.KEY_BOOK_ISBN);
        mFields.add(R.id.description, UniqueId.KEY_DESCRIPTION)
                .setShowHtml(true);
        mFields.add(R.id.genre, UniqueId.KEY_BOOK_GENRE);
        mFields.add(R.id.language, UniqueId.KEY_BOOK_LANGUAGE);
        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES);
        mFields.add(R.id.format, UniqueId.KEY_BOOK_FORMAT);
        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER);

        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED)
                .setFormatter(new Fields.DateFieldFormatter());

        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION)
                .setFormatter(new Fields.DateFieldFormatter());

        mFields.add(R.id.price_listed, UniqueId.KEY_BOOK_PRICE_LISTED)
                .setFormatter(new Fields.PriceFormatter(getBook().getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY)));

        // add the cover image
        Field coverField = mFields.add(R.id.coverImage, "", UniqueId.BKEY_HAVE_THUMBNAIL)
                .setDoNotFetch(true);
        mCoverHandler = new CoverHandler(mActivity, mDb, getBookManager(),
                coverField, mFields.getField(R.id.isbn));


        // Personal fields
        mFields.add(R.id.bookshelves, UniqueId.KEY_BOOKSHELF_NAME)
                .setDoNotFetch(true);

        mFields.add(R.id.date_acquired, UniqueId.KEY_BOOK_DATE_ACQUIRED)
                .setFormatter(new Fields.DateFieldFormatter());

        mFields.add(R.id.price_paid, UniqueId.KEY_BOOK_PRICE_PAID)
                .setFormatter(new Fields.PriceFormatter(getBook().getString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY)));

        mFields.add(R.id.edition, UniqueId.KEY_BOOK_EDITION_BITMASK)
                .setFormatter(new Fields.BookEditionsFormatter());

        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED)
                .setFormatter(new Fields.BinaryYesNoEmptyFormatter(this.getResources()));

        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION);
        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES)
                .setShowHtml(true);

        mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START)
                .setFormatter(new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END)
                .setFormatter(new Fields.DateFieldFormatter());

        mFields.add(R.id.loaned_to, UniqueId.KEY_LOAN_LOANED_TO)
                .setDoNotFetch(true);
    }

    /**
     * returning here from somewhere else (e.g. from editing the book) and have an ID...reload!
     */
    @CallSuper
    @Override
    public void onResume() {
        long bookId = getBook().getBookId();
        if (bookId != 0) {
            getBook().reload(bookId);
        }
        // this will kick of the process that triggers onLoadFieldsFromBook
        super.onResume();
    }

    /**
     * At this point we're told to load our local (to the fragment) fields from the Book.
     *
     * @param book       to load from
     * @param setAllFrom flag indicating {@link Fields#setAllFrom(DataManager)} has already been called or not
     */
    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(final @NonNull Book book, final boolean setAllFrom) {
        super.onLoadFieldsFromBook(book, setAllFrom);

        populateAuthorListField(book);
        populateSeriesListField(book);

        // override setting the cover as we want a bigger size.
        ImageUtils.ThumbSize ts = ImageUtils.getThumbSizes(mActivity);
        mCoverHandler.populateCoverView(ts.small, ts.standard);

        // handle 'text' DoNotFetch fields
        mFields.getField(R.id.bookshelves).setValue(book.getBookshelfListAsText());
        populateLoanedToField(book.getBookId());

        // handle composite fields
        populateFormatSection(book);
        populatePublishingSection(book);

        // handle non-text fields
        populateTOC(book);
        populateReadStatus(book);

        // hide unwanted and empty fields
        showHideFields(true);

        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(this, "onLoadFieldsFromBook done");
        }
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Init the flat booklist & fling handler">

    /**
     * If we are passed a flat book list, get it and validate it
     */
    private void initBooklist(final @Nullable Bundle args,
                              final @Nullable Bundle savedInstanceState) {
        if (args == null) {
            return;
        }

        String list = args.getString(REQUEST_BKEY_FLATTENED_BOOKLIST);
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

        // ok, we absolutely have a list, get the position we need to be on.
        int pos = BundleUtils.getIntFromBundles(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION, savedInstanceState, args);

        mFlattenedBooklist.moveTo(pos);
        // the book might have moved around. So see if we can find it.
        while (mFlattenedBooklist.getBookId() != getBook().getBookId()) {
            if (!mFlattenedBooklist.moveNext()) {
                break;
            }
        }

        if (mFlattenedBooklist.getBookId() != getBook().getBookId()) {
            // book not found ? eh? give up...
            mFlattenedBooklist.close();
            mFlattenedBooklist = null;
            return;
        }

        // finally, enable the listener for flings
        initGestureDetector();
    }

    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     */
    private void initGestureDetector() {
        mGestureDetector = new GestureDetector(this.getContext(), new SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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
                        if (bookId != getBook().getBookId()) {
                            getBook().reload(bookId);
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });

        //noinspection ConstantConditions
        getView().setOnTouchListener(new View.OnTouchListener() {

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(final @NonNull View v, final @NonNull MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    return mGestureDetector != null && mGestureDetector.onTouchEvent(event);
                } else {
                    return false;
                }
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Populate">

    private void populateAuthorListField(final @NonNull Book book) {
        ArrayList<Author> authors = book.getAuthorList();
        int authorsCount = authors.size();
        boolean visible = authorsCount != 0;
        if (visible) {
            StringBuilder builder = new StringBuilder();
            builder.append(getString(R.string.lbl_by_author));
            builder.append(" ");
            for (int i = 0; i < authorsCount; i++) {
                builder.append(authors.get(i).getDisplayName());
                if (i != authorsCount - 1) {
                    builder.append(", ");
                }
            }
            mFields.getField(R.id.author).setValue(builder.toString());
        }
        //noinspection ConstantConditions
        getView().findViewById(R.id.author).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    protected void populateSeriesListField(final @NonNull Book book) {
        ArrayList<Series> list = book.getSeriesList();
        int seriesCount = list.size();
        boolean visible = seriesCount != 0 && mFields.getField(R.id.filename).visible;
        if (visible) {
            Series.pruneSeriesList(list);
            Utils.pruneList(mDb, list);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < seriesCount; i++) {
                builder.append("    ").append(list.get(i).getDisplayName());
                if (i != seriesCount - 1) {
                    builder.append("\n");
                }
            }

            mFields.getField(R.id.filename).setValue(builder.toString());
        }
        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_series).setVisibility(visible ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.filename).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Formats 'format' section of the book depending on values of 'pages' and 'format' fields.
     */
    private void populateFormatSection(final @NonNull Book book) {
        Field pagesField = mFields.getField(R.id.pages);
        String pages = book.getString(UniqueId.KEY_BOOK_PAGES);
        boolean hasPages = !pages.isEmpty();
        if (hasPages) {
            pages = getString(R.string.lbl_x_pages, pages);
        }
        pagesField.setValue(pages);


        Field formatField = mFields.getField(R.id.format);
        String format = book.getString(UniqueId.KEY_BOOK_FORMAT);
        boolean hasFormat = !format.isEmpty();
        if (hasPages && hasFormat) {
            formatField.setValue(getString(R.string.brackets, format));
        } else {
            formatField.setValue(format);
        }
    }

    /**
     * Formats 'Publishing' section depending on values of 'publisher' and 'date published' fields.
     */
    private void populatePublishingSection(final @NonNull Book book) {

        Field datePublishedField = mFields.getField(R.id.date_published);
        String datePublished = datePublishedField.format(book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED));
        boolean hasPublishDate = !datePublished.isEmpty();

        String pub = book.getString(UniqueId.KEY_BOOK_PUBLISHER);
        boolean hasPublisher = !pub.isEmpty();

        String result = "";
        if (hasPublisher && hasPublishDate) {
            // combine publisher and date into one field
            result = pub + " (" + datePublished + ")";
        } else if (hasPublisher) {
            result = pub;
        } else if (hasPublishDate) {
            result = datePublished;
        }

        mFields.getField(R.id.publisher).setValue(result);
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     *
     * @param bookId of the loaned book
     */
    private void populateLoanedToField(final long bookId) {
        String personLoanedTo = mDb.getLoanByBookId(bookId);
        personLoanedTo = (personLoanedTo == null ? "" : getString(R.string.loan_book_details_readonly_loaned_to, personLoanedTo));
        mFields.getField(R.id.loaned_to).setValue(personLoanedTo);

        mFields.getField(R.id.loaned_to).getView()
                .setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                    /**
                     * (yes, icons are not supported and won't show. Still leaving the setIcon calls in for now.)
                     */
                    @Override
                    @CallSuper
                    public void onCreateContextMenu(final @NonNull ContextMenu menu,
                                                    final @NonNull View v,
                                                    final @NonNull ContextMenu.ContextMenuInfo menuInfo) {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_RETURNED, 0, R.string.loan_returned)
                                .setIcon(R.drawable.ic_people);
                    }
                });
    }

    /**
     * Sets read status of the book if needed. Shows green tick if book is read.
     *
     * @param book the book
     */
    private void populateReadStatus(final @NonNull Book book) {
        //ENHANCE add to mFields?

        //noinspection ConstantConditions
        final CheckedTextView readField = getView().findViewById(R.id.read);
        boolean visible = Fields.isVisible(UniqueId.KEY_BOOK_READ);
        if (visible) {
            // set initial display state
            readField.setChecked(book.getBoolean(Book.IS_READ));
            readField.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    // allow flipping 'read' status quickly
                    boolean newState = !readField.isChecked();
                    if (BookUtils.setRead(mDb, book, newState)) {
                        readField.setChecked(newState);
                    }
                }
            });
        }
        readField.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Show or hide the Table Of Content section
     */
    private void populateTOC(final @NonNull Book book) {
        //ENHANCE add to mFields?
        ArrayList<TOCEntry> list = book.getTOC();

        // only show if: used + it's an ant + the ant has titles
        boolean visible = Fields.isVisible(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK)
                && book.getBoolean(Book.IS_ANTHOLOGY)
                && !list.isEmpty();

        if (visible) {
            //noinspection ConstantConditions
            final ListView contentSection = getView().findViewById(R.id.toc);

            ArrayAdapter<TOCEntry> adapter = new TOCListAdapter(mActivity,
                    R.layout.row_toc_entry_with_author, list);
            contentSection.setAdapter(adapter);

            getView().findViewById(R.id.toc_button)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (contentSection.getVisibility() == View.VISIBLE) {
                                contentSection.setVisibility(View.GONE);
                            } else {
                                contentSection.setVisibility(View.VISIBLE);
                                ViewUtils.justifyListViewHeightBasedOnChildren(contentSection);
                            }
                        }
                    });
        }

        //noinspection ConstantConditions
        getView().findViewById(R.id.row_toc).setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     *
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

        super.onPause();
    }

    /**
     * At this point we're told to save our local (to the fragment) fields to the Book.
     * This method deliberately does not call the super of course, as we don't want a save.
     *
     * @param book to save to
     */
    @Override
    protected void onSaveFieldsToBook(final @NonNull Book book) {
        // don't call super, Don't save!
        // and don't remove this method... or the super *would* do the save!

        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(this, "onSaveFieldsToBook done");
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        outState.putLong(UniqueId.KEY_ID, getBook().getBookId());
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, getBook().getRawData());
        if (mFlattenedBooklist != null) {
            outState.putInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION, (int) mFlattenedBooklist.getPosition());
        }
        super.onSaveInstanceState(outState);
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu handlers">
    @Override
    @CallSuper
    public boolean onContextItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_LOAN_RETURNED:
                Book book = getBook();
                mDb.deleteLoan(book.getBookId(), false);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(final @NonNull Menu menu, final @NonNull MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit_book)
                .setIcon(R.drawable.ic_mode_edit)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT:
                EditBookActivity.startActivityForResult(mActivity,  /* a54a7e79-88c3-4b48-89df-711bb28935c5 */
                        getBook().getBookId(), EditBookFragment.TAB_EDIT);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        switch (requestCode) {
            case EditBookFragment.REQUEST_CODE: {
                if (resultCode == EditBookFragment.RESULT_CHANGES_MADE) {
                    getBook().reload();
                    mActivity.setChangesMade(true);
                }
                return;
            }
        }

        // handle any cover image result codes
        if (mCoverHandler.onActivityResult(requestCode, resultCode, data)) {
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

//    /**
//     * We override the dispatcher because the ScrollView will consume all events otherwise.
//     */
//    @Override
//    @CallSuper
//    public boolean dispatchTouchEvent(MotionEvent event) {
//        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event)) {
//            return true;
//        }
//        super.dispatchTouchEvent(event);
//
//        return true;
//    }
}
