/*
 * @Copyright 2019 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * Implementation of Exporter that creates a CSV file.
 * <p>
 * 2019-02-01 : no longer exporting bookshelf ID's. They were not used during csv import anyhow.
 * Use xml export if you want ID's.
 */
public class CsvExporter
        implements Exporter {

    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_TOC = "anthology_titles";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_SERIES = "series_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_AUTHORS = "author_details";

    private static final int BUFFER_SIZE = 32768;

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
            + '"' + DBDefinitions.KEY_SIGNED + "\","
            + '"' + DBDefinitions.KEY_LOANEE + "\","
            + '"' + CSV_COLUMN_TOC + "\","
            + '"' + DBDefinitions.KEY_DESCRIPTION + "\","
            + '"' + DBDefinitions.KEY_GENRE + "\","
            + '"' + DBDefinitions.KEY_LANGUAGE + "\","
            + '"' + DBDefinitions.KEY_DATE_ADDED + "\","
            + '"' + DBDefinitions.KEY_LIBRARY_THING_ID + "\","
            + '"' + DBDefinitions.KEY_OPEN_LIBRARY_ID + "\","
            + '"' + DBDefinitions.KEY_ISFDB_ID + "\","
            + '"' + DBDefinitions.KEY_GOODREADS_BOOK_ID + "\","
            + '"' + DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE + "\""
            + '\n';


    @NonNull
    private final ExportHelper mExportHelper;
    /** cached localized "unknown" string. */
    private final String mUnknownString;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param exportHelper {@link ExportHelper#EXPORT_SINCE} and
     *                     {@link ExportHelper#getDateFrom} are respected.
     *                     Other flags are ignored, as this method only
     *                     handles {@link ExportHelper#BOOK_CSV} anyhow.
     */
    public CsvExporter(@NonNull final Context context,
                       @NonNull final ExportHelper exportHelper) {
        mUnknownString = context.getString(R.string.unknown);
        mExportHelper = exportHelper;
        mExportHelper.validate();
    }

    /**
     * Get the total number of books exported.
     *
     * @param os               Stream for writing data
     * @param progressListener Progress and cancellation interface
     *
     * @return total number of books exported, or 0 upon cancellation
     *
     * @throws IOException on failures
     */
    @Override
    @WorkerThread
    public Results doBooks(@NonNull final OutputStream os,
                           @NonNull final ProgressListener progressListener)
            throws IOException {

        Results results = new Results();

        long lastUpdate = 0;
        final StringBuilder row = new StringBuilder();

        try (DAO db = new DAO();
             BookCursor bookCursor = db.fetchBooksForExport(mExportHelper.getDateFrom());
             OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw, BUFFER_SIZE)) {

            int progressMaxCount = progressListener.getMax() + bookCursor.getCount();
            progressListener.setMax(progressMaxCount);

            // Before we start, make sure we're not cancelled already.
            if (progressListener.isCancelled()) {
                return results;
            }

            out.write(EXPORT_FIELD_HEADERS);
            while (bookCursor.moveToNext()) {
                if (progressListener.isCancelled()) {
                    return results;
                }

                results.booksExported++;
                long bookId = bookCursor.getId();

                String authorStringList = CsvCoder.getAuthorCoder()
                                                  .encodeList(db.getAuthorsByBookId(bookId));

                // Sanity check: ensure author is non-blank. This HAPPENS.
                // Probably due to constraint failures.
                if (authorStringList.trim().isEmpty()) {
                    authorStringList = mUnknownString + ", " + mUnknownString;
                }

                String title = bookCursor.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank. This has not happened yet, but we
                // know if does for author, so completeness suggests making sure all 'required'
                // fields are non-blank.
                if (title.trim().isEmpty()) {
                    title = mUnknownString;
                }

                row.setLength(0);
                row.append(format(bookId))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_BOOK_UUID)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)))
                   .append(format(authorStringList))
                   .append(format(title))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_ISBN)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_PUBLISHER)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_DATE_PUBLISHED)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)))
                   .append(format(bookCursor.getLong(DBDefinitions.KEY_EDITION_BITMASK)))

                   .append(format(bookCursor.getDouble(DBDefinitions.KEY_RATING)))
                   .append(format(CsvCoder.getBookshelfCoder()
                                          .encodeList(db.getBookshelvesByBookId(bookId))))
                   .append(format(bookCursor.getInt(DBDefinitions.KEY_READ)))
                   .append(format(CsvCoder.getSeriesCoder()
                                          .encodeList(db.getSeriesByBookId(bookId))))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_PAGES)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_PRIVATE_NOTES)))

                   .append(format(bookCursor.getString(DBDefinitions.KEY_PRICE_LISTED)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_PRICE_PAID)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_DATE_ACQUIRED)))

                   .append(format(bookCursor.getLong(DBDefinitions.KEY_TOC_BITMASK)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_LOCATION)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_READ_START)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_READ_END)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_FORMAT)))
                   .append(format(bookCursor.getInt(DBDefinitions.KEY_SIGNED)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_LOANEE)))
                   .append(format(CsvCoder.getTocCoder()
                                          .encodeList(db.getTocEntryByBook(bookId))))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_DESCRIPTION)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_GENRE)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_LANGUAGE)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_DATE_ADDED)))

                   .append(format(bookCursor.getLong(DBDefinitions.KEY_LIBRARY_THING_ID)))
                   .append(format(bookCursor.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID)))
                   .append(format(bookCursor.getLong(DBDefinitions.KEY_ISFDB_ID)))
                   .append(format(bookCursor.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID)))
                   .append(format(
                           bookCursor.getString(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE)));

                // replace the comma at the end of the line with a '\n'
                row.replace(row.length() - 1, row.length(), "\n");

                out.write(row.toString());

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
                    progressListener.onProgress(results.booksExported, title);
                    lastUpdate = now;
                }
            }
        } finally {
            Logger.info(App.getAppContext(), this,
                        "doBooks", "results=" + results);
        }
        return results;
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

    @SuppressWarnings("RedundantThrows")
    @Override
    public void close()
            throws IOException {
    }
}
