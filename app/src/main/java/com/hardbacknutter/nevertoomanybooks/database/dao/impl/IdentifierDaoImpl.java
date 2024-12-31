/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;

public class IdentifierDaoImpl
        extends BaseDaoImpl
        implements IdentifierDao {

    private static final String TAG = "IdentifierDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public IdentifierDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Run at <strong>installation</strong> time to add the predefined ID's to the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public static void onPostCreate(@NonNull final Context context,
                                    @NonNull final SQLiteDatabase db) {
        final List<Identifier> identifierList = List.of(
                new Identifier(Identifier.SID_ASIN, Identifier.TYPE_STRING,
                               context.getString(R.string.site_amazon), null),
                new Identifier(Identifier.SID_BEDETHEQUE, Identifier.TYPE_LONG,
                               context.getString(R.string.site_bedetheque), null),
                new Identifier(Identifier.SID_DNB, Identifier.TYPE_LONG,
                               context.getString(R.string.site_dnb_de), null),
                new Identifier(Identifier.SID_DOUBAN, Identifier.TYPE_LONG,
                               context.getString(R.string.site_douban), null),
                new Identifier(Identifier.SID_GOODREADS_BOOK, Identifier.TYPE_LONG,
                               context.getString(R.string.site_goodreads), null),
                new Identifier(Identifier.SID_GOOGLE, Identifier.TYPE_STRING,
                               context.getString(R.string.site_google_books), null),
                new Identifier(Identifier.SID_ISFDB, Identifier.TYPE_LONG,
                               context.getString(R.string.site_isfdb), null),
                new Identifier(Identifier.SID_KBNL, Identifier.TYPE_LONG,
                               context.getString(R.string.site_kb_nl), null),
                new Identifier(Identifier.SID_LAST_DODO_NL, Identifier.TYPE_LONG,
                               context.getString(R.string.site_lastdodo_nl), null),
                new Identifier(Identifier.SID_LCCN, Identifier.TYPE_STRING,
                               context.getString(R.string.site_lccn), null),
                new Identifier(Identifier.SID_LIBRARY_THING, Identifier.TYPE_LONG,
                               context.getString(R.string.site_library_thing), null),
                new Identifier(Identifier.SID_MOBI_ASIN, Identifier.TYPE_STRING,
                               context.getString(R.string.site_amazon), null),
                new Identifier(Identifier.SID_OCLC, Identifier.TYPE_STRING,
                               context.getString(R.string.site_worldcat), null),
                new Identifier(Identifier.SID_OPEN_LIBRARY, Identifier.TYPE_STRING,
                               context.getString(R.string.site_open_library), null),
                new Identifier(Identifier.SID_STRIP_INFO, Identifier.TYPE_LONG,
                               context.getString(R.string.site_stripinfo_be), null),
                new Identifier(Identifier.SID_STRIPWEB, Identifier.TYPE_LONG,
                               context.getString(R.string.site_stripweb_be), null),
                new Identifier(Identifier.SID_URI, Identifier.TYPE_STRING,
                               "URI/URL", null)
        );
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(
                db.compileStatement(Sql.INSERT))) {
            for (final Identifier identifier : identifierList) {
                doInsert(identifier, stmt);
            }
        } catch (@NonNull final SQLException e) {
            // log, but just rethrow insert errors... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        }
    }

    private static long doInsert(@NonNull final Identifier identifier,
                                 @NonNull final ExtSQLiteStatement stmt) {
        stmt.bindString(1, identifier.getName().toLowerCase(Locale.ENGLISH));
        stmt.bindString(2, String.valueOf(identifier.getType()));
        stmt.bindString(3, identifier.getDescription());
        stmt.bindString(4, identifier.getUrl());
        return stmt.executeInsert();
    }

    @NonNull
    @Override
    public Optional<Identifier> findById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Identifier(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public Optional<Identifier> findByName(@NonNull final String name) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Identifier(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public List<Identifier> getAll() {
        final List<Identifier> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_ALL_ORDERED_BY_NAME, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Identifier(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    public void insertOrUpdate(@NonNull final Book book)
            throws DaoInsertException, DaoUpdateException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final long bookId = book.getId();

        // Collect KNOWN identifiers
        final Map<Long, String> identifiers = new HashMap<>();
        getAll().forEach(identifier -> book.getIdentifierValue(identifier.getName()).ifPresent(
                sid -> identifiers.put(identifier.getId(), sid)));

        // Just delete all current links
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (identifiers.isEmpty()) {
            return;
        }

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Map.Entry<Long, String> entry : identifiers.entrySet()) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, entry.getKey());
                stmt.bindString(3, entry.getValue());
                if (stmt.executeInsert() == -1) {
                    throw new DaoInsertException("insert Book-Identifier");
                }
            }
        }
    }

    @Override
    public void fixId(@NonNull final Identifier identifier) {
        final long found = findByName(identifier.getName())
                .map(Identifier::getId).orElse(0L);
        identifier.setId(found);
    }

    @IntRange(from = 1)
    @Override
    public long insert(@NonNull final Identifier identifier)
            throws DaoInsertException {

        final long iId;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            iId = doInsert(identifier, stmt);
        }

        if (iId != -1) {
            identifier.setId(iId);
            return iId;
        }

        // The insert failed with -1
        throw new DaoInsertException(ERROR_INSERT_FROM + identifier);
    }

    @Override
    public void update(@NonNull final Identifier identifier)
            throws DaoUpdateException {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, identifier.getName().toLowerCase(Locale.ENGLISH));
            stmt.bindString(2, String.valueOf(identifier.getType()));
            stmt.bindString(3, identifier.getDescription());
            stmt.bindString(4, identifier.getUrl());

            stmt.bindLong(5, identifier.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            return;
        }

        throw new DaoUpdateException(ERROR_UPDATE_FROM + identifier);
    }

    @Override
    public boolean delete(@NonNull final Identifier identifier) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, identifier.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }
        if (rowsAffected > 0) {
            identifier.setId(0);
            return true;
        }
        return false;
    }

    @NonNull
    @Override
    public List<Identifier.Value> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Identifier.Value> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Identifier.Value(
                        new Identifier(rowData.getLong(DBKey.PK_ID), rowData),
                        rowData.getString(DBKey.IDENT_SID)));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Optional<String> findSid(@NonNull final String identifierName,
                                    final long bookId) {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_SID_BY_BOOK_ID_AND_NAME)) {
            stmt.bindLong(1, bookId);
            stmt.bindString(2, identifierName);

            final String sid = stmt.simpleQueryForStringOrNull();
            // null check sure.. the rest is paranoia
            if (sid != null && !sid.isEmpty() && !"0".equals(sid)) {
                return Optional.of(sid);
            }
        }
        return Optional.empty();
    }

    @IntRange(from = 0)
    @Override
    public long getBookId(@NonNull final Identifier identifier,
                          @NonNull final String sid) {
        try (SynchronizedStatement stmt = db.compileStatement(
                Sql.FIND_BOOK_BY_SID_AND_IDENTIFIER_ID)) {
            stmt.bindLong(1, identifier.getId());
            stmt.bindString(2, sid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @IntRange(from = 0)
    public long getBookId(@NonNull final String identifierName,
                          @NonNull final String sid) {
        try (SynchronizedStatement stmt = db.compileStatement(
                Sql.FIND_BOOK_BY_SID_AND_IDENTIFIER_NAME)) {
            stmt.bindString(1, identifierName);
            stmt.bindString(2, sid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    private static final class Sql {
        /** Insert an {@link Identifier}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_IDENTIFIERS.getName()
                + '(' + DBKey.IDENT_NAME
                + ',' + DBKey.IDENT_TYPE
                + ',' + DBKey.IDENT_DESC
                + ',' + DBKey.IDENT_URL
                + ") VALUES(?,?,?,?)";

        /** Update an {@link Identifier}. */
        static final String UPDATE =
                UPDATE_ + TBL_IDENTIFIERS.getName()
                + _SET_ + DBKey.IDENT_NAME + "=?"
                + ',' + DBKey.IDENT_TYPE + "=?"
                + ',' + DBKey.IDENT_DESC + "=?"
                + ',' + DBKey.IDENT_URL + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Identifier}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_IDENTIFIERS.getName() + _WHERE_ + DBKey.PK_ID + "=?";

        static final String SELECT_ALL =
                SELECT_ + TBL_IDENTIFIERS.dotAs(DBKey.PK_ID,
                                                DBKey.IDENT_NAME,
                                                DBKey.IDENT_TYPE,
                                                DBKey.IDENT_DESC,
                                                DBKey.IDENT_URL)
                + _FROM_ + TBL_IDENTIFIERS.ref();

        static final String SELECT_ALL_ORDERED_BY_NAME =
                SELECT_ALL + _ORDER_BY_ + DBKey.IDENT_NAME;

        static final String FIND_BY_ID =
                SELECT_ALL
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.PK_ID) + "=?";

        static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.IDENT_NAME) + "=?";

        static final String FIND_BOOK_BY_SID_AND_IDENTIFIER_ID =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_ + TBL_BOOK_IDENTIFIER.getName()
                + _WHERE_ + DBKey.FK_IDENTIFIER + "=?" + _AND_ + DBKey.IDENT_SID + "=?";

        static final String FIND_BOOK_BY_SID_AND_IDENTIFIER_NAME =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_ + TBL_BOOK_IDENTIFIER.startJoin(TBL_IDENTIFIERS)
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.IDENT_NAME) + "=?"
                + _AND_ + TBL_BOOK_IDENTIFIER.dot(DBKey.IDENT_SID) + "=?";

        static final String FIND_BY_BOOK_ID =
                SELECT_ + TBL_IDENTIFIERS.dotAs(DBKey.PK_ID,
                                                DBKey.IDENT_NAME,
                                                DBKey.IDENT_TYPE,
                                                DBKey.IDENT_DESC,
                                                DBKey.IDENT_URL)
                + ',' + TBL_BOOK_IDENTIFIER.dotAs(DBKey.IDENT_SID)
                + _FROM_ + TBL_BOOK_IDENTIFIER.startJoin(TBL_IDENTIFIERS)
                + _WHERE_ + TBL_BOOK_IDENTIFIER.dot(DBKey.FK_BOOK) + "=?";

        static final String FIND_SID_BY_BOOK_ID_AND_NAME =
                SELECT_ + TBL_BOOK_IDENTIFIER.dotAs(DBKey.IDENT_SID)
                + _FROM_ + TBL_BOOK_IDENTIFIER.startJoin(TBL_IDENTIFIERS)
                + _WHERE_ + TBL_BOOK_IDENTIFIER.dot(DBKey.FK_BOOK) + "=?"
                + _AND_ + TBL_IDENTIFIERS.dot(DBKey.IDENT_NAME) + "=?";

        /** Insert the link between a {@link Book} and a {@link Identifier}. */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_IDENTIFIER.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_IDENTIFIER
                + ',' + DBKey.IDENT_SID
                + ") VALUES(?,?,?)";

        /**
         * Delete the link between a {@link Book} and a {@link Identifier}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_IDENTIFIER.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";
    }
}
