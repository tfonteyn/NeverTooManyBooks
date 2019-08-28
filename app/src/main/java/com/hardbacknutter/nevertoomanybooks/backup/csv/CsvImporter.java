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
import android.database.sqlite.SQLiteDoneException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Implementation of {@link Importer} that reads a CSV file.
 * <p>
 * A CSV file which was not written by this app, should be careful about encoding the following
 * characters:
 * <ul>The obvious:
 * <li>"</li>
 * <li>'</li>
 * <li>\\</li>
 * <li>\r</li>
 * <li>\n</li>
 * <li>\t</li>
 * </ul>
 * <ul>These should be <strong>escaped</strong> when used in names, titles etc.
 * <li>,</li>
 * <li>|</li>
 * <li>*</li>
 * <li>(</li>
 * <li>)</li>
 * </ul>
 * <ul>These are used <strong>unescaped</strong>:
 * <li>',' is allowed/used in an Author name: "family, given-names",<br>
 * * and as a list separator in a list of Bookshelf names.</li>
 * <li>'|' is used as an element separator for fields that take more then one value.</li>
 * <li>'*' is used in few places where an element itself can consist of multiple parts.</li>
 * <li>'(' and ')' are used to add numbers or dates (between the brackets) to items.</li>
 * </ul>
 */
