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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Implementation of Importer that reads a CSV file.
 *
 * reminder: use UniqueId.KEY, not DOM. We are reading from file, putting into objects.
 *
 * @author pjw
 */
public class CsvImporter implements Importer {
    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;
    private final static char QUOTE_CHAR = '"';
    private final static char ESCAPE_CHAR = '\\';
    private final static char SEPARATOR = ',';

    private final static String STRINGED_ID = UniqueId.KEY_ID;

    /** as used in older versions, or from arbitrarily constructed CSV files */
    private static final String OLD_STYLE_AUTHOR_NAME = "author_name";

    @NonNull
    private final CatalogueDBAdapter mDb;

    @NonNull
    private Integer mCreated = 0;
    @NonNull
    private Integer mUpdated = 0;

    CsvImporter(final @NonNull Context context) {
        mDb = new CatalogueDBAdapter(context);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean importBooks(final @NonNull InputStream exportStream,
                               final @Nullable Importer.CoverFinder coverFinder,
                               final @NonNull Importer.OnImporterListener listener,
                               final int importFlags) throws IOException {

        final List<String> importedList = new ArrayList<>();

        final BufferedReader in = new BufferedReader(new InputStreamReader(exportStream, UTF8), BUFFER_SIZE);
        String line;
        while ((line = in.readLine()) != null) {
            importedList.add(line);
        }

        if (importedList.size() == 0) {
            return true;
        }

        listener.setMax(importedList.size() - 1);

        final Book book = new Book();

        // first line in import are the column names
        final String[] csvColumnNames = returnRow(importedList.get(0), true);
        // Store the names so we can check what is present
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase();
            book.putString(csvColumnNames[i], "");
        }

        // See if we can deduce the kind of escaping to use based on column names.
        // Version 1->3.3 export with family_name and author_id. Version 3.4+ do not; latest versions
        // make an attempt at escaping characters etc to preserve formatting.
        boolean fullEscaping = !book.containsKey(UniqueId.KEY_AUTHOR_ID) || !book.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME);

        // Make sure required fields in Book bundle are present.
        // ENHANCE: Rationalize import to allow updates using 1 or 2 columns. For now we require complete data.
        // ENHANCE: Do a search if mandatory columns missing (eg. allow 'import' of a list of ISBNs).
        // ENHANCE: Only make some columns mandatory if the ID is not in import, or not in DB (ie. if not an update)
        // ENHANCE: Export/Import should use GUIDs for book IDs, and put GUIDs on Image file names.

        // need either ID or UUID
        requireColumnOrThrow(book, UniqueId.KEY_ID, UniqueId.KEY_BOOK_UUID);
        // need some type of author name.
        requireColumnOrThrow(book,
                CsvExporter.CSV_COLUMN_AUTHORS, // aka author_details: preferred one as used in latest versions

                UniqueId.KEY_AUTHOR_FAMILY_NAME,
                UniqueId.KEY_AUTHOR_FORMATTED,
                OLD_STYLE_AUTHOR_NAME
                );

        final boolean updateOnlyIfNewer;
        if ((importFlags & Importer.IMPORT_NEW_OR_UPDATED) != 0) {
            if (!book.containsKey(UniqueId.KEY_LAST_UPDATE_DATE)) {
                throw new IllegalArgumentException("Imported data does not contain " + UniqueId.KEY_LAST_UPDATE_DATE);
            }
            updateOnlyIfNewer = true;
        } else {
            updateOnlyIfNewer = false;
        }

        int row = 1; // Start after headings.
        int txRowCount = 0;
        long lastUpdate = 0;

