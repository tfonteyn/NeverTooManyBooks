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
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih.DatabazeKnihAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibraryAuthorResolver;
import com.hardbacknutter.util.logger.LoggerFactory;

public final class AuthorResolverFactory {

    private static final String TAG = "AuthorResolverFactory";

    /**
     * Pref key.
     * "[engine].resolve.authors.[resolver]"
     */
    private static final String PK_RESOLVE_AUTHORS = ".resolve.authors.";

    private AuthorResolverFactory() {
    }

    /**
     * Run the resolvers for the given engine, for all authors of the given book.
     * Uses the Book, or when not available, the site {@link Locale}.
     * <p>
     * The authors are modified as needed, but <strong>NOT written</strong> to the database.
     * <p>
     * Any {@link SearchException} will cause an abort.
     * This may result in some authors having been processed, while others have not.
     * <strong>ALL results should be discarded in this case</strong>
     *
     * @param context      Current context
     * @param searchEngine requesting the resolve action
     * @param book         with authors
     *
     * @return {@code true} if the any {@link Author}s were modified; {@code false} otherwise
     *
     * @throws CredentialsException on authentication/login failures
     */
    public static boolean resolve(@NonNull final Context context,
                                  @NonNull final SearchEngine searchEngine,
                                  @NonNull final Book book)
            throws CredentialsException {
        // Note we NOT using {@link book#refreshAuthors} as we want to use the site Locale.
        final Locale locale = book.getLocale(context)
                                  .orElseGet(() -> searchEngine.getLocale(context));


        final List<Author> authors = book.getAuthors();
        final List<AuthorResolver> resolvers = getResolvers(context, searchEngine);
        boolean result = false;
        try {
            final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
            // loop Authors first, this way we don't hit a single resolver
            // continuously (well... if we use more than one resolver at least)
            for (final Author author : authors) {
                authorDao.refresh(context, author, locale);
                for (final AuthorResolver resolver : resolvers) {
                    result = resolver.resolve(context, author) || result;
                }
            }
        } catch (@NonNull final SearchException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }

        return result;
    }

    /**
     * Get a list of the supported resolvers for the given engine.
     * <p>
     * Note that {@link EngineId#KbNl} does not need a resolver as all available
     * author data is present on a book page.
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
            case Goodreads: {
                final List<AuthorResolver> list = new ArrayList<>();
                if (isEnabled(context, engineId)) {
                    list.add(GoodreadsAuthorResolver.create(context, searchEngine));
                }
                if (isEnabled(context, engineId, EngineId.OpenLibrary, false)) {
                    list.add(OpenLibraryAuthorResolver.create(context, searchEngine));
                }
                return list;
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
                if (isEnabled(context, engineId, EngineId.Bedetheque, true)) {
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

    @NonNull
    public static List<EngineId> getEnabledEngines(@NonNull final Context context) {

        return Stream.of(EngineId.Bedetheque,
                         EngineId.DatabazeKnih,
                         EngineId.Dnb,
                         EngineId.Goodreads,
                         EngineId.Isfdb,
                         EngineId.LastDodoNl,
                         EngineId.StripInfoBe,
                         EngineId.StripWebBe,
                         EngineId.OpenLibrary)
                     .filter(engineId -> isEnabled(context, engineId))
                     .collect(Collectors.toList());
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
        return isEnabled(context, engineId, engineId, true);
    }

    /**
     * An engine using a given resolver.
     *
     * @param context  Current context
     * @param engine   id
     * @param resolver id
     * @param defValue default
     *
     * @return flag
     */
    private static boolean isEnabled(@NonNull final Context context,
                                     @NonNull final EngineId engine,
                                     @NonNull final EngineId resolver,
                                     final boolean defValue) {

        return ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
               && PreferenceManager.getDefaultSharedPreferences(context)
                                   .getBoolean(getKey(engine, resolver), defValue);
    }

    // Allow easy use in testing
    @VisibleForTesting
    @NonNull
    public static String getKey(@NonNull final EngineId engine,
                                @NonNull final EngineId resolver) {
        return engine.getPreferenceKey()
               + PK_RESOLVE_AUTHORS
               + resolver.getPreferenceKey();
    }
}
