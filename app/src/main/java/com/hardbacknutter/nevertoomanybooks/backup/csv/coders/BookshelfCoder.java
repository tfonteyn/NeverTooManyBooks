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

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * StringList factory for a Bookshelf.
 * <ul>Format:
 *      <li>shelfName * {json}</li>
 * </ul>
 *
 * <strong>Note:</strong> In the format definition, the " * {json}" suffix is optional
 * and can be missing.
 */
public class BookshelfCoder
        implements StringList.Factory<Bookshelf> {

    @NonNull
    private final BooklistStyle mDefaultStyle;
    @NonNull
    private final char[] mEscapeChars = {'(', ')'};

    /**
     * Constructor.
     *
     * @param defaultStyle to use for bookshelves without a style set.
     */
    public BookshelfCoder(@NonNull final BooklistStyle defaultStyle) {
        mDefaultStyle = defaultStyle;
    }

    /**
     * Backwards compatibility rules ',' (not using the default '|').
     */
    @Override
    public char getElementSeparator() {
        return ',';
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Bookshelf bookshelf = new Bookshelf(parts.get(0), mDefaultStyle);
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));
                bookshelf.fromJson(details);
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return bookshelf;
    }

    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    @NonNull
    @Override
    public String encode(@NonNull final Bookshelf bookshelf) {
        String result = escape(bookshelf.getName(), mEscapeChars);

        final JSONObject details = new JSONObject();
        try {
            bookshelf.toJson(details);
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }

        if (details.length() != 0) {
            result += ' ' + String.valueOf(getObjectSeparator())
                      + ' ' + details.toString();
        }
        return result;
    }
}