        /* Iterate through each imported row */
        SyncLock txLock = null;
        try {
            while (row < importedList.size() && listener.isActive()) {
                // every 10 inserted, we commit the transaction
                if (mDb.inTransaction() && txRowCount > 10) {
                    mDb.setTransactionSuccessful();
                    mDb.endTransaction(txLock);
                }
                if (!mDb.inTransaction()) {
                    txLock = mDb.startTransaction(true);
                    txRowCount = 0;
                }
                txRowCount++;

                // Get row
                final String[] csvDataRow = returnRow(importedList.get(row), fullEscaping);
                // clear book (avoiding construction another object) and add each field
                book.clear();
                // read all columns of the current row into the Bundle
                //note that some of them require further processing before being valid.
                for (int i = 0; i < csvColumnNames.length; i++) {
                    book.putString(csvColumnNames[i], csvDataRow[i]);
                }

                // Validate ID
                // why String ? See book init, we store all keys we find in the import file as text simple to see if they are present.
                final String idStr = book.getString(STRINGED_ID);
                long bookId = 0;
                boolean hasNumericId = (idStr != null && !idStr.isEmpty());

                if (hasNumericId) {
                    try {
                        bookId = Long.parseLong(idStr);
                    } catch (Exception e) {
                        hasNumericId = false;
                    }
                }

                if (!hasNumericId) {
                    book.putString(STRINGED_ID, "0"); // yes, string, see above
                }

                // Get the UUID, and remove from collection if null/blank
                final String uuidVal = book.getString(UniqueId.KEY_BOOK_UUID);
                final boolean hasUuid = (uuidVal != null && !uuidVal.isEmpty());
                if (!hasUuid) {
                    // Remove any blank UUID column, just in case
                    if (book.containsKey(UniqueId.KEY_BOOK_UUID)) {
                        book.remove(UniqueId.KEY_BOOK_UUID);
                    }
                }

                requireNonBlankOrThrow(book, row, UniqueId.KEY_TITLE);
                final String title = book.getString(UniqueId.KEY_TITLE);

                // Handle these here, lookup id's etc, but do not write to db!
                // storing the book data does all that
                handleAuthors(mDb, book);
                handleSeries(mDb, book);
                if (book.containsKey(CsvExporter.CSV_COLUMN_TOC)) {
                    // ignore the actual value of the UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK! it will be
                    // 'reset' to mirror what we actually have when storing the book data
                    handleAnthology(mDb, book);
                }

                // v5 has columns
                // "bookshelf_id" == UniqueId.DOM_FK_BOOKSHELF_ID => see CsvExporter EXPORT_FIELD_HEADERS
                // "bookshelf" == UniqueId.KEY_BOOKSHELF_NAME
                // I suspect "bookshelf_text" is from older versions and obsolete now (Classic ?)
                if (book.containsKey(UniqueId.KEY_BOOKSHELF_NAME) && !book.containsKey("bookshelf_text")) {
                    handleBookshelves(mDb, book);

                }

                try {
                    // Save the original ID from the file for use in checking for images
                    long bookIdFromFile = bookId;

                    bookId = importBook(bookId, hasNumericId, uuidVal, hasUuid, book, updateOnlyIfNewer);

                    // When importing a file that has an ID or UUID, try to import a cover.
                    if (coverFinder != null) {
                        coverFinder.copyOrRenameCoverFile(uuidVal, bookIdFromFile, bookId);
                    }
                } catch (IOException e) {
                    Logger.error(e, "Cover import failed at row " + row);
                } catch (Exception e) {
                    Logger.error(e, "Import failed at row " + row);
                }

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200 && listener.isActive()) {
                    listener.onProgress(title + "\n(" + BookCatalogueApp.getResourceString(R.string.progress_msg_n_created_m_updated, mCreated, mUpdated) + ")", row);
                    lastUpdate = now;
                }

                row++;
            }
        } catch (Exception e) {
            Logger.error(e);
            throw new RuntimeException(e);
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(txLock);
            }
            try {
                // purging is justified here... or not ?
                mDb.purgeAuthors();
                mDb.purgeSeries();
                mDb.analyzeDb();
            } catch (Exception e) {
                Logger.error(e);
            }

            mDb.close();
        }

        Logger.info(this,"Csv Import successful: rows processed: " + row + ", created:" + mCreated + ", updated: " + mUpdated);
        return true;
    }



    /**
     * @return new or updated bookId
     */
    private long importBook(long bookId,
                            final boolean hasNumericId,
                            final @NonNull String uuidVal,
                            final boolean hasUuid,
                            final @NonNull Book book,
                            final boolean updateOnlyIfNewer) {
        if (!hasUuid && !hasNumericId) {
            // Always import empty IDs...even if they are duplicates.
            long id = mDb.insertBookWithId(0, book);
            book.putLong(UniqueId.KEY_ID, id);
            // Would be nice to import a cover, but with no ID/UUID that is not possible
        } else {
            // we have UUID or ID, so it's an update

            // Let the UUID trump the ID; we may be importing someone else's list with bogus IDs
            boolean exists = false;

            if (hasUuid) {
                long tmp_id = mDb.getBookIdFromUuid(uuidVal);

                if (tmp_id != 0) {
                    bookId = tmp_id;
                    exists = true;
                } else {
                    // We have a UUID, but book does not exist. We will create a book.
                    // Make sure the ID (if present) is not already used.
                    if (hasNumericId && mDb.bookExists(bookId)) {
                        bookId = 0;
                    }
                }
            } else {
                exists = mDb.bookExists(bookId);
            }

            if (exists) {
                if (!updateOnlyIfNewer || updateOnlyIfNewer(mDb, book, bookId)) {
                    mDb.updateBook(bookId, book,
                            CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES
                                    | CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                    mUpdated++;
                }
            } else {
                bookId = mDb.insertBookWithId(bookId, book);
                mCreated++;
            }

            // Save the real ID to the collection (will/may be used later)
            book.putLong(UniqueId.KEY_ID, bookId);

        }
        return bookId;
    }


    private boolean updateOnlyIfNewer(final @NonNull CatalogueDBAdapter db,
                                      final @NonNull Book book,
                                      final long bookId) {
        String bookDateStr = db.getBookLastUpdateDate(bookId);

        Date bookDate = null;
        if (bookDateStr != null && !bookDateStr.isEmpty()) {
            try {
                bookDate = DateUtils.parseDate(bookDateStr);
            } catch (Exception ignore) {
                // Treat as if never updated
            }
        }

        String importDateStr = book.getString(UniqueId.KEY_LAST_UPDATE_DATE);

        Date importDate = null;
        if (!importDateStr.isEmpty()) {
            try {
                importDate = DateUtils.parseDate(importDateStr);
            } catch (Exception ignore) {
                // Treat as if never updated
            }
        }
        return importDate != null && (bookDate == null || importDate.compareTo(bookDate) > 0);
    }

    /**
     * The "bookshelf_id" column is not used at all (similar to how author etc is done)
     */
    private void handleBookshelves(final @NonNull CatalogueDBAdapter db,
                                   final @NonNull Book book) {
        String encodedList = book.getString(UniqueId.KEY_BOOKSHELF_NAME);

        book.putBookshelfList(StringList.getBookshelfUtils().decode(Bookshelf.SEPARATOR, encodedList, false));

        book.remove(UniqueId.KEY_BOOKSHELF_NAME);
    }

    /**
     * Database access is strictly limited to fetching id's
     */
    private void handleAnthology(final @NonNull CatalogueDBAdapter db,
                                 final @NonNull Book book) {

        String encodedList = book.getString(CsvExporter.CSV_COLUMN_TOC);
        if (!encodedList.isEmpty()) {
            ArrayList<TOCEntry> list = StringList.getTOCUtils().decode(encodedList,false);
            if (!list.isEmpty()) {
                // fixup the id's
                Utils.pruneList(db, list);
                book.putTOC(list);
            }
        }

        // remove the unneeded string encoded set
        book.remove(CsvExporter.CSV_COLUMN_TOC);
    }

    /**
     * Database access is strictly limited to fetching id's
     *
     * Get the list of series from whatever source is available.
     */
    private void handleSeries(final @NonNull CatalogueDBAdapter db,
                              final @NonNull Book book) {
        String encodedList = book.getString(CsvExporter.CSV_COLUMN_SERIES);
        if (encodedList.isEmpty()) {
            // Try to build from SERIES_NAME and SERIES_NUM. It may all be blank
            if (book.containsKey(UniqueId.KEY_SERIES_NAME)) {
                encodedList = book.getString(UniqueId.KEY_SERIES_NAME);
                if (!encodedList.isEmpty()) {
                    String seriesNum = book.getString(UniqueId.KEY_SERIES_NUM);
                    encodedList += "(" + seriesNum + ")";
                } else {
                    encodedList = null;
                }
            }
        }
        // Handle the series
        final ArrayList<Series> list = StringList.getSeriesUtils().decode(encodedList, false);
        Series.pruneSeriesList(list);
        Utils.pruneList(db, list);
        book.putSeriesList(list);
        book.remove(CsvExporter.CSV_COLUMN_SERIES);
    }

    /**
     * Database access is strictly limited to fetching id's
     *
     * Get the list of authors from whatever source is available.
     */
    private void handleAuthors(final @NonNull CatalogueDBAdapter db,
                               final @NonNull Book book) {
        // preferred & used in latest versions
        String encodedList = book.getString(CsvExporter.CSV_COLUMN_AUTHORS);
        if (encodedList.isEmpty()) {
            // Need to build it from other/older fields.

            if (book.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
                // Build from family/given
                encodedList = book.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
                String given = "";
                if (book.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                    given = book.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
                }
                if (!given.isEmpty()) {
                    encodedList += ", " + given;
                }

            } else if (book.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {
                encodedList = book.getString(UniqueId.KEY_AUTHOR_FORMATTED);

            } else if (book.containsKey(OLD_STYLE_AUTHOR_NAME)) {
                encodedList = book.getString(OLD_STYLE_AUTHOR_NAME);
            }
        }

        // A pre-existing bug sometimes results in blank author-details due to bad underlying data
        // (it seems a 'book' record gets written without an 'author' record; should not happen)
        // so we allow blank author_details and fill in a regional version of "Author, Unknown"
        if (encodedList.isEmpty()) {
            encodedList = BookCatalogueApp.getResourceString(R.string.lbl_author) + ", " + BookCatalogueApp.getResourceString(R.string.unknown);
        }

        // Now build the array for authors
        final ArrayList<Author> list = StringList.getAuthorUtils().decode(encodedList, false);
        Utils.pruneList(db, list);
        book.putAuthorList(list);
        book.remove(CsvExporter.CSV_COLUMN_AUTHORS);
    }

    //
    // This CSV parser is not a complete parser, but it will parse files exported by older
    // versions. At some stage in the future it would be good to allow full CSV export
    // and import to allow for escape('\') chars so that cr/lf can be preserved.
    //
    @NonNull
    private String[] returnRow(final @NonNull String row, final boolean fullEscaping) {
        // Need to handle double quotes etc

        // Current position
        int pos = 0;
        // In a quoted string
        boolean inQuote = false;
        // Found an escape char
        boolean inEsc = false;
        // 'Current' char
        char c;
        // 'Next' char
        char next = (!row.isEmpty()) ? row.charAt(0) : '\0';
        // Last position in row
        int endPos = row.length() - 1;
        // Array of fields found in row
        final List<String> fields = new ArrayList<>();
        // Temp. storage for current field
        StringBuilder bld = new StringBuilder();

        while (next != '\0') {
            // Get current and next char
            c = next;
            next = (pos < endPos) ? row.charAt(pos + 1) : '\0';

            // If we are 'escaped', just append the char, handling special cases
            if (inEsc) {
                bld.append(unescape(c));
                inEsc = false;
            } else if (inQuote) {
                switch (c) {
                    case QUOTE_CHAR:
                        if (next == QUOTE_CHAR) {
                            // Double-quote: Advance one more and append a single quote
                            pos++;
                            next = (pos < endPos) ? row.charAt(pos + 1) : '\0';
                            bld.append(c);
                        } else {
                            // Leave the quote
                            inQuote = false;
                        }
                        break;
                    case ESCAPE_CHAR:
                        if (fullEscaping) {
                            inEsc = true;
                        } else {
                            bld.append(c);
                        }
                        break;
                    default:
                        bld.append(c);
                        break;
                }
            } else {
                // This is just a raw string; no escape or quote active.
                // Ignore leading space.
                if ((c != ' ' && c != '\t') || bld.length() != 0) {
                    switch (c) {
                        case QUOTE_CHAR:
                            if (bld.length() > 0) {
                                // Fields with quotes MUST be quoted...
                                throw new IllegalArgumentException();
                            } else {
                                inQuote = true;
                            }
                            break;
                        case ESCAPE_CHAR:
                            if (fullEscaping) {
                                inEsc = true;
                            } else {
                                bld.append(c);
                            }
                            break;
                        case SEPARATOR:
                            // Add this field and reset it.
                            fields.add(bld.toString());
                            bld = new StringBuilder();
                            break;
                        default:
                            // Just append the char
                            bld.append(c);
                            break;
                    }
                }
            }
            pos++;
        }

        // Add the remaining chunk
        fields.add(bld.toString());

        // Return the result as a String[].
        String[] imported = new String[fields.size()];
        fields.toArray(imported);

        return imported;
    }

    private char unescape(char c) {
        switch (c) {
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'n':
                return '\n';
            default:
                // Handle simple escapes. We could go further and allow arbitrary numeric chars by
                // testing for numeric sequences here but that is beyond the scope of this app.
                return c;
        }
    }

    /**
     * Require a column to be present. First one found; remainders are not needed
     */
    private void requireColumnOrThrow(final @NonNull Book book,
                                      String... names) throws ImportException {
        for (String name : names) {
            if (book.containsKey(name)) {
                return;
            }
        }

        throw new ImportException(BookCatalogueApp.getResourceString(R.string.error_file_must_contain_any_column,
                Utils.join(",", names)));
    }

    private void requireNonBlankOrThrow(final @NonNull Book book,
                                        final int row,
                                        @SuppressWarnings("SameParameterValue") final @NonNull String name)
            throws ImportException {
        if (!book.getString(name).isEmpty()) {
            return;
        }
        throw new ImportException(BookCatalogueApp.getResourceString(R.string.error_column_is_blank, name, row));
    }

    @SuppressWarnings("unused")
    private void requireAnyNonBlankOrThrow(final @NonNull Book book,
                                           final int row,
                                           final @NonNull String... names) throws ImportException {
        for (String name : names) {
            if (book.containsKey(name) && !book.getString(name).isEmpty()) {
                return;
            }
        }

        throw new ImportException(BookCatalogueApp.getResourceString(R.string.error_columns_are_blank, Utils.join(",", names), row));
    }

}
