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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Objects;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Implementation of Exporter that creates a CSV file.
 *
 * @author pjw
 */
public class CsvExporter implements Exporter {
    /** standard exportBooks file */
    public static final String EXPORT_FILE_NAME = "exportBooks.csv";
    /** standard temp exportBooks file, first we write here, then rename to csv */
    static final String EXPORT_TEMP_FILE_NAME = "exportBooks.tmp";
    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;
    /** pattern we look for to rename/keep older copies */
    private static final String EXPORT_CSV_FILES_PATTERN = "exportBooks.%s.csv";
    /** backup copies to keep */
    private static final int COPIES = 5;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final ExportSettings mSettings;

    /* The CSV file has columns with these names */
    /** string-encoded - used in import/exportBooks, never change this string! */
    static final String CSV_COLUMN_TOC = "anthology_titles";
    /** string-encoded - used in import/exportBooks, never change this string! */
    static final String CSV_COLUMN_SERIES = "series_details";
    /** string-encoded - used in import/exportBooks, never change this string! */
    static final String CSV_COLUMN_AUTHORS = "author_details";
    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?)
     *
     * The fields *._DETAILS are string encoded
     */
    private final String EXPORT_FIELD_HEADERS =
            '"' + DOM_PK_ID.name + "\"," +
                    '"' + CSV_COLUMN_AUTHORS + "\"," +
                    '"' + DOM_TITLE + "\"," +
                    '"' + DOM_BOOK_ISBN + "\"," +
                    '"' + DOM_BOOK_PUBLISHER + "\"," +
                    '"' + DOM_BOOK_DATE_PUBLISHED + "\"," +
                    '"' + DOM_FIRST_PUBLICATION + "\"," +
                    '"' + DOM_BOOK_EDITION_BITMASK + "\"," +
                    '"' + DOM_BOOK_RATING + "\"," +
                    // this should be UniqueId.DOM_FK_BOOKSHELF_ID but it was misnamed originally
                    // but in fact, FIXME? it's not actually used during import anyway
                    '"' + "bookshelf_id\"," +
                    '"' + DOM_BOOKSHELF + "\"," +
                    '"' + DOM_BOOK_READ + "\"," +
                    '"' + CSV_COLUMN_SERIES + "\"," +
                    '"' + DOM_BOOK_PAGES + "\"," +
                    '"' + DOM_BOOK_NOTES + "\"," +

                    '"' + DOM_BOOK_PRICE_LISTED + "\"," +
                    '"' + DOM_BOOK_PRICE_LISTED_CURRENCY + "\"," +
                    '"' + DOM_BOOK_PRICE_PAID + "\"," +
                    '"' + DOM_BOOK_PRICE_PAID_CURRENCY + "\"," +
                    '"' + DOM_BOOK_DATE_ACQUIRED + "\"," +

                    '"' + DOM_BOOK_ANTHOLOGY_BITMASK + "\"," +
                    '"' + DOM_BOOK_LOCATION + "\"," +
                    '"' + DOM_BOOK_READ_START + "\"," +
                    '"' + DOM_BOOK_READ_END + "\"," +
                    '"' + DOM_BOOK_FORMAT + "\"," +
                    '"' + DOM_BOOK_SIGNED + "\"," +
                    '"' + DOM_LOANED_TO + "\"," +
                    '"' + CSV_COLUMN_TOC + "\"," +
                    '"' + DOM_DESCRIPTION + "\"," +
                    '"' + DOM_BOOK_GENRE + "\"," +
                    '"' + DOM_BOOK_LANGUAGE + "\"," +
                    '"' + DOM_BOOK_DATE_ADDED + "\"," +
                    '"' + DOM_BOOK_LIBRARY_THING_ID + "\"," +
                    '"' + DOM_BOOK_ISFDB_ID + "\"," +
                    '"' + DOM_BOOK_GOODREADS_BOOK_ID + "\"," +
                    '"' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "\"," +
                    '"' + DOM_LAST_UPDATE_DATE + "\"," +
                    '"' + DOM_BOOK_UUID +
                    "\n";

    /**
     * Constructor
     *
     * @param settings {@link ExportSettings#file} is not used, as we must support writing to a stream.
     *                 {@link ExportSettings#EXPORT_SINCE} and {@link ExportSettings#dateFrom} is respected.
     *                 Other flags are ignored, as this method only
     *                 handles {@link ExportSettings#BOOK_DATA} anyhow.
     */
    public CsvExporter(final @NonNull Context context, final @NonNull ExportSettings settings) {
        mDb = new CatalogueDBAdapter(context);
        mSettings = settings;
    }

    /**
     * @param outputStream Stream for writing data
     * @param listener     Progress and cancellation interface
     *
     * @return <tt>true</tt>on success
     *
     * @throws IOException on any error
     */
    public boolean exportBooks(final @NonNull OutputStream outputStream,
                               final @NonNull ExportListener listener) throws IOException {
        final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown);
        final String AUTHOR = BookCatalogueApp.getResourceString(R.string.lbl_author);

        if (StorageUtils.isWriteProtected()) {
            throw new IOException("Export Failed - Could not write to Storage");
        }

        // if we want 'since', we *must* have a valid dateFrom
        if ((mSettings.options & ExportSettings.EXPORT_SINCE) != 0) {
            Objects.requireNonNull(mSettings.dateFrom,"Export Failed - 'dateFrom' is null");
        } else {
            // sanity check: we don't want 'since', so make sure fromDate is not set.
            mSettings.dateFrom = null;
        }
        // Display startup message
        listener.onProgress(BookCatalogueApp.getResourceString(R.string.progress_msg_export_starting), 0);
        boolean displayingStartupMessage = true;

        long lastUpdate = 0;
        int num = 0;
        final StringBuilder row = new StringBuilder();

        try (BookCursor bookCursor = mDb.exportBooks(mSettings.dateFrom);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE)) {

            final BookRowView bookCursorRow = bookCursor.getCursorRow();
            final int totalBooks = bookCursor.getCount();

            if (listener.isCancelled()) {
                return false;
            }

            listener.setMax(totalBooks);
            out.write(EXPORT_FIELD_HEADERS);

            while (bookCursor.moveToNext() && !listener.isCancelled()) {
                num++;
                long bookId = bookCursor.getLong(bookCursor.getColumnIndexOrThrow(DOM_PK_ID.name));

                String author_stringList = StringList.getAuthorUtils().encode(mDb.getBookAuthorList(bookId));
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
                //   StringList.getBookshelfUtils().encode( mDb.getBookshelvesByBookId(bookId));
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
                            .append(StringList.encodeListItem(Bookshelf.SEPARATOR, bookshelf.name))
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
                        .append(formatCell(StringList.getSeriesUtils().encode(mDb.getBookSeriesList(bookId))))
                        .append(formatCell(bookCursorRow.getPages()))
                        .append(formatCell(bookCursorRow.getNotes()))

                        .append(formatCell(bookCursorRow.getListPrice()))
                        .append(formatCell(bookCursorRow.getListPriceCurrency()))
                        .append(formatCell(bookCursorRow.getPricePaid()))
                        .append(formatCell(bookCursorRow.getPricePaidCurrency()))
                        .append(formatCell(bookCursorRow.getDateAcquired()))

                        .append(formatCell(bookCursorRow.getAnthologyBitMask()))
                        .append(formatCell(bookCursorRow.getLocation()))
                        .append(formatCell(bookCursorRow.getReadStart()))
                        .append(formatCell(bookCursorRow.getReadEnd()))
                        .append(formatCell(bookCursorRow.getFormat()))
                        .append(formatCell(bookCursorRow.getSigned()))
                        .append(formatCell(bookCursorRow.getLoanedTo()))
                        .append(formatCell(StringList.getTOCUtils().encode( mDb.getTOCEntriesByBook(bookId))))
                        .append(formatCell(bookCursorRow.getDescription()))
                        .append(formatCell(bookCursorRow.getGenre()))
                        .append(formatCell(bookCursorRow.getLanguageCode()))
                        .append(formatCell(bookCursorRow.getDateAdded()))

                        .append(formatCell(bookCursorRow.getLibraryThingBookId()))
                        .append(formatCell(bookCursorRow.getISFDBBookId()))
                        .append(formatCell(bookCursorRow.getGoodreadsBookId()))
                        .append(formatCell(bookCursorRow.getDateLastSyncedWithGoodreads()))

                        .append(formatCell(bookCursorRow.getDateLastUpdated()))
                        .append(formatCell(bookCursorRow.getBookUuid()));

                // replace the comma at the end of the line with a '\n'
                row.replace(row.length()-1, row.length(), "\n");

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

    /**
     * At the end of a successful export, rename the temp file to {@link #EXPORT_FILE_NAME}
     *
     * @param tempFile to rename
     */
    void renameFiles(final @NonNull File tempFile) {
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
