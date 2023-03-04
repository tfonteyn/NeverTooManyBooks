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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntryMergeHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Note the insert + update method reside in {@link BookDaoImpl} as they are only
 * ever needed there + as they are always called in a loop, benefit from the caching of statements.
 */
public class TocEntryDaoImpl
        extends BaseDaoImpl
        implements TocEntryDao {

    /** Log tag. */
    private static final String TAG = "TocEntryDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";
    @NonNull
    private final Supplier<BookDao> bookDaoSupplier;
    @NonNull
    private final Supplier<AuthorDao> authorDaoSupplier;
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                    Underlying database
     * @param reorderHelperSupplier deferred supplier for the {@link ReorderHelper}.
     */
    public TocEntryDaoImpl(@NonNull final SynchronizedDb db,
                           @NonNull final Supplier<BookDao> bookDaoSupplier,
                           @NonNull final Supplier<AuthorDao> authorDaoSupplier,
                           @NonNull final Supplier<ReorderHelper> reorderHelperSupplier) {
        super(db, TAG);
        this.bookDaoSupplier = bookDaoSupplier;
        this.authorDaoSupplier = authorDaoSupplier;
        this.reorderHelperSupplier = reorderHelperSupplier;
    }

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<TocEntry> list,
                             @NonNull final Function<TocEntry, Locale> localeSupplier) {
        if (list.isEmpty()) {
            return false;
        }

        final TocEntryMergeHelper mergeHelper = new TocEntryMergeHelper();
        return mergeHelper.merge(context, list, localeSupplier,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(context, current, () -> locale));
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final TocEntry tocEntry,
                      @NonNull final Supplier<Locale> localeSupplier) {

        final Author primaryAuthor = tocEntry.getPrimaryAuthor();
        authorDaoSupplier.get().fixId(context, primaryAuthor, localeSupplier);

        final long id = find(context, tocEntry, localeSupplier);
        tocEntry.setId(id);
    }

    @Override
    public long find(@NonNull final Context context,
                     @NonNull final TocEntry tocEntry,
                     @NonNull final Supplier<Locale> localeSupplier) {

        final OrderByData obd = OrderByData.create(context, reorderHelperSupplier.get(),
                                                   tocEntry.getTitle(), localeSupplier.get());

        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_ID)) {
            stmt.bindLong(1, tocEntry.getPrimaryAuthor().getId());
            stmt.bindString(2, SqlEncode.orderByColumn(tocEntry.getTitle(), obd.locale));
            stmt.bindString(3, SqlEncode.orderByColumn(obd.title, obd.locale));
            return stmt.simpleQueryForLongOrZero();
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
    public ArrayList<TocEntry> getByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.TOC_ENTRIES_BY_BOOK_ID,
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
    public ArrayList<Long> getBookIds(final long tocId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BOOK_IDS_BY_TOC_ENTRY_ID,
                                         new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final TocEntry tocEntry) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, tocEntry.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            tocEntry.setId(0);
            fixPositions(context);
        }
        return rowsAffected == 1;
    }

    @Override
    public int fixPositions(@NonNull final Context context) {

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = context.getResources().getConfiguration().getLocales().get(0);
            final BookDao bookDao = bookDaoSupplier.get();

            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<TocEntry> list = getByBookId(bookId);
                    bookDao.insertOrUpdateToc(context, bookId, list, false, bookLocale);
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

        /** All Book id's for a given {@link TocEntry}. */
        private static final String SELECT_BOOK_IDS_BY_TOC_ENTRY_ID =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
                + _WHERE_ + DBKey.FK_TOC_ENTRY + "=?";

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
        private static final String TOC_ENTRIES_BY_BOOK_ID =
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
         * Get the id of a {@link TocEntry} by Title.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Search TITLE_OB on both "The Title" and "Title, The"
         */
        private static final String FIND_ID =
                SELECT_ + DBKey.PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
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
