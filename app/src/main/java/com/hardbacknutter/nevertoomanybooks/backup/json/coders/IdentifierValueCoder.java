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

package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class IdentifierValueCoder
        implements JsonCoder<Identifier.Value> {

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Identifier.Value element)
            throws JSONException {
        final JSONObject out = new JSONObject();
        out.put(DBKey.IDENTIFIERS.KEY, element.getKey());
        out.put(DBKey.BOOK_IDENTIFIER_SID, element.getSid());
        return out;
    }

    @Override
    @NonNull
    public Identifier.Value decode(@NonNull final JSONObject data)
            throws JSONException {
        return new Identifier.Value(data.getString(DBKey.IDENTIFIERS.KEY),
                                    data.getString(DBKey.BOOK_IDENTIFIER_SID));
    }
}
