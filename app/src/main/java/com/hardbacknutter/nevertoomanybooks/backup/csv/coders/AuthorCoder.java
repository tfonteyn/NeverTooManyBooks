/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.annotation.VisibleForTesting;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * StringList factory for a Author.
 * <ul>Format:
 *      <li>authorName * {json}</li>
 * </ul>
 * <ul>With authorName:
 *      <li>writing out: "family, givenNames"</li>
 *      <li>reading in: see {@link Author#from(String)}</li>
 * </ul>
 *
 * <strong>Note:</strong> In the format definition, the " * {json}" suffix is optional
 * and can be missing.
 */
public class AuthorCoder
        implements StringList.Coder<Author> {

    /** String encoding use: separator between family name and given-names. */
    private static final char NAME_SEPARATOR = ',';

    private static final char[] ESCAPE_CHARS = {NAME_SEPARATOR, ' ', '(', ')'};

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public AuthorCoder() {
    }

    @NonNull
    @Override
    public String encode(@NonNull final Author author) {
        // Note the use of NAME_SEPARATOR between family and given-names,
        // i.e. the names are considered ONE field with a private separator.
        String result = escape(author.getFamilyName(), ESCAPE_CHARS)
                        + NAME_SEPARATOR + ' '
                        + escape(author.getGivenNames(), ESCAPE_CHARS);

        final JSONObject details = new JSONObject();
        try {
            if (author.isComplete()) {
                details.put(DBKey.BOOL_AUTHOR_IS_COMPLETE, true);
            }
            if (author.getType() != Author.TYPE_UNKNOWN) {
                details.put(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK, author.getType());
            }
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }

        if (!details.isEmpty()) {
            result += ' ' + String.valueOf(getObjectSeparator()) + ' ' + details.toString();
        }
        return result;
    }

    @Override
    @NonNull
    public Author decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Author author = Author.from(parts.get(0));
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));

                if (details.has(DBKey.BOOL_AUTHOR_IS_COMPLETE)) {
                    author.setComplete(details.optBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE));
                } else if (details.has("complete")) {
                    author.setComplete(details.optBoolean("complete"));
                }

                if (details.has(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK)) {
                    author.setType(details.optInt(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK));
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
