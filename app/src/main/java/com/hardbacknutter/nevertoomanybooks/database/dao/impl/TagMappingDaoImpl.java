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
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagMappingDao;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAG_MAPPINGS;

/**
 * The external tag names are stored as all-lowercase.
 * Hence, comparing/lookups must likewise be done in lowercase.
 */
public class TagMappingDaoImpl
        extends BaseDaoImpl
        implements TagMappingDao {

    private static final String TAG = "TagMappingDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public TagMappingDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @NonNull
    private static List<Pair<String, Set<String>>> createInitialList() {
        return List.of(
                // These are just some examples to give the user some ideas

                // unify what are identical terms
                new Pair<>("science-fiction", Set.of("Science Fiction")),
                new Pair<>("Sciencefiction", Set.of("Science Fiction")),
                // splitting of combinations into multiple tags
                new Pair<>("science fiction fantasy", Set.of("Science Fiction", "Fantasy")),
                new Pair<>("Science Fiction & Fantasy", Set.of("Science Fiction", "Fantasy"))
                // and so on... up to the user to set up theirs obviously
        );
    }

    /**
     * Run at <strong>installation</strong> time to add the predefined mappings to the database.
     *
     * @param db Underlying database
     *
     * @throws SQLException on failure
     */
    public static void onPostCreate(@NonNull final SQLiteDatabase db) {
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(Sql.INSERT))) {
            for (final Pair<String, Set<String>> pair : createInitialList()) {
                doInsert(pair.first, pair.second, stmt);
            }
        } catch (@NonNull final SQLException e) {
            // log, but just rethrow insert errors... we're in a real mess now
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        }
    }

    /**
     * Insert the mapping.
     * <strong>Exception handling and {@code -1} returns MUST be done by the caller</strong>
     *
     * @param tag      to insert
     * @param mappings to insert
     * @param stmt     statement to run
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    private static long doInsert(@NonNull final String tag,
                                 @NonNull final Set<String> mappings,
                                 @NonNull final ExtSQLiteStatement stmt) {

        final String mapped = TagMapping.encodeMappingString(mappings);
        stmt.bindString(1, tag);
        stmt.bindString(2, mapped);
        return stmt.executeInsert();
    }

    @NonNull
    @Override
    public Optional<TagMapping> findByName(@NonNull final TagMapping mapping) {
        final String name = mapping.getTagName();

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new TagMapping(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    public void fixId(@NonNull final TagMapping mapping) {
        final long found = findByName(mapping)
                .map(TagMapping::getId).orElse(0L);
        mapping.setId(found);
    }

    @NonNull
    @Override
    public List<TagMapping> getAll() {
        final List<TagMapping> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.GET_ALL, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new TagMapping(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @IntRange(from = 1)
    @Override
    public long insert(@NonNull final TagMapping mapping)
            throws DaoInsertException {
        final long iId;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            iId = doInsert(mapping.getTagName(), mapping.getMappings(), stmt);
        }
        if (iId != -1) {
            mapping.setId(iId);
            return iId;
        }
        // The insert failed with -1
        throw new DaoInsertException(ERROR_INSERT_FROM + mapping);
    }

    @Override
    public void update(@NonNull final TagMapping mapping)
            throws DaoUpdateException {

        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, mapping.getTagName());
            stmt.bindString(2, TagMapping.encodeMappingString(mapping.getMappings()));

            stmt.bindLong(3, mapping.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            return;
        }

        throw new DaoUpdateException(ERROR_UPDATE_FROM + mapping);

    }

    @Override
    public boolean delete(@NonNull final TagMapping mapping) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, mapping.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }
        return rowsAffected > 0;
    }

    private static final class Sql {

        static final String FIND_BY_NAME =
                SELECT_ + DBKey.PK_ID
                + ',' + DBKey.TAGS.TAG
                + ',' + DBKey.TAGS.TAG_MAPPING
                + _FROM_ + TBL_TAG_MAPPINGS.getName()
                + _WHERE_ + DBKey.TAGS.TAG + "=?";

        static final String INSERT =
                INSERT_INTO_ + TBL_TAG_MAPPINGS.getName()
                + '(' + DBKey.TAGS.TAG
                + ',' + DBKey.TAGS.TAG_MAPPING
                + ") VALUES(?,?)";

        static final String UPDATE =
                UPDATE_ + TBL_TAG_MAPPINGS.getName()
                + _SET_ + DBKey.TAGS.TAG + "=?"
                + ',' + DBKey.TAGS.TAG_MAPPING + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_TAG_MAPPINGS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        static final String GET_ALL =
                SELECT_ + DBKey.PK_ID
                + ',' + DBKey.TAGS.TAG
                + ',' + DBKey.TAGS.TAG_MAPPING
                + _FROM_ + TBL_TAG_MAPPINGS.getName()
                + _ORDER_BY_ + DBKey.TAGS.TAG;
    }
}
