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

import android.database.Cursor;
import android.database.SQLException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierValueDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;

public class IdentifierValueDaoImpl
        extends IdentifierDaoImpl
        implements IdentifierValueDao {

    private static final String TAG = "IdentifierValueDaoImpl";
    @NonNull
    private final Sql sql;

    /**
     * Constructor.
     *
     * @param db        Database Access
     * @param linkTable the link table between entity and Identifiers
     * @param fk        the foreign key {@code DBKey.FK_*} between the tables
     */
    public IdentifierValueDaoImpl(@NonNull final SynchronizedDb db,
                                  @NonNull final TableDefinition linkTable,
                                  @NonNull final String fk) {
        super(db, TAG);
        sql = new Sql(linkTable, fk);
    }

    @Override
    public void insertOrUpdate(@NonNull final Identifier.EntityType entityType,
                               @IntRange(from = 1) final long fkId,
                               @NonNull final Collection<Identifier.Value> list)
            throws SQLException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        pruneList(list);

        // Ensure all Identifiers exist in DB so they all have a valid ID
        final Map<Long, String> targetLinkStates = new HashMap<>(list.size());
        for (final Identifier.Value iv : list) {
            @Nullable
            Identifier identifier = find(iv.getKey(), entityType).orElse(null);
            // Create if needed
            if (identifier == null) {
                identifier = new Identifier(iv.getKey(), entityType);
                insert(identifier);
            }
            targetLinkStates.put(identifier.getId(), iv.getSid());
        }

        // Fetch current link states (Identifier ID -> SID value)
        final Map<Long, String> currentLinkStates = new HashMap<>();
        try (Cursor cursor = db.rawQuery(sql.FETCH_CURRENT_LINKS,
                                         new String[]{String.valueOf(fkId)})) {
            while (cursor.moveToNext()) {
                currentLinkStates.put(cursor.getLong(0), cursor.getString(1));
            }
        }

        // Check if any links or SID values have changed
        boolean needsResync = currentLinkStates.size() != list.size();
        if (!needsResync) {
            for (final Map.Entry<Long, String> entry : targetLinkStates.entrySet()) {
                final String currentSid = currentLinkStates.get(entry.getKey());
                if (currentSid == null || !currentSid.equals(entry.getValue())) {
                    needsResync = true;
                    break;
                }
            }
        }

        // If no links or SID values changed, we're done
        if (!needsResync) {
            return;
        }

        // Wipe old links ONLY if previous links actually existed
        if (!currentLinkStates.isEmpty()) {
            deleteAllLinks(fkId);
        }

        // If there is nothing to insert, we're done
        if (list.isEmpty()) {
            return;
        }

        // Insert the new ones.
        try (SynchronizedStatement stmt = db.compileStatement(sql.INSERT_LINK)) {
            for (final Map.Entry<Long, String> entry : targetLinkStates.entrySet()) {
                stmt.bindLong(1, fkId);
                stmt.bindLong(2, entry.getKey());
                stmt.bindString(3, entry.getValue());

                stmt.executeInsert(() -> "insert FK-Identifier");
            }
        }
    }

    private void deleteAllLinks(@IntRange(from = 1) final long fkId) {
        try (SynchronizedStatement stmt = db.compileStatement(sql.DELETE_LINK_BY_FK)) {
            stmt.bindLong(1, fkId);
            stmt.executeUpdateDelete(null);
        }
    }

    @Override
    public int countLinks(@NonNull final Identifier identifier) {
        try (SynchronizedStatement stmt = db.compileStatement(sql.COUNT_FK)) {
            stmt.bindLong(1, identifier.getId());
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @NonNull
    @Override
    public List<Identifier.Value> getByFkId(@IntRange(from = 1) final long fkId) {
        final List<Identifier.Value> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql.FIND_BY_LINK_ID,
                                         new String[]{String.valueOf(fkId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Identifier.Value(
                        rowData.getString(DBKey.IDENTIFIERS.KEY),
                        rowData.getString(DBKey.IDENTIFIERS.SID)));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Optional<String> findSid(@NonNull final String key,
                                    @IntRange(from = 1) final long fkId) {

        try (SynchronizedStatement stmt = db.compileStatement(
                sql.FIND_SID_BY_FK_AND_IDENTIFIER_KEY)) {
            stmt.bindLong(1, fkId);
            stmt.bindString(2, key);

            final String sid = stmt.simpleQueryForStringOrNull();
            // null check sure... the rest is paranoia
            if (sid != null && !sid.isEmpty() && !"0".equals(sid)) {
                return Optional.of(sid);
            }
        }
        return Optional.empty();
    }

    @Override
    @NonNull
    public Optional<Long> findIdentifierOwnerId(@NonNull final String key,
                                                @NonNull final String sid) {
        try (SynchronizedStatement stmt = db.compileStatement(
                sql.FIND_FK_BY_IDENTIFIER_KEY_AND_SID)) {
            stmt.bindString(1, key);
            stmt.bindString(2, sid);
            final long id = stmt.simpleQueryForLongOrZero();
            return id == 0 ? Optional.empty() : Optional.of(id);
        }
    }

    @SuppressWarnings({"NonConstantFieldWithUpperCaseName", "CheckStyle"})
    private static final class Sql {

        final String COUNT_FK;

        final String FIND_FK_BY_IDENTIFIER_KEY_AND_SID;

        final String FIND_BY_LINK_ID;

        final String FIND_SID_BY_FK_AND_IDENTIFIER_KEY;

        final String FETCH_CURRENT_LINKS;

        /** Insert the link between a {@link Book} or {@link Author} and an {@link Identifier}. */
        final String INSERT_LINK;

        /**
         * Delete the link between a {@link Book} or {@link Author} and an {@link Identifier}.
         * <p>
         * This is done when an FK is updated; first delete all links, then re-create them.
         */
        final String DELETE_LINK_BY_FK;

        Sql(@NonNull final TableDefinition linkTable,
            @NonNull final String fk) {

            COUNT_FK =
                    SELECT_COUNT_FROM_ + linkTable.as()
                    + _WHERE_ + linkTable.dot(DBKey.FK_IDENTIFIER) + "=?";

            FIND_FK_BY_IDENTIFIER_KEY_AND_SID =
                    SELECT_ + fk
                    + _FROM_ + linkTable.startJoin(TBL_IDENTIFIERS)
                    + _WHERE_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY) + "=?"
                    + _AND_ + linkTable.dot(DBKey.IDENTIFIERS.SID) + "=?";

            FIND_SID_BY_FK_AND_IDENTIFIER_KEY =
                    SELECT_ + linkTable.dotAs(DBKey.IDENTIFIERS.SID)
                    + _FROM_ + linkTable.startJoin(TBL_IDENTIFIERS)
                    + _WHERE_ + linkTable.dot(fk) + "=?"
                    + _AND_ + TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY) + "=?";

            FIND_BY_LINK_ID =
                    SELECT_ + TBL_IDENTIFIERS.dotAs(DBKey.IDENTIFIERS.KEY)
                    + ',' + linkTable.dotAs(DBKey.IDENTIFIERS.SID)
                    + _FROM_ + linkTable.startJoin(TBL_IDENTIFIERS)
                    + _WHERE_ + linkTable.dot(fk) + "=?";

            FETCH_CURRENT_LINKS =
                    SELECT_ + DBKey.FK_IDENTIFIER + ',' + DBKey.IDENTIFIERS.SID
                    + _FROM_ + linkTable.getName()
                    + _WHERE_ + fk + "=?";

            INSERT_LINK =
                    INSERT_INTO_ + linkTable.getName()
                    + '(' + fk
                    + ',' + DBKey.FK_IDENTIFIER
                    + ',' + DBKey.IDENTIFIERS.SID
                    + ") VALUES(?,?,?)";

            DELETE_LINK_BY_FK =
                    DELETE_FROM_ + linkTable.getName()
                    + _WHERE_ + fk + "=?";
        }
    }
}
