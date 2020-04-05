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
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
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
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlUtils;

/**
 * <ul>Supports:
 *      <li>{@link ArchiveContainerEntry#InfoHeaderXml}</li>
 *      <li>{@link ArchiveContainerEntry#BooklistStylesXml}</li>
 *      <li>{@link ArchiveContainerEntry#PreferencesXml}</li>
 *      <li>{@link ArchiveContainerEntry#BooksXml}</li>
 * </ul>
 *
 * <strong>WARNING: EXPERIMENTAL</strong> There are two types of XML here.
 * <ul>Type based, where the tag name is the type. Used by:
 *      <li>{@link ArchiveInfo}</li>
 *      <li>{@link android.content.SharedPreferences}</li>
 *      <li>{@link BooklistStyle}</li>
 * </ul>
 * <ul>Reason:
 *      <li>more or less flat objects (Bundle or Bundle-like)</li>
 *      <li>can be generically written (and read), so future adding/remove
 *          entries requires no changes here</li>
 *      <li>really only useful to the application itself</li>
 *      <li>write and read support</li>
 * </ul>
 * <ul>Database column name based. Used by:
 *      <li>{@link Bookshelf}</li>
 *      <li>{@link Author}</li>
 *      <li>{@link Series}</li>
 *      <li>{@link Book}</li>
 * </ul>
 * <ul>Reason:
 *      <li>EXPERIMENTAL - format can/will change</li>
 *      <li>meant for loading on a computer to create reports or whatever...</li>
 *      <li>not bound to the application itself.</li>
 *      <li>write (export) only.</li>
 * </ul>
 */
