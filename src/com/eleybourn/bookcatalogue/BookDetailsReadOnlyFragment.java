package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.Intent;
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
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

import java.util.ArrayList;
import java.util.Date;

/**
 * Class for representing read-only book details.
 *
 * @author n.silin
 */
public class BookDetailsReadOnlyFragment extends BookDetailsAbstractFragment {

    /**
     * ok, so why an Adapter and not handle this just like Series is currently handled....
     *
     * TODO the idea is to have a new Activity: AnthologyTitle -> books containing the story
     * TODO once done, retrofit the same to Series.
     */
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
        mThumper = ImageUtils.getThumbSizes(getActivity());

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
            setBookThumbnail(book.getRowId(), mThumper.normal, mThumper.normal * 2);

            // Additional fields for read-only mode which are not initialized automatically
            showReadStatus(book);
            // XXX: Use the data!
            showLoanedInfo(book.getRowId());
            showSignedStatus(book);
            formatFormatSection(book);
            formatPublishingSection(book);
            if (0 != book.getInt(BookData.LOCAL_KEY_ANTHOLOGY)) {
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

    /**
     * FIXME: use a background task to build the list instead of on the UI thread !
     */
    private void showAnthologySection(final BookData book) {
        View section = getView().findViewById(R.id.anthology_section);
        final ArrayList<AnthologyTitle> list = book.getAnthologyTitles();
        if (list.isEmpty()) {
            // book is an Anthology, but the user has not added any titles (yet)
            section.setVisibility(View.GONE);
            return;
        }

        section.setVisibility(View.VISIBLE);

        AnthologyTitleListAdapter adapter = new AnthologyTitleListAdapter(getActivity(), R.layout.row_anthology, list);
        final ListView titles = getView().findViewById(R.id.anthology_titlelist);
        titles.setAdapter(adapter);

        Button btn = getView().findViewById(R.id.anthology_button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (titles.getVisibility() == View.VISIBLE) {
                    titles.setVisibility(View.GONE);
                } else {
                    titles.setVisibility(View.VISIBLE);
                    justifyListViewHeightBasedOnChildren(titles);
                }
            }
        });
    }

    /**
     * Gets the total number of rows from the adapter, then uses that to set the ListView to the
     * full height so all rows are visible (no scrolling)
     */
    private void justifyListViewHeightBasedOnChildren(final ListView listView) {
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
        mFields.add(R.id.rating, UniqueId.KEY_RATING, null);
        mFields.add(R.id.notes, UniqueId.KEY_NOTES, null)
                .setShowHtml(true);
        mFields.add(R.id.read_start, UniqueId.KEY_BOOK_READ_START, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.read_end, UniqueId.KEY_BOOK_READ_END, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.location, UniqueId.KEY_BOOK_LOCATION, null);
        // Make sure the label is hidden when the ISBN is
        mFields.add(R.id.isbn_label, "", UniqueId.KEY_ISBN, null);
        mFields.add(R.id.publishing_details, "", UniqueId.KEY_PUBLISHER, null);
    }

    /**
     * Formats 'format' section of the book depending on values
     * of 'pages' and 'format' fields.
     */
    private void formatFormatSection(BookData book) {
        // Number of pages
        boolean hasPages = false;
        if (FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_PAGES)) {
            Field pagesField = mFields.getField(R.id.pages);
            String pages = book.getString(UniqueId.KEY_BOOK_PAGES);
            hasPages = pages != null && !pages.isEmpty();
            if (hasPages) {
                pagesField.setValue(getString(R.string.book_details_readonly_pages, pages));
            }
        }
        // 'format' field
        if (FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_FORMAT)) {
            Field formatField = mFields.getField(R.id.format);
            String format = book.getString(UniqueId.KEY_BOOK_FORMAT);
            boolean hasFormat = format != null && !format.isEmpty();
            if (hasFormat) {
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
    private void formatPublishingSection(BookData book) {
        String date = book.getString(UniqueId.KEY_BOOK_DATE_PUBLISHED);
        boolean hasDate = date != null && !date.isEmpty();
        String pub = book.getString(UniqueId.KEY_PUBLISHER);
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
    private void showLoanedInfo(final long rowId) {
        String personLoanedTo = mDb.getLoanByBook(rowId);
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
     * @param bookData the book
     */
    private void showReadStatus(@NonNull final BookData bookData) {
        final CheckedTextView readField = getView().findViewById(R.id.read);
        boolean visible = FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_READ);
        readField.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (visible) {
            // set initial display state, REMINDER: setSelected will NOT update the GUI...
            readField.setChecked(bookData.isRead());
            readField.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    boolean newState = !readField.isChecked();
                    if (BookUtils.setRead(mDb, bookData, newState)) {
                        readField.setChecked(newState);
                    }
                }
            });
        }
    }

    /**
     * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
     *
     * @param bookData Cursor containing information of the book from database
     */
    private void showSignedStatus(@NonNull final BookData bookData) {
        if (bookData.isSigned()) {
            TextView v = getView().findViewById(R.id.signed);
            v.setText(getResources().getString(R.string.yes));
        }
    }

    /**
     * Updates all fields of book from database.
     */
    private void updateFields(@NonNull final BookData bookData) {
        populateFieldsFromBook(bookData);
        // Populate author and series fields
        populateAuthorListField();
        populateSeriesListField();
    }

    @Override
    protected void onLoadBookDetails(@NonNull final BookData bookData, final boolean setAllDone) {
        if (!setAllDone)
            mFields.setAll(bookData);
        updateFields(bookData);
    }

    @Override
    protected void onSaveBookDetails(@NonNull final BookData bookData) {
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
        public String format(@NonNull final Field f, @Nullable final String source) {
            try {
                boolean val = Datum.toBoolean(source, false);
                return BookCatalogueApp.getResourceString(val ? R.string.yes : R.string.no);
            } catch (Exception e) {
                return source;
            }
        }

        /**
         * Extract as an SQL date.
         */
        public String extract(@NonNull final Field f, @NonNull final String source) {
            try {
                return Datum.toBoolean(source, false) ? "1" : "0";
            } catch (Exception e) {
                return source;
            }
        }
    }

    protected class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {

        AnthologyTitleListAdapter(@NonNull final Context context, final int rowViewId, @NonNull final ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(@NonNull final View convertView, @NonNull final AnthologyTitle item, final int position) {
            TextView author = convertView.findViewById(R.id.row_author);
            author.setText(item.getAuthor().getDisplayName());
            TextView title = convertView.findViewById(R.id.row_title);
            title.setText(item.getTitle());
        }
    }

}
