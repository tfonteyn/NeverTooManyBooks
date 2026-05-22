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
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * <p>
 * This is a bit of a garbage bin where we handle all things specific to migration.
 * This class should only be used during upgrades or imports (public methods).
 */
public class IdentifierMigration {

    public static final String BOOK_URI_OBSOLETE = "book_uri";
    public static final String AUTHOR_URI_OBSOLETE = "author_uri";

    private static final String TAG = "IdentifierMigration";

    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String SELECT_1_FROM_ = "SELECT 1 FROM ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";
    private static final String _WHERE_ = " WHERE ";

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

    @NonNull
    public static Collection<Identifier> mapV7Identifier(final long id,
                                                         @NonNull final String key,
                                                         @NonNull final Identifier.Type type,
                                                         @NonNull final String name,
                                                         @Nullable final String siteUrl,
                                                         @Nullable final String bookUri,
                                                         @Nullable final String authorUri,
                                                         @Nullable final String wikidataClaim) {
        if (bookUri == null && authorUri == null
            || bookUri != null && authorUri != null) {
            // no urls at all or both present; create Book AND Author
            final Identifier iBook = new Identifier(
                    key, Identifier.EntityType.Book, type, name, siteUrl,
                    null,
                    null);
            // reuse the id
            iBook.setId(id);

            // new entry, no id
            final Identifier iAuthor = new Identifier(
                    key, Identifier.EntityType.Author, type, name, siteUrl,
                    null,
                    wikidataClaim);

            return List.of(iBook, iAuthor);

        } else if (bookUri != null) {
            // We only a book uri, and no author uri
            final Identifier iBook = new Identifier(
                    key, Identifier.EntityType.Book, type, name, siteUrl,
                    bookUri,
                    null);
            // reuse the id
            iBook.setId(id);
            return List.of(iBook);

        } else {
            // we have an author uri and no book uri
            final Identifier iAuthor = new Identifier(
                    key, Identifier.EntityType.Author, type, name, siteUrl,
                    authorUri,
                    wikidataClaim);
            // reuse the id
            iAuthor.setId(id);
            return List.of(iAuthor);
        }
    }

    /**
     * Add the given Identifiers using their keys.
     * Silently skips the ones already predefined/existing.
     *
     * @param keys of the {@link Identifier}s to add
     */
    void add(@NonNull final Set<String> keys) {
        keys.stream()
            .map(this::getPredefined)
            .flatMap(Optional::stream)
            // key must be unique
            .filter(identifier -> !isPresent(identifier.getKey()))
            .forEach(identifier -> insert(List.of(identifier)));
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
    private Optional<Identifier> getPredefined(@NonNull final String key) {
        return predefined.stream()
                         .filter(identifier -> identifier.getKey().equals(key))
                         .findFirst();
    }


    void deleteAllPredefined() {
        final List<String> keys = predefined.stream().map(Identifier::getKey)
                                            .collect(Collectors.toList());

        try (SQLiteStatement stmt = db.compileStatement(
                DELETE_FROM_ + TBL_IDENTIFIERS.getName()
                + _WHERE_ + DBKey.IDENTIFIERS.KEY + "=?")) {
            for (final String key : keys) {
                stmt.bindString(1, key);
                stmt.executeUpdateDelete();
            }
        }
    }

    /**
     * Assuming all predefined are previously deleted,
     * insert them using the correct up-to-date schema.
     */
    void reinsertPredefined() {
        insert(predefined);
    }

    void insert(@NonNull final Collection<Identifier> list) {
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(
                IdentifierDaoImpl.Sql.INSERT))) {
            for (final Identifier identifier : list) {
                IdentifierDaoImpl.doInsert(identifier, stmt);
            }
        } catch (@NonNull final SQLException e) {
            // log... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        } catch (@NonNull final DaoInsertException e) {
            // log, but just rethrow insert errors... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw new SQLException("db52", e);
        }
    }

    /**
     * Get all rows from the Identifier table as a collection of Bundles.
     * The "_id" column will be available as a {@code long},
     * all other columns are {@code String}.
     *
     * @return all rows
     */
    @NonNull
    Collection<Bundle> getCurrentList() {
        final List<Bundle> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TBL_IDENTIFIERS, null)) {
            while (cursor.moveToNext()) {
                final String[] columnNames = cursor.getColumnNames();
                final Bundle row = new Bundle();
                for (int c = 0; c < columnNames.length; c++) {
                    // yes, very inefficient... oh well.
                    if (DBKey.PK_ID.equals(columnNames[c])) {
                        row.putLong(columnNames[c], cursor.getLong(c));
                    } else {
                        row.putString(columnNames[c], cursor.getString(c));
                    }
                }
                result.add(row);
            }
        }
        return result;
    }

    /**
     * Update the name for the given key. Does nothing if the key is not found.
     *
     * @param key to update
     */
    void fixName(@NonNull final String key) {
        getPredefined(key).ifPresent(identifier -> db
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
        getPredefined(key).ifPresent(identifier -> db
                .execSQL(UPDATE_ + TBL_IDENTIFIERS.getName()
                         + _SET_ + DBKey.IDENTIFIERS.TYPE + "='" + identifier.getType() + '\''
                         + _WHERE_ + DBKey.IDENTIFIERS.KEY + "='" + identifier.getKey() + '\''));
    }

    /**
     * Add the column {@link DBKey.IDENTIFIERS#WIKIDATA_CLAIM} if not yet there.
     * <p>
     * This call is still needed even after db52 update to allow importing
     * backup archives created with older versions.
     *
     * @param keys set of specific keys to add/update, or an empty Set to do all known keys.
     */
    public void initWikidataClaim(@NonNull final Set<String> keys) {
        init(DBDefinitions.DOM_IDENTIFIER_WIKIDATA_CLAIM, keys, Identifier::getWikidataClaim);
    }

    // db52 update REMOVED
    //    /**
    //     * Add the column {@link IdentifierMigration#BOOK_URI_OBSOLETE} if not yet there.
    //     *
    //     * @param keys set of specific keys to add/update, or an empty Set to do all known keys.
    //     */
    //    void initBookUrl(@NonNull final Set<String> keys) {
    //        init(DOM_IDENTIFIER_BOOK_URI_OBSOLETE, keys, Identifier::getUri);
    //    }

    // db52 update REMOVED
    //    /**
    //     * Add the column {@link IdentifierMigration#AUTHOR_URI_OBSOLETE} if not yet there.
    //     *
    //     * @param keys set of specific keys to add/update, or an empty Set to do all known keys.
    //     */
    //    void initAuthorUrl(@NonNull final Set<String> keys) {
    //        init(DOM_IDENTIFIER_AUTHOR_URI_OBSOLETE, keys, Identifier::getAuthorUri);
    //    }

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
