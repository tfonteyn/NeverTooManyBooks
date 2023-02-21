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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class BookshelfCoder
        implements JsonCoder<Bookshelf> {

    @NonNull
    private final Style defaultStyle;
    @NonNull
    private final Context context;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public BookshelfCoder(@NonNull final Context context) {
        this.context = context;
        defaultStyle = ServiceLocator.getInstance().getStyles().getDefault(context);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Bookshelf bookshelf)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.PK_ID, bookshelf.getId());
        out.put(DBKey.BOOKSHELF_NAME, bookshelf.getName());
        if (!bookshelf.getStyleUuid().isEmpty()) {
            out.put(DBKey.FK_STYLE, bookshelf.getStyleUuid());
        }
        return out;
    }

    @NonNull
    @Override
    public JSONObject encodeReference(@NonNull final Bookshelf bookshelf)
            throws JSONException {
        final JSONObject out = new JSONObject();
        out.put(DBKey.BOOKSHELF_NAME, bookshelf.getName());
        return out;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final JSONObject data)
            throws JSONException {

        final Bookshelf bookshelf = new Bookshelf(
                data.getString(DBKey.BOOKSHELF_NAME), defaultStyle);
        bookshelf.setId(data.getLong(DBKey.PK_ID));

        // It's quite possible that the UUID is not a style we (currently) know.
        // But that does not matter as we'll check it upon first access.
        if (data.has(DBKey.FK_STYLE)) {
            bookshelf.setStyleUuid(data.getString(DBKey.FK_STYLE));
        } else if (data.has("style")) {
            bookshelf.setStyleUuid(data.getString("style"));
        }

        return bookshelf;
    }

    @NonNull
    @Override
    public Optional<Bookshelf> decodeReference(@NonNull final JSONObject data)
            throws JSONException {

        final String name = data.optString(DBKey.BOOKSHELF_NAME);
        if (name != null && !name.isEmpty()) {
            final Optional<Bookshelf> bookshelf =
                    ServiceLocator.getInstance().getBookshelfDao().findByName(name);
            if (bookshelf.isPresent()) {
                return bookshelf;
            }
        }

        final Optional<Bookshelf> bookshelf = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED);
        if (bookshelf.isPresent()) {
            return bookshelf;
        }
        return Bookshelf.getBookshelf(context, Bookshelf.DEFAULT);
    }
}
