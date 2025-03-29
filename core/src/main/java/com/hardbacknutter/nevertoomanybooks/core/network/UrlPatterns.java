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

package com.hardbacknutter.nevertoomanybooks.core.network;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Seemingly very robust. The original here in {@link #FULL} has been stripped
 * of some protocols and the 'localhost' string.
 *
 * @see <a href="https://github.com/validatorjs/validator.js/tree/master/src/lib">
 *         validator.js</a>
 */
@SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
public final class UrlPatterns {

    /**
     * For doc only.
     */
    private static final String FULL =
            "^"
            + "(?!mailto:)"
            + "(?:(?:http|https|ftp)://)"
            + "(?:\\S+(?::\\S*)?@)?"
            + "(?:(?:(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}(?:\\.(?:[0-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))"
            + "|"
            + "(?:(?:[a-z\\u00a1-\\uffff0-9]+-?)*[a-z\\u00a1-\\uffff0-9]+)(?:\\.(?:[a-z\\u00a1-\\uffff0-9]+-?)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff]{2,})))|localhost)"
            + "(?::\\d{2,5})?"
            + "(?:(/|\\?|#)[^\\s]*)?"
            + "$";


    /** Redirecting to the browser. */
    private static final String BROWSER_PROTOCOL = "^(?:(?:http|https)://)";
    /** Internal app usage is limited to https. */
    private static final String APP_PROTOCOL = "(?:(?:https)://)";

    private static final String USER_INFO = "(?:\\S+(?::\\S*)?@)?";

    private static final String IPV4 =
            "(?:(?:(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}(?:\\.(?:[0-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))";

    private static final String HOST_DOMAIN =
            "(?:(?:[a-z\\u00a1-\\uffff0-9]+-?)*[a-z\\u00a1-\\uffff0-9]+)(?:\\.(?:[a-z\\u00a1-\\uffff0-9]+-?)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff]{2,})))|localhost)";


    private static final String PORT = "(?::\\d{2,5})?";
    private static final String QUERY = "(?:([/?#])\\S*)?";
    private static final String EOL = "$";

    private static final Pattern SITE_URL_PATTERN = Pattern.compile(
            BROWSER_PROTOCOL
            + USER_INFO
            + IPV4 + "|" + HOST_DOMAIN
            + PORT
            + QUERY
            + EOL);

    private static final Pattern APP_URL_PATTERN = Pattern.compile(
            APP_PROTOCOL
            + USER_INFO
            + IPV4 + "|" + HOST_DOMAIN
            + PORT
            + QUERY
            + EOL);

    private UrlPatterns() {
    }


    /**
     * Check for a valid URL.
     *
     * @param url to check
     *
     * @return flag
     */
    public static boolean isBlankOrValidUrl(@Nullable final String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        return SITE_URL_PATTERN.matcher(url).matches();
    }

    /**
     * Check for a valid URI containing a single {@code %s} parameter.
     *
     * @param uri to check
     *
     * @return flag
     */
    public static boolean isBlankOrValidUriWith1s(@Nullable final String uri) {
        if (uri == null || uri.isEmpty() || "%s".equals(uri)) {
            return true;
        }

        return APP_URL_PATTERN.matcher(uri).matches()
               && containsOneStringParam(uri);
    }

    private static boolean containsOneStringParam(@NonNull final String s) {
        final int i = s.indexOf("%s");
        return i != -1 && i == s.lastIndexOf("%s");
    }
}
