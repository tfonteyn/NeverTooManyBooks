package com.eleybourn.bookcatalogue.backup.xml;

import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.filters.BooleanFilter;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANEE;
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

    @NonNull
    private final DBA mDb;
    @NonNull
    private final ExportSettings mSettings;

    public XmlExporter() {
        mDb = new DBA(BookCatalogueApp.getAppContext());
        mSettings = new ExportSettings();
        mSettings.what = ExportSettings.ALL;
    }

    /**
     * Constructor.
     *
     * @param settings exporting books respects {@link ExportSettings#dateFrom}
     *                 All other flags are ignored.
     */
    public XmlExporter(@NonNull final ExportSettings settings) {
        mDb = new DBA(BookCatalogueApp.getAppContext());
        settings.validate();
        mSettings = settings;
    }

    /**
     * @param outputStream Stream for writing data
     * @param listener     Progress and cancellation interface
     *
     * @return items written
     *
     * @throws IOException on failure
     */
    @Override
    public int doExport(@NonNull final OutputStream outputStream,
                        @NonNull final ExportListener listener)
            throws IOException {

        int pos = 0;
        listener.setMax(5);

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream,
                                                                            StandardCharsets.UTF_8),
                                                     XmlUtils.BUFFER_SIZE)) {

            out.append('<' + XmlUtils.XML_ROOT)
               .append(XmlUtils.version(XML_EXPORTER_VERSION))
               .append(">\n");

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResString(R.string.lbl_bookshelves),
                                    pos++);
                pos += doBookshelves(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResString(R.string.lbl_author), pos++);
                pos += doAuthors(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResString(R.string.lbl_series), pos++);
                pos += doSeries(out, listener);
            }

            if (!listener.isCancelled()) {
                listener.onProgress(BookCatalogueApp.getResString(R.string.lbl_book), pos++);
                pos += doBooks(out, listener);
            }

            if (!listener.isCancelled()
                    && (mSettings.what & ExportSettings.BOOK_LIST_STYLES) != 0) {
                listener.onProgress(BookCatalogueApp.getResString(R.string.lbl_styles), pos++);
                pos += doStyles(out, listener);
            }

            if (!listener.isCancelled()
                    && (mSettings.what & ExportSettings.PREFERENCES) != 0) {
                listener.onProgress(BookCatalogueApp.getResString(R.string.lbl_preferences),
                                    pos++);
                pos += doPreferences(out, listener);
            }

            out.append("</" + XmlUtils.XML_ROOT + ">\n");
        }
        return pos;
    }

    /**
     * Write out {@link com.eleybourn.bookcatalogue.database.DatabaseDefinitions#TBL_BOOKSHELF}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doBookshelves(@NonNull final BufferedWriter out,
                              @NonNull final ExportListener listener)
            throws IOException {
        int count = 0;
        List<Bookshelf> list = mDb.getBookshelves();
        out.append('<' + XmlUtils.XML_BOOKSHELF_LIST)
           .append(XmlUtils.version(XML_EXPORTER_BOOKSHELVES_VERSION))
           .append(">\n");

        for (Bookshelf bookshelf : list) {
            out.append('<' + XmlUtils.XML_BOOKSHELF)
               .append(XmlUtils.id(bookshelf.id))
               .append(XmlUtils.attr(DOM_BOOKSHELF.name, bookshelf.name))
               .append("/>\n");
            count++;
        }
        out.append("</" + XmlUtils.XML_BOOKSHELF_LIST + ">\n");
        return count;
    }

    /**
     * Write out {@link com.eleybourn.bookcatalogue.database.DatabaseDefinitions#TBL_AUTHORS}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doAuthors(@NonNull final BufferedWriter out,
                          @NonNull final ExportListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlUtils.XML_AUTHOR_LIST)
           .append(XmlUtils.version(XML_EXPORTER_AUTHORS_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchAuthors()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);

            while (cursor.moveToNext()) {
                out.append('<' + XmlUtils.XML_AUTHOR)
                   .append(XmlUtils.id(mapper.getLong(DOM_PK_ID)))
                   .append(XmlUtils.attr(DOM_AUTHOR_FAMILY_NAME.name,
                                         mapper.getString(DOM_AUTHOR_FAMILY_NAME)))
                   .append(XmlUtils.attr(DOM_AUTHOR_GIVEN_NAMES.name,
                                         mapper.getString(DOM_AUTHOR_GIVEN_NAMES)))
                   .append(XmlUtils.attr(DOM_AUTHOR_IS_COMPLETE.name,
                                         mapper.getInt(DOM_AUTHOR_IS_COMPLETE)))
                   .append("/>\n");
                count++;
            }
        }
        out.append("</" + XmlUtils.XML_AUTHOR_LIST + ">\n");
        return count;
    }

    /**
     * Write out {@link com.eleybourn.bookcatalogue.database.DatabaseDefinitions#TBL_SERIES}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doSeries(@NonNull final BufferedWriter out,
                         @NonNull final ExportListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlUtils.XML_SERIES_LIST)
           .append(XmlUtils.version(XML_EXPORTER_SERIES_VERSION))
           .append(">\n");

        try (Cursor cursor = mDb.fetchSeries()) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES);
            while (cursor.moveToNext()) {
                out.append('<' + XmlUtils.XML_SERIES)
                   .append(XmlUtils.id(mapper.getLong(DOM_PK_ID)))
                   .append(XmlUtils.attr(DOM_SERIES_NAME.name,
                                         mapper.getString(DOM_SERIES_NAME)))
                   .append(XmlUtils.attr(DOM_SERIES_IS_COMPLETE.name,
                                         mapper.getInt(DOM_SERIES_IS_COMPLETE)))
                   .append("/>\n");
            }
        }
        out.append("</" + XmlUtils.XML_SERIES_LIST + ">\n");
        return count;
    }

    /**
     * 'loan_to' is included here, might still change.
     *
     * Write out {@link com.eleybourn.bookcatalogue.database.DatabaseDefinitions#TBL_BOOKS}.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @return number of items written
     *
     * @throws IOException on failure
     */
    private int doBooks(@NonNull final BufferedWriter out,
                        @NonNull final ExportListener listener)
            throws IOException {
        int count = 0;
        out.append('<' + XmlUtils.XML_BOOK_LIST)
           .append(XmlUtils.version(XML_EXPORTER_BOOKS_VERSION))
           .append(">\n");

        try (BookCursor bookCursor = mDb.fetchFlattenedBooks(mSettings.dateFrom)) {
            BookRowView bookCursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                // basic ID
                out.append('<' + XmlUtils.XML_BOOK)
                   .append(XmlUtils.id(bookCursorRow.getId()))
                   .append(XmlUtils.attr(DOM_TITLE.name,
                                         bookCursorRow.getTitle()))
                   .append(XmlUtils.attr(DOM_BOOK_ISBN.name,
                                         bookCursorRow.getIsbn()))
                   .append("\n")

                   // publishing information
                   .append(XmlUtils.attr(DOM_BOOK_PUBLISHER.name,
                                         bookCursorRow.getPublisherName()))
                   .append(XmlUtils.attr(DOM_BOOK_DATE_PUBLISHED.name,
                                         bookCursorRow.getDatePublished()))
                   .append(XmlUtils.attr(DOM_FIRST_PUBLICATION.name,
                                         bookCursorRow.getFirstPublication()))
                   .append("\n")
                   .append(XmlUtils.attr(DOM_BOOK_FORMAT.name,
                                         bookCursorRow.getFormat()))
                   .append(XmlUtils.attr(DOM_BOOK_PAGES.name,
                                         bookCursorRow.getPages()))
                   .append(XmlUtils.attr(DOM_BOOK_GENRE.name,
                                         bookCursorRow.getGenre()))
                   .append(XmlUtils.attr(DOM_BOOK_LANGUAGE.name,
                                         bookCursorRow.getLanguageCode()))
                   .append(XmlUtils.attr(DOM_BOOK_ANTHOLOGY_BITMASK.name,
                                         bookCursorRow.getAnthologyBitMask()))
                   .append("\n")

                   // reading facts
                   .append(XmlUtils.attr(DOM_BOOK_READ.name,
                                         bookCursorRow.getRead()))
                   .append(XmlUtils.attr(DOM_BOOK_READ_START.name,
                                         bookCursorRow.getReadStart()))
                   .append(XmlUtils.attr(DOM_BOOK_READ_END.name,
                                         bookCursorRow.getReadEnd()))
                   .append("\n")

                   // price information
                   .append(XmlUtils.attr(DOM_BOOK_PRICE_LISTED.name,
                                         bookCursorRow.getListPrice()))
                   .append(XmlUtils.attr(DOM_BOOK_PRICE_LISTED_CURRENCY.name,
                                         bookCursorRow.getListPriceCurrency()))
                   .append(XmlUtils.attr(DOM_BOOK_PRICE_PAID.name,
                                         bookCursorRow.getPricePaid()))
                   .append(XmlUtils.attr(DOM_BOOK_PRICE_PAID_CURRENCY.name,
                                         bookCursorRow.getPricePaidCurrency()))
                   .append(XmlUtils.attr(DOM_BOOK_DATE_ACQUIRED.name,
                                         bookCursorRow.getDateAcquired()))
                   .append("\n")


                   .append(XmlUtils.attr(DOM_BOOK_LOCATION.name,
                                         bookCursorRow.getLocation()))
                   .append(XmlUtils.attr(DOM_BOOK_RATING.name,
                                         bookCursorRow.getRating()))
                   .append(XmlUtils.attr(DOM_BOOK_SIGNED.name,
                                         bookCursorRow.getSigned()))
                   .append(XmlUtils.attr(DOM_BOOK_EDITION_BITMASK.name,
                                         bookCursorRow.getEditionBitMask()))
                   .append("\n")

                   // external id's
                   .append(XmlUtils.attr(DOM_BOOK_LIBRARY_THING_ID.name,
                                         bookCursorRow.getLibraryThingBookId()))
                   .append(XmlUtils.attr(DOM_BOOK_ISFDB_ID.name,
                                         bookCursorRow.getISFDBBookId()))
                   .append(XmlUtils.attr(DOM_BOOK_GOODREADS_BOOK_ID.name,
                                         bookCursorRow.getGoodreadsBookId()))
                   .append(XmlUtils.attr(DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name,
                                         bookCursorRow.getDateLastSyncedWithGoodreads()))
                   .append("\n")

                   .append(XmlUtils.attr(DOM_BOOK_DATE_ADDED.name,
                                         bookCursorRow.getDateAdded()))
                   .append(XmlUtils.attr(DOM_LAST_UPDATE_DATE.name,
                                         bookCursorRow.getDateLastUpdated()))
                   .append(XmlUtils.attr(DOM_BOOK_UUID.name,
                                         bookCursorRow.getBookUuid()))
                   .append("\n")

                   .append(XmlUtils.attr(DOM_LOANEE.name,
                                         bookCursorRow.getLoanedTo()))
                   .append(">\n");

                out.append(XmlUtils.tagWithCData(DOM_BOOK_DESCRIPTION.name, null,
                                                 bookCursorRow.getDescription()));
                out.append(XmlUtils.tagWithCData(DOM_BOOK_NOTES.name, null,
                                                 bookCursorRow.getNotes()));

                out.append("</" + XmlUtils.XML_BOOK + ">\n");
                count++;
            }
        }
        out.append("</" + XmlUtils.XML_BOOK_LIST + ">\n");
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
                         @NonNull final ExportListener listener)
            throws IOException {
        Collection<BooklistStyle> styles = BooklistStyles.getUserStyles(mDb).values();
        if (styles.isEmpty()) {
            return 0;
        }

        out.append('<' + XmlUtils.XML_STYLE_LIST)
           .append(XmlUtils.version(XML_EXPORTER_STYLES_VERSION))
           .append(">\n");

        for (BooklistStyle style : styles) {
            out.append('<' + XmlUtils.XML_STYLE)
               .append(XmlUtils.id(style.getId()))
               .append(XmlUtils.name(style.getUuid()))
               .append(">\n");

            // 'Flat' Preferences for this style.
            for (PPref p : style.getPreferences(false).values()) {
                out.append(XmlUtils.typedTag(p.getKey(), p.get()));
            }

            // Groups with their Preferences
            out.append('<' + XmlUtils.XML_GROUP_LIST + '>');
            for (BooklistGroup group : style.getGroups()) {
                out.append('<' + XmlUtils.XML_GROUP)
                   .append(XmlUtils.id(group.getKind()))
                   .append(">\n");
                for (PPref p : group.getPreferences().values()) {
                    out.append(XmlUtils.typedTag(p.getKey(), p.get()));
                }
                out.append("</" + XmlUtils.XML_GROUP + ">\n");
            }
            out.append("</" + XmlUtils.XML_GROUP_LIST + '>');

            // Filters with their Preferences
            out.append('<' + XmlUtils.XML_FILTER_LIST + '>');
            for (BooleanFilter filter : style.getFilters().values()) {
                if (filter.isActive()) {
                    out.append(XmlUtils.tag(XmlUtils.XML_FILTER, filter.getKey(), filter.get()));
                }
            }
            out.append("</" + XmlUtils.XML_FILTER_LIST + '>');

            out.append("</" + XmlUtils.XML_STYLE + ">\n");
        }
        out.append("</" + XmlUtils.XML_STYLE_LIST + ">\n");
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
                        @NonNull final ExportListener listener)
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

    /**
     * Write out the standard archive info block.
     *
     * @param out      writer
     * @param listener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
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
           .append(XmlUtils.size(accessor.size()))
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
                out.append(XmlUtils.name(nameAttr));
            }
            out.append(XmlUtils.version(accessor.getVersionAttribute()))
               .append(" >\n");

            // loop through all keys of the element
            for (String name : keys) {
                out.append(XmlUtils.typedTag(name, accessor.get(name)));
            }
            out.append("</").append(accessor.getElementRoot()).append(">\n");
        } while (accessor.hasMore());

        // close the list.
        out.append("</").append(listRoot).append(">\n");
    }


    @Override
    public void close()
            throws IOException {
        if (mDb != null) {
            mDb.close();
        }
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
            return XmlUtils.XML_STYLE_LIST;
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
            return XmlUtils.XML_STYLE;
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
            if (DEBUG_SWITCHES.XML && BuildConfig.DEBUG) {
                Logger.info(this, "uuid=" + current.getUuid() + "|name=" + key);
            }
            return currentPPrefs.get(key).get();
        }
    }

}
