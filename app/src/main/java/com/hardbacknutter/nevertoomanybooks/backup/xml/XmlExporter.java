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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
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
 *      <li>{@link Publisher}</li>
 *      <li>{@link Book}</li>
 * </ul>
 * <ul>Reason:
 *      <li>EXPERIMENTAL - format can/will change</li>
 *      <li>meant for loading on a computer to create reports or whatever...</li>
 *      <li>not bound to the application itself.</li>
 *      <li>write (export) only.</li>
 *      <li>NO LINK TABLES YET</li>
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
    private static final int XML_EXPORTER_PUBLISHER_VERSION = 1;
    private static final int XML_EXPORTER_TOC_VERSION = 1;
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
    private final LocalDateTime mUtcSinceDateTime;
    /** cached localized "unknown" string. */
    @NonNull
    private final String mUnknownString;

    /**
     * Constructor.
     *
     * @param context          Current context
     * @param options          {@link Options} flags
     * @param utcSinceDateTime (optional) UTC based date to select only books modified or added
     *                         since.
     */
    public XmlExporter(@NonNull final Context context,
                       final int options,
                       @Nullable final LocalDateTime utcSinceDateTime) {
        if (BuildConfig.DEBUG /* always */) {
            // For now, we only want to write one entity at a time.
            // This is by choice so debug is easier.
            //TODO: restructure and allow multi-writes
            if (Integer.bitCount(options) > 1) {
                throw new IllegalStateException("only one option allowed");
            }
        }

        final Locale locale = LocaleUtils.getUserLocale(context);
        mUnknownString = context.getString(R.string.unknown).toUpperCase(locale);

        mOptions = options;
        mUtcSinceDateTime = utcSinceDateTime;
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
        final String xml = " (xml)";

        // ignore non-supported options
        final boolean writeStyles = (mOptions & Options.STYLES) != 0;
        final boolean writePrefs = (mOptions & Options.PREFS) != 0;

        final boolean writeBooks = (mOptions & Options.BOOKS) != 0;

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

            progressListener.onProgressStep(1, context.getString(R.string.lbl_authors) + xml);
            writeAuthors(writer, progressListener);

            progressListener
                    .onProgressStep(1, context.getString(R.string.lbl_series_multiple) + xml);
            writeSeries(writer, progressListener);

            progressListener.onProgressStep(1, context.getString(R.string.lbl_publishers) + xml);
            writePublishers(writer, progressListener);

            progressListener
                    .onProgressStep(1, context.getString(R.string.lbl_table_of_content) + xml);
            writeToc(writer, progressListener);

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
        final Collection<BooklistStyle> styles =
                BooklistStyle.StyleDAO.getStyles(context, mDb).values();
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
        final Collection<BooklistStyle> styles =
                BooklistStyle.StyleDAO.getStyles(context, mDb).values();
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
            for (Filter<?> filter : style.getActiveFilters(context)) {
                if (filter.isActive(context)) {
                    writer.write(XmlUtils.tag(XmlTags.TAG_FILTER,
                                              filter.getKey(), filter.getValue(context)));
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

        final Map<String, ?> all = PreferenceManager.getDefaultSharedPreferences(context).getAll();

        // remove the acra settings
        final Iterator<String> it = all.keySet().iterator();
        while (it.hasNext()) {
            final String key = it.next();
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

            final DataHolder rowData = new CursorRow(cursor);
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

            final DataHolder rowData = new CursorRow(cursor);
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

            final DataHolder rowData = new CursorRow(cursor);
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
     * Write out {@link DBDefinitions#TBL_SERIES}.
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writePublishers(@NonNull final Writer writer,
                                 @NonNull final ProgressListener progressListener)
            throws IOException {

        try (Cursor cursor = mDb.fetchPublishers()) {
            writer.write('<' + XmlTags.TAG_PUBLISHER_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_PUBLISHER_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                writer.write('<' + XmlTags.TAG_PUBLISHER);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PUBLISHER_NAME,
                                           rowData.getString(DBDefinitions.KEY_PUBLISHER_NAME)));
                writer.write("/>\n");
            }
            writer.write("</" + XmlTags.TAG_PUBLISHER_LIST + ">\n");
        }
    }

    /**
     * Write out {@link DBDefinitions#TBL_TOC_ENTRIES}.
     *
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private void writeToc(@NonNull final Writer writer,
                          @NonNull final ProgressListener progressListener)
            throws IOException {
        try (Cursor cursor = mDb.fetchTocs()) {
            writer.write('<' + XmlTags.TAG_TOC_ENTRY_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_TOC_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                // the tag is written as a single line (no line-feeds)
                writer.write('<' + XmlTags.TAG_TOC_ENTRY);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(
                        DBDefinitions.KEY_TITLE,
                        rowData.getString(DBDefinitions.KEY_TITLE)));
                writer.write(XmlUtils.attr(
                        DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                        rowData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));

                // close the start tag
                writer.write(">");

                // Write Authors as list, allowing for future expansion to multiple authors/toc
                writer.write('<' + XmlTags.TAG_AUTHOR_LIST);
                writer.write(XmlUtils.sizeAttr(1));
                writer.write(">");
                writer.write('<' + XmlTags.TAG_AUTHOR);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_FK_AUTHOR)));
                writer.write("/>");
                writer.write("</" + XmlTags.TAG_AUTHOR_LIST + ">");

                writer.write("</" + XmlTags.TAG_TOC_ENTRY + ">\n");
            }
            writer.write("</" + XmlTags.TAG_TOC_ENTRY_LIST + ">\n");
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

        final Book book = new Book();

        try (Cursor cursor = mDb.fetchBooksForExport(mUtcSinceDateTime)) {
            writer.write('<' + XmlTags.TAG_BOOK_LIST);
            writer.write(XmlUtils.versionAttr(XML_EXPORTER_BOOKS_VERSION));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            int progressMaxCount = progressListener.getProgressMaxPos() + cursor.getCount();
            progressListener.setProgressMaxPos(progressMaxCount);

            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                book.load(cursor, mDb);

                String title = book.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank.
                if (title.trim().isEmpty()) {
                    title = mUnknownString;
                }

                // it's a buffered writer, no need to first StringBuilder the line.
                writer.write('<' + XmlTags.TAG_BOOK);
                writer.write(XmlUtils.idAttr(book.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_TITLE, title));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_ISBN,
                                           book.getString(DBDefinitions.KEY_ISBN)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_UUID,
                                           book.getString(DBDefinitions.KEY_BOOK_UUID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_UTC_ADDED,
                                           book.getString(DBDefinitions.KEY_UTC_ADDED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_UTC_LAST_UPDATED,
                                           book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_READ,
                                           book.getBoolean(DBDefinitions.KEY_READ)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_READ_START,
                                           book.getString(DBDefinitions.KEY_READ_START)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_READ_END,
                                           book.getString(DBDefinitions.KEY_READ_END)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRINT_RUN,
                                           book.getString(DBDefinitions.KEY_PRINT_RUN)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_PUBLISHED,
                                           book.getString(DBDefinitions.KEY_DATE_PUBLISHED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_LISTED,
                                           book.getDouble(DBDefinitions.KEY_PRICE_LISTED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                           book.getString(
                                                   DBDefinitions.KEY_PRICE_LISTED_CURRENCY)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                           book.getString(
                                                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_FORMAT,
                                           book.getString(DBDefinitions.KEY_FORMAT)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_COLOR,
                                           book.getString(DBDefinitions.KEY_COLOR)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PAGES,
                                           book.getString(DBDefinitions.KEY_PAGES)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_GENRE,
                                           book.getString(DBDefinitions.KEY_GENRE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_LANGUAGE,
                                           book.getString(DBDefinitions.KEY_LANGUAGE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_TOC_BITMASK,
                                           book.getLong(DBDefinitions.KEY_TOC_BITMASK)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_CONDITION,
                                           book.getInt(DBDefinitions.KEY_BOOK_CONDITION)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_CONDITION_COVER,
                                           book.getInt(
                                                   DBDefinitions.KEY_BOOK_CONDITION_COVER)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_PAID,
                                           book.getDouble(DBDefinitions.KEY_PRICE_PAID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                                           book.getString(
                                                   DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_ACQUIRED,
                                           book.getString(DBDefinitions.KEY_DATE_ACQUIRED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_LOCATION,
                                           book.getString(DBDefinitions.KEY_LOCATION)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_RATING,
                                           book.getDouble(DBDefinitions.KEY_RATING)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_SIGNED,
                                           book.getBoolean(DBDefinitions.KEY_SIGNED)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EDITION_BITMASK,
                                           book.getLong(DBDefinitions.KEY_EDITION_BITMASK)));

                // external ID's
                //NEWTHINGS: add new site specific ID: add attribute
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_LIBRARY_THING,
                                           book.getLong(DBDefinitions.KEY_EID_LIBRARY_THING)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_STRIP_INFO_BE,
                                           book.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_OPEN_LIBRARY,
                                           book.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_ISFDB,
                                           book.getLong(DBDefinitions.KEY_EID_ISFDB)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_EID_GOODREADS_BOOK,
                                           book.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)));
                writer.write(XmlUtils.attr(
                        DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS,
                        book.getString(DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS)));

                // cross-linked with the loanee table
                writer.write(XmlUtils.attr(DBDefinitions.KEY_LOANEE,
                                           book.getString(DBDefinitions.KEY_LOANEE)));

                // close the start tag
                writer.write(">\n");

                // the text field tags
                writer.write(XmlUtils.tagWithCData(
                        DBDefinitions.KEY_DESCRIPTION, null,
                        book.getString(DBDefinitions.KEY_DESCRIPTION)));
                writer.write(XmlUtils.tagWithCData(
                        DBDefinitions.KEY_PRIVATE_NOTES, null,
                        book.getString(DBDefinitions.KEY_PRIVATE_NOTES)));


                final ArrayList<Author> authors =
                        book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
                if (!authors.isEmpty()) {
                    writer.write('<' + XmlTags.TAG_AUTHOR_LIST);
                    writer.write(XmlUtils.sizeAttr(authors.size()));
                    writer.write(">");
                    for (Author author : authors) {
                        writer.write('<' + XmlTags.TAG_AUTHOR);
                        writer.write(XmlUtils.idAttr(author.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + XmlTags.TAG_AUTHOR_LIST + ">\n");
                }

                final ArrayList<Series> seriesList =
                        book.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
                if (!seriesList.isEmpty()) {
                    writer.write('<' + XmlTags.TAG_SERIES_LIST);
                    writer.write(XmlUtils.sizeAttr(seriesList.size()));
                    writer.write(">");
                    for (Series series : seriesList) {
                        writer.write('<' + XmlTags.TAG_SERIES);
                        writer.write(XmlUtils.idAttr(series.getId()));
                        writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_NUM_IN_SERIES,
                                                   series.getNumber()));
                        writer.write("/>");
                    }
                    writer.write("</" + XmlTags.TAG_SERIES_LIST + ">\n");
                }

                final ArrayList<Publisher> publishers =
                        book.getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
                if (!publishers.isEmpty()) {
                    writer.write('<' + XmlTags.TAG_PUBLISHER_LIST);
                    writer.write(XmlUtils.sizeAttr(publishers.size()));
                    writer.write(">");
                    for (Publisher publisher : publishers) {
                        writer.write('<' + XmlTags.TAG_PUBLISHER);
                        writer.write(XmlUtils.idAttr(publisher.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + XmlTags.TAG_PUBLISHER_LIST + ">\n");
                }

                final ArrayList<Bookshelf> bookshelves =
                        book.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY);
                if (!bookshelves.isEmpty()) {
                    writer.write('<' + XmlTags.TAG_BOOKSHELF_LIST);
                    writer.write(XmlUtils.sizeAttr(bookshelves.size()));
                    writer.write(">");
                    for (Bookshelf bookshelf : bookshelves) {
                        writer.write('<' + XmlTags.TAG_BOOKSHELF);
                        writer.write(XmlUtils.idAttr(bookshelf.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + XmlTags.TAG_BOOKSHELF_LIST + ">\n");
                }

                final ArrayList<TocEntry> tocEntries =
                        book.getParcelableArrayList(Book.BKEY_TOC_ARRAY);
                if (!tocEntries.isEmpty()) {
                    writer.write('<' + XmlTags.TAG_TOC_ENTRY_LIST);
                    writer.write(XmlUtils.sizeAttr(tocEntries.size()));
                    writer.write(">");
                    for (TocEntry tocEntry : tocEntries) {
                        writer.write('<' + XmlTags.TAG_TOC_ENTRY);
                        writer.write(XmlUtils.idAttr(tocEntry.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + XmlTags.TAG_TOC_ENTRY_LIST + ">\n");
                }

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
        final String listRoot = accessor.getRootTag();
        writer.write('<');
        writer.write(listRoot);
        writer.write(XmlUtils.versionAttr(accessor.getRootTagVersionAttribute()));
        writer.write(XmlUtils.sizeAttr(accessor.getRootTagSizeAttribute()));
        writer.write(">\n");

        // loop through all elements
        while (accessor.hasMoreElements()) {
            // start with an element, optionally add an id and/or name attribute
            final long idAttr = accessor.getElementTagIdAttribute();
            final String nameAttr = accessor.getElementTagNameAttribute();
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
            final boolean hasMore = mHasMore;
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
            final boolean hasMore = mHasMore;
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
     * --- This includes the actual groups of the style: a CSV String of group ID's
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
