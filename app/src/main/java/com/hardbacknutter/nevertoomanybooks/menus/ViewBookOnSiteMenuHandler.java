/*
 * @Copyright 2018-2026 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.core.utils.CodeType;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.ASIN;

/**
 * Collects all {@link Identifier}s present
 * and builds/displays a menu suitable for a given {@link Book}.
 * <p>
 * We hide the entire submenu if there are none.
 */
public class ViewBookOnSiteMenuHandler
        extends ViewOnSiteMenuHandler<DataHolder> {

    /**
     * Constructor.
     */
    public ViewBookOnSiteMenuHandler() {
        super(Identifier.EntityType.Book, R.id.SUBMENU_VIEW_BOOK_ON_SITE, R.id.MENU_GROUP_BOOK);
    }

    @NonNull
    List<Identifier.Value> getSids(@NonNull final DataHolder data) {
        final List<Identifier.Value> ivs = DataHolderUtils.getSids(DBKey.FK_BOOK, data);

        // The code below sole goal is to check for, or try to construct an ASIN
        // and add it as-needed/if-possible to the list of identifier values.

        // If we have an ASIN, return all the Identifiers now.
        if (ivs.stream().map(Identifier.Value::getKey).anyMatch(Identifier.SID_ASIN::equals)) {
            return ivs;
        }
        // If we don't have an ISBN, nothing we can try more, return all the Identifiers now.
        if (!data.contains(DBKey.ISBN)) {
            return ivs;
        }

        // No ASIN present, see if we can deduce one from the ISBN
        final String isbnStr = data.getString(DBKey.ISBN);

        // check if the ISBN *is* an ASIN
        final ASIN asin = new ASIN(isbnStr);
        if (asin.isValid()) {
            ivs.add(new Identifier.Value(Identifier.SID_ASIN, asin.asText()));
            return ivs;
        }

        // If the ISBN is an ISBN-10, then it *is* an ASIN
        final ISBN isbn = new ISBN(isbnStr, true);
        if (isbn.isValid() && isbn.isIsbn10Compat()) {
            ivs.add(new Identifier.Value(Identifier.SID_ASIN,
                                         isbn.asText(CodeType.Isbn10)));
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
