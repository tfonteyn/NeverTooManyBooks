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

import com.eleybourn.bookcatalogue.adapters.TOCAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import java.util.ArrayList;

/**
 * Class for representing read-only book details.
 */
public class BookFragment
        extends BookBaseFragment
        implements BookManager {

    public static final String TAG = "BookFragment";

    static final String REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION = "FBLP";
    static final String REQUEST_BKEY_FLATTENED_BOOKLIST = "FBL";

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
        // cache to avoid multiple calls to requireActivity()
        mActivity = (BaseActivity) requireActivity();

        super.onActivityCreated(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            initBooklist(args, savedInstanceState);
        }

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
        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, getBook().getId());
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
        mFields.add(R.id.isbn, UniqueId.KEY_BOOK_ISBN);
        mFields.add(R.id.description, UniqueId.KEY_BOOK_DESCRIPTION)
               .setShowHtml(true);
        mFields.add(R.id.genre, UniqueId.KEY_BOOK_GENRE);
        mFields.add(R.id.language, UniqueId.KEY_BOOK_LANGUAGE)
               .setFormatter(new Fields.LanguageFormatter());
        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES)
               .setFormatter(new Fields.FieldFormatter() {
                   @NonNull
                   @Override
                   public String format(@NonNull final Field field,
                                        @Nullable final String source) {
                       if (source != null && !source.isEmpty()) {
                           return getString(R.string.lbl_x_pages, source);
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
        mFields.add(R.id.format, UniqueId.KEY_BOOK_FORMAT);
        mFields.add(R.id.price_listed, UniqueId.KEY_BOOK_PRICE_LISTED)
               .setFormatter(new Fields.PriceFormatter(
                       getBook().getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY)));
        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION)
               .setFormatter(dateFormatter);

        // defined, but handled manually
        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR);
        // defined, but handled manually
        mFields.add(R.id.series, "", UniqueId.KEY_SERIES);

        // populated, but manually re-populated.
        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER);
        // not a field on the screen, but used in re-population of publisher.
        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED)
               .setFormatter(dateFormatter);

        // defined, but handled manually
        Field coverField = mFields.add(R.id.coverImage, "", UniqueId.BKEY_THUMBNAIL);
        mCoverHandler = new CoverHandler(this, mDb, getBookManager(),
                                         coverField, mFields.getField(R.id.isbn));


        // Personal fields
        mFields.add(R.id.date_acquired, UniqueId.KEY_BOOK_DATE_ACQUIRED)
               .setFormatter(dateFormatter);
        mFields.add(R.id.price_paid, UniqueId.KEY_BOOK_PRICE_PAID)
               .setFormatter(new Fields.PriceFormatter(
                       getBook().getString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY)));
        mFields.add(R.id.edition, UniqueId.KEY_BOOK_EDITION_BITMASK)
               .setFormatter(new Fields.BookEditionsFormatter());

        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION);
        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING);
        mFields.add(R.id.notes, UniqueId.KEY_BOOK_NOTES)
               .setShowHtml(true);
        mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START)
               .setFormatter(dateFormatter);
        mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END)
               .setFormatter(dateFormatter);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.read, UniqueId.KEY_BOOK_READ);
        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED)
               .setFormatter(new Fields.BinaryYesNoEmptyFormatter(requireContext()));

        // defined, but handled manually
        mFields.add(R.id.bookshelves, "", UniqueId.KEY_BOOKSHELF_NAME);

        // defined, but handled manually
        mFields.add(R.id.loaned_to, "", UniqueId.KEY_BOOK_LOANEE);
    }

    /**
     * returning here from somewhere else (e.g. from editing the book) and have an ID...reload!
     */
    @CallSuper
    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        // we need the book before the super will load the fields
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
        super.onLoadFieldsFromBook(book, setAllFrom);

        populateAuthorListField(book);
        populateSeriesListField(book);

        // override setting the cover as we want a bigger size.
        ImageUtils.ImageSize ts = ImageUtils.getImageSizes(mActivity);
        mCoverHandler.populateCoverView(ts.small, ts.standard);

        // handle 'text' DoNotFetch fields
        ArrayList<Bookshelf> bsList = book.getList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        mFields.getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));
        populateLoanedToField(book.getId());

        // handle composite fields
        populatePublishingSection(book);

        // handle non-text fields
        populateTOC(book);

        // hide unwanted and empty text fields
        showHideFields(true);

        // non-text fields
        Field editionsField = mFields.getField(R.id.edition);
        if ("0".equals(editionsField.getValue().toString())) {
            //noinspection ConstantConditions
            getView().findViewById(R.id.row_edition).setVisibility(View.GONE);
            // can't do this as our field is a number field
            //showHideField(hideIfEmpty, R.id.edition, R.id.row_edition, R.id.lbl_edition);
        }

        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Init the flat booklist & fling handler">

    /**
     * If we are passed a flat book list, get it and validate it.
     */
    private void initBooklist(@NonNull final Bundle args,
                              @Nullable final Bundle savedInstanceState) {

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
        int pos;
        if (savedInstanceState != null) {
            pos = savedInstanceState.getInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION, 0);
        } else {
            pos = args.getInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION, 0);
        }

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

        // finally, enable the listener for flings
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        //noinspection ConstantConditions
        getView().setOnTouchListener(new View.OnTouchListener() {
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
        //noinspection ConstantConditions
        getView().findViewById(R.id.author).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void populateSeriesListField(@NonNull final Book book) {
        ArrayList<Series> list = book.getList(UniqueId.BKEY_SERIES_ARRAY);
        int seriesCount = list.size();
        boolean visible = seriesCount != 0 && mFields.getField(R.id.series).isVisible();
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
        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_series).setVisibility(visible ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.series).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Formats 'Publishing' section
     * <p>
     * 'publisher' and 'date published' are combined
     * 'first published' and 'list price' are previously handled automatically
     * <p>
     * If all 4 are empty, the section header is hidden.
     */
    private void populatePublishingSection(@NonNull final Book book) {

        String datePublished = mFields.getField(R.id.date_published)
                                      .format(book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED));
        boolean hasPublishDate = !datePublished.isEmpty();

        String publisher = book.getString(UniqueId.KEY_BOOK_PUBLISHER);
        boolean hasPublisher = !publisher.isEmpty();

        String result = "";
        if (hasPublisher && hasPublishDate) {
            // combine publisher and date into one field
            result = publisher + " (" + datePublished + ')';
        } else if (hasPublisher) {
            result = publisher;
        } else if (hasPublishDate) {
            result = datePublished;
        }
        mFields.getField(R.id.publisher).setValue(result);

        // hide header if no fields populated.
        if (result.isEmpty()
                && !mFields.getField(R.id.price_listed).getValue().toString().isEmpty()
                && !mFields.getField(R.id.first_publication).getValue().toString().isEmpty()) {
            //noinspection ConstantConditions
            getView().findViewById(R.id.lbl_publishing).setVisibility(View.GONE);
        }
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * Allows returning the book via a context menu.
     *
     * @param bookId of the loaned book
     */
    private void populateLoanedToField(final long bookId) {
        String personLoanedTo = mDb.getLoaneeByBookId(bookId);
        if (personLoanedTo != null) {
            personLoanedTo = getString(R.string.lbl_loaned_to_name,
                                       personLoanedTo);
        } else {
            personLoanedTo = "";
        }
        mFields.getField(R.id.loaned_to).setValue(personLoanedTo);

        mFields.getField(R.id.loaned_to).getView()
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

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateTOC(@NonNull final Book book) {
        //ENHANCE: add to mFields?
        ArrayList<TocEntry> list = book.getList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        // only show if: field in use + it's flagged as an ant + the ant has titles
        boolean visible = Fields.isVisible(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK)
                && book.isBitSet(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                                 TocEntry.Type.MULTIPLE_WORKS)
                && !list.isEmpty();

        if (visible) {
            //noinspection ConstantConditions
            final ListView contentSection = getView().findViewById(R.id.toc);

            ArrayAdapter<TocEntry> adapter =
                    new TOCAdapter(mActivity, R.layout.row_toc_entry_with_author, list);
            contentSection.setAdapter(adapter);

            getView().findViewById(R.id.toc_button)
                     .setOnClickListener(new View.OnClickListener() {
                         @Override
                         public void onClick(@NonNull final View v) {
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
        outState.putLong(UniqueId.KEY_ID, getBook().getId());
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, getBook().getRawData());
        if (mFlattenedBooklist != null) {
            outState.putInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION,
                            (int) mFlattenedBooklist.getPosition());
        }
        super.onSaveInstanceState(outState);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu handlers">
    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_LOAN_RETURNED:
                loanIsReturned();
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * A book was returned. Update the database, and hide the loan view.
     */
    protected void loanIsReturned() {
        getBook().loanReturned(mDb);
        mFields.getField(R.id.loaned_to).setValue("");
        mFields.getField(R.id.loaned_to).getView().setVisibility(View.GONE);
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

        super.onCreateOptionsMenu(menu, inflater);
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
                Intent intent = new Intent(getContext(), EditBookActivity.class);
                intent.putExtra(UniqueId.KEY_ID, getBook().getId());
                intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
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
