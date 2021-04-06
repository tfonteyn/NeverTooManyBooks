/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Handles all authentication for stripinfo.be access.
 */
public class StripInfoAuth {

    /** Preferences prefix. */
    public static final String PREF_KEY = "stripinfo";
    public static final String PK_HOST_USER = PREF_KEY + ".host.user";
    public static final String PK_HOST_PASS = PREF_KEY + ".host.password";

    /** Whether to show any sync menus at all. */
    private static final String PK_ENABLED = PREF_KEY + ".enabled";

    private static final String PK_LOGIN_TO_SEARCH = PREF_KEY + ".login.to.search";
    /** the id returned in the cookie. Stored for easy access. */
    private static final String PK_HOST_USER_ID = PREF_KEY + ".host.userId";

    /** Log tag. */
    private static final String TAG = "StripInfoAuth";

    private static final String USER_LOGIN_URL = "/user/login";

    /**
     * si_userdata={"userid":"66","password":"blah","settings":{"acceptCookies":true}};
     * expires=Tue, 08-Mar-2022 14:22:43 GMT; Max-Age=31536000; path=/; domain=stripinfo.be
     */
    private static final String COOKIE_SI_USERDATA = "si_userdata";
    private static final String COOKIE_DOMAIN = "stripinfo.be";

    private static final String UTF_8 = "UTF-8";

    @NonNull
    private final String mSiteUrl;

    @NonNull
    private final CookieManager mCookieManager;

    /** Parsed from the Cookie. */
    @Nullable
    private String mUserId;

    public StripInfoAuth(@NonNull final String siteUrl) {
        mSiteUrl = siteUrl;

        // Setup BEFORE doing first request!
        mCookieManager = ServiceLocator.getInstance().getCookieManager();
    }

    /**
     * Check if SYNC menus should be shown at all. This does not affect searching.
     *
     * @param global Global preferences
     *
     * @return {@code true} if menus should be shown
     */
    @AnyThread
    public static boolean isSyncEnabled(@NonNull final SharedPreferences global) {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return global.getBoolean(PK_ENABLED, true);
        } else {
            return false;
        }
    }

    /**
     * Check whether the user should be logged in to the website during a <strong>search</strong>.
     * This is independent from synchronization actions (where obviously login is always required).
     *
     * @return {@code true} if we should perform a login
     */
    @AnyThread
    public static boolean isLoginToSearch() {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return ServiceLocator.getGlobalPreferences().getBoolean(PK_LOGIN_TO_SEARCH, false);
        } else {
            return false;
        }
    }

    /**
     * Initialises the global {@link CookieManager} and performs a login
     * using the stored credentials.
     *
     * @return the valid user id
     *
     * @throws CredentialsException on login failure
     * @throws IOException          on any other failure
     */
    public String login()
            throws IOException {
        mUserId = null;

        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        final String username = global.getString(PK_HOST_USER, "");
        final String password = global.getString(PK_HOST_PASS, "");

        // Sanity check
        //noinspection ConstantConditions
        if (username.isEmpty() || password.isEmpty()) {
            throw new CredentialsException(R.string.site_stripinfo_be, "", null);
        }

        StripInfoSearchEngine.THROTTLER.waitUntilRequestAllowed();

        final HttpURLConnection request = (HttpURLConnection)
                new URL(mSiteUrl + USER_LOGIN_URL).openConnection();
        request.setRequestMethod(HttpUtils.POST);
        request.setDoOutput(true);

        final StringJoiner postBody = new StringJoiner("&");
        postBody.add("userName=" + URLEncoder.encode(username, UTF_8));
        postBody.add("passw=" + URLEncoder.encode(password, UTF_8));
        postBody.add("submit=Inloggen");
        postBody.add("frmName=login");

        // explicit connect for clarity
        request.connect();

        try (OutputStream os = request.getOutputStream();
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw)) {
            writer.write(postBody.toString());
            writer.flush();
        }

        // the server always sends a 200 OK, but do a sanity check
        HttpUtils.checkResponseCode(request, R.string.site_stripinfo_be);

        // The presence of the cookie gives us the real status.
        final Optional<HttpCookie> optional = mCookieManager
                .getCookieStore().getCookies()
                .stream()
                .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                             && COOKIE_SI_USERDATA.equals(c.getName()))
                .findFirst();

        request.disconnect();

        if (optional.isPresent()) {
            final HttpCookie cookie = optional.get();
            try {
                final String cookieValue = URLDecoder.decode(cookie.getValue(), UTF_8);
                // {"userid":"66","password":"blah","settings":{"acceptCookies":true}}
                final JSONObject jsonCookie = new JSONObject(cookieValue);
                final String userId = jsonCookie.optString("userid");
                if (userId != null && !userId.isEmpty()) {
                    mUserId = userId;
                    // store as String. We don't need to know it's a number.
                    global.edit().putString(PK_HOST_USER_ID, mUserId).apply();
                    return mUserId;
                }
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.e(TAG, "cookie.getValue()=" + cookie.getValue(), e);
            }
        }
        mUserId = null;
        throw new CredentialsException(R.string.site_stripinfo_be, "", null);
    }

    /**
     * The user id for the <strong>current</strong> session.
     * <p>
     * In the website html sometimes referred to as "member".
     *
     * @return user id or {@code null}
     */
    @Nullable
    public String getUserId() {
        return mUserId;
    }
}
