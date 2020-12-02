/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * Implementation of {@link RecordReader} that reads a CSV file.
 * <ul>Supports:
 *      <li>{@link ArchiveReaderRecord.Type#Books}</li>
 * </ul>
 * <p>
 * A CSV file which was not written by this app, should be careful about encoding the following
 * characters:
 *
 * <strong>DOUBLE escape the '*' character; i.e. '*' should be encoded as \\\\*</strong>
 *
 * <ul>Always <strong>escape:</strong>
 *      <li>"</li>
 *      <li>'</li>
 *      <li>\</li>
 *      <li>\r</li>
 *      <li>\n</li>
 *      <li>\t</li>
 *      <li>|</li>
 *      <li>(</li>
 *      <li>)</li>
 * </ul>
 *
 * <ul>Unescaped special characters:
 *      <li>',' is recognised/used in an Author name: "family, given-names",<br>
 *          and as a list separator in a list of Bookshelf names.</li>
 *      <li>'|' is used as an element separator for fields that take more than one value.<br>
 *          e.g. a list of Author names, Series, ...</li>
 *      <li>'*' is used in few places where an element itself can consist of multiple parts.
 *          e.g. See Author and Series object encoding. (for advanced usage)</li>
 *      <li>'(' and ')' are used to add numbers or dates (between the brackets) to items.
 *          e.g. TOC entries can contain a date, Series will have the book number,... </li>
 * </ul>
 * <p>
 * Space characters are encoded for Authors names while exporting,
 * but it's not strictly needed although some exotic names might get mangled;
 * when in doubt, escape.
 * <p>
 * ENHANCE: after an import offer to run an internet update on the list of book id's
 * This would/could be split up....
 * - books inserted which had a uuid/id
 * - books updated
 * - books inserted while not having a uuid/id; i.e. unknown input source -> would require ISBN
 */
public class CsvRecordReader
        implements RecordReader {

    /** Log tag. */
    private static final String TAG = "CsvRecordReader";

    /** Buffer for the Reader. */
    private static final int BUFFER_SIZE = 65535;

    /** log error string. */
    private static final String ERROR_IMPORT_FAILED_AT_ROW = "Import failed at row ";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final Locale mUserLocale;
    /** cached localized "Books" string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mProgressMessage;
    @NonNull
    private final String mUnknownString;

    private final BookCoder mBookCoder;

    private ImportResults mResults;

    /**
     * Constructor.
     * <p>
     * Only supports {@link ArchiveReaderRecord.Type#Books}.
     *
     * @param context Current context
     * @param db      Database Access;
     */
    @AnyThread
    public CsvRecordReader(@NonNull final Context context,
                           @NonNull final DAO db) {
        mDb = db;
        mBookCoder = new BookCoder(StyleDAO.getDefault(context, mDb));
        mUserLocale = AppLocale.getInstance().getUserLocale(context);

        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
        mUnknownString = context.getString(R.string.unknown);
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @ImportHelper.Options final int options,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        mResults = new ImportResults();

        // Read the whole file content into a list of lines.
        final List<String> content = record.asList();
        if (content.isEmpty()) {
            return mResults;
        }

        final boolean updatesMustSync =
                (options & ImportHelper.OPTION_UPDATES_MUST_SYNC) != 0;
        final boolean updatesMayOverwrite =
                (options & ImportHelper.OPTION_UPDATES_MAY_OVERWRITE) != 0;

        // First line in the import file must be the column names.
        // Store them to use as keys into the book.
        final String[] csvColumnNames = parse(context, 0, content.get(0));
        // sanity check: make sure they are lower case
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase(mUserLocale);
        }
        // check for required columns
        final List<String> csvColumnNamesList = Arrays.asList(csvColumnNames);
        // If a sync was requested, we'll need this column or cannot proceed.
        if (updatesMustSync) {
            requireColumnOrThrow(context, csvColumnNamesList, DBDefinitions.KEY_UTC_LAST_UPDATED);
        }

        // One book == One row. We start after the headings row.
        int row = 1;
        // Count the number of rows between committing a transaction
        int txRowCount = 0;
        // Instance in time when we last send a progress message
        long lastUpdateTime = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        SyncLock txLock = null;
        try {
            // not perfect, but good enough
            if (progressListener.getMaxPos() < content.size()) {
                progressListener.setMaxPos(content.size());
            }

            // Iterate through each imported row or until cancelled
            while (row < content.size() && !progressListener.isCancelled()) {
                // every 10 inserted, we commit the transaction
                if (mDb.inTransaction() && txRowCount > 10) {
                    mDb.setTransactionSuccessful();
                    mDb.endTransaction(txLock);
                }
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                    txRowCount = 0;
                }
                txRowCount++;

                try {
                    final String[] csvDataRow = parse(context, row, content.get(row));

                    if (csvDataRow.length != csvColumnNames.length) {
                        throw new ImportException(context.getString(
                                R.string.error_import_csv_column_count_mismatch, row));
                    }

                    final Book book = mBookCoder.decode(context, mDb,
                                                        csvColumnNames, csvDataRow);

                    // Do we have a DBDefinitions.KEY_BOOK_UUID in the import ?
                    @Nullable
                    final boolean hasUuid = handleUuid(book);
                    // Do we have a DBDefinitions.KEY_PK_ID in the import ?
                    final long importNumericId = extractNumericId(book);

                    // ALWAYS let the UUID trump the ID; we may be importing someone else's list
                    if (hasUuid) {
                        importBookWithUuid(context, updatesMustSync, updatesMayOverwrite,
                                           book, importNumericId);

                    } else if (importNumericId > 0) {
                        importBookWithId(context, updatesMustSync, updatesMayOverwrite,
                                         book, importNumericId);

                    } else {
                        importBook(context, book);
                    }

                } catch (@NonNull final DAO.DaoWriteException
                        | SQLiteDoneException e) {
                    //TODO: use a meaningful user-displaying string.
                    handleRowException(context, row, e, mUnknownString);

                } catch (@NonNull final ImportException e) {
                    // an ImportException has a user-displayable message.
                    handleRowException(context, row, e,
                                       Objects.requireNonNull(e.getLocalizedMessage()));
                }

                row++;

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdateTime) > progressListener.getUpdateIntervalInMs()
                    && !progressListener.isCancelled()) {
                    final String msg = String.format(mProgressMessage,
                                                     mBooksString,
                                                     mResults.booksCreated,
                                                     mResults.booksUpdated,
                                                     mResults.booksSkipped);
                    progressListener.publishProgressStep(delta, msg);
                    lastUpdateTime = now;
                    delta = 0;
                }
            }
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(txLock);
            }
        }

        // minus 1 to compensate for the last increment
        mResults.booksProcessed = row - 1;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            Log.d(TAG, "read|mResults=" + mResults);
        }
        return mResults;
    }

    private void handleRowException(@NonNull final Context context,
                                    final int row,
                                    @NonNull final Exception e,
                                    @NonNull final String msg) {
        mResults.booksSkipped++;
        mResults.failedLinesMessage.add(msg);
        mResults.failedLinesNr.add(row);

        if (BuildConfig.DEBUG /* always */) {
            if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Logger.warn(context, TAG, "e=" + e.getMessage(),
                            ERROR_IMPORT_FAILED_AT_ROW + row);
            } else if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
                // logging with the full exception is VERY HEAVY
                Logger.error(context, TAG, e, ERROR_IMPORT_FAILED_AT_ROW + row);
            }
        }
    }

    /**
     * insert or update a single book which has a <strong>valid UUID</strong>.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws DAO.DaoWriteException on failure
     */
    private void importBookWithUuid(@NonNull final Context context,
                                    final boolean updatesMustSync,
                                    final boolean updatesMayOverwrite,
                                    @NonNull final Book book,
                                    final long importNumericId)
            throws DAO.DaoWriteException {
        // Verified to be valid earlier.
        final String uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);

        // check if the book exists in our database, and fetch it's id.
        final long databaseBookId = mDb.getBookIdFromUuid(uuid);
        if (databaseBookId > 0) {
            // The book exists in our database (matching UUID).

            // Explicitly set the EXISTING id on the book
            // (the importBookId was removed earlier, and is IGNORED)
            book.putLong(DBDefinitions.KEY_PK_ID, databaseBookId);

            // UPDATE the existing book (if allowed). Check the sync option FIRST!
            if ((updatesMustSync && isImportNewer(context, mDb, book, databaseBookId))
                || updatesMayOverwrite) {

                mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                          | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                mResults.booksUpdated++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "UUID=" + uuid
                               + "|databaseBookId=" + databaseBookId
                               + "|update|" + book.getTitle());
                }

            } else {
                mResults.booksSkipped++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "UUID=" + uuid
                               + "|databaseBookId=" + databaseBookId
                               + "|skipped|" + book.getTitle());
                }
            }

        } else {
            // The book does NOT exist in our database (no match for the UUID), insert it.

            // If we have an importBookId, and it does not already exist, we reuse it.
            if (importNumericId > 0 && !mDb.bookExistsById(importNumericId)) {
                book.putLong(DBDefinitions.KEY_PK_ID, importNumericId);
            }

            // the Book object will contain:
            // - valid DBDefinitions.KEY_BOOK_UUID not existent in the database
            // - NO id, OR an id which does not exist in the database yet.
            // INSERT, explicitly allowing the id to be reused if present
            final long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                                         | DAO.BOOK_FLAG_USE_ID_IF_PRESENT);
            mResults.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "UUID=" + uuid
                           + "|importNumericId=" + importNumericId
                           + "|insert=" + insId
                           + "|" + book.getTitle());
            }
        }
    }

    /**
     * insert or update a single book which has a potentially usable id.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws DAO.DaoWriteException on failure
     */
    private void importBookWithId(@NonNull final Context context,
                                  final boolean updatesMustSync,
                                  final boolean updatesMayOverwrite,
                                  @NonNull final Book book,
                                  final long importNumericId)
            throws DAO.DaoWriteException {
        // Add the importNumericId back to the book.
        book.putLong(DBDefinitions.KEY_PK_ID, importNumericId);

        // Is that id already in use ?
        if (!mDb.bookExistsById(importNumericId)) {
            // The id is not in use, simply insert the book using the given importNumericId,
            // explicitly allowing the id to be reused
            final long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                                         | DAO.BOOK_FLAG_USE_ID_IF_PRESENT);
            mResults.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "importNumericId=" + importNumericId
                           + "|insert=" + insId
                           + "|" + book.getTitle());
            }

        } else {
            // The id IS in use, we will be updating an existing book (if allowed).

            // This is risky as we might overwrite a different book which happens
            // to have the same id, but other than skipping there is no other option for now.
            // Ideally, we should ask the user presenting a choice "keep/overwrite"

            // UPDATE the existing book (if allowed). Check the sync option FIRST!
            if ((updatesMustSync && isImportNewer(context, mDb, book, importNumericId))
                || updatesMayOverwrite) {
                mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                          | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                mResults.booksUpdated++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "importNumericId=" + importNumericId
                               + "|update|" + book.getTitle());
                }
            } else {
                mResults.booksSkipped++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "importNumericId=" + importNumericId
                               + "|skipped|" + book.getTitle());
                }
            }
        }
    }

    private void importBook(@NonNull final Context context,
                            final Book book)
            throws DAO.DaoWriteException {
        // Always import books which have no UUID/ID, even if the book is a potential duplicate.
        // We don't try and search/match but leave it to the user.
        final long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION);
        mResults.booksCreated++;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            Log.d(TAG, "UUID=''"
                       + "|ID=0"
                       + "|insert=" + insId
                       + "|" + book.getTitle());
        }
    }


    /**
     * Check if the incoming book is newer than the stored book data.
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the book we're updating
     * @param bookId  the book id to lookup in our database
     */
    private boolean isImportNewer(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final Book book,
                                  @IntRange(from = 1) final long bookId) {
        final LocalDateTime utcImportDate =
                DateParser.getInstance(context)
                          .parseISO(book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED));
        if (utcImportDate == null) {
            return false;
        }

        final LocalDateTime utcLastUpdated = db.getBookLastUpdateUtcDate(context, bookId);

        return utcLastUpdated == null || utcImportDate.isAfter(utcLastUpdated);
    }

    /**
     * Process the UUID if present.
     *
     * @param book the book
     *
     * @return {@code true} if the book has a UUID
     */
    private boolean handleUuid(@NonNull final Book book) {

        final String uuid;

        // Get the "book_uuid", and remove from book if null/blank
        if (book.contains(DBDefinitions.KEY_BOOK_UUID)) {
            uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);
            if (uuid.isEmpty()) {
                book.remove(DBDefinitions.KEY_BOOK_UUID);
            }

        } else if (book.contains(DBDefinitions.KEY_UUID)) {
            // second chance: see if we have a "uuid" column.
            uuid = book.getString(DBDefinitions.KEY_UUID);
            // ALWAYS remove as we won't use this key again.
            book.remove(DBDefinitions.KEY_UUID);
            // but if we got a UUID from it, store it again, using the correct key
            if (!uuid.isEmpty()) {
                book.putString(DBDefinitions.KEY_BOOK_UUID, uuid);
            }
        } else {
            uuid = null;
        }

        return uuid != null && !uuid.isEmpty();
    }

    /**
     * Process the ID if present. <strong>It will be removed from the Book.</strong>
     *
     * @param book the book
     *
     * @return the id, if any.
     */
    private long extractNumericId(@NonNull final Book book) {
        // Do we have a numeric id in the import ?
        // String: see book init, we copied all fields we find in the import file as text.
        final String idStr = book.getString(DBDefinitions.KEY_PK_ID);
        // ALWAYS remove here to avoid type-issues further down. We'll re-add if needed.
        book.remove(DBDefinitions.KEY_PK_ID);

        if (!idStr.isEmpty()) {
            try {
                return Long.parseLong(idStr);
            } catch (@NonNull final NumberFormatException ignore) {
                // don't log, it's fine.
            }
        }
        return 0;
    }

    /**
     * This CSV parser is not a complete parser, but it is "good enough".
     *
     * @param context Current context
     * @param row     row number
     * @param line    with CSV fields
     *
     * @return an array representing the row
     *
     * @throws ImportException on failure to parse this line
     */
    @NonNull
    private String[] parse(@NonNull final Context context,
                           final int row,
                           @NonNull final String line)
            throws ImportException {
        try {
            // Fields found in row
            final Collection<String> fields = new ArrayList<>();
            // Temporary storage for current field
            final StringBuilder sb = new StringBuilder();

            // Current position
            int pos = 0;
            // In a quoted string
            boolean inQuotes = false;
            // Found an escape char
            boolean isEsc = false;
            // 'Current' char
            char c;
            // Last position in row
            final int endPos = line.length() - 1;
            // 'Next' char
            char next = (line.isEmpty()) ? '\0' : line.charAt(0);

            // '\0' is used as (and artificial) null character indicating end-of-string.
            while (next != '\0') {
                // Get current and next char
                c = next;
                if (pos < endPos) {
                    next = line.charAt(pos + 1);
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
                                    next = line.charAt(pos + 1);
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
                            isEsc = true;
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
                                    final String msg = context.getString(
                                            R.string.warning_import_unescaped_quote, row, pos);
                                    throw new ImportException(msg);
                                } else {
                                    inQuotes = true;
                                }
                                break;

                            case '\\':
                                isEsc = true;
                                break;

                            case ',':
                                // Add this field and reset for the next.
                                fields.add(sb.toString());
                                sb.setLength(0);
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
            final String[] columns = new String[fields.size()];
            fields.toArray(columns);

            return columns;

        } catch (@NonNull final StackOverflowError e) {
            // StackOverflowError has been seen when the StringBuilder overflows.
            // The stack at the time was 1040kb. Not reproduced as yet.
            Log.e(TAG, "line.length=" + line.length() + "\n" + line, e);
            throw new ImportException(context.getString(R.string.error_import_csv_line_to_long,
                                                        row, line.length()), e);
        }
    }

    /**
     * Require a column to be present. First one found; remainders are not needed.
     *
     * @param context        Current context
     * @param columnsPresent the column names which are present
     * @param names          columns which should be checked for, in order of preference
     *
     * @throws ImportException if no suitable column is present
     */
    private void requireColumnOrThrow(@NonNull final Context context,
                                      @NonNull final List<String> columnsPresent,
                                      @NonNull final String... names)
            throws ImportException {


        for (final String name : names) {
            if (columnsPresent.contains(name)) {
                return;
            }
        }

        final String msg = context.getString(R.string.error_import_csv_missing_columns_x,
                                             TextUtils.join(",", names));
        throw new ImportException(msg);
    }
}
