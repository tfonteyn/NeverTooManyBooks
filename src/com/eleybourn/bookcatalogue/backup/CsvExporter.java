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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Implementation of Exporter that creates a CSV file.
 *
 * @author pjw
 */
public class CsvExporter implements Exporter {
    /** standard export file */
    public static final String EXPORT_FILE_NAME = "export.csv";
    /** standard temp export file, first we write here, then rename to csv */
    static final String EXPORT_TEMP_FILE_NAME = "export.tmp";
    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;
    /** pattern we look for to rename/keep older copies */
    private static final String EXPORT_CSV_FILES_PATTERN = "export.%s.csv";
    /** backup copies to keep */
    private static final int COPIES = 5;
    @NonNull
    private final CatalogueDBAdapter mDb;
    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?)
     *
     * The fields *._DETAILS are string encoded
     */
    private final String EXPORT_FIELD_HEADERS =
            '"' + DOM_ID.name + "\"," +
                    '"' + UniqueId.BKEY_AUTHOR_STRING_LIST + "\"," +
                    '"' + DOM_TITLE + "\"," +
                    '"' + DOM_BOOK_ISBN + "\"," +
                    '"' + DOM_BOOK_PUBLISHER + "\"," +
                    '"' + DOM_BOOK_DATE_PUBLISHED + "\"," +
                    '"' + DOM_FIRST_PUBLICATION + "\"," +
                    '"' + DOM_BOOK_EDITION_BITMASK + "\"," +
                    '"' + DOM_BOOK_RATING + "\"," +
                    // this should be UniqueId.DOM_BOOKSHELF_ID but it was misnamed originally
                    // but in fact, FIXME? it's not actually used during import anyway
                    '"' + "bookshelf_id\"," +
                    '"' + DOM_BOOKSHELF + "\"," +
                    '"' + DOM_BOOK_READ + "\"," +
                    '"' + UniqueId.BKEY_SERIES_STRING_LIST + "\"," +
                    '"' + DOM_BOOK_PAGES + "\"," +
                    '"' + DOM_BOOK_NOTES + "\"," +

                    '"' + DOM_BOOK_PRICE_LISTED + "\"," +
                    '"' + DOM_BOOK_PRICE_LISTED_CURRENCY + "\"," +
                    '"' + DOM_BOOK_PRICE_PAID + "\"," +
                    '"' + DOM_BOOK_PRICE_PAID_CURRENCY + "\"," +

                    '"' + DOM_BOOK_ANTHOLOGY_BITMASK + "\"," +
                    '"' + DOM_BOOK_LOCATION + "\"," +
                    '"' + DOM_BOOK_READ_START + "\"," +
                    '"' + DOM_BOOK_READ_END + "\"," +
                    '"' + DOM_BOOK_FORMAT + "\"," +
                    '"' + DOM_BOOK_SIGNED + "\"," +
                    '"' + DOM_LOANED_TO + "\"," +
                    '"' + UniqueId.BKEY_TOC_STRING_LIST + "\"," +
                    '"' + DOM_DESCRIPTION + "\"," +
                    '"' + DOM_BOOK_GENRE + "\"," +
                    '"' + DOM_BOOK_LANGUAGE + "\"," +
                    '"' + DOM_BOOK_DATE_ADDED + "\"," +
                    '"' + DOM_BOOK_GOODREADS_BOOK_ID + "\"," +
                    '"' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "\"," +
                    '"' + DOM_LAST_UPDATE_DATE + "\"," +
                    '"' + DOM_BOOK_UUID + "\"," +
                    "\n";

    //FIXME: turn into a string resource + actually report somewhere
    @SuppressWarnings("FieldCanBeLocal")
    private String mLastError;

    CsvExporter() {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext())
                .open();
    }

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

    public boolean export(final @NonNull OutputStream outputStream,
                          final @NonNull Exporter.ExportListener listener,
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

        try (BookCursor bookCursor = mDb.exportBooks(since);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE)) {

            final BookCursorRow bookCursorRow = bookCursor.getCursorRow();
            final int totalBooks = bookCursor.getCount();

            if (listener.isCancelled()) {
                return false;
            }

            listener.setMax(totalBooks);
            out.write(EXPORT_FIELD_HEADERS);

            while (bookCursor.moveToNext() && !listener.isCancelled()) {
                num++;
                long bookId = bookCursor.getLong(bookCursor.getColumnIndexOrThrow(DOM_ID.name));

                String author_stringList = ArrayUtils.getAuthorUtils().encodeList(mDb.getBookAuthorList(bookId));
                // Sanity check: ensure author is non-blank. This HAPPENS. Probably due to constraint failures.
                if (author_stringList.trim().isEmpty()) {
                    author_stringList = AUTHOR + ", " + UNKNOWN;
                }

                String title = bookCursorRow.getTitle();
                // Sanity check: ensure title is non-blank. This has not happened yet, but we
                // know if does for author, so completeness suggests making sure all 'required'
                // fields are non-blank.
                if (title.trim().isEmpty()) {
                    title = UNKNOWN;
                }

                // For the names, could use this of course:
                //   ArrayUtils.getBookshelfUtils().encodeList( mDb.getBookshelvesByBookId(bookId));
                // but we also want a list of the id's.
                // so....
                // the selected bookshelves: two CSV columns with CSV id's + CSV names
                StringBuilder bookshelves_id_stringList = new StringBuilder();
                StringBuilder bookshelves_name_stringList = new StringBuilder();
                for (Bookshelf bookshelf : mDb.getBookshelvesByBookId(bookId)) {
                    bookshelves_id_stringList
                            .append(bookshelf.id)
                            .append(Bookshelf.SEPARATOR);
                    bookshelves_name_stringList
                            .append(ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, bookshelf.name))
                            .append(Bookshelf.SEPARATOR);
                }

                row.setLength(0);
                row.append(formatCell(bookId))
                        .append(formatCell(author_stringList))
                        .append(formatCell(title))
                        .append(formatCell(bookCursorRow.getIsbn()))
                        .append(formatCell(bookCursorRow.getPublisherName()))
                        .append(formatCell(bookCursorRow.getDatePublished()))
                        .append(formatCell(bookCursorRow.getFirstPublication()))
                        .append(formatCell(bookCursorRow.getEditionBitMask()))
                        .append(formatCell(bookCursorRow.getRating()))
                        .append(formatCell(bookshelves_id_stringList.toString()))
                        .append(formatCell(bookshelves_name_stringList.toString()))
                        .append(formatCell(bookCursorRow.getRead()))
                        .append(formatCell(ArrayUtils.getSeriesUtils().encodeList(mDb.getBookSeriesList(bookId))))
                        .append(formatCell(bookCursorRow.getPages()))
                        .append(formatCell(bookCursorRow.getNotes()))

                        .append(formatCell(bookCursorRow.getListPrice()))
                        .append(formatCell(bookCursorRow.getListPriceCurrency()))
                        .append(formatCell(bookCursorRow.getPricePaid()))
                        .append(formatCell(bookCursorRow.getPricePaidCurrency()))

                        .append(formatCell(bookCursorRow.getAnthologyBitMask()))
                        .append(formatCell(bookCursorRow.getLocation()))
                        .append(formatCell(bookCursorRow.getReadStart()))
                        .append(formatCell(bookCursorRow.getReadEnd()))
                        .append(formatCell(bookCursorRow.getFormat()))
                        .append(formatCell(bookCursorRow.getSigned()))
                        .append(formatCell(bookCursorRow.getLoanedTo()))
                        .append(formatCell(ArrayUtils.getTOCUtils().encodeList( mDb.getTOCEntriesByBook(bookId))))
                        .append(formatCell(bookCursorRow.getDescription()))
                        .append(formatCell(bookCursorRow.getGenre()))
                        .append(formatCell(bookCursorRow.getLanguage()))
                        .append(formatCell(bookCursorRow.getDateAdded()))
                        .append(formatCell(bookCursorRow.getGoodreadsBookId()))
                        .append(formatCell(bookCursorRow.getDateLastSyncedWithGoodReads()))
                        .append(formatCell(bookCursorRow.getDateLastUpdated()))
                        .append(formatCell(bookCursorRow.getBookUuid()))
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
            Logger.info(this, "Books Exported: " + num);
            if (displayingStartupMessage) {
                try {
                    listener.onProgress("", 0);
                } catch (Exception ignored) {
                }
            }
            mDb.close();
        }
        return true;
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
    private String formatCell(final @Nullable String cell) {
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
