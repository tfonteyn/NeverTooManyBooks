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
 * The site redirects to the local country but in doing so,
 * it looses the book/author part... the user will need to edit the url's.
 * We should document this...
 */
public final class Audible {

    private static final String SITE_URL = "https://www.audible.com";
    private static final String BOOK_URL = "https://www.audible.com/pd/%s";
    private static final String AUTHOR_URL = "https://www.audible.com/author/%s";
    private static final String SERIES_URL = "https://www.audible.com/series/%s";

    private Audible() {
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
        final String name = context.getString(R.string.identifier_audible);
        // 2026-05-29: no wikidata claims found
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_AUDIBLE,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               null),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_AUDIBLE,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               null),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Text,
                               Identifier.SID_AUDIBLE,
                               name,
                               SITE_URL,
                               SERIES_URL,
                               null)
        );
    }
}
