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

package com.hardbacknutter.nevertoomanybooks.database.updates;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.IdentifierDaoImpl;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;

/**
 * App 7.0.0 / db 35 introduced the Identifier table.
 */
public class IdentifierMigration {

    private static final String TAG = "IdentifierMigration";

    /**
     * Archive format v7 and older used individual Identifier/Bundle keys on the book itself.
     * This maps the old name to the new name.
     */
    public static final Map<String, String> MAPPINGS = Map.of(
            "goodreads_book_id", Identifier.SID_GOODREADS,
            "isfdb_book_id", Identifier.SID_ISFDB,
            "lt_book_id", Identifier.SID_LIBRARY_THING,
            "ol_book_id", Identifier.SID_OPEN_LIBRARY,
            "si_book_id", Identifier.SID_STRIP_INFO,
            "ld_book_id", Identifier.SID_LAST_DODO_NL,
            "bdt_book_id", Identifier.SID_BEDETHEQUE
    );

    private static final String SELECT_1_FROM_ = "SELECT 1 FROM ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";
    private static final String _WHERE_ = " WHERE ";

    @NonNull
    private final SQLiteDatabase db;
    private final Collection<Identifier> predefined;
    private final TableInfo tableInfo;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public IdentifierMigration(@NonNull final Context context) {
        this(context, ServiceLocator.getInstance().getDb().getSQLiteDatabase());
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param db      Underlying database
     */
    IdentifierMigration(@NonNull final Context context,
                        @NonNull final SQLiteDatabase db) {
        this.db = db;

        predefined = Identifier.createInitialList(context);
        tableInfo = DBDefinitions.TBL_IDENTIFIERS.getTableInfo(db);
    }


    /**
     * Add the given Identifiers using their keys.
     * Silently skips the ones already existing.
     *
     * @param keys of the {@link Identifier}s to add
     */
    void add(@NonNull final Set<String> keys) {
        keys.stream().map(this::get).flatMap(Optional::stream).forEach(this::add);
    }

    /**
     * Add the given {@link Identifier}.
     * Silently skip if it already exists.
     *
     * @param identifier to add
     * @throws SQLException on failure
     */
    private void add(@NonNull final Identifier identifier) {
        // key must be unique
        if (isPresent(identifier.getKey())) {
            return;
        }

        // IdentifierDaoImpl#doInsert(@NonNull final Identifier identifier,
        //                 .               @NonNull final ExtSQLiteStatement stmt)
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(
                IdentifierDaoImpl.Sql.INSERT))) {
            IdentifierDaoImpl.doInsert(identifier, stmt);
        } catch (@NonNull final SQLException e) {
            // log... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        } catch (@NonNull final DaoInsertException e) {
            // log, but just rethrow insert errors... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw new SQLException("onPostCreate", e);
        }
    }

    private boolean isPresent(@NonNull final String key) {
        try (SQLiteStatement stmt = db.compileStatement(
                SELECT_1_FROM_ + DBDefinitions.TBL_IDENTIFIERS.getName()
                + _WHERE_ + DBKey.IDENTIFIERS.KEY + "=?")) {
            stmt.bindString(1, key);
            return 1 == stmt.simpleQueryForLong();
        } catch (@NonNull final SQLiteDoneException ignore) {
            // ignore
        }
        return false;
    }

    @NonNull
    private Optional<Identifier> get(@NonNull final String key) {
        return predefined.stream()
                         .filter(identifier -> identifier.getKey().equals(key))
                         .findFirst();
    }

    /**
     * Update the name for the given key. Does nothing if the key is not found.
     *
     * @param key to update
     */
    void fixName(@NonNull final String key) {
        get(key).ifPresent(identifier -> db
                .execSQL(UPDATE_ + TBL_IDENTIFIERS.getName()
                         + _SET_ + DBKey.IDENTIFIERS.NAME + "='" + identifier.getName() + '\''
                         + _WHERE_ + DBKey.IDENTIFIERS.KEY + "='" + identifier.getKey() + '\''));
    }

    /**
     * Update the type for the given key. Does nothing if the key is not found.
     *
     * @param key to update
     */
    void fixType(@NonNull final String key) {
        get(key).ifPresent(identifier -> db
                .execSQL(UPDATE_ + TBL_IDENTIFIERS.getName()
                         + _SET_ + DBKey.IDENTIFIERS.TYPE + "='" + identifier.getType() + '\''
                         + _WHERE_ + DBKey.IDENTIFIERS.KEY + "='" + identifier.getKey() + '\''));
    }

    /**
     * Add the column {@link DBKey.IDENTIFIERS#WIKIDATA_CLAIM} if not yet there.
     *
     * @param keys set of specific keys to add/update, or an empty Set to do all known keys.
     */
    public void initWikidataClaim(@NonNull final Set<String> keys) {
        init(DBDefinitions.DOM_IDENTIFIER_WIKIDATA_CLAIM, keys, Identifier::getWikidataClaim);
    }

    /**
     * Add the column {@link DBKey.IDENTIFIERS#BOOK_URI} if not yet there.
     *
     * @param keys set of specific keys to add/update, or an empty Set to do all known keys.
     */
    void initBookUrl(@NonNull final Set<String> keys) {
        init(DBDefinitions.DOM_IDENTIFIER_BOOK_URI, keys, Identifier::getBookUri);
    }

    /**
     * Add the column {@link DBKey.IDENTIFIERS#AUTHOR_URI} if not yet there.
     *
     * @param keys set of specific keys to add/update, or an empty Set to do all known keys.
     */
    void initAuthorUrl(@NonNull final Set<String> keys) {
        init(DBDefinitions.DOM_IDENTIFIER_AUTHOR_URI, keys, Identifier::getAuthorUri);
    }

    /**
     * Update the given domain for the given keys.
     * The column is added to the table if not there yet.
     * Silently skips keys which have been deleted. i.e. this will NOT restore missing keys.
     *
     * @param domain        to add/use
     * @param keys          to update
     * @param valueSupplier provides the value to store
     *
     * @see com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao#restore(Context)
     */
    private void init(@NonNull final Domain domain,
                      @NonNull final Set<String> keys,
                      @NonNull final Function<Identifier, Optional<String>> valueSupplier) {
        // Create the column if not already present.
        if (tableInfo.getColumn(domain.getName()) == null) {
            TBL_IDENTIFIERS.alterTableAddColumns(db, domain);
        }

        // Load the data from the predefined Identifiers
        try (SQLiteStatement stmt = db.compileStatement(
                UPDATE_ + TBL_IDENTIFIERS.getName()
                + _SET_ + domain + "=?"
                + _WHERE_ + DBDefinitions.DOM_IDENTIFIER_KEY + "=?")) {

            predefined.stream()
                      .filter(identifier -> keys.isEmpty() || keys.contains(identifier.getKey()))
                      .forEach(identifier -> valueSupplier.apply(identifier).ifPresent(v -> {
                          stmt.bindString(1, v);
                          stmt.bindString(2, identifier.getKey());
                          stmt.executeUpdateDelete();
                      }));
        }
    }
}
