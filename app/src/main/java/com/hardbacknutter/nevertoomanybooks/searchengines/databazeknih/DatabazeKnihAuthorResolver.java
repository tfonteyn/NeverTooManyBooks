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

package com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Connects to the DatabazeKnih website to resolve author pseudonyms.
 * <p>
 * {@link AuthorResolver#resolve(Context, Author)} relies on the
 * {@link Identifier#SID_DATABAZE_KNIH} being correct to do  the lookup.
 * It will:
 * <ul>
 *     <li>add the {@link Author#setRealAuthor(Author)} if applicable</li>
 * </ul>
 * <p>
 * Available:
 * - Birthdate
 * - Deathdate
 * - country
 * <p>
 * A search by name is possible.
 * {@code "https://www.databazeknih.cz/search?in=authors&q=" + formattedName};
 * but there is no point as a book search will always have the author sid available anyhow.
 */
public final class DatabazeKnihAuthorResolver
        implements AuthorResolver {

    // <a href="/filtrovani-autoru?nationId=80">britská</a><span class="gray">,</span>  1916 - 1990
    // <a href="/filtrovani-autoru?nationId=79">americká</a><span class="gray">,</span>  1948
    // <a href="/filtrovani-autoru?nationId=13">česká</a>
    private static final Pattern BIRTH_DEATH_DATE_PATTERN = Pattern.compile(
            ".*,\\s*(\\d\\d\\d\\d)\\s*(?:-\\s*|)(\\d\\d\\d\\d|)");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");

    @NonNull
    private final DatabazeKnihSearchEngine searchEngine;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private DatabazeKnihAuthorResolver(@NonNull final Context context,
                                       @NonNull final DatabazeKnihSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link DatabazeKnihSearchEngine}
     */
    @VisibleForTesting
    private DatabazeKnihAuthorResolver(@NonNull final Context context,
                                       @Nullable final Cancellable caller) {
        this(context, (DatabazeKnihSearchEngine) EngineId.DatabazeKnih.createSearchEngine(context));
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
        if (searchEngine instanceof DatabazeKnihSearchEngine) {
            return new DatabazeKnihAuthorResolver(context,
                                                  (DatabazeKnihSearchEngine) searchEngine);
        } else {
            return new DatabazeKnihAuthorResolver(context, searchEngine);
        }
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {

        final Author found;
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        if (oIv.isPresent()) {
            found = searchBySid(context, oIv.get());
            if (found != null) {
                boolean modified = author.merge(found, true);
                // DatabaseKnih uses the same id for pseudonym names.
                // force the real-author to have the same id
                final Author realAuthor = author.getRealAuthor();
                if (realAuthor != null) {
                    author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH)
                          .ifPresent(id -> realAuthor.setIdentifierValue(
                                  Identifier.SID_DATABAZE_KNIH, id));
                }
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

    @Nullable
    private Author searchBySid(@NonNull final Context context,
                               @NonNull final String sid)
            throws SearchException, CredentialsException {

        final String url = String.format(DatabazeKnihSearchEngine.AUTHOR_URL, sid);
        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            return parse(context, document, sid);
        }
        return null;
    }

    @Nullable
    private Author parse(@NonNull final Context context,
                         @NonNull final Document document,
                         @NonNull final String sid) {
        final Element section = document.selectFirst("div#left_less");
        if (section == null) {
            return null;
        }
        final Element realNameElement = section.selectFirst("h1");
        if (realNameElement == null) {
            return null;
        }
        final String realName = realNameElement.text();
        if (realName.isEmpty()) {
            return null;
        }

        // Always present, but can be empty
        String pseudonym = null;
        final Element pseudonymElement = section.selectFirst("h2");
        if (pseudonymElement != null) {
            final String text = pseudonymElement.text();
            if (text.contains("pseudonym")) {
                final Element a = pseudonymElement.selectFirst("a");
                if (a != null) {
                    pseudonym = a.text();
                }
            }
        }

        final Author author;
        @Nullable
        final Author realAuthor;
        if (pseudonym == null) {
            author = Author.from(realName);
            realAuthor = null;
        } else {
            // The author of the book IS the pseudonym (as listed on the book)
            author = Author.from(pseudonym);
            realAuthor = Author.from(realName);
            author.setRealAuthor(realAuthor);
        }

        final Element birthEtcElement = section.selectFirst("p");
        if (birthEtcElement != null) {
            final Matcher matcher = BIRTH_DEATH_DATE_PATTERN.matcher(birthEtcElement.text());
            if (matcher.find()) {
                final String birthYear = matcher.group(1);
                if (birthYear != null && !birthYear.isEmpty()) {
                    author.setBirthDate(birthYear);
                    if (realAuthor != null) {
                        realAuthor.setBirthDate(birthYear);
                    }
                }
                final String deathYear = matcher.group(2);
                if (deathYear != null && !deathYear.isEmpty()) {
                    author.setDeathDate(deathYear);
                    if (realAuthor != null) {
                        realAuthor.setDeathDate(birthYear);
                    }
                }
            }
        }

        final Element picElement = section.selectFirst("div.img_author_detail");
        if (picElement != null) {
            // style="background-image:url("https://www.databazeknih.cz/...")"
            // no parsing, just brute-force-match to get the "middle" of this value -> url
            final String[] parts = QUOTE_PATTERN.split(picElement.attr("style"));
            if (parts.length == 3) {
                final String url = parts[1];
                if (!url.contains("empty-author") && !url.contains("antologie-kolektiv-autoru")) {
                    try {
                        searchEngine.saveImage(context, url, null, sid, 0, null)
                                    .ifPresent(fileSpec -> {
                                        author.setTmpPictureFileSpec(fileSpec);
                                        if (realAuthor != null) {
                                            realAuthor.setImageUuid(fileSpec);
                                        }
                                    });
                    } catch (@NonNull final StorageException ignore) {
                        // ignore
                    }
                }
            }
        }
        return author;
    }
}
