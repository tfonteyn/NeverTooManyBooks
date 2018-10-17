package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
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

/**
 * Class for representing read-only book details.
 *
 * @author n.silin
 */
public class BookDetailsFragment extends BookAbstractFragmentWithCoverImage {

    /**
     * ok, so why an Adapter and not handle this just like Series is currently handled....
     *
     * TODO the idea is to have a new Activity: {@link AnthologyTitle} -> books containing the story
     * There is not much point in doing this in the Builder. The amount of entries is expected to be small.
     * Main audience: the collector who wants *everything* of a certain author.
     */
    @Override
    @CallSuper
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.book_details, container, false);
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

    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();

        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER, null);
        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED, null);
        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION, null);

        // From 'My comments' tab
        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING, null);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES, null).setShowHtml(true);
        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION, null);
        mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END, null, new Fields.DateFieldFormatter());

        // Make sure the label is hidden when the field is TOMF more labels ?
        mFields.add(R.id.lbl_isbn, "", UniqueId.KEY_BOOK_ISBN, null);
        mFields.add(R.id.lbl_publishing, "", UniqueId.KEY_BOOK_PUBLISHER, null);


        // set the formatter for binary values as yes/no/blank for simple displaying
        mFields.getField(R.id.signed).formatter = new Fields.BinaryYesNoEmptyFormatter(this.getResources());
    }

    @Override
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        super.onLoadBookDetails(book, setAllDone);
        populateAuthorListField(book);
        populateSeriesListField(book);
    }

    /**
     * Additional fields which are not initialized automatically
     */
    @Override
    protected void populateFields(@NonNull final Book book) {
        setCoverImage(book.getBookId(), mThumbSize.normal, mThumbSize.normal * 2);
        showFormatSection(book);
        showPublishingSection(book);
        showReadStatus(book);
        showLoanedInfo(book.getBookId());
        showTOC(book);

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(true);

        // do this after showHide
        populateBookshelves(book);
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
     * Populate bookshelves and hide the field if bookshelves are not set.
     */
    @CallSuper
    private void populateBookshelves(@NonNull final Book book) {
        Field bookshelves = mFields.getField(R.id.bookshelf);
        boolean visible = bookshelves.visible && super.populateBookshelves(bookshelves, book);

        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_bookshelves).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Show or hide the Table Of Content section aka AnthologyTitles
     */
    private void showTOC(@NonNull final Book book) {
        ArrayList<AnthologyTitle> list = book.getContentList();
        //noinspection ConstantConditions
        final ListView contentSection = getView().findViewById(R.id.toc);

        // only show if: used + it's an ant + the ant has titles
        boolean visible = (Fields.isVisible(UniqueId.KEY_ANTHOLOGY_BITMASK)
                && book.isAnthology()
                && !list.isEmpty());

        if (visible) {
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
        getView().findViewById(R.id.toc_row).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Formats 'format' section of the book depending on values of 'pages' and 'format' fields.
     *
     * Actual 'visibility' is handled by parent
     */
    private void showFormatSection(@NonNull final Book book) {
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
    private void showPublishingSection(@NonNull final Book book) {
        String date = book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED);
        boolean dateVisible = Fields.isVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED) && !date.isEmpty();

        String pub = book.getString(UniqueId.KEY_BOOK_PUBLISHER);
        boolean publisherVisible = Fields.isVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED) && !pub.isEmpty();

        if (dateVisible || publisherVisible) {
            if (dateVisible) {
                Date d = DateUtils.parseDate(date);
                if (d != null) {
                    date = DateUtils.toPrettyDate(d);
                }
            }

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
     * Inflates 'Loaned' field showing a person the book loaned to.
     * If book is not loaned field is invisible.
     *
     * @param bookId the loaned book
     */
    private void showLoanedInfo(final long bookId) {
        String personLoanedTo = mDb.getLoanByBookId(bookId);
        //noinspection ConstantConditions
        TextView textView = getView().findViewById(R.id.who);
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
    private void showReadStatus(@NonNull final Book book) {
        //noinspection ConstantConditions
        final CheckedTextView readField = getView().findViewById(R.id.read);
        boolean visible = Fields.isVisible(UniqueId.KEY_BOOK_READ);
        if (visible) {
            // set initial display state
            readField.setChecked(book.isRead());
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
     * returning here from somewhere else (e.g. from editing the book) and have an ID...reload!
     */
    @CallSuper
    @Override
    public void onResume() {

        Book book = getBook();
        if (book.getBookId() != 0) {
            book.reload();
        }
        super.onResume();
    }
}
