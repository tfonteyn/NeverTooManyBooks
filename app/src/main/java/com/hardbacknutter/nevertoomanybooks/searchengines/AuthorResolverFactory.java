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
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih.DatabazeKnihAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibraryAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.wikidata.WikidataAuthorResolver;

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
    static List<AuthorResolver> getResolvers(@NonNull final Context context,
                                             @NonNull final SearchEngine searchEngine) {
        final EngineId engineId = searchEngine.getEngineId();

        switch (engineId) {
            case Bedetheque: {
                if (isEnabled(engineId)) {
                    return List.of(BedethequeAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case BiblionetGr:
            case BibliotecePl:
            case Bnf: {
                if (isEnabled(engineId, EngineId.Wikidata, false)) {
                    return List.of(WikidataAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case DatabazeKnih: {
                if (isEnabled(engineId)) {
                    return List.of(DatabazeKnihAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case Dnb: {
                if (isEnabled(engineId)) {
                    return List.of(DnbAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case Goodreads: {
                final List<AuthorResolver> list = new ArrayList<>();
                if (isEnabled(engineId)) {
                    list.add(GoodreadsAuthorResolver.create(context, searchEngine));
                }
                if (isEnabled(engineId, EngineId.Wikidata, false)) {
                    list.add(WikidataAuthorResolver.create(context, searchEngine));
                }
                return list;
            }
            case Isfdb: {
                if (isEnabled(engineId)) {
                    return List.of(IsfdbAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case LastDodoNl:
            case StripInfoBe:
            case StripWebBe: {
                if (isEnabled(engineId, EngineId.Bedetheque, true)) {
                    return List.of(BedethequeAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case OpenLibrary: {
                if (isEnabled(engineId)) {
                    return List.of(OpenLibraryAuthorResolver.create(context, searchEngine));
                }
                break;
            }
            case Wikidata: {
                if (isEnabled(engineId)) {
                    return List.of(WikidataAuthorResolver.create(context, searchEngine));
                }
                break;
            }
        }
        return List.of();
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

        // For reference: these sites don't have their own resolvers,
        // but always use Bedetheque; so don't add them to any list here.
        // EngineId.LastDodoNl,
        // EngineId.StripInfoBe,
        // EngineId.StripWebBe,

        // These Engine resolvers support searching by name.
        // Always add them.
        final List<EngineId> searchByName =
                Stream.of(EngineId.Bedetheque,
                          EngineId.Isfdb,
                          EngineId.OpenLibrary,
                          EngineId.Wikidata)
                      .filter(AuthorResolverFactory::isEnabled)
                      .collect(Collectors.toList());

        // These Engine resolvers rely on their SID.
        // Only add them if we actually have an available SID value.
        final List<EngineId> searchBySid =
                Stream.of(EngineId.DatabazeKnih,
                          EngineId.Dnb,
                          EngineId.Goodreads)
                      .filter(AuthorResolverFactory::isEnabled)
                      // Sanity check, all engines here should have keys,
                      // or we should not have added them!
                      .filter(engineId -> engineId.getIdentifierKey() != null)
                      .filter(engineId -> sidKeys.contains(engineId.getIdentifierKey()))
                      .collect(Collectors.toList());

        return Stream.concat(searchByName.stream(), searchBySid.stream())
                     .sorted((f1, f2) ->
                                     f1.getName(context).compareToIgnoreCase(f2.getName(context)))
                     .collect(Collectors.toList());
    }

    /**
     * An engine using its own resolver.
     *
     * @param engineId the engine == resolver id
     *
     * @return flag
     */
    private static boolean isEnabled(@NonNull final EngineId engineId) {
        return isEnabled(engineId, engineId, true);
    }

    /**
     * An engine using a given resolver.
     *
     * @param engine   id
     * @param resolver id
     * @param defValue default
     *
     * @return flag
     */
    private static boolean isEnabled(@NonNull final EngineId engine,
                                     @NonNull final EngineId resolver,
                                     final boolean defValue) {

        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(getKey(engine, resolver), defValue);
    }

    // Allow easier use for testing
    @VisibleForTesting
    @NonNull
    public static String getKey(@NonNull final EngineId engine,
                                @NonNull final EngineId resolver) {
        return engine.getPreferenceKey() + PK_RESOLVE_AUTHORS + resolver.getPreferenceKey();
    }
}
