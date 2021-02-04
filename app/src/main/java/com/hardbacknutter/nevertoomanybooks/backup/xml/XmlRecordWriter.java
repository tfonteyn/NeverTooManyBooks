/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreVirtualLibrary;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * <ul>Supports:
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 * <ul>
 *      <li>EXPERIMENTAL - format can/will change</li>
 *      <li>meant for loading on a computer to create reports or whatever...</li>
 *      <li>write (export) only.</li>
 *      <li>NO LINK TABLES YET</li>
 * </ul>
 */
public class XmlRecordWriter
        implements RecordWriter {

    /** The format version of this RecordWriter. */
    private static final int VERSION = 2;

    /** Log tag. */
    private static final String TAG = "XmlRecordWriter";

    /** individual format versions of table based data. */
    private static final int VERSION_BOOKSHELVES = 1;
    private static final int VERSION_AUTHORS = 1;
    private static final int VERSION_SERIES = 1;
    private static final int VERSION_PUBLISHERS = 1;
    private static final int VERSION_TOC_LIST = 1;
    private static final int VERSION_BOOKS = 1;

    private static final String TAG_BOOKSHELF = "bookshelf";
    private static final String TAG_AUTHOR = "author";
    private static final String TAG_SERIES = "series";
    private static final String TAG_PUBLISHER = "publisher";
    private static final String TAG_TOC_ENTRY = "tocentry";
    private static final String TAG_BOOK = "book";

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    @Nullable
    private final LocalDateTime mUtcSinceDateTime;

    /**
     * Constructor.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     */
    @AnyThread
    public XmlRecordWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        mUtcSinceDateTime = utcSinceDateTime;
        mDb = new DAO(TAG);
    }

    @Override
    public void writeMetaData(@NonNull final Writer writer,
                              @NonNull final ArchiveMetaData metaData)
            throws IOException {

        final Bundle bundle = metaData.getBundle();
        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(RecordType.MetaData.getName()).append(">\n");
        for (final String name : bundle.keySet()) {
            sb.append(XmlUtils.typedTag(name, Objects.requireNonNull(bundle.get(name), name)));
        }
        sb.append("</").append(RecordType.MetaData.getName()).append(">\n");

        writer.write(sb.toString());
    }

    @Override
    @NonNull
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final Set<RecordType> entries,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        final ExportResults results = new ExportResults();

        if (entries.contains(RecordType.Books)) {

            // parsing will be faster if these go in the order done here.
            progressListener.publishProgressStep(
                    1, context.getString(R.string.lbl_bookshelves));
            writeBookshelves(writer, progressListener);

            progressListener.publishProgressStep(
                    1, context.getString(R.string.site_calibre));
            writeCalibreLibraries(writer, progressListener);

            progressListener.publishProgressStep(
                    1, context.getString(R.string.lbl_authors));
            writeAuthors(writer, progressListener);

            progressListener.publishProgressStep(
                    1, context.getString(R.string.lbl_series_multiple));
            writeSeries(writer, progressListener);

            progressListener.publishProgressStep(
                    1, context.getString(R.string.lbl_publishers));
            writePublishers(writer, progressListener);

            progressListener.publishProgressStep(
                    1, context.getString(R.string.lbl_table_of_content));
            writeToc(writer, progressListener);

            progressListener.publishProgressStep(1, context.getString(R.string.lbl_books));
            results.add(writeBooks(context, writer, mUtcSinceDateTime,
                                   entries.contains(RecordType.Cover),
                                   progressListener));
        }

        return results;
    }

    private void writeCalibreLibraries(@NonNull final Writer writer,
                                       @NonNull final ProgressListener progressListener)
            throws IOException {
        final ArrayList<CalibreLibrary> calibreLibraries = mDb.getCalibreLibraries();
        if (!calibreLibraries.isEmpty()) {
            writer.write("<CalibreLibraryList");
            writer.write(XmlUtils.versionAttr(1));
            writer.write(XmlUtils.sizeAttr(calibreLibraries.size()));
            writer.write(">\n");
            for (final CalibreLibrary library : calibreLibraries) {
                writer.write("<CalibreLibrary");
                writer.write(XmlUtils.idAttr(library.getId()));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_LIBRARY_STRING_ID,
                                           library.getLibraryStringId()));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_LIBRARY_UUID,
                                           library.getUuid()));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME,
                                           library.getName()));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE,
                                           library.getLastSyncDateAsString()));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_FK_BOOKSHELF,
                                           library.getMappedBookshelfId()));
                writer.write(">");

                final ArrayList<CalibreVirtualLibrary> virtualLibraries = library
                        .getVirtualLibraries();
                if (!virtualLibraries.isEmpty()) {
                    for (final CalibreVirtualLibrary vlib : virtualLibraries) {
                        writer.write("<CalibreVirtualLibrary");
                        writer.write(XmlUtils.idAttr(vlib.getId()));
                        writer.write(XmlUtils.attr(DBDefinitions.KEY_FK_CALIBRE_LIBRARY,
                                                   vlib.getLibraryId()));
                        writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME,
                                                   vlib.getName()));
                        writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_VIRT_LIB_EXPR,
                                                   vlib.getExpr()));
                        writer.write(XmlUtils.attr(DBDefinitions.KEY_FK_BOOKSHELF,
                                                   vlib.getMappedBookshelfId()));
                        writer.write("/>");
                    }
                }
                writer.write("</CalibreLibrary>\n");
            }
            writer.write("</CalibreLibraryList>\n");
        }
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
            writer.write('<' + Book.BKEY_BOOKSHELF_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_BOOKSHELVES));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_BOOKSHELF);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOKSHELF_NAME,
                                           rowData.getString(DBDefinitions.KEY_BOOKSHELF_NAME)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_FK_STYLE,
                                           rowData.getString(DBDefinitions.KEY_STYLE_UUID)));
                writer.write("/>\n");
            }
            writer.write("</" + Book.BKEY_BOOKSHELF_LIST + ">\n");
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
            writer.write('<' + Book.BKEY_AUTHOR_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_AUTHORS));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_AUTHOR);
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
            writer.write("</" + Book.BKEY_AUTHOR_LIST + ">\n");
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
            writer.write('<' + Book.BKEY_SERIES_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_SERIES));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_SERIES);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_SERIES_TITLE,
                                           rowData.getString(DBDefinitions.KEY_SERIES_TITLE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                           rowData.getBoolean(
                                                   DBDefinitions.KEY_SERIES_IS_COMPLETE)));
                writer.write("/>\n");
            }
            writer.write("</" + Book.BKEY_SERIES_LIST + ">\n");
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
            writer.write('<' + Book.BKEY_PUBLISHER_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_PUBLISHERS));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_PUBLISHER);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PUBLISHER_NAME,
                                           rowData.getString(DBDefinitions.KEY_PUBLISHER_NAME)));
                writer.write("/>\n");
            }
            writer.write("</" + Book.BKEY_PUBLISHER_LIST + ">\n");
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
            writer.write('<' + Book.BKEY_TOC_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_TOC_LIST));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                // the tag is written as a single line (no line-feeds)
                writer.write('<' + TAG_TOC_ENTRY);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_TITLE,
                                           rowData.getString(DBDefinitions.KEY_TITLE)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                           rowData.getString(
                                                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
                writer.write(">");

                // Write Authors as a list, allowing for future expansion to multiple authors/toc
                writer.write('<' + Book.BKEY_AUTHOR_LIST);
                writer.write(XmlUtils.sizeAttr(1));
                writer.write(">");
                writer.write('<' + DBDefinitions.KEY_FK_AUTHOR);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBDefinitions.KEY_FK_AUTHOR)));
                writer.write("/>");
                writer.write("</" + Book.BKEY_AUTHOR_LIST + ">");

                writer.write("</" + TAG_TOC_ENTRY + ">\n");
            }
            writer.write("</" + Book.BKEY_TOC_LIST + ">\n");
        }
    }

    /**
     * 'loan_to' is added to the books section here, this might be removed.
     *
     * @param context          Current context
     * @param writer           writer
     * @param progressListener Progress and cancellation interface
     *
     * @throws IOException on failure
     */
    private ExportResults writeBooks(@NonNull final Context context,
                                     @NonNull final Writer writer,
                                     @Nullable final LocalDateTime utcSinceDateTime,
                                     final boolean collectCoverFilenames,
                                     @NonNull final ProgressListener progressListener)
            throws IOException {

        final ExportResults results = new ExportResults();

        int delta = 0;
        long lastUpdate = 0;

        final List<Domain> externalIdDomains = SearchEngineRegistry
                .getInstance().getExternalIdDomains();

        try (Cursor cursor = mDb.fetchBooksForExport(utcSinceDateTime)) {
            writer.write('<' + RecordType.Books.getName());
            writer.write(XmlUtils.versionAttr(VERSION_BOOKS));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                final Book book = Book.from(cursor, mDb);
                final String uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);

                String title = book.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank.
                if (title.trim().isEmpty()) {
                    title = context.getString(R.string.unknown_title);
                }

                writer.write('<' + TAG_BOOK);
                writer.write(XmlUtils.idAttr(book.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_TITLE, title));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_ISBN,
                                           book.getString(DBDefinitions.KEY_ISBN)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_UUID, uuid));
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
                                           book.getInt(DBDefinitions.KEY_BOOK_CONDITION_COVER)));

                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_PAID,
                                           book.getDouble(DBDefinitions.KEY_PRICE_PAID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                                           book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
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

                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_BOOK_ID,
                                           book.getInt(DBDefinitions.KEY_CALIBRE_BOOK_ID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_BOOK_UUID,
                                           book.getString(DBDefinitions.KEY_CALIBRE_BOOK_UUID)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT,
                                           book.getString(
                                                   DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT)));
                writer.write(XmlUtils.attr(DBDefinitions.KEY_FK_CALIBRE_LIBRARY,
                                           book.getLong(DBDefinitions.KEY_FK_CALIBRE_LIBRARY)));

                // external ID's
                for (final Domain domain : externalIdDomains) {
                    final String key = domain.getName();
                    writer.write(XmlUtils.attr(key, book.getString(key)));
                }
                //NEWTHINGS: adding a new search engine: optional: add engine specific keys
                writer.write(XmlUtils.attr(
                        DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE,
                        book.getString(DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE)));


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
                        book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
                if (!authors.isEmpty()) {
                    writer.write('<' + Book.BKEY_AUTHOR_LIST);
                    writer.write(XmlUtils.sizeAttr(authors.size()));
                    writer.write(">");
                    for (final Author author : authors) {
                        writer.write('<' + DBDefinitions.KEY_FK_AUTHOR);
                        writer.write(XmlUtils.idAttr(author.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + Book.BKEY_AUTHOR_LIST + ">\n");
                }

                final ArrayList<Series> seriesList =
                        book.getParcelableArrayList(Book.BKEY_SERIES_LIST);
                if (!seriesList.isEmpty()) {
                    writer.write('<' + Book.BKEY_SERIES_LIST);
                    writer.write(XmlUtils.sizeAttr(seriesList.size()));
                    writer.write(">");
                    for (final Series series : seriesList) {
                        writer.write('<' + DBDefinitions.KEY_FK_SERIES);
                        writer.write(XmlUtils.idAttr(series.getId()));
                        writer.write(XmlUtils.attr(DBDefinitions.KEY_BOOK_NUM_IN_SERIES,
                                                   series.getNumber()));
                        writer.write("/>");
                    }
                    writer.write("</" + Book.BKEY_SERIES_LIST + ">\n");
                }

                final ArrayList<Publisher> publishers =
                        book.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
                if (!publishers.isEmpty()) {
                    writer.write('<' + Book.BKEY_PUBLISHER_LIST);
                    writer.write(XmlUtils.sizeAttr(publishers.size()));
                    writer.write(">");
                    for (final Publisher publisher : publishers) {
                        writer.write('<' + DBDefinitions.KEY_FK_PUBLISHER);
                        writer.write(XmlUtils.idAttr(publisher.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + Book.BKEY_PUBLISHER_LIST + ">\n");
                }

                final ArrayList<Bookshelf> bookshelves =
                        book.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
                if (!bookshelves.isEmpty()) {
                    writer.write('<' + Book.BKEY_BOOKSHELF_LIST);
                    writer.write(XmlUtils.sizeAttr(bookshelves.size()));
                    writer.write(">");
                    for (final Bookshelf bookshelf : bookshelves) {
                        writer.write('<' + DBDefinitions.KEY_FK_BOOKSHELF);
                        writer.write(XmlUtils.idAttr(bookshelf.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + Book.BKEY_BOOKSHELF_LIST + ">\n");
                }

                final ArrayList<TocEntry> tocEntries =
                        book.getParcelableArrayList(Book.BKEY_TOC_LIST);
                if (!tocEntries.isEmpty()) {
                    writer.write('<' + Book.BKEY_TOC_LIST);
                    writer.write(XmlUtils.sizeAttr(tocEntries.size()));
                    writer.write(">");
                    for (final TocEntry tocEntry : tocEntries) {
                        writer.write('<' + DBDefinitions.KEY_FK_TOC_ENTRY);
                        writer.write(XmlUtils.idAttr(tocEntry.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + Book.BKEY_TOC_LIST + ">\n");
                }

                writer.write("</" + TAG_BOOK + ">\n");

                results.addBook(book.getId());

                if (collectCoverFilenames) {
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        final File cover = book.getUuidCoverFile(context, cIdx);
                        if (cover != null && cover.exists()) {
                            results.addCover(cover.getName());
                        }
                    }
                }

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                    progressListener.publishProgressStep(delta, title);
                    lastUpdate = now;
                    delta = 0;
                }
            }
            writer.write("</" + RecordType.Books.getName() + ">\n");
        }

        return results;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public void close() {
        mDb.close();
    }
}