public class CsvImporter
        implements Importer {

    private static final int BUFFER_SIZE = 32768;

    private static final char QUOTE_CHAR = '"';
    private static final char ESCAPE_CHAR = '\\';
    private static final char SEPARATOR = ',';

    private static final String STRINGED_ID = DBDefinitions.KEY_PK_ID;

    /** as used in older versions, or from arbitrarily constructed CSV files. */
    private static final String OLD_STYLE_AUTHOR_NAME = "author_name";
    /** log error string. */
    private static final String ERROR_IMPORT_FAILED_AT_ROW = "Import failed at row ";

    /** Present in pre-v200 CSV files. Obsolete, not used. */
    private static final String LEGACY_BOOKSHELF_ID = "bookshelf_id";
    /** When present in pre-v200 CSV files, we should use it as the bookshelf name. */
    private static final String LEGACY_BOOKSHELF_TEXT_COLUMN = "bookshelf_text";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final ImportOptions mSettings;
    private final String mProgress_msg_n_created_m_updated;

    private final String mUnknownString;

    private final Results mResults = new Results();

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param settings {@link ImportOptions#file} is not used, as we must support
     *                 reading from a stream.
     *                 {@link ImportOptions#IMPORT_ONLY_NEW_OR_UPDATED} is respected.
     *                 Other flags are ignored, as this class only
     *                 handles {@link ImportOptions#BOOK_CSV} anyhow.
     */
    @UiThread
    public CsvImporter(@NonNull final Context context,
                       @NonNull final ImportOptions settings) {
        mUnknownString = context.getString(R.string.unknown);
        mProgress_msg_n_created_m_updated =
                context.getString(R.string.progress_msg_n_created_m_updated);

        mDb = new DAO();
        mSettings = settings;
    }

    @Override
    @WorkerThread
    @NonNull
    public Results doBooks(@NonNull final Context context,
                           @NonNull final InputStream importStream,
                           @Nullable final CoverFinder coverFinder,
                           @NonNull final ProgressListener listener)
            throws IOException, ImportException {

        BufferedReader in = new BufferedReader(
                new InputStreamReader(importStream, StandardCharsets.UTF_8), BUFFER_SIZE);

        // We read the whole file/list into memory.
        List<String> importedList = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            importedList.add(line);
        }
        if (importedList.isEmpty()) {
            return mResults;
        }

        // not perfect, but good enough
        if (listener.getMax() < importedList.size()) {
            listener.setMax(importedList.size() - 1);
        }

        final Book book = new Book();
        // first line in import are the column names
        final String[] csvColumnNames = returnRow(importedList.get(0), true);
        // Store the names so we can check what is present
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase(App.getSystemLocale());
            // add a place holder to the book.
            book.putString(csvColumnNames[i], "");
        }

        // See if we can deduce the kind of escaping to use based on column names.
        // Version 1->3.3 export with family_name and author_id.
        // Version 3.4+ do not; latest versions make an attempt at escaping
        // characters etc to preserve formatting.
        boolean fullEscaping = !book.containsKey(DBDefinitions.KEY_FK_AUTHOR)
                               || !book.containsKey(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);

        // Make sure required fields in Book bundle are present.
        // ENHANCE: Rationalize import to allow updates using 1 or 2 columns.
        // For now we require some id column + author/title
        // ENHANCE: Do a search if mandatory columns missing (e.g. allow 'import' of ISBNs).
        // ENHANCE: Only make some columns mandatory if the ID is not in import, or not in DB
        // (i.e. if not an update)

        // need either UUID or ID
        requireColumnOrThrow(book,
                             // preferred, the original "book_uuid"
                             DBDefinitions.KEY_BOOK_UUID,
                             // as a courtesy, we also allow the plain "uuid"
                             DBDefinitions.KEY_UUID,
                             // but an ID is also ok.
                             DBDefinitions.KEY_PK_ID);

        // need some type of author name.
        // ENHANCE: We should accept UPDATED books where the incoming row does not have a author.
        requireColumnOrThrow(book,
                             // aka author_details: preferred one as used in latest versions
                             CsvExporter.CSV_COLUMN_AUTHORS,
                             // alternative column names we handle.
                             DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                             DBDefinitions.KEY_AUTHOR_FORMATTED,
                             OLD_STYLE_AUTHOR_NAME
                            );

        // need a title.
        // ENHANCE: We should accept UPDATED books where the incoming row does not have a title.
        requireColumnOrThrow(book, DBDefinitions.KEY_TITLE);

        final boolean updateOnlyIfNewer;
        if ((mSettings.what & ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
            if (!book.containsKey(DBDefinitions.KEY_DATE_LAST_UPDATED)) {
                throw new IllegalArgumentException(
                        "Imported data does not contain " + DBDefinitions.KEY_DATE_LAST_UPDATED);
            }
            updateOnlyIfNewer = true;
        } else {
            updateOnlyIfNewer = false;
        }

        Locale userLocale = LocaleUtils.getPreferredLocale(context);

        // Start after headings.
        int row = 1;
        int txRowCount = 0;
        long lastUpdate = 0;
        int delta = 0;

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

                // Validate ID's
                BookIds bids = new BookIds(book);

                // check title
                requireNonBlankOrThrow(book, row, DBDefinitions.KEY_TITLE);

                // check any of the Author variations columns.
                handleAuthors(context, userLocale, mDb, book);
                // check the dedicated Series column, or check if the title contains a series part.
                handleSeries(context, mDb, book);
                // check any of the Bookshelf variations columns.
                handleBookshelves(context, userLocale, mDb, book);

                // optional
                if (book.containsKey(CsvExporter.CSV_COLUMN_TOC)) {
                    handleAnthology(context, mDb, book);
                }

                // ready to update/insert into the database
                try {
                    // Save the original ID from the file for use in checking for images
                    long bookIdFromFile = bids.bookId;
                    // go!
                    bids.bookId = importBook(context, book, bids, updateOnlyIfNewer);

                    // When importing a file that has an ID or UUID, try to import a cover.
                    if (coverFinder != null) {
                        if (!bids.uuid.isEmpty()) {
                            coverFinder.copyOrRenameCoverFile(bids.uuid);
                        } else {
                            coverFinder.copyOrRenameCoverFile(bookIdFromFile,
                                                              mDb.getBookUuid(bids.bookId));
                        }
                    }
                } catch (@NonNull final IOException | SQLiteDoneException e) {
                    Logger.error(this, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                }

                // limit the amount of progress updates, otherwise this will cause a slowdown.
                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > 200 && !listener.isCancelled()) {
                    String msg = String.format(mProgress_msg_n_created_m_updated,
                                               mResults.booksCreated, mResults.booksUpdated);
                    listener.onProgressStep(delta, msg);
                    delta = 0;
                    lastUpdate = now;
                }
                delta++;
                row++;
            } // end while
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
                             "created:" + mResults.booksCreated,
                             "updated: " + mResults.booksUpdated);
        }

        mResults.booksProcessed = row;
        return mResults;
    }

    @Override
    public void close() {
        try {
            // do some cleaning
            mDb.purge();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
        }
        mDb.close();
    }

    /**
     * insert or update a single book.
     *
     * @param context Current context
     *
     * @return the imported book id
     */
    private long importBook(@NonNull final Context context,
                            @NonNull final Book book,
                            @NonNull final BookIds bids,
                            final boolean updateOnlyIfNewer) {

        final boolean hasUuid = !bids.uuid.isEmpty();

        // Always import empty ID's...even if they are duplicates.
        // Would be nice to import a cover, but without ID/UUID that is not possible
        if (!hasUuid && !bids.hasNumericId) {
            return mDb.insertBook(context, book);
        }

        // we have a UUID or ID. We'll check if we already have the book.
        boolean exists = false;

        // Let the UUID trump the ID; we may be importing someone else's list with bogus ID's
        if (hasUuid) {
            long bookId = mDb.getBookIdFromUuid(bids.uuid);
            if (bookId != 0) {
                // use the id from the existing book, as found by UUID lookup
                bids.bookId = bookId;
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
                int rowsAffected = mDb.updateBook(context, bids.bookId, book,
                                                  DAO.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                if (rowsAffected == 1) {
                    mResults.booksUpdated++;
                } else {
                    mResults.booksFailed++;
                }
            }
        } else {
            bids.bookId = mDb.insertBook(context, bids.bookId, book);
            if (bids.bookId != -1) {
                mResults.booksCreated++;
            } else {
                mResults.booksFailed++;
            }
        }

        return bids.bookId;
    }

    private boolean updateOnlyIfNewer(@NonNull final DAO db,
                                      @NonNull final Book book,
                                      final long bookId) {

        Date bookDate = DateUtils.parseDate(db.getBookLastUpdateDate(bookId));
        Date importDate = DateUtils.parseDate(book.getString(DBDefinitions.KEY_DATE_LAST_UPDATED));

        return importDate != null && (bookDate == null || importDate.compareTo(bookDate) > 0);
    }

    /**
     * Process the bookshelves.
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param userLocale the locale the user is running the app in.
     * @param db         Database Access
     * @param book       the book
     */
    private void handleBookshelves(@NonNull final Context context,
                                   @NonNull final Locale userLocale,
                                   @NonNull final DAO db,
                                   @NonNull final Book book) {
        String encodedList = null;
        if (book.containsKey(DBDefinitions.KEY_BOOKSHELF)) {
            encodedList = book.getString(DBDefinitions.KEY_BOOKSHELF);

        } else if (book.containsKey(LEGACY_BOOKSHELF_TEXT_COLUMN)) {
            encodedList = book.getString(LEGACY_BOOKSHELF_TEXT_COLUMN);
        }

        ArrayList<Bookshelf> list = CsvCoder.getBookshelfCoder().decodeList(encodedList);
        if (!list.isEmpty()) {
            // fix the ID's
            ItemWithFixableId.pruneList(context, db, list, userLocale);
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }

        book.remove(DBDefinitions.KEY_BOOKSHELF);
        book.remove(LEGACY_BOOKSHELF_TEXT_COLUMN);
        book.remove(LEGACY_BOOKSHELF_ID);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     *
     * @param context    Current context
     * @param userLocale the locale the user is running the app in.
     * @param db         Database Access
     * @param book       the book
     */
    private void handleAuthors(@NonNull final Context context,
                               @NonNull final Locale userLocale,
                               @NonNull final DAO db,
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
        // so we allow blank author_details and fill in a localised version of "Unknown, Unknown"
        if (encodedList.isEmpty()) {
            encodedList = mUnknownString + ", " + mUnknownString;
        }

        // Now build the array for authors
        ArrayList<Author> list = CsvCoder.getAuthorCoder().decodeList(encodedList);
        ItemWithFixableId.pruneList(context, db, list, userLocale);
        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        book.remove(CsvExporter.CSV_COLUMN_AUTHORS);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of series from whatever source is available.
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the book
     */
    private void handleSeries(@NonNull final Context context,
                              @NonNull final DAO db,
                              @NonNull final Book book) {
        String encodedList = book.getString(CsvExporter.CSV_COLUMN_SERIES);
        if (encodedList.isEmpty()) {
            // Try to build from SERIES_NAME and SERIES_NUM. It may all be blank
            if (book.containsKey(DBDefinitions.KEY_SERIES_TITLE)) {
                encodedList = book.getString(DBDefinitions.KEY_SERIES_TITLE);
                if (!encodedList.isEmpty()) {
                    String seriesNum = book.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
                    encodedList += '(' + seriesNum + ')';
                } else {
                    encodedList = null;
                }
            }
        }

        // Handle the series
        Locale bookLocale = book.getLocale(context);
        ArrayList<Series> list = CsvCoder.getSeriesCoder().decodeList(encodedList);
        Series.pruneSeriesList(context, list, bookLocale);
        ItemWithFixableId.pruneList(context, db, list, bookLocale);
        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
        book.remove(CsvExporter.CSV_COLUMN_SERIES);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Ignore the actual value of the DBDefinitions.KEY_TOC_BITMASK! it will be
     * 'reset' to mirror what we actually have when storing the book data
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the book
     */
    private void handleAnthology(@NonNull final Context context,
                                 @NonNull final DAO db,
                                 @NonNull final Book book) {

        String encodedList = book.getString(CsvExporter.CSV_COLUMN_TOC);
        if (!encodedList.isEmpty()) {
            ArrayList<TocEntry> list = CsvCoder.getTocCoder().decodeList(encodedList);
            if (!list.isEmpty()) {
                // fix the ID's
                ItemWithFixableId.pruneList(context, db, list, book.getLocale(context));
                book.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, list);
            }
        }

        // remove the unneeded string encoded set
        book.remove(CsvExporter.CSV_COLUMN_TOC);
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
            if (pos < endPos) {
                next = row.charAt(pos + 1);
            } else {
                next = '\0';
            }

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
                            if (pos < endPos) {
                                next = row.charAt(pos + 1);
                            } else {
                                next = '\0';
                            }
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
     *
     * @param book  to check
     * @param names columns which are required
     *
     * @throws ImportException if the required column is missing
     */
    private void requireColumnOrThrow(@NonNull final Book book,
                                      @NonNull final String... names)
            throws ImportException {
        for (String name : names) {
            if (book.containsKey(name)) {
                return;
            }
        }

        throw new ImportException(R.string.import_error_csv_file_must_contain_any_column,
                                  TextUtils.join(",", names));
    }

    /**
     * @param book to check
     * @param name column which is required
     *
     * @throws ImportException if the required column is blank
     */
    private void requireNonBlankOrThrow(@NonNull final Book book,
                                        final int row,
                                        @SuppressWarnings("SameParameterValue")
                                        @NonNull final String name)
            throws ImportException {
        if (!book.getString(name).isEmpty()) {
            return;
        }
        throw new ImportException(R.string.error_column_is_blank, name, row);
    }

    /***
     *
     * @param book  to check
     * @param names columns which are required
     *
     * @throws ImportException if no suitable column is present
     */
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

        throw new ImportException(R.string.error_columns_are_blank,
                                  TextUtils.join(",", names), row);
    }

    /**
     * Holder class for identifiers of a book during import.
     * (created to let lint work)
     */
    private static class BookIds {

        String uuid;
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
                } catch (@NonNull final NumberFormatException e) {
                    // don't log, it's fine.
                    hasNumericId = false;
                }
            }

            if (!hasNumericId) {
                // yes, string, see above
                book.putString(STRINGED_ID, "0");
            }

            // Get the UUID, and remove from collection if null/blank
            if (book.containsKey(DBDefinitions.KEY_BOOK_UUID)) {
                uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);
                if (uuid.isEmpty()) {
                    // Remove any blank KEY_BOOK_UUID column
                    book.remove(DBDefinitions.KEY_BOOK_UUID);
                }
            } else if (book.containsKey(DBDefinitions.KEY_UUID)) {
                // read, remove and store (if not empty) as KEY_BOOK_UUID
                uuid = book.getString(DBDefinitions.KEY_UUID);
                book.remove(DBDefinitions.KEY_UUID);
                if (!uuid.isEmpty()) {
                    book.putString(DBDefinitions.KEY_BOOK_UUID, uuid);
                }
            }
        }
    }
}
