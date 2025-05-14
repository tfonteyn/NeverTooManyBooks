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
    public static List<AuthorResolver> getResolvers(@NonNull final Context context,
                                                    @NonNull final SearchEngine searchEngine) {
        final EngineId engineId = searchEngine.getEngineId();

        switch (engineId) {
            case Bedetheque: {
                if (isEnabled(context, engineId)) {
                    return List.of(BedethequeAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case DatabazeKnih: {
                if (isEnabled(context, engineId)) {
                    return List.of(DatabazeKnihAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case Dnb: {
                if (isEnabled(context, engineId)) {
                    return List.of(DnbAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case Isfdb: {
                if (isEnabled(context, engineId)) {
                    return List.of(IsfdbAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case LastDodoNl:
            case StripInfoBe:
            case StripWebBe: {
                if (isEnabled(context, searchEngine.getEngineId(), EngineId.Bedetheque)) {
                    return List.of(BedethequeAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case OpenLibrary: {
                if (isEnabled(context, engineId)) {
                    return List.of(OpenLibraryAuthorResolver.create(context, searchEngine));
                }
                break;
            }
        }
        return List.of();
    }


    /**
     * An engine using it's own resolver.
     *
     * @param context  Current context
     * @param engineId the engine == resolver id
     *
     * @return flag
     */
    private static boolean isEnabled(@NonNull final Context context,
                                     @NonNull final EngineId engineId) {
        return isEnabled(context, engineId, engineId);
    }

    /**
     * An engine using a given resolver.
     *
     * @param context  Current context
     * @param engine   id
     * @param resolver id
     *
     * @return flag
     */
    private static boolean isEnabled(@NonNull final Context context,
                                     @NonNull final EngineId engine,
                                     @NonNull final EngineId resolver) {

        final String key = engine.getPreferenceKey()
                           + PK_RESOLVE_AUTHORS
                           + resolver.getPreferenceKey();

        return ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
               && PreferenceManager.getDefaultSharedPreferences(context)
                                   .getBoolean(key, true);
    }
}
