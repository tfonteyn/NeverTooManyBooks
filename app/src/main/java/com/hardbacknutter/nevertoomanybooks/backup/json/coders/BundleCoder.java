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

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Minimalistic implementation which only supports
 * {@link String}, {@link Number} and {@link Boolean}.
 * <p>
 * {@code null} values are <strong>skipped</strong>.
 */
public class BundleCoder
        implements JsonCoder<Bundle> {

    @NonNull
    @Override
    public JSONObject encode(@NonNull final Bundle element)
            throws JSONException {
        final JSONObject out = new JSONObject();
        for (final String key : element.keySet()) {
            final Object o = element.get(key);
            if (o instanceof String
                || o instanceof Number
                || o instanceof Boolean) {
                out.put(key, o);

            } else if (o != null) {
                throw new IllegalArgumentException("type not supported: " + o);
            }
        }
        return out;
    }

    @NonNull
    @Override
    public Bundle decode(@NonNull final JSONObject data)
            throws JSONException {
        final Bundle bundle = new Bundle();
        final Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final Object o = data.get(key);
            if (o instanceof String) {
                bundle.putString(key, (String) o);

            } else if (o instanceof Long) {
                bundle.putLong(key, (long) o);
            } else if (o instanceof Integer) {
                bundle.putInt(key, (int) o);

            } else if (o instanceof Double) {
                bundle.putDouble(key, (long) o);
            } else if (o instanceof Float) {
                bundle.putFloat(key, (float) o);

            } else if (o instanceof Boolean) {
                bundle.putBoolean(key, (boolean) o);

            } else {
                throw new IllegalArgumentException("type not supported: " + o);
            }
        }
        return bundle;
    }
}
