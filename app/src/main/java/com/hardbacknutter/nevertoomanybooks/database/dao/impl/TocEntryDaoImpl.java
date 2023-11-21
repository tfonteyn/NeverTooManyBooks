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

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntryMergeHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class TocEntryDaoImpl
        extends BaseDaoImpl
        implements TocEntryDao {

    /** Log tag. */
    private static final String TAG = "TocEntryDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";
    @NonNull
    private final Supplier<AuthorDao> authorDaoSupplier;
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                    Underlying database
     * @param reorderHelperSupplier deferred supplier for the {@link ReorderHelper}
     * @param authorDaoSupplier     deferred supplier for the {@link AuthorDao}
     */
    public TocEntryDaoImpl(@NonNull final SynchronizedDb db,
                           @NonNull final Supplier<ReorderHelper> reorderHelperSupplier,
                           @NonNull final Supplier<AuthorDao> authorDaoSupplier) {
        super(db, TAG);
        this.authorDaoSupplier = authorDaoSupplier;
        this.reorderHelperSupplier = reorderHelperSupplier;
    }

    @NonNull
    @Override
    public Optional<TocEntry> getById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new TocEntry(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<TocEntry> list,
                             @NonNull final Function<TocEntry, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        final TocEntryMergeHelper mergeHelper = new TocEntryMergeHelper();
        return mergeHelper.merge(context, list, localeSupplier,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(context, current, locale));
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final TocEntry tocEntry,
                      @NonNull final Locale locale) {

        final Author primaryAuthor = tocEntry.getPrimaryAuthor();
        authorDaoSupplier.get().fixId(context, primaryAuthor, locale);

        final long found = findByName(context, tocEntry, locale)
                .map(TocEntry::getId).orElse(0L);
        tocEntry.setId(found);
    }

    @Override
    @NonNull
    public Optional<TocEntry> findByName(@NonNull final Context context,
                                         @NonNull final TocEntry tocEntry,
                                         @NonNull final Locale locale) {

        final ReorderHelper reorderHelper = reorderHelperSupplier.get();
        final String text = tocEntry.getTitle();
        final String obTitle = reorderHelper.reorderForSorting(context, text, locale);

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                String.valueOf(tocEntry.getPrimaryAuthor().getId()),
                SqlEncode.orderByColumn(tocEntry.getTitle(), locale),
                SqlEncode.orderByColumn(obTitle, locale)})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new TocEntry(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Cursor fetchAll() {
        return db.rawQuery(Sql.SELECT_ALL, null);
    }

    @Override
    @NonNull
    public List<BookLight> getBookTitles(@IntRange(from = 1) final long id,
                                         @NonNull final Author author) {
        final List<BookLight> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BOOK_TITLES_BY_TOC_ENTRY_ID,
                                         new String[]{String.valueOf(id)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new BookLight(rowData.getLong(DBKey.PK_ID), author, rowData));
            }
        }

        return list;
    }

    @Override
    @NonNull
    public List<TocEntry> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_TOC_ENTRIES_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new TocEntry(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Long> getBookIds(final long tocId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BOOK_IDS_BY_TOC_ENTRY_ID,
                                         new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               @NonNull final Collection<TocEntry> list,
                               final boolean lookupLocale,
                               @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final Function<TocEntry, Locale> listLocaleSupplier = item -> {
            if (lookupLocale) {
                return item.getLocale(context).orElse(bookLocale);
            } else {
                return bookLocale;
            }
        };

        pruneList(context, list, listLocaleSupplier);

        // Just delete all current links; we'll re-insert them for easier positioning
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final AuthorDao authorDao = authorDaoSupplier.get();

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK);
             SynchronizedStatement stmtInsToc = db.compileStatement(Sql.INSERT);
             SynchronizedStatement stmtUpdToc = db.compileStatement(Sql.UPDATE)) {

            long position = 0;
            for (final TocEntry tocEntry : list) {
                // Author must be handled separately;
                final Author author = tocEntry.getPrimaryAuthor();
                final Locale authorLocale;
                if (lookupLocale) {
                    authorLocale = author.getLocale(context).orElse(bookLocale);
                } else {
                    authorLocale = bookLocale;
                }
                authorDao.fixId(context, author, authorLocale);
                // Create if needed - NEVER do updates here
                if (author.getId() == 0) {
                    authorDao.insert(context, author, bookLocale);
                }

                final Locale locale = listLocaleSupplier.apply(tocEntry);
                final ReorderHelper reorderHelper = reorderHelperSupplier.get();
                final String text = tocEntry.getTitle();
                final String obTitle = reorderHelper.reorderForSorting(context, text, locale);

                if (tocEntry.getId() == 0) {
                    stmtInsToc.bindLong(1, author.getId());
                    stmtInsToc.bindString(2, tocEntry.getTitle());
                    stmtInsToc.bindString(3, SqlEncode.orderByColumn(obTitle, locale));
                    stmtInsToc.bindString(4, tocEntry
                            .getFirstPublicationDate().getIsoString());

                    final long iId = stmtInsToc.executeInsert();
                    if (iId > 0) {
                        tocEntry.setId(iId);
                    } else {
                        throw new DaoInsertException(ERROR_INSERT_FROM + tocEntry);
                    }

                } else {
                    // We cannot update the author as it's part of the primary key.
                    // (we should never even get here if the author was changed)
                    stmtUpdToc.bindString(1, tocEntry.getTitle());
                    stmtUpdToc.bindString(2, SqlEncode.orderByColumn(obTitle, locale));
                    stmtUpdToc.bindString(3, tocEntry
                            .getFirstPublicationDate().getIsoString());
                    stmtUpdToc.bindLong(4, tocEntry.getId());
                    if (stmtUpdToc.executeUpdateDelete() != 1) {
                        throw new DaoUpdateException(ERROR_UPDATE_FROM + tocEntry);
                    }
                }

                // create the book<->TocEntry link.
                //
                // As we delete all links before insert/updating above, we normally
                // *always* need to re-create the link here.
                // However, this will fail if we inserted "The Universe" and updated "Universe, The"
                // as the former will be stored as "Universe, The" so conflicting with the latter.
                // We tried to mitigate this conflict before it could trigger an issue here, but it
                // complicated the code and frankly ended in a chain of special condition
                // code branches during processing of internet search data.
                // So... let's just catch the SQL constraint exception and ignore it.
                // (do not use the sql 'REPLACE' command! We want to keep the original position)
                try {
                    position++;
                    stmt.bindLong(1, tocEntry.getId());
                    stmt.bindLong(2, bookId);
                    stmt.bindLong(3, position);
                    if (stmt.executeInsert() == -1) {
                        throw new DaoInsertException("insert Book-TocEntry");
                    }
                } catch (@NonNull final SQLiteConstraintException ignore) {
                    // ignore and reset the position counter.
                    position--;

                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger().d(TAG, "insertOrUpdate",
                                                    "SQLiteConstraintException",
                                                    "tocEntry=" + tocEntry.getId(),
                                                    "bookId=" + bookId);
                    }
                }
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final TocEntry tocEntry) {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
                stmt.bindLong(1, tocEntry.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                tocEntry.setId(0);
                fixPositions(context);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return true;
            }
            return false;
        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            return false;

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public int fixPositions(@NonNull final Context context) {

        final List<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            Synchronizer.SyncLock txLock = null;
            //noinspection CheckStyle
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final Book book = Book.from(bookId);
                    final List<TocEntry> list = getByBookId(bookId);
                    // We KNOW there are no updates needed.
                    insertOrUpdate(context, bookId, list, false,
                                   book.getLocaleOrUserLocale(context));
                }
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                LoggerFactory.getLogger().e(TAG, e);

            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
            }
        }
        return bookIds.size();
    }

    @Override
    public void purge() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    static final class Sql {

        /** Get a {@link TocEntry} by the TocEntry id. */
        static final String SELECT_BY_ID =
                SELECT_ + '*' + _WHERE_ + TBL_TOC_ENTRIES.dot(DBKey.PK_ID) + "=?";

        static final String INSERT =
                INSERT_INTO_ + TBL_TOC_ENTRIES.getName()
                + '(' + DBKey.FK_AUTHOR
                + ',' + DBKey.TITLE
                + ',' + DBKey.TITLE_OB
                + ',' + DBKey.FIRST_PUBLICATION__DATE
                + ") VALUES (?,?,?,?)";

        /** Update a single {@link TocEntry}. */
        static final String UPDATE =
                UPDATE_ + TBL_TOC_ENTRIES.getName()
                + _SET_ + DBKey.TITLE + "=?"
                + ',' + DBKey.TITLE_OB + "=?"
                + ',' + DBKey.FIRST_PUBLICATION__DATE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Insert the link between a {@link Book} and a {@link TocEntry}.
         */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_TOC_ENTRIES.getName()
                + '(' + DBKey.FK_TOC_ENTRY
                + ',' + DBKey.FK_BOOK
                + ',' + DBKey.BOOK_TOC_ENTRY_POSITION
                + ") VALUES (?,?,?)";

        /**
         * Delete the link between a {@link Book} and a {@link TocEntry}.
         * <p>
         * This is done when a TOC is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_TOC_ENTRIES.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** All Book id's for a given {@link TocEntry}. */
        private static final String SELECT_BOOK_IDS_BY_TOC_ENTRY_ID =
                SELECT_ + TBL_BOOK_TOC_ENTRIES.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.ref()
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(DBKey.FK_TOC_ENTRY) + "=?";

        /** All Books as {@link BookLight} for a given {@link TocEntry}. */
        private static final String SELECT_BOOK_TITLES_BY_TOC_ENTRY_ID =
                SELECT_ + TBL_BOOKS.dotAs(DBKey.PK_ID,
                                          DBKey.TITLE,
                                          DBKey.LANGUAGE,
                                          DBKey.FIRST_PUBLICATION__DATE)
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.startJoin(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(DBKey.FK_TOC_ENTRY) + "=?"
                + _ORDER_BY_ + TBL_BOOKS.dot(DBKey.TITLE_OB);

        /** {@link TocEntry}, all columns. */
        private static final String SELECT_ALL = "SELECT * FROM " + TBL_TOC_ENTRIES.getName();

        /** All {@link TocEntry}'s for a Book; ordered by position in the book. */
        private static final String SELECT_TOC_ENTRIES_BY_BOOK_ID =
                SELECT_ + TBL_TOC_ENTRIES.dotAs(DBKey.PK_ID,
                                                DBKey.FK_AUTHOR,
                                                DBKey.TITLE,
                                                DBKey.FIRST_PUBLICATION__DATE)
                // for convenience, we fetch the Author here
                + ',' + TBL_AUTHORS.dotAs(DBKey.AUTHOR_FAMILY_NAME,
                                          DBKey.AUTHOR_GIVEN_NAMES,
                                          DBKey.AUTHOR_IS_COMPLETE)

                // count the number of books this TOC entry is present in.
                + ", ("
                + SELECT_COUNT_FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
                // use the full table name on the left as we need a full table scan
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.getName() + '.' + DBKey.FK_TOC_ENTRY
                // but filtered on the results from the main query (i.e. alias on the right).
                + "=" + TBL_TOC_ENTRIES.dot(DBKey.PK_ID)
                + ')' + _AS_ + DBKey.BOOK_COUNT

                + _FROM_
                + TBL_TOC_ENTRIES.startJoin(TBL_BOOK_TOC_ENTRIES)
                + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(DBKey.BOOK_TOC_ENTRY_POSITION);

        /**
         * Find a {@link TocEntry} by name for a specific Author
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Searches TITLE_OB on both original and (potentially) reordered title.
         */
        private static final String FIND_BY_NAME =
                SELECT_ + '*' + _FROM_ + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + DBKey.FK_AUTHOR + "=?"
                + _AND_ + '(' + DBKey.TITLE_OB + "=? " + _COLLATION
                + _OR_ + DBKey.TITLE_OB + "=?" + _COLLATION + ')';

        /** Delete a {@link TocEntry}. */
        private static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_TOC_ENTRIES.getName() + _WHERE_ + DBKey.PK_ID + "=?";

        /** Purge a {@link TocEntry} if no longer in use. */
        private static final String PURGE =
                DELETE_FROM_ + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_TOC_ENTRY
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName() + ')';

        private static final String REPOSITION =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_
                + '(' + SELECT_ + DBKey.FK_BOOK
                + ",MIN(" + DBKey.BOOK_TOC_ENTRY_POSITION + ')' + _AS_ + "mp"
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
                + _GROUP_BY_ + DBKey.FK_BOOK
                + ')'
                + _WHERE_ + "mp>1";

        private Sql() {
        }
    }
}
