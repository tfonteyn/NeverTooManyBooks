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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
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
    /** Log tag. */
    private static final String TAG = "CsvExporter";
    /** Only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;
    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?).
     * <p>
     * The fields CSV_COLUMN_* are {@link StringList} encoded
     */
    private static final String EXPORT_FIELD_HEADERS =
            "\"" + DBDefinitions.KEY_PK_ID + "\","
            + '"' + DBDefinitions.KEY_BOOK_UUID + "\","
            + '"' + DBDefinitions.KEY_UTC_LAST_UPDATED + "\","
            + '"' + CSV_COLUMN_AUTHORS + "\","
            + '"' + DBDefinitions.KEY_TITLE + "\","
            + '"' + DBDefinitions.KEY_ISBN + "\","
            + '"' + DBDefinitions.KEY_PUBLISHER + "\","
            + '"' + DBDefinitions.KEY_PRINT_RUN + "\","
            + '"' + DBDefinitions.KEY_DATE_PUBLISHED + "\","
            + '"' + DBDefinitions.KEY_DATE_FIRST_PUBLICATION + "\","
            + '"' + DBDefinitions.KEY_EDITION_BITMASK + "\","
            + '"' + DBDefinitions.KEY_RATING + "\","
            + '"' + DBDefinitions.KEY_BOOKSHELF_NAME + "\","
            + '"' + DBDefinitions.KEY_READ + "\","
            + '"' + CSV_COLUMN_SERIES + "\","
            + '"' + DBDefinitions.KEY_PAGES + "\","
            + '"' + DBDefinitions.KEY_PRIVATE_NOTES + "\","
            + '"' + DBDefinitions.KEY_BOOK_CONDITION + "\","
            + '"' + DBDefinitions.KEY_BOOK_CONDITION_COVER + "\","

            + '"' + DBDefinitions.KEY_PRICE_LISTED + "\","
            + '"' + DBDefinitions.KEY_PRICE_LISTED_CURRENCY + "\","
            + '"' + DBDefinitions.KEY_PRICE_PAID + "\","
            + '"' + DBDefinitions.KEY_PRICE_PAID_CURRENCY + "\","
            + '"' + DBDefinitions.KEY_DATE_ACQUIRED + "\","

            + '"' + DBDefinitions.KEY_TOC_BITMASK + "\","
            + '"' + DBDefinitions.KEY_LOCATION + "\","
            + '"' + DBDefinitions.KEY_READ_START + "\","
            + '"' + DBDefinitions.KEY_READ_END + "\","
            + '"' + DBDefinitions.KEY_FORMAT + "\","
            + '"' + DBDefinitions.KEY_COLOR + "\","
            + '"' + DBDefinitions.KEY_SIGNED + "\","
            + '"' + DBDefinitions.KEY_LOANEE + "\","
            + '"' + CSV_COLUMN_TOC + "\","
            + '"' + DBDefinitions.KEY_DESCRIPTION + "\","
            + '"' + DBDefinitions.KEY_GENRE + "\","
            + '"' + DBDefinitions.KEY_LANGUAGE + "\","
            + '"' + DBDefinitions.KEY_UTC_ADDED + "\","
            //NEWTHINGS: add new site specific ID: add column label
            + '"' + DBDefinitions.KEY_EID_LIBRARY_THING + "\","
            + '"' + DBDefinitions.KEY_EID_STRIP_INFO_BE + "\","
            + '"' + DBDefinitions.KEY_EID_OPEN_LIBRARY + "\","
            + '"' + DBDefinitions.KEY_EID_ISFDB + "\","
            + '"' + DBDefinitions.KEY_EID_GOODREADS_BOOK + "\","
            + '"' + DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS + "\""
            + '\n';

    private static final String EMPTY_QUOTED_STRING = "\"\"";

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** cached localized "unknown" string. */
    private final String mUnknownString;
    private final StringList<Author> mAuthorCoder = CsvCoder.getAuthorCoder();
    private final StringList<Series> mSeriesCoder = CsvCoder.getSeriesCoder();
    private final StringList<TocEntry> mTocCoder = CsvCoder.getTocCoder();
    private final StringList<Bookshelf> mBookshelfCoder;
    @NonNull
    private final ExportResults mResults = new ExportResults();
    /** export configuration. */
    private final int mOptions;
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
        mUtcSinceDateTime = utcSinceDateTime;
        mDb = new DAO(TAG);
        mBookshelfCoder = CsvCoder.getBookshelfCoder(BooklistStyle.getDefault(context, mDb));
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

        // we only support books, return empty results, ignoring other options
        boolean writeBooks = (mOptions & Options.BOOKS) != 0;
        if (!writeBooks) {
            return mResults;
        }

        long lastUpdate = 0;

        try (Cursor cursor = mDb.fetchBooksForExport(mUtcSinceDateTime)) {
            // header: the top row with column labels
            writer.write(EXPORT_FIELD_HEADERS);

            int progressMaxCount = progressListener.getMax() + cursor.getCount();
            progressListener.setMax(progressMaxCount);

            final RowDataHolder rowData = new CursorRow(cursor);

            while (cursor.moveToNext() && !progressListener.isCancelled()) {

                long bookId = rowData.getLong(DBDefinitions.KEY_PK_ID);

                String authors = mAuthorCoder.encodeList(mDb.getAuthorsByBookId(bookId));
                // Sanity check: ensure author is non-blank.
                if (authors.trim().isEmpty()) {
                    authors = mUnknownString;
                }

                String title = rowData.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank.
                if (title.trim().isEmpty()) {
                    title = mUnknownString;
                }

                // it's a buffered writer, no need to first StringBuilder the line.
                writer.write(encode(bookId));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_BOOK_UUID)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_UTC_LAST_UPDATED)));
                writer.write(",");
                writer.write(encode(authors));
                writer.write(",");
                writer.write(encode(title));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_ISBN)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_PUBLISHER)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_PRINT_RUN)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
                writer.write(",");
                writer.write(encode(rowData.getLong(DBDefinitions.KEY_EDITION_BITMASK)));
                writer.write(",");
                writer.write(encode(rowData.getDouble(DBDefinitions.KEY_RATING)));
                writer.write(",");
                writer.write(
                        encode(mBookshelfCoder.encodeList(mDb.getBookshelvesByBookId(bookId))));
                writer.write(",");
                writer.write(encode(rowData.getInt(DBDefinitions.KEY_READ)));
                writer.write(",");
                writer.write(encode(mSeriesCoder.encodeList(mDb.getSeriesByBookId(bookId))));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_PAGES)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_PRIVATE_NOTES)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_BOOK_CONDITION)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_BOOK_CONDITION_COVER)));
                writer.write(",");
                writer.write(encode(rowData.getDouble(DBDefinitions.KEY_PRICE_LISTED)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)));
                writer.write(",");
                writer.write(encode(rowData.getDouble(DBDefinitions.KEY_PRICE_PAID)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_DATE_ACQUIRED)));
                writer.write(",");
                writer.write(encode(rowData.getLong(DBDefinitions.KEY_TOC_BITMASK)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_LOCATION)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_READ_START)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_READ_END)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_FORMAT)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_COLOR)));
                writer.write(",");
                writer.write(encode(rowData.getInt(DBDefinitions.KEY_SIGNED)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_LOANEE)));
                writer.write(",");
                writer.write(encode(mTocCoder.encodeList(mDb.getTocEntryByBook(bookId))));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_DESCRIPTION)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_GENRE)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_LANGUAGE)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_UTC_ADDED)));
                writer.write(",");

                //NEWTHINGS: add new site specific ID: add column value
                writer.write(encode(rowData.getLong(DBDefinitions.KEY_EID_LIBRARY_THING)));
                writer.write(",");
                writer.write(encode(rowData.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE)));
                writer.write(",");
                writer.write(encode(rowData.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY)));
                writer.write(",");
                writer.write(encode(rowData.getLong(DBDefinitions.KEY_EID_ISFDB)));
                writer.write(",");
                writer.write(encode(rowData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)));
                writer.write(",");
                writer.write(
                        encode(rowData.getString(DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS)));
                writer.write("\n");

                mResults.booksExported++;

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
                    progressListener.onProgress(mResults.booksExported, title);
                    lastUpdate = now;
                }
            }
        }

        return mResults;
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
            int endPos = cell.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                char c = cell.charAt(pos);
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
            return sb.append("\"").toString();
        } catch (@NonNull final NullPointerException e) {
            return EMPTY_QUOTED_STRING;
        }
    }

    @Override
    public void close() {
        mDb.close();
    }
}
