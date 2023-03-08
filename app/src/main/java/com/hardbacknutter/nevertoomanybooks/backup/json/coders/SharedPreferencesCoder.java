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

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.Map;

import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class SharedPreferencesCoder
        implements JsonCoder<SharedPreferences> {

    @Nullable
    private final SharedPreferences out;

    /**
     * Constructor for encoding.
     */
    public SharedPreferencesCoder() {
        out = null;
    }

    /**
     * Constructor for decoding.
     *
     * @param out the SharedPreferences to write to
     */
    public SharedPreferencesCoder(@NonNull final SharedPreferences out) {
        this.out = out;
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final SharedPreferences element)
            throws JSONException {

        final JSONObject encoded = new JSONObject();
        for (final Map.Entry<String, ?> source : element.getAll().entrySet()) {
            final String key = source.getKey();
            // skip the acra settings
            if (!key.startsWith("acra")) {
                final Object value = source.getValue();
                if (value != null) {
                    encoded.put(key, value);
                }
            }
        }

        return encoded;
    }

    @NonNull
    @Override
    public SharedPreferences decode(@NonNull final JSONObject data)
            throws JSONException {
        //noinspection ConstantConditions
        final SharedPreferences.Editor ed = out.edit();

        final Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final Object o = data.get(key);
            // JSONObject Tokenizer returns Integer, Long, or Double,
            // in that order (never a float)
            if (o instanceof String) {
                ed.putString(key, (String) o);

            } else if (o instanceof Boolean) {
                ed.putBoolean(key, (boolean) o);

            } else if (o instanceof Integer) {
                ed.putInt(key, (int) o);

            } else if (o instanceof Long) {
                ed.putLong(key, (long) o);

            } else if (o instanceof Double) {
                ed.putFloat(key, (float) o);
            }
        }
        ed.apply();
        return out;
    }
}
