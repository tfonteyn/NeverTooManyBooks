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
 * It's not entirely clear what the difference between LIBRIS and LIBRIS_XL is.
 * This was copied from the ISFDB website. Some day we'll need to look at the details.
 */
public final class LibrisSE {

    private static final String SITE_URL = "https://libris.kb.se";
    private static final String BOOK_URL = "https://libris.kb.se/bib/%s";
    private static final String AUTHOR_URL = "https://libris.kb.se/auth/%s";

    private static final String XL_SITE_URL = "https://libris.kb.se/katalogisering";
    private static final String XL_BOOK_URL = "https://libris.kb.se/%s";

    private LibrisSE() {
    }

    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_libris);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_LIBRIS,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               null),
                // the url+number redirects to a permalink in the format of:
                //   https://libris.kb.se/zw9cd5zh025njcf
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_LIBRIS,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P906"),

                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_LIBRIS_XL,
                               name,
                               XL_SITE_URL,
                               XL_BOOK_URL,
                               null)
        );
    }
}
