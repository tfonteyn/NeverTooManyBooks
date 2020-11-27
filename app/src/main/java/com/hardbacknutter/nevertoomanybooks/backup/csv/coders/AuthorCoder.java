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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

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
        implements StringList.Factory<Author> {


    /** String encoding use: separator between family name and given-names. */
    private static final char NAME_SEPARATOR = ',';

    @NonNull
    private static final char[] mEscapeChars = {NAME_SEPARATOR, ' ', '(', ')'};

    @Override
    @NonNull
    public Author decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Author author = Author.from(parts.get(0));
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));

                if (details.has(DBDefinitions.KEY_AUTHOR_IS_COMPLETE)) {
                    author.setComplete(details.optBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE));
                } else if (details.has("complete")) {
                    author.setComplete(details.optBoolean("complete"));
                }

                if (details.has(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)) {
                    author.setType(details.optInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK));
                } else if (details.has("type")) {
                    author.setType(details.optInt("type"));
                }
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return author;
    }

    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    @NonNull
    @Override
    public String encode(@NonNull final Author author) {
        // Note the use of NAME_SEPARATOR between family and given-names,
        // i.e. the names are considered ONE field with a private separator.
        String result = escape(author.getFamilyName(), mEscapeChars)
                        + NAME_SEPARATOR + ' '
                        + escape(author.getGivenNames(), mEscapeChars);

        final JSONObject details = new JSONObject();
        try {
            if (author.isComplete()) {
                details.put(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, true);
            }
            if (author.getType() != Author.TYPE_UNKNOWN) {
                details.put(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK, author.getType());
            }
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }

        if (details.length() != 0) {
            result += ' ' + String.valueOf(getObjectSeparator()) + ' ' + details.toString();
        }
        return result;
    }
}
