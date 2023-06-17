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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * StringList factory for a Author.
 * <p>
 * Format: authorName * {json}
 * <br><strong>Note:</strong> the " * {json}" suffix is optional and can be missing.
 * <p>
 * With authorName:
 * <ul>
 *      <li>writing out: "family, givenNames"</li>
 *      <li>reading in: see {@link Author#from(String)}</li>
 * </ul>
 */
public class AuthorCoder
        implements StringList.Coder<Author> {

    @Override
    @NonNull
    public Author decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Author author = Author.from(parts.get(0));
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));

                if (details.has(DBKey.AUTHOR_IS_COMPLETE)) {
                    author.setComplete(details.optBoolean(DBKey.AUTHOR_IS_COMPLETE));
                } else if (details.has("complete")) {
                    author.setComplete(details.optBoolean("complete"));
                }

                if (details.has(DBKey.AUTHOR_TYPE__BITMASK)) {
                    author.setType(details.optInt(DBKey.AUTHOR_TYPE__BITMASK));
                } else if (details.has("type")) {
                    author.setType(details.optInt("type"));
                }
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return author;
    }
}
