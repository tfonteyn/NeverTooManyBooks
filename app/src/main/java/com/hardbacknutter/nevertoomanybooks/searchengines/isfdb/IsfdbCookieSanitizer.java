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

package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.network.BiscuitStore;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;

/**
 * 2025-06-25: Proof of concept.
 * <p>
 * This would need to be added AFTER calling {@link FutureHttp}#checkResponseCode
 * but before doing ANYTHING ELSE.
 * <p>
 * For now, the modification in {@link BiscuitStore}
 * works well enough.
 */
public class IsfdbCookieSanitizer {

    private static final long ONE_DAY_IN_SECONDS = 86400;
    // Fix quoted expires="..."
    private static final Pattern QUOTED_PATTERN =
            Pattern.compile("(?i)(expires=)\"([^\"]+)\"");
    // Fix missing GMT at end of expires
    private static final Pattern TIMEZONE_PATTERN =
            Pattern.compile("(?i)(expires=[^;]+)(?! GMT)(?=;|$)");

    private final CookieStore cookieStore;
    @NonNull
    private final URI defaultUri;

    IsfdbCookieSanitizer(@NonNull final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        try {
            defaultUri = new URI("https://isfdb.org");
        } catch (@NonNull final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static String sanitizeSetCookieHeader(@Nullable final CharSequence raw) {
        if (raw == null) {
            return "";
        }

        final String s = QUOTED_PATTERN.matcher(raw).replaceAll("$1$2");
        return TIMEZONE_PATTERN.matcher(s).replaceAll("$1 GMT");

    }

    public void sanitize(@NonNull final Map<String, List<String>> headerFields) {
        final List<String> setCookieHeaders = headerFields.get("Set-Cookie");
        if (setCookieHeaders == null) {
            return;
        }

        for (final String rawHeader : setCookieHeaders) {
            final String sanitized = sanitizeSetCookieHeader(rawHeader);
            try {
                for (final HttpCookie cookie : HttpCookie.parse(sanitized)) {
                    final String domain = cookie.getDomain();

                    if (domain != null && domain.endsWith("isfdb.org")) {
                        cookie.setMaxAge(ONE_DAY_IN_SECONDS);
                    }

                    final URI cookieUri = domain == null ? defaultUri
                                                         : URI.create("https://" + domain);
                    cookieStore.add(cookieUri, cookie);
                }
            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }
}

