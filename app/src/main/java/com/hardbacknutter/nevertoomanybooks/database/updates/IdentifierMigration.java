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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.IdentifierDaoImpl;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;

/**
 * App 7.0.0 / db 35 introduced the Identifier table.
 * <p>
 * This is a bit of a garbage bin where we handle all things specific to migration.
 * This class should only be used during upgrades or imports (public methods).
 */
public class IdentifierMigration {

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
    private boolean newInstall;

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
    }

    /**
     * Map a V7 identifier which was used for BOTH book and author,
     * to one or two V8 identifiers: book and/or author identifiers.
     *
     * @return replacements
     */
    @SuppressWarnings({"MissingJavadoc", "CheckStyle"})
    @NonNull
    public static List<Identifier> mapV7Identifier(final long id,
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
                    Identifier.EntityType.Book, type, key, name, siteUrl,
                    null,
                    null);
            // reuse the id
            iBook.setId(id);

            // new entry, no id
            final Identifier iAuthor = new Identifier(
                    Identifier.EntityType.Author, type, key, name, siteUrl,
                    null,
                    wikidataClaim);

            return List.of(iBook, iAuthor);

        } else if (bookUri != null) {
            // We only a book uri, and no author uri
            final Identifier iBook = new Identifier(
                    Identifier.EntityType.Book, type, key, name, siteUrl,
                    bookUri,
                    null);
            // reuse the id
            iBook.setId(id);
            return List.of(iBook);

        } else {
            // we have an author uri and no book uri
            final Identifier iAuthor = new Identifier(
                    Identifier.EntityType.Author, type, key, name, siteUrl,
                    authorUri,
                    wikidataClaim);
            // reuse the id
            iAuthor.setId(id);
            return List.of(iAuthor);
        }
    }

    /**
     * Repair the wikidata claim/p column data for all predefined Identifiers.
     *
     * @param context Current context
     */
    public static void repairBuiltinIdentifiersWikidataClaim(@NonNull final Context context) {
        final SynchronizedDb db = ServiceLocator.getInstance().getDb();
        // Load the data from the predefined Identifiers
        try (SynchronizedStatement stmt = db.compileStatement(
                "UPDATE " + TBL_IDENTIFIERS.getName()
                + " SET " + DBDefinitions.DOM_IDENTIFIER_WIKIDATA_CLAIM + "=?"
                + " WHERE " + DBDefinitions.DOM_IDENTIFIER_KEY + "=?")) {

            for (final Identifier identifier : Identifier.createInitialList(context)) {
                final String claim = identifier.getWikidataClaim().orElse(null);
                if (claim != null) {
                    stmt.bindString(1, claim);
                    stmt.bindString(2, identifier.getKey());
                    stmt.executeUpdateDelete();
                }
            }
        }
    }

    /**
     * Subsequent updates know that we did a new install of the Identifier table.
     */
    void setIsNewInstall() {
        newInstall = true;
    }

    /**
     * Check if a previous upgrade did a new install of the Identifier table.
     *
     * @return flag
     */
    boolean isNewInstall() {
        return newInstall;
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
                "SELECT 1 FROM " + DBDefinitions.TBL_IDENTIFIERS.getName()
                + " WHERE " + DBKey.IDENTIFIERS.KEY + "=?")) {
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

    void insert(@NonNull final Collection<Identifier> list) {
        IdentifierDaoImpl.doInsert(db, list);
    }

    void update(@NonNull final Collection<Identifier> list) {
        IdentifierDaoImpl.doUpdate(db, list);
    }

    /**
     * Get all rows from the Identifier table as a map.
     * key:   identifier key
     * value: Bundle with:
     * - The "_id" column as a {@code long}.
     * - all other columns as {@code String}.
     * <p>
     * Contains all user-defined entries + the original predefined.
     *
     * @return all rows
     */
    @NonNull
    Map<String, Bundle> getCurrentList() {
        final Map<String, Bundle> result = new LinkedHashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + TBL_IDENTIFIERS, null)) {
            while (cursor.moveToNext()) {
                final String[] columnNames = cursor.getColumnNames();
                final Bundle row = new Bundle();
                for (int c = 0; c < columnNames.length; c++) {
                    // yes, very inefficient... oh well.
                    if (DBKey.PK_ID.equals(columnNames[c])) {
                        row.putLong(DBKey.PK_ID, cursor.getLong(c));
                    } else {
                        row.putString(columnNames[c], cursor.getString(c));
                    }
                }
                result.put(row.getString(DBKey.IDENTIFIERS.KEY), row);
            }
        }
        return result;
    }
}
