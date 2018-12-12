package com.eleybourn.bookcatalogue.backup.xml;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.csv.Exporter;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;

/**
 * Not using {@link com.eleybourn.bookcatalogue.backup.BackupUtils} xml methods are they are one-dimensional.
 *
 * Yes, I thought about using javax.xml.parsers.DocumentBuilderFactory
 * but face it.. overkill and (could) use a lot of memory.
 */
public class XmlExporter implements Exporter {

    private static final int XML_EXPORTER_VERSION = 1;
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;
    private static final int XML_EXPORTER_BOOKS_VERSION = 1;

    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;

    // root element, used to recognise 'our' files during import.
    private static final String XML_ROOT = "bc";

    private static final String ATTR_VERSION = "version";
    private static final String ATTR_ID = "id";

    private static final String XML_BOOKSHELVES = "bookshelves";
    private static final String XML_BOOKSHELF = "bookshelf";

    private static final String XML_AUTHORS = "authors";
    private static final String XML_AUTHOR = "author";

    private static final String XML_SERIES_ALL = "all_series";
    private static final String XML_SERIES = "series";

    private static final String XML_BOOKS = "books";
    private static final String XML_BOOK = "book";

    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final ExportSettings mSettings;

    /**
     * Constructor
     *
     * @param settings {@link ExportSettings#file} is not used, as we must support writing to a stream.
     *
     *                 {@link ExportSettings#EXPORT_SINCE} and {@link ExportSettings#dateFrom}
     *                 are not applicable as we don't export books here
     */
    public XmlExporter(final @NonNull ExportSettings settings) {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        settings.validate();
        mSettings = settings;
    }

    private String id(final long id) {
        return " " + ATTR_ID + "=\"" + id + "\"";
    }

    private String version(final long id) {
        return " " + ATTR_VERSION + "=\"" + id + "\"";
    }

    private String attr(final @NonNull String attr, final long value) {
        return " " + attr + "=\"" + value + "\"";
    }

    private String attr(final @NonNull String attr, final double value) {
        return " " + attr + "=\"" + value + "\"";
    }

    private String attr(final @NonNull String attr, final @NonNull String value) {
        return " " + attr + "=\"" + encode(value) + "\"";
    }

    private String attr(final @NonNull String attr, final boolean value) {
        return " " + attr + "=\"" + (value ? "1" : "0") + "\"";
    }

