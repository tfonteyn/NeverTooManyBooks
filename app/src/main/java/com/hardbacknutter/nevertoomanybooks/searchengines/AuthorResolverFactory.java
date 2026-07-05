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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public final class AuthorResolverFactory {

    private AuthorResolverFactory() {
    }

    /**
     * Get a list of engines whose dedicated resolver is enabled
     * and capable of either searching an Author by name or by one of the
     * given SID keys.
     *
     * @param context Current context
     * @param sidKeys available SIDs
     *
     * @return list
     */
    @NonNull
    public static List<EngineId> getEngines(@NonNull final Context context,
                                            @NonNull final List<String> sidKeys) {

        // Engines that only use another engine to do author resolving
        // (i.e. those that don't implement a dedicated resolver)
        // should not be added to these lists. They would be filtered
        // out anyhow due to filter(AuthorResolverHelper::isEnabled)

        // These Engine resolvers support searching by name.
        // Always add them.
        final List<EngineId> searchByName =
                Stream.of(EngineId.Bedetheque,
                          EngineId.Isfdb,
                          EngineId.OpenLibrary,
                          EngineId.Wikidata)
                      .filter(AuthorResolverHelper::isEnabled)
                      .collect(Collectors.toList());

        // These Engine resolvers rely on their SID.
        // Only add them if we actually have an available SID value.
        final List<EngineId> searchBySid =
                Stream.of(EngineId.DatabazeKnih,
                          EngineId.Dnb,
                          EngineId.Goodreads)
                      .filter(AuthorResolverHelper::isEnabled)
                      .filter(engineId -> engineId
                              .getIdentifierKey(Identifier.EntityType.Author)
                              .map(sidKeys::contains)
                              .orElse(false))
                      .collect(Collectors.toList());

        return Stream.concat(searchByName.stream(), searchBySid.stream())
                     .sorted((f1, f2) ->
                                     f1.getName(context).compareToIgnoreCase(f2.getName(context)))
                     .collect(Collectors.toList());
    }
}
