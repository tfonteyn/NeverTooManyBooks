/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.updates.IdentifierMigration;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class IdentifierCoder
        implements JsonCoder<Identifier> {

    @NonNull
    @Override
    public JSONObject encode(@NonNull final Identifier identifier)
            throws JSONException {
        final JSONObject out = new JSONObject();
        out.put(DBKey.PK_ID, identifier.getId());
        out.put(DBKey.IDENTIFIERS.KEY, identifier.getKey());

        // Write as an int
        out.put(DBKey.IDENTIFIERS.ENTITY, identifier.getEntityType().getId());

        // Write as a String for backwards compatibility.
        out.put(DBKey.IDENTIFIERS.TYPE, String.valueOf(identifier.getType().getId()));

        out.put(DBKey.IDENTIFIERS.NAME, identifier.getName());
        out.put(DBKey.IDENTIFIERS.SITE_URL, identifier.getSiteUrl());

        identifier.getRawUri().ifPresent(s -> out.put(DBKey.IDENTIFIERS.URI, s));
        identifier.getWikidataClaim().ifPresent(s -> out.put(DBKey.IDENTIFIERS.WIKIDATA_CLAIM, s));
        return out;
    }

    @NonNull
    @Override
    public Identifier decode(@NonNull final JSONObject data)
            throws JSONException {
        throw new IllegalStateException("Use decodeList instead");
    }

    @NonNull
    @Override
    public Collection<Identifier> decodeList(@NonNull final JSONObject data)
            throws JSONException {
        final long id = data.getLong(DBKey.PK_ID);
        final String key = data.getString(DBKey.IDENTIFIERS.KEY);

        final Identifier.EntityType entityType;
        if (data.has(DBKey.IDENTIFIERS.ENTITY)) {
            entityType = Identifier.EntityType.byId(data.getInt(DBKey.IDENTIFIERS.ENTITY));
        } else {
            // ZipArchiveWriter#VERSION == 8 and earlier don't have this field
            // Default to a Book
            entityType = Identifier.EntityType.Book;
        }

        final Identifier.Type type = Identifier.Type.byId(
                data.getString(DBKey.IDENTIFIERS.TYPE).charAt(0));
        final String name = data.getString(DBKey.IDENTIFIERS.NAME);

        final String siteUrl = data.optString(DBKey.IDENTIFIERS.SITE_URL, null);

        // check for fields which were present in ZipArchiveWriter#VERSION == 8
        if (data.has("book_uri")
            || data.has("author_uri")
            || data.has("wd_p_author_id")) {
            // We are sure we have an archive v8
            // Just decode it and we're done here.
            return v8decode(data, id, key, type, name, siteUrl);
        }

        // At this point we either have:
        // - archive v9 or later
        // - archive v8 without the legacy fields.
        // Just continue decoding fields from archive v9 and later.

        final String wikidataClaim = data.optString(DBKey.IDENTIFIERS.WIKIDATA_CLAIM, null);
        final String uri = data.optString(DBKey.IDENTIFIERS.URI, null);

        final Identifier identifier = new Identifier(entityType, type, key, name,
                                                     siteUrl, uri, wikidataClaim);
        identifier.setId(id);
        return List.of(identifier);
    }

    @NonNull
    private Collection<Identifier> v8decode(@NonNull final JSONObject data,
                                            final long id,
                                            @NonNull final String key,
                                            @NonNull final Identifier.Type type,
                                            @NonNull final String name,
                                            @Nullable final String siteUrl) {

        final String bookUrl = data.optString("book_uri", null);
        final String authorUrl = data.optString("author_uri", null);
        final String wikidataClaim = data.optString("wd_p_author_id", null);

        return IdentifierMigration.mapV7Identifier(
                id, key, type, name, siteUrl, bookUrl, authorUrl, wikidataClaim);
    }
}
