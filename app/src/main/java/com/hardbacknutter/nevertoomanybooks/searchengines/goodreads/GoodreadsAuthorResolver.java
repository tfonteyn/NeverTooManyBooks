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

package com.hardbacknutter.nevertoomanybooks.searchengines.goodreads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
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
 * Goodreads does not actually provide pseudonyms except in the unstructured author-header text.
 * But we can get birth/death dates and a picture.
 */
public final class GoodreadsAuthorResolver
        implements AuthorResolver {

    /**
     * Allow with and without a slug at the end.
     * <pre>{@code
     *      https://www.goodreads.com/author/show/218173
     *      https://www.goodreads.com/author/show/218173.Zidrou
     * }</pre>
     */
    private static final Pattern SID_FROM_LOCATION_PATTERN = Pattern.compile(
            "https://www\\.goodreads\\.com/author/show/(\\d+).*");

    @NonNull
    private final GoodreadsSearchEngine searchEngine;
    @Nullable
    private final String authorUri;
    @NonNull
    private final FullDateParser dateParser;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private GoodreadsAuthorResolver(@NonNull final Context context,
                                    @NonNull final GoodreadsSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        // The engine is hardcoded/defined with the identifier,
        // but the author-uri can be absent
        authorUri = this.searchEngine
                .getEngineId()
                .getIdentifier()
                .flatMap(identifier -> identifier.getAuthorUri(context))
                .orElse(null);

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        dateParser = new FullDateParser(new ISODateParser(systemLocale),
                                        List.of(searchEngine.getLocale(context)));
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link GoodreadsSearchEngine}
     */
    @VisibleForTesting
    private GoodreadsAuthorResolver(@NonNull final Context context,
                                    @Nullable final Cancellable caller) {
        this(context, (GoodreadsSearchEngine) EngineId.Goodreads.createSearchEngine(context));
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
        if (searchEngine instanceof GoodreadsSearchEngine) {
            return new GoodreadsAuthorResolver(context,
                                               (GoodreadsSearchEngine) searchEngine);
        } else {
            return new GoodreadsAuthorResolver(context, searchEngine);
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

        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        // no id, give up
        if (oIv.isEmpty()) {
            return false;
        }

        final String url = String.format(authorUri, oIv.get());
        final Document document = searchEngine.loadDocument(context, url, null);
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
                 @NonNull final Document document) {

        final Matcher matcher = SID_FROM_LOCATION_PATTERN.matcher(document.location());
        if (!matcher.find()) {
            // should never happen
            return null;
        }
        final String sid = matcher.group(1);

        final Element nameSpan = document.selectFirst("span[itemprop=name]");
        if (nameSpan == null) {
            return null;
        }

        final Author author = searchEngine.mapAuthor(context, nameSpan.text());
        author.setIdentifierValue(Identifier.SID_GOODREADS, sid);

        Element element;
        element = document.selectFirst("div.dataItem[itemprop=birthDate]");
        if (element != null) {
            dateParser.parse(element.text()).ifPresent(
                    birthDate -> author.setBirthDate(birthDate.format(DateTimeFormatter.ISO_DATE)));
        }
        element = document.selectFirst("div.dataItem[itemprop=deathDate]");
        if (element != null) {
            dateParser.parse(element.text()).ifPresent(
                    deathDate -> author.setDeathDate(deathDate.format(DateTimeFormatter.ISO_DATE)));
        }

        element = document.head().selectFirst("meta[itemprop=image]");
        if (element != null) {
            final String url = element.attr("content");
            // will contain "/nophoto/" for none
            if (url.contains("/authors/")) {
                try {
                    searchEngine.saveImage(context, url, null, sid, 0, null)
                                .ifPresent(author::setTmpPictureFileSpec);
                } catch (@NonNull final StorageException ignore) {
                    // ignore
                }
            }
        }
        return author;
    }
}
