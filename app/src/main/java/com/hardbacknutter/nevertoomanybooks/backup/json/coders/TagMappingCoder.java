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

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class TagMappingCoder
        implements JsonCoder<TagMapping> {

    @Override
    @NonNull
    public JSONObject encode(@NonNull final TagMapping tagMapping)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.PK_ID, tagMapping.getId());
        out.put(DBKey.TAGS.TAG, tagMapping.getTagName());
        // yes, out.put(@NonNull String key, @Nullable Collection<?> value)
        // would have worked. But this makes it blatantly clear how decode must work.
        final JSONArray a = new JSONArray();
        tagMapping.getMappings().forEach(a::put);
        out.put(DBKey.TAGS.TAG_MAPPING, a);

        return out;
    }

    @Override
    @NonNull
    public TagMapping decode(@NonNull final JSONObject data)
            throws JSONException {
        final String name = data.getString(DBKey.TAGS.TAG);
        final Set<String> mappings = new HashSet<>();

        final JSONArray a = data.getJSONArray(DBKey.TAGS.TAG_MAPPING);
        for (int i = 0; i < a.length(); i++) {
            mappings.add(a.getString(i));
        }

        final TagMapping tagMapping = new TagMapping(name, mappings);
        tagMapping.setId(data.getLong(DBKey.PK_ID));
        return tagMapping;
    }
}
