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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpPost;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Handles all authentication for stripinfo.be access.
 */
public class StripInfoAuth
        implements ConnectionValidator {

    /** Preferences prefix. */
    private static final String PREF_KEY = EngineId.StripInfoBe.getPreferenceKey();

    public static final String PK_HOST_USER = PREF_KEY + ".host.user";
    public static final String PK_HOST_PASS = PREF_KEY + ".host.password";

    static final String PK_LAST_SYNC = PREF_KEY + ".last.sync.date";

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

    @NonNull
    private final FutureHttpPost<Void> futureHttpPost;

    @NonNull
    private final String hostUrl;

    @NonNull
    private final CookieManager cookieManager;

    public StripInfoAuth() {
        // Setup BEFORE doing first request!
        cookieManager = ServiceLocator.getInstance().getCookieManager();

        final SearchEngineConfig config = EngineId.StripInfoBe.requireConfig();

        hostUrl = config.getHostUrl();

        futureHttpPost = new FutureHttpPost<>(EngineId.StripInfoBe.getLabelResId());
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs())
                      .setReadTimeout(config.getReadTimeoutInMs())
                      .setThrottler(config.getThrottler());
    }

    /**
     * Check whether the user should be logged in to the website during a <strong>search</strong>.
     * This is independent from synchronization actions (where obviously login is always required).
     *
     * @param context Current context
     *
     * @return {@code true} if we should perform a login
     */
    @AnyThread
    public static boolean isLoginToSearch(@NonNull final Context context) {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(PK_LOGIN_TO_SEARCH, false);
        } else {
            return false;
        }
    }

    /**
     * Check if the username is configured. We take this as the configuration being valid.
     *
     * @param context Current context
     *
     * @return {@code true} if at least the username has been setup in preferences
     */
    @AnyThread
    static boolean isUsernameSet(@NonNull final Context context) {
        return !PreferenceManager.getDefaultSharedPreferences(context)
                                 .getString(PK_HOST_USER, "")
                                 .isEmpty();
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
        return getUserId(cookieManager).orElse(null);
    }

    @NonNull
    private Optional<String> getUserId(@NonNull final CookieManager cookieManager) {
        final Optional<HttpCookie> oCookie =
                cookieManager.getCookieStore()
                             .getCookies()
                             .stream()
                             .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                                          && COOKIE_SI_USERDATA.equals(c.getName()))
                             .findFirst();

        if (oCookie.isPresent()) {
            final HttpCookie cookie = oCookie.get();
            if (!cookie.hasExpired()) {
                try {
                    final String cookieValue = URLDecoder.decode(cookie.getValue(),
                                                                 StandardCharsets.UTF_8);
                    // {"userid":"66","password":"blah","settings":{"acceptCookies":true}}
                    final JSONObject jsonCookie = new JSONObject(cookieValue);
                    final String userId = jsonCookie.optString("userid");
                    if (userId != null && !userId.isEmpty()) {
                        return Optional.of(userId);
                    }
                } catch (@NonNull final JSONException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger()
                                      .e(TAG, e, "cookie.getValue()=" + cookie.getValue());
                    }
                }
            }
        }
        return Optional.empty();
    }

    @WorkerThread
    @Override
    public boolean validateConnection(@NonNull final Context context)
            throws IOException, CredentialsException, StorageException {
        login(context);
        return true;
    }

    /**
     * Performs a login using the stored credentials.
     * <p>
     * Will check the cookie to see if we're already logged in,
     * and return with success immediately.
     *
     * @param context Current context
     *
     * @return the valid user id
     *
     * @throws CredentialsException on authentication/login failures
     * @throws IOException          on generic/other IO failures
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    @NonNull
    public String login(@NonNull final Context context)
            throws IOException, CredentialsException, StorageException {

        // Always FIRST check the configuration for having a username/password.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String username = prefs.getString(PK_HOST_USER, "");
        final String password = prefs.getString(PK_HOST_PASS, "");
        if (username.isEmpty() || password.isEmpty()) {
            throw new CredentialsException(R.string.site_stripinfo_be, "missing password");
        }

        // Secondly check if we're already logged in ?
        String userId = getUserId(cookieManager).orElse(null);
        if (userId != null) {
            prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
            return userId;
        }

        final String url = hostUrl + USER_LOGIN_URL;
        final String postBody = new StringJoiner("&")
                .add("userName=" + URLEncoder.encode(username, StandardCharsets.UTF_8))
                .add("passw=" + URLEncoder.encode(password, StandardCharsets.UTF_8))
                .add("submit=Inloggen")
                .add("frmName=login")
                .toString();

        futureHttpPost.post(url, postBody, null);

        userId = getUserId(cookieManager).orElse(null);
        if (userId != null) {
            prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
            return userId;
        }

        throw new CredentialsException(R.string.site_stripinfo_be, "login failed");
    }


    public void cancel() {
        futureHttpPost.cancel();
    }
}
