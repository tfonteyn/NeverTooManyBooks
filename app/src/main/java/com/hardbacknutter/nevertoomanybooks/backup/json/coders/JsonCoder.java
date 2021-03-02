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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public interface JsonCoder<T> {

    String TAG_APPLICATION_ROOT = "NeverTooManyBooks";

    @NonNull
    JSONObject encode(@NonNull T element)
            throws JSONException;

    @NonNull
    default JSONArray encode(@NonNull final List<T> list)
            throws JSONException {
        final List<JSONObject> result = new ArrayList<>();
        for (final T element : list) {
            result.add(encode(element));
        }
        return new JSONArray(result);
    }

    @NonNull
    T decode(@NonNull JSONObject data)
            throws JSONException;

    @NonNull
    default ArrayList<T> decode(@NonNull final JSONArray elements)
            throws JSONException {
        final ArrayList<T> list = new ArrayList<>();
        for (int i = 0; i < elements.length(); i++) {
            list.add(decode((JSONObject) elements.get(i)));
        }
        return list;
    }
}
