/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.XmlTags;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.options.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.options.Options;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * <strong>WARNING: EXPERIMENTAL</strong> There are two types of XML here.
 * <ul>Type based, where the tag name is the type. Used by:
 * <li>{@link ArchiveInfo}</li>
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
 *
 * <strong>Note:</strong> None of the methods here count/contribute to the ExportHelper
 * result counters.
 */
public class XmlExporter
        implements Exporter, Closeable {

    /** Log tag. */
    private static final String TAG = "XmlExporter";

    /** uber-version of the exporter (not necessarily the same as the archive container !). */
    public static final int XML_EXPORTER_VERSION = 2;

    /** individual format versions of table based data. */
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;
    private static final int XML_EXPORTER_BOOKS_VERSION = 1;

    /** individual format version of Styles. */
    private static final int XML_EXPORTER_STYLES_VERSION_1 = 1;
    private static final int XML_EXPORTER_STYLES_VERSION_2 = 2;

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** export configuration. */
    @NonNull
    private final ExportHelper mHelper;

    private final ExportResults mResults = new ExportResults();

    /**
     * Constructor.
     *
     * @param helper export configuration
     */
    public XmlExporter(@NonNull final ExportHelper helper) {
        mDb = new DAO(TAG);
        mHelper = helper;
        mHelper.validate();
    }

    /**
     * Write all desired item types to the output writer as XML.
     * <p>
     * The progressListener will not be very accurate as we advance by 1 for each step,
     * and not per item (author,book,etc) written.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        // suffix for progress messages.
        String xml = " (xml)";

        boolean incBooks = mHelper.getOption(Options.BOOKS);
        boolean incAuthors = mHelper.getOption(Options.AUTHORS);
        boolean incSeries = mHelper.getOption(Options.SERIES);
        boolean incBookshelves = mHelper.getOption(Options.BOOKSHELVES);
        boolean incPrefs = mHelper.getOption(Options.PREFERENCES);
        boolean incStyles = mHelper.getOption(Options.STYLES);

        // Write styles and prefs first.

        if (!progressListener.isCancelled() && incStyles) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_styles) + xml);
            writeStyles(context, writer);
            // an experiment, might be the v2 of the styles format
            //writeStyles2(context, writer, progressListener);
        }

        if (!progressListener.isCancelled() && incPrefs) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_settings) + xml);
            writePreferences(context, writer);
        }

        // not strictly needed, but parsing will be faster if these go in the order done here.

        if (!progressListener.isCancelled() && incBookshelves) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_bookshelves) + xml);
            writeBookshelves(writer, progressListener);
        }

        if (!progressListener.isCancelled() && incAuthors) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_author) + xml);
            writeAuthors(writer, progressListener);
        }

        if (!progressListener.isCancelled() && incSeries) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_series) + xml);
            writeSeries(writer, progressListener);
        }

        if (!progressListener.isCancelled() && incBooks) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_books) + xml);
            writeBooks(writer, progressListener);
        }

        return mResults;
    }

    /**
     * Write out the archive info block as an XML file.
     *
     * @param writer writer
     *
     * @throws IOException on failure
     */
    public void writeArchiveInfo(@NonNull final Writer writer,
                                 @NonNull final ArchiveInfo info)
            throws IOException {
        toXml(writer, new InfoWriter(info));
    }

    /**
     * Write out the user-defined styles.
     *
     * @param context Current context
     * @param writer  writer
     *
     * @throws IOException on failure
     */
    private void writeStyles(@NonNull final Context context,
                             @NonNull final Writer writer)
            throws IOException {
        Collection<BooklistStyle> styles =
                BooklistStyle.Helper.getUserStyles(context, mDb).values();
        if (!styles.isEmpty()) {
            toXml(writer, new StylesWriter(context, styles));
        }
        mResults.styles += styles.size();
    }

    /**
     * Write out the user-defined styles using custom tags.
     *
     * @param context          Current context
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeStyles2(@NonNull final Context context,
                              @NonNull final Writer writer,
                              @NonNull final ProgressListener progressListener)
            throws IOException {
        Collection<BooklistStyle> styles =
                BooklistStyle.Helper.getUserStyles(context, mDb).values();
        if (styles.isEmpty()) {
            return;
        }

        writer.write('<' + XmlTags.XML_STYLE_LIST);
        writer.write(XmlTags.version(XML_EXPORTER_STYLES_VERSION_2));
        writer.write(">\n");

        for (BooklistStyle style : styles) {
            writer.write('<' + XmlTags.XML_STYLE);
            writer.write(XmlTags.id(style.getId()));
            writer.write(XmlTags.name(style.getUuid()));
            writer.write(">\n");

            // All 'flat' Preferences for this style.
            for (PPref p : style.getPreferences(false).values()) {
                writer.write(XmlTags.typedTag(p.getKey(), p.getValue(context)));
            }

            // Groups with their Preferences.
            writer.write('<' + XmlTags.XML_GROUP_LIST + '>');
            for (BooklistGroup group : style.getGroups()) {
                writer.write('<' + XmlTags.XML_GROUP);
                writer.write(XmlTags.id(group.getId()));
                writer.write(">\n");
                for (PPref p : group.getPreferences().values()) {
                    writer.write(XmlTags.typedTag(p.getKey(), p.getValue(context)));
                }
                writer.write("</" + XmlTags.XML_GROUP + ">\n");
            }
            writer.write("</" + XmlTags.XML_GROUP_LIST + '>');

            // Active filters with their Preferences.
            writer.write('<' + XmlTags.XML_FILTER_LIST + '>');
            for (Filter filter : style.getActiveFilters(context)) {
                if (filter.isActive(context)) {
                    writer.write(XmlTags.tag(XmlTags.XML_FILTER, filter.getKey(), filter.get()));
                }
            }
            writer.write("</" + XmlTags.XML_FILTER_LIST + '>');

            // close style tag.
            writer.write("</" + XmlTags.XML_STYLE + ">\n");
        }

        writer.write("</" + XmlTags.XML_STYLE_LIST + ">\n");
        mResults.styles += styles.size();
    }

    /**
     * Write out the user preferences.
     *
     * @param context Current context
     * @param writer  writer
     *
     * @throws IOException on failure
     */
    private void writePreferences(@NonNull final Context context,
                                  @NonNull final Writer writer)
            throws IOException {

        Map<String, ?> all = PreferenceManager.getDefaultSharedPreferences(context).getAll();

        // remove the acra settings
        Iterator<String> it = all.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.startsWith("acra")) {
                it.remove();
            }
        }
        toXml(writer, new PreferencesWriter(context, all, null));
    }

    /**
     * Write out {@link DBDefinitions#TBL_BOOKSHELF}.
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeBookshelves(@NonNull final Writer writer,
                                  @NonNull final ProgressListener progressListener)
            throws IOException {
        List<Bookshelf> list = mDb.getBookshelves();
        writer.write('<' + XmlTags.XML_BOOKSHELF_LIST);
        writer.write(XmlTags.version(XML_EXPORTER_BOOKSHELVES_VERSION));
        writer.write(">\n");

        for (Bookshelf bookshelf : list) {
            writer.write('<' + XmlTags.XML_BOOKSHELF);
            writer.write(XmlTags.id(bookshelf.getId()));
            writer.write(XmlTags.attr(DBDefinitions.KEY_BOOKSHELF, bookshelf.getName()));
            writer.write(XmlTags.attr(DBDefinitions.KEY_FK_STYLE, bookshelf.getStyleUuid()));
            writer.write("/>\n");
        }
        writer.write("</" + XmlTags.XML_BOOKSHELF_LIST + ">\n");
    }

    /**
     * Write out {@link DBDefinitions#TBL_AUTHORS}.
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeAuthors(@NonNull final Writer writer,
                              @NonNull final ProgressListener progressListener)
            throws IOException {
        writer.write('<' + XmlTags.XML_AUTHOR_LIST);
        writer.write(XmlTags.version(XML_EXPORTER_AUTHORS_VERSION));
        writer.write(">\n");

        try (Cursor cursor = mDb.fetchAuthors()) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                writer.write('<' + XmlTags.XML_AUTHOR);
                writer.write(XmlTags.id(rowData.getLong(DBDefinitions.KEY_PK_ID)));

                writer.write(XmlTags.attr(DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                                          rowData.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES,
                                          rowData.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                          rowData.getBoolean(
                                                  DBDefinitions.KEY_AUTHOR_IS_COMPLETE)));
                writer.write("/>\n");
            }
        }
        writer.write("</" + XmlTags.XML_AUTHOR_LIST + ">\n");
    }

    /**
     * Write out {@link DBDefinitions#TBL_SERIES}.
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeSeries(@NonNull final Writer writer,
                             @NonNull final ProgressListener progressListener)
            throws IOException {
        writer.write('<' + XmlTags.XML_SERIES_LIST);
        writer.write(XmlTags.version(XML_EXPORTER_SERIES_VERSION));
        writer.write(">\n");

        try (Cursor cursor = mDb.fetchSeries()) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                writer.write('<' + XmlTags.XML_SERIES);
                writer.write(XmlTags.id(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_SERIES_TITLE,
                                          rowData.getString(DBDefinitions.KEY_SERIES_TITLE)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                          rowData.getBoolean(
                                                  DBDefinitions.KEY_SERIES_IS_COMPLETE)));
                writer.write("/>\n");
            }
        }
        writer.write("</" + XmlTags.XML_SERIES_LIST + ">\n");
    }

    /**
     * 'loan_to' is added to the books section here, this might be removed!
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeBooks(@NonNull final Writer writer,
                            @NonNull final ProgressListener progressListener)
            throws IOException {
        writer.write('<' + XmlTags.XML_BOOK_LIST);
        writer.write(XmlTags.version(XML_EXPORTER_BOOKS_VERSION));
        writer.write(">\n");

        try (Cursor cursor = mDb.fetchBooksForExport(mHelper.getDateFrom())) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                writer.write('<' + XmlTags.XML_BOOK);
                writer.write(XmlTags.id(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_TITLE,
                                          rowData.getString(DBDefinitions.KEY_TITLE)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_ISBN,
                                          rowData.getString(DBDefinitions.KEY_ISBN)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_BOOK_UUID,
                                          rowData.getString(DBDefinitions.KEY_BOOK_UUID)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_DATE_ADDED,
                                          rowData.getString(DBDefinitions.KEY_DATE_ADDED)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_DATE_LAST_UPDATED,
                                          rowData.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_READ,
                                          rowData.getBoolean(DBDefinitions.KEY_READ)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_READ_START,
                                          rowData.getString(DBDefinitions.KEY_READ_START)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_READ_END,
                                          rowData.getString(DBDefinitions.KEY_READ_END)));

                writer.write(XmlTags.attr(DBDefinitions.KEY_PUBLISHER,
                                          rowData.getString(DBDefinitions.KEY_PUBLISHER)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_PRINT_RUN,
                                          rowData.getString(DBDefinitions.KEY_PRINT_RUN)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_DATE_PUBLISHED,
                                          rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_PRICE_LISTED,
                                          rowData.getDouble(DBDefinitions.KEY_PRICE_LISTED)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                          rowData.getString(
                                                  DBDefinitions.KEY_PRICE_LISTED_CURRENCY)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                          rowData.getString(
                                                  DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_FORMAT,
                                          rowData.getString(DBDefinitions.KEY_FORMAT)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_COLOR,
                                          rowData.getString(DBDefinitions.KEY_COLOR)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_PAGES,
                                          rowData.getString(DBDefinitions.KEY_PAGES)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_GENRE,
                                          rowData.getString(DBDefinitions.KEY_GENRE)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_LANGUAGE,
                                          rowData.getString(DBDefinitions.KEY_LANGUAGE)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_TOC_BITMASK,
                                          rowData.getLong(DBDefinitions.KEY_TOC_BITMASK)));

                writer.write(XmlTags.attr(DBDefinitions.KEY_PRICE_PAID,
                                          rowData.getDouble(DBDefinitions.KEY_PRICE_PAID)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                                          rowData.getString(
                                                  DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_DATE_ACQUIRED,
                                          rowData.getString(DBDefinitions.KEY_DATE_ACQUIRED)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_LOCATION,
                                          rowData.getString(DBDefinitions.KEY_LOCATION)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_RATING,
                                          rowData.getDouble(DBDefinitions.KEY_RATING)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_SIGNED,
                                          rowData.getBoolean(DBDefinitions.KEY_SIGNED)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_EDITION_BITMASK,
                                          rowData.getLong(DBDefinitions.KEY_EDITION_BITMASK)));

                // external ID's
                //NEWTHINGS: add new site specific ID: add attribute
                writer.write(XmlTags.attr(DBDefinitions.KEY_EID_LIBRARY_THING,
                                          rowData.getLong(DBDefinitions.KEY_EID_LIBRARY_THING)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_EID_STRIP_INFO_BE,
                                          rowData.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_EID_OPEN_LIBRARY,
                                          rowData.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_EID_ISFDB,
                                          rowData.getLong(DBDefinitions.KEY_EID_ISFDB)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_EID_GOODREADS_BOOK,
                                          rowData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)));
                writer.write(XmlTags.attr(DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE,
                                          rowData.getString(
                                                  DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE)));

                // cross-linked with the loanee table
                writer.write(XmlTags.attr(DBDefinitions.KEY_LOANEE,
                                          rowData.getString(DBDefinitions.KEY_LOANEE)));

                // close the tag
                writer.write(">\n");

                // last are the text field tags
                writer.write(XmlTags.tagWithCData(DBDefinitions.KEY_DESCRIPTION, null,
                                                  rowData.getString(
                                                          DBDefinitions.KEY_DESCRIPTION)));
                writer.write(XmlTags.tagWithCData(DBDefinitions.KEY_PRIVATE_NOTES, null,
                                                  rowData.getString(
                                                          DBDefinitions.KEY_PRIVATE_NOTES)));

                writer.write("</" + XmlTags.XML_BOOK + ">\n");
                mResults.booksExported++;
            }
        }
        writer.write("</" + XmlTags.XML_BOOK_LIST + ">\n");
    }

    /**
     * Internal routine to send the passed {@link EntityWriter} data to an XML file.
     *
     * @param writer   where to send the XML to
     * @param accessor which provides the input
     */
    private void toXml(@NonNull final Writer writer,
                       @NonNull final EntityWriter<String> accessor)
            throws IOException {

        String listRoot = accessor.getListRoot();
        writer.write('<');
        writer.write(listRoot);
        writer.write(XmlTags.size(accessor.size()));
        writer.write(">\n");

        // loop through all elements
        do {
            // IMPORTANT: get the keys for each iteration, as they might be different
            // from element to element.
            String[] keys = accessor.elementKeySet().toArray(new String[]{});
            // sure, not needed, but if you want to eyeball the resulting file...
            Arrays.sort(keys);

            // start with an element, optionally add a name attribute
            String nameAttr = accessor.getElementNameAttribute();
            writer.write('<');
            writer.write(accessor.getElementRoot());
            writer.write(XmlTags.version(accessor.getElementVersionAttribute()));
            if (nameAttr != null) {
                writer.write(XmlTags.name(nameAttr));
            }
            writer.write(" >\n");

            // loop through all keys of the element
            for (String name : keys) {
                writer.write(XmlTags.typedTag(name, accessor.get(name)));
            }
            // end of element.
            writer.write("</");
            writer.write(accessor.getElementRoot());
            writer.write(">\n");

        } while (accessor.hasMore());

        // close the list.
        writer.write("</");
        writer.write(listRoot);
        writer.write(">\n");
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
         * When we do not have a list, this method should simply return 'false'.
         * <ul>When there is a list:
         * <li>the first element should be set to be the 'current'</li>
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
        private final ArchiveInfo mInfo;

        @NonNull
        private final Bundle mBundle;

        /**
         * Constructor.
         *
         * @param info block to write.
         */
        InfoWriter(@NonNull final ArchiveInfo info) {
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
            return Objects.requireNonNull(mBundle.get(key));
        }
    }

    /**
     * Supports a single Preferences block.
     */
    static class PreferencesWriter
            implements EntityWriter<String> {

        @Nullable
        private final String mName;

        private final Context mContext;
        private final Map<String, ?> mMap;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param map     to read from
         * @param name    (optional) of the SharedPreference
         */
        PreferencesWriter(final Context context,
                          @NonNull final Map<String, ?> map,
                          @SuppressWarnings("SameParameterValue") @Nullable final String name) {
            mContext = context;
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
            return App.getVersion(mContext);
        }

        @NonNull
        @Override
        public Set<String> elementKeySet() {
            return mMap.keySet();
        }

        @Override
        @NonNull
        public Object get(@NonNull final String key) {
            return Objects.requireNonNull(mMap.get(key));
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

        @NonNull
        private final Context mContext;
        private final Collection<BooklistStyle> mStyles;
        private final Iterator<BooklistStyle> it;

        private BooklistStyle currentStyle;
        /** the Preferences from the current style and the groups that have PPrefs. */
        private Map<String, PPref> currentStylePPrefs;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param styles  list of styles to write
         */
        StylesWriter(@NonNull final Context context,
                     @NonNull final Collection<BooklistStyle> styles) {
            mContext = context;
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
            return XmlExporter.XML_EXPORTER_STYLES_VERSION_1;
        }

        @Override
        @NonNull
        public Set<String> elementKeySet() {
            return currentStylePPrefs.keySet();
        }

        @NonNull
        @Override
        public Object get(@NonNull final String key) {
            return Objects.requireNonNull(currentStylePPrefs.get(key)).getValue(mContext);
        }
    }
}
