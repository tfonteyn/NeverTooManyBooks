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
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        out.put(DBKey.IDENTIFIERS.SID, element.getSid());
        return out;
    }

    @Override
    @Nullable
    public Identifier.Value decode(@NonNull final JSONObject data)
            throws JSONException {
        final String key = data.getString(DBKey.IDENTIFIERS.KEY);
        final String value = data.getString(DBKey.IDENTIFIERS.SID);

        if (Identifier.SID_BNF.equals(key) && value.length() > 8) {
            return decodeBnfLegacyFormat(value);
        }
        return new Identifier.Value(key, value);
    }

    /**
     * The internal storage format of the BnF sid has changed in app version 8.0.0.
     * Instead of storing the "cbXXXXXXXXc" values, we now store the raw "XXXXXXXX" value.
     *
     * @param value to decode
     *
     * @return decoded value, or {@code null} when discarded
     */
    @Nullable
    private Identifier.Value decodeBnfLegacyFormat(@NonNull final CharSequence value) {
        final Pattern p = Pattern.compile("^cb(\\d{8}).?");
        final Matcher matcher = p.matcher(value);
        if (matcher.find()) {
            final String s = matcher.group(1);
            if (s != null) {
                return new Identifier.Value(Identifier.SID_BNF, s);
            }
        }
        // discard - better NO value than an invalid value
        return null;
    }
}
