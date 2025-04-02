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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
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
 * Authors are searched for in the local cache database, or fetched in bulk
 * from the website and stored in the cache.
 * <p>
 * {@link AuthorResolver#resolve(Context, Author)} will:
 * <ul>
 *     <li>add the {@link Identifier#SID_BEDETHEQUE} if it's missing</li>
 *     <li>update/correct any diacritics in the names</li>
 *     <li>add the {@link Author#setRealAuthor(Author)} if applicable</li>
 * </ul>
 * <p>
 * Aside of Bedetheque itself, this class is also used by StripInfo and LastDodo.
 *
 * @see BedethequeCacheDao
 * @see AuthorListLoader
 */
public class BedethequeAuthorResolver
        implements AuthorResolver {

    private static final String TAG = "BedethequeAuthorResolver";

    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale locale;
    private final FullDateParser dateParser;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private BedethequeAuthorResolver(@NonNull final Context context,
                                     @NonNull final BedethequeSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        final List<Locale> locales = LocaleListUtils.asList(context, locale);
        dateParser = new FullDateParser(new ISODateParser(systemLocale), locales);
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
        this(context, (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine(context));
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
        if (searchEngine instanceof BedethequeSearchEngine) {
            return new BedethequeAuthorResolver(context,
                                                (BedethequeSearchEngine) searchEngine);
        } else {
            return new BedethequeAuthorResolver(context, searchEngine);
        }
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        // We SHOULD pass in the book-locale here...
        authorDao.refresh(context, author, locale);

        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        final BdtAuthor bdtAuthor = lookupInCache(context, author);
        // If the author is not found, the website does not know that name at all, give up
        if (bdtAuthor == null) {
            return false;
        }

        boolean modified = false;

        // Add/update the identifier while we have it.
        final String bdtId = bdtAuthor.getBdtId();
        if (bdtId != null) {
            author.setIdentifierValue(Identifier.SID_BEDETHEQUE, bdtId);
            modified = true;
        }

        // we have it in the cache, check if it's fully resolved
        if (!bdtAuthor.isResolved()) {
            // load the details-page from the site, and parse it.
            if (!lookupOnSite(context, bdtAuthor, author)) {
                // The website list page had it, but there is no details page.
                // We should never get here... flw
                return false;
            }
        }

        // it should now be resolved
        // Copy temporary info from bdtAuthor to the author, and resolve the realAuthor
        if (resolvePenName(context, author, bdtAuthor)) {
            modified = true;
        }

        return modified;
    }

    private boolean resolvePenName(@NonNull final Context context,
                                   @NonNull final Author author,
                                   @NonNull final BdtAuthor bdtAuthor) {
        final String resolvedName = bdtAuthor.getResolvedName();
        // If the author uses a pen-name, update accordingly
        if (resolvedName != null) {
            // The name was a pen-name and we have resolved it to their real name
            // Add it accordingly to the original Author object
            final Author realAuthor = Author.from(resolvedName);
            ServiceLocator.getInstance().getAuthorDao().refresh(context, realAuthor, locale);

            author.setRealAuthor(realAuthor);

            // While resolving, the name of the bdtAuthor CAN be corrected/updated.
            // Check that it still MATCHES the original author name
            final Author penAuthor = Author.from(bdtAuthor.getName());
            // Case-sensitive! We must allow correcting the case.
            if (penAuthor.isSameName(author)) {
                // It does, we now overwrite the original name; this will correct any diacritics
                author.setName(penAuthor.getFamilyName(), penAuthor.getGivenNames());
            }
            return true;
        }
        return false;
    }

    /**
     * Lookup the Author in the local cache.
     * If not found, fetch the alphabet-page from the website which updates the cache,
     * and check again.
     *
     * @param context current Context
     * @param author  to lookup
     *
     * @return cached BdtAuthor, or {@code null} if not found
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    @Nullable
    private BdtAuthor lookupInCache(@NonNull final Context context,
                                    @NonNull final Author author)
            throws SearchException, CredentialsException {

        final BedethequeCacheDao cacheDao = ServiceLocator.getInstance().getBedethequeCacheDao();

        // Check if we have the author in the cache
        final String name = author.getFormattedName(false);
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
        return bdtAuthor;
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

    /**
     * Lookup the author on the website.
     * If successful, it will have been updated in the cache database.
     *
     * @param context   current Context
     * @param bdtAuthor to lookup
     * @param author
     *
     * @return {@code true} on success
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    private boolean lookupOnSite(@NonNull final Context context,
                                 @NonNull final BdtAuthor bdtAuthor,
                                 @NonNull final Author author)
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
            String website;
            LocalDate birthDate;
            LocalDate deathDate;
            @Nullable
            String birthCountry = null;

            for (final Element label : labels) {
                switch (label.text()) {
                    case "Nom :": {
                        // <label>Nom :</label><span>Lemmens</span>
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            familyName = span.text();
                        }
                        break;
                    }
                    case "Prénom :": {
                        // <label>Prénom :</label><span>Xavier</span>
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            givenName = span.text();
                        }
                        break;
                    }
                    case "Pseudo :": {
                        // <label>Pseudo :</label>Lem
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            penName = textNode.toString();
                        }
                        break;
                    }
                    case "Naissance :": {
                        // <label>Naissance :</label>le 13/10/1980 <span class="pays-auteur">(BELGIQUE)</span>
                        final Node textNode = label.nextSibling();
                        birthDate = parseDate(textNode).orElse(null);
                        birthCountry = parseBirthCountry(label.nextElementSibling());
                        break;
                    }
                    case "Décès :": {
                        // <label>Décès :</label>le 27/03/2006
                        final Node textNode = label.nextSibling();
                        deathDate = parseDate(textNode).orElse(null);
                        break;
                    }
                    case "Pays :": {
                        // <label>Pays :</label><span class="pays-auteur">FRANCE</span>
                        // Don't overwrite
                        if (birthCountry == null) {
                            birthCountry = parseBirthCountry(label.nextElementSibling());
                        }
                        break;
                    }
                    case "Site internet :": {
                        // <label>Site internet :</label><a href="https://bealema.com" target="_blank">https://bealema.com</a>
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            website = a.attr("href");
                        }
                        break;
                    }
                    default:
                        // "Identifiant :"
                        // skipped, we already have the bdtId from the url
                        break;
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

    @Nullable
    private String parseBirthCountry(@Nullable final Element span) {
        String birthCountry = null;
        if (span != null) {
            birthCountry = span.text();
            if (birthCountry.startsWith("(") && birthCountry.endsWith(")")) {
                birthCountry = birthCountry.substring(1, birthCountry.length() - 1)
                                           .trim();
            }
        }
        return birthCountry;
    }

    @NonNull
    private Optional<LocalDate> parseDate(@Nullable final Node textNode) {
        if (textNode != null) {
            String s = textNode.toString();
            if (s.startsWith("le ")) {
                s = s.substring(3);
            }
            return dateParser.parse(s, locale).map(LocalDate::from);
        }
        return Optional.empty();
    }

    /**
     * <pre>
     *     {@code
     *     <div class="auteur-image">
     *         <a class="zoom-format-icon colorbox cboxElement"
     *            href="https://www.bedetheque.com/media/Photos/Photo_96.jpg"
     *            title="Leloup, Roger">
     *            <img src="https://www.bedetheque.com/media/Photos/Photo_96.jpg"
     *                 class="fadeover" style="opacity: 1;">
     *         </a>
     *     </div>
     *     }
     * </pre>
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseImage(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @NonNull final String bdtId)
            throws StorageException {
        final Element a = document.selectFirst("div.auteur-image a");
        if (a != null) {
            final String url = a.attr("href");
            if (!"https://www.bdgest.com/skin/nophoto.png".equals(url)) {
                return searchEngine.saveImage(context, url, bdtId, 0, null);
            }
        }
        return Optional.empty();
    }

    @Nullable
    String parseBio(@NonNull final Document document) {
        final Element bio = document.selectFirst("p.bio");
        if (bio != null) {
            return bio.wholeText();
        }
        return null;
    }
}
