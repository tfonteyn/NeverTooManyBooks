package com.eleybourn.bookcatalogue.backup.xml;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Base64;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * There are two types of XML here.
 *
 * Type based, where the tag name is the type. Used by:
 * {@link BackupInfo}
 * {@link android.content.SharedPreferences}
 * {@link BooklistStyle}
 * Reason:
 * - more or less flat objects (Bundle or Bundle-like)
 * - can be generically written (and read), so future adding/remove entries requires no changes here
 * - really only useful to the application itself.
 *
 * Database column name based. Used by:
 * {@link Bookshelf}
 * {@link Author}
 * {@link Series}
 * {@link Book}
 * Reason:
 * - EXPERIMENTAL (NO import facility; format can/will change)
 * - meant for loading on a computer to create reports or whatever...
 * - not bound to the application itself.
 *
 */
public class XmlExporter
    implements Exporter, Closeable {

    // uber-version of the exporter (not necessarily the same as the archive container !)
    public static final int XML_EXPORTER_VERSION = 2;

    // individual format versions of table based data
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;
    private static final int XML_EXPORTER_BOOKS_VERSION = 1;

    // individual format version of Styles
    private static final int XML_EXPORTER_STYLES_VERSION = 1;

    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final ExportSettings mSettings;

    public XmlExporter() {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mSettings = new ExportSettings();
        mSettings.what = ExportSettings.ALL;
    }

    /**
     * Constructor
     *
     * @param settings exporting books respects {@link ExportSettings#dateFrom}
     *                 All other flags are ignored.
     */
    public XmlExporter(@NonNull final ExportSettings settings) {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        settings.validate();
        mSettings = settings;
    }


    @Override
    public int doExport(@NonNull final OutputStream outputStream,
                        @NonNull final ExportListener listener)
        throws IOException {

        int pos = 0;
        listener.setMax(5);

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream,
                                                                            XmlUtils.UTF8),
                                                     XmlUtils.BUFFER_SIZE)) {

            out.append('<' + XmlUtils.XML_ROOT)
               .append(version(XML_EXPORTER_VERSION))
               .append(">\n");

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_bookshelves),
                                    pos++);
                pos += doBookshelves(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_author), pos++);
                pos += doAuthors(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_series), pos++);
                pos += doSeries(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_book), pos++);
                pos += doBooks(out, listener);
            }

            if (!listener.isCancelled() && (mSettings.what & ExportSettings.BOOK_LIST_STYLES) != 0) {
                listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_styles), pos++);
                pos += doStyles(out, listener);
            }

            if (!listener.isCancelled() && (mSettings.what & ExportSettings.PREFERENCES) != 0) {
                listener.onProgress(BookCatalogueApp.getResourceString(R.string.lbl_preferences),
                                    pos++);
                pos += doPreferences(out, listener);
            }

            out.append("</" + XmlUtils.XML_ROOT + ">\n");
        }
        return pos;
    }

    private int doBookshelves(@NonNull final BufferedWriter out,
                              @NonNull final ExportListener listener)
        throws IOException {
        int count = 0;
        List<Bookshelf> list = mDb.getBookshelves();
        out.append('<' + XmlUtils.XML_BOOKSHELF_LIST)
           .append(version(XML_EXPORTER_BOOKSHELVES_VERSION))
           .append(">\n");

        for (Bookshelf bookshelf : list) {
            out.append('<' + XmlUtils.XML_BOOKSHELF)
               .append(id(bookshelf.id))
               .append(attr(DOM_BOOKSHELF.name, bookshelf.name))
               .append("/>\n");
            count++;
        }
        out.append("</" + XmlUtils.XML_BOOKSHELF_LIST + ">\n");
        return count;
    }

    private int doAuthors(@NonNull final BufferedWriter out,
                          @NonNull final ExportListener listener)
        throws IOException {
        int count = 0;
        out.append('<' + XmlUtils.XML_AUTHOR_LIST)
           .append(version(XML_EXPORTER_AUTHORS_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchAuthors()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);

            while (cursor.moveToNext()) {
                out.append('<' + XmlUtils.XML_AUTHOR)
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
        out.append("</" + XmlUtils.XML_AUTHOR_LIST + ">\n");
        return count;
    }

    private int doSeries(@NonNull final BufferedWriter out,
                         @NonNull final ExportListener listener)
        throws IOException {
        int count = 0;
        out.append('<' + XmlUtils.XML_SERIES_LIST)
           .append(version(XML_EXPORTER_SERIES_VERSION))
           .append(">\n");
        try (Cursor cursor = mDb.fetchSeries()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES);
            while (cursor.moveToNext()) {
                out.append('<' + XmlUtils.XML_SERIES)
                   .append(id(mapper.getLong(DOM_PK_ID)))
                   .append(attr(DOM_SERIES_NAME.name,
                                mapper.getString(DOM_SERIES_NAME)))
                   .append(attr(DOM_SERIES_IS_COMPLETE.name,
                                mapper.getInt(DOM_SERIES_IS_COMPLETE)))
                   .append("/>\n");
            }
        }
        out.append("</" + XmlUtils.XML_SERIES_LIST + ">\n");
        return count;
    }

    /**
     * 'loan_to' is included here, might still change
     */
    private int doBooks(@NonNull final BufferedWriter out,
                        @NonNull final ExportListener listener)
        throws IOException {
        int count = 0;
        out.append('<' + XmlUtils.XML_BOOK_LIST).append(version(XML_EXPORTER_BOOKS_VERSION)).append(
            ">\n");
        try (BookCursor bookCursor = mDb.fetchFlattenedBooks(mSettings.dateFrom)) {
            BookRowView bookCursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                out.append('<' + XmlUtils.XML_BOOK)
                   .append(attr(DOM_PK_ID.name, bookCursorRow.getId()))
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
                   .append(attr(DOM_BOOK_PRICE_LISTED_CURRENCY.name,
                                bookCursorRow.getListPriceCurrency()))
                   .append(attr(DOM_BOOK_PRICE_PAID.name, bookCursorRow.getPricePaid()))
                   .append(attr(DOM_BOOK_PRICE_PAID_CURRENCY.name,
                                bookCursorRow.getPricePaidCurrency()))
                   .append(attr(DOM_BOOK_DATE_ACQUIRED.name, bookCursorRow.getDateAcquired()))
                   .append("\n")
                   .append(attr(DOM_BOOK_ANTHOLOGY_BITMASK.name,
                                bookCursorRow.getAnthologyBitMask()))
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
                   .append(attr(DOM_BOOK_LIBRARY_THING_ID.name,
                                bookCursorRow.getLibraryThingBookId()))
                   .append(attr(DOM_BOOK_ISFDB_ID.name,
                                bookCursorRow.getISFDBBookId()))
                   .append(attr(DOM_BOOK_GOODREADS_BOOK_ID.name,
                                bookCursorRow.getGoodreadsBookId()))
                   .append(attr(DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name,
                                bookCursorRow.getDateLastSyncedWithGoodreads()))
                   .append("\n")
                   .append(attr(DOM_LAST_UPDATE_DATE.name,
                                bookCursorRow.getDateLastUpdated()))
                   .append(attr(DOM_BOOK_UUID.name,
                                bookCursorRow.getBookUuid()))
                   .append("\n")
                   .append(attr(DOM_LOANED_TO.name, bookCursorRow.getLoanedTo()))
                   .append("/>\n");

                count++;
            }
        }
        out.append("</" + XmlUtils.XML_BOOK_LIST + ">\n");
        return count;
    }

    public int doStyles2(@NonNull final BufferedWriter out,
                         @NonNull final ExportListener listener)
        throws IOException {
        Collection<BooklistStyle> styles = mDb.getBooklistStyles().values();
        if (styles.isEmpty()) {
            return 0;
        }
        out.append('<' + XmlUtils.XML_STYLE_LIST).append(
            version(XML_EXPORTER_STYLES_VERSION)).append(">\n");
        for (BooklistStyle style : styles) {
            out.append('<' + XmlUtils.XML_STYLE + ">\n");

            out.append("</" + XmlUtils.XML_STYLE + ">\n");
        }
        out.append("</" + XmlUtils.XML_STYLE_LIST + ">\n");
        return styles.size();
    }

    public int doStyles(@NonNull final BufferedWriter out,
                        @NonNull final ExportListener listener)
        throws IOException {
        Collection<BooklistStyle> styles = mDb.getBooklistStyles().values();
        if (styles.isEmpty()) {
            return 0;
        }
        toXml(out, new StylesWriter(styles));
        return styles.size();
    }

    public int doPreferences(@NonNull final BufferedWriter out,
                             @NonNull final ExportListener listener)
        throws IOException {
        // remove the acra settings
        Map<String, ?> all = Prefs.getPrefs().getAll();
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

    public void doBackupInfoBlock(@NonNull final BufferedWriter out,
                                  @NonNull final ExportListener listener,
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
            for (String key : keys) {
                Object object = accessor.get(key);
                if (object instanceof String) {
                    out.append(constructElement(XmlUtils.XML_STRING, key, object));
                } else if (object instanceof Boolean) {
                    out.append(constructElement(XmlUtils.XML_BOOLEAN, key, object));
                } else if (object instanceof Integer) {
                    out.append(constructElement(XmlUtils.XML_INT, key, object));
                } else if (object instanceof Long) {
                    out.append(constructElement(XmlUtils.XML_LONG, key, object));
                } else if (object instanceof Float) {
                    out.append(constructElement(XmlUtils.XML_FLOAT, key, object));
                } else if (object instanceof Double) {
                    out.append(constructElement(XmlUtils.XML_DOUBLE, key, object));

                } else if (object instanceof Set) {
                    out.append(
                        constructElementWithBody(XmlUtils.XML_SET, key, encodeSet((Set) object)));
                } else if (object instanceof Serializable) {
                    out.append(
                        constructElementWithBody(XmlUtils.XML_SERIALIZABLE, key,
                                                        Base64.encodeToString(
                                                            convertToBytes(object),
                                                            Base64.DEFAULT)));
                } else {
                    if (object == null) {
                        throw new NullPointerException();
                    }
                    throw new RTE.IllegalTypeException(object.getClass().getCanonicalName());
                }
            }
            out.append("</").append(accessor.getElementRoot()).append(">\n");
        } while (accessor.hasMore());

        // close the list.
        out.append("</").append(listRoot).append(">\n");
    }

    /**
     * Helper for encoding an object to Base64. The android lib wants a byte[]
     *
     * @param object to transform
     *
     * @return the array
     *
     * @throws IOException upon failure
     */
    private byte[] convertToBytes(Object object)
        throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }

    @Override
    public void close()
        throws IOException {
        if (mDb != null) {
            mDb.close();
        }
    }


    /**
     * Database row-id.
     *
     * @param id of the item in its table
     *
     * @return string representation of the attribute, with leading space.
     */
    private String id(final long id) {
        return ' ' + XmlUtils.ATTR_ID + "=\"" + id + '"';
    }

    private String version(final long version) {
        return ' ' + XmlUtils.ATTR_VERSION + "=\"" + version + '"';
    }

    /** element name attribute; i.e. the "thing" we are reading/writing. */
    private String name(@NonNull final String name) {
        return ' ' + XmlUtils.ATTR_NAME + "=\"" + name + '"';
    }

    /** the value of the individual item of the "thing". */
    private String value(@NonNull final String value) {
        return ' ' + XmlUtils.ATTR_VALUE + "=\"" + XmlUtils.encode(value) + '"';
    }

    private String size(final long size) {
        return ' ' + XmlUtils.ATTR_SIZE + "=\"" + size + '"';
    }

    private String attr(@NonNull final String attr,
                        final long value) {
        return ' ' + attr + "=\"" + value + '"';
    }

    private String attr(@NonNull final String attr,
                        final double value) {
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
    private String attr(@NonNull final String attr,
                        @NonNull final String value) {
        return ' ' + attr + "=\"" + XmlUtils.encode(value) + '"';
    }

    /**
     * Encode the given Set to xml
     * For now, only Set<String> is supported.
     *
     * @param set to encode
     *
     * @return partial xml
     */
    private String encodeSet(@NonNull final Set set) {
        StringBuilder sb = new StringBuilder("\n");
        for (Object s : set) {
            if (s instanceof String) {
                sb.append("  <" + XmlUtils.XML_STRING + '>')
                  .append(XmlUtils.encode(s.toString()))
                  .append("</" + XmlUtils.XML_STRING + ">\n");
            } else {
                throw new IllegalArgumentException();
            }
        }
        return sb.toString();
    }

    /**
     * String values are automatically encoded.
     *
     * @return tag with value attribute, empty body
     */
    private String constructElement(@NonNull final String tag,
                                    @NonNull final Object value) {
        if (value instanceof String) {
            // strings are encoded
            return '<' + tag + value(XmlUtils.encode(value.toString())) + "/>\n";
        } else {
            // non-strings as-is; for boolean this means: true,false
            return '<' + tag + value(value.toString()) + "/>\n";
        }
    }

    /**
     * String values are automatically encoded.
     *
     * @return tag with name and value attribute, empty body
     */
    private String constructElement(@NonNull final String tag,
                                    @NonNull final String name,
                                    @NonNull final Object value) {
        if (value instanceof String) {
            // strings are encoded
            return '<' + tag + name(name) + value(XmlUtils.encode(value.toString())) + "/>\n";
        } else {
            // non-strings as-is; for boolean this means: true,false
            return '<' + tag + name(name) + value(value.toString()) + "/>\n";
        }
    }

    /**
     * Any encoding of the object MUST be done before calling this method.
     *
     * @return tag with name attribute and content body
     */
    private String constructElementWithBody(@NonNull final String tag,
                                            @NonNull final String name,
                                            @NonNull final Object value) {
        return '<' + tag + name(name) + '>' + value.toString() + "</" + tag + ">\n";
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
         *
         * When there is a list, then:
         * - the first element should be set to the 'current'
         * - a loop should be (is) implemented with:
         * <code>
         * do {
         * current.get(...)
         * } while (hasMore());
         *
         * </code>
         *
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
         * Can be null.
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
        Object get(@NonNull final K key);
    }

    /**
     * Supports a single INFO block
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
            return XmlUtils.XML_INFO_LIST;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlUtils.XML_INFO;
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
            return mBundle.get(key);
        }
    }

    /**
     * Supports a single Preferences block
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
                          @Nullable final String name) {
            mMap = map;
            mName = name;
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlUtils.XML_PREFERENCES_LIST;
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
            return XmlUtils.XML_PREFERENCES;
        }

        @Nullable
        @Override
        public String getNameAttribute() {
            return mName;
        }

        @Override
        public long getVersionAttribute() {
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
            return mMap.get(key);
        }
    }

    /**
     * Supports a list of Styles.
     *
     * Filters and Groups are flattened.
     * - each filter has a tag
     * - actual groups are written as a set of id's (kinds)
     * - each preference in a group has a tag.
     */
    static class StylesWriter
        implements EntityWriter<String> {

        private final Collection<BooklistStyle> styles;
        private final Iterator<BooklistStyle> it;

        private BooklistStyle current;
        /** the PPrefs from the style and the groups that have PPrefs */
        private Map<String, PPref> currentPPrefs;

        StylesWriter(@NonNull final Collection<BooklistStyle> styles) {
            this.styles = styles;
            this.it = styles.iterator();
            // get first element ready for processing
            hasMore();
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlUtils.XML_STYLE_LIST;
        }

        @Override
        public int size() {
            return styles.size();
        }

        @Override
        public boolean hasMore() {
            if (it.hasNext()) {
                current = it.next();
                currentPPrefs = current.getPPrefs();
                return true;
            } else {
                return false;
            }
        }

        @NonNull
        @Override
        public String getElementRoot() {
            return XmlUtils.XML_STYLE;
        }

        @Nullable
        @Override
        public String getNameAttribute() {
            return current.uuid;
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
            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, "uuid=" + current.uuid + "|name=" + key);
            }
            return currentPPrefs.get(key).get(current.uuid);
        }
    }

}
