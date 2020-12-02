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

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class AuthorCoder
        implements JsonCoder<Author> {

    AuthorCoder() {
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Author author)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBDefinitions.KEY_PK_ID, author.getId());
        out.put(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, author.getFamilyName());

        if (!author.getGivenNames().isEmpty()) {
            out.put(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, author.getGivenNames());
        }
        if (author.isComplete()) {
            out.put(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, true);
        }
        if (author.getType() != Author.TYPE_UNKNOWN) {
            out.put(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK, author.getType());
        }

        return out;
    }

    @Override
    @NonNull
    public Author decode(@NonNull final JSONObject data)
            throws JSONException {

        final Author author = new Author(data.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME),
                                         // optional
                                         data.optString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

        author.setId(data.getLong(DBDefinitions.KEY_PK_ID));

        if (data.has(DBDefinitions.KEY_AUTHOR_IS_COMPLETE)) {
            author.setComplete(data.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE));
        } else if (data.has("complete")) {
            author.setComplete(data.getBoolean("complete"));
        }

        if (data.has(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)) {
            author.setType(data.getInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK));
        } else if (data.has("type")) {
            author.setType(data.getInt("type"));
        }

        return author;
    }
}
