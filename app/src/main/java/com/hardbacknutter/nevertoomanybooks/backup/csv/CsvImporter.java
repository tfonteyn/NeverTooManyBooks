/*
 * @Copyright 2020 HardBackNutter
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
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
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

/**
 * Implementation of {@link Importer} that reads a CSV file.
 * <p>
 * A CSV file which was not written by this app, should be careful about encoding the following
 * characters:
 *
 * <strong>DOUBLE escape the '*' character; i.e. '*' should be encoded as \\\\*</strong>
 *
 * <ul>Always <strong>escape:</strong>
 * <li>"</li>
 * <li>'</li>
 * <li>\</li>
 * <li>\r</li>
 * <li>\n</li>
 * <li>\t</li>
 * <li>|</li>
 * <li>(</li>
 * <li>)</li>
 * </ul>
 *
 * <ul>Unescaped special characters:
 * <li>',' is recognised/used in an Author name: "family, given-names",<br>
 * and as a list separator in a list of Bookshelf names.
 * </li>
 * <li>'|' is used as an element separator for fields that take more than one value.<br>
 * e.g. a list of Author names, Series, ...
 * </li>
 * <li>'*' is used in few places where an element itself can consist of multiple parts.
 * e.g. See Author and Series object encoding. (for advanced usage)</li>
 * <li>'(' and ')' are used to add numbers or dates (between the brackets) to items.
 * e.g. TOC entries can contain a date, Series will have the book number,... </li>
 * </ul>
 * <p>
 * Space characters are encoded for Authors names while exporting,
 * but it's not strictly needed although some exotic names might get mangled;
 * when in doubt, escape.
 */
