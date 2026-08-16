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

package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Currently using q values specific for Firefox.
 */
public final class HttpLanguageHeader {

    private HttpLanguageHeader() {
    }

    /**
     * Create a suitable "Accept-Language" header with site and user languages.
     *
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     *
     * @return header string
     *
     * @see HttpConstants#ACCEPT_LANGUAGE
     */
    @NonNull
    public static String create(@NonNull final Locale siteLocale,
                                @NonNull final Locale userLocale) {
        final Set<String> noDups = new HashSet<>();
        final StringJoiner accept = new StringJoiner(",");

        int q = 10;
        q = addLocale(siteLocale, q, noDups, accept);
        q = addLocale(userLocale, q, noDups, accept);
        // Always add English (no country) if not there already.
        q = addLocale(Locale.ENGLISH, q, noDups, accept);

        return accept.toString();
    }

    private static int addLocale(@NonNull final Locale locale,
                                 final int qIn,
                                 @NonNull final Set<String> noDups,
                                 @NonNull final StringJoiner accept) {
        int q = qIn;
        final String languageTag = locale.toLanguageTag();
        if (noDups.add(languageTag)) {
            accept.add(q >= 10 ? languageTag : languageTag + ";q=0." + q);
            q--;
        }

        final String language = locale.getLanguage();
        if (noDups.add(language)) {
            accept.add(q >= 10 ? language : language + ";q=0." + q);
            q--;
        }

        return q;
    }
}
