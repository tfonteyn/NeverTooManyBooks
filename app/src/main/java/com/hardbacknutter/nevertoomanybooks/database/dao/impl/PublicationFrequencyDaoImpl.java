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
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublicationFrequencyDao;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

public class PublicationFrequencyDaoImpl
        extends BaseDaoImpl
        implements PublicationFrequencyDao {

    /** Log tag. */
    private static final String TAG = "PubFrequencyDaoImpl";

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public PublicationFrequencyDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    public boolean setFrequency(@NonNull final Series series) {
        @Nullable
        final PublicationFrequency frequency = series.getPublicationFrequency();

        if (frequency == null) {
            return delete(series);
        }

        // Do NOT store when unknown, just delete it
        // This is paranoia, as the series should in theory never contain an unknown value
        if (frequency.getType() == PublicationFrequency.Type.Unknown) {
            return delete(series);
        }

        return insertOrUpdate(series.getId(), frequency);
    }

    private boolean insertOrUpdate(@IntRange(from = 1) final long seriesId,
                                   @NonNull final PublicationFrequency frequency) {
        final Optional<PublicationFrequency> current = findBySeriesId(seriesId);
        if (current.isEmpty()) {
            return insert(seriesId, frequency);

        } else if (!frequency.equals(current.get())) {
            return update(seriesId, frequency);
        }
        return false;
    }

    private boolean insert(@IntRange(from = 1) final long seriesId,
                           @NonNull final PublicationFrequency frequency) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindLong(1, seriesId);
            stmt.bindLong(2, frequency.getType().getId());
            stmt.bindLong(3, frequency.getCadence());
            stmt.bindBoolean(4, frequency.isOrdinal());
            return stmt.executeInsert(null) > 0;
        }
    }

    private boolean update(@IntRange(from = 1) final long seriesId,
                           @NonNull final PublicationFrequency frequency) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindLong(1, frequency.getType().getId());
            stmt.bindLong(2, frequency.getCadence());
            stmt.bindBoolean(3, frequency.isOrdinal());

            stmt.bindLong(4, seriesId);
            return stmt.executeUpdateDelete(null) > 0;
        }
    }

    @NonNull
    private Optional<PublicationFrequency> findBySeriesId(@IntRange(from = 1) final long seriesId) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_SERIES_ID,
                                         new String[]{String.valueOf(seriesId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return Optional.of(new PublicationFrequency(rowData));
            }
        }
        return Optional.empty();
    }

    private boolean delete(@NonNull final Series series) {
        if (delete(series.getId())) {
            series.setPublicationFrequency(null);
            return true;
        }
        return false;
    }

    private boolean delete(@IntRange(from = 1) final long seriesId) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_SERIES_ID)) {
            stmt.bindLong(1, seriesId);
            rowsAffected = stmt.executeUpdateDelete(null);
        }
        return rowsAffected > 0;
    }

    private static final class Sql {

        static final String FIND_BY_SERIES_ID =
                SELECT_ + DBKey.PUBLICATION_FREQUENCY.TYPE
                + ',' + DBKey.PUBLICATION_FREQUENCY.CADENCE
                + ',' + DBKey.PUBLICATION_FREQUENCY.IS_ORDINAL
                + _FROM_ + DBDefinitions.TBL_SERIES_PUBLICATION_FREQUENCY.getName()
                + _WHERE_ + DBKey.FK_SERIES + "=?";

        static final String INSERT =
                INSERT_INTO_ + DBDefinitions.TBL_SERIES_PUBLICATION_FREQUENCY.getName()
                + '(' + DBKey.FK_SERIES
                + ',' + DBKey.PUBLICATION_FREQUENCY.TYPE
                + ',' + DBKey.PUBLICATION_FREQUENCY.CADENCE
                + ',' + DBKey.PUBLICATION_FREQUENCY.IS_ORDINAL
                + ") VALUES (?,?,?,?)";

        static final String UPDATE =
                UPDATE_ + DBDefinitions.TBL_SERIES_PUBLICATION_FREQUENCY.getName()
                + _SET_ + DBKey.PUBLICATION_FREQUENCY.TYPE + "=?"
                + ',' + DBKey.PUBLICATION_FREQUENCY.CADENCE + "=?"
                + ',' + DBKey.PUBLICATION_FREQUENCY.IS_ORDINAL + "=?"
                + _WHERE_ + DBKey.FK_SERIES + "=?";

        static final String DELETE_BY_SERIES_ID =
                DELETE_FROM_ + DBDefinitions.TBL_SERIES_PUBLICATION_FREQUENCY.getName()
                + _WHERE_ + DBKey.FK_SERIES + "=?";
    }
}
