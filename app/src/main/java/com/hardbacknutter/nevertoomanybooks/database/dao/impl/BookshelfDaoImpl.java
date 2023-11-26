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
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.TopRowListPosition;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.BookshelfMergeHelper;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF_FILTERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

public class BookshelfDaoImpl
        extends BaseDaoImpl
        implements BookshelfDao {

    /**
     * Preference name - the bookshelf to load next time we startup.
     * Storing the name and not the id. If you export/import... the id will be different.
     */
    private static final String PK_BOOKSHELF_CURRENT = "Bookshelf.CurrentBookshelf";
    /** Log tag. */
    private static final String TAG = "BookshelfDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";
    @NonNull
    private final Supplier<StylesHelper> stylesHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                   Underlying database
     * @param stylesHelperSupplier deferred supplier for the {@link StylesHelper}
     */
    public BookshelfDaoImpl(@NonNull final SynchronizedDb db,
                            @NonNull final Supplier<StylesHelper> stylesHelperSupplier) {
        super(db, TAG);
        this.stylesHelperSupplier = stylesHelperSupplier;
    }

    /**
     * Run at installation time to add the 'all' and default shelves to the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public static void onPostCreate(@NonNull final Context context,
                                    @NonNull final SQLiteDatabase db) {
        // inserts a 'All Books' bookshelf with _id==-1, see {@link Bookshelf}.
        db.execSQL(INSERT_INTO_ + TBL_BOOKSHELF
                   + '(' + DBKey.PK_ID
                   + ',' + DBKey.BOOKSHELF_NAME
                   + ',' + DBKey.FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + BuiltinStyle.HARD_DEFAULT_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL(INSERT_INTO_ + TBL_BOOKSHELF
                   + '(' + DBKey.PK_ID
                   + ',' + DBKey.BOOKSHELF_NAME
                   + ',' + DBKey.FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.HARD_DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + BuiltinStyle.HARD_DEFAULT_ID
                   + ')');
    }

    @NonNull
    private Optional<Bookshelf> getAllBooksBookshelf(@NonNull final Context context) {
        final Bookshelf bookshelf = new Bookshelf(
                context.getString(R.string.bookshelf_all_books),
                stylesHelperSupplier.get().getDefault());
        bookshelf.setId(Bookshelf.ALL_BOOKS);
        return Optional.of(bookshelf);
    }

    @NonNull
    private Optional<Bookshelf> getDefaultBookshelf(@NonNull final Context context) {
        final Bookshelf bookshelf = new Bookshelf(
                context.getString(R.string.bookshelf_my_books),
                stylesHelperSupplier.get().getDefault());
        bookshelf.setId(Bookshelf.HARD_DEFAULT);
        return Optional.of(bookshelf);
    }

    @NonNull
    private Optional<Bookshelf> getPreferredBookshelf(@NonNull final Context context) {
        final String name = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PK_BOOKSHELF_CURRENT, null);
        if (name != null && !name.isEmpty()) {
            return findByName(name);
        }
        return Optional.empty();
    }

    @Override
    public void setAsPreferred(@NonNull final Context context,
                               @NonNull final Bookshelf bookshelf) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(PK_BOOKSHELF_CURRENT, bookshelf.getName())
                         .apply();
    }

    @NonNull
    public Optional<Bookshelf> getBookshelf(@NonNull final Context context,
                                            final long id) {
        if (id == 0) {
            return Optional.empty();
        } else if (id == Bookshelf.ALL_BOOKS) {
            return getAllBooksBookshelf(context);
        } else if (id == Bookshelf.HARD_DEFAULT) {
            return getDefaultBookshelf(context);
        } else if (id == Bookshelf.USER_DEFAULT) {
            return getPreferredBookshelf(context);
        } else {
            return findById(id);
        }
    }

    @NonNull
    public Optional<Bookshelf> getBookshelf(@NonNull final Context context,
                                            @NonNull final long... ids) {
        for (final long id : ids) {
            final Optional<Bookshelf> bookshelf = getBookshelf(context, id);
            if (bookshelf.isPresent()) {
                return bookshelf;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Bookshelf> findById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Bookshelf(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Optional<Bookshelf> findByName(@NonNull final Context context,
                                          @NonNull final Bookshelf bookshelf,
                                          @NonNull final Locale locale) {
        return findByName(bookshelf.getName());
    }

    @Override
    @NonNull
    public Optional<Bookshelf> findByName(@NonNull final String name) {

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public List<Bookshelf> getAll() {
        final List<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_ALL_ORDERED_BY_NAME, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @NonNull
    @Override
    public Cursor fetchAll() {
        return db.rawQuery(Sql.SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @NonNull
    @Override
    public List<PFilter<?>> getFilters(final long bookshelfId) {
        final List<PFilter<?>> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_FILTERS_BY_BOOKSHELF_ID,
                                         new String[]{String.valueOf(bookshelfId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final String dbKey = rowData.getString(DBKey.FILTER_DBKEY);
                final String value = rowData.getString(DBKey.FILTER_VALUE, null);
                // setPersistedValue accepts null values, but is there any point using null?
                if (value != null) {
                    final PFilter<?> filter = FilterFactory.createFilter(dbKey);
                    if (filter != null) {
                        filter.setPersistedValue(value);
                        list.add(filter);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Store the <strong>active filter</strong>.
     *
     * @param context     Current context
     * @param bookshelfId the Bookshelf id; passed separately to allow clean inserts
     * @param bookshelf   to store
     */
    private void storeFilters(@NonNull final Context context,
                              final long bookshelfId,
                              @NonNull final Bookshelf bookshelf) {

        // prune the filters so we only keep the active ones
        final List<PFilter<?>> list = bookshelf.pruneFilters(context);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try (SynchronizedStatement stmt = db.compileStatement(
                    Sql.DELETE_FILTERS_BY_BOOKSHELF_ID)) {
                stmt.bindLong(1, bookshelfId);
                stmt.executeUpdateDelete();
            }

            if (list.isEmpty()) {
                return;
            }

            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_FILTER)) {
                list.forEach(filter -> {
                    stmt.bindLong(1, bookshelfId);
                    stmt.bindString(2, filter.getDBKey());
                    stmt.bindString(3, filter.getPersistedValue());
                    stmt.executeInsert();
                });
            }

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
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Bookshelf> list) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final BookshelfMergeHelper mergeHelper = new BookshelfMergeHelper();
        return mergeHelper.merge(context, list, current -> userLocale,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(context, current, locale));
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Bookshelf bookshelf,
                      @NonNull final Locale locale) {
        final long found = findByName(context, bookshelf, locale)
                .map(Bookshelf::getId).orElse(0L);
        bookshelf.setId(found);
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Bookshelf bookshelf,
                        @NonNull final Locale locale) {
        // If needed, check if we already have it in the database.
        if (bookshelf.getId() == 0) {
            fixId(context, bookshelf, locale);
        }

        // If we do already have it, update the object
        if (bookshelf.getId() > 0) {
            final Optional<Bookshelf> dbBookshelf = findById(bookshelf.getId());
            // Sanity check
            if (dbBookshelf.isPresent()) {
                // copy any updated fields
                bookshelf.copyFrom(dbBookshelf.get());
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                bookshelf.setId(0);
            }
        }
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               @NonNull final Collection<Bookshelf> list)
            throws DaoInsertException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates; shelves don't use a Locale, hence no lookup done.
        pruneList(context, list);

        // Just delete all current links; we'll insert them from scratch.
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Bookshelf bookshelf : list) {
                // create if needed - do NOT do updates here
                if (bookshelf.getId() == 0) {
                    insert(context, bookshelf, locale);
                }
                //2023-06-11: If we ever do updates here, then we need to check the triggers!
                // also: look at AuthorDaoImpl/PublisherDaoImpl how we avoid unneeded updates

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, bookshelf.getId());
                if (stmt.executeInsert() == -1) {
                    throw new DaoInsertException("insert Book-Bookshelf");
                }
            }
        }
    }

    @Override
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf,
                       @NonNull final Locale bookLocale)
            throws DaoInsertException {

        // validate the style first
        final long styleId = bookshelf.getStyle().getId();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final TopRowListPosition topRowListPosition = bookshelf.getTopRowPosition();
            final long iId;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, topRowListPosition.getAdapterPosition());
                stmt.bindLong(4, topRowListPosition.getViewOffset());
                iId = stmt.executeInsert();
            }

            if (iId > 0) {
                bookshelf.setId(iId);
                storeFilters(context, iId, bookshelf);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return iId;
            }

            // Reset the id before throwing!
            bookshelf.setId(0);
            throw new DaoInsertException(ERROR_INSERT_FROM + bookshelf);
        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            LoggerFactory.getLogger().e(TAG, e);
            // Reset the id before throwing!
            bookshelf.setId(0);
            throw new DaoInsertException(ERROR_INSERT_FROM + bookshelf, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf,
                       @NonNull final Locale bookLocale)
            throws DaoUpdateException {

        // validate the style first
        final long styleId = bookshelf.getStyle().getId();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final TopRowListPosition topRowListPosition = bookshelf.getTopRowPosition();
            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, topRowListPosition.getAdapterPosition());
                stmt.bindLong(4, topRowListPosition.getViewOffset());
                stmt.bindLong(5, bookshelf.getId());

                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                storeFilters(context, bookshelf.getId(), bookshelf);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }

            throw new DaoUpdateException(ERROR_UPDATE_FROM + bookshelf);
        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            LoggerFactory.getLogger().e(TAG, e);
            throw new DaoUpdateException(ERROR_UPDATE_FROM + bookshelf, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final Bookshelf bookshelf) {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            purgeNodeStates(bookshelf);

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
                stmt.bindLong(1, bookshelf.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                bookshelf.setId(0);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return true;
            }
            return false;
        } catch (@NonNull final DaoUpdateException | SQLException | IllegalArgumentException e) {
            return false;

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void moveBooks(@NonNull final Context context,
                          @NonNull final Bookshelf source,
                          @NonNull final Bookshelf target) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Bookshelf.
            // We don't hold 'position' for bookshelves... just do a mass update
            final ContentValues cv = new ContentValues();
            cv.put(DBKey.FK_BOOKSHELF, target.getId());
            db.update(TBL_BOOK_BOOKSHELF.getName(), cv, DBKey.FK_BOOKSHELF + "=?",
                      new String[]{String.valueOf(source.getId())});

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
    public void purgeNodeStates(@NonNull final Bookshelf bookshelf)
            throws DaoUpdateException {
        try (SynchronizedStatement stmt = db
                .compileStatement(Sql.FIND_BOOK_LIST_NODE_STATE_BY_BOOKSHELF_ID)) {
            stmt.bindLong(1, bookshelf.getId());
            stmt.executeUpdateDelete();

        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            LoggerFactory.getLogger().e(TAG, e);
            throw new DaoUpdateException(ERROR_UPDATE_FROM + bookshelf, e);
        }
    }

    @NonNull
    @Override
    public List<Long> getBookIds(final long bookshelfId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BOOK_IDS_BY_BOOKSHELF_ID,
                                         new String[]{String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Bookshelf> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
            return list;
        }
    }

    private static final class Sql {
        /** Insert a {@link Bookshelf}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_BOOKSHELF.getName()
                + '(' + DBKey.BOOKSHELF_NAME
                + ',' + DBKey.FK_STYLE
                + ',' + DBKey.BOOKSHELF_BL_TOP_POS
                + ',' + DBKey.BOOKSHELF_BL_TOP_OFFSET
                + ") VALUES (?,?,?,?)";

        /** Update a {@link Bookshelf}. */
        static final String UPDATE =
                UPDATE_ + TBL_BOOKSHELF.getName()
                + _SET_ + DBKey.BOOKSHELF_NAME + "=?"
                + ',' + DBKey.FK_STYLE + "=?"
                + ',' + DBKey.BOOKSHELF_BL_TOP_POS + "=?"
                + ',' + DBKey.BOOKSHELF_BL_TOP_OFFSET + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Bookshelf}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_BOOKSHELF.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Insert the link between a {@link Book} and a {@link Bookshelf}. */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_BOOKSHELF.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_BOOKSHELF
                + ") VALUES (?,?)";

        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_BOOKSHELF.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** Get a count of the {@link Bookshelf}s. */
        static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_BOOKSHELF.getName();

        /** A list of all {@link Bookshelf}s, unordered. Joined with the styles table. */
        static final String SELECT_ALL =
                SELECT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                              DBKey.BOOKSHELF_NAME,
                                              DBKey.BOOKSHELF_BL_TOP_POS,
                                              DBKey.BOOKSHELF_BL_TOP_OFFSET,
                                              DBKey.FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.STYLE_UUID)
                + _FROM_ + TBL_BOOKSHELF.startJoin(TBL_BOOKLIST_STYLES);

        /** Get a list of all {@link Bookshelf} ordered by name. */
        static final String SELECT_ALL_ORDERED_BY_NAME =
                SELECT_ALL + _ORDER_BY_ + DBKey.BOOKSHELF_NAME + _COLLATION;

        /** Find a {@link Bookshelf} by its id. Joined with the styles table. */
        static final String FIND_BY_ID =
                SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + "=?";

        /**
         * Find a {@link Bookshelf} by name; Joined with the styles table.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + "=?" + _COLLATION;

        /**
         * All {@link Bookshelf}s for a {@link Book}.
         * Ordered by name.
         */
        static final String FIND_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                                       DBKey.BOOKSHELF_NAME,
                                                       DBKey.BOOKSHELF_BL_TOP_POS,
                                                       DBKey.BOOKSHELF_BL_TOP_OFFSET,
                                                       DBKey.FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.STYLE_UUID)

                + _FROM_ + TBL_BOOK_BOOKSHELF.startJoin(TBL_BOOKSHELF, TBL_BOOKLIST_STYLES)
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + _COLLATION;


        /** All {@link Book}s (id only!) for a given {@link Bookshelf}. */
        static final String FIND_BOOK_IDS_BY_BOOKSHELF_ID =
                SELECT_ + TBL_BOOK_BOOKSHELF.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_BOOKSHELF.ref()
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?";

        static final String FIND_BOOK_LIST_NODE_STATE_BY_BOOKSHELF_ID =
                DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

        static final String INSERT_FILTER =
                INSERT_INTO_ + TBL_BOOKSHELF_FILTERS.getName()
                + '(' + DBKey.FK_BOOKSHELF
                + ',' + DBKey.FILTER_DBKEY
                + ',' + DBKey.FILTER_VALUE
                + ") VALUES (?,?,?)";

        static final String DELETE_FILTERS_BY_BOOKSHELF_ID =
                DELETE_FROM_ + TBL_BOOKSHELF_FILTERS.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

        static final String FIND_FILTERS_BY_BOOKSHELF_ID =
                SELECT_ + DBKey.FILTER_DBKEY + ',' + DBKey.FILTER_VALUE
                + _FROM_ + TBL_BOOKSHELF_FILTERS.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";
    }
}
