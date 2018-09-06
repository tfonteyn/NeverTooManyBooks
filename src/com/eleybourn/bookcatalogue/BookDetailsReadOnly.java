package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.Convert;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.Date;

/**
 * Class for representing read-only book details.
 *
 * @author n.silin
 *
 * Fragment !
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

    // first time user opens up the list, create the entries. Avoids unneeded/lengthy rebuilds.
    private boolean generateAnthologyFieldList = true;
    private void showAnthologySection(BookData book) {
        final BookData aBook = book;
        View section = getView().findViewById(R.id.anthology_section);
        section.setVisibility(View.VISIBLE);
        Button btn = getView().findViewById(R.id.anthology_button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View titles = getView().findViewById(R.id.anthology_titlelist);
                titles.setVisibility(titles.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

                if (generateAnthologyFieldList) {
                    populateAnthologyListField(aBook);
                    generateAnthologyFieldList = false;
                }
            }
        });
    }

    /**
     * FIXME: is this *really* the best way to do this ? Surely this can be done better/faster ?
     * ArrayAdapter...
     *
     */
    protected void populateAnthologyListField(BookData book) {
        ArrayList<Author> bookAuthors = book.getAuthors();
        boolean isSingleAuthor = (1 == bookAuthors.size());
        long firstAuthorId = book.getAuthors().get(0).id;

        ViewGroup view = getView().findViewById(R.id.anthology_titlelist);
        for (AnthologyTitle aTitle : book.getAnthologyTitles()) {
            ViewGroup antGroup = (ViewGroup)getLayoutInflater().inflate(R.layout.row_anthology, null);

            TextView antTitle = antGroup.findViewById(R.id.row_title);
            antTitle.setText(aTitle.getTitle());

            TextView antAuthor = antGroup.findViewById(R.id.row_author);
            Author author = aTitle.getAuthor();
            if (isSingleAuthor && (firstAuthorId == author.id)) {
                antAuthor.setVisibility(View.GONE);
            } else {
                antAuthor.setText(author.getSortName());
                antAuthor.setVisibility(View.VISIBLE);
            }
            view.addView(antGroup);
        }
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
            mFields.getField(R.id.series)
                    .setShowHtml(true)
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
        mFields.add(R.id.rating, ColumnNames.KEY_RATING, null);
        mFields.add(R.id.notes, ColumnNames.KEY_NOTES, null)
                .setShowHtml(true);
        mFields.add(R.id.read_start, ColumnNames.KEY_READ_START, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, ColumnNames.KEY_READ_END, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.location, ColumnNames.KEY_LOCATION, null);
        // Make sure the label is hidden when the ISBN is
        mFields.add(R.id.isbn_label, "", ColumnNames.KEY_ISBN, null);
        mFields.add(R.id.publishing_details, "", ColumnNames.KEY_PUBLISHER, null);
    }

    /**
     * Formats 'format' section of the book depending on values
     * of 'pages' and 'format' fields.
     */
    private void formatFormatSection(BookData book) {
        Field field = mFields.getField(R.id.pages);
        String value = book.getString(ColumnNames.KEY_PAGES);
        boolean isExist = value != null && !value.isEmpty();
        if (isExist) { //If 'pages' field is set format it
            field.setValue(getString(R.string.book_details_readonly_pages, value));
        }
        // Format 'format' field
        field = mFields.getField(R.id.format);
        value = book.getString(ColumnNames.KEY_FORMAT);
        if (isExist && value != null && !value.isEmpty()) {
            /* Surround 'format' field with braces if 'pages' field is set
             * and 'format' field is not empty */
            field.setValue(getString(R.string.brackets, value));
        }
    }

    /**
     * Formats 'Publishing' section of the book depending on values
     * of 'publisher' and 'date published' fields.
     */
    private void formatPublishingSection(BookData book) {
        String date = book.getString(ColumnNames.KEY_DATE_PUBLISHED);
        boolean hasDate = date != null && !date.isEmpty();
        String pub = book.getString(ColumnNames.KEY_PUBLISHER);
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
        if (FieldVisibility.isVisible(ColumnNames.KEY_READ)) {
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
