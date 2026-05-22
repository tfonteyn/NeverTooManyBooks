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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;

class V8 {

    private static final String TAG = "V8";

    @NonNull
    private final Context context;
    @NonNull
    private final SQLiteDatabase db;

    @NonNull
    private final IdentifierMigration identifierMigration;

    /**
     * Constructor.
     *
     * @param context             Current context
     * @param db                  Underlying database
     * @param identifierMigration helper
     */
    V8(@NonNull final Context context,
       @NonNull final SQLiteDatabase db,
       @NonNull final IdentifierMigration identifierMigration) {
        this.context = context;
        this.db = db;
        this.identifierMigration = identifierMigration;
    }

    /**
     * v8.0.0: 52
     *
     * @param oldVersion The old database version.
     */
    void update(final int oldVersion) {
        if (oldVersion < 52) {
            db52();
        }
    }

    private void db52() {
        // add the new columns FIRST
        TBL_IDENTIFIERS.alterTableAddColumns(db,
                                             DBDefinitions.DOM_IDENTIFIER_ENTITY,
                                             DBDefinitions.DOM_IDENTIFIER_URI);
        // We'll need to recreate the index to include the entity column
        TBL_IDENTIFIERS.getIndex(DBKey.IDENTIFIERS.KEY)
                       .ifPresent(index -> index.delete(db));

        // reminder: we're in a transaction, rest easy
        identifierMigration.deleteAllPredefined();
        // We now only have user defined Identifiers in the table
        final Collection<Bundle> currentList = identifierMigration.getCurrentList();
        if (!currentList.isEmpty()) {
            // Clear the entire table.
            db.delete(TBL_IDENTIFIERS.getName(), null, null);

            // migrate the user defined Identifiers
            final List<Identifier> toInsert = new ArrayList<>();
            for (final Bundle ib : currentList) {
                final long id = ib.getLong(DBKey.PK_ID, 0);
                final String key = ib.getString(DBKey.IDENTIFIERS.KEY);
                final String typeStr = ib.getString(DBKey.IDENTIFIERS.TYPE);
                final String name = ib.getString(DBKey.IDENTIFIERS.NAME);
                // Paranoia... should NEVER be the case
                if (key != null && name != null && typeStr != null && !typeStr.isEmpty()) {
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

                    toInsert.addAll(IdentifierMigration.mapV7Identifier(
                            id, key, type, name, siteUrl, bookUri, authorUri, wikidataClaim));
                }
            }

            // insert the user defined Identifiers
            identifierMigration.insert(toInsert);
        }
        // finally re-insert the predefined Identifiers
        identifierMigration.reinsertPredefined();

        // and delete the now obsolete columns by recreating the entire table
        // The indexes will be recreated as normal at the end of the upgrade.
        TBL_IDENTIFIERS.recreate(db);
    }
}
