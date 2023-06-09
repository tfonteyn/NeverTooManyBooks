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

package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class DeletedBooksCoder
        implements JsonCoder<Pair<String, String>> {

    @NonNull
    @Override
    public JSONObject encode(@NonNull final Pair<String, String> element)
            throws JSONException {
        final JSONObject out = new JSONObject();
        out.put(DBKey.BOOK_UUID, element.first);
        out.put(DBKey.DATE_ADDED__UTC, element.second);
        return out;
    }

    @NonNull
    @Override
    public Pair<String, String> decode(@NonNull final JSONObject data)
            throws JSONException {
        return new Pair<>(data.getString(DBKey.BOOK_UUID),
                          data.getString(DBKey.DATE_ADDED__UTC));
    }
}
