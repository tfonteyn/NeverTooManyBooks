/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.SQLException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.util.logger.LoggerFactory;

public class BookRepository {

    private static final String TAG = "BookRepository";

    private static final String ERROR_CREATING_BOOK_FROM = "Failed creating book from\n";
    private static final String ERROR_UPDATING_BOOK_FROM = "Failed updating book from\n";

    private final BookDao bookDao;
    private final BookDaoHelper bookDaoHelper;
    private final Locale userLocale;
    private final SynchronizedDb db;

    /**
     * Constructor.
     * <p>
     * ENHANCE: pass in {@link DataReader.Updates} option to propagate to Authors
     *  and eventually to other linked objects.
     * <p>
     * Dev. note: This class is used/created in ViewModels, do NOT store the Context!
     *
     * @param context Current context (noy stored)
     */
    public BookRepository(@NonNull final Context context) {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        db = serviceLocator.getDb();

        bookDao = serviceLocator.getBookDao();

        final TableInfo tableInfo = db.getTableInfo(DBDefinitions.TBL_BOOKS);

        final List<Locale> userLocales = LocaleListUtils.asList(
                context.getResources().getConfiguration().getLocales());
        bookDaoHelper = new BookDaoHelper(tableInfo, userLocales);
        userLocale = userLocales.get(0);
    }

