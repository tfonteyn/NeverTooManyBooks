/*
 * @Copyright 2018-2024 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * StringList factory for a Bookshelf.
 * <p>
 * Format: shelfName * {json}
 * <br><strong>Note:</strong> the " * {json}" suffix is optional and can be missing.
 */
public class BookshelfCoder
        implements StringList.Coder<Bookshelf> {

    @NonNull
    private final Style defaultStyle;

    private final char elementSeparator;

    /**
     * Constructor.
     *
     * @param elementSeparator custom separator
     * @param defaultStyle     the default style to use
     */
    BookshelfCoder(final char elementSeparator,
                   @NonNull final Style defaultStyle) {
        this.elementSeparator = elementSeparator;
        this.defaultStyle = defaultStyle;
    }

    @Override
    public char getElementSeparator() {
        return elementSeparator;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Bookshelf bookshelf = new Bookshelf(parts.get(0), defaultStyle);
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));
                // It's quite possible that the UUID is not a style we (currently) know.
                // But that does not matter as we'll check it upon first access.
                String styleUuid = details.optString(DBKey.FK_STYLE);
                if (styleUuid != null && !styleUuid.isEmpty()) {
                    bookshelf.setStyleUuid(styleUuid);
                } else {
                    styleUuid = details.optString("style");
                    if (styleUuid != null && !styleUuid.isEmpty()) {
                        bookshelf.setStyleUuid(styleUuid);
                    }
                }
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return bookshelf;
    }
}
