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
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;

public final class HttpLanguageHeader {

    /** Helper to randomise some urls to avoid fingerprinting by the servers. */
    @SuppressWarnings("TypeMayBeWeakened")
    @NonNull
    private static final Random RANDOM = new Random();

    private HttpLanguageHeader() {
    }

    /**
     * Create a suitable "Accept-Language" header with site and user languages.
     * The site locale is sent first.
     * The priorities (q) will be a little randomised to help prevent fingerprinting.
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
        final int offset = RANDOM.nextInt(2);

        final StringJoiner accept = new StringJoiner(",");

        // use 0.8 or 0.7
        accept.add(addLangTag(siteLocale.toLanguageTag(), siteLocale.getLanguage(),
                              8 + offset, noDups));
        // use 0.5 or 0.4
        // Always add English if not there already.
        accept.add(addLangTag(userLocale.toLanguageTag(), userLocale.getLanguage(),
                              4 + offset, noDups));
        // use 0.3 or 0.2
        accept.add(addLangTag("en", "en-GB", 2 + offset, noDups));

        return accept.toString();
    }

    @NonNull
    private static CharSequence addLangTag(@NonNull final String languageTag,
                                           @NonNull final String language,
                                           final int q,
                                           @NonNull final Set<String> noDups) {

        final StringJoiner accept = new StringJoiner(",");
        boolean addQ = false;
        if (!noDups.contains(languageTag)) {
            accept.add(languageTag);
            noDups.add(languageTag);
            addQ = true;
        }
        if (!noDups.contains(language)) {
            accept.add(language);
            noDups.add(language);
            addQ = true;
        }

        // only add q if we actually added a value.
        if (addQ) {
            return accept + ";q=0." + q;
        } else {
            return accept.toString();
        }
    }
}
