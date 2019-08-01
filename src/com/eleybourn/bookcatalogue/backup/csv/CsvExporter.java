/*
 * @copyright 2013 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.MappedCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.StringList;

/**
 * Implementation of Exporter that creates a CSV file.
 * <p>
 * 2019-02-01 : no longer exporting bookshelf ID's. They were not used during csv import anyhow.
 * Use xml export if you want ID's.
 *
 * @author pjw
 */
public class CsvExporter
        implements Exporter {

    /** standard export file. */
    public static final String EXPORT_FILE_NAME = "export.csv";
    /** standard temp export file, first we write here, then rename to csv. */
    static final String EXPORT_TEMP_FILE_NAME = "export.tmp";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_TOC = "anthology_titles";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_SERIES = "series_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    static final String CSV_COLUMN_AUTHORS = "author_details";
    private static final int BUFFER_SIZE = 32768;
    /** pattern we look for to rename/keep older copies. */
    private static final String EXPORT_CSV_FILES_PATTERN = "export.%s.csv";

    /** backup copies to keep. */
    private static final int COPIES = 5;
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
                    + '"' + DBDefinitions.KEY_NOTES + "\","

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
    private final ExportOptions mSettings;
    private final String mUnknownString;

    /**
     * Constructor.
     *
     * @param context  Current context for accessing resources.
     * @param settings {@link ExportOptions#file} is not used, as we must support writing
     *                 to a stream. {@link ExportOptions#EXPORT_SINCE} and
     *                 {@link ExportOptions#dateFrom} are respected.
     *                 Other flags are ignored, as this method only
     *                 handles {@link ExportOptions#BOOK_CSV} anyhow.
     */
    public CsvExporter(@NonNull final Context context,
                       @NonNull final ExportOptions settings) {

        mUnknownString = context.getString(R.string.unknown);

        mSettings = settings;
        settings.validate();
    }

    /**
     * At the end of a successful export, rename the temp file to {@link #EXPORT_FILE_NAME}.
     *
     * @param tempFile to rename
     */
    static void renameFiles(@NonNull final File tempFile) {
        File fLast = StorageUtils.getFile(String.format(EXPORT_CSV_FILES_PATTERN, COPIES));
        StorageUtils.deleteFile(fLast);

        for (int i = COPIES - 1; i > 0; i--) {
            final File fCurr = StorageUtils.getFile(String.format(EXPORT_CSV_FILES_PATTERN, i));
            StorageUtils.renameFile(fCurr, fLast);
            fLast = fCurr;
        }
        final File export = StorageUtils.getFile(EXPORT_FILE_NAME);
        StorageUtils.renameFile(export, fLast);
        StorageUtils.renameFile(tempFile, export);
    }

    @Override
    @WorkerThread
    public int doBooks(@NonNull final OutputStream outputStream,
                       @NonNull final ProgressListener listener,
                       final boolean includeCoverCount)
            throws IOException {

        // Display startup message
        listener.onProgress(0, R.string.progress_msg_export_starting);

        long lastUpdate = 0;
        int numberOfBooksExported = 0;
        final StringBuilder row = new StringBuilder();

        try (DAO db = new DAO();
             BookCursor bookCursor = db.fetchBooksForExport(mSettings.dateFrom);
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), BUFFER_SIZE)) {

            final MappedCursorRow cursorRow = bookCursor.getCursorRow();
            int progressMaxCount = bookCursor.getCount();

            if (listener.isCancelled()) {
                return 0;
            }

            // assume each book will have a cover.
            if (includeCoverCount) {
                progressMaxCount *= 2;
            }
            listener.setMax(progressMaxCount);

            out.write(EXPORT_FIELD_HEADERS);

            while (bookCursor.moveToNext() && !listener.isCancelled()) {
                numberOfBooksExported++;
                long bookId = bookCursor.getId();

                String authorStringList = StringList.getAuthorCoder()
                                                    .encode(db.getAuthorsByBookId(bookId));

                // Sanity check: ensure author is non-blank. This HAPPENS.
                // Probably due to constraint failures.
                if (authorStringList.trim().isEmpty()) {
                    authorStringList = mUnknownString + ", " + mUnknownString;
                }

                String title = cursorRow.getString(DBDefinitions.KEY_TITLE);
                // Sanity check: ensure title is non-blank. This has not happened yet, but we
                // know if does for author, so completeness suggests making sure all 'required'
                // fields are non-blank.
                if (title.trim().isEmpty()) {
                    title = mUnknownString;
                }

                row.setLength(0);
                row.append(format(bookId))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_BOOK_UUID)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_DATE_LAST_UPDATED)))
                   .append(format(authorStringList))
                   .append(format(title))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_ISBN)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_PUBLISHER)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_DATE_PUBLISHED)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)))
                   .append(format(cursorRow.getLong(DBDefinitions.KEY_EDITION_BITMASK)))

                   .append(format(cursorRow.getDouble(DBDefinitions.KEY_RATING)))
                   .append(format(StringList.getBookshelfCoder()
                                            .encode(db.getBookshelvesByBookId(bookId))))
                   .append(format(cursorRow.getInt(DBDefinitions.KEY_READ)))
                   .append(format(StringList.getSeriesCoder()
                                            .encode(db.getSeriesByBookId(bookId))))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_PAGES)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_NOTES)))

                   .append(format(cursorRow.getString(DBDefinitions.KEY_PRICE_LISTED)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_PRICE_PAID)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_DATE_ACQUIRED)))

                   .append(format(cursorRow.getLong(DBDefinitions.KEY_TOC_BITMASK)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_LOCATION)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_READ_START)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_READ_END)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_FORMAT)))
                   .append(format(cursorRow.getInt(DBDefinitions.KEY_SIGNED)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_LOANEE)))
                   .append(format(StringList.getTocCoder()
                                            .encode(db.getTocEntryByBook(bookId))))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_DESCRIPTION)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_GENRE)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_LANGUAGE)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_DATE_ADDED)))

                   .append(format(cursorRow.getLong(DBDefinitions.KEY_LIBRARY_THING_ID)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID)))
                   .append(format(cursorRow.getLong(DBDefinitions.KEY_ISFDB_ID)))
                   .append(format(cursorRow.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID)))
                   .append(format(cursorRow.getString(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE)));

                // replace the comma at the end of the line with a '\n'
                row.replace(row.length() - 1, row.length(), "\n");

                out.write(row.toString());

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200) {
                    listener.onProgress(numberOfBooksExported, title);
                    lastUpdate = now;
                }
            }
        } finally {
            Logger.info(this, "doBooks", "books=" + numberOfBooksExported);
        }
        return numberOfBooksExported;
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

}
