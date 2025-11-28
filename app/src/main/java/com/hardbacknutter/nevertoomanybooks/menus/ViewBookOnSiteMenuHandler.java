/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.menus;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * Collects all {@link Identifier}s present
 * and builds/displays a menu suitable for the given {@link Book}.
 * <p>
 * We hide the entire submenu if there are none.
 */
public class ViewBookOnSiteMenuHandler
        extends ViewOnSiteMenuHandler<DataHolder> {

    /**
     * Constructor.
     */
    public ViewBookOnSiteMenuHandler() {
        super(R.id.SUBMENU_VIEW_BOOK_ON_SITE,
              R.id.MENU_GROUP_BOOK,
              (context, identifier) -> identifier.getBookUri());
    }

    @NonNull
    List<Identifier.Value> getSids(@NonNull final DataHolder data) {
        final List<Identifier.Value> ivs = DataHolderUtils.getSids(DBKey.FK_BOOK, data);

        if (ivs.stream().map(Identifier.Value::getKey).noneMatch(Identifier.SID_ASIN::equals)) {
            //URGENT: is this a good idea? The browser/amazon gives a 404 if the isbn is not found
            // When looking for the Amazon ASIN, fallback on an Isbn code if possible
            if (data.contains(DBKey.ISBN)) {
                final String isbnStr = data.getString(DBKey.ISBN);
                final ISBN isbn = new ISBN(isbnStr, true);
                if (isbn.isValid(true) && isbn.isIsbn10Compat()) {
                    ivs.add(new Identifier.Value(Identifier.SID_ASIN,
                                                 isbn.asText(ISBN.Type.Isbn10)));
                }
            }
        }
        return ivs;
    }

    @NonNull
    @Override
    Optional<String> getSid(@NonNull final DataHolder data,
                            @NonNull final String key) {
        return DataHolderUtils.getSid(DBKey.FK_BOOK, data, key);
    }
}
