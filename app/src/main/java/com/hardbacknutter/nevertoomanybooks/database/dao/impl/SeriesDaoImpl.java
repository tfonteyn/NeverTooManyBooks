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
import android.database.sqlite.SQLiteException;

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
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.database.UncheckedDaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.SeriesMergeHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

public class SeriesDaoImpl
        extends BaseDaoImpl
        implements SeriesDao {

    /** Log tag. */
    private static final String TAG = "SeriesDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                    Underlying database
     * @param reorderHelperSupplier deferred supplier for the {@link ReorderHelper}
     */
    public SeriesDaoImpl(@NonNull final SynchronizedDb db,
                         @NonNull final Supplier<ReorderHelper> reorderHelperSupplier) {
        super(db, TAG);
        this.reorderHelperSupplier = reorderHelperSupplier;
    }

    @NonNull
    @Override
    public Optional<Series> getById(final long id) {
        try (Cursor cursor = db.rawQuery(Sql.GET_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Series(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Optional<Series> findByName(@NonNull final Context context,
                                       @NonNull final Series series,
                                       @NonNull final Supplier<Locale> localeSupplier) {

        final OrderByData obd = OrderByData.create(context, reorderHelperSupplier.get(),
                                                   series.getTitle(),
                                                   localeSupplier.get());

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                SqlEncode.orderByColumn(series.getTitle(), obd.getLocale()),
                SqlEncode.orderByColumn(obd.getTitle(), obd.getLocale())})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Series(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public List<String> getNames() {
        return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES);
    }

    @Override
    @NonNull
    public List<Series> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Series> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SERIES_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Series(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public String getLanguage(final long id) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.GET_LANGUAGE)) {
            stmt.bindLong(1, id);
            final String code = stmt.simpleQueryForStringOrNull();
            return code != null ? code : "";
        }
    }

    @Override
    @NonNull
    public List<Long> getBookIds(final long seriesId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BOOK_IDS_BY_SERIES_ID,
                                         new String[]{String.valueOf(seriesId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Long> getBookIds(final long seriesId,
                                 final long bookshelfId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                Sql.SELECT_BOOK_IDS_BY_SERIES_ID_AND_BOOKSHELF_ID,
                new String[]{String.valueOf(seriesId), String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Cursor fetchAll() {
        return db.rawQuery(Sql.SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public long countBooks(@NonNull final Context context,
                           @NonNull final Series series,
                           @NonNull final Locale bookLocale) {
        if (series.getId() == 0) {
            fixId(context, series, () -> series.getLocale(context).orElse(bookLocale));
            if (series.getId() == 0) {
                return 0;
            }
        }

        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_BOOKS)) {
            stmt.bindLong(1, series.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public boolean setComplete(@NonNull final Series series,
                               final boolean complete) {
        final ContentValues cv = new ContentValues();
        cv.put(DBKey.SERIES_IS_COMPLETE, complete);

        final boolean success =
                0 < db.update(TBL_SERIES.getName(), cv, DBKey.PK_ID + "=?",
                              new String[]{String.valueOf(series.getId())});
        if (success) {
            series.setComplete(complete);
        }
        return success;
    }

    /**
     * Remove duplicates.
     * Consolidates series/- and series/number.
     * <p>
     * Remove Series from the list where the titles are the same, but one entry has a
     * {@code null} or empty number.
     * e.g. the following list should be processed as indicated:
     * <ul>
     * <li>foo(5)</li>
     * <li>foo <-- delete</li>
     * <li>bar <-- delete</li>
     * <li>bar <-- delete</li>
     * <li>bar(1)</li>
     * <li>foo(5) <-- delete</li>
     * <li>foo(6)</li>
     * </ul>
     * Note we keep BOTH foo(5) + foo(6)
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Series> list,
                             @NonNull final Function<Series, Locale> localeSupplier) {
        if (list.size() < 2) {
            return false;
        }

        final SeriesMergeHelper mergeHelper = new SeriesMergeHelper();
        return mergeHelper.merge(context, list, localeSupplier,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(context, current, () -> locale));
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Series series,
                      @NonNull final Supplier<Locale> localeSupplier) {
        final long found = findByName(context, series, localeSupplier)
                .map(Series::getId).orElse(0L);
        series.setId(found);
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Series series,
                        @NonNull final Supplier<Locale> localeSupplier) {

        // If needed, check if we already have it in the database.
        if (series.getId() == 0) {
            fixId(context, series, localeSupplier);
        }

        // If we do already have it, update the object
        if (series.getId() > 0) {
            final Optional<Series> dbSeries = getById(series.getId());
            // Sanity check
            if (dbSeries.isPresent()) {
                // copy any updated fields
                series.copyFrom(dbSeries.get(), false);
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                series.setId(0);
            }
        }
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               final boolean doUpdates,
                               @NonNull final Collection<Series> list,
                               final boolean lookupLocale,
                               @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }


        final Function<Series, Locale> localeSupplier = item -> {
            if (lookupLocale) {
                return item.getLocale(context).orElse(bookLocale);
            } else {
                return bookLocale;
            }
        };

        pruneList(context, list, localeSupplier);

        // Just delete all current links; we'll re-insert them for easier positioning
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        int position = 0;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Series series : list) {
                fixId(context, series, () -> localeSupplier.apply(series));

                // create if needed - do NOT do updates unless explicitly allowed
                if (series.getId() == 0) {
                    insert(context, series, bookLocale);
                } else if (doUpdates) {
                    // https://stackoverflow.com/questions/6677517/update-if-different-changed
                    // ONLY update if there are actual changes.
                    // Otherwise the trigger after_update_on" + TBL_SERIES
                    // thereby setting DATE_LAST_UPDATED__UTC for
                    // ALL books by that series
                    getById(series.getId()).ifPresent(current -> {
                        if (!current.equals(series)) {
                            try {
                                update(context, series, bookLocale);
                            } catch (@NonNull final DaoWriteException e) {
                                throw new UncheckedDaoWriteException(e);
                            }
                        }
                    });
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, series.getId());
                stmt.bindString(3, series.getNumber());
                stmt.bindLong(4, position);
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Series");
                }
            }
        }
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Series series,
                       @NonNull final Locale bookLocale)
            throws DaoWriteException {

        final Locale locale = series.getLocale(context).orElse(bookLocale);
        final OrderByData obd =
                OrderByData.create(context, reorderHelperSupplier.get(),
                                   series.getTitle(), locale);

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, SqlEncode.orderByColumn(obd.getTitle(), obd.getLocale()));
            stmt.bindBoolean(3, series.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                series.setId(iId);
                return iId;
            }

            throw new DaoWriteException(ERROR_INSERT_FROM + series);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_INSERT_FROM + series, e);
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Series series,
                       @NonNull final Locale bookLocale)
            throws DaoWriteException {

        final Locale locale = series.getLocale(context).orElse(bookLocale);
        final OrderByData obd =
                OrderByData.create(context, reorderHelperSupplier.get(),
                                   series.getTitle(), locale);

        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, SqlEncode.orderByColumn(obd.getTitle(), obd.getLocale()));
            stmt.bindBoolean(3, series.isComplete());
            stmt.bindLong(4, series.getId());

            final boolean success = 0 < stmt.executeUpdateDelete();
            if (success) {
                return;
            }

            throw new DaoWriteException(ERROR_UPDATE_FROM + series);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_UPDATE_FROM + series, e);
        }
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final Series series) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, series.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            series.setId(0);
            fixPositions(context);
        }
        return rowsAffected == 1;
    }

    @Override
    public void moveBooks(@NonNull final Context context,
                          @NonNull final Series source,
                          @NonNull final Series target)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Series,
            // respecting the position of the Series in the list for each book.
            for (final long bookId : getBookIds(source.getId())) {
                final Book book = Book.from(bookId);

                final Collection<Series> fromBook = book.getSeries();
                final Collection<Series> destList = new ArrayList<>();

                for (final Series item : fromBook) {
                    if (source.getId() == item.getId()) {
                        destList.add(target);
                        // We could 'break' here as there should be no duplicates,
                        // but paranoia...
                    } else {
                        // just keep/copy
                        destList.add(item);
                    }
                }

                // delete old links and store all new links
                // We KNOW there are no updates needed.
                insertOrUpdate(context, bookId, false, destList,
                               true, book.getLocaleOrUserLocale(context));
            }

            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void purge() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public int fixPositions(@NonNull final Context context) {

        final List<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final Book book = Book.from(bookId);
                    final List<Series> list = getByBookId(bookId);
                    // We KNOW there are no updates needed.
                    insertOrUpdate(context, bookId, false, list, false,
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

    private static final class Sql {

        /**
         * Delete the link between a {@link Book} and a {@link Series}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_SERIES.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";

        /**
         * Insert the link between a {@link Book} and a {@link Series}.
         */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_SERIES.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_SERIES
                + ',' + DBKey.SERIES_BOOK_NUMBER
                + ',' + DBKey.BOOK_SERIES_POSITION
                + ") VALUES(?,?,?,?)";

        /** All Books (id only) for a given Series. */
        private static final String SELECT_BOOK_IDS_BY_SERIES_ID =
                SELECT_ + TBL_BOOK_SERIES.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_SERIES.ref()
                + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_SERIES) + "=?";

        /** All Books (id only) for a given Series and Bookshelf. */
        private static final String SELECT_BOOK_IDS_BY_SERIES_ID_AND_BOOKSHELF_ID =
                SELECT_ + TBL_BOOKS.dotAs(DBKey.PK_ID)
                + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_BOOKS, TBL_BOOK_BOOKSHELF)
                + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_SERIES) + "=?"
                + _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?";

        /** name only. */
        private static final String SELECT_ALL_NAMES =
                SELECT_DISTINCT_ + DBKey.SERIES_TITLE
                + ',' + DBKey.SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName()
                + _ORDER_BY_ + DBKey.SERIES_TITLE_OB + _COLLATION;

        /** {@link Series}, all columns. */
        private static final String SELECT_ALL = "SELECT * FROM " + TBL_SERIES.getName();

        /** Get a {@link Series} by the Series id. */
        private static final String GET_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

        /** All Series for a Book; ordered by position, name. */
        private static final String SERIES_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_SERIES.dotAs(DBKey.PK_ID,
                                                    DBKey.SERIES_TITLE,
                                                    DBKey.SERIES_TITLE_OB,
                                                    DBKey.SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(DBKey.SERIES_BOOK_NUMBER,
                                              DBKey.BOOK_SERIES_POSITION)

                + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(DBKey.BOOK_SERIES_POSITION)
                + ',' + TBL_SERIES.dot(DBKey.SERIES_TITLE_OB) + _COLLATION;

        /**
         * Find a {@link Series} by Title.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Searches SERIES_TITLE_OB on both "The Title" and "Title, The"
         */
        private static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + DBKey.SERIES_TITLE_OB + "=?" + _COLLATION
                + _OR_ + DBKey.SERIES_TITLE_OB + "=?" + _COLLATION;

        /**
         * Get the language (ISO3) code for a Series.
         * This is defined as the language code for the first book in the Series.
         */
        private static final String GET_LANGUAGE =
                SELECT_ + TBL_BOOKS.dotAs(DBKey.LANGUAGE)
                + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_SERIES) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(DBKey.SERIES_BOOK_NUMBER)
                + " LIMIT 1";

        private static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_SERIES.getName();

        /** Count the number of {@link Book}'s in a {@link Series}. */
        private static final String COUNT_BOOKS =
                SELECT_ + "COUNT(" + DBKey.FK_BOOK + ')'
                + _FROM_ + TBL_BOOK_SERIES.getName()
                + _WHERE_ + DBKey.FK_SERIES + "=?";

        private static final String INSERT =
                INSERT_INTO_ + TBL_SERIES.getName()
                + '(' + DBKey.SERIES_TITLE
                + ',' + DBKey.SERIES_TITLE_OB
                + ',' + DBKey.SERIES_IS_COMPLETE
                + ") VALUES (?,?,?)";

        private static final String UPDATE =
                UPDATE_ + TBL_SERIES.getName()
                + _SET_ + DBKey.SERIES_TITLE + "=?"
                + ',' + DBKey.SERIES_TITLE_OB + "=?"
                + ',' + DBKey.SERIES_IS_COMPLETE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Series}. */
        private static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_SERIES.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Purge a {@link Series} if no longer in use. */
        private static final String PURGE =
                DELETE_FROM_ + TBL_SERIES.getName()
                + _WHERE_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_SERIES
                + _FROM_ + TBL_BOOK_SERIES.getName() + ')';

        private static final String REPOSITION =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_
                + '(' + SELECT_ + DBKey.FK_BOOK
                + ",MIN(" + DBKey.BOOK_SERIES_POSITION + ')' + _AS_ + "mp"
                + _FROM_ + TBL_BOOK_SERIES.getName()
                + _GROUP_BY_ + DBKey.FK_BOOK
                + ')'
                + _WHERE_ + "mp>1";
    }
}
