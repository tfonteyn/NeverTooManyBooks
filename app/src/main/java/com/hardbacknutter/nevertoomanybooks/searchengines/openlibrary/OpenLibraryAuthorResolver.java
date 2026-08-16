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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
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

/**
 * It's a little unreliable... see comments inside {@link AuthorParser}.
 * <p>
 * Available:
 * - birthdate
 * - death date
 * - picture
 * - bio: text, or type/value(text)
 * - links to external sites
 */
public final class OpenLibraryAuthorResolver
        implements AuthorResolver {

    private static final String CHARSET = "UTF-8";

    private static final String AUTHOR_SEARCH = "https://openlibrary.org/search/authors.json?q=";

    @NonNull
    private final OpenLibrarySearchEngine searchEngine;
    @NonNull
    private final AuthorParser authorParser;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    @VisibleForTesting
    OpenLibraryAuthorResolver(@NonNull final Context context,
                                      @NonNull final OpenLibrarySearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        authorParser = new AuthorParser(context, searchEngine);
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link OpenLibrarySearchEngine}
     */
    @VisibleForTesting
    private OpenLibraryAuthorResolver(@NonNull final Context context,
                                      @Nullable final Cancellable caller) {
        this(context, (OpenLibrarySearchEngine) EngineId.OpenLibrary.createSearchEngine(context));
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

        if (AuthorResolverHelper.isEnabled(EngineId.OpenLibrary)) {
            final AuthorResolver ar;
            if (searchEngine instanceof OpenLibrarySearchEngine) {
                ar = new OpenLibraryAuthorResolver(
                        context, (OpenLibrarySearchEngine) searchEngine);
            } else {
                ar = new OpenLibraryAuthorResolver(context, searchEngine);
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
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        if (oIv.isPresent()) {
            found = searchBySid(context, oIv.get());
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
            found = searchByName(context, author.getFormattedName(true));
            // 2025-05-10: insist on case-sensitive name equality for now.
            // If this proves problematic, we'll change it later...
            if (found != null && author.isSameName(found)) {
                return author.merge(found, true);
            }
        }

        return false;
    }

    /**
     * As usual with OpenLibrary, the JSON returned here is (slightly) DIFFERENT
     * from the JSON returned in {@link #searchByName}. Sigh...
     *
     * @param context Current context
     * @param sid     to search
     *
     * @return the {@link Author}; or {@code null} if not found
     *
     * @throws SearchException on generic exceptions (wrapped) during search
     */
    @Nullable
    private Author searchBySid(@NonNull final Context context,
                               @NonNull final String sid)
            throws SearchException {

        final String url = String.format(OpenLibrarySearchEngine.AUTHOR_URL, sid) + ".json";

        final FutureHttp<String> httpGet = searchEngine.createGetDocumentRequest();
        try {
            final String response = httpGet.getAsString(url, (con, s) -> s);
            final JSONObject document = new JSONObject(response);
            if (!searchEngine.isCancelled()) {
                return authorParser.parse(context, document);
            }
        } catch (@NonNull final StorageException | IOException | JSONException e) {
            throw new SearchException(searchEngine.getEngineId(), e);
        }
        return null;
    }

    /**
     * As usual with OpenLibrary, the JSON (docs[0]) returned here is (slightly) DIFFERENT
     * from the JSON returned in {@link #searchBySid}. Sigh...
     *
     * @param context Current context
     * @param names   to search for
     *
     * @return the {@link Author}; or {@code null} if not found
     *
     * @throws SearchException on generic exceptions (wrapped) during search
     */
    @Nullable
    private Author searchByName(@NonNull final Context context,
                                @NonNull final String names)
            throws SearchException {

        final FutureHttp<String> httpGet = searchEngine.createGetDocumentRequest();
        try {
            final String url = AUTHOR_SEARCH + URLEncoder.encode(names, CHARSET);

            final String response = httpGet.getAsString(url, (con, s) -> s);
            final JSONObject document = new JSONObject(response);
            if (!searchEngine.isCancelled()) {
                final int numFound = document.optInt("numFound");
                if (numFound < 1) {
                    return null;
                }
                final JSONArray docs = document.optJSONArray("docs");
                if (docs != null && !docs.isEmpty()) {
                    return authorParser.parse(context, docs.getJSONObject(0));
                }
            }
        } catch (@NonNull final StorageException | IOException | JSONException e) {
            throw new SearchException(searchEngine.getEngineId(), e);
        }
        return null;
    }
}
