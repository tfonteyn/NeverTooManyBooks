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
import java.util.Date;
import java.util.Locale;

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
 * <li>{@link ArchiveContainerEntry#BooksCsv}</li>
 * </ul>
 *
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
            + '"' + DBDefinitions.KEY_DATE_LAST_UPDATED + "\","
            + '"' + CSV_COLUMN_AUTHORS + "\","
            + '"' + DBDefinitions.KEY_TITLE + "\","
            + '"' + DBDefinitions.KEY_ISBN + "\","
            + '"' + DBDefinitions.KEY_PUBLISHER + "\","
            + '"' + DBDefinitions.KEY_PRINT_RUN + "\","
            + '"' + DBDefinitions.KEY_DATE_PUBLISHED + "\","
            + '"' + DBDefinitions.KEY_DATE_FIRST_PUBLICATION + "\","
            + '"' + DBDefinitions.KEY_EDITION_BITMASK + "\","
            + '"' + DBDefinitions.KEY_RATING + "\","
            + '"' + DBDefinitions.KEY_BOOKSHELF + "\","
            + '"' + DBDefinitions.KEY_READ + "\","
            + '"' + CSV_COLUMN_SERIES + "\","
            + '"' + DBDefinitions.KEY_PAGES + "\","
            + '"' + DBDefinitions.KEY_PRIVATE_NOTES + "\","

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
            + '"' + DBDefinitions.KEY_DATE_ADDED + "\","
            //NEWTHINGS: add new site specific ID: add column label
            + '"' + DBDefinitions.KEY_EID_LIBRARY_THING + "\","
            + '"' + DBDefinitions.KEY_EID_STRIP_INFO_BE + "\","
            + '"' + DBDefinitions.KEY_EID_OPEN_LIBRARY + "\","
            + '"' + DBDefinitions.KEY_EID_ISFDB + "\","
            + '"' + DBDefinitions.KEY_EID_GOODREADS_BOOK + "\","
            + '"' + DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE + "\""
            + '\n';
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
    private final Date mSince;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    @AnyThread
    public CsvExporter(@NonNull final Context context,
                       final int options,
                       @Nullable final Date since) {

        Locale locale = LocaleUtils.getUserLocale(context);
        mUnknownString = context.getString(R.string.unknown).toUpperCase(locale);

        mOptions = options;
        mSince = since;
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

        // we only support books, return empty results
        boolean writeBooks = (mOptions & Options.BOOKS) != 0;
        if (!writeBooks) {
            return mResults;
        }

        long lastUpdate = 0;

        final StringBuilder sb = new StringBuilder();

        try (Cursor cursor = mDb.fetchBooksForExport(mSince)) {

            int progressMaxCount = progressListener.getMax() + cursor.getCount();
            progressListener.setMax(progressMaxCount);

            // Before we start, make sure we're not cancelled already.
            if (progressListener.isCancelled()) {
                return mResults;
            }

            final RowDataHolder rowData = new CursorRow(cursor);

            writer.write(EXPORT_FIELD_HEADERS);
            while (cursor.moveToNext()) {
                if (progressListener.isCancelled()) {
                    return mResults;
                }

                mResults.booksExported++;
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

                sb.setLength(0);
                sb.append(format(bookId))
                  .append(format(rowData.getString(DBDefinitions.KEY_BOOK_UUID)))
                  .append(format(rowData.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)))
                  .append(format(authors))
                  .append(format(title))
                  .append(format(rowData.getString(DBDefinitions.KEY_ISBN)))
                  .append(format(rowData.getString(DBDefinitions.KEY_PUBLISHER)))
                  .append(format(rowData.getString(DBDefinitions.KEY_PRINT_RUN)))
                  .append(format(rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED)))
                  .append(format(rowData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)))
                  .append(format(rowData.getLong(DBDefinitions.KEY_EDITION_BITMASK)))

                  .append(format(rowData.getDouble(DBDefinitions.KEY_RATING)))
                  .append(format(
                          mBookshelfCoder.encodeList(mDb.getBookshelvesByBookId(bookId))))
                  .append(format(rowData.getInt(DBDefinitions.KEY_READ)))
                  .append(format(mSeriesCoder.encodeList(mDb.getSeriesByBookId(bookId))))
                  .append(format(rowData.getString(DBDefinitions.KEY_PAGES)))
                  .append(format(rowData.getString(DBDefinitions.KEY_PRIVATE_NOTES)))

                  .append(format(rowData.getDouble(DBDefinitions.KEY_PRICE_LISTED)))
                  .append(format(rowData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)))
                  .append(format(rowData.getDouble(DBDefinitions.KEY_PRICE_PAID)))
                  .append(format(rowData.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)))
                  .append(format(rowData.getString(DBDefinitions.KEY_DATE_ACQUIRED)))

                  .append(format(rowData.getLong(DBDefinitions.KEY_TOC_BITMASK)))
                  .append(format(rowData.getString(DBDefinitions.KEY_LOCATION)))
                  .append(format(rowData.getString(DBDefinitions.KEY_READ_START)))
                  .append(format(rowData.getString(DBDefinitions.KEY_READ_END)))
                  .append(format(rowData.getString(DBDefinitions.KEY_FORMAT)))
                  .append(format(rowData.getString(DBDefinitions.KEY_COLOR)))
                  .append(format(rowData.getInt(DBDefinitions.KEY_SIGNED)))
                  .append(format(rowData.getString(DBDefinitions.KEY_LOANEE)))
                  .append(format(mTocCoder.encodeList(mDb.getTocEntryByBook(bookId))))
                  .append(format(rowData.getString(DBDefinitions.KEY_DESCRIPTION)))
                  .append(format(rowData.getString(DBDefinitions.KEY_GENRE)))
                  .append(format(rowData.getString(DBDefinitions.KEY_LANGUAGE)))
                  .append(format(rowData.getString(DBDefinitions.KEY_DATE_ADDED)))

                  //NEWTHINGS: add new site specific ID: add column value
                  .append(format(rowData.getLong(DBDefinitions.KEY_EID_LIBRARY_THING)))
                  .append(format(rowData.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE)))
                  .append(format(rowData.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY)))
                  .append(format(rowData.getLong(DBDefinitions.KEY_EID_ISFDB)))
                  .append(format(rowData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)))
                  .append(format(
                          rowData.getString(DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE)));

                // replace the comma at the end of the line with a '\n'
                sb.replace(sb.length() - 1, sb.length(), "\n");

                writer.write(sb.toString());

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
    private String format(final long cell) {
        return format(String.valueOf(cell));
    }

    @NonNull
    private String format(final double cell) {
        return format(String.valueOf(cell));
    }

    /**
     * Double quote all "'s and remove all newlines.
     *
     * @param cell to format
     *
     * @return The formatted cell enclosed in escaped quotes and a trailing ','
     */
    @NonNull
    private String format(@Nullable final String cell) {
        try {
            if (cell == null || "null".equalsIgnoreCase(cell) || cell.trim().isEmpty()) {
                return "\"\",";
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
                        sb.append("\"\"");
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
            return sb.append("\",").toString();
        } catch (@NonNull final NullPointerException e) {
            return "\"\",";
        }
    }

    @Override
    public void close() {
        mDb.close();
    }
}
