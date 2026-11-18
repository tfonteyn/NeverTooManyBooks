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

/**
 * RELEASE: user agent strings: Last updated: 2026-11-17.
 * <p>
 * FIXME: depending on the user agent, some sites
 *  will return different html and parsing can fail
 *  2028-11-18: all parsers have ONLY been tested with the {@link #DesktopWindowsFirefox}.
 *  This is added as the default in EngineId.
 *  We need to check on a site-by-site basis whether it can/should be switched
 *  over to a mobile agent string in the SearchEngine init method.
 *
 * @see <a href="https://www.whatismybrowser.com/guides/the-latest-user-agent/">
 *         whatismybrowser</a>
 * @see HttpConstants#USER_AGENT
 */
public enum UserAgent {
    /**
     * Windows desktop Firefox.
     */
    DesktopWindowsFirefox("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:160.0) Gecko/20100101 Firefox/160.0"),

    /**
     * Taken from Chrome on a 2022 phone, with Android 16.
     * Chrome seems to obfuscate the android version and device model
     * as neither matches the actual phone.
     * Likewise the Chrome version ends in 0.0.0 which is unlikely to be true.
     */
    AndroidChrome("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Mobile Safari/537.36"),

    /**
     * Firefox on an Android device does not specify it's "Mobile".
     * <p>
     * We're deliberately using a version of Android which is a little 'behind'.
     */
    AndroidFirefox("Mozilla/5.0 (Android 14; Mobile; rv:159.0) Gecko/157.0 Firefox/159.0"),

    /**
     * Plain simple 'wget' emulation.
     */
    Wget("Wget/1.25.0");

    public static final UserAgent DEFAULT = UserAgent.DesktopWindowsFirefox;

    @NonNull
    private final String ua;

    UserAgent(@NonNull final String ua) {
        this.ua = ua;
    }

    /**
     * Get the plain user-agent string.
     *
     * @return string
     */
    @NonNull
    public String asString() {
        return ua;
    }
}
