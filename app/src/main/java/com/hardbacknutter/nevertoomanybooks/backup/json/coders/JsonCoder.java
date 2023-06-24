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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveWriter;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Note that {@link #decode(JSONObject)} returns the object because we EXPECT the object,
 * while {@link #decodeReference(JSONObject)} returns an Optional because the actual object
 * might legitimate not exist.
 *
 * @param <T> the type of Object we're encoding/decoding
 */
public interface JsonCoder<T> {

    /**
     * The json tag used for the root level by the {@link JsonArchiveWriter}.
     */
    String TAG_APPLICATION_ROOT = "NeverTooManyBooks";

    /**
     * Encode the given element.
     *
     * @param element to encode
     *
     * @return encoded data
     *
     * @throws IllegalArgumentException if the data could partially be encoded,
     *                                  but we encountered an unknown type/element.
     *                                  The message <strong>must be localized and
     *                                  will be shown to the user</strong>
     * @throws JSONException            upon any parsing error
     */
    @NonNull
    JSONObject encode(@NonNull T element)
            throws JSONException;

    /**
     * Encode a list of elements. Actual work is done in {@link #encode(Object)}.
     *
     * @param elements to encode
     *
     * @return encoded data
     *
     * @throws JSONException upon any parsing error
     */
    @NonNull
    default JSONArray encode(@NonNull final List<T> elements)
            throws JSONException {
        final List<JSONObject> result = new ArrayList<>();
        for (final T element : elements) {
            result.add(encode(element));
        }
        return new JSONArray(result);
    }

    @NonNull
    default JSONObject encodeReference(@NonNull final T element)
            throws JSONException {
        throw new UnsupportedOperationException();
    }

    @NonNull
    default JSONArray encodeReference(@NonNull final List<T> elements)
            throws JSONException {
        final List<JSONObject> result = new ArrayList<>();
        for (final T element : elements) {
            result.add(encodeReference(element));
        }
        return new JSONArray(result);
    }

    /**
     * Decode the given data object.
     *
     * @param data to decode
     *
     * @return decoded data
     *
     * @throws IllegalArgumentException if the data could partially be parsed,
     *                                  but we encountered an unknown type/element.
     *                                  The message <strong>must be localized and
     *                                  will be shown to the user</strong>
     * @throws JSONException            upon any parsing error
     */
    @NonNull
    T decode(@NonNull JSONObject data)
            throws JSONException;

    /**
     * Decode a list of elements. Actual work is done in {@link #decode(JSONObject)}.
     *
     * @param elements to decode
     *
     * @return decoded data
     *
     * @throws JSONException upon any parsing error
     */
    @NonNull
    default List<T> decode(@NonNull final JSONArray elements)
            throws JSONException {
        final List<T> list = new ArrayList<>();
        for (int i = 0; i < elements.length(); i++) {
            list.add(decode((JSONObject) elements.get(i)));
        }
        return list;
    }

    @NonNull
    default Optional<T> decodeReference(@NonNull final JSONObject data)
            throws JSONException {
        throw new UnsupportedOperationException();
    }

    @NonNull
    default List<T> decodeReference(@NonNull final JSONArray elements)
            throws JSONException {
        final List<T> list = new ArrayList<>();
        for (int i = 0; i < elements.length(); i++) {
            decodeReference((JSONObject) elements.get(i)).ifPresent(list::add);
        }
        return list;
    }
}
