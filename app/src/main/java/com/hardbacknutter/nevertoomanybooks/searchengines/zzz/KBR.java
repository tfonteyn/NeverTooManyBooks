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
 * 2026-07-26:  https://opac.kbr.be  seems to be completely broken as it does not do TLS ?
 *             and the http site redirect to the https ...
 */
public final class KBR {

    private static final String SITE_URL = "https://opac.kbr.be";

    private static final String BOOK_URL = "https://uurl.kbr.be/bib/%s";
    private static final String AUTHOR_URL = "https://uurl.kbr.be/aut/%s";
    private static final String SERIES_URL = "https://uurl.kbr.be/bib/%s";

    private KBR() {
    }

    /**
     * Called at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context
     *
     * @return list
     */
    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_kbr);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_KBR,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P9088"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_KBR,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P11249"),
                // Series SEEM to use the same wikidata claim as a book.
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Number,
                               Identifier.SID_KBR,
                               name,
                               SITE_URL,
                               SERIES_URL,
                               "P9088")
        );
    }
}
