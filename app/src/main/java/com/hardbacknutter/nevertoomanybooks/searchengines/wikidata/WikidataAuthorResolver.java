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

package com.hardbacknutter.nevertoomanybooks.searchengines.wikidata;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public final class WikidataAuthorResolver
        implements AuthorResolver {

    private static final String CHARSET = "UTF-8";

    /**
     * Param 1: two-char language code.
     * Param 2: search term.
     */
    private static final String AUTHOR_SEARCH_BY_NAME =
            "https://www.wikidata.org/w/api.php"
            + "?action=wbsearchentities"
            + "&language=%1$s"
            + "&format=json&search=%2$s";

    /**
     * Param 1: sid.
     */
    private static final String AUTHOR_SEARCH_BY_SID =
            "https://www.wikidata.org/wiki/Special:EntityData/%1$s.json";

    /**
     * The user Locale language; 2-char ISO-639 code.
     * Used to select the desired "label" in multi-language responses,
     * and as the query param when possible.
     *
     * @see #AUTHOR_SEARCH_BY_NAME
     */
    @NonNull
    private final String langCode;


    @NonNull
    private final WikidataSearchEngine searchEngine;
    @NonNull
    private final WikidataAuthorParser authorParser;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    @VisibleForTesting
    WikidataAuthorResolver(@NonNull final Context context,
                           @NonNull final WikidataSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        authorParser = new WikidataAuthorParser(context, searchEngine);

        langCode = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link WikidataSearchEngine}
     */
    @VisibleForTesting
    private WikidataAuthorResolver(@NonNull final Context context,
                                   @Nullable final Cancellable caller) {
        this(context, EngineId.Wikidata.createSearchEngine(context));
        searchEngine.setCaller(caller);
    }

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine which is requesting this resolver
     *
     * @return new instance
     */
    @NonNull
    public static List<AuthorResolver> create(@NonNull final Context context,
                                              @NonNull final SearchEngine searchEngine) {

        if (AuthorResolverHelper.isEnabled(EngineId.Wikidata)) {
            final AuthorResolver ar;
            if (searchEngine instanceof WikidataSearchEngine) {
                ar = new WikidataAuthorResolver(context, (WikidataSearchEngine) searchEngine);
            } else {
                ar = new WikidataAuthorResolver(context, searchEngine);
            }
            return List.of(ar);
        }
        return List.of();
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {
        final Author found;
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        if (oIv.isPresent()) {
            found = searchBySid(oIv.get());
            if (found != null) {
                boolean modified = author.merge(found, true);
                if (author.isSameName(found) && !author.isIdenticalName(found)) {
                    // correct diacritics difference
                    author.setName(found.getFamilyName(), found.getGivenNames());
                    modified = true;
                }
                return modified;
            }
        } else {
            found = searchByName(author.getFormattedName(true));
            // 2025-05-10: insist on case-sensitive name equality for now.
            // If this proves problematic, we'll change it later...
            if (found != null && author.isSameName(found)) {
                return author.merge(found, true);
            }
        }

        return false;
    }

    @Nullable
    private Author searchBySid(@NonNull final String sid)
            throws SearchException {

        final String url = String.format(AUTHOR_SEARCH_BY_SID, sid);

        final HttpCall httpCall = searchEngine.getHttpCallFactory().createCall();
        try {
            final String response = httpCall.getAsString(url, null);
            final JSONObject document = new JSONObject(response);
            if (!searchEngine.isCancelled()) {
                return authorParser.parse(langCode, document, sid);
            }
        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(searchEngine.getEngineId(), e);
        }
        return null;
    }

    @Nullable
    private Author searchByName(@NonNull final String names)
            throws SearchException {

        final HttpCall httpCall = searchEngine.getHttpCallFactory().createCall();
        try {
            final String url = String.format(AUTHOR_SEARCH_BY_NAME, langCode,
                                             URLEncoder.encode(names, CHARSET));

            final String response = httpCall.getAsString(url, null);
            final JSONObject document = new JSONObject(response);
            if (!searchEngine.isCancelled()) {
                final JSONArray docs = document.optJSONArray("search");
                if (docs != null && !docs.isEmpty()) {
                    final JSONObject entry = docs.getJSONObject(0);
                    final String sid = entry.optString("id");
                    if (!sid.isEmpty()) {
                        return searchBySid(sid);
                    }
                }
            }
        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(searchEngine.getEngineId(), e);
        }
        return null;
    }
}
