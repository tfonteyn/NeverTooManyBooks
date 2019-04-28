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
import android.database.sqlite.SQLiteDoneException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ImportException;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Implementation of Importer that reads a CSV file.
 * <p>
 * reminder: use UniqueId.KEY, not DOM. We are reading from file, putting into objects.
 *
 * @author pjw
 */
public class CsvImporter
        implements Importer {

    private static final int BUFFER_SIZE = 32768;

    private static final char QUOTE_CHAR = '"';
    private static final char ESCAPE_CHAR = '\\';
    private static final char SEPARATOR = ',';

    private static final String STRINGED_ID = DBDefinitions.KEY_ID;

    /** as used in older versions, or from arbitrarily constructed CSV files. */
    private static final String OLD_STYLE_AUTHOR_NAME = "author_name";
    private static final String ERROR_IMPORT_FAILED_AT_ROW = "Import failed at row ";
    private static final String LEGACY_BOOKSHELF_TEXT_COLUMN = "bookshelf_text";

    @NonNull
    private final Context mContext;
    @NonNull
    private final DBA mDb;
    @NonNull
    private final ImportSettings mSettings;

    @NonNull
    private Integer mCreated = 0;
    @NonNull
    private Integer mUpdated = 0;

    /**
     * Constructor.
     *
     * @param context  caller context
     * @param settings {@link ImportSettings#file} is not used, as we must support
     *                 reading from a stream.
     *                 {@link ImportSettings##dateFrom} is not applicable
     *                 {@link ImportSettings#IMPORT_ONLY_NEW_OR_UPDATED} is respected.
     *                 Other flags are ignored, as this class only
     *                 handles {@link ImportSettings#BOOK_CSV} anyhow.
     */
    public CsvImporter(@NonNull final Context context,
                       @NonNull final ImportSettings settings) {
        mContext = context;
        mDb = new DBA(mContext);
        mSettings = settings;
    }

    @Override
    public int doBooks(@NonNull final InputStream importStream,
                       @Nullable final CoverFinder coverFinder,
                       @NonNull final ImportListener listener)
            throws IOException {

        final List<String> importedList = new ArrayList<>();

        final BufferedReader in = new BufferedReader(
                new InputStreamReader(importStream, StandardCharsets.UTF_8), BUFFER_SIZE);

        String line;
        while ((line = in.readLine()) != null) {
            importedList.add(line);
        }
        if (importedList.isEmpty()) {
            return 0;
        }

        listener.setMax(importedList.size() - 1);

        final Book book = new Book();
        // first line in import are the column names
        final String[] csvColumnNames = returnRow(importedList.get(0), true);
        // Store the names so we can check what is present
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase(LocaleUtils.getSystemLocale());
            // add a place holder to the book.
            book.putString(csvColumnNames[i], "");
        }

        // See if we can deduce the kind of escaping to use based on column names.
        // Version 1->3.3 export with family_name and author_id.
        // Version 3.4+ do not; latest versions make an attempt at escaping
        // characters etc to preserve formatting.
        boolean fullEscaping = !book.containsKey(DBDefinitions.KEY_AUTHOR)
                || !book.containsKey(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);

        // Make sure required fields in Book bundle are present.
        // ENHANCE: Rationalize import to allow updates using 1 or 2 columns.
        // For now we require complete data.
        // ENHANCE: Do a search if mandatory columns missing
        // (eg. allow 'import' of a list of ISBNs).
        // ENHANCE: Only make some columns mandatory if the ID is not in import, or not in DB
        // (i.e. if not an update)
        // ENHANCE: Export/Import should use GUIDs for book IDs, and put GUIDs on Image file names.

        // need either ID or UUID
        requireColumnOrThrow(book, DBDefinitions.KEY_ID, DBDefinitions.KEY_BOOK_UUID);

        // need some type of author name.
        requireColumnOrThrow(book,
                             CsvExporter.CSV_COLUMN_AUTHORS,
                             // aka author_details: preferred one as used in latest versions

                             DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                             DBDefinitions.KEY_AUTHOR_FORMATTED,
                             OLD_STYLE_AUTHOR_NAME
        );

        final boolean updateOnlyIfNewer;
        if ((mSettings.what & ImportSettings.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
            if (!book.containsKey(DBDefinitions.KEY_DATE_LAST_UPDATED)) {
                throw new IllegalArgumentException(
                        "Imported data does not contain " + DBDefinitions.KEY_DATE_LAST_UPDATED);
            }
            updateOnlyIfNewer = true;
        } else {
            updateOnlyIfNewer = false;
        }

        // Start after headings.
        int row = 1;
        int txRowCount = 0;
        long lastUpdate = 0;

        // Iterate through each imported row
        SyncLock txLock = null;
        try {
            while (row < importedList.size() && !listener.isCancelled()) {
                // every 10 inserted, we commit the transaction
                if (mDb.inTransaction() && txRowCount > 10) {
                    mDb.setTransactionSuccessful();
                    //noinspection ConstantConditions
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

                // Validate IDs
                BookIds bids = new BookIds(book);
                // check title
                requireNonBlankOrThrow(book, row, DBDefinitions.KEY_TITLE);
                final String title = book.getString(DBDefinitions.KEY_TITLE);
                // Lookup id's etc, but do not write to db! Storing the book data does all that
                handleAuthors(mDb, book);
                handleSeries(mDb, book);
                if (book.containsKey(CsvExporter.CSV_COLUMN_TOC)) {
                    handleAnthology(mDb, book);
                }

                // v5 has columns
                // "bookshelf_id" == UniqueId.DOM_FK_BOOKSHELF_ID
                // => ignore, we don't / can't use it anyhow.
                // "bookshelf" == UniqueId.KEY_BOOKSHELF
                // I suspect "bookshelf_text" is from older versions and obsolete now (Classic ?)
                if (book.containsKey(DBDefinitions.KEY_BOOKSHELF)
                        && !book.containsKey(LEGACY_BOOKSHELF_TEXT_COLUMN)) {
                    handleBookshelves(mDb, book);
                }

                try {
                    // Save the original ID from the file for use in checking for images
                    long bookIdFromFile = bids.bookId;
                    // go!
                    bids.bookId = importBook(book, bids, updateOnlyIfNewer);

                    // When importing a file that has an ID or UUID, try to import a cover.
                    if (coverFinder != null) {
                        if (!bids.uuid.isEmpty()) {
                            coverFinder.copyOrRenameCoverFile(bids.uuid);
                        } else {
                            coverFinder.copyOrRenameCoverFile(bookIdFromFile,
                                                              mDb.getBookUuid(bids.bookId));
                        }
                    }
                } catch (IOException e) {
                    Logger.error(this, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                } catch (SQLiteDoneException e) {
                    Logger.error(this, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                } catch (RuntimeException e) {
                    Logger.error(this, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                }

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200 && !listener.isCancelled()) {
                    String msg = mContext.getString(R.string.progress_msg_n_created_m_updated,
                                                    mCreated, mUpdated);
                    listener.onProgress(title + "\n(" + msg + ')', row);
                    lastUpdate = now;
                }

                row++;
            } // end while

        } catch (ImportException e) {
            throw new RuntimeException(e);
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                //noinspection ConstantConditions
                mDb.endTransaction(txLock);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            // minus 1 for the headers.
            Logger.debugExit(this, "doBooks",
                             "Csv Import successful: rows processed: " + (row - 1),
                             "created:" + mCreated,
                             "updated: " + mUpdated);
        }

        return row;
    }

    @Override
    public void close() {
        try {
            // do some cleaning
            mDb.purge();
        } catch (RuntimeException e) {
            Logger.error(this, e);
        }
        mDb.close();
    }

    /**
     * insert or update a single book.
     */
    private long importBook(@NonNull final Book book,
                            @NonNull final BookIds bids,
                            final boolean updateOnlyIfNewer) {

        final boolean hasUuid = !bids.uuid.isEmpty();

        // Always import empty IDs...even if they are duplicates.
        // Would be nice to import a cover, but without ID/UUID that is not possible
        if (!hasUuid && !bids.hasNumericId) {
            return mDb.insertBook(book);
        }

        // we have UUID or ID, so it's an update

        // Let the UUID trump the ID; we may be importing someone else's list with bogus IDs
        boolean exists = false;

        if (hasUuid) {
            long tmp_id = mDb.getBookIdFromUuid(bids.uuid);
            if (tmp_id != 0) {
                // use the id from the existing book, as found by UUID lookup
                bids.bookId = tmp_id;
                exists = true;
            } else {
                // We have a UUID, but book does not exist. We will create a book.
                // Make sure the ID (if present) is not already used.
                if (bids.hasNumericId && mDb.bookExists(bids.bookId)) {
                    bids.bookId = 0;
                }
            }
        } else {
            exists = mDb.bookExists(bids.bookId);
        }

        if (exists) {
            if (!updateOnlyIfNewer || updateOnlyIfNewer(mDb, book, bids.bookId)) {
                mDb.updateBook(bids.bookId, book, DBA.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                mUpdated++;
            }
        } else {
            bids.bookId = mDb.insertBook(bids.bookId, book);
            mCreated++;
        }

        return bids.bookId;
    }

    private boolean updateOnlyIfNewer(@NonNull final DBA db,
                                      @NonNull final Book book,
                                      final long bookId) {

        Date bookDate = DateUtils.parseDate(db.getBookLastUpdateDate(bookId));
        Date importDate = DateUtils.parseDate(book.getString(DBDefinitions.KEY_DATE_LAST_UPDATED));

        return importDate != null && (bookDate == null || importDate.compareTo(bookDate) > 0);
    }

    /**
     * Database access is strictly limited to fetching id's.
     * <p>
     * Get the list of bookshelves.
     */
    private void handleBookshelves(@NonNull final DBA db,
                                   @NonNull final Book book) {
        String encodedList = book.getString(DBDefinitions.KEY_BOOKSHELF);
        ArrayList<Bookshelf> list = StringList.getBookshelfCoder()
                                              .decode(Bookshelf.MULTI_SHELF_SEPARATOR, encodedList,
                                                      false);
        book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);

        book.remove(DBDefinitions.KEY_BOOKSHELF);
        book.remove(LEGACY_BOOKSHELF_TEXT_COLUMN);
        book.remove("bookshelf_id");
    }

    /**
     * Database access is strictly limited to fetching id's.
     * <p>
     * Ignore the actual value of the UniqueId.KEY_TOC_BITMASK! it will be
     * 'reset' to mirror what we actually have when storing the book data
     */
    private void handleAnthology(@NonNull final DBA db,
                                 @NonNull final Book book) {

        String encodedList = book.getString(CsvExporter.CSV_COLUMN_TOC);
        if (!encodedList.isEmpty()) {
            ArrayList<TocEntry> list = StringList.getTocCoder().decode(encodedList, false);
            if (!list.isEmpty()) {
                // fixup the id's
                Utils.pruneList(db, list);
                book.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, list);
            }
        }

        // remove the unneeded string encoded set
        book.remove(CsvExporter.CSV_COLUMN_TOC);
    }

    /**
     * Database access is strictly limited to fetching id's.
     * <p>
     * Get the list of series from whatever source is available.
     */
    private void handleSeries(@NonNull final DBA db,
                              @NonNull final Book book) {
        String encodedList = book.getString(CsvExporter.CSV_COLUMN_SERIES);
        if (encodedList.isEmpty()) {
            // Try to build from SERIES_NAME and SERIES_NUM. It may all be blank
            if (book.containsKey(DBDefinitions.KEY_SERIES)) {
                encodedList = book.getString(DBDefinitions.KEY_SERIES);
                if (!encodedList.isEmpty()) {
                    String seriesNum = book.getString(DBDefinitions.KEY_SERIES_NUM);
                    encodedList += '(' + seriesNum + ')';
                } else {
                    encodedList = null;
                }
            }
        }

        // Handle the series
        final ArrayList<Series> list = StringList.getSeriesCoder().decode(encodedList, false);
        Series.pruneSeriesList(list);
        Utils.pruneList(db, list);
        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
        book.remove(CsvExporter.CSV_COLUMN_SERIES);
    }

    /**
     * Database access is strictly limited to fetching id's.
     * <p>
     * Get the list of authors from whatever source is available.
     */
    private void handleAuthors(@NonNull final DBA db,
                               @NonNull final Book book) {
        // preferred & used in latest versions
        String encodedList = book.getString(CsvExporter.CSV_COLUMN_AUTHORS);
        if (encodedList.isEmpty()) {
            // Need to build it from other/older fields.

            if (book.containsKey(DBDefinitions.KEY_AUTHOR_FAMILY_NAME)) {
                // Build from family/given
                encodedList = book.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                String given = "";
                if (book.containsKey(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES)) {
                    given = book.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                }
                if (!given.isEmpty()) {
                    encodedList += ", " + given;
                }

            } else if (book.containsKey(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                encodedList = book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);

            } else if (book.containsKey(OLD_STYLE_AUTHOR_NAME)) {
                encodedList = book.getString(OLD_STYLE_AUTHOR_NAME);
            }
        }

        // A pre-existing bug sometimes results in blank author-details due to bad underlying data
        // (it seems a 'book' record gets written without an 'author' record; should not happen)
        // so we allow blank author_details and fill in a regional version of "Author, Unknown"
        if (encodedList.isEmpty()) {
            encodedList = mContext.getString(R.string.lbl_author) + ", "
                    + mContext.getString(R.string.unknown);
        }

        // Now build the array for authors
        final ArrayList<Author> list = StringList.getAuthorCoder().decode(encodedList, false);
        Utils.pruneList(db, list);
        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        book.remove(CsvExporter.CSV_COLUMN_AUTHORS);
    }

    /**
     * This CSV parser is not a complete parser, but it will parse files exported by older
     * versions. At some stage in the future it would be good to allow full CSV export
     * and import to allow for escape('\') chars so that cr/lf can be preserved.
     *
     * @param row          with CSV
     * @param fullEscaping if {@code true} handle the import as a version 3.4+;
     *                     if {@code false} handle as an earlier version.
     *
     * @return an array representing the row
     */
    @NonNull
    private String[] returnRow(@NonNull final String row,
                               final boolean fullEscaping) {
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
        char next = (row.isEmpty()) ? '\0' : row.charAt(0);
        // Last position in row
        int endPos = row.length() - 1;
        // Array of fields found in row
        final List<String> fields = new ArrayList<>();
        // Temp. storage for current field
        StringBuilder sb = new StringBuilder();

        while (next != '\0') {
            // Get current and next char
            c = next;
            next = (pos < endPos) ? row.charAt(pos + 1) : '\0';

            // If we are 'escaped', just append the char, handling special cases
            if (inEsc) {
                sb.append(unescape(c));
                inEsc = false;
            } else if (inQuote) {
                switch (c) {
                    case QUOTE_CHAR:
                        if (next == QUOTE_CHAR) {
                            // Double-quote: Advance one more and append a single quote
                            pos++;
                            next = (pos < endPos) ? row.charAt(pos + 1) : '\0';
                            sb.append(c);
                        } else {
                            // Leave the quote
                            inQuote = false;
                        }
                        break;
                    case ESCAPE_CHAR:
                        if (fullEscaping) {
                            inEsc = true;
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            } else {
                // This is just a raw string; no escape or quote active.
                // Ignore leading space.
                if ((c != ' ' && c != '\t') || sb.length() != 0) {
                    switch (c) {
                        case QUOTE_CHAR:
                            if (sb.length() > 0) {
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
                                sb.append(c);
                            }
                            break;
                        case SEPARATOR:
                            // Add this field and reset it.
                            fields.add(sb.toString());
                            sb = new StringBuilder();
                            break;
                        default:
                            // Just append the char
                            sb.append(c);
                            break;
                    }
                }
            }
            pos++;
        }

        // Add the remaining chunk
        fields.add(sb.toString());

        // Return the result as a String[].
        String[] imported = new String[fields.size()];
        fields.toArray(imported);

        return imported;
    }

    private char unescape(final char c) {
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
     * Require a column to be present. First one found; remainders are not needed.
     */
    private void requireColumnOrThrow(@NonNull final Book book,
                                      @NonNull final String... names)
            throws ImportException {
        for (String name : names) {
            if (book.containsKey(name)) {
                return;
            }
        }

        throw new ImportException(
                mContext.getString(R.string.import_error_csv_file_must_contain_any_column,
                                   TextUtils.join(",", names)));
    }

    private void requireNonBlankOrThrow(@NonNull final Book book,
                                        final int row,
                                        @SuppressWarnings("SameParameterValue")
                                        @NonNull final String name)
            throws ImportException {
        if (!book.getString(name).isEmpty()) {
            return;
        }
        throw new ImportException(mContext.getString(R.string.error_column_is_blank, name, row));
    }

    @SuppressWarnings("unused")
    private void requireAnyNonBlankOrThrow(@NonNull final Book book,
                                           final int row,
                                           @NonNull final String... names)
            throws ImportException {
        for (String name : names) {
            if (book.containsKey(name) && !book.getString(name).isEmpty()) {
                return;
            }
        }

        throw new ImportException(mContext.getString(R.string.error_columns_are_blank,
                                                     TextUtils.join(",", names), row));
    }

    /**
     * Holder class for identifiers of a book during import.
     * (created to let lint work)
     */
    private static class BookIds {

        final String uuid;
        long bookId;
        boolean hasNumericId;

        /**
         * Constructor. All work is done here to populate the member variables.
         *
         * @param book the book, will be modified.
         */
        BookIds(@NonNull final Book /* in/out */ book) {
            // why String ? See book init, we store all keys we find in the import
            // file as text simple to see if they are present.
            final String idStr = book.getString(STRINGED_ID);
            hasNumericId = !idStr.isEmpty();

            if (hasNumericId) {
                try {
                    bookId = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    // don't log, it's fine.
                    hasNumericId = false;
                }
            }

            if (!hasNumericId) {
                // yes, string, see above
                book.putString(STRINGED_ID, "0");
            }

            // Get the UUID, and remove from collection if null/blank
            uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);
            if (uuid.isEmpty()) {
                // Remove any blank UUID column, just in case
                if (book.containsKey(DBDefinitions.KEY_BOOK_UUID)) {
                    book.remove(DBDefinitions.KEY_BOOK_UUID);
                }
            }
        }
    }
}
