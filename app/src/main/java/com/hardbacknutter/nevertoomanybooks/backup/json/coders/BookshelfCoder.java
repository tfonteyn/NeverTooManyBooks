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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class BookshelfCoder
        implements JsonCoder<Bookshelf> {

    @NonNull
    private final ListStyle mDefaultStyle;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    BookshelfCoder(@NonNull final Context context) {
        mDefaultStyle = StyleUtils.getDefault(context);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Bookshelf bookshelf)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBDefinitions.KEY_PK_ID, bookshelf.getId());
        out.put(DBDefinitions.KEY_BOOKSHELF_NAME, bookshelf.getName());
        if (!bookshelf.getStyleUuid().isEmpty()) {
            out.put(DBDefinitions.KEY_FK_STYLE, bookshelf.getStyleUuid());
        }
        return out;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final JSONObject data)
            throws JSONException {

        final Bookshelf bookshelf = new Bookshelf(
                data.getString(DBDefinitions.KEY_BOOKSHELF_NAME), mDefaultStyle);
        bookshelf.setId(data.getLong(DBDefinitions.KEY_PK_ID));

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
