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

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * FIXME: we're a bit [bleeped] here due to using the beta website.
 * The author permalink takes us to the old site which we don't parse
 * because we mistakenly believed that DNB would actually finish
 * the work on the beta in a reasonable timeframe...
 * <p>
 * Available:
 * - Birthdate
 * - Deathdate
 * - Birthplace
 * - gender
 * - Akademischer Grad bzw. Titel
 * - Profession
 * - info
 */
public final class DnbAuthorResolver
        implements AuthorResolver {

    /**
     * {@code <p>1920-1992 <small>(Lebensdaten)</small></p>}
     * {@code <p>1972- <small>(Lebensdaten)</small></p>}
     */
    private static final Pattern BIRTH_DEATH_DATE_PATTERN = Pattern.compile(
            "(\\d\\d\\d\\d)-(\\d\\d\\d\\d|)\\s*\\(Lebensdaten\\)");

    @NonNull
    private final DnbSearchEngine searchEngine;
    @Nullable
    private final String authorUri;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private DnbAuthorResolver(@NonNull final Context context,
                              @NonNull final DnbSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        // hardcoded to the beta website
        authorUri = DnbSearchEngine.KATALOG_DNB_DE + "/DE/resource.html?id=%s";
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
        this(context, (DnbSearchEngine) EngineId.Dnb.createSearchEngine(context));
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
    public static AuthorResolver create(@NonNull final Context context,
                                        @NonNull final SearchEngine searchEngine) {
        if (searchEngine instanceof DnbSearchEngine) {
            return new DnbAuthorResolver(context,
                                         (DnbSearchEngine) searchEngine);
        } else {
            return new DnbAuthorResolver(context, searchEngine);
        }
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {
        // the user can delete it...
        if (authorUri == null) {
            return false;
        }

        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DNB);
        // no id, give up
        if (oIv.isEmpty()) {
            return false;
        }
        // We investigated searching by author name, but this seems hard to achieve.
        // e.g. a search for "Isaac Asimov" returned:
        // Ihre Suchanfrage ergab 528 Treffer.
        //   506 Treffer in Medien
        //   und 22 Treffer im Wissensnetz
        // => the Wissensnetz list of links does contain an entry "Person" as the 4th or 5th
        // element (the link does NOT have the author id) which we then need to follow
        // to get to the author page. The request/response and parsing overhead is just
        // too large.

        final String url = String.format(authorUri, oIv.get());

        final Document document = searchEngine.loadDocument(context, url, Map.of(
                HttpConstants.REFERER, DnbSearchEngine.KATALOG_DNB_DE));

        if (!searchEngine.isCancelled()) {
            final Author found = parse(context, document);
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
    Author parse(@NonNull final Context context,
                 @NonNull final Document document)
            throws SearchException, CredentialsException {

        Author author = null;
        // relative url
        String realAuthorUrl = null;
        boolean isPseudonym = false;

        final Element tableElement = document.selectFirst("table.c-catalog-table__table");
        // We could collapse the above select with the select for the tr's
        // But there is a tbody in between, let's take the safe route for now
        if (tableElement != null) {
            final Elements trs = tableElement.select("tr.c-catalog-table__row");
            for (final Element tr : trs) {
                final Element label = tr.selectFirst("th.c-catalog-table__head > p");
                if (label != null) {
                    final Element td = tr.selectFirst("td.c-catalog-table__content > p");
                    if (td != null) {
                        final String s = label.text();
                        // We should get the german labels, but it seems we might get the
                        // English ones despite the "DE" in the url. So... check on both!
                        switch (s) {
                            case "Name": {
                                author = Author.from(td.text());
                                break;
                            }
                            case "Wirklicher Name":
                            case "Real name": {
                                final Element a = td.selectFirst("a");
                                if (a != null) {
                                    realAuthorUrl = a.attr("href");
                                }
                                break;
                            }
                            case "Zeit":
                            case "Time": {
                                final Matcher matcher = BIRTH_DEATH_DATE_PATTERN.matcher(td.text());
                                if (matcher.find()) {
                                    final String birthYear = matcher.group(1);
                                    if (birthYear != null && !birthYear.isEmpty()) {
                                        //noinspection DataFlowIssue
                                        author.setBirthDate(birthYear);
                                    }
                                    final String deathYear = matcher.group(2);
                                    if (deathYear != null && !deathYear.isEmpty()) {
                                        //noinspection DataFlowIssue
                                        author.setDeathDate(deathYear);
                                    }
                                }
                                break;
                            }
                            case "Ort":
                            case "Place": {
                                break;
                            }
                            case "Geschlecht":
                            case "Gender": {
                                // männlich
                                // weiblich
                                break;
                            }
                            case "Akademischer Grad bzw. Titel":
                            case "Academic degree": {
                                break;
                            }
                            case "Weitere Angaben":
                            case "Further information": {
                                break;
                            }
                            case "Datensatztyp":
                            case "Record type": {
                                // can be one of:
                                // Person
                                // Pseudonym
                                isPseudonym = "Pseudonym".equals(td.text());
                                break;
                            }
                            case "Datensatz-ID":
                            case "Record ID": {
                                if (author != null) {
                                    author.setIdentifierValue(Identifier.SID_DNB, td.text());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (author != null && isPseudonym && realAuthorUrl != null) {
            final String url = searchEngine.getHostUrl() + '/' + realAuthorUrl;
            final Document raDoc = searchEngine.loadDocument(context, url, Map.of(
                    HttpConstants.REFERER, document.location()));
            if (!searchEngine.isCancelled()) {
                final Author realAuthor = parse(context, raDoc);
                if (realAuthor != null) {
                    author.setRealAuthor(realAuthor);
                }
            }
        }

        return author;
    }
}