public class XmlExporter
        implements Exporter, Closeable {

    /** The format version of this exporter. */
    public static final int VERSION = 2;
    /** Log tag. */
    private static final String TAG = "XmlExporter";
    /** individual format versions of table based data. */
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;
    private static final int XML_EXPORTER_BOOKS_VERSION = 1;

    /** individual format version of Preferences. */
    private static final int XML_EXPORTER_PREFERENCES_VERSION = 1;

    /** individual format version of Styles. */
    private static final int XML_EXPORTER_STYLES_VERSION = 1;
    private static final int XML_EXPORTER_STYLES_VERSION_EXPERIMENTAL = 99;

    /** Only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    private final ExportResults mResults = new ExportResults();
    private final int mOptions;
    @Nullable
    private final Date mSince;
    /** cached localized "unknown" string. */
    @NonNull
    private final String mUnknownString;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param options what to export
     * @param since   (optional) date to use as cut-off for the last-updated date
     */
    public XmlExporter(@NonNull final Context context,
                       final int options,
                       @Nullable final Date since) {
        if (BuildConfig.DEBUG /* always */) {
            // For now, we only want to write one entity at a time.
            // This is by choice so debug is easier.
            //TODO: restructure and allow multi-writes
            if (Integer.bitCount(options) > 1) {
                throw new IllegalStateException("only one option allowed");
            }
        }

        Locale locale = LocaleUtils.getUserLocale(context);
        mUnknownString = context.getString(R.string.unknown).toUpperCase(locale);

        mOptions = options;
        mSince = since;
        mDb = new DAO(TAG);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    /**
     * Write all desired item types to the output writer as XML.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        // suffix for progress messages.
        String xml = " (xml)";

        // ignore non-supported options
        boolean writeBooks = (mOptions & Options.BOOKS) != 0;
        boolean writePrefs = (mOptions & Options.PREFS) != 0;
        boolean writeStyles = (mOptions & Options.STYLES) != 0;

        // Write styles and prefs first.

        if (!progressListener.isCancelled() && writeStyles) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_styles) + xml);
            writeStyles(context, writer);
            // an experiment, might become the v2 of the styles format
            //writeStyles2(context, writer, progressListener);
        }

        if (!progressListener.isCancelled() && writePrefs) {
            progressListener.onProgressStep(1, context.getString(R.string.lbl_settings) + xml);
            writePreferences(context, writer);
        }

        if (!progressListener.isCancelled() && writeBooks) {
            // parsing will be faster if these go in the order done here.
            progressListener.onProgressStep(1, context.getString(R.string.lbl_bookshelves) + xml);
            writeBookshelves(writer, progressListener);
            progressListener.onProgressStep(1, context.getString(R.string.lbl_author) + xml);
            writeAuthors(writer, progressListener);
            progressListener.onProgressStep(1, context.getString(R.string.lbl_series) + xml);
            writeSeries(writer, progressListener);
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

        writer.write('<' + XmlTags.TAG_STYLE_LIST);
        writer.write(XmlUtils.versionAttr(XML_EXPORTER_STYLES_VERSION_EXPERIMENTAL));
        writer.write(">\n");

        for (BooklistStyle style : styles) {
            writer.write('<' + XmlTags.TAG_STYLE);
            writer.write(XmlUtils.idAttr(style.getId()));
            writer.write(XmlUtils.nameAttr(style.getUuid()));
            writer.write(">\n");

            // All 'flat' Preferences for this style.
            for (PPref p : style.getPreferences(false).values()) {
                writer.write(XmlUtils.typedTag(p.getKey(), p.getValue(context)));
            }

            // Groups with their Preferences.
            writer.write('<' + XmlTags.TAG_GROUP_LIST + '>');
            for (BooklistGroup group : style.getGroups()) {
                writer.write('<' + XmlTags.TAG_GROUP);
                writer.write(XmlUtils.idAttr(group.getId()));
                writer.write(">\n");
                for (PPref p : group.getPreferences().values()) {
                    writer.write(XmlUtils.typedTag(p.getKey(), p.getValue(context)));
                }
                writer.write("</" + XmlTags.TAG_GROUP + ">\n");
            }
            writer.write("</" + XmlTags.TAG_GROUP_LIST + '>');

            // Active filters with their Preferences.
            writer.write('<' + XmlTags.TAG_FILTER_LIST + '>');
            for (Filter filter : style.getActiveFilters(context)) {
                if (filter.isActive(context)) {
                    writer.write(XmlUtils.tag(XmlTags.TAG_FILTER, filter.getKey(), filter.get()));
                }
            }
            writer.write("</" + XmlTags.TAG_FILTER_LIST + '>');

            // close style tag.
            writer.write("</" + XmlTags.TAG_STYLE + ">\n");
        }

        writer.write("</" + XmlTags.TAG_STYLE_LIST + ">\n");
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
        mResults.preferences++;
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

        try (Cursor cursor = mDb.fetchBookshelves()) {
            writer.write('<' + XmlTags.TAG_BOOKSHELF_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_BOOKSHELVES_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + XmlTags.TAG_BOOKSHELF);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOKSHELF_NAME,
                                           rowData.getString(DBDefinitions.KEY_BOOKSHELF_NAME)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_FK_STYLE,
                                           rowData.getString(DBDefinitions.KEY_UUID)));
                writer.write("/>\n");
            }
            writer.write("</" + XmlTags.TAG_BOOKSHELF_LIST + ">\n");
        }
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

        try (Cursor cursor = mDb.fetchAuthors()) {
            writer.write('<' + XmlTags.TAG_AUTHOR_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_AUTHORS_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                writer.write('<' + XmlTags.TAG_AUTHOR);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                                           rowData.getString(
                                                   DBDefinitions.KEY_AUTHOR_FAMILY_NAME)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES,
                                           rowData.getString(
                                                   DBDefinitions.KEY_AUTHOR_GIVEN_NAMES)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                           rowData.getBoolean(
                                                   DBDefinitions.KEY_AUTHOR_IS_COMPLETE)));
                writer.write("/>\n");
            }
            writer.write("</" + XmlTags.TAG_AUTHOR_LIST + ">\n");
        }
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

        try (Cursor cursor = mDb.fetchSeries()) {
            writer.write('<' + XmlTags.TAG_SERIES_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_SERIES_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                writer.write('<' + XmlTags.TAG_SERIES);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_SERIES_TITLE,
                                           rowData.getString(DBDefinitions.KEY_SERIES_TITLE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                           rowData.getBoolean(
                                                   DBDefinitions.KEY_SERIES_IS_COMPLETE)));
                writer.write("/>\n");
            }
            writer.write("</" + XmlTags.TAG_SERIES_LIST + ">\n");
        }
    }

    /**
     * 'loan_to' is added to the books section here, this might be removed.
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeBooks(@NonNull final Writer writer,
                            @NonNull final ProgressListener progressListener)
            throws IOException {

        long lastUpdate = 0;

        try (Cursor cursor = mDb.fetchBooksForExport(mSince)) {
            writer.write('<' + XmlTags.TAG_BOOK_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_BOOKS_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            int progressMaxCount = progressListener.getMax() + cursor.getCount();
            progressListener.setMax(progressMaxCount);

            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                String title = rowData.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank.
                if (title.trim().isEmpty()) {
                    title = mUnknownString;
                }

                // it's a buffered writer, no need to first StringBuilder the line.
                writer.write('<' + XmlTags.TAG_BOOK);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_TITLE, title));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_ISBN,
                                           rowData.getString(DBDefinitions.KEY_ISBN)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_UUID,
                                           rowData.getString(DBDefinitions.KEY_BOOK_UUID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_ADDED,
                                           rowData.getString(DBDefinitions.KEY_DATE_ADDED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_LAST_UPDATED,
                                           rowData.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_READ,
                                           rowData.getBoolean(DBDefinitions.KEY_READ)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_READ_START,
                                           rowData.getString(DBDefinitions.KEY_READ_START)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_READ_END,
                                           rowData.getString(DBDefinitions.KEY_READ_END)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_PUBLISHER,
                                           rowData.getString(DBDefinitions.KEY_PUBLISHER)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRINT_RUN,
                                           rowData.getString(DBDefinitions.KEY_PRINT_RUN)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_PUBLISHED,
                                           rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_LISTED,
                                           rowData.getDouble(DBDefinitions.KEY_PRICE_LISTED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                           rowData.getString(
                                                   DBDefinitions.KEY_PRICE_LISTED_CURRENCY)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                           rowData.getString(
                                                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_FORMAT,
                                           rowData.getString(DBDefinitions.KEY_FORMAT)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_COLOR,
                                           rowData.getString(DBDefinitions.KEY_COLOR)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PAGES,
                                           rowData.getString(DBDefinitions.KEY_PAGES)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_GENRE,
                                           rowData.getString(DBDefinitions.KEY_GENRE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_LANGUAGE,
                                           rowData.getString(DBDefinitions.KEY_LANGUAGE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_TOC_BITMASK,
                                           rowData.getLong(DBDefinitions.KEY_TOC_BITMASK)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_CONDITION,
                                           rowData.getInt(DBDefinitions.KEY_BOOK_CONDITION)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_CONDITION_COVER,
                                           rowData.getInt(DBDefinitions.KEY_BOOK_CONDITION_COVER)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_PAID,
                                           rowData.getDouble(DBDefinitions.KEY_PRICE_PAID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                                           rowData.getString(
                                                   DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_ACQUIRED,
                                           rowData.getString(DBDefinitions.KEY_DATE_ACQUIRED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_LOCATION,
                                           rowData.getString(DBDefinitions.KEY_LOCATION)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_RATING,
                                           rowData.getDouble(DBDefinitions.KEY_RATING)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_SIGNED,
                                           rowData.getBoolean(DBDefinitions.KEY_SIGNED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EDITION_BITMASK,
                                           rowData.getLong(DBDefinitions.KEY_EDITION_BITMASK)));

                // external ID's
                //NEWTHINGS: add new site specific ID: add attribute
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_LIBRARY_THING,
                                           rowData.getLong(DBDefinitions.KEY_EID_LIBRARY_THING)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_STRIP_INFO_BE,
                                           rowData.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_OPEN_LIBRARY,
                                           rowData.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_ISFDB,
                                           rowData.getLong(DBDefinitions.KEY_EID_ISFDB)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_GOODREADS_BOOK,
                                           rowData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE,
                                           rowData.getString(
                                                   DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE)));

                // cross-linked with the loanee table
                writer.write(XmlUtils.attr(DBDefinitions.KEY_LOANEE,
                                           rowData.getString(DBDefinitions.KEY_LOANEE)));

                // close the tag
                writer.write(">\n");

                // last are the text field tags
                writer.write(XmlUtils.tagWithCData(DBDefinitions.KEY_DESCRIPTION, null,
                                                   rowData.getString(
                                                           DBDefinitions.KEY_DESCRIPTION)));
                writer.write(XmlUtils.tagWithCData(DBDefinitions.KEY_PRIVATE_NOTES, null,
                                                   rowData.getString(
                                                           DBDefinitions.KEY_PRIVATE_NOTES)));

                writer.write("</" + XmlTags.TAG_BOOK + ">\n");

                mResults.booksExported++;

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
                    progressListener.onProgress(mResults.booksExported, title);
                    lastUpdate = now;
                }
            }
            writer.write("</" + XmlTags.TAG_BOOK_LIST + ">\n");
        }
    }

    /**
     * Internal routine that uses the passed {@link EntityWriter} to convert
     * and send collection data to an XML file.
     *
     * @param writer   where to send the XML to
     * @param accessor the EntityReader to convert the object to XML
     *
     * @throws IOException on failure
     */
    private void toXml(@NonNull final Writer writer,
                       @NonNull final EntityWriter<String> accessor)
            throws IOException {

        // the list root does not get a version number,  individual elements do.
        String listRoot = accessor.getRootTag();
        writer.write('<');
        writer.write(listRoot);
        writer.write(XmlUtils.versionAttr(accessor.getRootTagVersionAttribute()));
        writer.write(XmlUtils.sizeAttr(accessor.getRootTagSizeAttribute()));
        writer.write(">\n");

        // loop through all elements
        while (accessor.hasMoreElements()) {
            // start with an element, optionally add an id and/or name attribute
            long idAttr = accessor.getElementTagIdAttribute();
            String nameAttr = accessor.getElementTagNameAttribute();
            writer.write('<');
            writer.write(accessor.getElementTag());
            if (idAttr != 0) {
                writer.write(XmlUtils.idAttr(idAttr));
            }
            if (nameAttr != null) {
                writer.write(XmlUtils.nameAttr(nameAttr));
            }
            writer.write(">\n");

            // loop through all keys of the element
            // IMPORTANT: get the keys for each iteration, as they might be different
            // from element to element.
            for (String name : accessor.getElementKeySet()) {
                writer.write(XmlUtils.typedTag(name, accessor.get(name)));
            }
            // end of element.
            writer.write("</");
            writer.write(accessor.getElementTag());
            writer.write(">\n");
        }

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
     * <p>
     * Supports 2-layer deep/flat xml output; i.e. a list (tag) of elements (one tag per element)
     *
     * @param <K> Type of the collection key
     */
    interface EntityWriter<K> {

        /**
         * Get the top-root tag name.
         *
         * @return root tag name
         */
        @NonNull
        String getRootTag();

        /**
         * Get the top-root tag version attribute.
         *
         * @return version
         */
        long getRootTagVersionAttribute();

        /**
         * Get the size of the list.
         *
         * @return size
         */
        int getRootTagSizeAttribute();

        /**
         * Check if the collection has more elements.
         * <p>
         * See {@link XmlImporter.StylesReader} for an example
         *
         * @return {@code true} if there are
         */
        boolean hasMoreElements();

        /**
         * Get the element tag for each item in the collection.
         *
         * @return root
         */
        @NonNull
        String getElementTag();

        /**
         * Get the element id attribute for each item in the collection.
         * Optional; {@code 0} by default.
         *
         * @return name, or {@code null}
         */
        default long getElementTagIdAttribute() {
            return 0;
        }

        /**
         * Get the element tag name attribute for each item in the collection.
         * Optional. {@code null} by default.
         *
         * @return name, or {@code null}
         */
        @Nullable
        default String getElementTagNameAttribute() {
            return null;
        }

        /**
         * Get the collection of keys for the <strong>current</strong> element.
         * i.e. the XML writer assumes that each element can have a different key set.
         *
         * @return keys
         */
        Set<K> getElementKeySet();

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
        @NonNull
        private final ArchiveInfo mInfo;

        @NonNull
        private final Bundle mBundle;

        private boolean mHasMore = true;

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
        public String getRootTag() {
            return XmlTags.TAG_INFO_LIST;
        }

        @Override
        public long getRootTagVersionAttribute() {
            return mInfo.getArchiveVersion();
        }

        @Override
        public int getRootTagSizeAttribute() {
            return 1;
        }

        @Override
        public boolean hasMoreElements() {
            boolean hasMore = mHasMore;
            mHasMore = false;
            return hasMore;
        }

        @Override
        @NonNull
        public String getElementTag() {
            return XmlTags.TAG_INFO;
        }

        @Override
        @NonNull
        public Set<String> getElementKeySet() {
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
        @NonNull
        private final Context mContext;
        @NonNull
        private final Map<String, ?> mMap;

        private boolean mHasMore = true;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param map     to read from
         * @param name    (optional) of the SharedPreference
         */
        PreferencesWriter(@NonNull final Context context,
                          @NonNull final Map<String, ?> map,
                          @SuppressWarnings("SameParameterValue") @Nullable final String name) {
            mContext = context;
            mMap = map;
            mName = name;
        }

        @Override
        @NonNull
        public String getRootTag() {
            return XmlTags.TAG_PREFERENCES_LIST;
        }

        @Override
        public long getRootTagVersionAttribute() {
            return App.getVersion(mContext);
        }

        @Override
        public int getRootTagSizeAttribute() {
            return XML_EXPORTER_PREFERENCES_VERSION;
        }

        @Override
        public boolean hasMoreElements() {
            boolean hasMore = mHasMore;
            mHasMore = false;
            return hasMore;
        }

        @Override
        @NonNull
        public String getElementTag() {
            return XmlTags.TAG_PREFERENCES;
        }

        @Nullable
        @Override
        public String getElementTagNameAttribute() {
            return mName;
        }

        @NonNull
        @Override
        public Set<String> getElementKeySet() {
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
        @NonNull
        private final Collection<BooklistStyle> mStyles;
        @NonNull
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
        }

        @Override
        @NonNull
        public String getRootTag() {
            return XmlTags.TAG_STYLE_LIST;
        }

        @Override
        public long getRootTagVersionAttribute() {
            return XmlExporter.XML_EXPORTER_STYLES_VERSION;
        }

        @Override
        public int getRootTagSizeAttribute() {
            return mStyles.size();
        }

        @Override
        public boolean hasMoreElements() {
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
        public String getElementTag() {
            return XmlTags.TAG_STYLE;
        }

        @Nullable
        @Override
        public String getElementTagNameAttribute() {
            return currentStyle.getUuid();
        }

        @Override
        @NonNull
        public Set<String> getElementKeySet() {
            return currentStylePPrefs.keySet();
        }

        @NonNull
        @Override
        public Object get(@NonNull final String key) {
            return Objects.requireNonNull(currentStylePPrefs.get(key)).getValue(mContext);
        }
    }
}
