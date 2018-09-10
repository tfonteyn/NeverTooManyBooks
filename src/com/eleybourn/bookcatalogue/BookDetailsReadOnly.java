package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.database.ColumnInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.Convert;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class for representing read-only book details.
 *
 * @author n.silin
 */
public class BookDetailsReadOnly extends BookDetailsFragmentAbstract {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Tracker.enterOnCreateView(this);
        final View v = inflater.inflate(R.layout.book_details, null);
        Tracker.exitOnCreateView(this);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        /* In superclass onCreate method we initialize fields, background,
         * display metrics and other. So see super.onActivityCreated */
        super.onActivityCreated(savedInstanceState);

        // Set additional (non book details) fields before their populating
        addFields();

        /*
         * We have to override this value to initialize book thumb with right size.
         * You have to see in book_details.xml to get dividing coefficient
         */
        mThumbEditSize = Math.min(mMetrics.widthPixels, mMetrics.heightPixels) / 3;

        if (savedInstanceState == null) {
            HintManager.displayHint(getActivity(), R.string.hint_view_only_help, null);
        }

        // Just format a binary value as yes/no/blank
        mFields.getField(R.id.signed).formatter = new BinaryYesNoEmptyFormatter();
    }

    /**
     * This is a straight passthrough
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case UniqueId.ACTIVITY_EDIT_BOOK:
                // Update fields of read-only book after editing
                // --- onResume() calls through to restoreBookData() which will do this now
                //if (resultCode == Activity.RESULT_OK) {
                //	updateFields(mEditManager.getBookData());
                //}
                break;
        }
    }

    @Override
    /* The only difference from super class method is initializing of additional
     * fields needed for read-only mode (user notes, loaned, etc.) */
    protected void populateFieldsFromBook(BookData book) {
        try {
            populateBookDetailsFields(book);

            // Set maximum aspect ratio width : height = 1 : 2
            setBookThumbnail(book.getRowId(), mThumbEditSize, mThumbEditSize * 2);

            // Additional fields for read-only mode which are not initialized automatically
            showReadStatus(book);
            // XXX: Use the data!
            showLoanedInfo(book.getRowId());
            showSignedStatus(book);
            formatFormatSection(book);
            formatPublishingSection(book);
            if (0 != book.getInt(BookData.KEY_ANTHOLOGY)) {
                showAnthologySection(book);
            }

            // Restore default visibility and hide unused/unwanted and empty fields
            showHideFields(true);

            // Hide the fields that we never use...
            getView().findViewById(R.id.anthology).setVisibility(View.GONE);

        } catch (Exception e) {
            Logger.logError(e);
        }

        // Populate bookshelves and hide the field if bookshelves are not set.
        if (!populateBookshelvesField(mFields, book)) {
            getView().findViewById(R.id.lbl_bookshelves).setVisibility(View.GONE);
            //getView().findViewById(R.id.bookshelf_text).setVisibility(View.GONE);
        }
    }

    private boolean mGenerateAnthologyFieldList = true;
    private void showAnthologySection(final BookData book) {
        View section = getView().findViewById(R.id.anthology_section);
        section.setVisibility(View.VISIBLE);
        Button btn = getView().findViewById(R.id.anthology_button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ListView titles = getView().findViewById(R.id.anthology_titlelist);
                titles.setVisibility(titles.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

                if (mGenerateAnthologyFieldList) {
                    AnthologyTitleListAdapter adapter = new AnthologyTitleListAdapter(getActivity(), R.layout.row_anthology, book.getAnthologyTitles());
                    titles.setAdapter(adapter);
                    justifyListViewHeightBasedOnChildren(titles);
                    mGenerateAnthologyFieldList = false;
                }
            }
        });
    }

    protected class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {

        AnthologyTitleListAdapter(Context context, int rowViewId, List<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(int position, View convertView, AnthologyTitle anthology) {
            TextView author = convertView.findViewById(R.id.row_author);
            author.setText(anthology.getAuthor().getDisplayName());
            TextView title = convertView.findViewById(R.id.row_title);
            title.setText(anthology.getTitle());
        }
    }

    /**
     * Gets the total number of rows from the adapter, then uses that to set the ListView to the
     * full height so all rows are visible (no scrolling)
     *
     */
    private void justifyListViewHeightBasedOnChildren (final ListView listView) {
        ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }

        int totalHeight = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            View listItem = adapter.getView(i, null, listView);
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams par = listView.getLayoutParams();
        par.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount() - 1));
        listView.setLayoutParams(par);
        listView.requestLayout();
    }

    @Override
    /* Override populating author field. Hide the field if author not set or
     * shows author (or authors through ',') with 'by' at the beginning. */
    protected void populateAuthorListField() {
        ArrayList<Author> authors = mEditManager.getBookData().getAuthors();
        int authorsCount = authors.size();
        if (authorsCount == 0) {
            // Hide author field if it is not set
            getView().findViewById(R.id.author).setVisibility(View.GONE);
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(getResources().getString(R.string.book_details_readonly_by));
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
    protected void populateSeriesListField() {
        ArrayList<Series> series = mEditManager.getBookData().getSeries();

        int size;
        try {
            size = series.size();
        } catch (NullPointerException e) {
            size = 0;
        }
        if (size == 0 || !mFields.getField(R.id.series).visible) {
            // Hide 'Series' label and data
            getView().findViewById(R.id.lbl_series).setVisibility(View.GONE);
            getView().findViewById(R.id.series).setVisibility(View.GONE);
            return;
        } else {
            // Show 'Series' label and data
            getView().findViewById(R.id.lbl_series).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.series).setVisibility(View.VISIBLE);

            String newText = null;
            Utils.pruneSeriesList(series);
            Utils.pruneList(mDb, series);
            int seriesCount = series.size();
            if (seriesCount > 0) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < seriesCount; i++) {
                    builder.append("    ").append(series.get(i).getDisplayName());
                    if (i != seriesCount - 1) {
                        builder.append("<br/>");
                    }
                }
                newText = builder.toString();
            }
            //FIXME: html causes colour to change making 'Light' theme display very faint
            mFields.getField(R.id.series)
                    .setShowHtml(true) /* so <br/> work */
                    .setValue(newText);
        }
    }

    /**
     * Add other fields of book to details fields. We need this method to automatically
     * populate some fields during populating.
     * Note that it should be performed before populating.
     */
    private void addFields() {
        // From 'My comments' tab
        mFields.add(R.id.rating, ColumnInfo.KEY_RATING, null);
        mFields.add(R.id.notes, ColumnInfo.KEY_NOTES, null)
                .setShowHtml(true);
        mFields.add(R.id.read_start, ColumnInfo.KEY_READ_START, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, ColumnInfo.KEY_READ_END, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.location, ColumnInfo.KEY_LOCATION, null);
        // Make sure the label is hidden when the ISBN is
        mFields.add(R.id.isbn_label, "", ColumnInfo.KEY_ISBN, null);
        mFields.add(R.id.publishing_details, "", ColumnInfo.KEY_PUBLISHER, null);
    }

    /**
     * Formats 'format' section of the book depending on values
     * of 'pages' and 'format' fields.
     */
    private void formatFormatSection(BookData book) {
        // Number of pages
        Field pagesField = mFields.getField(R.id.pages);
        String pages = book.getString(ColumnInfo.KEY_PAGES);
        boolean hasPages = pages != null && !pages.isEmpty();
        if (hasPages) {
            pagesField.setValue(getString(R.string.book_details_readonly_pages, pages));
        }

        // 'format' field
        Field formatField = mFields.getField(R.id.format);
        String format = book.getString(ColumnInfo.KEY_FORMAT);
        boolean hasFormat = format != null && !format.isEmpty();
        if (hasFormat) {
            if (hasPages) {
                formatField.setValue(getString(R.string.brackets, format));
            } else {
                formatField.setValue(format);
            }
        }
    }

    /**
     * Formats 'Publishing' section of the book depending on values
     * of 'publisher' and 'date published' fields.
     */
    private void formatPublishingSection(BookData book) {
        String date = book.getString(ColumnInfo.KEY_DATE_PUBLISHED);
        boolean hasDate = date != null && !date.isEmpty();
        String pub = book.getString(ColumnInfo.KEY_PUBLISHER);
        boolean hasPub = pub != null && !pub.isEmpty();
        String value;

        if (hasDate) {
            try {
                Date d = DateUtils.parseDate(date);
                date = DateUtils.toPrettyDate(d);
            } catch (Exception e) {
                // Ignore; just use what we have
            }
        }

        if (hasPub) {
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
        mFields.getField(R.id.publishing_details).setValue(value);
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * If book is not loaned field is invisible.
     *
     * @param rowId Database row _id of the loaned book
     */
    private void showLoanedInfo(Long rowId) {
        String personLoanedTo = mDb.fetchLoanByBook(rowId);
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
     * Sets read status of the book if needed. Shows green tick if book is read.
     *
     * @param book  the book
     */
    private void showReadStatus(final BookData book) {
        if (FieldVisibilityActivity.isVisible(ColumnInfo.KEY_READ)) {
            final CompoundButton readField = getView().findViewById(R.id.read);
            // set initial display state, REMINDER: setSelected will NOT update the GUI...
            readField.setChecked(book.isRead());
            readField.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    BookUtils.setRead(mDb, book, isChecked);
                }
            });
        }
    }

    /**
     * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
     *
     * @param book Cursor containing information of the book from database
     */
    private void showSignedStatus(BookData book) {
        if (book.isSigned()) {
            TextView v = getView().findViewById(R.id.signed);
            v.setText(getResources().getString(R.string.yes));
        }
    }

    /**
     * Updates all fields of book from database.
     */
    private void updateFields(BookData book) {
        populateFieldsFromBook(book);
        // Populate author and series fields
        populateAuthorListField();
        populateSeriesListField();
    }

    @Override
    protected void onLoadBookDetails(BookData book, boolean setAllDone) {
        if (!setAllDone)
            mFields.setAll(book);
        updateFields(book);
    }

    @Override
    protected void onSaveBookDetails(BookData book) {
        // Override to Do nothing because we modify the fields to make them look pretty.
    }

    public void onResume() {
        // If we are read-only, returning here from somewhere else and have an ID...reload!
        BookData book = mEditManager.getBookData();
        if (book.getRowId() != 0) {
            book.reload();
        }
        super.onResume();
    }

    /**
     * Formatter for date fields. On failure just return the raw string.
     *
     * @author Philip Warner
     */
    private static class BinaryYesNoEmptyFormatter implements FieldFormatter {

        /**
         * Display as a human-friendly date
         */
        public String format(Field f, String source) {
            try {
                boolean val = Convert.toBoolean(source, false);
                return BookCatalogueApp.getResourceString(val ? R.string.yes : R.string.no);
            } catch (Exception e) {
                return source;
            }
        }

        /**
         * Extract as an SQL date.
         */
        public String extract(Field f, String source) {
            try {
                return Convert.toBoolean(source, false) ? "1" : "0";
            } catch (Exception e) {
                return source;
            }
        }
    }

}