    /**
     * escape quotes and all newlines/tab
     *
     * @param cell to format
     *
     * @return The encoded cell
     */
    @NonNull
    private String encode(final @Nullable String cell) {
        try {
            if (cell == null || "null".equalsIgnoreCase(cell) || cell.trim().isEmpty()) {
                return "";
            }

            final StringBuilder bld = new StringBuilder();
            int endPos = cell.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                char c = cell.charAt(pos);
                switch (c) {
                    case '\r':
                        bld.append("\\r");
                        break;
                    case '\n':
                        bld.append("\\n");
                        break;
                    case '\t':
                        bld.append("\\t");
                        break;
                    case '"':
                        bld.append("&quot;");
                        break;
                    case '\'':
                        bld.append("&apos;");
                        break;
                    case '\\':
                        bld.append("\\\\");
                        break;
                    default:
                        bld.append(c);
                }
                pos++;

            }
            return bld.toString();
        } catch (NullPointerException e) {
            return "\"\"";
        }
    }

    @Override
    public boolean doExport(@NonNull final OutputStream outputStream,
                            @NonNull final ExportListener listener) throws IOException {

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE)) {
            out.append("<" + XML_ROOT).append(version(XML_EXPORTER_VERSION)).append(">\n");
            listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_bookshelves), 0);
            if (!doBookshelves(out) || listener.isCancelled()) {
                return false;
            }
            listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_author), 0);
            if (!doAuthors(out) || listener.isCancelled()) {
                return false;
            }
            listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_series), 0);
            if (!doSeries(out) || listener.isCancelled()) {
                return false;
            }
            listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_book), 0);
            if (!doBooks(out) || listener.isCancelled()) {
                return false;
            }
            out.append("</" + XML_ROOT + ">\n");
        }
        return true;
    }

    private boolean doBookshelves(@NonNull final BufferedWriter out) throws IOException {
        List<Bookshelf> list = mDb.getBookshelves();
        out.append("<" + XML_BOOKSHELVES).append(version(XML_EXPORTER_BOOKSHELVES_VERSION)).append(">\n");
        for (Bookshelf bookshelf : list) {
            out.append("<" + XML_BOOKSHELF)
                    .append(id(bookshelf.id))
                    .append(attr(DOM_BOOKSHELF.name, bookshelf.name))
                    .append("/>\n");
        }
        out.append("</" + XML_BOOKSHELVES + ">\n");
        return true;
    }

    private boolean doAuthors(@NonNull final BufferedWriter out) throws IOException {
        out.append("<" + XML_AUTHORS).append(version(XML_EXPORTER_AUTHORS_VERSION)).append(">\n");
        try (Cursor cursor = mDb.fetchAuthors()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);

            while (cursor.moveToNext()) {
                out.append("<" + XML_AUTHOR)
                        .append(id(mapper.getLong(DOM_PK_ID)))
                        .append(attr(DOM_AUTHOR_FAMILY_NAME.name, mapper.getString(DOM_AUTHOR_FAMILY_NAME)))
                        .append(attr(DOM_AUTHOR_GIVEN_NAMES.name, mapper.getString(DOM_AUTHOR_GIVEN_NAMES)))
                        .append(attr(DOM_AUTHOR_IS_COMPLETE.name, mapper.getInt(DOM_AUTHOR_IS_COMPLETE)))
                        .append("/>\n");
            }
        }
        out.append("</" + XML_AUTHORS + ">\n");
        return true;
    }

    private boolean doSeries(@NonNull final BufferedWriter out) throws IOException {
        out.append("<" + XML_SERIES_ALL).append(version(XML_EXPORTER_SERIES_VERSION)).append(">\n");
        try (Cursor cursor = mDb.fetchSeries()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES);
            while (cursor.moveToNext()) {
                out.append("<" + XML_SERIES)
                        .append(id(mapper.getLong(DOM_PK_ID)))
                        .append(attr(DOM_SERIES_NAME.name, mapper.getString(DOM_SERIES_NAME)))
                        .append(attr(DOM_SERIES_IS_COMPLETE.name, mapper.getInt(DOM_SERIES_IS_COMPLETE)))
                        .append("/>\n");
            }
        }
        out.append("</" + XML_SERIES_ALL + ">\n");
        return true;
    }

    /**
     * 'loan_to' is included here
     */
    private boolean doBooks(@NonNull final BufferedWriter out) throws IOException {
        out.append("<" + XML_BOOKS).append(version(XML_EXPORTER_BOOKS_VERSION)).append(">\n");
        try (BookCursor bookCursor = mDb.fetchFlattenedBooks(mSettings.dateFrom)) {
            BookRowView bookCursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                out.append("<" + XML_BOOK)
                        .append(id(bookCursorRow.getId()))
                        .append(attr(DOM_TITLE.name, bookCursorRow.getTitle()))
                        .append(attr(DOM_BOOK_ISBN.name, bookCursorRow.getIsbn()))
                        .append("\n")
                        .append(attr(DOM_BOOK_PUBLISHER.name, bookCursorRow.getPublisherName()))
                        .append(attr(DOM_BOOK_DATE_PUBLISHED.name, bookCursorRow.getDatePublished()))
                        .append(attr(DOM_FIRST_PUBLICATION.name, bookCursorRow.getFirstPublication()))
                        .append(attr(DOM_BOOK_EDITION_BITMASK.name, bookCursorRow.getEditionBitMask()))
                        .append("\n")
                        .append(attr(DOM_BOOK_RATING.name, bookCursorRow.getRating()))
                        .append(attr(DOM_BOOK_READ.name, bookCursorRow.getRead()))
                        .append(attr(DOM_BOOK_PAGES.name, bookCursorRow.getPages()))
                        .append("\n")
                        .append(attr(DOM_BOOK_NOTES.name, bookCursorRow.getNotes()))
                        .append("\n")
                        .append(attr(DOM_BOOK_PRICE_LISTED.name, bookCursorRow.getListPrice()))
                        .append(attr(DOM_BOOK_PRICE_LISTED_CURRENCY.name, bookCursorRow.getListPriceCurrency()))
                        .append(attr(DOM_BOOK_PRICE_PAID.name, bookCursorRow.getPricePaid()))
                        .append(attr(DOM_BOOK_PRICE_PAID_CURRENCY.name, bookCursorRow.getPricePaidCurrency()))
                        .append(attr(DOM_BOOK_DATE_ACQUIRED.name, bookCursorRow.getDateAcquired()))
                        .append("\n")
                        .append(attr(DOM_BOOK_ANTHOLOGY_BITMASK.name, bookCursorRow.getAnthologyBitMask()))
                        .append(attr(DOM_BOOK_LOCATION.name, bookCursorRow.getLocation()))
                        .append(attr(DOM_BOOK_READ_START.name, bookCursorRow.getReadStart()))
                        .append(attr(DOM_BOOK_READ_END.name, bookCursorRow.getReadEnd()))
                        .append(attr(DOM_BOOK_FORMAT.name, bookCursorRow.getFormat()))
                        .append(attr(DOM_BOOK_SIGNED.name, bookCursorRow.getSigned()))
                        .append("\n")
                        .append(attr(DOM_BOOK_DESCRIPTION.name, bookCursorRow.getDescription()))
                        .append("\n")
                        .append(attr(DOM_BOOK_GENRE.name, bookCursorRow.getGenre()))
                        .append(attr(DOM_BOOK_LANGUAGE.name, bookCursorRow.getLanguageCode()))
                        .append(attr(DOM_BOOK_DATE_ADDED.name, bookCursorRow.getDateAdded()))
                        .append("\n")
                        .append(attr(DOM_BOOK_LIBRARY_THING_ID.name, bookCursorRow.getLibraryThingBookId()))
                        .append(attr(DOM_BOOK_ISFDB_ID.name, bookCursorRow.getISFDBBookId()))
                        .append(attr(DOM_BOOK_GOODREADS_BOOK_ID.name, bookCursorRow.getGoodreadsBookId()))
                        .append(attr(DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name, bookCursorRow.getDateLastSyncedWithGoodreads()))
                        .append("\n")
                        .append(attr(DOM_LAST_UPDATE_DATE.name, bookCursorRow.getDateLastUpdated()))
                        .append(attr(DOM_BOOK_UUID.name, bookCursorRow.getBookUuid()))
                        .append("\n")
                        .append(attr(DOM_LOANED_TO.name, bookCursorRow.getLoanedTo()))
                        .append("/>\n");
            }
        }
        out.append("</" + XML_BOOKS + ">\n");
        return true;
    }
}
