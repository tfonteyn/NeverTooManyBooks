package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

/**
 * Class for representing read-only book details.
 *
 * * ok, so why an Adapter and not handle this just like Series is currently handled....
 * *
 * * TODO the idea is to have a new Activity: {@link TOCEntry} -> books containing the story
 * * There is not much point in doing this in the Builder. The amount of entries is expected to be small.
 * * Main audience: the collector who wants *everything* of a certain author.
 *
 * @author n.silin
 */
public class BookDetailsFragment extends BookAbstractFragmentWithCoverImage {

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    /**
     * has no specific Arguments or savedInstanceState as all is done via {@link #getBook()}
     */
    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // override parent as our Activity determines the 'right' dividing coefficient
        initThumbSize(requireActivity());

        if (savedInstanceState == null) {
            HintManager.displayHint(requireActivity(), R.string.hint_view_only_help, null);
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

        mFields.add(R.id.series, UniqueId.KEY_SERIES_NAME);
        mFields.add(R.id.title, UniqueId.KEY_TITLE);
        mFields.add(R.id.isbn, UniqueId.KEY_BOOK_ISBN);
        mFields.add(R.id.description, UniqueId.KEY_DESCRIPTION).setShowHtml(true);
        mFields.add(R.id.genre, UniqueId.KEY_BOOK_GENRE);
        mFields.add(R.id.language, UniqueId.KEY_BOOK_LANGUAGE);
        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES);
        mFields.add(R.id.format, UniqueId.KEY_BOOK_FORMAT);
        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER);
        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED);
        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION);

        mFields.add(R.id.price_listed, UniqueId.KEY_BOOK_PRICE_LISTED)
                .setFormatter(new Fields.PriceFormatter(getBook().getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY)));


        // Personal fields
        mFields.add(R.id.bookshelves, UniqueId.KEY_BOOKSHELF_NAME)
                .setDoNotFetch(true);

        mFields.add(R.id.date_purchased, UniqueId.KEY_BOOK_DATE_ADDED)
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

    @Override
    @CallSuper
    protected void onLoadBookDetails(final @NonNull Book book, final boolean setAllFrom) {
        super.onLoadBookDetails(book, setAllFrom);

        populateAuthorListField(book);
        populateSeriesListField(book);

        // override setting the cover as we want specific sizes.
        populateCoverImage(book.getBookId(), mThumbSize.normal, mThumbSize.normal * 2);

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

        if (BuildConfig.DEBUG) {
            Logger.info(this, "onLoadBookDetails done");
        }
    }

    protected void populateAuthorListField(final @NonNull Book book) {
        ArrayList<Author> authors = book.getAuthorList();
        int authorsCount = authors.size();
        boolean visible = authorsCount != 0;
        if (visible) {
            StringBuilder builder = new StringBuilder();
            builder.append(getString(R.string.book_details_readonly_by));
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
        boolean visible = seriesCount != 0 && mFields.getField(R.id.series).visible;
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

            mFields.getField(R.id.series).setValue(builder.toString());
        }
        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_series).setVisibility(visible ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.series).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Formats 'format' section of the book depending on values of 'pages' and 'format' fields.
     */
    private void populateFormatSection(final @NonNull Book book) {
        Field pagesField = mFields.getField(R.id.pages);
        String pages = book.getString(UniqueId.KEY_BOOK_PAGES);
        boolean hasPages = !pages.isEmpty();
        if (hasPages) {
            pages = getString(R.string.book_details_readonly_pages, pages);
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
        String date = book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED);
        boolean hasPublishDate = !date.isEmpty();
        // pretty format the date if we have one
        if (hasPublishDate) {
            Date d = DateUtils.parseDate(date);
            if (d != null) {
                date = DateUtils.toPrettyDate(d);
            }
        }

        String pub = book.getString(UniqueId.KEY_BOOK_PUBLISHER);
        boolean hasPublisher = !pub.isEmpty();

        String result = "";
        if (hasPublisher && hasPublishDate) {
            // combine publisher and date into one field
            result = pub + " (" + date + ")";
        } else if (hasPublisher) {
            result = pub;
        } else if (hasPublishDate) {
            result = date;
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
        personLoanedTo = (personLoanedTo == null ? "" : getString(R.string.book_details_readonly_loaned_to, personLoanedTo));
        mFields.getField(R.id.loaned_to).setValue(personLoanedTo);

        mFields.getField(R.id.loaned_to).getView()
                .setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                    @Override
                    @CallSuper
                    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_LOANS, 0, R.string.edit_book_friends)
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

            ArrayAdapter<TOCEntry> adapter = new TOCListAdapter(requireActivity(),
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

    @Override
    protected void onSaveBookDetails(final @NonNull Book book) {
        // don't call super, Don't save!
        // and don't remove this method... or the super *would* try the save!

        if (BuildConfig.DEBUG) {
            Logger.info(this, "onSaveBookDetails done");
        }
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT_LOANS:
                //TOMF ENHANCE the 'loan' tab of EditBook needs to be replaced with using a initTextFieldEditor
                EditBookActivity.startActivityForResult(requireActivity(), /* c6e741b0-7b43-403b-9907-5f8c7eeb3f37 */
                        EditBookActivity.REQUEST_CODE, EditBookActivity.TAB_EDIT_LOANS);
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
        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.edit_book)
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
                EditBookActivity.startActivityForResult(requireActivity(),  /* a54a7e79-88c3-4b48-89df-711bb28935c5 */
                        getBook().getBookId(), EditBookActivity.TAB_EDIT);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case EditBookActivity.REQUEST_CODE: /* a54a7e79-88c3-4b48-89df-711bb28935c5, c6e741b0-7b43-403b-9907-5f8c7eeb3f37 */
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    long bookId = data.getLongExtra(UniqueId.KEY_ID, 0);
                    if (bookId > 0) {
                        reload(bookId);
                    } else {
                        throw new IllegalStateException("bookId==0");
                    }
                }
                return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * returning here from somewhere else (e.g. from editing the book) and have an ID...reload!
     */
    @CallSuper
    @Override
    public void onResume() {
        super.onResume();
        long bookId = getBook().getBookId();
        if (bookId != 0) {
            getBook().reload(bookId);
        }
    }
}
