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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

public final class DnbAuthorResolver
        implements AuthorResolver {

    @NonNull
    private final DnbSearchEngine searchEngine;
    /**
     * Param 1: SRU query.
     * <p>
     * Hardcoded to return a single result for now.
     */
    private static final String SRU_URL = "https://services.dnb.de/sru/authorities?"
                                          + "version=1.1"
                                          + "&operation=searchRetrieve"
                                          + "&query=%1$s"
                                          + "&recordSchema=MARC21-xml"
                                          + "&maximumRecords=1";

    /**
     * Private Constructor.
     *
     * @param searchEngine the engine
     */
    @VisibleForTesting
    DnbAuthorResolver(@NonNull final DnbSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link DnbSearchEngine}
     */
    @VisibleForTesting
    private DnbAuthorResolver(@NonNull final Context context,
                              @Nullable final Cancellable caller) {
        this((DnbSearchEngine) EngineId.Dnb.createSearchEngine(context));
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

        if (AuthorResolverHelper.isEnabled(EngineId.Dnb)) {
            final AuthorResolver ar;
            if (searchEngine instanceof DnbSearchEngine) {
                ar = new DnbAuthorResolver((DnbSearchEngine) searchEngine);
            } else {
                ar = new DnbAuthorResolver(context, searchEngine);
            }
            return List.of(ar);
        }
        return List.of();
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {

        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DNB);
        // no id, give up
        if (oIv.isEmpty()) {
            return false;
        }

        final String query = "nid=" + oIv.get();
        final String url = String.format(SRU_URL, URLEncoder
                .encode(query, StandardCharsets.UTF_8)
                .replace("+", "%20"));

        final Document document = searchEngine.loadDocument(context, Parser.xmlParser(), url, null);

        if (!searchEngine.isCancelled()) {
            final Author found = parse(document);
            if (found != null) {
                boolean modified = author.merge(found, true);
                if (author.isSameName(found) && !author.isIdenticalName(found)) {
                    // correct diacritics difference
                    author.setName(found.getFamilyName(), found.getGivenNames());
                    modified = true;
                }
                return modified;
            }
        }

        return false;
    }

    @VisibleForTesting
    @Nullable
    Author parse(@NonNull final Document document) {

        final DnbParser parser = new DnbParser(document);

        // There will only be one or none
        final Elements tags = document.select("datafield[tag='100']");
        final List<Author> authors = parser.author(tags);
        if (authors.isEmpty()) {
            return null;
        }

        final Author author = authors.get(0);

        final List<Identifier.Value> ivs = parser.identifiers();

        // add the DNB one to the front of the list
        final Identifier.Value value = parser.cf001();
        if (value != null) {
            ivs.add(0, value);
        }

        ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
        if (!ivs.isEmpty()) {
            author.setIdentifiers(ivs);
        }
        return author;
    }
}
