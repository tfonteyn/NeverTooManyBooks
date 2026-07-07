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

/**
 * Digital Object Identifier.
 *
 * @see <a href="https://www.doi.org/the-identifier/resources/factsheets/doi-resolution-documentation">
 *         doi-resolution-documentation</a>
 */
public final class DOI {

    private static final String SITE_URL = "https://www.doi.org";
    private static final String BOOK_URL = "https://doi.org/%s";

    private DOI() {
    }

    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_doi);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_DOI,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               null)
        );
    }
}
