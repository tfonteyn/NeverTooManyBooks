/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.annotation.VisibleForTesting;

import java.util.Iterator;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

class AuthorListLoader {

    private static final String TAG = "AuthorListLoader";

    @NonNull
    private final Context context;
    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale locale;

    AuthorListLoader(@NonNull final Context context,
                     @NonNull final BedethequeSearchEngine searchEngine) {
        this.context = context;
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);
    }

    /**
     * Fetch and parse the list of authors from the website for the given first
     * character of the name.
     * <p>
     * The site has 27 pages with name lists. The 26 [A-Z] + a '0' page with
     * all the names which don't start with an [A-Z].
     *
     * @param c1 first character of the name
     *
     * @return {@code true} on success
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    boolean fetch(final char c1)
            throws SearchException, CredentialsException {

        final String url = searchEngine.getHostUrl(context) + "/liste_auteurs_BD_" + c1 + ".html";
        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            return parseAuthorList(document);
        }
        return false;
    }

    /**
     * Parse and store the list of author name/url.
     *
     * @param document to parse
     *
     * @return {@code true} on success
     */
    @VisibleForTesting
    boolean parseAuthorList(@NonNull final Document document) {

        final Iterator<Element> iterator = document.select("ul.nav-liste > li > a")
                                                   .iterator();

        try {
            return ServiceLocator.getInstance().getBedethequeCacheDao().insert(locale, () -> {
                if (iterator.hasNext()) {
                    final Element a = iterator.next();
                    final String url = a.attr("href");
                    final Element span = a.selectFirst("span.libelle");
                    if (span != null) {
                        final String name = span.text();
                        //Log.d(TAG, name );
                        return new BdtAuthor(name, url);
                    }
                }
                return null;
            });
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e);
            return false;
        }
    }
}
