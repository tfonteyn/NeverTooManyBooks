/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.xml;

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

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.StartupActivity;
import com.hardbacknutter.nevertomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertomanybooks.backup.Exporter;
import com.hardbacknutter.nevertomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertomanybooks.database.cursors.ColumnMapper;
import com.hardbacknutter.nevertomanybooks.database.cursors.MappedCursorRow;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.Book;
import com.hardbacknutter.nevertomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertomanybooks.entities.Series;
import com.hardbacknutter.nevertomanybooks.utils.IllegalTypeException;

/**
 * <strong>WARNING: EXPERIMENTAL</strong>
 * <p>
 * There are two types of XML here.
 * <p>
 * Type based, where the tag name is the type. Used by:
 * <ul>
 * <li>{@link BackupInfo}</li>
 * <li>{@link android.content.SharedPreferences}</li>
 * <li>{@link BooklistStyle}</li>
 * Reason:
 * <ul>
 * <li>more or less flat objects (Bundle or Bundle-like)</li>
 * <li>can be generically written (and read), so future adding/remove
 * entries requires no changes here</li>
 * <li>really only useful to the application itself</li>
 * </ul>
 * Database column name based. Used by:
 * <ul>
 * <li>{@link Bookshelf}</li>
 * <li>{@link Author}</li>
 * <li>{@link Series}</li>
 * <li>{@link Book}</li>
 * </ul>
 * Reason:
 * <ul>
 * <li>EXPERIMENTAL (NO import facility; format can/will change)</li>
 * <li>meant for loading on a computer to create reports or whatever...</li>
 * <li>not bound to the application itself.</li>
 * </ul>
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

    /** Database Access. */
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
                        break;
                }
                pos++;

            }
            return sb.toString();
        } catch (@NonNull final NullPointerException e) {
            return "\"\"";
        }
    }

    /**
     * Fulfils the interface contract. Not in direct use yet.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @WorkerThread
    public int doBooks(@NonNull final OutputStream outputStream,
                       @NonNull final ProgressListener listener,
                       final boolean includeCoverCount)
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

    /**
     * Write all supported item types to the output stream as XML.
     *
     * @param outputStream Stream for writing data
     * @param listener     Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public void doAll(@NonNull final OutputStream outputStream,
                      @NonNull final ProgressListener listener)
            throws IOException {

        int delta = 0;

        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), BUFFER_SIZE)) {

            out.append('<' + XmlTags.XML_ROOT)
               .append(version(XML_EXPORTER_VERSION))
               .append(">\n");

            if (!listener.isCancelled()) {
                listener.onProgressStep(delta++, R.string.lbl_bookshelves);
                doBookshelves(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(delta++, R.string.lbl_author);
                doAuthors(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(delta++, R.string.lbl_series);
                doSeries(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(delta++, R.string.lbl_book);
                doBooks(out, listener);
            }

            if (!listener.isCancelled()
                && (mSettings.what & ExportOptions.BOOK_LIST_STYLES) != 0) {
                listener.onProgress(delta++, R.string.lbl_styles);
                doStyles(out, listener);
            }

            if (!listener.isCancelled()
                && (mSettings.what & ExportOptions.PREFERENCES) != 0) {
                listener.onProgress(delta++, R.string.lbl_settings);
                doPreferences(out, listener);
            }

            out.append("</" + XmlTags.XML_ROOT + ">\n");
        }
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
    @SuppressWarnings("UnusedReturnValue")
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
               .append(attr(DBDefinitions.KEY_BOOKSHELF, bookshelf.getName()))
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
    @SuppressWarnings("UnusedReturnValue")
    private int doAuthors(@NonNull final BufferedWriter out,
                          @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlTags.XML_AUTHOR_LIST)
           .append(version(XML_EXPORTER_AUTHORS_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchAuthors()) {
            ColumnMapper mapper = new ColumnMapper(cursor);
            while (cursor.moveToNext()) {
                //URGENT: redo this with child xml tags, and give each tag a 'type' attribute
                out.append('<' + XmlTags.XML_AUTHOR)
                   .append(id(mapper.getLong(DBDefinitions.KEY_PK_ID)))

                   .append(attr(DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                                mapper.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME)))
                   .append(attr(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES,
                                mapper.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES)))
                   .append(attr(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                mapper.getInt(DBDefinitions.KEY_AUTHOR_IS_COMPLETE)))
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
    @SuppressWarnings("UnusedReturnValue")
    private int doSeries(@NonNull final BufferedWriter out,
                         @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlTags.XML_SERIES_LIST)
           .append(version(XML_EXPORTER_SERIES_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchSeries()) {
            ColumnMapper mapper = new ColumnMapper(cursor);
            while (cursor.moveToNext()) {
                out.append('<' + XmlTags.XML_SERIES)
                   .append(id(mapper.getLong(DBDefinitions.KEY_PK_ID)))
                   .append(attr(DBDefinitions.KEY_SERIES_TITLE,
                                mapper.getString(DBDefinitions.KEY_SERIES_TITLE)))
                   .append(attr(DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                mapper.getInt(DBDefinitions.KEY_SERIES_IS_COMPLETE)))
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
    private int doBooks(@NonNull final BufferedWriter out,
                        @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlTags.XML_BOOK_LIST)
           .append(version(XML_EXPORTER_BOOKS_VERSION))
           .append(">\n");

        try (BookCursor bookCursor = mDb.fetchBooksForExport(mSettings.dateFrom)) {
            MappedCursorRow cursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                // basic ID
                out.append('<' + XmlTags.XML_BOOK)
                   .append(id(cursorRow.getLong(DBDefinitions.KEY_PK_ID)))
                   .append(attr(DBDefinitions.KEY_TITLE,
                                cursorRow.getString(DBDefinitions.KEY_TITLE)))
                   .append(attr(DBDefinitions.KEY_ISBN,
                                cursorRow.getString(DBDefinitions.KEY_ISBN)))
                   .append("\n")

                   // publishing information
                   .append(attr(DBDefinitions.KEY_PUBLISHER,
                                cursorRow.getString(DBDefinitions.KEY_PUBLISHER)))
                   .append(attr(DBDefinitions.KEY_DATE_PUBLISHED,
                                cursorRow.getString(DBDefinitions.KEY_DATE_PUBLISHED)))
                   .append(attr(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                cursorRow.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)))
                   .append("\n")
                   .append(attr(DBDefinitions.KEY_FORMAT,
                                cursorRow.getString(DBDefinitions.KEY_FORMAT)))
                   .append(attr(DBDefinitions.KEY_PAGES,
                                cursorRow.getString(DBDefinitions.KEY_PAGES)))
                   .append(attr(DBDefinitions.KEY_GENRE,
                                cursorRow.getString(DBDefinitions.KEY_GENRE)))
                   .append(attr(DBDefinitions.KEY_LANGUAGE,
                                cursorRow.getString(DBDefinitions.KEY_LANGUAGE)))
                   .append(attr(DBDefinitions.KEY_TOC_BITMASK,
                                cursorRow.getLong(DBDefinitions.KEY_TOC_BITMASK)))
                   .append("\n")

                   // reading facts
                   .append(attr(DBDefinitions.KEY_READ,
                                cursorRow.getInt(DBDefinitions.KEY_READ)))
                   .append(attr(DBDefinitions.KEY_READ_START,
                                cursorRow.getString(DBDefinitions.KEY_READ_START)))
                   .append(attr(DBDefinitions.KEY_READ_END,
                                cursorRow.getString(DBDefinitions.KEY_READ_END)))
                   .append("\n")

                   // price information
                   .append(attr(DBDefinitions.KEY_PRICE_LISTED,
                                cursorRow.getString(DBDefinitions.KEY_PRICE_LISTED)))
                   .append(attr(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                cursorRow.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)))
                   .append(attr(DBDefinitions.KEY_PRICE_PAID,
                                cursorRow.getString(DBDefinitions.KEY_PRICE_PAID)))
                   .append(attr(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                                cursorRow.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)))
                   .append(attr(DBDefinitions.KEY_DATE_ACQUIRED,
                                cursorRow.getString(DBDefinitions.KEY_DATE_ACQUIRED)))
                   .append("\n")


                   .append(attr(DBDefinitions.KEY_LOCATION,
                                cursorRow.getString(DBDefinitions.KEY_LOCATION)))
                   .append(attr(DBDefinitions.KEY_RATING,
                                cursorRow.getDouble(DBDefinitions.KEY_RATING)))
                   .append(attr(DBDefinitions.KEY_SIGNED,
                                cursorRow.getInt(DBDefinitions.KEY_SIGNED)))
                   .append(attr(DBDefinitions.KEY_EDITION_BITMASK,
                                cursorRow.getLong(DBDefinitions.KEY_EDITION_BITMASK)))
                   .append("\n")

                   // external ID's
                   .append(attr(DBDefinitions.KEY_LIBRARY_THING_ID,
                                cursorRow.getLong(DBDefinitions.KEY_LIBRARY_THING_ID)))
                   .append(attr(DBDefinitions.KEY_OPEN_LIBRARY_ID,
                                cursorRow.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID)))
                   .append(attr(DBDefinitions.KEY_ISFDB_ID,
                                cursorRow.getLong(DBDefinitions.KEY_ISFDB_ID)))
                   .append(attr(DBDefinitions.KEY_GOODREADS_BOOK_ID,
                                cursorRow.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID)))
                   .append(attr(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE,
                                cursorRow.getString(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE)))
                   .append("\n")

                   .append(attr(DBDefinitions.KEY_DATE_ADDED,
                                cursorRow.getString(DBDefinitions.KEY_DATE_ADDED)))
                   .append(attr(DBDefinitions.KEY_DATE_LAST_UPDATED,
                                cursorRow.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)))
                   .append(attr(DBDefinitions.KEY_BOOK_UUID,
                                cursorRow.getString(DBDefinitions.KEY_BOOK_UUID)))
                   .append("\n")

                   .append(attr(DBDefinitions.KEY_LOANEE,
                                cursorRow.getString(DBDefinitions.KEY_LOANEE)))
                   .append(">\n");

                out.append(tagWithCData(DBDefinitions.KEY_DESCRIPTION, null,
                                        cursorRow.getString(DBDefinitions.KEY_DESCRIPTION)));
                out.append(tagWithCData(DBDefinitions.KEY_NOTES, null,
                                        cursorRow.getString(DBDefinitions.KEY_NOTES)));

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
            for (Filter filter : style.getFilters()) {
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
    @SuppressWarnings("UnusedReturnValue")
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
    @SuppressWarnings("UnusedReturnValue")
    public int doPreferences(@NonNull final BufferedWriter out,
                             @NonNull final ProgressListener listener)
            throws IOException {
        // remove the acra settings
        Map<String, ?> all = mSettings.getPrefs().getAll();
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
            return XmlTags.XML_INFO;
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
     * --- This includes the actual groups of the style: a CSV String of ID's (kinds)
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
