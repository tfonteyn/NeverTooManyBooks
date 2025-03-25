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

package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public final class IsfdbAuthorResolver
        implements AuthorResolver {

    private static final String TAG = "IsfdbAuthorResolver";

    @NonNull
    private final IsfdbSearchEngine searchEngine;
    @Nullable
    private final String authorUri;
    @NonNull
    private final String authorSearchUrl;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private IsfdbAuthorResolver(@NonNull final Context context,
                                @NonNull final IsfdbSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        // The engine is hardcoded/defined with the identifier,
        // but the author-uri can be absent
        //noinspection OptionalGetWithoutIsPresent
        authorUri = searchEngine.getEngineId()
                                .getIdentifier().get()
                                .getAuthorUri(context).orElse(null);

        authorSearchUrl = searchEngine.getHostUrl(context) + "/cgi-bin/se.cgi?arg=%s&type=Name";
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link IsfdbSearchEngine}
     */
    private IsfdbAuthorResolver(@NonNull final Context context,
                                @Nullable final Cancellable caller) {
        this(context, (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context));
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
        if (searchEngine instanceof IsfdbSearchEngine) {
            return new IsfdbAuthorResolver(context, (IsfdbSearchEngine) searchEngine);
        } else {
            return new IsfdbAuthorResolver(context, searchEngine);
        }
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {

        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        final Author found;
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        if (oIv.isEmpty()) {
            found = searchByName(context, author.getFormattedName(true));
        } else {
            found = searchBySid(context, oIv.get());
        }

        if (found != null) {
            author.setName(found.getFamilyName(), found.getGivenNames());
            author.setRealAuthor(found.getRealAuthor());
            author.addIdentifiers(found.getIdentifiers());
            ServiceLocator.getInstance().getIdentifierDao()
                          .pruneList(author.getIdentifiers());
            return true;
        }

        return false;
    }

    @Nullable
    private Author searchByName(@NonNull final Context context,
                                @NonNull final String names)
            throws SearchException, CredentialsException {

        final String url = String.format(authorSearchUrl, names);
        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            return parse(context, document);
        }
        return null;
    }

    @Nullable
    private Author searchBySid(@NonNull final Context context,
                               @NonNull final String sid)
            throws SearchException, CredentialsException {
        // the user can delete it...
        if (authorUri == null) {
            return null;
        }
        final String url = String.format(authorUri, sid);
        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            return parse(context, document);
        }
        return null;
    }

    @Nullable
    private Author parse(@NonNull final Context context,
                         @NonNull final Document document)
            throws SearchException, CredentialsException {
        final String pageUrl = document.location();
        if (pageUrl.contains("ea.cgi")) {
            final String sid = getIdFromUrl(pageUrl);
            if (sid != null) {
                return parse(context, document, sid);
            }
        } else if (pageUrl.contains("se.cgi")) {
            // multi result: cannot be handled.
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().w(TAG, "parse|pageUrl=" + pageUrl);
            }
        } else {
            // dunno, let's log it
            LoggerFactory.getLogger().w(TAG, "parse|pageUrl=" + pageUrl);
        }
        return null;
    }

    @Nullable
    private Author parse(@NonNull final Context context,
                         @NonNull final Document document,
                         @NonNull final String sid)
            throws SearchException, CredentialsException {
        final Element root = document.selectFirst("div.ContentBox");
        if (root == null) {
            return null;
        }

        Element element;
        element = root.selectFirst("b:contains(Legal Name:)");
        if (element == null) {
            element = root.selectFirst("b:contains(Author:)");
            if (element == null) {
                return null;
            }
        }
        final Node node = element.nextSibling();
        if (node == null) {
            return null;
        }
        final Author author = Author.from(node.toString());
        author.setIdentifierValue(Identifier.SID_ISFDB, sid);

        // test for an "Alternate Name" or a "real name" result.
        final Element altNameElement = root.selectFirst(
                "b:contains(Used As Alternate Name By:)");
        if (altNameElement != null) {
            // we're on an "Alternate Name." result page
            element = altNameElement.nextElementSibling();
            // extract the sid of the 'real' author name
            if (element != null && "a".equals(element.tag().getName())) {
                final String realAuthorSid = getIdFromUrl(element.attr("href"));
                if (realAuthorSid != null) {
                    // and follow the link to get the real author details
                    final Author realAuthor = searchBySid(context, realAuthorSid);
                    if (realAuthor != null) {
                        author.setRealAuthor(realAuthor);
                    }
                }
            }
        } else {
            // we're on a "real name" result page.
            parseExtraData(root, author);
        }

        return author;
    }

    @Nullable
    private String getIdFromUrl(@Nullable final String url) {
        if (url == null) {
            return null;
        }
        final String[] split = url.split("\\?");
        if (split.length > 1) {
            final String sid = split[1];
            if (!sid.isEmpty()) {
                return sid;
            }
        }
        return null;
    }

    public void parseExtraData(@NonNull final Element root,
                               @NonNull final Author author) {

        final List<Tag> tags = new ArrayList<>();
        String birthPlace = null;
        String birthDate = null;
        String deathDate = null;
        String imageUrl = null;

        final Element image = root.selectFirst("img[alt='Author Picture']");
        if (image != null) {
            imageUrl = image.attr("src");
        }

        final Elements lis = root.select("li");
        for (final Element li : lis) {
            final Element label = li.selectFirst("b");
            if (label != null) {
                final String lblText = label.text();
                switch (lblText) {
                    case "Birthplace:": {
                        final Node node = label.nextSibling();
                        if (node != null) {
                            birthPlace = node.toString();
                        }
                        break;
                    }
                    case "Birthdate:": {
                        final Node node = label.nextSibling();
                        if (node != null) {
                            birthDate = node.toString();
                        }
                        break;
                    }
                    case "Deathdate:": {
                        final Node node = label.nextSibling();
                        if (node != null) {
                            deathDate = node.toString();
                        }
                        break;
                    }
                    case "Language:":
                        break;
                    case "Webpages:":
                        break;
                    case "Used These Alternate Names:":
                        break;
                    case "Note:":
                        break;
                    case "Additional Bibliographic Comments:":
                        break;
                    case "Author Tags:":
                        final Elements as = li.select("a");
                        for (final Element a : as) {
                            final String text = a.text();
                            if (!text.startsWith("View all tags")) {
                                tags.add(new Tag(text));
                            }
                        }
                        break;

                }
            }
        }

        Log.d(TAG, "imageUrl: " + imageUrl);
        Log.d(TAG, "birthPlace: " + birthPlace);
        Log.d(TAG, "birthDate: " + birthDate);
        Log.d(TAG, "deathDate: " + deathDate);
        Log.d(TAG, "tags: " + tags);
    }
}