    /**
     * Create a new {@link Book}.
     *
     * @param context Current context
     * @param book    object to insert. Will be updated with the id.
     * @param flags   See {@link BookDao.ImportFlag} for flag definitions
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     * @throws DaoImageException when saving the images failed
     */
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookDao.ImportFlag> flags)
            throws DaoWriteException, DaoImageException {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Insert the book itself
            final long iId = bookDao.insert(context, bookDaoHelper, book, flags);

            // add the links to series, authors,...
            insertBookLinks(context, userLocale, book, flags);

            // populate the search suggestions table
            ServiceLocator.getInstance().getFtsDao().insert(book);

            // move the covers from the cache dir to their permanent dir/name
            persistCovers(book);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
            return iId;

        } catch (@NonNull final SQLException e) {
            removeIds(book, flags);
            throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book, e);

        } catch (@NonNull final DaoWriteException e) {
            removeIds(book, flags);
            throw e;

        } catch (@NonNull final StorageException | IOException e) {
            removeIds(book, flags);
            throw new DaoImageException(e);

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    // helper for 'insert'
    private void removeIds(@NonNull final Book book,
                           @NonNull final Set<BookDao.ImportFlag> flags) {
        // Do NOT remove them if we're importing
        if (flags.contains(BookDao.ImportFlag.UseIdIfPresent)) {
            return;
        }
        book.setId(0);
        book.setUuid(null);
    }

    /**
     * Update the given {@link Book}.
     * <p>
     * This will update <strong>ONLY</strong> the fields present in the given Book.
     * Non-present fields will not be touched. i.e. this is a delta operation.
     * <p>
     * TRIGGERS:
     * - If the Code of a {@link Book} is changed, reset external ID's and sync dates.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set.
     *                May contain extra data which will be ignored.
     * @param flags   See {@link BookDao.ImportFlag} for flag definitions
     *
     * @throws DaoWriteException on failure
     * @throws DaoImageException when saving the images failed
     */
    public void update(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookDao.ImportFlag> flags)
            throws DaoWriteException, DaoImageException {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Update the book itself
            bookDao.update(context, bookDaoHelper, book, flags);

            // add the links to series, authors,...
            insertBookLinks(context, userLocale, book, flags);

            // populate the search suggestions table
            ServiceLocator.getInstance().getFtsDao().update(book.getId());

            // move the covers from the cache dir to their permanent dir/name
            persistCovers(book);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }

        } catch (@NonNull final SQLException e) {
            throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book, e);

        } catch (@NonNull final StorageException | IOException e) {
            throw new DaoImageException(e);

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Called during {@link #insert} and {@link #update}.
     * Each step in this method will first delete all entries in the Book-[tableX] table
     * for the given book, and then insert the new links.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context    Current context
     * @param userLocale Current Locale
     * @param book       A collection with the columns to be set. May contain extra data.
     * @param flags      See {@link BookDao.ImportFlag} for flag definitions
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException (debug) if there is no current transaction
     */
    private void insertBookLinks(@NonNull final Context context,
                                 @NonNull final Locale userLocale,
                                 @NonNull final Book book,
                                 @NonNull final Set<BookDao.ImportFlag> flags)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Only lookup locales
        // when we're NOT in batch mode (i.e. NOT doing an import)
        final boolean lookupLocale = !flags.contains(BookDao.ImportFlag.RunInBatch);

        // FIXME: apply useIdIfPresent to the tags collection, perhaps to others as well
        //final boolean useIdIfPresent = !flags.contains(ImportFlag.UseIdIfPresent);

        // unconditional lookup of the book locale!
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        if (book.contains(Book.BKEY_BOOKSHELF_LIST)) {
            // Bookshelves will be inserted if new, but never updated
            serviceLocator.getBookshelfDao().insertOrUpdate(context,
                                                            book.getId(),
                                                            book.getBookshelves());
        }

        if (book.contains(Book.BKEY_AUTHOR_LIST)) {
            serviceLocator.getAuthorDao().insertOrUpdate(context,
                                                         book.getId(), true,
                                                         book.getAuthors(),
                                                         author -> bookLocale);
        }


        if (book.contains(Book.BKEY_SERIES_LIST)) {
            final Function<Series, Locale> localeSupplier = series -> {
                if (lookupLocale) {
                    return series.getLocale(userLocale).orElse(bookLocale);
                } else {
                    return bookLocale;
                }
            };
            serviceLocator.getSeriesDao().insertOrUpdate(context,
                                                         book.getId(), true,
                                                         book.getSeries(),
                                                         localeSupplier);
        }

        if (book.contains(Book.BKEY_PUBLISHER_LIST)) {
            serviceLocator.getPublisherDao().insertOrUpdate(context,
                                                            book.getId(), true,
                                                            book.getPublishers(),
                                                            publisher -> bookLocale);
        }

        if (book.contains(Book.BKEY_TOC_LIST)) {
            // TOC entries are two steps away; they can exist in other books
            // Hence we will both insert new entries
            // AND update existing ones as needed.
            serviceLocator.getTocEntryDao().insertOrUpdate(context,
                                                           book.getId(),
                                                           book.getToc(),
                                                           tocEntry -> bookLocale);
        }

        if (book.contains(Book.BKEY_TAG_LIST)) {
            // These are two steps away; they can exist in other books.
            // We will insert new entries
            // AND update existing ones as needed.
            serviceLocator.getTagDao().insertOrUpdate(context,
                                                      book.getId(),
                                                      book.getTags(),
                                                      tag -> bookLocale);
        }

        if (book.contains(Identifier.Value.BKEY_LIST)) {
            // These are two steps away; they can exist in other books.
            // However, we in fact do NOT use id's except for the internal database references.
            // Instead, we always work with the String key.
            // We will insert new entries
            // but there is nothing to update as such.
            serviceLocator.getBookIdentifierDao()
                          .insertOrUpdate(Identifier.EntityType.Book,
                                          book.getId(), book.getIdentifiers());
        }

        // Returning a book == deleting the loanee,
        // is handled directly, here we only need to bother with insert/update
        if (book.contains(DBKey.LOANEE_NAME)) {
            serviceLocator.getLoaneeDao().setLoanee(book);
        }

        // Handle synchronisation field.
        if (book.contains(DBKey.CALIBRE.BOOK_UUID)) {
            // Calibre libraries will be inserted if new, but not updated
            serviceLocator.getCalibreDao().insertOrUpdate(context, book);
        }

        // Handle synchronisation field.
        if (book.getIdentifierValue(Identifier.SID_STRIP_INFO).isPresent()) {
            serviceLocator.getStripInfoDao().insertOrUpdate(book);
        }
    }

    /**
     * Called during {@link #insert} and {@link #update}.
     *
     * @param book to process
     *
     * @throws StorageException The covers directory is not available
     * @throws IOException      on generic/other IO failures
     */
    @SuppressWarnings("OverlyBroadThrowsClause")
    private void persistCovers(@NonNull final Book book)
            throws StorageException, IOException {

        final String uuid = book.getUuid();
        final CoverStorage coverStorage = ServiceLocator.getInstance().getCoverStorage();

        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            if (book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx])) {
                final String fileSpec = book.getString(Book.BKEY_TMP_FILE_SPEC[cIdx]);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "persistCovers",
                                    "BKEY_TMP_FILE_SPEC[" + cIdx + "]=`" + fileSpec + '`');
                }

                if (fileSpec.isEmpty()) {
                    // A *present* but empty fileSpec indicates we need to delete the cover
                    final int finalCIdx = cIdx;
                    ASyncExecutor.STORAGE_WRITES.execute(
                            () -> coverStorage.delete(uuid, finalCIdx));
                } else {
                    // Rename the temp file to the uuid permanent file name
                    coverStorage.persist(new File(fileSpec), uuid, cIdx);
                }

                book.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
            }
        }
    }
}
