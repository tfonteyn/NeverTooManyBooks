/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoCoverException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;

/**
 * Database access helper class.
 * <p>
 * insert/update of a Book failures are handled with {@link DaoWriteException}
 * which makes the deep nesting of calls easier to handle.
 * <p>
 * All others follow the pattern of:
 * insert: return new id, or {@code -1} for error.
 * update: return rows affected, can be 0; or boolean when appropriate.
 * <p>
 * Individual deletes return boolean (i.e. 0 or 1 row affected)
 * Multi-deletes return either void, or the number of rows deleted.
 * <p>
 * TODO: some places ignore insert/update failures. A storage full could trigger a failure.
 */
public class BookDaoImpl
        extends BaseDaoImpl
        implements BookDao {

    /** Log tag. */
    private static final String TAG = "BookDaoImpl";

    private static final String ERROR_CREATING_BOOK_FROM = "Failed creating book from\n";
    private static final String ERROR_UPDATING_BOOK_FROM = "Failed updating book from\n";
    private static final String ERROR_STORING_COVERS = "Failed storing the covers for book from\n";
    private static final String ERROR_UUID = "Invalid UUID";

    @NonNull
    private final DateParser dateParser;
    @NonNull
    private final Supplier<AuthorDao> authorDaoSupplier;
    @NonNull
    private final Supplier<SeriesDao> seriesDaoSupplier;
    @NonNull
    private final Supplier<PublisherDao> publisherDaoSupplier;
    @NonNull
    private final Supplier<BookshelfDao> bookshelfDaoSupplier;
    @NonNull
    private final Supplier<TocEntryDao> tocEntryDaoSupplier;
    @NonNull
    private final Supplier<LoaneeDao> loaneeDaoDaoSupplier;
    @NonNull
    private final Supplier<CalibreDao> calibreDaoSupplier;
    @NonNull
    private final Supplier<StripInfoDao> stripInfoDaoSupplier;
    @NonNull
    private final Supplier<FtsDao> ftsDaoSupplier;
    @NonNull
    private final Supplier<CoverStorage> coverStorageSupplier;
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                    Underlying database
     * @param systemLocale          to use for ISO date parsing
     * @param authorDaoSupplier     deferred supplier for the {@link AuthorDao}
     * @param seriesDaoSupplier     deferred supplier for the {@link SeriesDao}
     * @param publisherDaoSupplier  deferred supplier for the {@link PublisherDao}
     * @param bookshelfDaoSupplier  deferred supplier for the {@link BookshelfDao}
     * @param tocEntryDaoSupplier   deferred supplier for the {@link TocEntryDao}
     * @param loaneeDaoDaoSupplier  deferred supplier for the {@link LoaneeDao}
     * @param calibreDaoSupplier    deferred supplier for the {@link CalibreDao}
     * @param stripInfoDaoSupplier  deferred supplier for the {@link StripInfoDao}
     * @param ftsDaoSupplier        deferred supplier for the {@link FtsDao}
     * @param coverStorageSupplier  deferred supplier for the {@link CoverStorage}
     * @param reorderHelperSupplier deferred supplier for the {@link ReorderHelper}
     */
    public BookDaoImpl(@NonNull final SynchronizedDb db,
                       @NonNull final Locale systemLocale,
                       @NonNull final Supplier<AuthorDao> authorDaoSupplier,
                       @NonNull final Supplier<SeriesDao> seriesDaoSupplier,
                       @NonNull final Supplier<PublisherDao> publisherDaoSupplier,
                       @NonNull final Supplier<BookshelfDao> bookshelfDaoSupplier,
                       @NonNull final Supplier<TocEntryDao> tocEntryDaoSupplier,
                       @NonNull final Supplier<LoaneeDao> loaneeDaoDaoSupplier,
                       @NonNull final Supplier<CalibreDao> calibreDaoSupplier,
                       @NonNull final Supplier<StripInfoDao> stripInfoDaoSupplier,
                       @NonNull final Supplier<FtsDao> ftsDaoSupplier,
                       @NonNull final Supplier<CoverStorage> coverStorageSupplier,
                       @NonNull final Supplier<ReorderHelper> reorderHelperSupplier) {
        super(db, TAG);
        dateParser = new ISODateParser(systemLocale);
        this.authorDaoSupplier = authorDaoSupplier;
        this.seriesDaoSupplier = seriesDaoSupplier;
        this.publisherDaoSupplier = publisherDaoSupplier;
        this.bookshelfDaoSupplier = bookshelfDaoSupplier;
        this.tocEntryDaoSupplier = tocEntryDaoSupplier;
        this.loaneeDaoDaoSupplier = loaneeDaoDaoSupplier;
        this.calibreDaoSupplier = calibreDaoSupplier;
        this.stripInfoDaoSupplier = stripInfoDaoSupplier;
        this.ftsDaoSupplier = ftsDaoSupplier;
        this.coverStorageSupplier = coverStorageSupplier;
        this.reorderHelperSupplier = reorderHelperSupplier;
    }

    /**
     * Update the 'last updated' of the given book.
     * <p>
     * If successful, the book itself will also be updated with
     * the current date-time (which will be very slightly 'later' then what we store).
     *
     * @param book to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean touch(@NonNull final Book book) {
        final boolean result;
        final long bookId = book.getId();
        try (SynchronizedStatement stmt = db.compileStatement(Sql.TOUCH)) {
            stmt.bindLong(1, bookId);
            result = 0 < stmt.executeUpdateDelete();
        }
        if (result) {
            book.setLastModified(LocalDateTime.now(ZoneOffset.UTC));
            return true;

        } else {
            return false;
        }
    }

    @Override
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Book /* in/out */ book,
                       @NonNull final Set<BookFlag> flags)
            throws StorageException,
                   DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        //noinspection OverlyBroadCatchBlock,CheckStyle
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final BookDaoHelper bookDaoHelper = new BookDaoHelper(context,
                                                                  coverStorageSupplier,
                                                                  reorderHelperSupplier,
                                                                  book, true);
            final ContentValues cv = bookDaoHelper
                    .process(context)
                    .filterValues(db.getTableInfo(TBL_BOOKS));

            // Make sure we have at least one author
            final List<Author> authors = book.getAuthors();
            if (authors.isEmpty()) {
                throw new DaoInsertException("No authors for book=" + book);
            }

            final String addedOrUpdatedNow = SqlEncode.date(LocalDateTime.now(ZoneOffset.UTC));

            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(DBKey.DATE_ADDED__UTC)) {
                cv.put(DBKey.DATE_ADDED__UTC, addedOrUpdatedNow);
            }
            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(DBKey.DATE_LAST_UPDATED__UTC)) {
                cv.put(DBKey.DATE_LAST_UPDATED__UTC, addedOrUpdatedNow);
            }

            // if we have an id and we're allowed, use it as-is.
            if (book.getId() > 0 && flags.contains(BookFlag.UseIdIfPresent)) {
                cv.put(DBKey.PK_ID, book.getId());
            } else {
                // in all other circumstances, make absolutely sure we DO NOT pass in an id.
                cv.remove(DBKey.PK_ID);
            }

            // go!
            final long newBookId = db.insert(TBL_BOOKS.getName(), cv);
            if (newBookId <= 0) {
                LoggerFactory.getLogger()
                             .e(TAG, new Throwable(), "Insert failed"
                                                      + "|table=" + TBL_BOOKS.getName()
                                                      + "|cv=" + cv);

                book.putLong(DBKey.PK_ID, 0);
                book.remove(DBKey.BOOK_UUID);
                throw new DaoInsertException(ERROR_CREATING_BOOK_FROM + book);
            }

            // Set the new id/uuid on the Book itself
            // We manually remove it again (see below) upon any error
            book.putLong(DBKey.PK_ID, newBookId);
            // always lookup the UUID
            // (even if we inserted with a uuid... to protect against future changes)
            final String uuid = getBookUuid(newBookId);
            SanityCheck.requireValue(uuid, ERROR_UUID);
            book.putString(DBKey.BOOK_UUID, uuid);

            // next we add the links to series, authors,...
            insertBookLinks(context, book, flags);

            // and populate the search suggestions table
            ftsDaoSupplier.get().insert(newBookId);

            // lastly we move the covers from the cache dir to their permanent dir/name
            try {
                bookDaoHelper.persistCovers();

            } catch (@NonNull final StorageException e) {
                book.putLong(DBKey.PK_ID, 0);
                book.remove(DBKey.BOOK_UUID);
                throw e;

            } catch (@NonNull final IOException e) {
                book.putLong(DBKey.PK_ID, 0);
                book.remove(DBKey.BOOK_UUID);
                throw new DaoCoverException(ERROR_STORING_COVERS + book, e);
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
            return newBookId;

        } catch (@NonNull final RuntimeException e) {
            // Theoretically there is no need to catch RTE here, but paranoia...
            LoggerFactory.getLogger().e(TAG, e);
            throw new DaoInsertException(ERROR_CREATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookFlag> flags)
            throws StorageException,
                   DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        //noinspection OverlyBroadCatchBlock,CheckStyle
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final BookDaoHelper bookDaoHelper = new BookDaoHelper(context,
                                                                  coverStorageSupplier,
                                                                  reorderHelperSupplier,
                                                                  book, false);
            final ContentValues cv = bookDaoHelper
                    .process(context)
                    .filterValues(db.getTableInfo(TBL_BOOKS));

            // Disallow UUID updates
            if (cv.containsKey(DBKey.BOOK_UUID)) {
                cv.remove(DBKey.BOOK_UUID);
            }

            // set the DATE_LAST_UPDATED__UTC to 'now' if we're allowed,
            // or if it's not already present.
            if (!flags.contains(BookFlag.UseUpdateDateIfPresent)
                || !cv.containsKey(DBKey.DATE_LAST_UPDATED__UTC)) {
                cv.put(DBKey.DATE_LAST_UPDATED__UTC, SqlEncode
                        .date(LocalDateTime.now(ZoneOffset.UTC)));
            }

            // Reminder: We're updating ONLY the fields present in the ContentValues.
            // Other fields in the database row are not affected.
            // go !
            final int rowsAffected = db.update(TBL_BOOKS.getName(), cv, DBKey.PK_ID + "=?",
                                               new String[]{String.valueOf(book.getId())});

            if (rowsAffected > 0) {
                // always lookup the UUID
                final String uuid = getBookUuid(book.getId());
                SanityCheck.requireValue(uuid, ERROR_UUID);
                book.putString(DBKey.BOOK_UUID, uuid);

                insertBookLinks(context, book, flags);

                ftsDaoSupplier.get().update(book.getId());

                try {
                    bookDaoHelper.persistCovers();
                } catch (@NonNull final IOException e) {
                    throw new DaoCoverException(ERROR_STORING_COVERS + book);
                }

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }

            throw new DaoUpdateException(ERROR_UPDATING_BOOK_FROM + book);
        } catch (@NonNull final RuntimeException e) {
            // Theoretically there is no need to catch RTE here, but paranoia...
            LoggerFactory.getLogger().e(TAG, e);
            throw new DaoUpdateException(ERROR_UPDATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        final boolean success = delete(book.getId());
        if (success) {
            book.remove(DBKey.PK_ID);
            book.remove(DBKey.BOOK_UUID);
        }
        return success;
    }

    @Override
    public boolean delete(@NonNull final BookLight bookLight) {
        final boolean success = delete(bookLight.getId());
        if (success) {
            bookLight.setId(0);
        }
        return success;
    }

    @Override
    public boolean delete(@IntRange(from = 1) final long id) {
        final String uuid = getBookUuid(id);
        // sanity check
        if (uuid == null || uuid.isBlank()) {
            return false;
        }

        return deleteByUuid(List.of(uuid)) == 1;
    }

    /**
     * Delete the books for the given list of UUIDs.
     *
     * @param uuids list of book UUIDs
     *
     * @return the number of books deleted (i.e rowsAffected)
     */
    @Override
    public int deleteByUuid(@NonNull final List<String> uuids) {
        final List<String> actuallyDeleted = new ArrayList<>();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Delete the book, and remember which ones were really deleted.
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_UUID)) {
                for (final String uuid : uuids) {
                    stmt.bindString(1, uuid);
                    if (stmt.executeUpdateDelete() > 0) {
                        actuallyDeleted.add(uuid);
                    }
                }
            }

            // At this point all database actions were successful.
            // Now delete the covers for those actually deleted books.
            // Note that if anything goes wrong here:
            // - the database will be rolled back as expected.
            // - the already deleted covers will NOT be restored
            //   automatically. The user can however restore them
            //   one-by-one if they enabled the undo facility for covers.
            // but what could go wrong during a file-delete op... flw... oh well.
            actuallyDeleted.forEach(uuid -> {
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    coverStorageSupplier.get().delete(uuid, cIdx);
                }
            });

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        return actuallyDeleted.size();
    }

    /**
     * Called during book insert & update.
     * Each step in this method will first delete all entries in the Book-[tableX] table
     * for this bookId, and then insert the new links.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     * @param flags   See {@link BookFlag} for flag definitions
     *
     * @throws DaoWriteException on failure
     */
    private void insertBookLinks(@NonNull final Context context,
                                 @NonNull final Book book,
                                 @NonNull final Set<BookFlag> flags)
            throws DaoWriteException {

        // Only lookup locales
        // when we're NOT in batch mode (i.e. NOT doing an import)
        final boolean lookupLocale = !flags.contains(BookFlag.RunInBatch);

        // Only update individual Author, Series, Publisher
        // when we're NOT in batch mode (i.e. NOT doing an import)
        final boolean doUpdates = !flags.contains(BookFlag.RunInBatch);

        // unconditional lookup of the book locale!
        final Locale bookLocale = book.getLocaleOrUserLocale(context);

        if (book.contains(Book.BKEY_BOOKSHELF_LIST)) {
            // Bookshelves will be inserted if new, but never updated
            bookshelfDaoSupplier.get().insertOrUpdate(context, book.getId(),
                                                      book.getBookshelves());
        }

        if (book.contains(Book.BKEY_AUTHOR_LIST)) {
            final List<Author> list = book.getAuthors();
            // Authors will be inserted if new, but only updated if allowed
            authorDaoSupplier.get().insertOrUpdate(context, book.getId(), doUpdates,
                                                   list, author -> bookLocale);
        }

        if (book.contains(Book.BKEY_SERIES_LIST)) {
            final List<Series> list = book.getSeries();
            final Function<Series, Locale> localeSupplier = item -> {
                if (lookupLocale) {
                    return item.getLocale(context).orElse(bookLocale);
                } else {
                    return bookLocale;
                }
            };
            // Series will be inserted if new, but only updated if allowed
            seriesDaoSupplier.get().insertOrUpdate(context, book.getId(), doUpdates,
                                                   list, localeSupplier);
        }

        if (book.contains(Book.BKEY_PUBLISHER_LIST)) {
            final List<Publisher> list = book.getPublishers();
            // Publishers will be inserted if new, but only updated if allowed
            publisherDaoSupplier.get().insertOrUpdate(context, book.getId(), doUpdates,
                                                      list, publisher -> bookLocale);
        }

        if (book.contains(Book.BKEY_TOC_LIST)) {
            // TOC entries are two steps away; they can exist in other books
            // Hence we will both insert new entries
            // AND update existing ones as needed.
            tocEntryDaoSupplier.get().insertOrUpdate(context, book.getId(),
                                                     book.getToc(), tocEntry -> bookLocale);
        }

        if (book.contains(DBKey.LOANEE_NAME)) {
            loaneeDaoDaoSupplier.get().setLoanee(book);
        }

        if (book.contains(DBKey.CALIBRE_BOOK_UUID)) {
            // Calibre libraries will be inserted if new, but not updated
            calibreDaoSupplier.get().updateOrInsert(context, book);
        }

        if (book.contains(DBKey.SID_STRIP_INFO)) {
            stripInfoDaoSupplier.get().updateOrInsert(book);
        }
    }

    @Override
    public boolean setRead(@NonNull final Book book,
                           final boolean read) {
        final String now = read ? SqlEncode.date(LocalDateTime.now()) : "";

        final boolean success;
        // don't call standalone method, we want to use the same 'now' to update the book
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE_FIELD_READ)) {
            stmt.bindBoolean(1, read);
            stmt.bindString(2, now);
            stmt.bindLong(3, book.getId());
            success = 0 < stmt.executeUpdateDelete();
        }

        if (success) {
            book.putBoolean(DBKey.READ__BOOL, read);
            book.putString(DBKey.READ_END__DATE, now);
            book.putString(DBKey.DATE_LAST_UPDATED__UTC, now);
        }

        return success;
    }

    @Override
    public int count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Return an Cursor with all Books selected by the passed arguments.
     *
     * @param whereClause   without the 'where' keyword, can be {@code null} or {@code ""}
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @param orderByClause without the 'order by' keyword, can be {@code null} or {@code ""}
     *
     * @return A Book Cursor with 0..1 row
     */
    @NonNull
    private TypedCursor getBookCursor(@Nullable final CharSequence whereClause,
                                      @Nullable final String[] selectionArgs,
                                      @Nullable final CharSequence orderByClause) {

        final String sql = Sql.SELECT_BOOK_FROM
                           + (whereClause != null && whereClause.length() > 0
                              ? _WHERE_ + whereClause : "")
                           + (orderByClause != null && orderByClause.length() > 0
                              ? _ORDER_BY_ + orderByClause : "")

                           + _COLLATION;

        final TypedCursor cursor = db.rawQueryWithTypedCursor(sql, selectionArgs, null);
        // force the TypedCursor to retrieve the real column types.
        cursor.setDb(db, TBL_BOOKS);
        return cursor;
    }

    @Override
    @NonNull
    public TypedCursor fetchById(@IntRange(from = 1) final long id) {
        return getBookCursor(TBL_BOOKS.dot(DBKey.PK_ID) + "=?",
                             new String[]{String.valueOf(id)},
                             null);
    }

    @Override
    @NonNull
    public TypedCursor fetchByKey(@NonNull final String key,
                                  @NonNull final String externalId) {
        return getBookCursor(TBL_BOOKS.dot(key) + "=?", new String[]{externalId},
                             TBL_BOOKS.dot(DBKey.PK_ID));
    }

    @Override
    @NonNull
    public TypedCursor fetchForAutoUpdate(@NonNull final List<Long> idList) {
        if (idList.isEmpty()) {
            throw new IllegalArgumentException("idList.isEmpty()");
        }

        if (idList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(DBKey.AUTO_UPDATE) + "=1"
                                 + _AND_ + TBL_BOOKS.dot(DBKey.PK_ID) + "=?",
                                 new String[]{String.valueOf(idList.get(0))},
                                 null);

        } else {
            return getBookCursor(TBL_BOOKS.dot(DBKey.AUTO_UPDATE) + "=1"
                                 + _AND_ + TBL_BOOKS.dot(DBKey.PK_ID)
                                 + " IN (" + TextUtils.join(",", idList) + ')',
                                 null,
                                 TBL_BOOKS.dot(DBKey.PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchForAutoUpdateFromIdOnwards(@IntRange(from = 1) final long id) {
        return getBookCursor(TBL_BOOKS.dot(DBKey.AUTO_UPDATE) + "=1"
                             + _AND_ + TBL_BOOKS.dot(DBKey.PK_ID) + ">=?",
                             new String[]{String.valueOf(id)},
                             TBL_BOOKS.dot(DBKey.PK_ID));
    }

    @Override
    public int countBooksForExport(@Nullable final LocalDateTime sinceDateTime) {
        if (sinceDateTime == null) {
            try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
                return (int) stmt.simpleQueryForLongOrZero();
            }
        } else {
            try (SynchronizedStatement stmt = db.compileStatement(
                    Sql.COUNT_ALL + _WHERE_ + DBKey.DATE_LAST_UPDATED__UTC + ">=?")) {
                stmt.bindString(1, SqlEncode.date(sinceDateTime));
                return (int) stmt.simpleQueryForLongOrZero();
            }
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchBooksForExport(@Nullable final LocalDateTime sinceDateTime) {
        if (sinceDateTime == null) {
            return getBookCursor(null, null, TBL_BOOKS.dot(DBKey.PK_ID));
        } else {
            return getBookCursor(TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC) + ">=?",
                                 new String[]{SqlEncode.date(sinceDateTime)},
                                 TBL_BOOKS.dot(DBKey.PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchBooksForExportToCalibre(@IntRange(from = 1) final long libraryId,
                                                    @Nullable final LocalDateTime sinceDateTime) {
        if (sinceDateTime == null) {
            return getBookCursor(TBL_CALIBRE_BOOKS.dot(DBKey.FK_CALIBRE_LIBRARY) + "=?",
                                 new String[]{String.valueOf(libraryId)},
                                 TBL_BOOKS.dot(DBKey.PK_ID));
        } else {
            return getBookCursor(TBL_CALIBRE_BOOKS.dot(DBKey.FK_CALIBRE_LIBRARY) + "=?"
                                 + _AND_ + TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC) + ">=?",
                                 new String[]{String.valueOf(libraryId),
                                         SqlEncode.date(sinceDateTime)},
                                 TBL_BOOKS.dot(DBKey.PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchBooksForExportToStripInfo(@Nullable final LocalDateTime sinceDateTime) {
        if (sinceDateTime == null) {
            return getBookCursor(null, null, TBL_BOOKS.dot(DBKey.PK_ID));
        } else {
            return getBookCursor(TBL_STRIPINFO_COLLECTION.dot(
                                         DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC) + ">=?",
                                 new String[]{SqlEncode.date(sinceDateTime)},
                                 TBL_BOOKS.dot(DBKey.PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchByIsbn(@NonNull final List<ISBN> isbnList) {
        if (isbnList.isEmpty()) {
            throw new IllegalArgumentException("isbnList.isEmpty()");
        }

        if (isbnList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(DBKey.BOOK_ISBN) + "=?",
                                 new String[]{isbnList.get(0).asText()}, null);
        } else {
            return getBookCursor(TBL_BOOKS.dot(DBKey.BOOK_ISBN)
                                 + " IN ("
                                 + isbnList.stream()
                                           .map(s -> '\'' + s.asText() + '\'')
                                           .collect(Collectors.joining(","))
                                 + ')',
                                 null,
                                 TBL_BOOKS.dot(DBKey.PK_ID));
        }
    }

    @Override
    @NonNull
    public List<String> getBookUuidList() {
        return getColumnAsStringArrayList(Sql.SELECT_ALL_UUID);
    }

    /**
     * Return the book UUID based on the id.
     *
     * @param bookId of the book
     *
     * @return the book UUID, or {@code null} if not found/failure
     */
    @Nullable
    private String getBookUuid(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_UUID_BY_ID)) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    @Override
    @IntRange(from = 0)
    public long getBookIdByUuid(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_ID_BY_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public List<Pair<Long, String>> getBookIdAndTitleByIsbn(@NonNull final ISBN isbn) {
        final List<Pair<Long, String>> list = new ArrayList<>();
        // If the string is ISBN-10 compatible, we search on both formats;
        // i.e. an actual ISBN-10, or an ISBN-13 in the 978 range.
        if (isbn.isIsbn10Compat()) {
            try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ISBN_10_OR_13,
                                             new String[]{isbn.asText(ISBN.Type.Isbn10),
                                                     isbn.asText(ISBN.Type.Isbn13)})) {
                while (cursor.moveToNext()) {
                    list.add(new Pair<>(cursor.getLong(0),
                                        cursor.getString(1)));
                }
            }
        } else {
            // otherwise just search on the string as-is; regardless of validity
            // (this would actually include valid ISBN-13 in the 979 range).
            try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ISBN, new String[]{isbn.asText()})) {
                while (cursor.moveToNext()) {
                    list.add(new Pair<>(cursor.getLong(0),
                                        cursor.getString(1)));
                }
            }
        }

        return list;
    }

    @Override
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean bookExistsById(@IntRange(from = 1) final long id) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.BOOK_ID_EXISTS)) {
            stmt.bindLong(1, id);
            return stmt.simpleQueryForLongOrZero() == 1;
        }
    }

    @Override
    public boolean bookExistsByIsbn(@NonNull final String isbnStr) {
        final ISBN isbn = new ISBN(isbnStr, false);
        // If the string is ISBN-10 compatible, we search on both formats;
        // i.e. an actual ISBN-10, or an ISBN-13 in the 978 range.
        if (isbn.isIsbn10Compat()) {
            try (SynchronizedStatement stmt = db.compileStatement(Sql.BOOK_ISBN_10_OR_13_EXISTS)) {
                stmt.bindString(1, isbn.asText(ISBN.Type.Isbn10));
                stmt.bindString(2, isbn.asText(ISBN.Type.Isbn13));
                return stmt.simpleQueryForLongOrZero() == 1;
            }
        } else {
            // otherwise just search on the string as-is; regardless of validity
            // (this would actually include valid ISBN-13 in the 979 range).
            try (SynchronizedStatement stmt = db.compileStatement(Sql.BOOK_ISBN_EXISTS)) {
                stmt.bindString(1, isbn.asText());
                return stmt.simpleQueryForLongOrZero() == 1;
            }
        }
    }

    @Override
    @NonNull
    public List<String> getCurrencyCodes(@NonNull final String key) {
        if (!DBKey.MONEY_KEYS.contains(key)) {
            throw new IllegalArgumentException(key);
        }

        final String sql = "SELECT DISTINCT UPPER(" + key + DBKey.CURRENCY_SUFFIX
                           + ") FROM " + TBL_BOOKS.getName()
                           + _ORDER_BY_ + key + DBKey.CURRENCY_SUFFIX + _COLLATION;

        return getColumnAsStringArrayList(sql);
    }

    @NonNull
    public Optional<LocalDateTime> getLastUpdateDate(@IntRange(from = 1) final long id) {
        try (SynchronizedStatement stmt =
                     db.compileStatement(Sql.FIND_LAST_UPDATE_DATE_BY_BOOK_ID)) {
            stmt.bindLong(1, id);
            return dateParser.parse(stmt.simpleQueryForStringOrNull());
        }
    }

    private static final class Sql {

        /** Delete a {@link Book}. */
        static final String DELETE_BY_UUID =
                DELETE_FROM_ + TBL_BOOKS.getName() + _WHERE_ + DBKey.BOOK_UUID + "=?";

        /** Update a single Book's read status and read_end date. */
        static final String UPDATE_FIELD_READ =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
                + ',' + DBKey.READ__BOOL + "=?"
                + ',' + DBKey.READ_END__DATE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Update a {@link Book} {@link DBKey#DATE_LAST_UPDATED__UTC} to 'now'. */
        static final String TOUCH =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Get a count of the {@link Book}s. */
        static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_BOOKS.getName();

        /** Find the {@link Book} id+title based on a search for the ISBN (both 10 & 13). */
        static final String FIND_BY_ISBN_10_OR_13 =
                SELECT_ + DBKey.PK_ID + ',' + DBKey.TITLE + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + DBKey.BOOK_ISBN + " LIKE ? OR " + DBKey.BOOK_ISBN + " LIKE ?";

        /**
         * Find the {@link Book} id+title based on a search for the ISBN.
         * The isbn need not be valid and can in fact be any code whatsoever.
         */
        static final String FIND_BY_ISBN =
                SELECT_ + DBKey.PK_ID + ',' + DBKey.TITLE + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + DBKey.BOOK_ISBN + " LIKE ?";

        /** Find the UUID of a {@link Book} by its id. */
        static final String FIND_UUID_BY_ID =
                SELECT_ + DBKey.BOOK_UUID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Find the id of a {@link Book} by its UUID. */
        static final String FIND_ID_BY_UUID =
                SELECT_ + DBKey.PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + DBKey.BOOK_UUID + "=?";

        /** Find the {@link DBKey#DATE_LAST_UPDATED__UTC} for a {@link Book} by its id. */
        static final String FIND_LAST_UPDATE_DATE_BY_BOOK_ID =
                SELECT_ + DBKey.DATE_LAST_UPDATED__UTC + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Check if a {@link Book} exists with a specified {@link DBKey#PK_ID}.
         * The result will be {@code 0} or {@code 1}.
         */
        static final String BOOK_ID_EXISTS =
                SELECT_EXISTS_ + '('
                + SELECT_ + "null" + _FROM_ + TBL_BOOKS.getName() + _WHERE_ + DBKey.PK_ID + "=?"
                + ')';

        /**
         * Check if a {@link Book} exists with a single specified {@link DBKey#BOOK_ISBN}.
         * The result will be {@code 0} or {@code 1}.
         */
        static final String BOOK_ISBN_EXISTS =
                SELECT_EXISTS_ + '(' + Sql.FIND_BY_ISBN + ')';

        /**
         * Check if a {@link Book} exists with a either a {@link DBKey#BOOK_ISBN}
         * ISBN-10, or an ISBN-13 in the 978 range.
         * The result will be {@code 0} or {@code 1}.
         */
        static final String BOOK_ISBN_10_OR_13_EXISTS =
                SELECT_EXISTS_ + '(' + Sql.FIND_BY_ISBN_10_OR_13 + ')';

        /** Book UUID only, for accessing all cover image files. */
        static final String SELECT_ALL_UUID =
                SELECT_ + DBKey.BOOK_UUID + _FROM_ + TBL_BOOKS.getName();

        /** The SELECT and FROM clause for getting a book (list). */
        static final String SELECT_BOOK_FROM;

        static {
            //NEWTHINGS: adding fields ? Now is a good time to update {@link Book#duplicate}

            // Note we could use TBL_BOOKS.dot("*")
            // We'd fetch the unneeded TITLE_OB field, but that would be ok.
            // Nevertheless, listing the fields here gives a better understanding

            SELECT_BOOK_FROM = SELECT_ + TBL_BOOKS.dotAs(
                    DBKey.PK_ID, DBKey.BOOK_UUID,
                    DBKey.TITLE, DBKey.TITLE_ORIGINAL_LANG,
                    DBKey.BOOK_ISBN, DBKey.BOOK_CONTENT_TYPE,
                    DBKey.BOOK_PUBLICATION__DATE, DBKey.PRINT_RUN,
                    DBKey.PRICE_LISTED, DBKey.PRICE_LISTED_CURRENCY,
                    DBKey.FIRST_PUBLICATION__DATE,
                    DBKey.FORMAT, DBKey.COLOR, DBKey.GENRE, DBKey.LANGUAGE, DBKey.PAGE_COUNT,
                    // Main/public description about the content/publication
                    DBKey.DESCRIPTION,
                    // partially edition info, partially user-owned info.
                    DBKey.EDITION__BITMASK,
                    // user notes
                    DBKey.PERSONAL_NOTES,
                    DBKey.BOOK_CONDITION, DBKey.BOOK_CONDITION_COVER,
                    DBKey.LOCATION, DBKey.SIGNED__BOOL, DBKey.RATING,
                    DBKey.READ__BOOL, DBKey.READ_START__DATE, DBKey.READ_END__DATE,
                    DBKey.DATE_ACQUIRED,
                    DBKey.PRICE_PAID, DBKey.PRICE_PAID_CURRENCY,
                    // added/updated
                    DBKey.DATE_ADDED__UTC, DBKey.DATE_LAST_UPDATED__UTC,
                    DBKey.AUTO_UPDATE
                    //NEWTHINGS: adding a new search engine: optional: add engine specific keys
            )

                               + ',' + TBL_BOOKS.dotAs(SearchEngineConfig.getExternalIdDomains())

                               // LEFT OUTER JOIN, COALESCE nulls to ""
                               + ",COALESCE(" + TBL_BOOK_LOANEE.dot(DBKey.LOANEE_NAME) + ", '')"
                               + _AS_ + DBKey.LOANEE_NAME

                               // LEFT OUTER JOIN, columns default to NULL
                               + ','
                               + TBL_CALIBRE_BOOKS
                                       .dotAs(DBKey.CALIBRE_BOOK_ID,
                                              DBKey.CALIBRE_BOOK_UUID,
                                              DBKey.CALIBRE_BOOK_MAIN_FORMAT,
                                              DBKey.FK_CALIBRE_LIBRARY)

                               // LEFT OUTER JOIN, columns default to NULL
                               + ','
                               + TBL_STRIPINFO_COLLECTION
                                       .dotAs(DBKey.STRIP_INFO_COLL_ID,
                                              DBKey.STRIP_INFO_OWNED,
                                              DBKey.STRIP_INFO_DIGITAL,
                                              DBKey.STRIP_INFO_WANTED,
                                              DBKey.STRIP_INFO_AMOUNT,
                                              DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC)

                               + _FROM_ + TBL_BOOKS.ref()
                               + TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE)
                               + TBL_BOOKS.leftOuterJoin(TBL_CALIBRE_BOOKS)
                               + TBL_BOOKS.leftOuterJoin(TBL_STRIPINFO_COLLECTION);
        }
    }
}
