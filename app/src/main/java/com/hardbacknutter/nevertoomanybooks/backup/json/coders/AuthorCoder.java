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

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class AuthorCoder
        implements JsonCoder<Author> {

    AuthorCoder() {
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Author author)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.PK_ID, author.getId());
        out.put(DBKey.AUTHOR.FAMILY_NAME, author.getFamilyName());

        if (!author.getGivenNames().isEmpty()) {
            out.put(DBKey.AUTHOR.GIVEN_NAMES, author.getGivenNames());
        }

        author.getBirthDate().ifPresent(s -> out.put(DBKey.AUTHOR.BIRTH_DATE, s));
        author.getDeathDate().ifPresent(s -> out.put(DBKey.AUTHOR.DEATH_DATE, s));
        author.getPictureUuid().ifPresent(uuid -> out.put(DBKey.AUTHOR.PICTURE_UUID, uuid));

        if (author.isComplete()) {
            out.put(DBKey.AUTHOR.COMPLETE, true);
        }
        if (author.getType() != Author.TYPE_UNKNOWN) {
            out.put(DBKey.AUTHOR.BOOK_AUTHOR_TYPE, author.getType());
        }
        if (author.getRealAuthor() != null) {
            out.put(DBKey.FK_AUTHOR_REAL_AUTHOR, encode(author.getRealAuthor()));
        }

        return out;
    }

    @Override
    @NonNull
    public Author decode(@NonNull final JSONObject data)
            throws JSONException {

        final Author author = new Author(data.getString(DBKey.AUTHOR.FAMILY_NAME),
                                         // optional
                                         data.optString(DBKey.AUTHOR.GIVEN_NAMES));

        author.setId(data.getLong(DBKey.PK_ID));

        if (data.has(DBKey.AUTHOR.BIRTH_DATE)) {
            author.setBirthDate(data.optString(DBKey.AUTHOR.BIRTH_DATE));
        }
        if (data.has(DBKey.AUTHOR.DEATH_DATE)) {
            author.setDeathDate(data.optString(DBKey.AUTHOR.DEATH_DATE));
        }
        if (data.has(DBKey.AUTHOR.PICTURE_UUID)) {
            author.setPictureUuid(data.optString(DBKey.AUTHOR.PICTURE_UUID));
        }
        if (data.has(DBKey.AUTHOR.COMPLETE)) {
            author.setComplete(data.getBoolean(DBKey.AUTHOR.COMPLETE));
        } else if (data.has("complete")) {
            author.setComplete(data.getBoolean("complete"));
        }

        if (data.has(DBKey.AUTHOR.BOOK_AUTHOR_TYPE)) {
            author.setType(data.getInt(DBKey.AUTHOR.BOOK_AUTHOR_TYPE));
        } else if (data.has("type")) {
            author.setType(data.getInt("type"));
        }

        if (data.has(DBKey.FK_AUTHOR_REAL_AUTHOR)) {
            author.setRealAuthor(decode(data.getJSONObject(DBKey.FK_AUTHOR_REAL_AUTHOR)));
        }
        return author;
    }
}
