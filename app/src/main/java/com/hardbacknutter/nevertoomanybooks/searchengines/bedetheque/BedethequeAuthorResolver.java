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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormaliser;
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
 * <p>
 * Available:
 * - birthdate (and country)
 * - death date
 * - picture
 * - website(s)
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
    private final BedethequeCacheDao cacheDao;
    private final TextNormaliser textNormaliser;

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

        cacheDao = ServiceLocator.getInstance().getBedethequeCacheDao();
        textNormaliser = new TextNormaliser();
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

        final BdtAuthor bdtAuthor = lookupInCache(context, author);
        if (bdtAuthor == null) {
            // The website does not know that name at all, give up
            return false;
        }

        boolean modified = false;

        // Add the identifier if needed.
        if (author.getIdentifierValue(Identifier.SID_BEDETHEQUE).isEmpty()) {
            final String bdtId = bdtAuthor.getBdtId();
            if (bdtId != null) {
                author.setIdentifierValue(Identifier.SID_BEDETHEQUE, bdtId);
                modified = true;
            }
        }

        // load the details-page from the site, and parse it.
        final String url = bdtAuthor.getUrl();
        if (url != null && !url.isEmpty()) {
            final Document document = searchEngine.loadDocument(context, url, null);
            if (!searchEngine.isCancelled()) {
                final Author found = parse(context, document);
                // We handle the check for name equality and the subsequent
                // overwriting of the names different here as compared
                // to other sites/resolvers.
                // We do this due to:
                // - majority of french names with diacritics
                // - resolver is used by 3 other sites which often
                //   use the wrong/lack diacritics
                // Note we do NOT overwrite the 'realAuthor' name.
                // as typically we just found them here.
                if (found != null && author.isSameName(found)) {
                    // Overwrite the original name to correct any diacritics
                    author.setName(found.getFamilyName(), found.getGivenNames());
                    // and merge as normal
                    author.merge(found, true);

                    updateCache(author, bdtAuthor);

                    // be paranoid, ALWAYS report we modified the author
                    return true;
                }
            }
        }
        return modified;
    }

    private void updateCache(@NonNull final Author author,
                             @NonNull final BdtAuthor bdtAuthor) {

        final Author realAuthor = author.getRealAuthor();
        if (realAuthor != null) {
            final String realName = realAuthor.getFormattedName(false);
            if (!realName.equals(bdtAuthor.getRealName())) {
                bdtAuthor.setRealName(realName);
                try {
                    cacheDao.update(bdtAuthor, locale);
                } catch (@NonNull final DaoWriteException e) {
                    // log, but ignore - should never happen unless disk full
                    LoggerFactory.getLogger().e(TAG, e);
                }
            }
        }
    }

    /**
     * Lookup the Author in the local cache. Searches on both list-name AND real-name.
     * <p>
     * If not found, fetch the alphabet-page from the website which updates the cache,
     * and searches the cache a second time.
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

        // Check if we have the author in the cache
        final String name = author.getFormattedName(false);
        BdtAuthor bdtAuthor = cacheDao.findByName(name, locale).orElse(null);
        if (bdtAuthor == null) {
            // If not resolved / not found,
            final AuthorListLoader pageLoader = new AuthorListLoader(context, searchEngine);
            final char c1 = firstChar(author.getFamilyName());
            // and the list-page was never fetched before,
            if (!cacheDao.isAuthorPageCached(c1)) {
                // go fetch the list-page on which the author should/could be
                if (pageLoader.fetch(c1)) {
                    // If the author was on the list page, we should find it in the cache now.
                    bdtAuthor = cacheDao.findByName(name, locale).orElse(null);
                }
            }
        }
        return bdtAuthor;
    }

    /**
     * Take the first character from the given name and normalise it to [0A-Z]
     * for use with the other class methods.
     *
     * @param name to use
     *
     * @return [0A-Z] of the first character
     */
    private char firstChar(@NonNull final CharSequence name) {
        // transliterate is 'enough'
        final String normalized = textNormaliser.transliterate(String.valueOf(name.charAt(0)));
        if (normalized.isEmpty()) {
            return '0';
        }
        final char c1 = normalized.toUpperCase(locale).charAt(0);
        return Character.isAlphabetic(c1) ? c1 : '0';
    }

    /**
     * Parse the author details.
     *
     * @param context  Current context
     * @param document to parse
     *
     * @return author, or {@code null} on failure
     */
    @VisibleForTesting
    @Nullable
    Author parse(@NonNull final Context context,
                 @NonNull final Document document) {
        final Element info = document.selectFirst("div.auteur-infos ul.auteur-info");
        if (info != null) {
            final Elements labels = info.getElementsByTag("label");
            int sid = 0;
            @Nullable
            String familyName = null;
            @Nullable
            String givenName = null;
            @Nullable
            String penName = null;
            @Nullable
            String website = null;
            @Nullable
            String birthDate = null;
            @Nullable
            String deathDate = null;
            @Nullable
            String birthCountry = null;

            for (final Element label : labels) {
                switch (label.text()) {
                    case "Identifiant :": {
                        // <label>Identifiant :</label>111
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            try {
                                sid = Integer.parseInt(textNode.toString().strip());
                            } catch (@NonNull final NumberFormatException ignore) {
                                // ignore
                            }
                        }
                        break;
                    }
                    case "Nom :": {
                        // <label>Nom :</label><span>De Bevere</span>
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            familyName = span.text();
                        }
                        break;
                    }
                    case "Prénom :": {
                        // <label>Prénom :</label><span>Maurice</span>
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            givenName = span.text();
                        }
                        break;
                    }
                    case "Pseudo :": {
                        // <label>Pseudo :</label>Morris
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            penName = textNode.toString().strip();
                        }
                        break;
                    }
                    case "Naissance :": {
                        // <label>Naissance :</label>le 13/10/1980 <span class="pays-auteur">(BELGIQUE)</span>
                        birthDate = parseDate(label.nextSibling());
//                        birthCountry = parseBirthCountry(label.nextElementSibling());
                        break;
                    }
                    case "Décès :": {
                        // <label>Décès :</label>le 27/03/2006
                        deathDate = parseDate(label.nextSibling());
                        break;
                    }
