/*
 * @Copyright 2018-2024 HardBackNutter
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

/**
 * ENHANCE the use of AuthorResolver to let them access the website Author API/page
 *  (providing they have this) anf fetch extra information about the author
 *  (born/died, language, country... i.e. not limited to the pen-name)
 */
public final class AuthorResolverFactory {

    /**
     * "[engine].resolve.authors.[resolver]"
     */
    private static final String RESOLVE_AUTHORS = ".resolve.authors.";

    private AuthorResolverFactory() {
    }

    /**
     * Get a a list of the supported resolvers for the given engine.
     *
     * @param context      Current context
     * @param searchEngine to use
     *
     * @return list
     */
    @NonNull
    public static List<AuthorResolver> getResolvers(@NonNull final Context context,
                                                    @NonNull final SearchEngine searchEngine) {

        // For now, we only support a single resolver, so the last part is hardcoded
        final String key = searchEngine.getEngineId().getPreferenceKey()
                           + RESOLVE_AUTHORS
                           + EngineId.Bedetheque.getPreferenceKey();

        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(key, false)) {

            return List.of(BedethequeAuthorResolver.create(context, searchEngine));
        } else {
            return List.of();
        }
    }
}
