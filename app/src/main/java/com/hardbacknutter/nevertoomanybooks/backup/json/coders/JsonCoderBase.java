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
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class JsonCoderBase<T> {

    @NonNull
    public abstract JSONObject encode(@NonNull final T entity);

    @NonNull
    public abstract T decode(@NonNull final JSONObject data)
            throws JSONException;

    @NonNull
    public JSONArray encode(@NonNull final List<T> list) {
        return new JSONArray(list.stream().map(this::encode).collect(Collectors.toList()));
    }

    @NonNull
    public ArrayList<T> decode(@NonNull final JSONArray objects)
            throws JSONException {
        final ArrayList<T> list = new ArrayList<>();
        for (int i = 0; i < objects.length(); i++) {
            list.add(decode((JSONObject) objects.get(i)));
        }
        return list;
    }
}
