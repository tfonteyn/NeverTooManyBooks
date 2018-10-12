package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.datamanager.Datum;
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

/**
 * Class for representing read-only book details.
 *
 * @author n.silin
 */
public class BookDetailsFragment extends BookDetailsAbstractFragment {

    /**
     * ok, so why an Adapter and not handle this just like Series is currently handled....
     *
     * TODO the idea is to have a new Activity: {@link AnthologyTitle} -> books containing the story
     * There is not much point in doing this in the Builder. The amount of entries is expected to be small.
     * Main audience: the collector who wants *everything* of a certain author.
     */
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.book_details, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // override parent as our Activity determines the 'right' dividing coefficient
        setThumbSize(getActivity());

        if (savedInstanceState == null) {
            HintManager.displayHint(getActivity(), R.string.hint_view_only_help, null);
        }
    }

    @Override
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        super.onLoadBookDetails(book, setAllDone);
        populateFields(book);
        populateAuthorListField(book);
        populateSeriesListField(book);
    }

    @Override
    protected void populateFields(@NonNull final Book book) {
        try {
            setCoverImage(book.getBookId(), mThumbSize.normal, mThumbSize.normal * 2);

            // Additional fields which are not initialized automatically
            showReadStatus(book);
            showLoanedInfo(book.getBookId());
            showSignedStatus(book.isSigned());
            formatFormatSection(book);
            formatPublishingSection(book);

            if (book.getInt(Book.IS_ANTHOLOGY) > 0) {
                showTOC(book);
            }

            // Restore default visibility and hide unused/unwanted and empty fields
            showHideFields(true);

        } catch (Exception e) {
            Logger.error(e);
        }

        // Populate bookshelves and hide the field if bookshelves are not set.
        if (!populateBookshelves(mFields.getField(R.id.bookshelf), book)) {
            getView().findViewById(R.id.lbl_bookshelves).setVisibility(View.GONE);
        }

    }

    @Override
    protected void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> authors = book.getAuthorList();
        int authorsCount = authors.size();
        if (authorsCount == 0) {
            // Hide author field if it is not set
            getView().findViewById(R.id.author).setVisibility(View.GONE);
        } else {
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
    }

    @Override
    protected void populateSeriesListField(@NonNull final Book book) {
        ArrayList<Series> series = book.getSeriesList();
        int seriesCount = series.size();

        if (seriesCount == 0 || !mFields.getField(R.id.series).visible) {
            // Hide 'Series' label and data
            getView().findViewById(R.id.lbl_series).setVisibility(View.GONE);
            getView().findViewById(R.id.series).setVisibility(View.GONE);
        } else {
            // Show 'Series' label and data
            getView().findViewById(R.id.lbl_series).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.series).setVisibility(View.VISIBLE);

            String newText = "";
            Series.pruneSeriesList(series);
            Utils.pruneList(mDb, series);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < seriesCount; i++) {
                builder.append("    ").append(series.get(i).getDisplayName());
                if (i != seriesCount - 1) {
                    builder.append("<br/>");
                }
            }
            newText = builder.toString();

            mFields.getField(R.id.series)
                    .setShowHtml(true) /* so <br/> work */
                    .setValue(newText);
        }
    }

    /**
     * Gets the total number of rows from the adapter, then use that to set the ListView to the
     * full height so all rows are visible (no scrolling)
     *
     * Does nothing if the adapter is null, or if the view is not visible
     */
    private void justifyListViewHeightBasedOnChildren(@NonNull final ListView listView) {
        ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        if (listView.getVisibility() != View.VISIBLE) {
            return;
        }

        int totalHeight = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            View listItem = adapter.getView(i, null, listView);
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams layoutParams = listView.getLayoutParams();
        layoutParams.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount()));
        listView.setLayoutParams(layoutParams);
        listView.requestLayout();
    }

    /**
     * Overriding, as we need additional fields added.
     */
    @Override
    protected void initFields() {
        // do the base fields
        super.initFields();

        // format binary values as yes/no/blank for simple displaying
        mFields.getField(R.id.signed).formatter = new BinaryYesNoEmptyFormatter();

        // From 'My comments' tab
        mFields.add(R.id.rating, UniqueId.KEY_BOOK_RATING, null);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES, null)
                .setShowHtml(true);

        mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION, null);
        // Make sure the label is hidden when the ISBN is
        mFields.add(R.id.isbn_label, "", UniqueId.KEY_ISBN, null);
        mFields.add(R.id.publisher, "", UniqueId.KEY_BOOK_PUBLISHER, null);
    }

    /**
     * Formats 'format' section of the book depending on values
     * of 'pages' and 'format' fields.
     */
    private void formatFormatSection(@NonNull final Book book) {
        // Number of pages
        boolean hasPages = false;
        if (FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_PAGES)) {
            Field pagesField = mFields.getField(R.id.pages);
            String pages = book.getString(UniqueId.KEY_BOOK_PAGES);
            hasPages = !pages.isEmpty();
            if (hasPages) {
                pagesField.setValue(getString(R.string.book_details_readonly_pages, pages));
            }
        }
        // 'format' field
        if (FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_FORMAT)) {
            Field formatField = mFields.getField(R.id.format);
            String format = book.getString(UniqueId.KEY_BOOK_FORMAT);
            if (!format.isEmpty()) {
                if (hasPages && FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_PAGES)) {
                    formatField.setValue(getString(R.string.brackets, format));
                } else {
                    formatField.setValue(format);
                }
            }
        }
    }

    /**
     * Formats 'Publishing' section of the book depending on values
     * of 'publisher' and 'date published' fields.
     */
    private void formatPublishingSection(@NonNull final Book book) {
        String date = book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED);
        boolean hasDate = !date.isEmpty();
        if (hasDate) {
            Date d = DateUtils.parseDate(date);
            if (d != null) {
                date = DateUtils.toPrettyDate(d);
            }
        }

        String value;
        String pub = book.getString(UniqueId.KEY_BOOK_PUBLISHER);
        if (!pub.isEmpty()) {
            if (hasDate) {
                value = pub + "; " + date;
            } else {
                value = pub;
            }
        } else {
            if (hasDate) {
                value = date;
            } else {
                value = "";
            }
        }
        mFields.getField(R.id.publisher).setValue(value);
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * If book is not loaned field is invisible.
     *
     * @param bookId the loaned book
     */
    private void showLoanedInfo(final long bookId) {
        String personLoanedTo = mDb.getLoanByBookId(bookId);
        TextView textView = getView().findViewById(R.id.who);
        if (personLoanedTo != null) {
            textView.setVisibility(View.VISIBLE);
            String resultText = getString(R.string.book_details_readonly_loaned_to, personLoanedTo);
            textView.setText(resultText);
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    /**
     * Show or hide the Table Of Content section aka AnthologyTitles
     */
    private void showTOC(@NonNull final Book book) {
        View headerSection = getView().findViewById(R.id.toc_row);
        final ArrayList<AnthologyTitle> list = book.getContentList();
        if (list.isEmpty()) {
            // book is an Anthology, but the user has not added any titles (yet)
            headerSection.setVisibility(View.GONE);
            return;
        }
        headerSection.setVisibility(View.VISIBLE);

        AnthologyTitleListAdapter adapter = new AnthologyTitleListAdapter(getActivity(), R.layout.row_anthology, list);
        final ListView contentSection = getView().findViewById(R.id.toc);
        contentSection.setAdapter(adapter);

        Button btn = getView().findViewById(R.id.toc_button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (contentSection.getVisibility() == View.VISIBLE) {
                    contentSection.setVisibility(View.GONE);
                } else {
                    contentSection.setVisibility(View.VISIBLE);
                    justifyListViewHeightBasedOnChildren(contentSection);
                }
            }
        });
    }

    /**
     * Sets read status of the book if needed. Shows green tick if book is read.
     *
     * @param book the book
     */
    private void showReadStatus(@NonNull final Book book) {
        boolean visible = FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_READ);
        final CheckedTextView readField = getView().findViewById(R.id.read);
        readField.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (visible) {
            // set initial display state, REMINDER: setSelected will NOT update the GUI...
            readField.setChecked(book.isRead());
            readField.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    boolean newState = !readField.isChecked();
                    if (BookUtils.setRead(mDb, book, newState)) {
                        readField.setChecked(newState);
                    }
                }
            });
        }
    }

    /**
     * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
     */
    private void showSignedStatus(final boolean isSigned) {
        TextView v = getView().findViewById(R.id.signed);
        v.setText(getString(isSigned ? R.string.yes : R.string.no));
    }

    @Override
    protected void onSaveBookDetails(@NonNull final Book book) {
        // Override the super to Do nothing because we modify the fields to make them look pretty.
        //so NOT calling: super.onSaveBookDetails(book);

        // but we DO want to adjust the TOC size if needed.
        final ListView contentSection = getView().findViewById(R.id.toc);
        justifyListViewHeightBasedOnChildren(contentSection);

    }

    public void onResume() {
        // returning here from somewhere else (e.g. from editing the book) and have an ID...reload!
        Book book = mEditManager.getBook();
        if (book.getBookId() != 0) {
            book.reload();
        }
        super.onResume();
    }

    /**
     * Formatter for boolean fields. On failure just return the raw string.
     *
     * @author Philip Warner
     */
    private class BinaryYesNoEmptyFormatter implements FieldFormatter {

        /**
         * Display as a human-friendly date
         */
        @NonNull
        public String format(@NonNull final Field f, @Nullable final String source) {
            if (source == null) {
                return "";
            }
            try {
                boolean val = Datum.toBoolean(source, false);
                return BookDetailsFragment.this.getString(val ? R.string.yes : R.string.no);
            } catch (IllegalArgumentException e) {
                return source;
            }
        }

        /**
         * Extract as an SQL date.
         */
        @NonNull
        public String extract(@NonNull final Field f, @NonNull final String source) {
            try {
                return Datum.toBoolean(source, false) ? "1" : "0";
            } catch (IllegalArgumentException e) {
                return source;
            }
        }
    }

}
