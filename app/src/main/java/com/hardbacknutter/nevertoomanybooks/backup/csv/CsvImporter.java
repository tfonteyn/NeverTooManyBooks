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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.AuthorCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookshelfCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.PublisherCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.SeriesCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.TocEntryCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * Implementation of {@link Importer} that reads a CSV file.
 * <ul>Supports:
 *      <li>{@link ArchiveContainerEntry#BooksCsv}</li>
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
 */
public class CsvImporter
        implements Importer {

    /** Log tag. */
    private static final String TAG = "CsvImporter";

    /** Buffer for the Reader. */
    private static final int BUFFER_SIZE = 65535;

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
    private final Locale mUserLocale;

    /** import configuration. */
    private final int mOptions;

    /** cached localized "Books" string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mProgressMessage;
    @NonNull
    private final String mUnknownString;
    @NonNull
    private final ImportResults mResults = new ImportResults();

    private final StringList<Author> mAuthorCoder = new StringList<>(new AuthorCoder());
    private final StringList<Series> mSeriesCoder = new StringList<>(new SeriesCoder());
    private final StringList<Publisher> mPublisherCoder = new StringList<>(new PublisherCoder());
    private final StringList<TocEntry> mTocCoder = new StringList<>(new TocEntryCoder());
    private final StringList<Bookshelf> mBookshelfCoder;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param options {@link ImportManager#IMPORT_ONLY_NEW_OR_UPDATED} is respected.
     *                Other flags are ignored, as this class only
     *                handles {@link Options#BOOKS} anyhow.
     */
    @AnyThread
    public CsvImporter(@NonNull final Context context,
                       final int options) {

        mOptions = options;

        mDb = new DAO(TAG);
        mBookshelfCoder = new StringList<>(
                new BookshelfCoder(BooklistStyle.getDefault(context, mDb)));
        mUserLocale = AppLocale.getInstance().getUserLocale(context);

        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
        mUnknownString = context.getString(R.string.unknown);
    }

    /**
     * @param context          Current context
     * @param entity           to read data from
     * @param progressListener Progress and cancellation provider
     *
     * @return {@link ImportResults}
     *
     * @throws IndexOutOfBoundsException if the number of column headers != number of column data
     * @throws IOException               on failure
     * @throws ImportException           on failure
     */
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ReaderEntity entity,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        // we only support books, return empty results
        if (!entity.getType().equals(ArchiveContainerEntry.BooksCsv)) {
            return mResults;
        }

        // Don't close this stream!
        final InputStream is = entity.getInputStream();
        final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE);

        // We read the whole file/list into memory.
        final List<String> importedList = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            importedList.add(line);
        }
        if (importedList.isEmpty()) {
            return mResults;
        }

        // not perfect, but good enough
        if (progressListener.getProgressMaxPos() < importedList.size()) {
            progressListener.setProgressMaxPos(importedList.size());
        }

        // reused during the loop
        final Book book = new Book();
        // first line in import must be the column names
        final String[] csvColumnNames = parse(context, 0, importedList.get(0), true);

        // Store the names so we can check what is present
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase(mUserLocale);
            // temporary add to the book; used to check columns before starting actual import
            book.putString(csvColumnNames[i], "");
        }

        // Make sure required fields in Book bundle are present.
        // ENHANCE: Rationalize import to allow updates using 1 or 2 columns.
        // - For now we require some id column + a title
        // - Do a search if mandatory columns missing (e.g. allow 'import' of ISBNs).
        // - Only make some columns mandatory if the id is not in import, or not in DB
        // (i.e. if not an update)

        // need either UUID or ID
        requireColumnOrThrow(context, book,
                             // preferred, the original "book_uuid"
                             DBDefinitions.KEY_BOOK_UUID,
                             // as a courtesy, we also allow the plain "uuid"
                             DBDefinitions.KEY_UUID,
                             // but an id is also ok.
                             DBDefinitions.KEY_PK_ID);

        // need a title.
        requireColumnOrThrow(context, book, DBDefinitions.KEY_TITLE);

        // Overwrite (forceUpdate=true) existing data; or (forceUpdate=false) only
        // if the incoming data is newer.
        final boolean forceUpdate;
        if ((mOptions & ImportManager.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
            requireColumnOrThrow(context, book, DBDefinitions.KEY_UTC_LAST_UPDATED);
            forceUpdate = false;
        } else {
            forceUpdate = true;
        }

        // See if we can deduce the kind of escaping to use based on column names.
        // BC Version 1->3.3 export with family_name and author_id.
        // BC Version 3.4+ do not; latest versions make an attempt at escaping
        // characters etc to preserve formatting.
        final boolean fullEscaping = !book.contains(DBDefinitions.KEY_FK_AUTHOR)
                                     || !book.contains(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);

        // Start after headings.
        int row = 1;
        int txRowCount = 0;
        long lastUpdate = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        SyncLock txLock = null;
        try {
            // Iterate through each imported row
            while (row < importedList.size() && !progressListener.isCancelled()) {
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
                    final String[] csvDataRow = parse(context, row, importedList.get(row),
                                                      fullEscaping);
                    // clear book (avoiding construction another object)
                    book.clear();
                    // Read all columns of the current row into the Bundle.
                    // Note that some of them require further processing before being valid.
                    // Throws IndexOutOfBoundsException if the number of fields does
                    // not match the number of column headers.
                    for (int i = 0; i < csvColumnNames.length; i++) {
                        book.putString(csvColumnNames[i], csvDataRow[i]);
                    }

                    // check we have a title
                    if (book.getString(DBDefinitions.KEY_TITLE).isEmpty()) {
                        final String msg = context.getString(
                                R.string.error_csv_column_is_blank, DBDefinitions.KEY_TITLE, row);
                        throw new ImportException(msg);
                    }

                    // Do we have a DBDefinitions.KEY_BOOK_UUID in the import ?
                    @Nullable
                    final String importUuid = handleUuid(book);

                    // Do we have a DBDefinitions.KEY_PK_ID in the import ?
                    final long importNumericId = handleNumericId(book);

                    // check/fix the language
                    final Locale bookLocale = book.getLocale(context);

                    // cleanup/handle the list elements.
                    // Database access is strictly limited to fetching ID's for the list elements.
                    handleAuthors(context, mDb, book, bookLocale);
                    handleSeries(context, mDb, book, bookLocale);
                    handlePublishers(context, mDb, book, bookLocale);
                    handleAnthology(context, mDb, book, bookLocale);
                    handleBookshelves(mDb, book);

                    // do the actual import.
                    importBook(context, forceUpdate, book, importUuid, importNumericId);

                } catch (@NonNull final DAO.DaoWriteException
                        | SQLiteDoneException
                        | IndexOutOfBoundsException e) {
                    mResults.booksSkipped++;
                    //TODO: see if we can give a meaningful user-displaying string.
                    mResults.failedLinesMessage.add(mUnknownString);
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
                } catch (@NonNull final ImportException e) {
                    mResults.booksSkipped++;
                    // an ImportException has a user-displayable message.
                    mResults.failedLinesMessage.add(e.getLocalizedMessage());
                    mResults.failedLinesNr.add(row);

                    if (BuildConfig.DEBUG /* always */) {
                        if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                            Logger.warn(context, TAG, "e=" + e.getMessage(),
                                        ERROR_IMPORT_FAILED_AT_ROW + row);
                        } else if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
                            // logging with the exception is VERY HEAVY
                            Logger.error(context, TAG, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                        }
                    }
                }

                // limit the amount of progress updates, otherwise this will cause a slowdown.
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()
                    && !progressListener.isCancelled()) {
                    final String msg = String.format(mProgressMessage,
                                                     mBooksString,
                                                     mResults.booksCreated,
                                                     mResults.booksUpdated,
                                                     mResults.booksSkipped);
                    progressListener.publishProgressStep(delta, msg);
                    delta = 0;
                    lastUpdate = now;
                }
                delta++;
                row++;
            }
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(txLock);
            }
        }

        // minus 1 for the column header line
        mResults.booksProcessed = row - 1;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            Log.d(TAG, "read|mResults=" + mResults);
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
     * @param context     Current context
     * @param forceUpdate Flag for existing books:
     *                    {@code true} to always update
     *                    {@code false} to only update if the incoming data is newer
     * @param book        to import
     *
     * @throws DAO.DaoWriteException on failure
     */
    private void importBook(@NonNull final Context context,
                            final boolean forceUpdate,
                            @NonNull final Book book,
                            @Nullable final String importUuid,
                            final long importNumericId)
            throws DAO.DaoWriteException {

        final boolean hasUuid = importUuid != null && !importUuid.isEmpty();
        final boolean hasNumericId = importNumericId > 0;

        // ALWAYS let the UUID trump the ID; we may be importing someone else's list
        if (hasUuid) {
            // check if the book exists in our database, and fetch it's id.
            final long databaseBookId = mDb.getBookIdFromUuid(importUuid);
            if (databaseBookId > 0) {
                // The book exists in our database (matching UUID).

                // Explicitly set the EXISTING id on the book (the importBookId is IGNORED)
                book.putLong(DBDefinitions.KEY_PK_ID, databaseBookId);

                // and UPDATE the existing book (if allowed)
                if (forceUpdate || isImportNewer(context, mDb, book, databaseBookId)) {
                    mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                              | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                    mResults.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "UUID=" + importUuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|update|" + book.getTitle());
                    }

                } else {
                    mResults.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "UUID=" + importUuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|skipped|" + book.getTitle());
                    }
                }

            } else {
                // The book does NOT exist in our database (no match for the UUID), insert it.

                // If we have an importBookId, and it does not already exist, we reuse it.
                if (hasNumericId && !mDb.bookExistsById(importNumericId)) {
                    book.putLong(DBDefinitions.KEY_PK_ID, importNumericId);
                }

                // the Book object will contain:
                // - valid DBDefinitions.KEY_BOOK_UUID not existent in the database
                // - NO id, OR an id which does not exist in the database yet.
                // INSERT, explicitly allowing the id to be reused if present
                long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                                       | DAO.BOOK_FLAG_USE_ID_IF_PRESENT);
                mResults.booksCreated++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "UUID=" + importUuid
                               + "|importNumericId=" + importNumericId
                               + "|insert=" + insId
                               + "|" + book.getTitle());
                }
            }

        } else if (hasNumericId) {
            // Add the importNumericId back to the book.
            book.putLong(DBDefinitions.KEY_PK_ID, importNumericId);

            // Is that id already in use ?
            if (!mDb.bookExistsById(importNumericId)) {
                // The id is not in use, simply insert the book using the given importNumericId,
                // explicitly allowing the id to be reused
                long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
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

                if (forceUpdate || isImportNewer(context, mDb, book, importNumericId)) {
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

        } else {
            // Always import books which have no UUID/ID, even if the book is a potential duplicate.
            // We don't try and search/match but leave it to the user.
            long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION);
            mResults.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "UUID=''"
                           + "|ID=0"
                           + "|insert=" + insId
                           + "|" + book.getTitle());
            }
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
                                  final long bookId) {
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
     * @return the uuid, if any.
     */
    @Nullable
    private String handleUuid(@NonNull final Book book) {

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
            // if we have a UUID, store it again, using the correct key
            if (!uuid.isEmpty()) {
                book.putString(DBDefinitions.KEY_BOOK_UUID, uuid);
            }
        } else {
            uuid = null;
        }

        return uuid;
    }

    /**
     * Process the ID if present.
     *
     * @param book the book
     *
     * @return the id, if any.
     */
    private long handleNumericId(@NonNull final Book book) {
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
     * Process the bookshelves.
     * Database access is strictly limited to fetching ID's.
     *
     * @param db   Database Access
     * @param book the book
     */
    private void handleBookshelves(@NonNull final DAO db,
                                   @NonNull final Book /* in/out */ book) {

        // Current version uses/prefers KEY_BOOKSHELF,
        // but old files might contain LEGACY_BOOKSHELF_TEXT_COLUMN (and LEGACY_BOOKSHELF_ID)
        // Both are CSV formatted
        String encodedList = null;
        if (book.contains(DBDefinitions.KEY_BOOKSHELF_NAME)) {
            encodedList = book.getString(DBDefinitions.KEY_BOOKSHELF_NAME);

        } else if (book.contains(LEGACY_BOOKSHELF_TEXT_COLUMN)) {
            encodedList = book.getString(LEGACY_BOOKSHELF_TEXT_COLUMN);
        }

        if (encodedList != null && !encodedList.isEmpty()) {
            ArrayList<Bookshelf> bookshelves = mBookshelfCoder.decodeList(encodedList);
            if (!bookshelves.isEmpty()) {
                Bookshelf.pruneList(bookshelves, db);
                book.putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelves);
            }
        }
        book.remove(DBDefinitions.KEY_BOOKSHELF_NAME);
        book.remove(LEGACY_BOOKSHELF_TEXT_COLUMN);
        book.remove(LEGACY_BOOKSHELF_ID);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, we add an 'Unknown' author.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void handleAuthors(@NonNull final Context context,
                               @NonNull final DAO db,
                               @NonNull final Book /* in/out */ book,
                               @NonNull final Locale bookLocale) {

        // preferred format is a single CSV field
        final String encodedList = book.getString(CsvExporter.CSV_COLUMN_AUTHORS);
        book.remove(CsvExporter.CSV_COLUMN_AUTHORS);

        final ArrayList<Author> authors;
        if (!encodedList.isEmpty()) {
            authors = mAuthorCoder.decodeList(encodedList);
            Author.pruneList(authors, context, db, false, bookLocale);

        } else {
            authors = new ArrayList<>();

            if (book.contains(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                final String a = book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                if (!a.isEmpty()) {
                    authors.add(Author.from(a));
                }
                book.remove(DBDefinitions.KEY_AUTHOR_FORMATTED);

            } else if (book.contains(DBDefinitions.KEY_AUTHOR_FAMILY_NAME)) {
                final String family = book.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                if (!family.isEmpty()) {
                    // given will be "" if it's not present
                    final String given = book.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                    authors.add(new Author(family, given));
                }
                book.remove(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                book.remove(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);

            } else if (book.contains(OLD_STYLE_AUTHOR_NAME)) {
                final String a = book.getString(OLD_STYLE_AUTHOR_NAME);
                if (!a.isEmpty()) {
                    authors.add(Author.from(a));
                }
                book.remove(OLD_STYLE_AUTHOR_NAME);
            }
        }

        // we MUST have an author.
        if (authors.isEmpty()) {
            authors.add(Author.createUnknownAuthor(context));
        }
        book.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authors);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of series from whatever source is available.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void handleSeries(@NonNull final Context context,
                              @NonNull final DAO db,
                              @NonNull final Book /* in/out */ book,
                              @NonNull final Locale bookLocale) {

        // preferred format is a single CSV field
        final String encodedList = book.getString(CsvExporter.CSV_COLUMN_SERIES);
        book.remove(CsvExporter.CSV_COLUMN_SERIES);

        final ArrayList<Series> list;
        if (!encodedList.isEmpty()) {
            list = mSeriesCoder.decodeList(encodedList);

            // Force using the Book Locale, otherwise the import is far to slow.
            Series.pruneList(list, context, db, false, bookLocale);

        } else {
            list = new ArrayList<>();

            if (book.contains(DBDefinitions.KEY_SERIES_TITLE)) {
                final String title = book.getString(DBDefinitions.KEY_SERIES_TITLE);
                if (!title.isEmpty()) {
                    final Series series = new Series(title);
                    // number will be "" if it's not present
                    series.setNumber(book.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES));
                    list.add(series);
                }
                book.remove(DBDefinitions.KEY_SERIES_TITLE);
                book.remove(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            }
        }
        if (!list.isEmpty()) {
            book.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, list);
        }
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of Publishers.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void handlePublishers(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final Book /* in/out */ book,
                                  @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CsvExporter.CSV_COLUMN_PUBLISHERS);
        book.remove(CsvExporter.CSV_COLUMN_PUBLISHERS);

        final ArrayList<Publisher> list;
        if (!encodedList.isEmpty()) {
            list = mPublisherCoder.decodeList(encodedList);

            // Force using the Book Locale, otherwise the import is far to slow.
            Publisher.pruneList(list, context, db, false, bookLocale);
        } else {
            list = null;
        }

        if (list != null && !list.isEmpty()) {
            book.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, list);
        }
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Ignore the actual value of the DBDefinitions.KEY_TOC_BITMASK! it will be
     * 'reset' to mirror what we actually have when storing the book data
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void handleAnthology(@NonNull final Context context,
                                 @NonNull final DAO db,
                                 @NonNull final Book /* in/out */ book,
                                 @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CsvExporter.CSV_COLUMN_TOC);
        book.remove(CsvExporter.CSV_COLUMN_TOC);

        if (!encodedList.isEmpty()) {
            final ArrayList<TocEntry> toc = mTocCoder.decodeList(encodedList);
            if (!toc.isEmpty()) {
                TocEntry.pruneList(toc, context, db, false, bookLocale);
                book.putParcelableArrayList(Book.BKEY_TOC_ARRAY, toc);
            }
        }
    }

    /**
     * This CSV parser is not a complete parser, but it will parse files exported by older
     * versions.
     *
     * @param row          row number
     * @param line         with CSV fields
     * @param fullEscaping if {@code true} handle the import as a version 3.4+;
     *                     if {@code false} handle as an earlier version.
     *
     * @return an array representing the row
     *
     * @throws ImportException on failure to parse this line
     */
    @NonNull
    private String[] parse(@NonNull final Context context,
                           final int row,
                           @NonNull final String line,
                           final boolean fullEscaping)
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
            int endPos = line.length() - 1;
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
                                    final String msg = context.getString(
                                            R.string.warning_import_unescaped_quote, row, pos);
                                    throw new ImportException(msg);
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
            throw new ImportException(context.getString(R.string.error_csv_line_to_long,
                                                        row, line.length()), e);
        }
    }

    /**
     * Require a column to be present. First one found; remainders are not needed.
     *
     * @param book  to check
     * @param names columns which should be checked for, in order of preference
     *
     * @throws ImportException if no suitable column is present
     */
    private void requireColumnOrThrow(@NonNull final Context context,
                                      @NonNull final Book book,
                                      @NonNull final String... names)
            throws ImportException {
        for (String name : names) {
            if (book.contains(name)) {
                return;
            }
        }

        final String msg = context.getString(R.string.error_import_csv_file_must_contain_columns_x,
                                             TextUtils.join(",", names));
        throw new ImportException(msg);
    }
}
