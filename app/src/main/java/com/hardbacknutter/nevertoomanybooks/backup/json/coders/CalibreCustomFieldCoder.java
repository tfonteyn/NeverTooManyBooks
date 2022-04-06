/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class CalibreCustomFieldCoder
        implements JsonCoder<CalibreCustomField> {

    @Override
    @NonNull
    public JSONObject encode(@NonNull final CalibreCustomField field)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.PK_ID, field.getId());
        out.put(DBKey.CALIBRE_CUSTOM_FIELD_NAME, field.calibreKey);
        out.put(DBKey.CALIBRE_CUSTOM_FIELD_TYPE, field.type);
        out.put(DBKey.CALIBRE_CUSTOM_FIELD_MAPPING, field.dbKey);
        return out;
    }

    @Override
    @NonNull
    public CalibreCustomField decode(@NonNull final JSONObject data)
            throws JSONException {

        final CalibreCustomField field = new CalibreCustomField(
                data.getString(DBKey.CALIBRE_CUSTOM_FIELD_NAME),
                data.getString(DBKey.CALIBRE_CUSTOM_FIELD_TYPE),
                data.getString(DBKey.CALIBRE_CUSTOM_FIELD_MAPPING));
        field.setId(data.getLong(DBKey.PK_ID));
        return field;
    }
}
