package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

/**
 * Class for representing read-only book details.
 *
 *      * ok, so why an Adapter and not handle this just like Series is currently handled....
 *      *
 *      * TODO the idea is to have a new Activity: {@link AnthologyTitle} -> books containing the story
 *      * There is not much point in doing this in the Builder. The amount of entries is expected to be small.
 *      * Main audience: the collector who wants *everything* of a certain author.
 *
 * @author n.silin
 */
public class BookDetailsFragment extends BookAbstractFragmentWithCoverImage {

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // override parent as our Activity determines the 'right' dividing coefficient
        initThumbSize(requireActivity());

        if (savedInstanceState == null) {
            HintManager.displayHint(requireActivity(), R.string.hint_view_only_help, null);
        }
    }

    /**
     * Add all book fields with corresponding formatters. (validators not needed obviously)
     * Note this is NOT where we set values.
     *
     * Some fields are only present (or need specific handling) on the 'show' activity.
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();

        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER);
        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED);
        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION);

        mFields.add(R.id.bookshelves, UniqueId.KEY_BOOKSHELF_NAME)
                .setOutputOnly(true);

        // From 'My comments' tab
        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES).setShowHtml(true);
        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION);
        mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START)
                .setFormatter(new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END)
                .setFormatter(new Fields.DateFieldFormatter());
        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED)
                .setFormatter(new Fields.BinaryYesNoEmptyFormatter(this.getResources()));
        mFields.add(R.id.edition, UniqueId.KEY_BOOK_EDITION_BITMASK)
                .setOutputOnly(true);
    }

    protected void populateFields(@NonNull final Book book) {
        setCoverImage(book.getBookId(), mThumbSize.normal, mThumbSize.normal * 2);
        populateFormatSection(book);
        populatePublishingSection(book);
        populateReadStatus(book);
        populateLoanedToField(book.getBookId());
        showTOC(book);

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(true);


        // populate Bookshelves: do this after showHide
        Field bookshelfField = mFields.getField(R.id.bookshelves);
        boolean hasShelves = super.populateBookshelves(bookshelfField, book);
        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_bookshelves)
                .setVisibility(bookshelfField.visible && hasShelves ? View.VISIBLE : View.GONE);

        // populate Editions: do this after showHide
        Field editionsField = mFields.getField(R.id.edition);
        boolean hasEditions = super.populateEditions(editionsField, book);
        getView().findViewById(R.id.row_edition)
                .setVisibility(editionsField.visible && hasEditions ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void populateAuthorListField(@NonNull final Book book) {
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

    @Override
    protected void populateSeriesListField(@NonNull final Book book) {
        ArrayList<Series> series = book.getSeriesList();
        int seriesCount = series.size();
        boolean visible = seriesCount != 0 && mFields.getField(R.id.series).visible;
        if (visible) {
            Series.pruneSeriesList(series);
            Utils.pruneList(mDb, series);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < seriesCount; i++) {
                builder.append("    ").append(series.get(i).getDisplayName());
                if (i != seriesCount - 1) {
                    builder.append("\n");
                }
            }

            mFields.getField(R.id.series)
                    .setValue(builder.toString());
        }
        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_series).setVisibility(visible ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.series).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Formats 'format' section of the book depending on values of 'pages' and 'format' fields.
     */
    private void populateFormatSection(@NonNull final Book book) {
        String pages = book.getString(UniqueId.KEY_BOOK_PAGES);
        boolean pagesVisible = Fields.isVisible(UniqueId.KEY_BOOK_PAGES) && !pages.isEmpty();
        if (pagesVisible) {
            Field pagesField = mFields.getField(R.id.pages);
            pagesField.setValue(getString(R.string.book_details_readonly_pages, pages));
        }

        String format = book.getString(UniqueId.KEY_BOOK_FORMAT);
        boolean formatVisible = Fields.isVisible(UniqueId.KEY_BOOK_FORMAT) && !format.isEmpty();
        if (formatVisible) {
            Field formatField = mFields.getField(R.id.format);
            if (pagesVisible) {
                formatField.setValue(getString(R.string.brackets, format));
            } else {
                formatField.setValue(format);
            }
        }
    }

    /**
     * Formats 'Publishing' section depending on values of 'publisher' and 'date published' fields.
     *
     * Actual 'visibility' is handled by parent
     */
    private void populatePublishingSection(@NonNull final Book book) {
        String date = book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED);
        boolean dateVisible = Fields.isVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED) && !date.isEmpty();

        String pub = book.getString(UniqueId.KEY_BOOK_PUBLISHER);
        boolean publisherVisible = Fields.isVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED) && !pub.isEmpty();

        if (dateVisible || publisherVisible) {
            // pretty format the date if we have one
            if (dateVisible) {
                Date d = DateUtils.parseDate(date);
                if (d != null) {
                    date = DateUtils.toPrettyDate(d);
                }
            }

            // combine publisher and date into one field
            String value;
            if (publisherVisible) {
                if (dateVisible) {
                    value = pub + " (" + date + ")";
                } else {
                    value = pub;
                }
            } else {
                value = date;
            }
            mFields.getField(R.id.publisher).setValue(value);
        }
    }

    /**
     * TOMF: make this a DataAccessor on Book ? -> SQL for book could left join with TBL_LOAN
     *
     * Inflates 'Loaned' field showing a person the book loaned to.
     * If book is not loaned the field is invisible.
     *
     * @param bookId the loaned book
     */
    private void populateLoanedToField(final long bookId) {
        String personLoanedTo = mDb.getLoanByBookId(bookId);
        //noinspection ConstantConditions
        TextView textView = getView().findViewById(R.id.loaned_to);
        boolean visible = Fields.isVisible(UniqueId.KEY_LOAN_LOANED_TO) && personLoanedTo != null;
        if (visible) {
            textView.setText(getString(R.string.book_details_readonly_loaned_to, personLoanedTo));
        }
        textView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets read status of the book if needed. Shows green tick if book is read.
     *
     * @param book the book
     */
    private void populateReadStatus(@NonNull final Book book) {
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
     * Show or hide the Table Of Content section aka AnthologyTitles
     */
    private void showTOC(@NonNull final Book book) {
        ArrayList<AnthologyTitle> list = book.getTOC();

        // only show if: used + it's an ant + the ant has titles
        boolean visible = (Fields.isVisible(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK)
                && book.isAnthology()
                && !list.isEmpty());

        if (visible) {
            //noinspection ConstantConditions
            final ListView contentSection = getView().findViewById(R.id.toc);

            ArrayAdapter<AnthologyTitle> adapter = new AnthologyTitleListAdapter(requireActivity(),
                    R.layout.row_anthology_with_author, list);
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

    /**
     *
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
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
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT:
                EditBookActivity.startActivityForResult(requireActivity(),  /* a54a7e79-88c3-4b48-89df-711bb28935c5 */
                        getBook().getBookId(), EditBookActivity.TAB_EDIT);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllFrom) {
        if (BuildConfig.DEBUG) {
            Logger.info(this,"onLoadBookDetails");
        }
        super.onLoadBookDetails(book, setAllFrom);
    }

    @Override
    protected void onSaveBookDetails(@NonNull final Book book) {
        if (BuildConfig.DEBUG) {
            Logger.info(this,"onSaveBookDetails");
        }
        // don't call super, Don't save!
        // and don't remove this method... or the super *would* do the save!
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this,"onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case EditBookActivity.REQUEST_CODE: /* a54a7e79-88c3-4b48-89df-711bb28935c5 */
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
