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
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHOR_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;

class V8 {

    private static final String TAG = "V8";

    @NonNull
    private final Context context;
    @NonNull
    private final SQLiteDatabase db;

    private final int oldVersion;
    @NonNull
    private final IdentifierMigration identifierMigration;

    /**
     * Constructor.
     *
     * @param context             Current context
     * @param db                  Underlying database
     * @param oldVersion          The old database version.
     * @param identifierMigration helper
     */
    V8(@NonNull final Context context,
       @NonNull final SQLiteDatabase db,
       final int oldVersion,
       @NonNull final IdentifierMigration identifierMigration) {
        this.context = context;
        this.db = db;
        this.oldVersion = oldVersion;
        this.identifierMigration = identifierMigration;
    }

    /**
     * Perform all updates.
     * <p>
     * v8.0.0: 52
     */
    void update() {
        if (oldVersion < 52) {
            db52();
        }
    }

    private void db52() {
        db52updateIdentifierTable();
        DBDefinitions.TBL_SERIES_IDENTIFIER.create(db, true);
        DBDefinitions.TBL_SERIES_PUBLICATION_FREQUENCY.create(db, true);

        TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_BOOK_EDITION_INFO);

        db52cleanupBnfIdentifiers();
    }

    private void db52updateIdentifierTable() {
        // There are TWO possible situations.
        // 1. We did an upgrade db35
        //    => the db35 update will have created and populated
        //       the entire Identifier table fully correct.
        // identifierMigration.isNewInstall() == true
        //
        // 2. We did an upgrade from a db36 or later version
        //    => we need to populate/migrate existing data.
        // identifierMigration.isNewInstall() == false


        if (identifierMigration.isNewInstall()) {
            return;
        }

        // add the new columns FIRST
        TBL_IDENTIFIERS.alterTableAddColumns(db,
                                             DBDefinitions.DOM_IDENTIFIER_ENTITY,
                                             DBDefinitions.DOM_IDENTIFIER_URI);
        // We'll need to recreate the index to include the entity column
        TBL_IDENTIFIERS.getIndex(DBKey.IDENTIFIERS.KEY)
                       .ifPresent(index -> index.delete(db));


        // isolate the linked tables
        Upgrade.runWithoutConstraints(db, () -> {

            // The new/updated format, using a key+entity
            final Collection<Identifier> initialList = Identifier.createInitialList(context);

            // Reduce to just the keys, this collapses the entities
            final Set<String> predefinedKeys = initialList
                    .stream().map(Identifier::getKey).collect(Collectors.toSet());


            // Get ALL current rows, includes both predefined and user-defined.
            final Map<String, Bundle> allCurrentKeys = identifierMigration.getCurrentList();

            final List<String> currentUserDefinedKeys = allCurrentKeys
                    .keySet()
                    .stream()
                    .filter(key -> !predefinedKeys.contains(key))
                    .toList();

            final List<String> currentPredefinedKeys = allCurrentKeys
                    .keySet()
                    .stream()
                    .filter(predefinedKeys::contains)
                    .toList();

            // collect all as needed, then run a bulk operation afterwards.
            final List<Identifier> toInsert = new ArrayList<>();
            final List<Identifier> toUpdate = new ArrayList<>();

            // migrate the user defined Identifiers
            for (final String key : currentUserDefinedKeys) {
                db52updateUserDefineIdentifiers(key, allCurrentKeys, toUpdate, toInsert);
            }

            // update the existing predefined Identifiers
            for (final String key : currentPredefinedKeys) {
                db52updateUPredefinedIdentifiers(key, allCurrentKeys, initialList,
                                                 toUpdate, toInsert);
                predefinedKeys.remove(key);
            }

            // any predefines ones left need inserting
            final List<Identifier> leftOver = initialList
                    .stream()
                    .filter(identifier -> predefinedKeys.contains(identifier.getKey()))
                    .toList();

            toInsert.addAll(leftOver);

            // RUN THE BULK OPERATIONS
            identifierMigration.update(toUpdate);
            identifierMigration.insert(toInsert);

            // Delete the now obsolete columns by recreating the entire table
            // The indexes will be recreated as normal at the end of the upgrade.
            TBL_IDENTIFIERS.recreate(db);
        });
    }

    private void db52updateUPredefinedIdentifiers(@NonNull final String key,
                                                  @NonNull final Map<String, Bundle> all,
                                                  @NonNull final Collection<Identifier> initialList,
                                                  @NonNull final List<Identifier> toUpdate,
                                                  @NonNull final List<Identifier> toInsert) {

        final Bundle ib = all.get(key);
        @SuppressWarnings("DataFlowIssue")
        final long id = ib.getLong(DBKey.PK_ID, 0);

        // The ones from the all-list ARE book-identifiers.
        // Copy that id to the predefined/initial one, and force an update
        initialList.stream()
                   .filter(identifier -> identifier.getKey().equals(key))
                   .filter(identifier -> identifier.getEntityType() == Identifier.EntityType.Book)
                   .findFirst()
                   .ifPresent(identifier -> {
                       identifier.setId(id);
                       toUpdate.add(identifier);
                   });

        // Filter for the non-book-identifiers; those always need inserting
        initialList.stream()
                   .filter(identifier -> identifier.getKey().equals(key))
                   .filter(identifier -> identifier.getEntityType() != Identifier.EntityType.Book)
                   .forEach(toInsert::add);
    }

    private void db52updateUserDefineIdentifiers(
            @NonNull final String key,
            @NonNull final Map<String, Bundle> all,
            @NonNull final List<Identifier> toUpdate,
            @NonNull final List<Identifier> toInsert) {

        final Bundle ib = all.get(key);
        @SuppressWarnings("DataFlowIssue")
        final long id = ib.getLong(DBKey.PK_ID, 0);
        final String typeStr = ib.getString(DBKey.IDENTIFIERS.TYPE);
        final String name = ib.getString(DBKey.IDENTIFIERS.NAME);
        // Paranoia... should NEVER be the case
        if (id != 0 && name != null && typeStr != null && !typeStr.isEmpty()) {
            @NonNull
            final Identifier.Type type = Identifier.Type.byId(typeStr.charAt(0));
            @Nullable
            final String siteUrl = ib.getString(DBKey.IDENTIFIERS.SITE_URL);
            @Nullable
            final String wikidataClaim = ib.getString(DBKey.IDENTIFIERS.WIKIDATA_CLAIM);
            @Nullable
            final String bookUri = ib.getString(IdentifierMigration.BOOK_URI_OBSOLETE);
            @Nullable
            final String authorUri = ib.getString(IdentifierMigration.AUTHOR_URI_OBSOLETE);

            final List<Identifier> c = IdentifierMigration.mapV7Identifier(
                    id, key, type, name, siteUrl, bookUri, authorUri, wikidataClaim);

            for (final Identifier identifier : c) {
                if (identifier.getId() > 0) {
                    // the existing book-identifiers
                    toUpdate.add(identifier);
                } else {
                    // the new author-identifiers
                    toInsert.add(identifier);
                }
            }
        }
    }

    private void db52cleanupBnfIdentifiers() {
        // The internal storage format of the BnF sid has changed.
        // Instead of storing the "cbXXXXXXXXc" values, we now store the raw "XXXXXXXX" value.

        long id;
        id = getIdentifierId(Identifier.EntityType.Book);
        if (id > 0) {
            db52cleanupBnfIdentifiers(TBL_BOOK_IDENTIFIER.getName(), id);
        }
        id = getIdentifierId(Identifier.EntityType.Author);
        if (id > 0) {
            db52cleanupBnfIdentifiers(TBL_AUTHOR_IDENTIFIER.getName(), id);
        }
    }

    /**
     * Get the id for the given entity for the BNF Identifier.
     *
     * @param entity to get
     *
     * @return id, or {@code 0} if none found.
     */
    private long getIdentifierId(@NonNull final Identifier.EntityType entity) {
        try (SQLiteStatement stmt = db.compileStatement(
                "SELECT " + DBKey.PK_ID + " FROM " + TBL_IDENTIFIERS.getName()
                + " WHERE " + DBKey.IDENTIFIERS.KEY + "=?"
                + " AND " + DBKey.IDENTIFIERS.ENTITY + "=?")) {

            stmt.bindLong(1, entity.getId());
            stmt.bindString(2, Identifier.SID_BNF);
            return stmt.simpleQueryForLong();

        } catch (@NonNull final SQLiteDoneException e) {
            return 0;
        }
    }

    private void db52cleanupBnfIdentifiers(@NonNull final String tableName,
                                           final long id) {

        try (SQLiteStatement stmt = db.compileStatement(
                "UPDATE " + tableName
                + " SET " + DBKey.IDENTIFIERS.SID + "=SUBSTR(" + DBKey.IDENTIFIERS.SID + ",3,8)"
                + " WHERE " + DBKey.FK_IDENTIFIER + "=?"
                + "  AND LENGTH(" + DBKey.IDENTIFIERS.SID + ")=11"
                + "  AND " + DBKey.IDENTIFIERS.SID + " LIKE 'cb%'")) {

            stmt.bindLong(1, id);
            final int rowsAffected = stmt.executeUpdateDelete();
            LoggerFactory.getLogger().w(TAG, "db52cleanupBnfIdentifiers"
                                             + "|id=" + id
                                             + "|table=" + tableName
                                             + "|rows=" + rowsAffected);
        }
    }
}
