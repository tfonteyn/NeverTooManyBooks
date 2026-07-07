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

package com.hardbacknutter.nevertoomanybooks.searchengines.zzz;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public final class VIAF {

    private static final String SITE_URL = "https://viaf.org";
    private static final String ITEM_URL = "https://viaf.org/en/viaf/%s";

    private VIAF() {
    }

    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_viaf);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_VIAF,
                               name,
                               SITE_URL,
                               ITEM_URL,
                               "P214"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_VIAF,
                               name,
                               SITE_URL,
                               ITEM_URL,
                               "P214"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Number,
                               Identifier.SID_VIAF,
                               name,
                               SITE_URL,
                               ITEM_URL,
                               "P214")
        );
    }
}
