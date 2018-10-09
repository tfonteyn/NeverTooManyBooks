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
package com.eleybourn.bookcatalogue.backup;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Date;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Implementation of Exporter that creates a CSV file.
 *
 * @author pjw
 */
public class CsvExporter implements Exporter {
    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;

    /** standard export file */
    public static final String EXPORT_FILE_NAME = "export.csv";
    /** standard temp export file, first we write here, then rename to csv */
    static final String EXPORT_TEMP_FILE_NAME = "export.tmp";
    /** pattern we look for to rename/keep older copies */
    private static final String EXPORT_CSV_FILES_PATTERN = "export.%s.csv";
    /** backup copies to keep */
    private static final int COPIES = 5;
    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?)
     *
     * The fields *._DETAILS are string encoded
     */
    private final String EXPORT_FIELD_HEADERS =
            '"' + DOM_ID.name + "\"," +
                    '"' + UniqueId.BKEY_AUTHOR_DETAILS + "\"," +
                    '"' + DOM_TITLE + "\"," +
                    '"' + DOM_BOOK_ISBN + "\"," +
                    '"' + DOM_BOOK_PUBLISHER + "\"," +
                    '"' + DOM_BOOK_DATE_PUBLISHED + "\"," +
                    '"' + DOM_FIRST_PUBLICATION + "\"," +
                    '"' + DOM_BOOK_RATING + "\"," +
                    '"' + "bookshelf_id\"," + // DOM_BOOKSHELF_ID but we misnamed it originally
                    '"' + DOM_BOOKSHELF + "\"," +
                    '"' + DOM_BOOK_READ + "\"," +
                    '"' + UniqueId.BKEY_SERIES_DETAILS + "\"," +
                    '"' + DOM_BOOK_PAGES + "\"," +
                    '"' + DOM_BOOK_NOTES + "\"," +
                    '"' + DOM_BOOK_LIST_PRICE + "\"," +
                    '"' + DOM_BOOK_ANTHOLOGY_MASK + "\"," +
                    '"' + DOM_BOOK_LOCATION + "\"," +
                    '"' + DOM_BOOK_READ_START + "\"," +
                    '"' + DOM_BOOK_READ_END + "\"," +
                    '"' + DOM_BOOK_FORMAT + "\"," +
                    '"' + DOM_BOOK_SIGNED + "\"," +
                    '"' + DOM_LOANED_TO + "\"," +
                    '"' + UniqueId.BKEY_ANTHOLOGY_DETAILS + "\"," +
                    '"' + DOM_DESCRIPTION + "\"," +
                    '"' + DOM_BOOK_GENRE + "\"," +
                    '"' + DOM_BOOK_LANGUAGE + "\"," +
                    '"' + DOM_BOOK_DATE_ADDED + "\"," +
                    '"' + DOM_BOOK_GOODREADS_BOOK_ID + "\"," +
                    '"' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "\"," +
                    '"' + DOM_LAST_UPDATE_DATE + "\"," +
                    '"' + DOM_BOOK_UUID + "\"," +
                    "\n";
    private String mLastError;

    void renameFiles(final @NonNull File temp) {
        File fLast = StorageUtils.getFile(String.format(EXPORT_CSV_FILES_PATTERN, COPIES));
        StorageUtils.deleteFile(fLast);

        for (int i = COPIES - 1; i > 0; i--) {
            final File fCurr = StorageUtils.getFile(String.format(EXPORT_CSV_FILES_PATTERN, i));
            StorageUtils.renameFile(fCurr, fLast);
            fLast = fCurr;
        }
        final File export = StorageUtils.getFile(EXPORT_FILE_NAME);
        StorageUtils.renameFile(export, fLast);
        StorageUtils.renameFile(temp, export);
    }

    public boolean export(@NonNull final OutputStream outputStream,
                          @NonNull final Exporter.ExportListener listener,
                          final int backupFlags,
                          @Nullable Date since) throws IOException {
        final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown);
        final String AUTHOR = BookCatalogueApp.getResourceString(R.string.author);

        if (StorageUtils.isWriteProtected()) {
            mLastError = "Export Failed - Could not write to Storage";
            return false;
        }

        /* ENHANCE: Handle flags! */
        // Fix the 'since' date, if required
        if ((backupFlags & Exporter.EXPORT_SINCE) != 0) {
            if (since == null) {
                mLastError = "Export Failed - 'since' is null";
                return false;
            }
        } else {
            since = null;
        }

        // Display startup message
        listener.onProgress(BookCatalogueApp.getResourceString(R.string.export_starting_ellipsis), 0);
        boolean displayingStartupMessage = true;

        long lastUpdate = 0;
        int num = 0;
        final StringBuilder row = new StringBuilder();

        final CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();

        try (BooksCursor bookCursor = db.exportBooks(since);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE)) {

            final BookRowView rowView = bookCursor.getRowView();
            final int totalBooks = bookCursor.getCount();

            if (listener.isCancelled()) {
                return false;
            }

            listener.setMax(totalBooks);
            out.write(EXPORT_FIELD_HEADERS);

            while (bookCursor.moveToNext() && !listener.isCancelled()) {
                num++;
                long bookId = bookCursor.getLong(bookCursor.getColumnIndexOrThrow(DOM_ID.name));

                String authorDetails = ArrayUtils.getAuthorUtils().encodeList(ArrayUtils.MULTI_STRING_SEPARATOR, db.getBookAuthorList(bookId));
                // Sanity check: ensure author is non-blank. This HAPPENS. Probably due to constraint failures.
                if (authorDetails.trim().isEmpty()) {
                    authorDetails = AUTHOR + ", " + UNKNOWN;
                }

                String title = rowView.getTitle();
                // Sanity check: ensure title is non-blank. This has not happened yet, but we
                // know if does for author, so completeness suggests making sure all 'required'
                // fields are non-blank.
                if (title == null || title.trim().isEmpty()) {
                    title = UNKNOWN;
                }

                // the selected bookshelves: two CSV columns with CSV id's + CSV names
                StringBuilder bookshelves_id_text = new StringBuilder();
                StringBuilder bookshelves_name_text = new StringBuilder();
                try (Cursor bookshelves = db.fetchAllBookshelvesByBook(bookId)) {
                    int bsIdCol = bookshelves.getColumnIndex(DOM_ID.name);
                    int bsCol = bookshelves.getColumnIndex(DOM_BOOKSHELF.name);
                    while (bookshelves.moveToNext()) {
                        bookshelves_id_text
                                .append(bookshelves.getString(bsIdCol))
                                .append(Bookshelf.SEPARATOR);
                        bookshelves_name_text
                                .append(ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, bookshelves.getString(bsCol)))
                                .append(Bookshelf.SEPARATOR);
                    }
                }

                row.setLength(0);
                row.append(formatCell(bookId))
                        .append(formatCell(authorDetails))
                        .append(formatCell(title))
                        .append(formatCell(rowView.getIsbn()))
                        .append(formatCell(rowView.getPublisherName()))
                        .append(formatCell(rowView.getDatePublished()))
                        .append(formatCell(rowView.getFirstPublication()))
                        .append(formatCell(rowView.getRating()))
                        .append(formatCell(bookshelves_id_text.toString()))
                        .append(formatCell(bookshelves_name_text.toString()))
                        .append(formatCell(rowView.getRead()))
                        .append(formatCell(ArrayUtils.getSeriesUtils().encodeList(ArrayUtils.MULTI_STRING_SEPARATOR, db.getBookSeriesList(bookId))))
                        .append(formatCell(rowView.getPages()))
                        .append(formatCell(rowView.getNotes()))
                        .append(formatCell(rowView.getListPrice()))
                        .append(formatCell(rowView.getAnthologyMask()))
                        .append(formatCell(rowView.getLocation()))
                        .append(formatCell(rowView.getReadStart()))
                        .append(formatCell(rowView.getReadEnd()))
                        .append(formatCell(rowView.getFormat()))
                        .append(formatCell(rowView.getSigned()))
                        .append(formatCell(rowView.getLoanedTo()))
                        .append(formatCell(getAnthologyTitlesForExport(db, bookId, rowView)))
                        .append(formatCell(rowView.getDescription()))
                        .append(formatCell(rowView.getGenre()))
                        .append(formatCell(rowView.getLanguage()))
                        .append(formatCell(rowView.getDateAdded()))
                        .append(formatCell(rowView.getGoodreadsBookId()))
                        .append(formatCell(rowView.getDateLastSyncedWithGoodReads()))
                        .append(formatCell(rowView.getDateLastUpdated()))
                        .append(formatCell(rowView.getBookUuid()))
                        .append("\n");
                out.write(row.toString());

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200) {
                    if (displayingStartupMessage) {
                        listener.onProgress("", 0);
                        displayingStartupMessage = false;
                    }
                    listener.onProgress(title, num);
                    lastUpdate = now;
                }
            }
        } finally {
            System.out.println("Books Exported: " + num);
            if (displayingStartupMessage) {
                try {
                    listener.onProgress("", 0);
                } catch (Exception ignored) {
                }
            }
            db.close();
        }
        return true;
    }

    /**
     * // V82: Giants In The Sky * Blish, James|We, The Marauders * Silverberg, Robert|
     * // V83: Giants In The Sky (1952) * Blish, James|We, The Marauders (1958) * Silverberg, Robert|
     */
    @NonNull
    private String getAnthologyTitlesForExport(final CatalogueDBAdapter db, final long bookId, final BookRowView rowView) {
        StringBuilder anthology_titles = new StringBuilder();
        if (rowView.getAnthologyMask() != 0) {
            try (Cursor titles = db.fetchAnthologyTitlesByBookId(bookId)) {
                final int authorCol = titles.getColumnIndexOrThrow(DOM_AUTHOR_NAME.name);
                final int titleCol = titles.getColumnIndexOrThrow(DOM_TITLE.name);
                final int pubDateCol = titles.getColumnIndexOrThrow(DOM_FIRST_PUBLICATION.name);

                while (titles.moveToNext()) {
                    // we store the whole date, not just the year.. for future compatibility
                    String year = titles.getString(pubDateCol);
                    if (year != null && !year.isEmpty()) {
                        // start with space
                        year = " (" + year + ")";
                    } else {
                        year = "";
                    }
                    anthology_titles
                            .append(titles.getString(titleCol))
                            .append(year)    //V83 added
                            .append(" " + AnthologyTitle.TITLE_AUTHOR_DELIM + " ")
                            .append(titles.getString(authorCol))
                            .append(ArrayUtils.MULTI_STRING_SEPARATOR);
                }
            }
        }
        return anthology_titles.toString();
    }

    @NonNull
    private String formatCell(final long cell) {
        return formatCell(cell + "");
    }

    @NonNull
    private String formatCell(final double cell) {
        return formatCell(cell + "");
    }

    /**
     * Double quote all "'s and remove all newlines
     *
     * @param cell to format
     *
     * @return The formatted cell enclosed in escaped quotes and a trailing ','
     */
    @NonNull
    private String formatCell(@Nullable final String cell) {
        try {
            if (cell == null || "null".equalsIgnoreCase(cell) || cell.trim().isEmpty()) {
                return "\"\",";
            }

            final StringBuilder bld = new StringBuilder("\"");
            int endPos = cell.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                char c = cell.charAt(pos);
                switch (c) {
                    case '\r':
                        bld.append("\\r");
                        break;
                    case '\n':
                        bld.append("\\n");
                        break;
                    case '\t':
                        bld.append("\\t");
                        break;
                    case '"':
                        bld.append("\"\"");
                        break;
                    case '\\':
                        bld.append("\\\\");
                        break;
                    default:
                        bld.append(c);
                }
                pos++;

            }
            return bld.append("\",").toString();
        } catch (NullPointerException e) {
            return "\"\",";
        }
    }

}
