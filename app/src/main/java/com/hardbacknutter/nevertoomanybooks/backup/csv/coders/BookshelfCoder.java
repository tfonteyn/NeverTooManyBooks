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

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

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
        implements StringList.Coder<Bookshelf> {

    private static final char[] ESCAPE_CHARS = {'(', ')'};
    @NonNull
    private final ListStyle mDefaultStyle;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    BookshelfCoder(@NonNull final Context context) {
        mDefaultStyle = ServiceLocator.getInstance().getStyles().getDefault(context);
    }

    /**
     * Backwards compatibility rules ',' (not using the default '|').
     */
    @Override
    public char getElementSeparator() {
        return ',';
    }

    @NonNull
    @Override
    public String encode(@NonNull final Bookshelf bookshelf) {
        String result = escape(bookshelf.getName(), ESCAPE_CHARS);

        final JSONObject details = new JSONObject();
        try {
            if (!bookshelf.getStyleUuid().isEmpty()) {
                details.put(DBKey.FK_STYLE, bookshelf.getStyleUuid());
            }
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }

        if (!details.isEmpty()) {
            result += ' ' + String.valueOf(getObjectSeparator()) + ' ' + details;
        }
        return result;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Bookshelf bookshelf = new Bookshelf(parts.get(0), mDefaultStyle);
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));
                // It's quite possible that the UUID is not a style we (currently) know.
                // But that does not matter as we'll check it upon first access.
                if (details.has(DBKey.FK_STYLE)) {
                    bookshelf.setStyleUuid(details.optString(DBKey.FK_STYLE));
                } else if (details.has("style")) {
                    bookshelf.setStyleUuid(details.optString("style"));
                }
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return bookshelf;
    }
}
