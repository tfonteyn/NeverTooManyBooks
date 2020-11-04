/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.AuthorCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookshelfCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.PublisherCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.SeriesCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.TocEntryCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * <ul>Supports:
 *      <li>{@link ArchiveContainerEntry#BooksCsv}</li>
 * </ul>
 * <p>
 * All books will have full toc, author, series and bookshelf information.
 * <p>
 * Support {@link Options#BOOKS} only (and does in fact simply disregard all Options flags).
 */
public class CsvExporter
        implements Exporter {

    /** The format version of this exporter. */
    public static final int VERSION = 1;

    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_TOC = "anthology_titles";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_SERIES = "series_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_AUTHORS = "author_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_PUBLISHERS = "publisher";
    /** Log tag. */
    private static final String TAG = "CsvExporter";

    private static final String COMMA = ",";
    private static final String EMPTY_QUOTED_STRING = "\"\"";

    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?).
     * <p>
     * The fields CSV_COLUMN_* are {@link StringList} encoded
     * <p>
     * External id columns will be added to the end in {@link #getFieldHeaders} (List)}.
     */
    private static final String EXPORT_FIELD_HEADERS_BASE =
            '"' + DBDefinitions.KEY_PK_ID + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOK_UUID + '"'
            + COMMA + '"' + DBDefinitions.KEY_UTC_LAST_UPDATED + '"'
            + COMMA + '"' + CSV_COLUMN_AUTHORS + '"'
            + COMMA + '"' + DBDefinitions.KEY_TITLE + '"'
            + COMMA + '"' + DBDefinitions.KEY_ISBN + '"'
            + COMMA + '"' + CSV_COLUMN_PUBLISHERS + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRINT_RUN + '"'
            + COMMA + '"' + DBDefinitions.KEY_DATE_PUBLISHED + '"'
            + COMMA + '"' + DBDefinitions.KEY_DATE_FIRST_PUBLICATION + '"'
            + COMMA + '"' + DBDefinitions.KEY_EDITION_BITMASK + '"'
            + COMMA + '"' + DBDefinitions.KEY_RATING + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOKSHELF_NAME + '"'
            + COMMA + '"' + DBDefinitions.KEY_READ + '"'
            + COMMA + '"' + CSV_COLUMN_SERIES + '"'
            + COMMA + '"' + DBDefinitions.KEY_PAGES + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRIVATE_NOTES + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOK_CONDITION + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOK_CONDITION_COVER + '"'

            + COMMA + '"' + DBDefinitions.KEY_PRICE_LISTED + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRICE_LISTED_CURRENCY + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRICE_PAID + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRICE_PAID_CURRENCY + '"'
            + COMMA + '"' + DBDefinitions.KEY_DATE_ACQUIRED + '"'

            + COMMA + '"' + DBDefinitions.KEY_TOC_BITMASK + '"'
            + COMMA + '"' + DBDefinitions.KEY_LOCATION + '"'
            + COMMA + '"' + DBDefinitions.KEY_READ_START + '"'
            + COMMA + '"' + DBDefinitions.KEY_READ_END + '"'
            + COMMA + '"' + DBDefinitions.KEY_FORMAT + '"'
            + COMMA + '"' + DBDefinitions.KEY_COLOR + '"'
            + COMMA + '"' + DBDefinitions.KEY_SIGNED + '"'
            + COMMA + '"' + DBDefinitions.KEY_LOANEE + '"'
            + COMMA + '"' + CSV_COLUMN_TOC + '"'
            + COMMA + '"' + DBDefinitions.KEY_DESCRIPTION + '"'
            + COMMA + '"' + DBDefinitions.KEY_GENRE + '"'
            + COMMA + '"' + DBDefinitions.KEY_LANGUAGE + '"'
            + COMMA + '"' + DBDefinitions.KEY_UTC_ADDED + '"';

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** cached localized "unknown" string. */
    private final String mUnknownNameString;
    private final StringList<Author> mAuthorCoder = new StringList<>(new AuthorCoder());
    private final StringList<Series> mSeriesCoder = new StringList<>(new SeriesCoder());
    private final StringList<Publisher> mPublisherCoder = new StringList<>(new PublisherCoder());
    private final StringList<TocEntry> mTocCoder = new StringList<>(new TocEntryCoder());
    private final StringList<Bookshelf> mBookshelfCoder;
    @NonNull
    private final ExportResults mResults = new ExportResults();
    /** export configuration. */
    private final int mOptions;
    private final boolean mCollectCoverFilenames;

    @Nullable
    private final LocalDateTime mUtcSinceDateTime;

    /**
     * Constructor.
     *
     * @param context          Current context
     * @param options          {@link Options} flags
     * @param utcSinceDateTime (optional) UTC based date to select only books modified or added
     *                         since.
     */
    @AnyThread
    public CsvExporter(@NonNull final Context context,
                       final int options,
                       @Nullable final LocalDateTime utcSinceDateTime) {

        mOptions = options;
        mCollectCoverFilenames = (mOptions & Options.COVERS) != 0;
        mUtcSinceDateTime = utcSinceDateTime;

        mDb = new DAO(TAG);
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
        mUnknownNameString = context.getString(R.string.unknownName).toUpperCase(userLocale);

        mBookshelfCoder = new StringList<>(
                new BookshelfCoder(StyleDAO.getDefault(context, mDb)));
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        final boolean writeBooks = (mOptions & Options.BOOKS) != 0;
        // Sanity check: if we don't do books, return empty results
        if (!writeBooks) {
            return mResults;
        }

        int delta = 0;
        long lastUpdate = 0;

        final List<Domain> externalIdDomains = SearchEngineRegistry.getExternalIdDomains();

        try (Cursor cursor = mDb.fetchBooksForExport(mUtcSinceDateTime)) {
            // row 0 with the column labels
            writer.write(getFieldHeaders(externalIdDomains));

            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                final Book book = Book.from(cursor, mDb);
                final String uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);

                String title = book.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank.
                if (title.trim().isEmpty()) {
                    title = mUnknownNameString;
                }

                String authors = mAuthorCoder.encodeList(
                        book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST));
                // Sanity check: ensure author is non-blank.
                if (authors.trim().isEmpty()) {
                    authors = mUnknownNameString;
                }

                // it's a buffered writer, no need to first StringBuilder the line.
                writer.write(encode(book.getLong(DBDefinitions.KEY_PK_ID)));
                writer.write(COMMA);
                writer.write(encode(uuid));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED)));
                writer.write(COMMA);
                writer.write(encode(authors));
                writer.write(COMMA);
                writer.write(encode(title));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_ISBN)));
                writer.write(COMMA);
                writer.write(encode(mPublisherCoder.encodeList(
                        book.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST))));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_PRINT_RUN)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_DATE_PUBLISHED)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
                writer.write(COMMA);
                writer.write(encode(book.getLong(DBDefinitions.KEY_EDITION_BITMASK)));
                writer.write(COMMA);
                writer.write(encode(book.getDouble(DBDefinitions.KEY_RATING)));
                writer.write(COMMA);
                writer.write(encode(mBookshelfCoder.encodeList(
                        book.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST))));
                writer.write(COMMA);
                writer.write(encode(book.getInt(DBDefinitions.KEY_READ)));
                writer.write(COMMA);
                writer.write(encode(mSeriesCoder.encodeList(
                        book.getParcelableArrayList(Book.BKEY_SERIES_LIST))));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_PAGES)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_PRIVATE_NOTES)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_BOOK_CONDITION)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_BOOK_CONDITION_COVER)));
                writer.write(COMMA);
                writer.write(encode(book.getDouble(DBDefinitions.KEY_PRICE_LISTED)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)));
                writer.write(COMMA);
                writer.write(encode(book.getDouble(DBDefinitions.KEY_PRICE_PAID)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_DATE_ACQUIRED)));
                writer.write(COMMA);
                writer.write(encode(book.getLong(DBDefinitions.KEY_TOC_BITMASK)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_LOCATION)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_READ_START)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_READ_END)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_FORMAT)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_COLOR)));
                writer.write(COMMA);
                writer.write(encode(book.getInt(DBDefinitions.KEY_SIGNED)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_LOANEE)));
                writer.write(COMMA);
                writer.write(encode(mTocCoder.encodeList(
                        book.getParcelableArrayList(Book.BKEY_TOC_LIST))));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_DESCRIPTION)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_GENRE)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_LANGUAGE)));
                writer.write(COMMA);
                writer.write(encode(book.getString(DBDefinitions.KEY_UTC_ADDED)));

                // external ID's
                for (final Domain domain : externalIdDomains) {
                    writer.write(COMMA);
                    writer.write(encode(book.getString(domain.getName())));
                }
                //NEWTHINGS: adding a new search engine: optional: add engine specific keys
                writer.write(COMMA);
                writer.write(encode(
                        book.getString(DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE)));

                writer.write("\n");

                mResults.addBook(book.getId());

                if (mCollectCoverFilenames) {
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        final File cover = book.getUuidCoverFile(context, cIdx);
                        if (cover != null && cover.exists()) {
                            mResults.addCover(cover.getName());
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
        }

        return mResults;
    }

    @NonNull
    private String getFieldHeaders(final Iterable<Domain> externalIdDomains) {
        final StringBuilder sb = new StringBuilder(EXPORT_FIELD_HEADERS_BASE);
        for (final Domain domain : externalIdDomains) {
            sb.append(COMMA)
              .append('"').append(domain.getName()).append('"');
        }
        //NEWTHINGS: adding a new search engine: optional: add engine specific keys
        sb.append(COMMA).append('"')
          .append(DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE).append('"');
        return sb.append('\n').toString();
    }

    @NonNull
    private String encode(final long cell) {
        return encode(String.valueOf(cell));
    }

    @NonNull
    private String encode(final double cell) {
        return encode(String.valueOf(cell));
    }

    /**
     * Double quote all "'s and remove all newlines.
     *
     * @param cell to encode
     *
     * @return The encoded cell enclosed in escaped quotes
     */
    @NonNull
    private String encode(@Nullable final String cell) {

        try {
            if (cell == null || "null".equalsIgnoreCase(cell) || cell.trim().isEmpty()) {
                return EMPTY_QUOTED_STRING;
            }

            final StringBuilder sb = new StringBuilder("\"");
            final int endPos = cell.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                final char c = cell.charAt(pos);
                switch (c) {
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '"':
                        // quotes are escaped by doubling them
                        sb.append(EMPTY_QUOTED_STRING);
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
            return sb.append('"').toString();
        } catch (@NonNull final NullPointerException e) {
            return EMPTY_QUOTED_STRING;
        }
    }

    @Override
    public void close() {
        mDb.close();
    }
}
