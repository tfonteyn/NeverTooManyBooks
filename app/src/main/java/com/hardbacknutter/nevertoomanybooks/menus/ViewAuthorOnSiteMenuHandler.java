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
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * Collects all {@link Identifier}s present
 * and builds/displays a menu suitable for the given {@link Author}.
 * <p>
 * We hide the entire submenu if there are none.
 */
public class ViewAuthorOnSiteMenuHandler
        extends ViewOnSiteMenuHandler<DataHolder> {

    public ViewAuthorOnSiteMenuHandler() {
        super(R.id.SUBMENU_VIEW_AUTHOR_ON_SITE,
              0,
              (context, identifier) -> identifier.getAuthorUri(context));
    }

    @NonNull
    List<Identifier.Value> getSids(@NonNull final DataHolder data) {
        return DataHolderUtils.getSids(DBKey.FK_AUTHOR, data);
    }

    @NonNull
    @Override
    Optional<String> getSid(@NonNull final DataHolder data,
                            @NonNull final String key) {
        return DataHolderUtils.getSid(DBKey.FK_AUTHOR, data, key);
    }
}
