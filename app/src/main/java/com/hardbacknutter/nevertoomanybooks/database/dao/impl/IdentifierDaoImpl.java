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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.util.logger.LoggerFactory;

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
     * @param db Database Access
     */
    public IdentifierDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Constructor.
     *
     * @param db     Database Access
     * @param logTag of this DAO for logging.
     */
    IdentifierDaoImpl(@NonNull final SynchronizedDb db,
                      @NonNull final String logTag) {
        super(db, logTag);
    }

    /**
     * Run at <strong>installation</strong> time to add the predefined ID's to the database.
     * <p>
     * KEEP IN SYNC WITH restore.
     *
     * @param context Current context
     * @param db      Underlying database
     *
     * @throws SQLException on unexpected failures
     * @see #restore(Context)
     */
    public static void onPostCreate(@NonNull final Context context,
                                    @NonNull final SQLiteDatabase db) {
        final Collection<Identifier> identifierList = Identifier.createInitialList(context);
        doInsert(db, identifierList);
    }

    public static void doInsert(@NonNull final SQLiteDatabase db,
                                @NonNull final Collection<Identifier> identifierList) {
        // This method must run on API 26: Use a simple INSERT, and not the UPSERT!
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(Sql.INSERT))) {
            for (final Identifier identifier : identifierList) {
                doInsert(identifier, stmt);
            }
        } catch (@NonNull final SQLException e) {
            // log... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        } catch (@NonNull final DaoInsertException e) {
            // log, but just rethrow errors... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw new SQLException("doInsert", e);
        }
    }

    /**
     * Insert a new {@link Identifier}.
     *
     * @param identifier to insert. Will be updated with the id
     * @param stmt       statement to run
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoInsertException on failure
     */
    private static long doInsert(@NonNull final Identifier identifier,
                                 @NonNull final ExtSQLiteStatement stmt)
            throws DaoInsertException {
        int c = 0;
        stmt.bindString(++c, identifier.getKey().toLowerCase(Locale.ENGLISH));
        stmt.bindLong(++c, identifier.getEntityType().getId());

        stmt.bindString(++c, String.valueOf(identifier.getType().getId()));
        stmt.bindString(++c, identifier.getName());

        stmt.bindString(++c, identifier.getWikidataClaim().orElse(null));
        stmt.bindString(++c, identifier.getSiteUrl());
        stmt.bindString(++c, identifier.getRawUri().orElse(null));
        final long iId = stmt.executeInsert(null);

        if (iId != -1) {
            identifier.setId(iId);
            return iId;
        }

        // The insert failed with -1
        throw new DaoInsertException(ERROR_INSERT_FROM + identifier);
    }

    /**
     * Updates the given list of identifiers.
     *
     * @param db             Underlying database
     * @param identifierList to insert
     *
     * @throws SQLException FATAL - we're in a real mess now
     */
    public static void doUpdate(@NonNull final SQLiteDatabase db,
                                @NonNull final Collection<Identifier> identifierList) {
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(Sql.UPDATE))) {
            for (final Identifier identifier : identifierList) {
                doUpdate(identifier, stmt);
            }
        } catch (@NonNull final SQLException e) {
            // log... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        } catch (@NonNull final DaoUpdateException e) {
            // log, but just rethrow errors... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw new SQLException("doUpdate", e);
        }
    }

    /**
     * Update the given {@link Identifier}.
     *
     * @param identifier to update
     * @param stmt       statement to run
     *
     * @throws DaoUpdateException on failure
     */
    private static void doUpdate(@NonNull final Identifier identifier,
                                 @NonNull final ExtSQLiteStatement stmt)
            throws DaoUpdateException {
        int c = 0;
        stmt.bindString(++c, identifier.getKey().toLowerCase(Locale.ENGLISH));
        stmt.bindLong(++c, identifier.getEntityType().getId());

        stmt.bindString(++c, String.valueOf(identifier.getType().getId()));
        stmt.bindString(++c, identifier.getName());

        stmt.bindString(++c, identifier.getWikidataClaim().orElse(null));
        stmt.bindString(++c, identifier.getSiteUrl());
        stmt.bindString(++c, identifier.getRawUri().orElse(null));

        stmt.bindLong(++c, identifier.getId());
        final int rowsAffected = stmt.executeUpdateDelete(null);

        if (rowsAffected > 0) {
            return;
        }

        throw new DaoUpdateException(ERROR_UPDATE_FROM + identifier);
    }

    /**
     * KEEP IN SYNC WITH onPostCreate.
     *
     * @see #onPostCreate(Context, SQLiteDatabase)
     */
    @Override
    public void restore(@NonNull final Context context)
            throws DaoUpdateException, DaoInsertException {
        final Collection<Identifier> identifierList = Identifier.createInitialList(context);
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                restoreApi30(identifierList);
            } else {
                restoreApi26(identifierList);
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

    @RequiresApi(Build.VERSION_CODES.R)
    private void restoreApi30(@NonNull final Collection<Identifier> identifierList)
            throws DaoInsertException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BUILTIN)) {
            for (final Identifier identifier : identifierList) {
                doInsert(identifier, stmt);
            }
        }
    }

    private void restoreApi26(@NonNull final Collection<Identifier> identifierList)
            throws DaoUpdateException, DaoInsertException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        long iId;
        try (SynchronizedStatement stmtFindByKey = db.compileStatement(
                Sql.FIND_ID_BY_KEY_AND_ENTITY_TYPE);
             SynchronizedStatement stmtInsert = db.compileStatement(Sql.INSERT);
             SynchronizedStatement stmtUpdate = db.compileStatement(Sql.UPDATE)) {

            for (final Identifier identifier : identifierList) {
                // do we have this Key/EntityType?
                stmtFindByKey.bindString(1, identifier.getKey().toLowerCase(Locale.ENGLISH));
                stmtFindByKey.bindLong(2, identifier.getEntityType().getId());
                iId = stmtFindByKey.simpleQueryForLongOrZero();
                if (iId == 0) {
                    // no, add it
                    doInsert(identifier, stmtInsert);
                } else {
                    // key exists, update it
                    identifier.setId(iId);
                    doUpdate(identifier, stmtUpdate);
                }
            }
        }
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
    public Optional<Identifier> find(@NonNull final String key,
                                     @NonNull final Identifier.EntityType entityType) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_KEY_AND_ENTITY_TYPE, new String[]{
                key.toLowerCase(Locale.ENGLISH),
                String.valueOf(entityType.getId())})) {

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
        try (Cursor cursor = db.rawQuery(Sql.SELECT_ALL_ORDERED_BY_KEY, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Identifier(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Identifier> getAll(@NonNull final Identifier.EntityType entityType) {
        final List<Identifier> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_ALL_BY_ENTITY_ORDERED_BY_KEY,
                                         new String[]{String.valueOf(entityType.getId())})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Identifier(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    public boolean pruneList(@NonNull final Collection<Identifier.Value> list) {
        if (list.isEmpty()) {
            return false;
        }

        final Set<String> keysFound = new HashSet<>(list.size());
        final Iterator<Identifier.Value> iterator = list.iterator();
        boolean modified = false;

        while (iterator.hasNext()) {
            final Identifier.Value iv = iterator.next();
            if (!isValidIdentifier(iv) || !keysFound.add(iv.getKey().toLowerCase(Locale.ENGLISH))) {
                iterator.remove();
                modified = true;
            }
        }

        return modified;
    }

    private boolean isValidIdentifier(@NonNull final Identifier.Value iv) {
        final String sid = iv.getSid();
        // Not just a sanity check: we MUST check for null!
        //noinspection ConstantValue
        return sid != null && !sid.isEmpty() && !"0".equals(sid);
    }

    @Override
    public void fixId(@NonNull final Identifier identifier) {
        final long found = find(identifier.getKey(), identifier.getEntityType())
                .map(Identifier::getId).orElse(0L);
        identifier.setId(found);
    }

    @IntRange(from = 1)
    @Override
    public long insert(@NonNull final Identifier identifier)
            throws DaoInsertException {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            return doInsert(identifier, stmt);
        }
    }

    @Override
    public void update(@NonNull final Identifier identifier)
            throws DaoUpdateException {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            doUpdate(identifier, stmt);
        }
    }

    @Override
    public boolean delete(@NonNull final Identifier identifier) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, identifier.getId());
            rowsAffected = stmt.executeUpdateDelete(null);
        }
        if (rowsAffected > 0) {
            identifier.setId(0);
            return true;
        }
        return false;
    }

    private static final class Sql {
        /** Insert an {@link Identifier}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_IDENTIFIERS.getName()
                + '(' + DBKey.IDENTIFIERS.KEY
                + ',' + DBKey.IDENTIFIERS.ENTITY

                + ',' + DBKey.IDENTIFIERS.TYPE
                + ',' + DBKey.IDENTIFIERS.NAME

                + ',' + DBKey.IDENTIFIERS.WIKIDATA_CLAIM
                + ',' + DBKey.IDENTIFIERS.SITE_URL
                + ',' + DBKey.IDENTIFIERS.URI
                + ") VALUES(?,?,?,?,?,?,?)";

        /** Update an {@link Identifier}. */
        static final String UPDATE =
                UPDATE_ + TBL_IDENTIFIERS.getName()
                + _SET_ + DBKey.IDENTIFIERS.KEY + "=?"
                + ',' + DBKey.IDENTIFIERS.ENTITY + "=?"

                + ',' + DBKey.IDENTIFIERS.TYPE + "=?"
                + ',' + DBKey.IDENTIFIERS.NAME + "=?"

                + ',' + DBKey.IDENTIFIERS.WIKIDATA_CLAIM + "=?"
                + ',' + DBKey.IDENTIFIERS.SITE_URL + "=?"
                + ',' + DBKey.IDENTIFIERS.URI + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Identifier}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_IDENTIFIERS.getName() + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Insert the built-in identifiers at install/upgrade,
         * or re-insert/update them at a later time.
         */
        @RequiresApi(Build.VERSION_CODES.R)
        static final String INSERT_BUILTIN =
                INSERT + "ON CONFLICT(" + DBKey.IDENTIFIERS.KEY + ") DO UPDATE SET "
                + DBKey.IDENTIFIERS.KEY + "=excluded." + DBKey.IDENTIFIERS.KEY
                + ',' + DBKey.IDENTIFIERS.ENTITY + "=excluded." + DBKey.IDENTIFIERS.ENTITY

                + ',' + DBKey.IDENTIFIERS.TYPE + "=excluded." + DBKey.IDENTIFIERS.TYPE
                + ',' + DBKey.IDENTIFIERS.NAME + "=excluded." + DBKey.IDENTIFIERS.NAME

                + ',' + DBKey.IDENTIFIERS.WIKIDATA_CLAIM
                + "=excluded." + DBKey.IDENTIFIERS.WIKIDATA_CLAIM
                + ',' + DBKey.IDENTIFIERS.SITE_URL + "=excluded." + DBKey.IDENTIFIERS.SITE_URL
                + ',' + DBKey.IDENTIFIERS.URI + "=excluded." + DBKey.IDENTIFIERS.URI;

        static final String SELECT_ALL =
                SELECT_ + TBL_IDENTIFIERS.dotAs(DBKey.PK_ID,
                                                DBKey.IDENTIFIERS.KEY,
                                                DBKey.IDENTIFIERS.ENTITY,
                                                DBKey.IDENTIFIERS.TYPE,
                                                DBKey.IDENTIFIERS.NAME,
                                                DBKey.IDENTIFIERS.WIKIDATA_CLAIM,
                                                DBKey.IDENTIFIERS.SITE_URL,
                                                DBKey.IDENTIFIERS.URI);

        /**
         * ALL rows.
         */
        static final String SELECT_ALL_ORDERED_BY_KEY =
                SELECT_ALL + _FROM_ + TBL_IDENTIFIERS.as()
                + _ORDER_BY_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY);

        /**
         * All rows for a given {@link Identifier.EntityType}.
         */
        static final String SELECT_ALL_BY_ENTITY_ORDERED_BY_KEY =
                SELECT_ALL + _FROM_ + TBL_IDENTIFIERS.as()
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.ENTITY) + "=?"
                + _ORDER_BY_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY);

        static final String FIND_BY_ID =
                SELECT_ALL + _FROM_ + TBL_IDENTIFIERS.as()
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.PK_ID) + "=?";

        static final String FIND_BY_KEY_AND_ENTITY_TYPE =
                SELECT_ALL + _FROM_ + TBL_IDENTIFIERS.as()
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY) + "=?"
                + _AND_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.ENTITY) + "=?";

        static final String FIND_ID_BY_KEY_AND_ENTITY_TYPE =
                SELECT_ + TBL_IDENTIFIERS.dotAs(DBKey.PK_ID)
                + _FROM_ + TBL_IDENTIFIERS.as()
                + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY) + "=?"
                + _AND_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.ENTITY) + "=?";
    }
}
