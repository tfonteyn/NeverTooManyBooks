/*
 * @Copyright 2020 HardBackNutter
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

import org.json.JSONException;
import org.json.JSONObject;

public class SharedPreferencesCoder
        implements JsonCoder<SharedPreferences> {

    @Nullable
    private final SharedPreferences mOut;

    public SharedPreferencesCoder() {
        mOut = null;
    }

    public SharedPreferencesCoder(@NonNull final SharedPreferences out) {
        mOut = out;
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final SharedPreferences element)
            throws JSONException {

        final JSONObject out = new JSONObject();
        for (final Map.Entry<String, ?> entry : element.getAll().entrySet()) {
            final String key = entry.getKey();
            // skip the acra settings
            if (!key.startsWith("acra")) {
                final Object value = entry.getValue();
                if (value != null) {
                    out.put(key, value);
                }
            }
        }

        return out;
    }

    @NonNull
    @Override
    public SharedPreferences decode(@NonNull final JSONObject data)
            throws JSONException {
        //noinspection ConstantConditions
        final SharedPreferences.Editor ed = mOut.edit();

        final Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final Object o = data.get(key);
            if (o instanceof String) {
                ed.putString(key, (String) o);

            } else if (o instanceof Long) {
                ed.putLong(key, (long) o);
            } else if (o instanceof Integer) {
                ed.putInt(key, (int) o);

            } else if (o instanceof Float) {
                ed.putFloat(key, (long) o);

            } else if (o instanceof Boolean) {
                ed.putBoolean(key, (boolean) o);
            }
        }
        ed.apply();
        return mOut;
    }
}
