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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreCustomFieldDao;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_CUSTOM_FIELDS;

public class CalibreCustomFieldDaoImpl
        extends BaseDaoImpl
        implements CalibreCustomFieldDao {

    private static final String TAG = "CalibreCustomFieldDao";

    private static final String ERROR_UPDATE_FROM = "Update from\n";
    private static final String ERROR_INSERT_FROM = "Insert from\n";


    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public CalibreCustomFieldDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Run at installation time a set of default fields.
     * <p>
     * NEWTHINGS: adding a Calibre custom field
     *
     * @param db Underlying database
     *
     * @throws SQLException on any failures
     */
    public static void onPostCreate(@NonNull final SQLiteDatabase db)
        throws SQLException {
        //noinspection CheckStyle
        final String[][] all = {
                // From the built in templates:

                // SPECIAL HANDLING REQUIRED,
                {CalibreCustomField.FIELD_READ_PROGRESS,
                        CalibreCustomField.TYPE_COMPOSITE,
                        DBKey.READ_PROGRESS},

                // No special handling, it's a value "","1","2"..."5"
                // but with a distinct type.
                {"#rating", CalibreCustomField.TYPE_RATING, DBKey.RATING},


                // The below are custom fields as defined by NTMB.
                // These need to be manually defined in Calibre.
                // No special handling, these map 1:1
                {"#read", CalibreCustomField.TYPE_BOOL, DBKey.READ__BOOL},
                {"#read_start", CalibreCustomField.TYPE_DATETIME, DBKey.READ_START__DATE},
                {"#read_end", CalibreCustomField.TYPE_DATETIME, DBKey.READ_END__DATE},
                {"#date_read", CalibreCustomField.TYPE_DATETIME, DBKey.READ_END__DATE},
                // Supporting two different datatypes for the notes field
                {"#notes", CalibreCustomField.TYPE_TEXT, DBKey.PERSONAL_NOTES},
                {"#notes", CalibreCustomField.TYPE_COMMENTS, DBKey.PERSONAL_NOTES}
        };

        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(db.compileStatement(Sql.INSERT))) {
            for (final String[] row : all) {
                final CalibreCustomField field = new CalibreCustomField(row[0], row[1], row[2]);
                doInsert(field, stmt);
            }
        }
    }

    /**
     * Insert the field.
     * <strong>Exception handling and {@code -1} returns MUST be done by the caller</strong>
     *
     * @param field to insert
     * @param stmt  statement to run
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     *
     * @throws SQLException on any failures
     */
    public static long doInsert(@NonNull final CalibreCustomField field,
                                @NonNull final ExtSQLiteStatement stmt)
        throws SQLException {

        stmt.bindString(1, field.getCalibreKey());
        stmt.bindString(2, field.getType());
        stmt.bindString(3, field.getDbKey());
        return stmt.executeInsert(() -> ERROR_INSERT_FROM + field);
    }

    @Override
    public void fixId(@NonNull final CalibreCustomField field) {
        final long id = findByName(field);
        field.setId(id);
    }

    @Override
    @IntRange(from = 1)
    public long insert(@NonNull final CalibreCustomField field)
            throws DaoWriteException {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            final long iId = doInsert(field, stmt);
            field.setId(iId);
            return iId;

        } catch (@NonNull final SQLException e) {
            field.setId(0);
            throw new DaoWriteException(e);
        }
    }

    @Override
    public void update(@NonNull final CalibreCustomField field)
            throws DaoWriteException {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, field.getCalibreKey());
            stmt.bindString(2, field.getType());
            stmt.bindString(3, field.getDbKey());

            stmt.bindLong(4, field.getId());
            stmt.executeUpdateDelete(() -> ERROR_UPDATE_FROM + field);

        } catch (@NonNull final SQLException e) {
            throw new DaoWriteException(e);
        }
    }

    @Override
    public boolean delete(@NonNull final CalibreCustomField calibreCustomField) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, calibreCustomField.getId());
            rowsAffected = stmt.executeUpdateDelete(null);
        }
        if (rowsAffected > 0) {
            calibreCustomField.setId(0);
            return true;
        }
        return false;
    }

    @NonNull
    @Override
    public List<CalibreCustomField> getCustomFields() {
        final List<CalibreCustomField> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.SELECT_ALL, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final CalibreCustomField field = new CalibreCustomField(
                        rowData.getLong(DBKey.PK_ID),
                        rowData);
                list.add(field);
            }
        }
        return list;
    }

    private long findByName(@NonNull final CalibreCustomField calibreCustomField) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_BY_NAME)) {
            stmt.bindString(1, calibreCustomField.getCalibreKey());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    public static final class Sql {

        /** Insert a {@link CalibreCustomField}. */
        public static final String INSERT =
                INSERT_INTO_ + TBL_CALIBRE_CUSTOM_FIELDS.getName()
                + '(' + DBKey.CALIBRE.CUSTOM_FIELD_NAME
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_TYPE
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_MAPPING
                + ") VALUES(?,?,?)";

        static final String UPDATE =
                UPDATE_ + TBL_CALIBRE_CUSTOM_FIELDS.getName()
                + _SET_ + DBKey.CALIBRE.CUSTOM_FIELD_NAME + "=?"
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_TYPE + "=?"
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_MAPPING + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link CalibreCustomField}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_CALIBRE_CUSTOM_FIELDS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";


        static final String BASE_SELECT =
                SELECT_ + DBKey.PK_ID
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_NAME
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_TYPE
                + ',' + DBKey.CALIBRE.CUSTOM_FIELD_MAPPING
                + _FROM_ + TBL_CALIBRE_CUSTOM_FIELDS.getName();

        /** A list of all {@link CalibreCustomField}s, ordered by name. */
        static final String SELECT_ALL =
                BASE_SELECT + _ORDER_BY_ + DBKey.CALIBRE.CUSTOM_FIELD_NAME + _COLLATION;

        /**
         * Find a {@link CalibreCustomField} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String FIND_BY_NAME =
                BASE_SELECT + _WHERE_ + DBKey.CALIBRE.CUSTOM_FIELD_NAME + "=?";

        private Sql() {
        }
    }
}
