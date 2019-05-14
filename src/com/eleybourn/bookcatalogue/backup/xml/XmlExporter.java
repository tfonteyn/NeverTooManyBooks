package com.eleybourn.bookcatalogue.backup.xml;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.filters.BooleanFilter;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_TOC_BITMASK;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_SERIES;

/**
 * WARNING: EXPERIMENTAL
 * <p>
 * There are two types of XML here.
 * <p>
 * Type based, where the tag name is the type. Used by:
 * {@link BackupInfo}
 * {@link android.content.SharedPreferences}
 * {@link BooklistStyle}
 * Reason:
 * - more or less flat objects (Bundle or Bundle-like)
 * - can be generically written (and read), so future adding/remove entries requires no changes here
 * - really only useful to the application itself.
 * <p>
 * Database column name based. Used by:
 * {@link Bookshelf}
 * {@link Author}
 * {@link Series}
 * {@link Book}
 * Reason:
 * - EXPERIMENTAL (NO import facility; format can/will change)
 * - meant for loading on a computer to create reports or whatever...
 * - not bound to the application itself.
 */
public class XmlExporter
        implements Exporter, Closeable {

    // uber-version of the exporter (not necessarily the same as the archive container !)
    private static final int XML_EXPORTER_VERSION = 2;

    // individual format versions of table based data
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;
    private static final int XML_EXPORTER_BOOKS_VERSION = 1;

    // individual format version of Styles
    private static final int XML_EXPORTER_STYLES_VERSION = 1;

    private static final int BUFFER_SIZE = 32768;

    /** Database access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final ExportOptions mSettings;

    public XmlExporter() {
        mDb = new DAO();
        mSettings = new ExportOptions();
        mSettings.what = ExportOptions.ALL;
    }

    /**
     * Constructor.
     *
     * @param settings exporting books respects {@link ExportOptions#dateFrom}
     *                 All other flags are ignored.
     */
    @UiThread
    public XmlExporter(@NonNull final ExportOptions settings) {
        mDb = new DAO();
        settings.validate();
        mSettings = settings;
    }

    private static String version(final long version) {
        return ' ' + XmlTags.ATTR_VERSION + "=\"" + version + '"';
    }

    /**
     * Database row-id.
     *
     * @param id of the item in its table
     *
     * @return string representation of the attribute, with leading space.
     */
    private static String id(final long id) {
        return ' ' + XmlTags.ATTR_ID + "=\"" + id + '"';
    }

    /** element name attribute; i.e. the "thing" we are reading/writing. */
    private static String name(@NonNull final String name) {
        return ' ' + XmlTags.ATTR_NAME + "=\"" + name + '"';
    }

    /** the value of the individual item of the "thing". */
    private static String value(@NonNull final String value) {
        return ' ' + XmlTags.ATTR_VALUE + "=\"" + encodeString(value) + '"';
    }

    private static String size(final long size) {
        return ' ' + XmlTags.ATTR_SIZE + "=\"" + size + '"';
    }


    private static String attr(@NonNull final String attr,
                               final double value) {
        return ' ' + attr + "=\"" + value + '"';
    }

    private static String attr(@NonNull final String attr,
                               final long value) {
        return ' ' + attr + "=\"" + value + '"';
    }

    /**
     * Generic String value attribute. The String will be encoded.
     *
     * @param attr  attribute name
     * @param value attribute value, a string
     *
     * @return string representation of the attribute, with leading space.
     */
    private static String attr(@NonNull final String attr,
                               @NonNull final String value) {
        return ' ' + attr + "=\"" + encodeString(value) + '"';
    }

    /**
     * String values are automatically encoded.
     *
     * @return tag with (optional) name and value attribute, empty body
     */
    private static String tag(@NonNull final String tag,
                              @Nullable final String name,
                              @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            // strings are encoded
            return '<' + tag + (name != null ? name(name) : "")
                    + value(encodeString(String.valueOf(value))) + "/>\n";
        } else {
            // non-strings as-is; for boolean this means: true,false
            return typedTag(name, value);
        }
    }

    /**
     * No encoding of the value is done here.
     *
     * @return tag with (optional) name attribute and content body
     */
    private static String tagWithBody(@NonNull final String tag,
                                      @Nullable final String name,
                                      @NonNull final Object value) {

        return '<' + tag + (name != null ? name(name) : "") + '>'
                + value
                + "</" + tag + ">\n";
    }


    /**
     * @return tag with (optional) name attribute and CDATA content body
     */
    private static String tagWithCData(@NonNull final String tag,
                                       @Nullable final String name,
                                       @NonNull final String value) {
        return '<' + tag + (name != null ? name(name) : "") + ">\n"
                + "<![CDATA[" + value + "]]>\n"
                + "</" + tag + ">\n";
    }

    /**
     * Encodes a single value with a tag equal to the value's type.
     * Strings are CDATA encoded; others use the 'value' attribute.
     *
     * @param name  (optional) attribute for the tag
     * @param value to encode
     *
     * @return xml tag
     */
    private static String typedTag(@Nullable final String name,
                                   @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            return tagWithCData(XmlTags.XML_STRING, name, String.valueOf(value));

        } else if (value instanceof Boolean) {
            return tag(XmlTags.XML_BOOLEAN, name, String.valueOf(value));
        } else if (value instanceof Integer) {
            return tag(XmlTags.XML_INT, name, String.valueOf(value));
        } else if (value instanceof Long) {
            return tag(XmlTags.XML_LONG, name, String.valueOf(value));
        } else if (value instanceof Float) {
            return tag(XmlTags.XML_FLOAT, name, String.valueOf(value));
        } else if (value instanceof Double) {
            return tag(XmlTags.XML_DOUBLE, name, String.valueOf(value));

        } else if (value instanceof Set) {
            return tagWithBody(XmlTags.XML_SET, name, typedCollection((Collection) value));
        } else if (value instanceof List) {
            return tagWithBody(XmlTags.XML_LIST, name, typedCollection((Collection) value));

        } else if (value instanceof Serializable) {
            return tagWithBody(XmlTags.XML_SERIALIZABLE, name,
                               Base64.encodeToString(convertToBytes(value), Base64.DEFAULT));

        } else {
            //noinspection ConstantConditions
            throw new IllegalTypeException(value.getClass().getCanonicalName());
        }
    }

    /**
     * Encode the given Collection to xml.
     *
     * @param values to encode
     *
     * @return partial xml
     */
    private static String typedCollection(@NonNull final Collection values)
            throws IOException {
        StringBuilder sb = new StringBuilder("\n");
        for (Object value : values) {
            sb.append(typedTag(null, value));
        }
        return sb.toString();
    }

    /**
     * Helper for encoding an object to Base64. The android lib wants a byte[]
     *
     * @param object to transform
     *
     * @return the array
     *
     * @throws IOException on failure
     */
    private static byte[] convertToBytes(@NonNull final Object object)
            throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }

    /**
     * The 'value' attribute should be encoded if it's a String.
     * If there is a String body, then use {@link #tagWithCData}
     * <p>
     * escape reserved XML characters + all newlines/tab and the backslash.
     * <p>
     * quot "   U+0022 (34) XML 1.0 double quotation mark
     * amp  &   U+0026 (38) XML 1.0 ampersand
     * apos '   U+0027 (39) XML 1.0 apostrophe (apostrophe-quote)
     * lt   <   U+003C (60) XML 1.0 less-than sign
     * gt   >   U+003E (62) XML 1.0 greater-than sign
     *
     * @param data to encode
     *
     * @return The encoded data
     */
    @NonNull
    private static String encodeString(@Nullable final String data) {
        try {
            if (data == null || data.trim().isEmpty() || "null".equalsIgnoreCase(data)) {
                return "";
            }

            final StringBuilder sb = new StringBuilder();
            int endPos = data.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                char c = data.charAt(pos);
                switch (c) {
                    case '"':
                        sb.append("&quot;");
                        break;
                    case '&':
                        sb.append("&amp;");
                        break;
                    case '\'':
                        sb.append("&apos;");
                        break;
                    case '<':
                        sb.append("&lt;");
                        break;
                    case '>':
                        sb.append("&gt;");
                        break;

                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        sb.append(c);
                }
                pos++;

            }
            return sb.toString();
        } catch (NullPointerException e) {
            return "\"\"";
        }
    }

    /**
     * Fulfils the contract for {@link Exporter#doBooks(OutputStream, ProgressListener)}.
     * Not in direct use yet.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @WorkerThread
    public int doBooks(@NonNull final OutputStream outputStream,
                       @NonNull final ProgressListener listener)
            throws IOException {

        int pos;

        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), BUFFER_SIZE)) {
            out.append('<' + XmlTags.XML_ROOT)
               .append(version(XML_EXPORTER_VERSION))
               .append(">\n");
            pos = doBooks(out, listener);
            out.append("</" + XmlTags.XML_ROOT + ">\n");
        }
        return pos;
    }

    @SuppressWarnings("UnusedReturnValue")
    @WorkerThread
    public int doAll(@NonNull final OutputStream outputStream,
                     @NonNull final ProgressListener listener)
            throws IOException {

        int pos = 0;
        listener.setMax(5);

        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), BUFFER_SIZE)) {

            out.append('<' + XmlTags.XML_ROOT)
               .append(version(XML_EXPORTER_VERSION))
               .append(">\n");

            if (!listener.isCancelled()) {
                listener.onProgress(pos++, R.string.lbl_bookshelves);
                pos += doBookshelves(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(pos++, R.string.lbl_author);
                pos += doAuthors(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(pos++, R.string.lbl_series);
                pos += doSeries(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(pos++, R.string.lbl_book);
                pos += doBooks(out, listener);
            }

            if (!listener.isCancelled()
                    && (mSettings.what & ExportOptions.BOOK_LIST_STYLES) != 0) {
                listener.onProgress(pos++, R.string.lbl_styles);
                pos += doStyles(out, listener);
            }

            if (!listener.isCancelled()
                    && (mSettings.what & ExportOptions.PREFERENCES) != 0) {
                listener.onProgress(pos++, R.string.lbl_settings);
                pos += doPreferences(out, listener);
            }

            out.append("</" + XmlTags.XML_ROOT + ">\n");
        }
        return pos;
    }

    /**
     * Write out {@link DBDefinitions#TBL_BOOKSHELF}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doBookshelves(@NonNull final BufferedWriter out,
                              @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        List<Bookshelf> list = mDb.getBookshelves();
        out.append('<' + XmlTags.XML_BOOKSHELF_LIST)
           .append(version(XML_EXPORTER_BOOKSHELVES_VERSION))
           .append(">\n");

        for (Bookshelf bookshelf : list) {
            out.append('<' + XmlTags.XML_BOOKSHELF)
               .append(id(bookshelf.getId()))
               .append(attr(DOM_BOOKSHELF.name, bookshelf.getName()))
               .append("/>\n");
            count++;
        }
        out.append("</" + XmlTags.XML_BOOKSHELF_LIST + ">\n");
        return count;
    }

    /**
     * Write out {@link DBDefinitions#TBL_AUTHORS}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doAuthors(@NonNull final BufferedWriter out,
                          @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlTags.XML_AUTHOR_LIST)
           .append(version(XML_EXPORTER_AUTHORS_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchAuthors()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);

            while (cursor.moveToNext()) {
                out.append('<' + XmlTags.XML_AUTHOR)
                   .append(id(mapper.getLong(DOM_PK_ID)))

                   .append(attr(DOM_AUTHOR_FAMILY_NAME.name,
                                mapper.getString(DOM_AUTHOR_FAMILY_NAME)))
                   .append(attr(DOM_AUTHOR_GIVEN_NAMES.name,
                                mapper.getString(DOM_AUTHOR_GIVEN_NAMES)))
                   .append(attr(DOM_AUTHOR_IS_COMPLETE.name,
                                mapper.getInt(DOM_AUTHOR_IS_COMPLETE)))
                   .append("/>\n");
                count++;
            }
        }
        out.append("</" + XmlTags.XML_AUTHOR_LIST + ">\n");
        return count;
    }

    /**
     * Write out {@link DBDefinitions#TBL_SERIES}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doSeries(@NonNull final BufferedWriter out,
                         @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlTags.XML_SERIES_LIST)
           .append(version(XML_EXPORTER_SERIES_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchSeries()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES);
            while (cursor.moveToNext()) {
                out.append('<' + XmlTags.XML_SERIES)
                   .append(id(mapper.getLong(DOM_PK_ID)))
                   .append(attr(DOM_SERIES_TITLE.name,
                                mapper.getString(DOM_SERIES_TITLE)))
                   .append(attr(DOM_SERIES_IS_COMPLETE.name,
                                mapper.getInt(DOM_SERIES_IS_COMPLETE)))
                   .append("/>\n");
            }
        }
        out.append("</" + XmlTags.XML_SERIES_LIST + ">\n");
        return count;
    }

    /**
     * 'loan_to' is included here, might still change.
     * <p>
     * Write out {@link DBDefinitions#TBL_BOOKS}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    public int doBooks(@NonNull final BufferedWriter out,
                       @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlTags.XML_BOOK_LIST)
           .append(version(XML_EXPORTER_BOOKS_VERSION))
           .append(">\n");

        try (BookCursor bookCursor = mDb.fetchBooksForExport(mSettings.dateFrom)) {
            BookCursorRow bookCursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                // basic ID
                out.append('<' + XmlTags.XML_BOOK)
                   .append(id(bookCursorRow.getId()))
                   .append(attr(DOM_TITLE.name,
                                bookCursorRow.getTitle()))
                   .append(attr(DOM_BOOK_ISBN.name,
                                bookCursorRow.getIsbn()))
                   .append("\n")

                   // publishing information
                   .append(attr(DOM_BOOK_PUBLISHER.name,
                                bookCursorRow.getPublisherName()))
                   .append(attr(DOM_BOOK_DATE_PUBLISHED.name,
                                bookCursorRow.getDatePublished()))
                   .append(attr(DOM_FIRST_PUBLICATION.name,
                                bookCursorRow.getFirstPublication()))
                   .append("\n")
                   .append(attr(DOM_BOOK_FORMAT.name,
                                bookCursorRow.getFormat()))
                   .append(attr(DOM_BOOK_PAGES.name,
                                bookCursorRow.getPages()))
                   .append(attr(DOM_BOOK_GENRE.name,
                                bookCursorRow.getGenre()))
                   .append(attr(DOM_BOOK_LANGUAGE.name,
                                bookCursorRow.getLanguageCode()))
                   .append(attr(DOM_BOOK_TOC_BITMASK.name,
                                bookCursorRow.getAnthologyBitMask()))
                   .append("\n")

                   // reading facts
                   .append(attr(DOM_BOOK_READ.name,
                                bookCursorRow.getRead()))
                   .append(attr(DOM_BOOK_READ_START.name,
                                bookCursorRow.getReadStart()))
                   .append(attr(DOM_BOOK_READ_END.name,
                                bookCursorRow.getReadEnd()))
                   .append("\n")

                   // price information
                   .append(attr(DOM_BOOK_PRICE_LISTED.name,
                                bookCursorRow.getListPrice()))
                   .append(attr(DOM_BOOK_PRICE_LISTED_CURRENCY.name,
                                bookCursorRow.getListPriceCurrency()))
                   .append(attr(DOM_BOOK_PRICE_PAID.name,
                                bookCursorRow.getPricePaid()))
                   .append(attr(DOM_BOOK_PRICE_PAID_CURRENCY.name,
                                bookCursorRow.getPricePaidCurrency()))
                   .append(attr(DOM_BOOK_DATE_ACQUIRED.name,
                                bookCursorRow.getDateAcquired()))
                   .append("\n")


                   .append(attr(DOM_BOOK_LOCATION.name,
                                bookCursorRow.getLocation()))
                   .append(attr(DOM_BOOK_RATING.name,
                                bookCursorRow.getRating()))
                   .append(attr(DOM_BOOK_SIGNED.name,
                                bookCursorRow.getSigned()))
                   .append(attr(DOM_BOOK_EDITION_BITMASK.name,
                                bookCursorRow.getEditionBitMask()))
                   .append("\n")

                   // external id's
                   .append(attr(DOM_BOOK_LIBRARY_THING_ID.name,
                                bookCursorRow.getLibraryThingBookId()))
                   .append(attr(DOM_BOOK_OPEN_LIBRARY_ID.name,
                                bookCursorRow.getOpenLibraryBookId()))
                   .append(attr(DOM_BOOK_ISFDB_ID.name,
                                bookCursorRow.getISFDBBookId()))
                   .append(attr(DOM_BOOK_GOODREADS_BOOK_ID.name,
                                bookCursorRow.getGoodreadsBookId()))
                   .append(attr(DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name,
                                bookCursorRow.getDateLastSyncedWithGoodreads()))
                   .append("\n")

                   .append(attr(DOM_BOOK_DATE_ADDED.name,
                                bookCursorRow.getDateAdded()))
                   .append(attr(DOM_LAST_UPDATE_DATE.name,
                                bookCursorRow.getDateLastUpdated()))
                   .append(attr(DOM_BOOK_UUID.name,
                                bookCursorRow.getBookUuid()))
                   .append("\n")

                   .append(attr(DOM_BOOK_LOANEE.name,
                                bookCursorRow.getLoanedTo()))
                   .append(">\n");

                out.append(tagWithCData(DOM_BOOK_DESCRIPTION.name, null,
                                        bookCursorRow.getDescription()));
                out.append(tagWithCData(DOM_BOOK_NOTES.name, null,
                                        bookCursorRow.getNotes()));

                out.append("</" + XmlTags.XML_BOOK + ">\n");
                count++;
            }
        }
        out.append("</" + XmlTags.XML_BOOK_LIST + ">\n");
        return count;
    }

    /**
     * Write out the user-defined styles.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    public int doStyles2(@NonNull final BufferedWriter out,
                         @NonNull final ProgressListener listener)
            throws IOException {
        Collection<BooklistStyle> styles = BooklistStyles.getUserStyles(mDb).values();
        if (styles.isEmpty()) {
            return 0;
        }

        out.append('<' + XmlTags.XML_STYLE_LIST)
           .append(version(XML_EXPORTER_STYLES_VERSION))
           .append(">\n");

        for (BooklistStyle style : styles) {
            out.append('<' + XmlTags.XML_STYLE)
               .append(id(style.getId()))
               .append(name(style.getUuid()))
               .append(">\n");

            // 'Flat' Preferences for this style.
            for (PPref p : style.getPreferences(false).values()) {
                out.append(typedTag(p.getKey(), p.get()));
            }

            // Groups with their Preferences
            out.append('<' + XmlTags.XML_GROUP_LIST + '>');
            for (BooklistGroup group : style.getGroups()) {
                out.append('<' + XmlTags.XML_GROUP)
                   .append(id(group.getKind()))
                   .append(">\n");
                for (PPref p : group.getPreferences().values()) {
                    out.append(typedTag(p.getKey(), p.get()));
                }
                out.append("</" + XmlTags.XML_GROUP + ">\n");
            }
            out.append("</" + XmlTags.XML_GROUP_LIST + '>');

            // Filters with their Preferences
            out.append('<' + XmlTags.XML_FILTER_LIST + '>');
            for (BooleanFilter filter : style.getFilters().values()) {
                if (filter.isActive()) {
                    out.append(tag(XmlTags.XML_FILTER, filter.getKey(), filter.get()));
                }
            }
            out.append("</" + XmlTags.XML_FILTER_LIST + '>');

            out.append("</" + XmlTags.XML_STYLE + ">\n");
        }
        out.append("</" + XmlTags.XML_STYLE_LIST + ">\n");
        return styles.size();
    }

    /**
     * Write out the user-defined styles.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    public int doStyles(@NonNull final BufferedWriter out,
                        @NonNull final ProgressListener listener)
            throws IOException {
        Collection<BooklistStyle> styles = BooklistStyles.getUserStyles(mDb).values();
        if (styles.isEmpty()) {
            return 0;
        }
        toXml(out, new StylesWriter(styles));
        return styles.size();
    }

    /**
     * Write out the user preferences.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    public int doPreferences(@NonNull final BufferedWriter out,
                             @NonNull final ProgressListener listener)
            throws IOException {
        // remove the acra settings
        Map<String, ?> all = App.getPrefs().getAll();
        Iterator<String> it = all.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.startsWith("acra")) {
                it.remove();
            }
        }
        toXml(out, new PreferencesWriter(all, null));
        return 1;
    }

    /**
     * Write out the standard archive info block.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    public void doBackupInfoBlock(@NonNull final BufferedWriter out,
                                  @NonNull final ProgressListener listener,
                                  @NonNull final BackupInfo info)
            throws IOException {
        toXml(out, new InfoWriter(info));
    }

    /**
     * Internal routine to send the passed {@link EntityWriter} data to an XML file.
     *
     * @param out      where to send the XML to
     * @param accessor which provides the input
     */
    private void toXml(@NonNull final BufferedWriter out,
                       @NonNull final EntityWriter<String> accessor)
            throws IOException {

        String listRoot = accessor.getListRoot();
        out.append('<').append(listRoot)
           .append(size(accessor.size()))
           .append(">\n");

        // loop through all elements
        do {
            // sure, not needed, but if you want to eyeball the resulting file...
            String[] keys = accessor.keySet().toArray(new String[]{});
            Arrays.sort(keys);

            // start with an element, optionally add a name attribute
            out.append('<').append(accessor.getElementRoot());
            String nameAttr = accessor.getNameAttribute();
            if (nameAttr != null) {
                out.append(name(nameAttr));
            }
            out.append(version(accessor.getVersionAttribute()))
               .append(" >\n");

            // loop through all keys of the element
            for (String name : keys) {
                out.append(typedTag(name, accessor.get(name)));
            }
            out.append("</").append(accessor.getElementRoot()).append(">\n");
        } while (accessor.hasMore());

        // close the list.
        out.append("</").append(listRoot).append(">\n");
    }


    @Override
    public void close() {
        mDb.close();
    }

    /**
     * Class to provide access to a subset of the methods of collections.
     *
     * @param <K> Type of the collection key
     */
    interface EntityWriter<K> {

        /**
         * @return the top-root list element name (even if the list only contains one element)
         */
        @NonNull
        String getListRoot();

        /**
         * @return size of the list
         */
        int size();

        /**
         * When we do not have a list, this method should return 'false'.
         * <p>
         * When there is a list, then:
         * - the first element should be set to the 'current'
         * - a loop should be (is) implemented with:
         * <pre>
         * {@code
         *      do {
         *          current.get(...)
         *      } while (hasMore());
         * }
         * </pre>
         * See {@link XmlImporter.StylesReader} for an example
         */
        boolean hasMore();


        /**
         * @return The root element for each item in a list.
         */
        @NonNull
        String getElementRoot();

        /**
         * @return The name attribute to be set on the {@link #getElementRoot()}.
         * Can be {@code null}.
         */
        @Nullable
        String getNameAttribute();

        /**
         * @return The version attribute to be set on the {@link #getElementRoot()}.
         * We set it on the element, not on the list, as it describes the format of one element.
         */
        long getVersionAttribute();

        /**
         * @return the collection of keys for one element.
         */
        Set<K> keySet();

        /**
         * @return the object for the specified key of the current element.
         */
        @NonNull
        Object get(@NonNull K key);
    }

    /**
     * Supports a single INFO block.
     */
    static class InfoWriter
            implements EntityWriter<String> {

        private final BackupInfo mInfo;


        @NonNull
        private final Bundle mBundle;

        InfoWriter(@NonNull final BackupInfo info) {
            mInfo = info;
            mBundle = mInfo.getBundle();
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlTags.XML_INFO_LIST;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlTags.XML_INFO;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean hasMore() {
            return false;
        }

        @Nullable
        @Override
        public String getNameAttribute() {
            return null;
        }

        /**
         * @return the version of the Info block archiver version
         */
        @Override
        public long getVersionAttribute() {
            return mInfo.getArchVersion();
        }

        /**
         * Sets the iterator/current to the first element.
         * It is assumed that each element *will* have the same set of keys.
         *
         * @return the keys present in that element
         */
        @Override
        @NonNull
        public Set<String> keySet() {
            return mBundle.keySet();
        }

        @Override
        @NonNull
        public Object get(@NonNull final String key) {
            //noinspection ConstantConditions
            return mBundle.get(key);
        }
    }

    /**
     * Supports a single Preferences block.
     */
    static class PreferencesWriter
            implements EntityWriter<String> {

        @Nullable
        private final String mName;

        private final Map<String, ?> mMap;

        /**
         * @param map  to read from
         * @param name (optional) of the SharedPreference
         */
        PreferencesWriter(@NonNull final Map<String, ?> map,
                          @SuppressWarnings("SameParameterValue") @Nullable final String name) {
            mMap = map;
            mName = name;
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlTags.XML_PREFERENCES_LIST;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean hasMore() {
            return false;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlTags.XML_PREFERENCES;
        }

        @Nullable
        @Override
        public String getNameAttribute() {
            return mName;
        }

        @Override
        public long getVersionAttribute() {
            //noinspection ConstantConditions
            return (Long) mMap.get(StartupActivity.PREF_STARTUP_LAST_VERSION);
        }

        @NonNull
        @Override
        public Set<String> keySet() {
            return mMap.keySet();
        }

        @Override
        @NonNull
        public Object get(@NonNull final String key) {
            //noinspection ConstantConditions
            return mMap.get(key);
        }
    }

    /**
     * Supports a list of Styles.
     * <p>
     * - 'flat' preferences for the style.
     * --- This includes the actual groups of the style: a CSV String of id's (kinds)
     * - Filters and Groups are flattened.
     * - each filter/group has a typed tag
     * - each preference in a group has a typed tag.
     */
    static class StylesWriter
            implements EntityWriter<String> {

        private final Collection<BooklistStyle> mStyles;
        private final Iterator<BooklistStyle> it;

        private BooklistStyle current;
        /** the Preferences from the style and the groups that have PPrefs. */
        private Map<String, PPref> currentPPrefs;

        StylesWriter(@NonNull final Collection<BooklistStyle> styles) {
            mStyles = styles;
            it = styles.iterator();
            // get first element ready for processing
            hasMore();
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlTags.XML_STYLE_LIST;
        }

        @Override
        public int size() {
            return mStyles.size();
        }

        @Override
        public boolean hasMore() {
            if (it.hasNext()) {
                current = it.next();
                currentPPrefs = current.getPreferences(true);
                return true;
            } else {
                return false;
            }
        }

        @NonNull
        @Override
        public String getElementRoot() {
            return XmlTags.XML_STYLE;
        }

        @Nullable
        @Override
        public String getNameAttribute() {
            return current.getUuid();
        }

        @Override
        public long getVersionAttribute() {
            return XmlExporter.XML_EXPORTER_STYLES_VERSION;
        }

        /**
         * Sets the iterator/current to the first element.
         * It is assumed that each element *will* have the same set of keys.
         *
         * @return the keys present in that element
         */
        @Override
        @NonNull
        public Set<String> keySet() {
            return currentPPrefs.keySet();
        }

        @NonNull
        @Override
        public Object get(@NonNull final String key) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Logger.debug(this, "get", "uuid=" + current.getUuid() + "|name=" + key);
            }
            //noinspection ConstantConditions
            return currentPPrefs.get(key).get();
        }
    }
}