//                    case "Pays :": {
//                        // <label>Pays :</label><span class="pays-auteur">FRANCE</span>
//                        // Don't overwrite
//                        if (birthCountry == null) {
//                            birthCountry = parseBirthCountry(label.nextElementSibling());
//                        }
//                        break;
//                    }
//                    case "Site internet :": {
//                        // <label>Site internet :</label><a href="https://bealema.com" target="_blank">https://bealema.com</a>
//                        final Element a = label.nextElementSibling();
//                        if (a != null) {
//                            website = a.attr("href");
//                        }
//                        break;
//                    }
                }
            }

            // sanity check
            if (familyName != null && !familyName.isEmpty()) {
                final Author realAuthor = new Author(familyName, givenName);
                if (sid > 0) {
                    realAuthor.setIdentifierValue(Identifier.SID_BEDETHEQUE, sid);
                }
                realAuthor.setBirthDate(birthDate);
                realAuthor.setDeathDate(deathDate);

                @Nullable
                final String pictureFileSpec = parseImage(context, document, sid).orElse(null);
                realAuthor.setTmpPictureFileSpec(pictureFileSpec);

                if (penName == null) {
                    return realAuthor;
                } else {
                    final Author penNameAuthor = Author.from(penName);
                    if (sid > 0) {
                        penNameAuthor.setIdentifierValue(Identifier.SID_BEDETHEQUE, sid);
                    }
                    penNameAuthor.setBirthDate(birthDate);
                    penNameAuthor.setDeathDate(deathDate);
                    penNameAuthor.setTmpPictureFileSpec(pictureFileSpec);

                    penNameAuthor.setRealAuthor(realAuthor);
                    return penNameAuthor;
                }
            }
        }
        return null;
    }

    @Nullable
    private String parseBirthCountry(@Nullable final Element span) {
        String birthCountry = null;
        if (span != null) {
            birthCountry = span.text();
            if (birthCountry.startsWith("(") && birthCountry.endsWith(")")) {
                birthCountry = birthCountry.substring(1, birthCountry.length() - 1)
                                           .strip();
            }
        }
        return birthCountry;
    }

    /**
     * Parse a date.
     *
     * @param textNode to parse
     *
     * @return (partial) date as an iso string, or {@code null} if none found.
     */
    @Nullable
    private String parseDate(@Nullable final Node textNode) {
        if (textNode != null) {
            String s = textNode.toString().strip();
            // Not in the Pattern, paranoia...
            if (s.startsWith("le ")) {
                s = s.substring(3);
            }
            // quick and dirty check for dd/mm/yyyy
            if (s.length() != 10 || s.charAt(2) != '/' || s.charAt(5) != '/') {
                return null;
            }

            final String year = s.substring(6);
            final String month = s.substring(3, 5);
            final String day = s.substring(0, 2);
            if ("01".equals(day) && "01".equals(month)) {
                return year;
            }
            return year + "-" + month + "-" + day;
        }
        return null;
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
     *
     * @param context  Current context
     * @param document to parse
     * @param bdtId    used for the temp filename
     *
     * @return FileSpec
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseImage(@NonNull final Context context,
                                        @NonNull final Document document,
                                        final int bdtId) {
        final Element a = document.selectFirst("div.auteur-image a");
        if (a != null) {
            final String url = a.attr("href");
            if (!"https://www.bdgest.com/skin/nophoto.png".equals(url)) {
                try {
                    return searchEngine.saveImage(context, url, null,
                                                  String.valueOf(bdtId), 0, null);
                } catch (@NonNull final StorageException ignore) {
                    // ignore
                }
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
