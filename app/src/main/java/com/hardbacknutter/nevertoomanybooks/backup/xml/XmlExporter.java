/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * <strong>WARNING: EXPERIMENTAL</strong> There are two types of XML here.
 * <ul>Type based, where the tag name is the type. Used by:
 * <li>{@link BackupInfo}</li>
 * <li>{@link android.content.SharedPreferences}</li>
 * <li>{@link BooklistStyle}</li>
 * </ul>
 * <ul>Reason:
 * <li>more or less flat objects (Bundle or Bundle-like)</li>
 * <li>can be generically written (and read), so future adding/remove
 * entries requires no changes here</li>
 * <li>really only useful to the application itself</li>
 * </ul>
 * <ul>Database column name based. Used by:
 * <li>{@link Bookshelf}</li>
 * <li>{@link Author}</li>
 * <li>{@link Series}</li>
 * <li>{@link Book}</li>
 * </ul>
 * <ul>Reason:
 * <li>EXPERIMENTAL (NO import facility; format can/will change)</li>
 * <li>meant for loading on a computer to create reports or whatever...</li>
 * <li>not bound to the application itself.</li>
 * </ul>
 */
public class XmlExporter
        implements Exporter, Closeable {

    /** uber-version of the exporter (not necessarily the same as the archive container !). */
    private static final int XML_EXPORTER_VERSION = 2;

    /** individual format versions of table based data. */
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;
    private static final int XML_EXPORTER_BOOKS_VERSION = 1;

    /** individual format version of Styles. */
    private static final int XML_EXPORTER_STYLES_VERSION = 1;
    private static final int XML_EXPORTER_STYLES_VERSION_v2 = 2;

    private static final int BUFFER_SIZE = 32768;

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final ExportHelper mExportHelper;

    /**
     * Constructor.
     *
     * <strong>IMPORTANT:</strong> {@link ExportHelper#uri} is not used.
     * We always use the passed in OutputStream.
     */
    public XmlExporter() {
        mDb = new DAO();
        mExportHelper = new ExportHelper(ExportHelper.ALL, null);
        // no validation of settings obv.
    }

    /**
     * Constructor.
     * <p>
     * {@link #doBooks} respects {@link ExportHelper#getDateFrom}. All other flags are ignored.
     * <p>
     * {@link #doAll} respects {@link ExportHelper#BOOK_LIST_STYLES}
     * and {@link ExportHelper#PREFERENCES}. All other flags are ignored.
     * <p>
     * <strong>IMPORTANT:</strong> {@link ExportHelper#uri} is not used.
     * We always use the passed in OutputStream.
     *
     * @param exportHelper ExportHelper
     */
    @UiThread
    public XmlExporter(@NonNull final ExportHelper exportHelper) {
        mDb = new DAO();
        mExportHelper = exportHelper;
        mExportHelper.validate();
    }

    private static String version(final long version) {
        return ' ' + XmlTags.ATTR_VERSION + "=\"" + version + '"';
    }

    /**
     * Database row ID.
     *
     * @param id of the item in its table
     *
     * @return string representation of the attribute, with leading space.
     */
    private static String id(final long id) {
        return ' ' + XmlTags.ATTR_ID + "=\"" + id + '"';
    }

    /**
     * "name" attribute; i.e. the "thing" we are reading/writing.
     * If the incoming value is empty, an empty string is returned.
     *
     * @param value the name
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    private static String name(@Nullable final String value) {
        if (value != null && !value.isEmpty()) {
            return ' ' + XmlTags.ATTR_NAME + "=\"" + value + '"';
        } else {
            return "";
        }
    }

    /**
     * "value" attribute; the value of the individual item of the "thing".
     * If the incoming value is empty, an empty string is returned.
     *
     * @param value the value; will be encoded.
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    private static String value(@Nullable final String value) {
        if (value != null && !value.isEmpty()) {
            return ' ' + XmlTags.ATTR_VALUE + "=\"" + encodeString(value) + '"';
        } else {
            return "";
        }
    }

    /**
     * "size" attribute; should be used when writing out lists.
     *
     * @param value the size
     *
     * @return string representation of the attribute, with leading space.
     */
    private static String size(final long value) {
        return ' ' + XmlTags.ATTR_SIZE + "=\"" + value + '"';
    }

    /**
     * Generic {@code double} attribute.
     * If the incoming value is 0, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a double
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    private static String attr(@NonNull final String attr,
                               final double value) {
        if (value != 0) {
            return ' ' + attr + "=\"" + value + '"';
        } else {
            return "";
        }
    }

    /**
     * Generic {@code long} attribute.
     * If the incoming value is 0, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a long
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    private static String attr(@NonNull final String attr,
                               final long value) {
        if (value != 0) {
            return ' ' + attr + "=\"" + value + '"';
        } else {
            return "";
        }
    }

    /**
     * Generic {@code boolean} attribute.
     * <p>
     * If the incoming value is {@code false}, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a boolean
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    private static String attr(@NonNull final String attr,
                               final boolean value) {
        if (value) {
            return ' ' + attr + "=\"true\"";
        } else {
            return "";
        }
    }

    /**
     * Generic {@code String} attribute. The String will be encoded.
     * <p>
     * If the incoming value is empty, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a string
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    private static String attr(@NonNull final String attr,
                               @Nullable final String value) {
        if (value != null && !value.isEmpty()) {
            return ' ' + attr + "=\"" + encodeString(value) + '"';
        } else {
            return "";
        }
    }

    /**
     * Generic tag with (optional) name and value attribute, empty body.
     * String values are automatically encoded.
     *
     * @return the tag, or an empty string if the value was empty.
     *
     * @throws IOException on failure
     */
    private static String tag(@NonNull final String tag,
                              @Nullable final String name,
                              @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            String valueString = value.toString();
            if (!valueString.isEmpty()) {
                // strings are encoded
                return '<' + tag + name(name) + value(encodeString(String.valueOf(value))) + "/>\n";
            } else {
                return "";
            }
        } else {
            // non-strings as-is; for boolean this means: true,false
            return typedTag(name, value);
        }
    }

    /**
     * Generic tag with (optional) name attribute and content body.
     * No encoding of the value is done.
     *
     * @return the tag, or an empty string if the value was empty.
     */
    private static String tagWithBody(@NonNull final String tag,
                                      @Nullable final String name,
                                      @NonNull final Object value) {

        String valueString = value.toString();
        if (!valueString.isEmpty()) {
            return '<' + tag + name(name) + '>' + value + "</" + tag + ">\n";
        } else {
            return "";
        }
    }

    /**
     * Generic tag with (optional) name attribute and CDATA content body.
     *
     * @return the tag, or an empty string if the value was empty.
     */
    private static String tagWithCData(@NonNull final String tag,
                                       @Nullable final String name,
                                       @NonNull final String value) {
        if (!value.isEmpty()) {
            return '<' + tag + name(name) + ">\n"
                   + "<![CDATA[" + value + "]]>\n"
                   + "</" + tag + ">\n";
        } else {
            return "";
        }
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
            throw new UnexpectedValueException(value.getClass().getCanonicalName());
        }
    }

    /**
     * Encode the given Collection to xml. The order is preserved during writing.
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
     * Write all supported item types to the output stream as XML.
     *
     * @param os Stream for writing data
     * @param listener     Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public void doAll(@NonNull final OutputStream os,
                      @NonNull final ProgressListener listener)
            throws IOException {

        int delta = 0;

        boolean incStyles = (mExportHelper.options & Options.BOOK_LIST_STYLES) != 0;
        boolean incPrefs = (mExportHelper.options & Options.PREFERENCES) != 0;

        try (OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE)) {

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

            if (!listener.isCancelled() && incStyles) {
                listener.onProgress(delta++, R.string.lbl_styles);
                doStyles(out, listener);
                doStyles2(out, listener);
            }

            if (!listener.isCancelled() && incPrefs) {
                //noinspection UnusedAssignment
                listener.onProgress(delta++, R.string.lbl_settings);
                doPreferences(out, listener);
            }

            out.append("</" + XmlTags.XML_ROOT + ">\n");
        }
    }

    /**
     * Write out {@link DBDefinitions#TBL_BOOKSHELF}.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    private int doBookshelves(@NonNull final BufferedWriter writer,
                              @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        List<Bookshelf> list = mDb.getBookshelves();
        writer.append('<' + XmlTags.XML_BOOKSHELF_LIST)
              .append(version(XML_EXPORTER_BOOKSHELVES_VERSION))
              .append(">\n");

        for (Bookshelf bookshelf : list) {
            writer.append('<' + XmlTags.XML_BOOKSHELF)
                  .append(id(bookshelf.getId()))
                  .append(attr(DBDefinitions.KEY_BOOKSHELF, bookshelf.getName()))
                  .append(attr(DBDefinitions.KEY_FK_STYLE, bookshelf.getStyleUuid()))
                  .append("/>\n");
            count++;
        }
        writer.append("</" + XmlTags.XML_BOOKSHELF_LIST + ">\n");
        return count;
    }

    /**
     * Write out {@link DBDefinitions#TBL_AUTHORS}.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    private int doAuthors(@NonNull final BufferedWriter writer,
                          @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        writer.append('<' + XmlTags.XML_AUTHOR_LIST)
              .append(version(XML_EXPORTER_AUTHORS_VERSION))
              .append(">\n");

        try (Cursor cursor = mDb.fetchAuthors()) {
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                writer.append('<' + XmlTags.XML_AUTHOR)
                      .append(id(mapper.getLong(DBDefinitions.KEY_PK_ID)))

                      .append(attr(DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                                mapper.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME)))
                      .append(attr(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES,
                                mapper.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES)))
                      .append(attr(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                mapper.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE)))
                      .append("/>\n");
                count++;
            }
        }
        writer.append("</" + XmlTags.XML_AUTHOR_LIST + ">\n");
        return count;
    }

    /**
     * Write out {@link DBDefinitions#TBL_SERIES}.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    private int doSeries(@NonNull final BufferedWriter writer,
                         @NonNull final ProgressListener listener)
            throws IOException {
        int count = 0;
        writer.append('<' + XmlTags.XML_SERIES_LIST)
              .append(version(XML_EXPORTER_SERIES_VERSION))
              .append(">\n");

        try (Cursor cursor = mDb.fetchSeries()) {
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                writer.append('<' + XmlTags.XML_SERIES)
                      .append(id(mapper.getLong(DBDefinitions.KEY_PK_ID)))
                      .append(attr(DBDefinitions.KEY_SERIES_TITLE,
                                mapper.getString(DBDefinitions.KEY_SERIES_TITLE)))
                      .append(attr(DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                mapper.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE)))
                      .append("/>\n");
            }
        }
        writer.append("</" + XmlTags.XML_SERIES_LIST + ">\n");
        return count;
    }

    /**
     * Fulfils the interface contract. Not in direct use yet.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @WorkerThread
    public Results doBooks(@NonNull final OutputStream os,
                           @NonNull final ProgressListener listener,
                           final boolean includeCoverCount)
            throws IOException {

        Results results = new Results();

        try (OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw, BUFFER_SIZE)) {

            writer.append('<' + XmlTags.XML_ROOT)
               .append(version(XML_EXPORTER_VERSION))
               .append(">\n");
            results.booksExported = doBooks(writer, listener);
            writer.append("</" + XmlTags.XML_ROOT + ">\n");
        }

        results.booksProcessed = results.booksExported;

        return results;
    }

    /**
     * 'loan_to' is included here, might still change.
     * <p>
     * Write out {@link DBDefinitions#TBL_BOOKS}.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doBooks(@NonNull final BufferedWriter writer,
                        @NonNull final ProgressListener listener)
            throws IOException {
        int booksExported = 0;
        writer.append('<' + XmlTags.XML_BOOK_LIST)
           .append(version(XML_EXPORTER_BOOKS_VERSION))
           .append(">\n");

        try (BookCursor bookCursor = mDb.fetchBooksForExport(mExportHelper.getDateFrom())) {
            while (bookCursor.moveToNext()) {
                writer.append('<' + XmlTags.XML_BOOK)
                      .append(id(bookCursor.getLong(DBDefinitions.KEY_PK_ID)))
                      .append(attr(DBDefinitions.KEY_TITLE,
                                bookCursor.getString(DBDefinitions.KEY_TITLE)))
                      .append(attr(DBDefinitions.KEY_ISBN,
                                bookCursor.getString(DBDefinitions.KEY_ISBN)))
                      .append(attr(DBDefinitions.KEY_BOOK_UUID,
                                bookCursor.getString(DBDefinitions.KEY_BOOK_UUID)))
                      .append(attr(DBDefinitions.KEY_DATE_ADDED,
                                bookCursor.getString(DBDefinitions.KEY_DATE_ADDED)))
                      .append(attr(DBDefinitions.KEY_DATE_LAST_UPDATED,
                                bookCursor.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)))
                      .append(attr(DBDefinitions.KEY_READ,
                                bookCursor.getBoolean(DBDefinitions.KEY_READ)))
                      .append(attr(DBDefinitions.KEY_READ_START,
                                bookCursor.getString(DBDefinitions.KEY_READ_START)))
                      .append(attr(DBDefinitions.KEY_READ_END,
                                bookCursor.getString(DBDefinitions.KEY_READ_END)))

                      .append(attr(DBDefinitions.KEY_PUBLISHER,
                                bookCursor.getString(DBDefinitions.KEY_PUBLISHER)))
                      .append(attr(DBDefinitions.KEY_DATE_PUBLISHED,
                                bookCursor.getString(DBDefinitions.KEY_DATE_PUBLISHED)))
                      .append(attr(DBDefinitions.KEY_PRICE_LISTED,
                                bookCursor.getString(DBDefinitions.KEY_PRICE_LISTED)))
                      .append(attr(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                bookCursor.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)))
                      .append(attr(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                bookCursor.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)))
                      .append(attr(DBDefinitions.KEY_FORMAT,
                                bookCursor.getString(DBDefinitions.KEY_FORMAT)))
                      .append(attr(DBDefinitions.KEY_PAGES,
                                bookCursor.getString(DBDefinitions.KEY_PAGES)))
                      .append(attr(DBDefinitions.KEY_GENRE,
                                bookCursor.getString(DBDefinitions.KEY_GENRE)))
                      .append(attr(DBDefinitions.KEY_LANGUAGE,
                                bookCursor.getString(DBDefinitions.KEY_LANGUAGE)))
                      .append(attr(DBDefinitions.KEY_TOC_BITMASK,
                                bookCursor.getLong(DBDefinitions.KEY_TOC_BITMASK)))

                      .append(attr(DBDefinitions.KEY_PRICE_PAID,
                                bookCursor.getString(DBDefinitions.KEY_PRICE_PAID)))
                      .append(attr(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                                bookCursor.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)))
                      .append(attr(DBDefinitions.KEY_DATE_ACQUIRED,
                                bookCursor.getString(DBDefinitions.KEY_DATE_ACQUIRED)))
                      .append(attr(DBDefinitions.KEY_LOCATION,
                                bookCursor.getString(DBDefinitions.KEY_LOCATION)))
                      .append(attr(DBDefinitions.KEY_RATING,
                                bookCursor.getDouble(DBDefinitions.KEY_RATING)))
                      .append(attr(DBDefinitions.KEY_SIGNED,
                                bookCursor.getBoolean(DBDefinitions.KEY_SIGNED)))
                      .append(attr(DBDefinitions.KEY_EDITION_BITMASK,
                                bookCursor.getLong(DBDefinitions.KEY_EDITION_BITMASK)))

                      // external ID's
                      .append(attr(DBDefinitions.KEY_LIBRARY_THING_ID,
                                bookCursor.getLong(DBDefinitions.KEY_LIBRARY_THING_ID)))
                      .append(attr(DBDefinitions.KEY_OPEN_LIBRARY_ID,
                                bookCursor.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID)))
                      .append(attr(DBDefinitions.KEY_ISFDB_ID,
                                bookCursor.getLong(DBDefinitions.KEY_ISFDB_ID)))
                      .append(attr(DBDefinitions.KEY_GOODREADS_BOOK_ID,
                                bookCursor.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID)))
                      .append(attr(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE,
                                bookCursor.getString(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE)))

                      // cross-linked with the loanee table
                      .append(attr(DBDefinitions.KEY_LOANEE,
                                bookCursor.getString(DBDefinitions.KEY_LOANEE)))

                      // close the tag
                      .append(">\n")

                      // last are the text field tags
                      .append(tagWithCData(DBDefinitions.KEY_DESCRIPTION, null,
                                        bookCursor.getString(DBDefinitions.KEY_DESCRIPTION)))
                      .append(tagWithCData(DBDefinitions.KEY_NOTES, null,
                                        bookCursor.getString(DBDefinitions.KEY_NOTES)))

                      .append("</" + XmlTags.XML_BOOK + ">\n");
                booksExported++;
            }
        }
        writer.append("</" + XmlTags.XML_BOOK_LIST + ">\n");
        return booksExported;
    }

    /**
     * Write out the user-defined styles using custom tags.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    private int doStyles2(@NonNull final BufferedWriter writer,
                          @NonNull final ProgressListener listener)
            throws IOException {
        Collection<BooklistStyle> styles = BooklistStyle.Helper.getUserStyles(mDb).values();
        if (styles.isEmpty()) {
            return 0;
        }

        writer.append('<' + XmlTags.XML_STYLE_LIST)
              .append(version(XML_EXPORTER_STYLES_VERSION_v2))
              .append(">\n");

        for (BooklistStyle style : styles) {
            writer.append('<' + XmlTags.XML_STYLE)
                  .append(id(style.getId()))
                  .append(name(style.getUuid()))
                  .append(">\n");

            // All 'flat' Preferences for this style.
            for (PPref p : style.getPreferences(false).values()) {
                writer.append(typedTag(p.getKey(), p.get()));
            }

            // Groups with their Preferences.
            writer.append('<' + XmlTags.XML_GROUP_LIST + '>');
            for (BooklistGroup group : style.getGroups()) {
                writer.append('<' + XmlTags.XML_GROUP)
                      .append(id(group.getKind()))
                      .append(">\n");
                for (PPref p : group.getPreferences().values()) {
                    writer.append(typedTag(p.getKey(), p.get()));
                }
                writer.append("</" + XmlTags.XML_GROUP + ">\n");
            }
            writer.append("</" + XmlTags.XML_GROUP_LIST + '>');

            // Active filters with their Preferences.
            writer.append('<' + XmlTags.XML_FILTER_LIST + '>');
            for (Filter filter : style.getFilters()) {
                if (filter.isActive()) {
                    writer.append(tag(XmlTags.XML_FILTER, filter.getKey(), filter.get()));
                }
            }
            writer.append("</" + XmlTags.XML_FILTER_LIST + '>');

            // close style tag.
            writer.append("</" + XmlTags.XML_STYLE + ">\n");
        }

        writer.append("</" + XmlTags.XML_STYLE_LIST + ">\n");
        return styles.size();
    }

    /**
     * Write out the user-defined styles.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    public int doStyles(@NonNull final BufferedWriter writer,
                        @NonNull final ProgressListener listener)
            throws IOException {
        Collection<BooklistStyle> styles = BooklistStyle.Helper.getUserStyles(mDb).values();
        if (!styles.isEmpty()) {
            toXml(writer, new StylesWriter(styles));
        }
        return styles.size();
    }

    /**
     * Write out the user preferences.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    public int doPreferences(@NonNull final BufferedWriter writer,
                             @NonNull final ProgressListener listener)
            throws IOException {

        Map<String, ?> all = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                              .getAll();

        // remove the acra settings
        Iterator<String> it = all.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.startsWith("acra")) {
                it.remove();
            }
        }
        toXml(writer, new PreferencesWriter(all, null));
        return 1;
    }

    /**
     * Write out the standard archive info block.
     *
     * @param writer      writer
     * @param listener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    public void doBackupInfoBlock(@NonNull final BufferedWriter writer,
                                  @NonNull final ProgressListener listener,
                                  @NonNull final BackupInfo info)
            throws IOException {
        toXml(writer, new InfoWriter(info));
    }

    /**
     * Internal routine to send the passed {@link EntityWriter} data to an XML file.
     *
     * @param writer      where to send the XML to
     * @param accessor which provides the input
     */
    private void toXml(@NonNull final BufferedWriter writer,
                       @NonNull final EntityWriter<String> accessor)
            throws IOException {

        String listRoot = accessor.getListRoot();
        writer.append('<').append(listRoot)
              .append(size(accessor.size()))
              .append(">\n");

        // loop through all elements
        do {
            // IMPORTANT: get the keys for each iteration, as they might be different
            // from element to element.
            String[] keys = accessor.elementKeySet().toArray(new String[]{});
            // sure, not needed, but if you want to eyeball the resulting file...
            Arrays.sort(keys);

            // start with an element, optionally add a name attribute
            String nameAttr = accessor.getElementNameAttribute();
            writer.append('<')
                  .append(accessor.getElementRoot())
                  .append(version(accessor.getElementVersionAttribute()));
            if (nameAttr != null) {
                writer.append(name(nameAttr));
            }
            writer.append(" >\n");

            // loop through all keys of the element
            for (String name : keys) {
                writer.append(typedTag(name, accessor.get(name)));
            }
            // end of element.
            writer.append("</").append(accessor.getElementRoot()).append(">\n");

        } while (accessor.hasMore());

        // close the list.
        writer.append("</").append(listRoot).append(">\n");
    }


    @Override
    public void close() {
        mDb.close();
    }

    /**
     * Provide translator between Collections, and XML tags/attributes.
     *
     * @param <K> Type of the collection key
     */
    interface EntityWriter<K> {

        /**
         * Get the top-root list element name (even if the list only contains one element).
         *
         * @return list root
         */
        @NonNull
        String getListRoot();

        /**
         * Get the size of the list.
         *
         * @return size
         */
        int size();

        /**
         * When we do not have a list, this method should return 'false'.
         * <ul>When there is a list, then:
         * <li>the first element should be set to the 'current'</li>
         * <li>a loop should be (is) implemented with:</li>
         * </ul>
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
         * Get the root element for each item in a list.
         *
         * @return root
         */
        @NonNull
        String getElementRoot();

        /**
         * Get the name attribute to be set on the {@link #getElementRoot()}.
         *
         * @return name, or {@code null}
         */
        @Nullable
        String getElementNameAttribute();

        /**
         * The version attribute to be set on the {@link #getElementRoot()}.
         * We set it on the element, not on the list, as it describes the format of one element.
         *
         * @return version
         */
        long getElementVersionAttribute();

        /**
         * Get the collection of keys for an element.
         * Implementations should return the keys for the <strong>current</strong> element.
         * i.e. the XML writer assumes that each element can have a different key set.
         *
         * @return keys
         */
        Set<K> elementKeySet();

        /**
         * Get the object for the specified key of the current element.
         *
         * @return object
         */
        @NonNull
        Object get(@NonNull K key);
    }

    /**
     * Supports a single INFO block.
     */
    static class InfoWriter
            implements EntityWriter<String> {

        /** The data we'll be writing. */
        private final BackupInfo mInfo;

        @NonNull
        private final Bundle mBundle;

        /**
         * Constructor.
         *
         * @param info block to write.
         */
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
        public String getElementNameAttribute() {
            return null;
        }

        @Override
        public long getElementVersionAttribute() {
            return mInfo.getArchVersion();
        }

        @Override
        @NonNull
        public Set<String> elementKeySet() {
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
         * Constructor.
         *
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
        public String getElementNameAttribute() {
            return mName;
        }

        @Override
        public long getElementVersionAttribute() {
            //noinspection ConstantConditions
            return (Long) mMap.get(StartupActivity.PREF_STARTUP_LAST_VERSION);
        }

        @NonNull
        @Override
        public Set<String> elementKeySet() {
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

        private BooklistStyle currentStyle;
        /** the Preferences from the current style and the groups that have PPrefs. */
        private Map<String, PPref> currentStylePPrefs;

        /**
         * Constructor.
         *
         * @param styles list of styles to write
         */
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
                currentStyle = it.next();
                currentStylePPrefs = currentStyle.getPreferences(true);
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
        public String getElementNameAttribute() {
            return currentStyle.getUuid();
        }

        @Override
        public long getElementVersionAttribute() {
            return XmlExporter.XML_EXPORTER_STYLES_VERSION;
        }

        @Override
        @NonNull
        public Set<String> elementKeySet() {
            return currentStylePPrefs.keySet();
        }

        @NonNull
        @Override
        public Object get(@NonNull final String key) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Logger.debug(this, "get", "uuid=" + currentStyle.getUuid() + "|name=" + key);
            }
            //noinspection ConstantConditions
            return currentStylePPrefs.get(key).get();
        }
    }
}
