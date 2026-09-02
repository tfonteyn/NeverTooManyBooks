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
    public void setFrequency(@NonNull final Series series) {
        @Nullable
        final PublicationFrequency frequency = series.getPublicationFrequency();

        // Do NOT store when Unknown, just delete it
        if (frequency == null || frequency.getType() == PublicationFrequency.Type.Unknown) {
            deleteFrom(series);
            return;
        }

        insertOrUpdate(series.getId(), frequency);
    }

    private void insertOrUpdate(@IntRange(from = 1) final long seriesId,
                                @NonNull final PublicationFrequency frequency) {
        final PublicationFrequency current = findBySeriesId(seriesId);
        if (current == null) {
            insert(seriesId, frequency);
        } else if (!frequency.equals(current)) {
            update(seriesId, frequency);
        }
    }

    @Nullable
    private PublicationFrequency findBySeriesId(@IntRange(from = 1) final long seriesId) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_SERIES_ID,
                                         new String[]{String.valueOf(seriesId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return new PublicationFrequency(rowData);
            }
        }
        return null;
    }

    private void insert(@IntRange(from = 1) final long seriesId,
                           @NonNull final PublicationFrequency frequency) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindLong(1, seriesId);
            stmt.bindLong(2, frequency.getType().getId());
            stmt.bindLong(3, frequency.getCadence());
            stmt.bindBoolean(4, frequency.isOrdinal());
            stmt.executeInsert(null);
        }
    }

    private void update(@IntRange(from = 1) final long seriesId,
                        @NonNull final PublicationFrequency frequency) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindLong(1, frequency.getType().getId());
            stmt.bindLong(2, frequency.getCadence());
            stmt.bindBoolean(3, frequency.isOrdinal());

            stmt.bindLong(4, seriesId);
            stmt.executeUpdateDelete(null);
        }
    }

    private void deleteFrom(@NonNull final Series series) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_SERIES_ID)) {
            stmt.bindLong(1, series.getId());
            stmt.executeUpdateDelete(null);
        }
        series.setPublicationFrequency(null);
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
