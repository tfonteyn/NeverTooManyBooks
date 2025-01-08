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

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class IdentifierCoder
        implements JsonCoder<Pair<String, String>> {

    private final Set<String> keys;

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public IdentifierCoder() {
        keys = ServiceLocator.getInstance()
                             .getIdentifierDao()
                             .getAll()
                             .stream()
                             .collect(Collectors.toMap(
                                     Identifier::getKey,
                                     Function.identity())).keySet();
    }

    boolean contains(@NonNull final String key) {
        return keys.contains(key);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Pair<String, String> element)
            throws JSONException {
        final JSONObject out = new JSONObject();
        out.put(DBKey.IDENT_KEY, element.first);
        out.put(DBKey.IDENT_SID, element.second);
        return out;
    }

    @Override
    @NonNull
    public Pair<String, String> decode(@NonNull final JSONObject data)
            throws JSONException {
        return new Pair<>(data.getString(DBKey.IDENT_KEY),
                          data.getString(DBKey.IDENT_SID));
    }
}
