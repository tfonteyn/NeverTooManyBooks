/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * Connects to the Bedetheque website to resolve author pseudonyms.
 * <p>
 * Aside of Bedetheque itself, this class is also used by StripInfo and LastDodo.
 */
public class BedethequeAuthorResolver
        implements AuthorResolver {

    private static final String TAG = "BedethequeAuthorResolver";

    @NonNull
    private final Context context;
    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale locale;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private BedethequeAuthorResolver(@NonNull final Context context,
                                     @NonNull final BedethequeSearchEngine searchEngine) {
        this.context = context;
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link BedethequeSearchEngine}
     */
    @VisibleForTesting
    public BedethequeAuthorResolver(@NonNull final Context context,
                                    @Nullable final Cancellable caller) {
        this.context = context;
        searchEngine = (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(caller);
        locale = searchEngine.getLocale(context);
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
        if (searchEngine instanceof BedethequeSearchEngine) {
            return new BedethequeAuthorResolver(context,
                                                (BedethequeSearchEngine) searchEngine);
        } else {
            return new BedethequeAuthorResolver(context, searchEngine);
        }
    }


    /**
     * Take the first character from the given name and normalize it to [0A-Z]
     * for use with the other class methods.
     *
     * @param name to use
     *
     * @return [0A-Z] of the first character
     */
    private char firstChar(@NonNull final CharSequence name) {
        final String normalized = SqlEncode.normalize(String.valueOf(name.charAt(0)));
        if (normalized.isEmpty()) {
            return '0';
        }
        final char c1 = normalized.toUpperCase(locale).charAt(0);
        return Character.isAlphabetic(c1) ? c1 : '0';
    }

    @Override
    public boolean resolve(@NonNull final Author author)
            throws SearchException, CredentialsException {

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        // We SHOULD pass in the book-locale here...
        authorDao.refresh(context, author, locale);

        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        final String name = author.getFormattedName(false);

        final BedethequeCacheDao cacheDao = ServiceLocator.getInstance().getBedethequeCacheDao();
        // Check if we have the author in the cache
        BdtAuthor bdtAuthor = cacheDao.findByName(name, locale).orElse(null);
        if (bdtAuthor == null) {
            // If not resolved / not found,
            final AuthorListLoader pageLoader = new AuthorListLoader(context, searchEngine);
            final char c1 = firstChar(author.getFamilyName());
            // and the list-page was never fetched before,
            if (!cacheDao.isAuthorPageCached(c1)) {
                // go fetch the the list-page on which the author should/could be
                if (pageLoader.fetch(c1)) {
                    // If the author was on the list page, we should find it in the cache now.
                    bdtAuthor = cacheDao.findByName(name, locale).orElse(null);
                }
            }
        }

        // If the author is still not found in the cache, give up
        if (bdtAuthor == null) {
            return false;
        }

        // we have it in the cache, check if it's fully resolved
        if (!bdtAuthor.isResolved()) {
            if (!lookup(bdtAuthor)) {
                // The website list page had it, but there is no details page.
                // We should never get here... flw
                return false;
            }
        }

        // it should now be resolved.

        final String resolvedName = bdtAuthor.getResolvedName();
        // If the author does not use a pen-name, we're done.
        if (resolvedName == null) {
            return false;
        }

        // The name was a pen-name and we have resolved it to their real name
        // Add it accordingly to the original Author object
        final Author realAuthor = Author.from(resolvedName);
        authorDao.refresh(context, realAuthor, locale);
        author.setRealAuthor(realAuthor);

        // While resolving, the name of the bdtAuthor CAN be overwritten.
        // Check that it still MATCHES the original author name
        final Author penAuthor = Author.from(bdtAuthor.getName());
        // Case-sensitive! We must allow correcting the case.
        if (penAuthor.isSameName(author)) {
            // It does, we now overwrite the original name; this will correct any diacritics
            author.setName(penAuthor.getFamilyName(), penAuthor.getGivenNames());
        }

        return true;
    }


    /**
     * Lookup the author on the website.
     * If successful, it will have been updated in the cache database.
     *
     * @param bdtAuthor to lookup
     *
     * @return {@code true} on success
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    private boolean lookup(@NonNull final BdtAuthor bdtAuthor)
            throws SearchException, CredentialsException {
        final String url = bdtAuthor.getUrl();
        if (url == null || url.isEmpty()) {
            return false;
        }

        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            if (parseAuthor(document, bdtAuthor)) {
                try {
                    ServiceLocator.getInstance().getBedethequeCacheDao()
                                  .update(bdtAuthor, locale);
                    return true;
                } catch (@NonNull final DaoWriteException e) {
                    // log, but ignore - should never happen unless disk full
                    LoggerFactory.getLogger().e(TAG, e);
                }
            }
        }
        return false;
    }

    /**
     * Parse the downloaded document and update the given {@link BdtAuthor} if possible.
     *
     * @param document  to parse
     * @param bdtAuthor to update
     *
     * @return {@code true} on success
     */
    @VisibleForTesting
    boolean parseAuthor(@NonNull final Document document,
                        @NonNull final BdtAuthor bdtAuthor) {

        final Element info = document.selectFirst("div.auteur-infos ul.auteur-info");
        if (info != null) {
            final Elements labels = info.getElementsByTag("label");
            String familyName = "";
            String givenName = "";
            String penName = "";

            for (final Element label : labels) {
                //noinspection SwitchStatementWithoutDefaultBranch
                switch (label.text()) {
                    case "Nom :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            familyName = span.text();
                        }
                        break;
                    }
                    case "Prénom :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            givenName = span.text();
                        }
                        break;
                    }
                    case "Pseudo :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            penName = textNode.toString();
                        }
                        break;
                    }
                }
            }

            // sanity check
            if (!familyName.isEmpty()) {
                bdtAuthor.setResolvedName(
                        familyName + (givenName.isBlank() ? "" : ", " + givenName));
                return true;
            }
        }

        bdtAuthor.setResolvedName(null);
        return false;
    }
}
