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
import com.eleybourn.bookcatalogue.entities.BookData;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ImportThread.ImportException;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
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
 * reminder: use UniqueId.KEY, not DOM.
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

    private final CatalogueDBAdapter mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
    private Integer mCreated = 0;
    private Integer mUpdated = 0;

    @SuppressWarnings("UnusedReturnValue")
    public boolean importBooks(@NonNull final InputStream exportStream,
                               @Nullable final Importer.CoverFinder coverFinder,
                               @NonNull final Importer.OnImporterListener listener,
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

        // Container for values.
        final BookData bookData = new BookData();

        // first line in import are the column names
        final String[] csvColumnNames = returnRow(importedList.get(0), true);
        // Store the names so we can check what is present
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase();
            bookData.putString(csvColumnNames[i], "");
        }

        // See if we can deduce the kind of escaping to use based on column names.
        // Version 1->3.3 export with family_name and author_id. Version 3.4+ do not; latest versions
        // make an attempt at escaping characters etc to preserve formatting.
        boolean fullEscaping = !bookData.containsKey(UniqueId.KEY_AUTHOR_ID) || !bookData.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME);

        // Make sure required fields are present.
        // ENHANCE: Rationalize import to allow updates using 1 or 2 columns. For now we require complete data.
        // ENHANCE: Do a search if mandatory columns missing (eg. allow 'import' of a list of ISBNs).
        // ENHANCE: Only make some columns mandatory if the ID is not in import, or not in DB (ie. if not an update)
        // ENHANCE: Export/Import should use GUIDs for book IDs, and put GUIDs on Image file names.
        requireColumnOrThrow(bookData,
                UniqueId.KEY_ID,
                UniqueId.KEY_BOOK_UUID,
                UniqueId.KEY_AUTHOR_FAMILY_NAME,
                UniqueId.KEY_AUTHOR_FORMATTED,
                UniqueId.KEY_AUTHOR_NAME,
                UniqueId.BKEY_AUTHOR_DETAILS);

        final boolean updateOnlyIfNewer;
        if ((importFlags & Importer.IMPORT_NEW_OR_UPDATED) != 0) {
            if (!bookData.containsKey(UniqueId.KEY_LAST_UPDATE_DATE)) {
                throw new IllegalArgumentException("Imported data does not contain " + UniqueId.KEY_LAST_UPDATE_DATE);
            }
            updateOnlyIfNewer = true;
        } else {
            updateOnlyIfNewer = false;
        }

        mDb.open();

        int row = 1; // Start after headings.
        int txRowCount = 0;
        long lastUpdate = 0;

        /* Iterate through each imported row */
        SyncLock syncLock = null;
        try {
            while (row < importedList.size() && listener.isActive()) {
                // every 10 inserted, we commit the transaction
                if (mDb.inTransaction() && txRowCount > 10) {
                    mDb.setTransactionSuccessful();
                    mDb.endTransaction(syncLock);
                }
                if (!mDb.inTransaction()) {
                    syncLock = mDb.startTransaction(true);
                    txRowCount = 0;
                }
                txRowCount++;

                // Get row
                final String[] csvDataRow = returnRow(importedList.get(row), fullEscaping);
                // and add each field into bookData
                bookData.clear();
                for (int i = 0; i < csvColumnNames.length; i++) {
                    bookData.putString(csvColumnNames[i], csvDataRow[i]);
                }

                // Validate ID
                // why String ? See bookData init, we store all keys we find in the import file as text simple to see if they are present.
                final String idStr = bookData.getString(STRINGED_ID);
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
                    bookData.putString(STRINGED_ID, "0"); // yes, string, see above
                }

                // Get the UUID, and remove from collection if null/blank
                final String uuidVal = bookData.getString(UniqueId.KEY_BOOK_UUID);
                final boolean hasUuid = (uuidVal != null && !uuidVal.isEmpty());
                if (!hasUuid) {
                    // Remove any blank UUID column, just in case
                    if (bookData.containsKey(UniqueId.KEY_BOOK_UUID)) {
                        bookData.remove(UniqueId.KEY_BOOK_UUID);
                    }
                }

                requireNonBlankOrThrow(bookData, row, UniqueId.KEY_TITLE);
                final String title = bookData.getString(UniqueId.KEY_TITLE);

                // Keep author handling local
                handleAuthors(mDb, bookData);

                // Keep series handling local
                handleSeries(mDb, bookData);

                // Keep anthology handling local
                if (bookData.containsKey(UniqueId.KEY_ANTHOLOGY_MASK)) {
                    handleAnthology(mDb, bookData);
                }

                // Make sure we have UniqueId.BKEY_BOOKSHELF_TEXT if we imported bookshelf
                if (bookData.containsKey(UniqueId.KEY_BOOKSHELF_NAME) && !bookData.containsKey(UniqueId.BKEY_BOOKSHELF_TEXT)) {
                    bookData.setBookshelfList(bookData.getString(UniqueId.KEY_BOOKSHELF_NAME));
                }

                try {
                    // Save the original ID from the file for use in checking for images
                    long bookIdFromFile = bookId;

                    bookId = importBook(bookId, hasNumericId, uuidVal, hasUuid, bookData, updateOnlyIfNewer);

                    // When importing a file that has an ID or UUID, try to import a cover.
                    if (coverFinder != null) {
                        coverFinder.copyOrRenameCoverFile(uuidVal, bookIdFromFile, bookId);
                    }
                } catch (IOException e) {
                    Logger.logError(e, "Cover import failed at row " + row);
                } catch (Exception e) {
                    Logger.logError(e, "Import failed at row " + row);
                }

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200 && listener.isActive()) {
                    listener.onProgress(title + "\n(" + BookCatalogueApp.getResourceString(R.string.n_created_m_updated, mCreated, mUpdated) + ")", row);
                    lastUpdate = now;
                }

                row++;
            }
        } catch (Exception e) {
            Logger.logError(e);
            throw new RuntimeException(e);
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(syncLock);
            }
            try {
                mDb.purgeAuthors();
                mDb.purgeSeries();
                mDb.analyzeDb();
            } catch (Exception e) {
                Logger.logError(e);
            }
            try {
                mDb.close();
            } catch (Exception e) {
                Logger.logError(e);
            }
        }

        return true;
    }

    /**
     * @return new or updated bookId
     *
     * @throws Exception on failure
     */
    private long importBook(long bookId,
                            final boolean hasNumericId,
                            final String uuidVal,
                            final boolean hasUuid,
                            final BookData bookData,
                            final boolean updateOnlyIfNewer) throws Exception {
        if (!hasUuid && !hasNumericId) {
            // Always import empty IDs...even if they are duplicates.
            long id = mDb.insertBookWithId(0, bookData);
            bookData.putLong(UniqueId.KEY_ID, id);
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
                if (!updateOnlyIfNewer || updateOnlyIfNewer(mDb, bookData, bookId)) {
                    mDb.updateBook(bookId, bookData,
                            CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES
                                    | CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                    mUpdated++;
                }
            } else {
                bookId = mDb.insertBookWithId(bookId, bookData);
                mCreated++;
            }

            // Save the real ID to the collection (will/may be used later)
            bookData.putLong(UniqueId.KEY_ID, bookId);

        }
        return bookId;
    }


    private boolean updateOnlyIfNewer(@NonNull final CatalogueDBAdapter db,
                                      @NonNull final BookData bookData,
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

        String importDateStr = bookData.getString(UniqueId.KEY_LAST_UPDATE_DATE);

        Date importDate = null;
        if (importDateStr != null && !importDateStr.isEmpty()) {
            try {
                importDate = DateUtils.parseDate(importDateStr);
            } catch (Exception ignore) {
                // Treat as if never updated
            }
        }
        return importDate != null && (bookDate == null || importDate.compareTo(bookDate) > 0);
    }

    /**
     * Database access is strictly limited to fetching id's
     */
    private void handleAnthology(@NonNull final CatalogueDBAdapter db,
                                 @NonNull final BookData bookData) {
        // see if the book is marked as an anthology.
        long anthologyMask = 0;
        try {
            anthologyMask = bookData.getLong(UniqueId.KEY_ANTHOLOGY_MASK);
        } catch (NumberFormatException ignore) {
            bookData.remove(UniqueId.KEY_ANTHOLOGY_MASK);
        }

        if (anthologyMask != 0) {
            long bookId = bookData.getLong(UniqueId.KEY_ID);
            String encodedString =  bookData.getString(UniqueId.BKEY_ANTHOLOGY_DETAILS);
            if (encodedString != null && !encodedString.isEmpty()) {
                String[] anthology_titles = encodedString.split("\\" + ArrayUtils.MULTI_STRING_SEPARATOR);
                // There is *always* one; but it will be empty if no titles present
                if (!anthology_titles[0].isEmpty()) {
                    ArrayList<AnthologyTitle> ata = new ArrayList<>();
                    for (String title : anthology_titles) {
                        ata.add(new AnthologyTitle(title, bookId));
                    }
                    // fixup the id's
                    Utils.pruneList(db, ata);
                    bookData.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, ata);
                }
            }
        }
        // remove the unneeded string encoded set
        bookData.remove(UniqueId.BKEY_ANTHOLOGY_DETAILS);
    }

    /**
     * Database access is strictly limited to fetching id's
     */
    private void handleSeries(@NonNull final CatalogueDBAdapter db,
                              @NonNull final BookData bookData) {
        String seriesDetails = bookData.getString(UniqueId.BKEY_SERIES_DETAILS);
        if (seriesDetails == null || seriesDetails.isEmpty()) {
            // Try to build from SERIES_NAME and SERIES_NUM. It may all be blank
            if (bookData.containsKey(UniqueId.KEY_SERIES_NAME)) {
                seriesDetails = bookData.getString(UniqueId.KEY_SERIES_NAME);
                if (seriesDetails != null && !seriesDetails.isEmpty()) {
                    String seriesNum = bookData.getString(UniqueId.KEY_SERIES_NUM);
                    if (seriesNum == null) {
                        seriesNum = "";
                    }
                    seriesDetails += "(" + seriesNum + ")";
                } else {
                    seriesDetails = null;
                }
            }
        }
        // Handle the series
        final ArrayList<Series> sa = ArrayUtils.getSeriesUtils().decodeList(ArrayUtils.MULTI_STRING_SEPARATOR, seriesDetails, false);
        Series.pruneSeriesList(sa);
        Utils.pruneList(db, sa);
        bookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, sa);
    }

    /**
     * Database access is strictly limited to fetching id's
     */
    private void handleAuthors(@NonNull final CatalogueDBAdapter db,
                               @NonNull final BookData bookData) {
        // Get the list of authors from whatever source is available.
        String authorDetails = bookData.getString(UniqueId.BKEY_AUTHOR_DETAILS);
        if (authorDetails == null || authorDetails.isEmpty()) {
            // Need to build it from other fields.
            if (bookData.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
                // Build from family/given
                authorDetails = bookData.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
                String given = "";
                if (bookData.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                    given = bookData.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
                }
                if (given != null && !given.isEmpty()) {
                    authorDetails += ", " + given;
                }
            } else if (bookData.containsKey(UniqueId.KEY_AUTHOR_NAME)) {
                authorDetails = bookData.getString(UniqueId.KEY_AUTHOR_NAME);
            } else if (bookData.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {
                authorDetails = bookData.getString(UniqueId.KEY_AUTHOR_FORMATTED);
            }
        }

        // A pre-existing bug sometimes results in blank author-details due to bad underlying data
        // (it seems a 'book' record gets written without an 'author' record; should not happen)
        // so we allow blank author_details and full in a regional version of "Author, Unknown"
        if (authorDetails == null || authorDetails.isEmpty()) {
            authorDetails = BookCatalogueApp.getResourceString(R.string.author) + ", " + BookCatalogueApp.getResourceString(R.string.unknown);
            //String s = BookCatalogueApp.getResourceString(R.string.column_is_blank);
            //throw new ImportException(String.format(s, DatabaseDefinitions.BKEY_AUTHOR_DETAILS, row));
        }

        // Now build the array for authors
        final ArrayList<Author> aa = ArrayUtils.getAuthorUtils().decodeList(ArrayUtils.MULTI_STRING_SEPARATOR, authorDetails, false);
        Utils.pruneList(db, aa);
        bookData.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, aa);
    }

    //
    // This CSV parser is not a complete parser, but it will parse files exported by older
    // versions. At some stage in the future it would be good to allow full CSV export
    // and import to allow for escape('\') chars so that cr/lf can be preserved.
    //
    private String[] returnRow(@NonNull final String row, final boolean fullEscaping) {
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

    /** Require a column */
    private void requireColumnOrThrow(@NonNull final BookData bookData,
                                      String... names) throws ImportException {
        for (String name : names) {
            if (bookData.containsKey(name)) {
                return;
            }
        }

        throw new ImportException(BookCatalogueApp.getResourceString(R.string.file_must_contain_any_column,
                Utils.join(",", names)));
    }

    private void requireNonBlankOrThrow(@NonNull final BookData bookData,
                                        final int row,
                                        @SuppressWarnings("SameParameterValue") @NonNull final String name)
            throws ImportException {
        if (!bookData.getString(name).isEmpty()) {
            return;
        }
        throw new ImportException(BookCatalogueApp.getResourceString(R.string.column_is_blank, name, row));
    }

    @SuppressWarnings("unused")
    private void requireAnyNonBlankOrThrow(@NonNull final BookData bookData,
                                           final int row,
                                           @NonNull final String... names) throws ImportException {
        for (String name : names) {
            if (bookData.containsKey(name) && !bookData.getString(name).isEmpty()) {
                return;
            }
        }

        throw new ImportException(BookCatalogueApp.getResourceString(R.string.columns_are_blank, Utils.join(",", names), row));
    }

}
