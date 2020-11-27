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

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class BookshelfCoder
        extends JsonCoderBase<Bookshelf> {

    @NonNull
    private final BooklistStyle mDefaultStyle;

    /**
     * Constructor.
     *
     * @param defaultStyle to use for bookshelves without a style set.
     */
    public BookshelfCoder(@NonNull final BooklistStyle defaultStyle) {
        mDefaultStyle = defaultStyle;
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Bookshelf bookshelf) {
        final JSONObject data = new JSONObject();
        try {
            data.put(DBDefinitions.KEY_PK_ID, bookshelf.getId());
            data.put(DBDefinitions.KEY_BOOKSHELF_NAME, bookshelf.getName());
            if (!bookshelf.getStyleUuid().isEmpty()) {
                data.put(DBDefinitions.KEY_FK_STYLE, bookshelf.getStyleUuid());
            }
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }
        return data;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final JSONObject data)
            throws JSONException {

        final Bookshelf bookshelf = new Bookshelf(data.getString(DBDefinitions.KEY_BOOKSHELF_NAME),
                                                  mDefaultStyle);

        // It's quite possible that the UUID is not a style we (currently) know.
        // But that does not matter as we'll check it upon first access.
        if (data.has(DBDefinitions.KEY_FK_STYLE)) {
            bookshelf.setStyleUuid(data.getString(DBDefinitions.KEY_FK_STYLE));
        } else if (data.has("style")) {
            bookshelf.setStyleUuid(data.getString("style"));
        }

        return bookshelf;
    }
}