public class CsvImporter
        implements Importer {

    /** Log tag. */
    private static final String TAG = "CsvImporter";

    /** Only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;

    private static final int BUFFER_SIZE = 32768;

    private static final String STRINGED_ID = DBDefinitions.KEY_PK_ID;

    /** as used in older versions, or from arbitrarily constructed CSV files. */
    private static final String OLD_STYLE_AUTHOR_NAME = "author_name";
    /** log error string. */
    private static final String ERROR_IMPORT_FAILED_AT_ROW = "Import failed at row ";

    /** Present in BookCatalogue CSV files. Obsolete, not used. */
    private static final String LEGACY_BOOKSHELF_ID = "bookshelf_id";
    /** When present in BookCatalogue CSV files, we should use it as the bookshelf name. */
    private static final String LEGACY_BOOKSHELF_TEXT_COLUMN = "bookshelf_text";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final ImportHelper mSettings;

    /** cached localized "Books" string. */
    private final String mBooksString;
    private final String mProgress_msg_n_created_m_updated;

    private final Results mResults = new Results();

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param settings {@link ImportHelper#IMPORT_ONLY_NEW_OR_UPDATED} is respected.
     *                 Other flags are ignored, as this class only
     *                 handles {@link ImportHelper#BOOK_CSV} anyhow.
     */
    @AnyThread
    public CsvImporter(@NonNull final Context context,
                       @NonNull final ImportHelper settings) {
        mBooksString = context.getString(R.string.lbl_books);
        mProgress_msg_n_created_m_updated =
                context.getString(R.string.progress_msg_n_created_m_updated);

        mDb = new DAO(TAG);
        mSettings = settings;
    }

    @Override
    @WorkerThread
    public Results doBooks(@NonNull final Context context,
                           @NonNull final InputStream is,
                           @Nullable final CoverFinder coverFinder,
                           @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        BufferedReader in = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8), BUFFER_SIZE);

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
        if (progressListener.getMax() < importedList.size()) {
            progressListener.setMax(importedList.size());
        }

        final Book book = new Book();
        // first line in import must be the column names
        final String[] csvColumnNames;
        try {
            csvColumnNames = parseRow(importedList.get(0), true);
        } catch (@NonNull final ParseException e) {
            // if we cannot even parse the column header, give up.
            throw new ImportException(R.string.error_import_failed);
        }

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
        // - For now we require some id column + author/title
        // - Do a search if mandatory columns missing (e.g. allow 'import' of ISBNs).
        // - Only make some columns mandatory if the id is not in import, or not in DB
        // (i.e. if not an update)

        // need either UUID or ID
        requireColumnOrThrow(book,
                             // preferred, the original "book_uuid"
                             DBDefinitions.KEY_BOOK_UUID,
                             // as a courtesy, we also allow the plain "uuid"
                             DBDefinitions.KEY_UUID,
                             // but an id is also ok.
                             DBDefinitions.KEY_PK_ID);

        // need some type of author name.
        requireColumnOrThrow(book,
                             // aka author_details: preferred one as used in latest versions
                             CsvExporter.CSV_COLUMN_AUTHORS,
                             // alternative column names we handle.
                             DBDefinitions.KEY_AUTHOR_FAMILY_NAME,
                             DBDefinitions.KEY_AUTHOR_FORMATTED,
                             OLD_STYLE_AUTHOR_NAME
                            );

        // need a title.
        requireColumnOrThrow(book, DBDefinitions.KEY_TITLE);

        final boolean updateOnlyIfNewer;
        if ((mSettings.options & ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
            if (!book.containsKey(DBDefinitions.KEY_DATE_LAST_UPDATED)) {
                throw new ImportException(R.string.error_import_csv_file_must_contain_columns_x,
                                          DBDefinitions.KEY_DATE_LAST_UPDATED);
            }
            updateOnlyIfNewer = true;
        } else {
            updateOnlyIfNewer = false;
        }

        // Start after headings.
        int row = 1;
        int txRowCount = 0;
        long lastUpdate = 0;
        // we only update progress every PROGRESS_UPDATE_INTERVAL ms.
        // Count the nr of books in between.
        int delta = 0;

        SyncLock txLock = null;
        try {
            // Iterate through each imported row
            while (row < importedList.size() && !progressListener.isCancelled()) {
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

                try {
                    final String[] csvDataRow = parseRow(importedList.get(row), fullEscaping);
                    // clear book (avoiding construction another object)
                    book.clear();
                    // Read all columns of the current row into the Bundle.
                    // Note that some of them require further processing before being valid.
                    // Throws IndexOutOfBoundsException if the number of fields does
                    // not match the number of column headers.
                    for (int i = 0; i < csvColumnNames.length; i++) {
                        book.putString(csvColumnNames[i], csvDataRow[i]);
                    }

                    // Validate ID's
                    BookIds bids = new BookIds(book);

                    // check title
                    requireNonBlankOrThrow(book, row, DBDefinitions.KEY_TITLE);

                    // check any of the Author variations columns.
                    handleAuthors(context, mDb, book);
                    // check the dedicated Series column,
                    // or if the book title contains a series part.
                    handleSeries(context, mDb, book);
                    // check any of the Bookshelf variations columns.
                    handleBookshelves(context, mDb, book);

                    // optional
                    if (book.containsKey(CsvExporter.CSV_COLUMN_TOC)) {
                        handleAnthology(context, mDb, book);
                    }

                    // The data is ready to be send to the database

                    // Save the original id from the file for use in checking for images
                    long bookIdFromFile = bids.bookId;
                    // go!
                    bids.bookId = importBook(context, book, bids, updateOnlyIfNewer);

                    // When importing a file that has an id or UUID, try to import a cover.
                    if (coverFinder != null) {
                        if (!bids.uuid.isEmpty()) {
                            coverFinder.copyOrRenameCoverFile(bids.uuid);
                        } else {
                            coverFinder.copyOrRenameCoverFile(bookIdFromFile,
                                                              mDb.getBookUuid(bids.bookId));
                        }
                    }
                } catch (@NonNull final SQLiteDoneException
                        | ParseException | IndexOutOfBoundsException e) {
                    mResults.failedCsvLines.add(row);
                    Logger.error(context, TAG, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                }

                // limit the amount of progress updates, otherwise this will cause a slowdown.
                long now = System.currentTimeMillis();
                if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL
                    && !progressListener.isCancelled()) {
                    String msg = String.format(mProgress_msg_n_created_m_updated,
                                               mBooksString,
                                               mResults.booksCreated,
                                               mResults.booksUpdated);
                    progressListener.onProgressStep(delta, msg);
                    delta = 0;
                    lastUpdate = now;
                }
                delta++;
                row++;
            }
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                //noinspection ConstantConditions
                mDb.endTransaction(txLock);
            }
        }

        // minus 1 for the headers.
        mResults.booksProcessed = row - 1;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Log.d(TAG, "EXIT|doBooks|Csv Import successful|" + mResults);
        }
        return mResults;
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }

    /**
     * insert or update a single book.
     *
     * @param context           Current context
     * @param book              to import
     * @param bids              the holder with all information about the book id/uuid
     * @param updateOnlyIfNewer flag
     *
     * @return the imported book ID
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
                // Make sure the id (if present) is not already used.
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
                                                  DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                if (rowsAffected == 1) {
                    mResults.booksUpdated++;
                }
            }
        } else {
            bids.bookId = mDb.insertBook(context, bids.bookId, book);
            if (bids.bookId != -1) {
                mResults.booksCreated++;
            }
        }

        return bids.bookId;
    }

    /**
     * @param db Database Access
     */
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
     * @param context Current context
     * @param db      Database Access
     * @param book    the book
     */
    private void handleBookshelves(@NonNull final Context context,
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
            ItemWithFixableId.pruneList(list, context, db, Locale.getDefault(), false);
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
     * @param context Current context
     * @param db      Database Access
     * @param book    the book
     */
    private void handleAuthors(@NonNull final Context context,
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

        // Now build the array for authors
        ArrayList<Author> list;
        if (encodedList.isEmpty()) {
            list = new ArrayList<>();
            list.add(Author.createUnknownAuthor(context));
        } else {
            list = CsvCoder.getAuthorCoder().decodeList(encodedList);
            ItemWithFixableId.pruneList(list, context, db, Locale.getDefault(), false);
        }

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

        // run in batch mode, i.e. force using the bookLocale;
        // otherwise the import is far to slow and of little benefit.
        Series.pruneList(list, context, db, bookLocale, true);

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
                ItemWithFixableId.pruneList(list, context, db, book.getLocale(context), false);
                book.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, list);
            }
        }

        // remove the unneeded string encoded set
        book.remove(CsvExporter.CSV_COLUMN_TOC);
    }

    /**
     * This CSV parser is not a complete parser, but it will parse files exported by older
     * versions.
     *
     * @param row          with CSV fields
     * @param fullEscaping if {@code true} handle the import as a version 3.4+;
     *                     if {@code false} handle as an earlier version.
     *
     * @return an array representing the row
     *
     * @throws ParseException on failure
     */
    @NonNull
    private String[] parseRow(@NonNull final String row,
                              final boolean fullEscaping)
            throws ParseException {
        // Fields found in row
        final Collection<String> fields = new ArrayList<>();
        // Temporary storage for current field
        StringBuilder sb = new StringBuilder();

        // Current position
        int pos = 0;
        // In a quoted string
        boolean inQuotes = false;
        // Found an escape char
        boolean isEsc = false;
        // 'Current' char
        char c;
        // Last position in row
        int endPos = row.length() - 1;
        // 'Next' char
        char next = (row.isEmpty()) ? '\0' : row.charAt(0);

        // '\0' is used as (and artificial) null character indicating end-of-string.
        while (next != '\0') {
            // Get current and next char
            c = next;
            if (pos < endPos) {
                next = row.charAt(pos + 1);
            } else {
                next = '\0';
            }

            if (isEsc) {
                switch (c) {
                    case '\\':
                        sb.append('\\');
                        break;

                    case 'r':
                        sb.append('\r');
                        break;

                    case 't':
                        sb.append('\t');
                        break;

                    case 'n':
                        sb.append('\n');
                        break;

                    default:
                        sb.append(c);
                        break;
                }
                isEsc = false;

            } else if (inQuotes) {
                switch (c) {
                    case '"':
                        if (next == '"') {
                            // substitute two successive quotes with one quote
                            pos++;
                            if (pos < endPos) {
                                next = row.charAt(pos + 1);
                            } else {
                                next = '\0';
                            }
                            sb.append(c);

                        } else {
                            // end of quoted string
                            inQuotes = false;
                        }
                        break;

                    case '\\':
                        if (fullEscaping) {
                            isEsc = true;
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
                // Ignore leading whitespace.
                if ((c != ' ' && c != '\t') || sb.length() != 0) {
                    switch (c) {
                        case '"':
                            if (sb.length() > 0) {
                                // Fields with inner quotes MUST be escaped
                                throw new ParseException("unescaped quote", pos);
                            } else {
                                inQuotes = true;
                            }
                            break;

                        case '\\':
                            if (fullEscaping) {
                                isEsc = true;
                            } else {
                                sb.append(c);
                            }
                            break;

                        case ',':
                            // Add this field and reset for the next.
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

        throw new ImportException(R.string.error_import_csv_file_must_contain_columns_x,
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
