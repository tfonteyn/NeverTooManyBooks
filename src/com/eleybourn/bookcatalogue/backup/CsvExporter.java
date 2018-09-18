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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.EditBookFieldsFragment;
import com.eleybourn.bookcatalogue.BooksRow;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Date;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Implementation of Exporter that creates a CSV file.
 *
 * @author pjw
 */
public class CsvExporter implements Exporter {
    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;

    private String mLastError;

    public String getLastError() {
        return mLastError;
    }

    public boolean export(@NonNull final OutputStream outputStream,
                          @NonNull final Exporter.ExportListener listener,
                          final int backupFlags, Date since) throws IOException {
        final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown);
        final String AUTHOR = BookCatalogueApp.getResourceString(R.string.author);

        /* RELEASE: Handle flags! */
        int num = 0;
        if (StorageUtils.isWriteProtected()) {
            mLastError = "Export Failed - Could not write to SDCard";
            return false;
        }

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

        final StringBuilder export = new StringBuilder(
                '"' + DOM_ID.name + "\"," +            //0
                        '"' + UniqueId.BKEY_AUTHOR_DETAILS + "\"," +    //2
                        '"' + DOM_TITLE + "\"," +            //4
                        '"' + DOM_ISBN + "\"," +            //5
                        '"' + DOM_PUBLISHER + "\"," +        //6
                        '"' + DOM_BOOK_DATE_PUBLISHED + "\"," +    //7
                        '"' + DOM_BOOK_RATING + "\"," +            //8
                        '"' + "bookshelf_id\"," +              //9
                        '"' + DOM_BOOKSHELF_ID + "\"," +        //10
                        '"' + DOM_BOOK_READ + "\"," +                //11
                        '"' + UniqueId.BKEY_SERIES_DETAILS + "\"," +    //12
                        '"' + DOM_BOOK_PAGES + "\"," +            //14
                        '"' + DOM_NOTES + "\"," +            //15
                        '"' + DOM_BOOK_LIST_PRICE + "\"," +        //16
                        '"' + DOM_ANTHOLOGY_MASK + "\"," +        //17
                        '"' + DOM_BOOK_LOCATION + "\"," +            //18
                        '"' + DOM_BOOK_READ_START + "\"," +        //19
                        '"' + DOM_BOOK_READ_END + "\"," +            //20
                        '"' + DOM_BOOK_FORMAT + "\"," +            //21
                        '"' + DOM_BOOK_SIGNED + "\"," +            //22
                        '"' + DOM_LOANED_TO + "\"," +            //23
                        '"' + UniqueId.BKEY_ANTHOLOGY_TITLES + "\"," +       //24
                        '"' + DOM_DESCRIPTION + "\"," +        //25
                        '"' + DOM_BOOK_GENRE + "\"," +            //26
                        '"' + DOM_BOOK_LANGUAGE + "\"," +            //+1
                        '"' + DOM_BOOK_DATE_ADDED + "\"," +        //27
                        '"' + DOM_GOODREADS_BOOK_ID + "\"," +        //28
                        '"' + DOM_GOODREADS_LAST_SYNC_DATE + "\"," + //29
                        '"' + DOM_LAST_UPDATE_DATE + "\"," +         //30
                        '"' + DOM_BOOK_UUID + "\"," +        //31
                        "\n");

        long lastUpdate = 0;

        final StringBuilder row = new StringBuilder();

        final CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();

        final BooksCursor bookCursor = db.exportBooks(since);
        final BooksRow rv = bookCursor.getRowView();

