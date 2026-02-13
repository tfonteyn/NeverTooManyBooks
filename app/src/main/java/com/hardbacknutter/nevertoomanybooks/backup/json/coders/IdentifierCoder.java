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

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
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
        out.put(DBKey.IDENTIFIERS.TYPE, String.valueOf(identifier.getType()));
        out.put(DBKey.IDENTIFIERS.NAME, identifier.getName());
        identifier.getWikidataClaimAuthorId().ifPresent(
                s -> out.put(DBKey.IDENTIFIERS.WIKIDATA_CLAIM_AUTHOR_ID, s));
        out.put(DBKey.IDENTIFIERS.SITE_URL, identifier.getSiteUrl());
        identifier.getBookUri().ifPresent(s -> out.put(DBKey.IDENTIFIERS.BOOK_URI, s));
        identifier.getAuthorUri().ifPresent(s -> out.put(DBKey.IDENTIFIERS.AUTHOR_URI, s));
        return out;
    }

    @NonNull
    @Override
    public Identifier decode(@NonNull final JSONObject data)
            throws JSONException {
        final String key = data.getString(DBKey.IDENTIFIERS.KEY);
        final char type = data.getString(DBKey.IDENTIFIERS.TYPE).charAt(0);
        final String name = data.getString(DBKey.IDENTIFIERS.NAME);
        @Nullable
        final String wikidataClaimAuthorId =
                data.optString(DBKey.IDENTIFIERS.WIKIDATA_CLAIM_AUTHOR_ID, null);
        @Nullable
        final String siteUrl = data.optString(DBKey.IDENTIFIERS.SITE_URL, null);
        @Nullable
        final String bookUrl = data.optString(DBKey.IDENTIFIERS.BOOK_URI, null);
        @Nullable
        final String authorUrl = data.optString(DBKey.IDENTIFIERS.AUTHOR_URI, null);

        final Identifier identifier = new Identifier(key, type, name, wikidataClaimAuthorId,
                                                     siteUrl, bookUrl, authorUrl);
        identifier.setId(data.getLong(DBKey.PK_ID));
        return identifier;
    }
}
