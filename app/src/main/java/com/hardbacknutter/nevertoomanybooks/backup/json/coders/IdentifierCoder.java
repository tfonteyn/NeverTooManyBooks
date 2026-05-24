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
        out.put(DBKey.IDENTIFIERS.ENTITY, identifier.getEntityType().getId());

        out.put(DBKey.IDENTIFIERS.TYPE, identifier.getType().getId());
        out.put(DBKey.IDENTIFIERS.NAME, identifier.getName());
        out.put(DBKey.IDENTIFIERS.SITE_URL, identifier.getSiteUrl());

        identifier.getUri().ifPresent(s -> out.put(DBKey.IDENTIFIERS.URI, s));
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
        // For legacy archives missing this field, we default to a Book
        final Identifier.EntityType entityType = Identifier.EntityType.byId(
                data.getInt(DBKey.IDENTIFIERS.ENTITY));

        final Identifier.Type type = Identifier.Type.byId(
                data.getString(DBKey.IDENTIFIERS.TYPE).charAt(0));
        final String name = data.getString(DBKey.IDENTIFIERS.NAME);
        @Nullable
        final String siteUrl = data.optString(DBKey.IDENTIFIERS.SITE_URL, null);

        @Nullable
        final String wikidataClaim =
                data.optString(DBKey.IDENTIFIERS.WIKIDATA_CLAIM, null);

        @Nullable
        final String uri = data.optString(DBKey.IDENTIFIERS.URI, null);
        if (uri != null) {
            final Identifier identifier = new Identifier(key, entityType, type, name,
                                                         siteUrl, uri, wikidataClaim);

            identifier.setId(id);
            return List.of(identifier);
        }

        // Check for potential legacy fields and try migrating them
        return decodeV7(id, key, type, name, siteUrl, wikidataClaim, data);
    }

    @NonNull
    private Collection<Identifier> decodeV7(final long id,
                                            @NonNull final String key,
                                            @NonNull final Identifier.Type type,
                                            @NonNull final String name,
                                            @Nullable final String siteUrl,
                                            @Nullable final String wikidataClaim,
                                            @NonNull final JSONObject data) {
        @Nullable
        final String bookUrl = data.optString(IdentifierMigration.BOOK_URI_OBSOLETE, null);
        @Nullable
        final String authorUrl = data.optString(IdentifierMigration.AUTHOR_URI_OBSOLETE, null);

        return IdentifierMigration.mapV7Identifier(
                id, key, type, name, siteUrl, bookUrl, authorUrl, wikidataClaim);
    }
}
