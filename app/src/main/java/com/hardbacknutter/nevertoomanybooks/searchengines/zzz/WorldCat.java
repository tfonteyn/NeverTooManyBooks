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

public final class WorldCat {

    private static final String SITE_URL = "https://search.worldcat.org";
    private static final String BOOK_URL = "https://www.worldcat.org/oclc/%s";
    private static final String AUTHOR_URL = "https://id.oclc.org/worldcat/entity/%s";

    private WorldCat() {
    }

    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_worldcat);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_OCLC,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P243"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_OCLC,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P10832")
        );
    }
}