        try {
            final int totalBooks = bookCursor.getCount();

            if (!listener.isCancelled()) {

                listener.setMax(totalBooks);

                /* write to the SDCard */
                final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE);
                out.write(export.toString());
                if (bookCursor.moveToFirst()) {
                    do {
                        num++;
                        long id = bookCursor.getLong(bookCursor.getColumnIndexOrThrow(DOM_ID.name));
                        // Just get the string from the database and save it. It should be in standard SQL form already.
                        String dateString = "";
                        try {
                            dateString = bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_DATE_PUBLISHED.name));
                        } catch (Exception e) {
                            //do nothing
                        }
                        // Just get the string from the database and save it. It should be in standard SQL form already.
                        String dateReadStartString = "";
                        try {
                            dateReadStartString = bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_READ_START.name));
                        } catch (Exception e) {
                            Logger.logError(e);
                            //do nothing
                        }
                        // Just get the string from the database and save it. It should be in standard SQL form already.
                        String dateReadEndString = "";
                        try {
                            dateReadEndString = bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_READ_END.name));
                        } catch (Exception e) {
                            Logger.logError(e);
                            //do nothing
                        }
                        // Just get the string from the database and save it. It should be in standard SQL form already.
                        String dateAddedString = "";
                        try {
                            dateAddedString = bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_DATE_ADDED.name));
                        } catch (Exception e) {
                            //do nothing
                        }

                        int anthology = bookCursor.getInt(bookCursor.getColumnIndexOrThrow(DOM_ANTHOLOGY_MASK.name));
                        StringBuilder anthology_titles = new StringBuilder();
                        if (anthology != 0) {
                            try (Cursor titles = db.fetchAnthologyTitlesByBook(id)) {
                                if (titles.moveToFirst()) {
                                    do {
                                        String ant_title = titles.getString(titles.getColumnIndexOrThrow(DOM_TITLE.name));
                                        String ant_author = titles.getString(titles.getColumnIndexOrThrow(DOM_AUTHOR_NAME.name));
                                        anthology_titles.append(ant_title).append(" * ").append(ant_author).append("|");
                                    } while (titles.moveToNext());
                                }
                            }
                        }
                        String title = bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_TITLE.name));
                        // Sanity check: ensure title is non-blank. This has not happened yet, but we
                        // know if does for author, so completeness suggests making sure all 'required'
                        // fields are non-blank.
                        if (title == null || title.trim().isEmpty())
                            title = UNKNOWN;

                        //Display the selected bookshelves
                        StringBuilder bookshelves_id_text = new StringBuilder();
                        StringBuilder bookshelves_name_text = new StringBuilder();
                        try (Cursor bookshelves = db.fetchAllBookshelvesByBook(id)) {
                            while (bookshelves.moveToNext()) {
                                bookshelves_id_text
                                        .append(bookshelves.getString(bookshelves.getColumnIndex(DOM_ID.name)))
                                        .append(EditBookFieldsFragment.BOOKSHELF_SEPARATOR);
                                bookshelves_name_text
                                        .append(ArrayUtils.encodeListItem(EditBookFieldsFragment.BOOKSHELF_SEPARATOR, bookshelves.getString(bookshelves.getColumnIndex(DOM_BOOKSHELF_ID.name))))
                                        .append(EditBookFieldsFragment.BOOKSHELF_SEPARATOR);
                            }
                        }

                        String authorDetails = ArrayUtils.getAuthorUtils().encodeList('|', db.getBookAuthorList(id));
                        // Sanity check: ensure author is non-blank. This HAPPENS. Probably due to constraint failures.
                        if (authorDetails.trim().isEmpty())
                            authorDetails = AUTHOR + ", " + UNKNOWN;

                        String seriesDetails = ArrayUtils.getSeriesUtils().encodeList('|', db.getBookSeriesList(id));

                        row.setLength(0);
                        row.append("\"").append(formatCell(id)).append("\",");
                        row.append("\"").append(formatCell(authorDetails)).append("\",");
                        row.append("\"").append(formatCell(title)).append("\",");
                        row.append("\"").append(formatCell(rv.getIsbn())).append("\",");
                        row.append("\"").append(formatCell(rv.getPublisher())).append("\",");
                        row.append("\"").append(formatCell(dateString)).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_RATING.name)))).append("\",");
                        row.append("\"").append(formatCell(bookshelves_id_text)).append("\",");
                        row.append("\"").append(formatCell(bookshelves_name_text)).append("\",");
                        row.append("\"").append(formatCell(rv.getRead())).append("\",");
                        row.append("\"").append(formatCell(seriesDetails)).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_PAGES.name)))).append("\",");
                        row.append("\"").append(formatCell(rv.getNotes())).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_LIST_PRICE.name)))).append("\",");
                        row.append("\"").append(formatCell(anthology)).append("\",");
                        row.append("\"").append(formatCell(rv.getLocation())).append("\",");
                        row.append("\"").append(formatCell(dateReadStartString)).append("\",");
                        row.append("\"").append(formatCell(dateReadEndString)).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_FORMAT.name)))).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_BOOK_SIGNED.name)))).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_LOANED_TO.name)) + "")).append("\",");
                        row.append("\"").append(formatCell(anthology_titles.toString())).append("\",");
                        row.append("\"").append(formatCell(rv.getDescription())).append("\",");
                        row.append("\"").append(formatCell(rv.getGenre())).append("\",");
                        row.append("\"").append(formatCell(rv.getLanguage())).append("\",");
                        row.append("\"").append(formatCell(dateAddedString)).append("\",");
                        row.append("\"").append(formatCell(rv.getGoodreadsBookId())).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_GOODREADS_LAST_SYNC_DATE.name)))).append("\",");
                        row.append("\"").append(formatCell(bookCursor.getString(bookCursor.getColumnIndexOrThrow(DOM_LAST_UPDATE_DATE.name)))).append("\",");
                        row.append("\"").append(formatCell(rv.getBookUuid())).append("\",");
                        row.append("\n");
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
                    while (bookCursor.moveToNext() && !listener.isCancelled());
                }

                out.close();
            }

        } finally {
            if (BuildConfig.DEBUG) {
                System.out.println("Books Exported: " + num);
            }
            if (displayingStartupMessage) {
                try {
                    listener.onProgress("", 0);
                } catch (Exception ignored) {
                }
            }
            try {
                bookCursor.close();
            } catch (Exception ignored) {
            }
            db.close();
        }
        return true;
    }

    private String formatCell(@NonNull final StringBuilder cell) {
        return cell.toString();
    }

    private String formatCell(final long cell) {
        return formatCell(cell + "");
    }

    /**
     * Double quote all "'s and remove all newlines
     *
     * @param cell The cell the format
     *
     * @return The formatted cell
     */
    @NonNull
    private String formatCell(@NonNull final String cell) {
        try {
            if ("null".equals(cell) || cell.trim().isEmpty()) {
                return "";
            }
            final StringBuilder bld = new StringBuilder();
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
            return bld.toString();
        } catch (NullPointerException e) {
            return "";
        }
    }

}
