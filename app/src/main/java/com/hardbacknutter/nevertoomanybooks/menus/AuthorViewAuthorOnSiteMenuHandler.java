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
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * Collects all {@link Identifier}s present
 * and builds/displays a menu suitable for a given {@link Author}.
 * <p>
 * We hide the entire submenu if there are none.
 * <p>
 * Used by {@link com.hardbacknutter.nevertoomanybooks.AuthorWorksFragment}.
 *
 * @see ViewAuthorOnSiteMenuHandler
 */
public class AuthorViewAuthorOnSiteMenuHandler
        extends ViewOnSiteMenuHandler<Author> {

    /**
     * Constructor.
     */
    public AuthorViewAuthorOnSiteMenuHandler() {
        super(R.id.SUBMENU_VIEW_AUTHOR_ON_SITE, 0);
    }

    @NonNull
    @Override
    Optional<String> getUri(@NonNull final Identifier identifier) {
        return identifier.getAuthorUri();
    }

    @NonNull
    List<Identifier.Value> getSids(@NonNull final Author data) {
        return data.getIdentifiers();
    }

    @NonNull
    @Override
    Optional<String> getSid(@NonNull final Author data,
                            @NonNull final String key) {
        return data.getIdentifierValue(key);
    }
}
