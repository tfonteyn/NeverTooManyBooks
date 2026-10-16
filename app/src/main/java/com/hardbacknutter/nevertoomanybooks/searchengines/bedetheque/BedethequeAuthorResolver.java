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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormaliser;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
    private final Locale siteLocale;
    @NonNull
    private final BedethequeCacheDao cacheDao;
    @NonNull
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
        siteLocale = searchEngine.getLocale(context);

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
        this(context, EngineId.Bedetheque.createSearchEngine(context));
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

        if (AuthorResolverHelper.isEnabled(EngineId.Bedetheque)) {
            final AuthorResolver ar;
            if (searchEngine instanceof BedethequeSearchEngine) {
                ar = new BedethequeAuthorResolver(
                        context, (BedethequeSearchEngine) searchEngine);
            } else {
                ar = new BedethequeAuthorResolver(context, searchEngine);
            }
            return List.of(ar);
        }
        return List.of();
    }

    @Override
    @WorkerThread
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
            final Document document = searchEngine.loadHtml(context, url, null);
            if (!searchEngine.isCancelled()) {
                final Author found = parse(document);
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
                cacheDao.update(bdtAuthor, siteLocale);
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
     * @throws SearchException on generic exceptions (wrapped) during search
     */
    @WorkerThread
    @Nullable
    private BdtAuthor lookupInCache(@NonNull final Context context,
                                    @NonNull final Author author)
            throws SearchException {

        // Check if we have the author in the cache
        final String name = author.getFormattedName(false);
        BdtAuthor bdtAuthor = cacheDao.findByName(name, siteLocale).orElse(null);
        if (bdtAuthor == null) {
            // If not resolved / not found,
            final AuthorListLoader pageLoader = new AuthorListLoader(context, searchEngine);
            final char c1 = firstChar(author.getFamilyName());
            // and the list-page was never fetched before,
            if (!cacheDao.isAuthorPageCached(c1)) {
                // go fetch the list-page on which the author should/could be
                if (pageLoader.fetch(c1)) {
                    // If the author was on the list page, we should find it in the cache now.
                    bdtAuthor = cacheDao.findByName(name, siteLocale).orElse(null);
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
        final String normalised = textNormaliser.transliterate(String.valueOf(name.charAt(0)));
        if (normalised.isEmpty()) {
            return '0';
        }
        final char c1 = normalised.toUpperCase(siteLocale).charAt(0);
        return Character.isAlphabetic(c1) ? c1 : '0';
    }

    /**
     * Parse the author details.
     *
     * @param document to parse
     *
     * @return author, or {@code null} on failure
     */
    @VisibleForTesting
    @Nullable
    Author parse(@NonNull final Document document) {

        final Elements columns = document.select("div.bdt-auh-facts dl");
        if (columns.isEmpty()) {
            return null;
        }

        @Nullable
        String penName = null;

        final Author realAuthor = new Author("", null);
        for (final Element column : columns) {
            final Elements dts = column.select("dt");
            for (final Element dt : dts) {
                final String key = dt.text();
                final Element dd = dt.nextElementSibling();
                if (dd != null && "dd".equalsIgnoreCase(dd.tagName())) {
                    if ("Pseudo".equals(key)) {
                        penName = dd.text();
                    } else {
                        parse(key, dd, realAuthor);
                    }
                }
            }
        }

        // sanity check
        if (realAuthor.getFamilyName().isEmpty()) {
            return null;
        }

        final Optional<String> oSid = realAuthor.getIdentifierValue(Identifier.SID_BEDETHEQUE);

        @Nullable
        String pictureFileSpec = null;
        if (oSid.isPresent()) {
            pictureFileSpec = parseImage(document, oSid.get()).orElse(null);
            realAuthor.setTmpPictureFileSpec(pictureFileSpec);
        }

        if (penName == null) {
            return realAuthor;
        }

        // Wrap the real author inside the pseudonym
        final Author penNameAuthor = Author.from(penName);
        oSid.ifPresent(sid -> penNameAuthor
                .setIdentifierValue(Identifier.SID_BEDETHEQUE, sid));

        realAuthor.getBirthDate().ifPresent(penNameAuthor::setBirthDate);
        realAuthor.getDeathDate().ifPresent(penNameAuthor::setDeathDate);
        penNameAuthor.setTmpPictureFileSpec(pictureFileSpec);

        penNameAuthor.setRealAuthor(realAuthor);
        return penNameAuthor;
    }

    private void parse(@NonNull final String key,
                       @NonNull final Element dd,
                       @NonNull final Author author) {
        switch (key) {
            case "Identifiant": {
                try {
                    final int sid = Integer.parseInt(dd.text());
                    if (sid > 0) {
                        author.setIdentifierValue(Identifier.SID_BEDETHEQUE, sid);
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
                break;
            }
            case "Nom": {
                author.setFamilyName(dd.text());
                break;
            }
            case "Prénom": {
                author.setGivenNames(dd.text());
                break;
            }
            case "Naissance": {
                author.setBirthDate(parseDate(dd));
                break;
            }
            case "Décès": {
                author.setDeathDate(parseDate(dd));
                break;
            }
        }
    }

    /**
     * Parse a date.
     *
     * @param dd to parse
     *
     * @return (partial) date as an iso string, or {@code null} if none found.
     */
    @Nullable
    private String parseDate(@NonNull final Element dd) {
        String s = dd.ownText();
        // Remove trailing middle-dot and any extra whitespace
        s = s.replace("·", "").strip();

        // Remove the prefix if present
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

    @Nullable
    private String parseBirthCountry(@NonNull final Element dd) {
        String birthCountry = null;
        final Element span = dd.selectFirst("span");
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
     * Parse an image.
     * <pre>
     *     {@code
     *     <div class="bdt-auh-photo">
     *         <a class="colorbox bdt-img cboxElement"
     *            href="https://www.bedetheque.com/media/Photos/Photo_367.jpg" title="Jijé">
     *            <img src="https://www.bedetheque.com/media/Photos/Photo_367.jpg" alt="Jijé"
     *            onerror="this.parentNode.classList.add('is-absente');"></a>
     *      </div>
     *     }
     * </pre>
     *
     * @param document to parse
     * @param bookId   used for the temp filename
     *
     * @return FileSpec
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseImage(@NonNull final Document document,
                                        @NonNull final String bookId) {
        final Element a = document.selectFirst("div.bdt-auh-photo a");
        if (a != null) {
            final String url = a.attr("href");
            if (!"https://www.bdgest.com/skin/nophoto.png".equals(url)) {
                //noinspection OverlyBroadCatchBlock
                try {
                    return searchEngine.getHttpCallFactory()
                                       .saveImage(url, null, bookId, 0, null);
                } catch (@NonNull final StorageException e) {
                    // we ignore author-image failures; but log them
                    LoggerFactory.getLogger().e(TAG, e);
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
