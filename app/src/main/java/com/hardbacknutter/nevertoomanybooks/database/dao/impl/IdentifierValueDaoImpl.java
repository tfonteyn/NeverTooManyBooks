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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
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
     * @param db        Underlying database
     * @param linkTable either the Author or the Book link table
     * @param fk        either the Author or the Book {@code DBKey.FK_*}
     */
    public IdentifierValueDaoImpl(@NonNull final SynchronizedDb db,
                                  @NonNull final TableDefinition linkTable,
                                  @NonNull final String fk) {
        super(db, TAG);
        sql = new Sql(linkTable, fk);
    }

    @Override
    public void insertOrUpdate(@IntRange(from = 1) final long fkId,
                               @NonNull final Collection<Identifier.Value> list)
            throws DaoInsertException, DaoUpdateException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        pruneList(list);

        // Just delete all current links
        try (SynchronizedStatement stmt1 = db.compileStatement(sql.DELETE_LINK_BY_FK)) {
            stmt1.bindLong(1, fkId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        try (SynchronizedStatement stmt = db.compileStatement(sql.INSERT_LINK)) {
            for (final Identifier.Value iv : list) {

                Identifier identifier = findByKey(iv.getKey()).orElse(null);
                if (identifier == null) {
                    // We do NOT want to speculate it might be TYPE_LONG!
                    // See docs on the Identifier class for usage.
                    identifier = new Identifier(iv.getKey());
                    insert(identifier);
                }
                stmt.bindLong(1, fkId);
                stmt.bindLong(2, identifier.getId());
                stmt.bindString(3, iv.getSid());
                if (stmt.executeInsert() == -1) {
                    throw new DaoInsertException("insert FK-Identifier");
                }
            }
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
    public Optional<Long> findFkId(@NonNull final String key,
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
                    SELECT_COUNT_FROM_ + linkTable.ref()
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
                    IdentifierDaoImpl.Sql.SELECT_ALL
                    + ',' + linkTable.dotAs(DBKey.IDENTIFIERS.SID)
                    + _FROM_ + linkTable.startJoin(TBL_IDENTIFIERS)
                    + _WHERE_ + linkTable.dot(fk) + "=?";

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
