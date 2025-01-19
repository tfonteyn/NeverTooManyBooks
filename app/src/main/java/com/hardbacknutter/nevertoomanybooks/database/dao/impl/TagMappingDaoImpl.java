/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.util.Pair;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagMappingDao;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAG_MAPPINGS;

/**
 * The external tag names are stored as all-lowercase.
 * Hence comparing/lookups must likewise be done in lowercase.
 */
public class TagMappingDaoImpl
        extends BaseDaoImpl
        implements TagMappingDao {

    private static final String TAG = "TagMappingDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";

    private static final Pattern SPLIT = Pattern.compile("[^\\\\],");

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public TagMappingDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @NonNull
    private static List<Pair<String, Set<String>>> createInitialList() {
        return List.of(
                // These are just some examples to give the user some ideas
                // Note the keys must be all-lowercase here.
                new Pair<>("science-fiction", Set.of("Science Fiction")),
                new Pair<>("sciencefiction", Set.of("Science Fiction")),
                new Pair<>("science fiction fantasy", Set.of("Science Fiction", "Fantasy")),
                new Pair<>("science fiction & fantasy", Set.of("Science Fiction", "Fantasy"))
        );
    }

    public static void onPostCreate(@NonNull final Context context,
                                    @NonNull final SQLiteDatabase db) {
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

    private static long doInsert(@NonNull final String tag,
                                 @NonNull final Set<String> mappings,
                                 @NonNull final ExtSQLiteStatement stmt) {

        final String mapped = mappings
                .stream()
                .map(s -> s.replace(",", "\\,"))
                .collect(Collectors.joining(","));
        stmt.bindString(1, tag);
        stmt.bindString(2, mapped);
        return stmt.executeInsert();
    }

    @NonNull
    @Override
    public Map<String, Set<String>> getAll() {
        final Map<String, Set<String>> map = new HashMap<>();
        try (Cursor cursor = db.rawQuery(Sql.GET_ALL, null)) {
            while (cursor.moveToNext()) {
                final String tag = cursor.getString(0);
                final String[] split = SPLIT.split(cursor.getString(1));
                map.put(tag, Set.of(split));
            }
        }
        return map;
    }

    @IntRange(from = 1)
    @Override
    public long insert(@NonNull final String extTag,
                       @NonNull final Set<String> mappings)
            throws DaoInsertException {
        final long iId;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            iId = doInsert(extTag, mappings, stmt);
        }
        if (iId != -1) {
            return iId;
        }
        // The insert failed with -1
        throw new DaoInsertException(ERROR_INSERT_FROM + extTag + "=" + mappings);
    }

    @Override
    public boolean delete(@NonNull final String extTag) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_TAG)) {
            stmt.bindString(1, extTag);
            rowsAffected = stmt.executeUpdateDelete();
        }
        return rowsAffected > 0;
    }

    private static final class Sql {
        static final String DELETE_BY_TAG =
                DELETE_FROM_ + TBL_TAG_MAPPINGS.getName() + _WHERE_ + DBKey.TAG + "=?";

        static final String INSERT =
                INSERT_INTO_ + TBL_TAG_MAPPINGS.getName()
                + '(' + DBKey.TAG
                + ',' + DBKey.TAG_MAPPING
                + ") VALUES(?,?)";

        static final String GET_ALL =
                SELECT_ + DBKey.TAG + ',' + DBKey.TAG_MAPPING
                + _FROM_ + TBL_TAG_MAPPINGS.getName()
                + _ORDER_BY_ + DBKey.TAG;
    }
}
