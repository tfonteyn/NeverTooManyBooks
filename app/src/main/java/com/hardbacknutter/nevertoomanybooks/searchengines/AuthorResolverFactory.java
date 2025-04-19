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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih.DatabazeKnihAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibraryAuthorResolver;

/**
 * ENHANCE the use of AuthorResolver to let them access the website Author API/page
 *  (providing they have this) and fetch extra information about the author
 *  (born/died, language, country... i.e. not limited to the pen-name)
 */
public final class AuthorResolverFactory {

    /**
     * Pref key.
     * "[engine].resolve.authors.[resolver]"
     */
    private static final String PK_RESOLVE_AUTHORS = ".resolve.authors.";

    private AuthorResolverFactory() {
    }

    /**
     * Get a list of the supported resolvers for the given engine.
     *
     * @param context      Current context
     * @param searchEngine to use
     *
     * @return list
     */
    @NonNull
    public static List<AuthorResolver> getEuroComicResolvers(
            @NonNull final Context context,
            @NonNull final SearchEngine searchEngine) {

        // For now, we only support the Bedetheque resolver, so the last part is hardcoded
        final String key = searchEngine.getEngineId().getPreferenceKey()
                           + PK_RESOLVE_AUTHORS
                           + EngineId.Bedetheque.getPreferenceKey();

        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(key, false)) {

            return List.of(BedethequeAuthorResolver.create(context, searchEngine));
        } else {
            return List.of();
        }
    }

    @NonNull
    public static List<AuthorResolver> getResolvers(@NonNull final Context context,
                                                    @NonNull final SearchEngine searchEngine) {
        final String pk = searchEngine.getEngineId().getPreferenceKey();
        // For now, we only support a single resolver matching their own SearchEngine,
        // so the last part is the same as the first
        final String key = pk + PK_RESOLVE_AUTHORS + pk;

        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(key, true)) {

            switch (searchEngine.getEngineId()) {
                case DatabazeKnih: {
                    return List.of(DatabazeKnihAuthorResolver.create(context, searchEngine));
                }
                case Dnb: {
                    return List.of(DnbAuthorResolver.create(context, searchEngine));
                }
                case Isfdb: {
                    return List.of(IsfdbAuthorResolver.create(context, searchEngine));
                }
                case OpenLibrary: {
                    return List.of(OpenLibraryAuthorResolver.create(context, searchEngine));
                }
            }
        }
        return List.of();
    }
}
