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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.database.ExtSQLiteStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreCustomFieldDaoImpl;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;

class CalibreMigration {

    private static final String SELECT_1_FROM_ = "SELECT 1 FROM ";
    private static final String _WHERE_ = " WHERE ";

    @NonNull
    private final SQLiteDatabase db;

    CalibreMigration(@NonNull final SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * Check if there is already a row (table/column) with the given value.
     *
     * @param value to look for
     *
     * @return flag
     */
    private boolean isPresent(@NonNull final String value) {
        try (SQLiteStatement stmt = db.compileStatement(
                SELECT_1_FROM_ + DBDefinitions.TBL_CALIBRE_CUSTOM_FIELDS.getName()
                + _WHERE_ + DBKey.CALIBRE.CUSTOM_FIELD_NAME + "=?")) {
            stmt.bindString(1, value);
            return 1 == stmt.simpleQueryForLong();
        } catch (@NonNull final SQLiteDoneException ignore) {
            // ignore
        }
        return false;
    }

    /**
     * Add the given {@link CalibreCustomField}.
     * Silently skip if it already exists.
     *
     * @param calibreKey The Calibre field name
     * @param type       The Calibre field type
     * @param dbKey      The local {@link DBKey} to which the field is to be mapped
     */
    void add(@NonNull final String calibreKey,
             @NonNull @CalibreCustomField.Type final String type,
             @NonNull final String dbKey) {
        // key must be unique
        if (isPresent(calibreKey)) {
            return;
        }

        final CalibreCustomField field = new CalibreCustomField(calibreKey, type, dbKey);
        try (ExtSQLiteStatement stmt = new ExtSQLiteStatement(
                db.compileStatement(CalibreCustomFieldDaoImpl.Sql.INSERT))) {
            CalibreCustomFieldDaoImpl.doInsert(field, stmt);
        }
    }
}
