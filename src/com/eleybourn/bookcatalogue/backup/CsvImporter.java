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

import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.backup.ImportThread.ImportException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;

import static com.eleybourn.bookcatalogue.UniqueId.BKEY_AUTHOR_ARRAY;
import static com.eleybourn.bookcatalogue.UniqueId.BKEY_AUTHOR_DETAILS;
import static com.eleybourn.bookcatalogue.UniqueId.BKEY_SERIES_ARRAY;
import static com.eleybourn.bookcatalogue.UniqueId.BKEY_SERIES_DETAILS;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_BOOKSHELF;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_BOOK_UUID;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_ID;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_LOANED_TO;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_SERIES_NAME;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_SERIES_NUM;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_TITLE;

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

    @SuppressWarnings("UnusedReturnValue")
    public boolean importBooks(@NonNull final InputStream exportStream,
                               @Nullable final Importer.CoverFinder coverFinder,
                               @NonNull final Importer.OnImporterListener listener,
                               final int importFlags) throws IOException {
        final ArrayList<String> importedString = new ArrayList<>();

        final BufferedReader in = new BufferedReader(new InputStreamReader(exportStream, UTF8), BUFFER_SIZE);
        String line;
        while ((line = in.readLine()) != null) {
            importedString.add(line);
        }

        return importBooks(importedString, coverFinder, listener, importFlags);
    }

    @SuppressWarnings("SameReturnValue")
    private boolean importBooks(@NonNull final ArrayList<String> export,
                                @Nullable final Importer.CoverFinder coverFinder,
                                @NonNull final Importer.OnImporterListener listener,
                                final int importFlags) {

        if (export == null || export.size() == 0)
            return true;

        Integer nCreated = 0;
        Integer nUpdated = 0;

        listener.setMax(export.size() - 1);

        // Container for values.
        final BookData values = new BookData();

        final String[] names = returnRow(export.get(0), true);

        // Store the names so we can check what is present
        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].toLowerCase();
            values.putString(names[i], "");
        }

        // See if we can deduce the kind of escaping to use based on column names.
        // Version 1->3.3 export with family_name and author_id. Version 3.4+ do not; latest versions
        // make an attempt at escaping characters etc to preserve formatting.
        boolean fullEscaping = !values.containsKey(KEY_AUTHOR_ID) || !values.containsKey(KEY_AUTHOR_FAMILY_NAME);

        // Make sure required fields are present.
        // ENHANCE: Rationalize import to allow updates using 1 or 2 columns. For now we require complete data.
        // ENHANCE: Do a search if mandatory columns missing (eg. allow 'import' of a list of ISBNs).
        // ENHANCE: Only make some columns mandatory if the ID is not in import, or not in DB (ie. if not an update)
        // ENHANCE: Export/Import should use GUIDs for book IDs, and put GUIDs on Image file names.
        requireColumnOr(values,
                KEY_ID,
                KEY_BOOK_UUID,
                KEY_AUTHOR_FAMILY_NAME,
                KEY_AUTHOR_FORMATTED,
                KEY_AUTHOR_NAME,
                BKEY_AUTHOR_DETAILS);

        boolean updateOnlyIfNewer;
        if ((importFlags & Importer.IMPORT_NEW_OR_UPDATED) != 0) {
            if (!values.containsKey(KEY_LAST_UPDATE_DATE)) {
                throw new RuntimeException("Imported data does not contain " + KEY_LAST_UPDATE_DATE);
            }
            updateOnlyIfNewer = true;
        } else {
            updateOnlyIfNewer = false;
        }


        final CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();

        int row = 1; // Start after headings.
        boolean inTransaction = false;
        int txRowCount = 0;

        long lastUpdate = 0;
        /* Iterate through each imported row */
        SyncLock txLock = null;
        try {
            while (row < export.size() && !listener.isCancelled()) {
                if (inTransaction && txRowCount > 10) {
                    db.setTransactionSuccessful();
                    db.endTransaction(txLock);
                    inTransaction = false;
                }
                if (!inTransaction) {
                    txLock = db.startTransaction(true);
                    inTransaction = true;
                    txRowCount = 0;
                }
                txRowCount++;

                // Get row
                final String[] imported = returnRow(export.get(row), fullEscaping);

                values.clear();
                for (int i = 0; i < names.length; i++) {
                    values.putString(names[i], imported[i]);
                }

                boolean hasNumericId;
                // Validate ID
                final String idStr = values.getString(KEY_ID.toLowerCase());
                Long idLong;
                if (idStr == null || idStr.isEmpty()) {
                    hasNumericId = false;
                    idLong = 0L;
                } else {
                    try {
                        idLong = Long.parseLong(idStr);
                        hasNumericId = true;
                    } catch (Exception e) {
                        hasNumericId = false;
                        idLong = 0L;
                    }
                }
                if (!hasNumericId) {
                    values.putString(KEY_ID, "0");
                }

                // Get the UUID, and remove from collection if null/blank
                boolean hasUuid;
                final String uuidColumnName = KEY_BOOK_UUID;
                final String uuidVal = values.getString(uuidColumnName);
                if (uuidVal != null && !uuidVal.isEmpty()) {
                    hasUuid = true;
                } else {
                    // Remove any blank UUID column, just in case
                    if (values.containsKey(uuidColumnName))
                        values.remove(uuidColumnName);
                    hasUuid = false;
                }

                requireNonBlank(values, row, KEY_TITLE);
                final String title = values.getString(KEY_TITLE);

                // Keep author handling stuff local
                importBooks_handleAuthors(db, values);

                // Keep series handling local
                importBooks_handleSeries(db, values);


                // Make sure we have bookshelf_text if we imported bookshelf
                if (values.containsKey(KEY_BOOKSHELF) && !values.containsKey("bookshelf_text")) {
                    values.setBookshelfList(values.getString(KEY_BOOKSHELF));
                }

                try {
                    boolean doUpdate;
                    if (!hasUuid && !hasNumericId) {
                        doUpdate = true;
                        // Always import empty IDs...even if they are duplicates.
                        Long id = db.createBook(values, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                        values.putString(KEY_ID, id.toString());
                        // Would be nice to import a cover, but with no ID/UUID that is not possible
                        //mImportCreated++;
                    } else {
                        boolean exists;
                        // Save the original ID from the file for use in checking for images
                        Long idFromFile = idLong;
                        // newId will get the ID allocated if a book is created
                        Long newId;

                        // Let the UUID trump the ID; we may be importing someone else's list with bogus IDs
                        if (hasUuid) {
                            Long l = db.getBookIdFromUuid(uuidVal);
                            if (l != 0) {
                                exists = true;
                                idLong = l;
                            } else {
                                exists = false;
                                // We have a UUID, but book does not exist. We will create a book.
                                // Make sure the ID (if present) is not already used.
                                if (hasNumericId && db.checkBookExists(idLong))
                                    idLong = 0L;
                            }
                        } else {
                            exists = db.checkBookExists(idLong);
                        }

                        if (exists) {
                            doUpdate = !updateOnlyIfNewer
                                    || importBooks_updateOnlyIfNewer(db, values, idLong);

                            if (doUpdate) {
                                db.updateBook(idLong, values,
                                        CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES
                                                | CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                                nUpdated++;
                            }
                            //mImportUpdated++;
                        } else {
                            doUpdate = true;
                            newId = db.createBook(idLong, values, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                            nCreated++;
                            //mImportCreated++;
                            values.putString(KEY_ID, newId.toString());
                            idLong = newId;
                        }

                        // When importing a file that has an ID or UUID, try to import a cover.
                        if (coverFinder != null) {
                            coverFinder.copyOrRenameCoverFile(uuidVal, idFromFile, idLong);
                        }
                        // Save the real ID to the collection (will/may be used later)
                        values.putString(KEY_ID, idLong.toString());
                    }

                    if (doUpdate) {
                        if (values.containsKey(KEY_LOANED_TO) && !"".equals(values.get(KEY_LOANED_TO))) {
                            importBooks_handleLoan(db, values);
                        }

                        if (values.containsKey(KEY_ANTHOLOGY_MASK)) {
                            importBooks_handleAnthology(db, values);
                        }
                    }

                } catch (Exception e) {
                    Logger.logError(e, "Import at row " + row);
                }

                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200 && !listener.isCancelled()) {
                    listener.onProgress(title + "\n(" + BookCatalogueApp.getResourceString(R.string.n_created_m_updated, nCreated, nUpdated) + ")", row);
                    lastUpdate = now;
                }

                // Increment row count
                row++;
            }
        } catch (Exception e) {
            Logger.logError(e);
            throw new RuntimeException(e);
        } finally {
            if (inTransaction) {
                db.setTransactionSuccessful();
                db.endTransaction(txLock);
            }
            try {
                db.purgeAuthors();
                db.purgeSeries();
                db.analyzeDb();
            } catch (Exception ignore) {
                // Do nothing. Not a critical step.
                Logger.logError(ignore);
            }
            try {
                db.close();
            } catch (Exception ignore) {
                // Do nothing. Not a critical step.
                Logger.logError(ignore);
            }
        }

        return true;

// XXX: Make sure this is replicated
//		if (listener.isCancelled()) {
//			doToast(getString(R.string.cancelled));
//		} else {
//			doToast(getString(R.string.import_complete));
//		}
    }

    private boolean importBooks_updateOnlyIfNewer(@NonNull final CatalogueDBAdapter db,
                                                  @NonNull final BookData values,
                                                  @NonNull final Long idLong) {
        Date bookDate;
        Date importDate;
        String bookDateStr = db.getBookUpdateDate(idLong);
        if (bookDateStr == null || bookDateStr.isEmpty()) {
            bookDate = null; // Local record has never been updated
        } else {
            try {
                bookDate = DateUtils.parseDate(bookDateStr);
            } catch (Exception e) {
                bookDate = null; // Treat as if never updated
            }
        }
        String importDateStr = values.getString(KEY_LAST_UPDATE_DATE);
        if (importDateStr == null || importDateStr.isEmpty()) {
            importDate = null; // Imported record has never been updated
        } else {
            try {
                importDate = DateUtils.parseDate(importDateStr);
            } catch (Exception e) {
                importDate = null; // Treat as if never updated
            }
        }
        return importDate != null
                && (bookDate == null || importDate.compareTo(bookDate) > 0);
    }

    private void importBooks_handleAnthology(@NonNull final CatalogueDBAdapter db,
                                             @NonNull final BookData values) {
        int anthology;
        try {
            anthology = Integer.parseInt(values.getString(KEY_ANTHOLOGY_MASK));
        } catch (NumberFormatException ignore) {
            anthology = 0;
        }
        if (anthology != 0) {
            long id = Integer.parseInt(values.getString(KEY_ID));
            // We have anthology details, delete the current details.
            db.deleteAnthologyTitlesByBook(id, false);
            int oldi = 0;
            String anthology_titles = values.getString(UniqueId.BKEY_ANTHOLOGY_TITLES);
            try {
                int i = anthology_titles.indexOf("|", oldi);
                while (i > -1) {
                    String extracted_title = anthology_titles.substring(oldi, i).trim();

                    int j = extracted_title.indexOf("*");
                    if (j > -1) {
                        String ant_title = extracted_title.substring(0, j).trim();
                        String ant_author = extracted_title.substring((j + 1)).trim();

                        Author author = Author.toAuthor(ant_author);
                        db.insertAnthologyTitle(id, author, ant_title, true, false);
                    }
                    oldi = i + 1;
                    i = anthology_titles.indexOf("|", oldi);
                }
            } catch (NullPointerException ignore) {
                //do nothing. There are no anthology titles
            }
        }
    }

    private void importBooks_handleLoan(@NonNull final CatalogueDBAdapter db, @NonNull final BookData values) {
        int id = Integer.parseInt(values.getString(KEY_ID));
        db.deleteLoan(id, false);
        db.insertLoan(values, false);
    }

    private void importBooks_handleSeries(@NonNull final CatalogueDBAdapter db, @NonNull final BookData values) {
        String seriesDetails = values.getString(BKEY_SERIES_DETAILS);
        if (seriesDetails == null || seriesDetails.isEmpty()) {
            // Try to build from SERIES_NAME and SERIES_NUM. It may all be blank
            if (values.containsKey(KEY_SERIES_NAME)) {
                seriesDetails = values.getString(KEY_SERIES_NAME);
                if (seriesDetails != null && !seriesDetails.isEmpty()) {
                    String seriesNum = values.getString(KEY_SERIES_NUM);
                    if (seriesNum == null)
                        seriesNum = "";
                    seriesDetails += "(" + seriesNum + ")";
                } else {
                    seriesDetails = null;
                }
            }
        }
        // Handle the series
        final ArrayList<Series> sa = ArrayUtils.getSeriesUtils().decodeList('|', seriesDetails, false);
        Utils.pruneSeriesList(sa);
        Utils.pruneList(db, sa);
        values.putSerializable(BKEY_SERIES_ARRAY, sa);
    }

    private void importBooks_handleAuthors(CatalogueDBAdapter db, BookData values) {
        // Get the list of authors from whatever source is available.
        String authorDetails = values.getString(BKEY_AUTHOR_DETAILS);
        if (authorDetails == null || authorDetails.isEmpty()) {
            // Need to build it from other fields.
            if (values.containsKey(KEY_AUTHOR_FAMILY_NAME)) {
                // Build from family/given
                authorDetails = values.getString(KEY_AUTHOR_FAMILY_NAME);
                String given = "";
                if (values.containsKey(KEY_AUTHOR_GIVEN_NAMES))
                    given = values.getString(KEY_AUTHOR_GIVEN_NAMES);
                if (given != null && !given.isEmpty())
                    authorDetails += ", " + given;
            } else if (values.containsKey(KEY_AUTHOR_NAME)) {
                authorDetails = values.getString(KEY_AUTHOR_NAME);
            } else if (values.containsKey(KEY_AUTHOR_FORMATTED)) {
                authorDetails = values.getString(KEY_AUTHOR_FORMATTED);
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
        final ArrayList<Author> aa = ArrayUtils.getAuthorUtils().decodeList('|', authorDetails, false);
        Utils.pruneList(db, aa);
        values.putSerializable(BKEY_AUTHOR_ARRAY, aa);
    }

    //
    // This CSV parser is not a complete parser, but it will parse files exported by older
    // versions. At some stage in the future it would be good to allow full CSV export
    // and import to allow for escape('\') chars so that cr/lf can be preserved.
    //
    private String[] returnRow(String row, boolean fullEscaping) {
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
        int endPos   = row.length() - 1;
        // Array of fields found in row
        final ArrayList<String> fields  = new ArrayList<>();
        // Temp. storage for current field
        StringBuilder bld  = new StringBuilder();

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
                        if (fullEscaping)
                            inEsc = true;
                        else
                            bld.append(c);
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
                            if (fullEscaping)
                                inEsc = true;
                            else
                                bld.append(c);
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

    // Require a column
    private void requireColumnOr(@NonNull final BookData values, String... names) throws ImportException {
        for (String name : names)
            if (values.containsKey(name))
                return;

        throw new ImportException(BookCatalogueApp.getResourceString(R.string.file_must_contain_any_column, Utils.join(",", names)));
    }

    private void requireNonBlank(@NonNull final BookData values, int row, String name) throws ImportException {
        if (!values.getString(name).isEmpty())
            return;
        throw new ImportException(BookCatalogueApp.getResourceString(R.string.column_is_blank, name, row));
    }

    @SuppressWarnings("unused")
    private void requireAnyNonBlank(@NonNull final BookData values, int row, String... names) throws ImportException {
        for (String name : names)
            if (values.containsKey(name) && !values.getString(name).isEmpty())
                return;

        throw new ImportException(BookCatalogueApp.getResourceString(R.string.columns_are_blank, Utils.join(",", names), row));
    }

}
