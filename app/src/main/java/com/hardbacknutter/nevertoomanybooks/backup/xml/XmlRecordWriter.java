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
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreVirtualLibrary;
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

    /**
     * The format version of this RecordWriter.
     * <p>
     * Not used; each xml record has it's own version: i.e. {@link #VERSION_BOOKS}.
     */
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
            progressListener.publishProgress(
                    1, context.getString(R.string.lbl_bookshelves));
            writeBookshelves(writer, progressListener);

            progressListener.publishProgress(
                    1, context.getString(R.string.site_calibre));
            writeCalibreLibraries(writer, progressListener);

            progressListener.publishProgress(
                    1, context.getString(R.string.lbl_authors));
            writeAuthors(writer, progressListener);

            progressListener.publishProgress(
                    1, context.getString(R.string.lbl_series_multiple));
            writeSeries(writer, progressListener);

            progressListener.publishProgress(
                    1, context.getString(R.string.lbl_publishers));
            writePublishers(writer, progressListener);

            progressListener.publishProgress(
                    1, context.getString(R.string.lbl_table_of_content));
            writeToc(writer, progressListener);

            progressListener.publishProgress(1, context.getString(R.string.lbl_books));
            results.add(writeBooks(context, writer, mUtcSinceDateTime,
                                   entries.contains(RecordType.Cover),
                                   progressListener));
        }

        return results;
    }

    private void writeCalibreLibraries(@NonNull final Writer writer,
                                       @NonNull final ProgressListener progressListener)
            throws IOException {
        final ArrayList<CalibreLibrary> calibreLibraries =
                ServiceLocator.getInstance().getCalibreLibraryDao().getLibraries();
        if (!calibreLibraries.isEmpty()) {
            writer.write("<CalibreLibraryList");
            writer.write(XmlUtils.versionAttr(1));
            writer.write(XmlUtils.sizeAttr(calibreLibraries.size()));
            writer.write(">\n");
            for (final CalibreLibrary library : calibreLibraries) {
                writer.write("<CalibreLibrary");
                writer.write(XmlUtils.idAttr(library.getId()));
                writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_LIBRARY_STRING_ID,
                                           library.getLibraryStringId()));
                writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_LIBRARY_UUID,
                                           library.getUuid()));
                writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_LIBRARY_NAME,
                                           library.getName()));
                writer.write(XmlUtils.attr(DBKey.UTC_DATE_LAST_SYNC_CALIBRE_LIBRARY,
                                           library.getLastSyncDateAsString()));
                writer.write(XmlUtils.attr(DBKey.FK_BOOKSHELF,
                                           library.getMappedBookshelfId()));
                writer.write(">");

                for (final CalibreVirtualLibrary vlib : library.getVirtualLibraries()) {
                    writer.write("<CalibreVirtualLibrary");
                    writer.write(XmlUtils.idAttr(vlib.getId()));
                    writer.write(XmlUtils.attr(DBKey.FK_CALIBRE_LIBRARY, vlib.getLibraryId()));
                    writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_LIBRARY_NAME, vlib.getName()));
                    writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_VIRT_LIB_EXPR, vlib.getExpr()));
                    writer.write(XmlUtils.attr(DBKey.FK_BOOKSHELF,
                                               vlib.getMappedBookshelfId()));
                    writer.write("/>");
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

        try (Cursor cursor = ServiceLocator.getInstance().getBookshelfDao().fetchAllUserShelves()) {
            writer.write('<' + Book.BKEY_BOOKSHELF_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_BOOKSHELVES));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_BOOKSHELF);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBKey.PK_ID)));
                writer.write(XmlUtils.attr(DBKey.KEY_BOOKSHELF_NAME,
                                           rowData.getString(DBKey.KEY_BOOKSHELF_NAME)));
                writer.write(XmlUtils.attr(DBKey.FK_STYLE,
                                           rowData.getString(DBKey.KEY_STYLE_UUID)));
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

        try (Cursor cursor = ServiceLocator.getInstance().getAuthorDao().fetchAll()) {
            writer.write('<' + Book.BKEY_AUTHOR_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_AUTHORS));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_AUTHOR);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBKey.PK_ID)));

                writer.write(XmlUtils.attr(DBKey.KEY_AUTHOR_FAMILY_NAME,
                                           rowData.getString(DBKey.KEY_AUTHOR_FAMILY_NAME)));
                writer.write(XmlUtils.attr(DBKey.KEY_AUTHOR_GIVEN_NAMES,
                                           rowData.getString(DBKey.KEY_AUTHOR_GIVEN_NAMES)));
                writer.write(XmlUtils.attr(DBKey.BOOL_AUTHOR_IS_COMPLETE,
                                           rowData.getBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE)));
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


        try (Cursor cursor = ServiceLocator.getInstance().getSeriesDao().fetchAll()) {
            writer.write('<' + Book.BKEY_SERIES_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_SERIES));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_SERIES);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBKey.PK_ID)));
                writer.write(XmlUtils.attr(DBKey.KEY_SERIES_TITLE,
                                           rowData.getString(DBKey.KEY_SERIES_TITLE)));
                writer.write(XmlUtils.attr(DBKey.BOOL_SERIES_IS_COMPLETE,
                                           rowData.getBoolean(
                                                   DBKey.BOOL_SERIES_IS_COMPLETE)));
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


        try (Cursor cursor = ServiceLocator.getInstance().getPublisherDao().fetchAll()) {
            writer.write('<' + Book.BKEY_PUBLISHER_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_PUBLISHERS));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                writer.write('<' + TAG_PUBLISHER);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBKey.PK_ID)));
                writer.write(XmlUtils.attr(DBKey.KEY_PUBLISHER_NAME,
                                           rowData.getString(DBKey.KEY_PUBLISHER_NAME)));
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

        try (Cursor cursor = ServiceLocator.getInstance().getTocEntryDao().fetchAll()) {
            writer.write('<' + Book.BKEY_TOC_LIST);
            writer.write(XmlUtils.versionAttr(VERSION_TOC_LIST));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                // the tag is written as a single line (no line-feeds)
                writer.write('<' + TAG_TOC_ENTRY);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBKey.PK_ID)));
                writer.write(XmlUtils.attr(DBKey.KEY_TITLE,
                                           rowData.getString(DBKey.KEY_TITLE)));
                writer.write(XmlUtils.attr(DBKey.DATE_FIRST_PUBLICATION,
                                           rowData.getString(DBKey.DATE_FIRST_PUBLICATION)));
                writer.write(">");

                // Write Authors as a list, allowing for future expansion to multiple authors/toc
                writer.write('<' + Book.BKEY_AUTHOR_LIST);
                writer.write(XmlUtils.sizeAttr(1));
                writer.write(">");
                writer.write('<' + DBKey.FK_AUTHOR);
                writer.write(XmlUtils.idAttr(rowData.getLong(DBKey.FK_AUTHOR)));
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

        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
        try (Cursor cursor = bookDao.fetchBooksForExport(utcSinceDateTime)) {

            writer.write('<' + RecordType.Books.getName());
            writer.write(XmlUtils.versionAttr(VERSION_BOOKS));
            writer.write(XmlUtils.sizeAttr(cursor.getCount()));
            writer.write(">\n");

            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                final Book book = Book.from(cursor);
                final String uuid = book.getString(DBKey.KEY_BOOK_UUID);

                String title = book.getString(DBKey.KEY_TITLE);
                // Sanity check: ensure title is non-blank.
                if (title.trim().isEmpty()) {
                    title = context.getString(R.string.unknown_title);
                }

                writer.write('<' + TAG_BOOK);
                writer.write(XmlUtils.idAttr(book.getLong(DBKey.PK_ID)));
                writer.write(XmlUtils.attr(DBKey.KEY_TITLE, title));
                writer.write(XmlUtils.attr(DBKey.KEY_ISBN,
                                           book.getString(DBKey.KEY_ISBN)));
                writer.write(XmlUtils.attr(DBKey.KEY_BOOK_UUID, uuid));
                writer.write(XmlUtils.attr(DBKey.UTC_DATE_ADDED,
                                           book.getString(DBKey.UTC_DATE_ADDED)));
                writer.write(XmlUtils.attr(DBKey.UTC_DATE_LAST_UPDATED,
                                           book.getString(DBKey.UTC_DATE_LAST_UPDATED)));
                writer.write(XmlUtils.attr(DBKey.BOOL_READ,
                                           book.getBoolean(DBKey.BOOL_READ)));
                writer.write(XmlUtils.attr(DBKey.DATE_READ_START,
                                           book.getString(DBKey.DATE_READ_START)));
                writer.write(XmlUtils.attr(DBKey.DATE_READ_END,
                                           book.getString(DBKey.DATE_READ_END)));

                writer.write(XmlUtils.attr(DBKey.KEY_PRINT_RUN,
                                           book.getString(DBKey.KEY_PRINT_RUN)));
                writer.write(XmlUtils.attr(DBKey.DATE_BOOK_PUBLICATION,
                                           book.getString(DBKey.DATE_BOOK_PUBLICATION)));
                writer.write(XmlUtils.attr(DBKey.PRICE_LISTED,
                                           book.getDouble(DBKey.PRICE_LISTED)));
                writer.write(XmlUtils.attr(DBKey.PRICE_LISTED_CURRENCY,
                                           book.getString(DBKey.PRICE_LISTED_CURRENCY)));
                writer.write(XmlUtils.attr(DBKey.DATE_FIRST_PUBLICATION,
                                           book.getString(DBKey.DATE_FIRST_PUBLICATION)));
                writer.write(XmlUtils.attr(DBKey.KEY_FORMAT,
                                           book.getString(DBKey.KEY_FORMAT)));
                writer.write(XmlUtils.attr(DBKey.KEY_COLOR,
                                           book.getString(DBKey.KEY_COLOR)));
                writer.write(XmlUtils.attr(DBKey.KEY_PAGES,
                                           book.getString(DBKey.KEY_PAGES)));
                writer.write(XmlUtils.attr(DBKey.KEY_GENRE,
                                           book.getString(DBKey.KEY_GENRE)));
                writer.write(XmlUtils.attr(DBKey.KEY_LANGUAGE,
                                           book.getString(DBKey.KEY_LANGUAGE)));
                writer.write(XmlUtils.attr(DBKey.BITMASK_TOC,
                                           book.getLong(DBKey.BITMASK_TOC)));

                writer.write(XmlUtils.attr(DBKey.KEY_BOOK_CONDITION,
                                           book.getInt(DBKey.KEY_BOOK_CONDITION)));
                writer.write(XmlUtils.attr(DBKey.KEY_BOOK_CONDITION_COVER,
                                           book.getInt(DBKey.KEY_BOOK_CONDITION_COVER)));

                writer.write(XmlUtils.attr(DBKey.PRICE_PAID,
                                           book.getDouble(DBKey.PRICE_PAID)));
                writer.write(XmlUtils.attr(DBKey.PRICE_PAID_CURRENCY,
                                           book.getString(DBKey.PRICE_PAID_CURRENCY)));
                writer.write(XmlUtils.attr(DBKey.DATE_ACQUIRED,
                                           book.getString(DBKey.DATE_ACQUIRED)));
                writer.write(XmlUtils.attr(DBKey.KEY_LOCATION,
                                           book.getString(DBKey.KEY_LOCATION)));
                writer.write(XmlUtils.attr(DBKey.KEY_RATING,
                                           book.getDouble(DBKey.KEY_RATING)));
                writer.write(XmlUtils.attr(DBKey.BOOL_SIGNED,
                                           book.getBoolean(DBKey.BOOL_SIGNED)));
                writer.write(XmlUtils.attr(DBKey.BITMASK_EDITION,
                                           book.getLong(DBKey.BITMASK_EDITION)));

                writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_BOOK_ID,
                                           book.getInt(DBKey.KEY_CALIBRE_BOOK_ID)));
                writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_BOOK_UUID,
                                           book.getString(DBKey.KEY_CALIBRE_BOOK_UUID)));
                writer.write(XmlUtils.attr(DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT,
                                           book.getString(DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT)));
                writer.write(XmlUtils.attr(DBKey.FK_CALIBRE_LIBRARY,
                                           book.getLong(DBKey.FK_CALIBRE_LIBRARY)));

                // external ID's
                for (final Domain domain : externalIdDomains) {
                    final String key = domain.getName();
                    writer.write(XmlUtils.attr(key, book.getString(key)));
                }
                //NEWTHINGS: adding a new search engine: optional: add engine specific keys

                // cross-linked with the loanee table
                writer.write(XmlUtils.attr(DBKey.KEY_LOANEE, book.getString(DBKey.KEY_LOANEE)));

                // close the start tag
                writer.write(">\n");

                // the text field tags
                writer.write(XmlUtils.tagWithCData(
                        DBKey.KEY_DESCRIPTION, null,
                        book.getString(DBKey.KEY_DESCRIPTION)));
                writer.write(XmlUtils.tagWithCData(
                        DBKey.KEY_PRIVATE_NOTES, null,
                        book.getString(DBKey.KEY_PRIVATE_NOTES)));


                final ArrayList<Author> authors =
                        book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
                if (!authors.isEmpty()) {
                    writer.write('<' + Book.BKEY_AUTHOR_LIST);
                    writer.write(XmlUtils.sizeAttr(authors.size()));
                    writer.write(">");
                    for (final Author author : authors) {
                        writer.write('<' + DBKey.FK_AUTHOR);
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
                        writer.write('<' + DBKey.FK_SERIES);
                        writer.write(XmlUtils.idAttr(series.getId()));
                        writer.write(XmlUtils.attr(DBKey.KEY_BOOK_NUM_IN_SERIES,
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
                        writer.write('<' + DBKey.FK_PUBLISHER);
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
                        writer.write('<' + DBKey.FK_BOOKSHELF);
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
                        writer.write('<' + DBKey.FK_TOC_ENTRY);
                        writer.write(XmlUtils.idAttr(tocEntry.getId()));
                        writer.write("/>");
                    }
                    writer.write("</" + Book.BKEY_TOC_LIST + ">\n");
                }

                writer.write("</" + TAG_BOOK + ">\n");

                results.addBook(book.getId());

                if (collectCoverFilenames) {
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        final File cover = book.getPersistedCoverFile(cIdx);
                        if (cover != null && cover.exists()) {
                            results.addCover(cover.getName());
                        }
                    }
                }

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                    progressListener.publishProgress(delta, title);
                    lastUpdate = now;
                    delta = 0;
                }
            }
            writer.write("</" + RecordType.Books.getName() + ">\n");
        }

        return results;
    }
}
