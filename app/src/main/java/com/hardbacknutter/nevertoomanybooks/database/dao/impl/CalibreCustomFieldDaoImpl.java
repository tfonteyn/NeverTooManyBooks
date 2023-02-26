/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;

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

    private static final String BASE_SELECT =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.CALIBRE_CUSTOM_FIELD_NAME
            + ',' + DBKey.CALIBRE_CUSTOM_FIELD_TYPE
            + ',' + DBKey.CALIBRE_CUSTOM_FIELD_MAPPING
            + _FROM_ + TBL_CALIBRE_CUSTOM_FIELDS.getName();

    private static final String SELECT_BY_ID =
            BASE_SELECT + _WHERE_ + DBKey.PK_ID + "=?";


    private static final String SELECT_ALL =
            BASE_SELECT + _ORDER_BY_ + DBKey.CALIBRE_CUSTOM_FIELD_NAME + _COLLATION;


    private static final String INSERT =
            INSERT_INTO_ + TBL_CALIBRE_CUSTOM_FIELDS.getName()
            + '(' + DBKey.CALIBRE_CUSTOM_FIELD_NAME
            + ',' + DBKey.CALIBRE_CUSTOM_FIELD_TYPE
            + ',' + DBKey.CALIBRE_CUSTOM_FIELD_MAPPING
            + ") VALUES(?,?,?)";

    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_CALIBRE_CUSTOM_FIELDS.getName()
            + _WHERE_ + DBKey.PK_ID + "=?";

    /**
     * Constructor.
     */
    public CalibreCustomFieldDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Run at installation time a set of default fields.
     *
     * @param db Database Access
     */
    public static void onPostCreate(@NonNull final SQLiteDatabase db) {
        final String[][] all = {
                {"#read", CalibreCustomField.TYPE_BOOL, DBKey.READ__BOOL},

                {"#read_start", CalibreCustomField.TYPE_DATETIME, DBKey.READ_START__DATE},
                {"#read_end", CalibreCustomField.TYPE_DATETIME, DBKey.READ_END__DATE},
                {"#date_read", CalibreCustomField.TYPE_DATETIME, DBKey.READ_END__DATE},

                {"#notes", CalibreCustomField.TYPE_TEXT, DBKey.PERSONAL_NOTES},
                {"#notes", CalibreCustomField.TYPE_COMMENTS, DBKey.PERSONAL_NOTES}
        };

        final ContentValues cv = new ContentValues();
        for (final String[] row : all) {
            cv.clear();
            cv.put(DBKey.CALIBRE_CUSTOM_FIELD_NAME, row[0]);
            cv.put(DBKey.CALIBRE_CUSTOM_FIELD_TYPE, row[1]);
            cv.put(DBKey.CALIBRE_CUSTOM_FIELD_MAPPING, row[2]);
            db.insert(TBL_CALIBRE_CUSTOM_FIELDS.getName(), null, cv);
        }
    }

    @Override
    public void fixId(@NonNull final CalibreCustomField calibreCustomField) {
        final long id = find(calibreCustomField);
        calibreCustomField.setId(id);
    }

    @Override
    public long insert(@NonNull final CalibreCustomField calibreCustomField) {
        try (SynchronizedStatement stmt = db.compileStatement(INSERT)) {
            stmt.bindString(1, calibreCustomField.getCalibreKey());
            stmt.bindString(2, calibreCustomField.getType());
            stmt.bindString(3, calibreCustomField.getDbKey());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                calibreCustomField.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final CalibreCustomField calibreCustomField) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.CALIBRE_CUSTOM_FIELD_NAME, calibreCustomField.getCalibreKey());
        cv.put(DBKey.CALIBRE_CUSTOM_FIELD_TYPE, calibreCustomField.getType());
        cv.put(DBKey.CALIBRE_CUSTOM_FIELD_MAPPING, calibreCustomField.getDbKey());

        final int rowsAffected = db.update(TBL_CALIBRE_CUSTOM_FIELDS.getName(), cv,
                                           DBKey.PK_ID + "=?",
                                           new String[]{String.valueOf(
                                                   calibreCustomField.getId())});
        return 0 < rowsAffected;
    }

    @Override
    public boolean delete(@NonNull final CalibreCustomField calibreCustomField) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = db.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, calibreCustomField.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            calibreCustomField.setId(0);
        }
        return rowsAffected == 1;
    }

    @NonNull
    @Override
    public ArrayList<CalibreCustomField> getCustomFields() {
        final ArrayList<CalibreCustomField> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_ALL, null)) {
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

    private long find(@NonNull final CalibreCustomField calibreCustomField) {
        try (SynchronizedStatement stmt = db.compileStatement(SELECT_BY_ID)) {
            stmt.bindLong(1, calibreCustomField.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }
}
