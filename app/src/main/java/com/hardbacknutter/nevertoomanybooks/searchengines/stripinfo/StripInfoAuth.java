/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
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
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpPost;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Handles all authentication for stripinfo.be access.
 * <p>
 * Note that once we have been authenticated, a cookie is set
 * <strong>for the duration</strong> of our session.
 * <p>
 * TODO: add "Forget credentials" for the current session
 */
public class StripInfoAuth
        implements SiteAuthModule,
                   ConnectionValidator {

    /** Log tag. */
    private static final String TAG = "StripInfoAuth";

    /** Preferences prefix. */
    private static final String PREF_KEY = EngineId.StripInfoBe.getPreferenceKey();

    static final String PK_HOST_USER = PREF_KEY + '.' + Prefs.PK_HOST_USER;
    static final String PK_HOST_PASS = PREF_KEY + '.' + Prefs.PK_HOST_PASSWORD;

    /** the id returned in the cookie. Stored for easy access. */
    private static final String PK_HOST_USER_ID = PREF_KEY + ".host.userId";

    private static final String USER_LOGIN_URL = "/user/login";

    private static final String COOKIE_DOMAIN = "stripinfo.be";

    /**
     * Cookie with the userdata as a JSON object.
     *
     * <pre>{@code
     *      {
     *          "userid": "66",
     *          "password": "blah",
     *          "settings": {
     *              "acceptCookies":true
     *          }
     *      }
     * }
     * </pre>
     */
    private static final String COOKIE_USERDATA = "si_userdata";

    @NonNull
    private final FutureHttpPost<Void> futureHttpPost;

    @NonNull
    private final String hostUrl;

    @NonNull
    private final CookieManager cookieManager;

    @NonNull
    private final SharedPreferences prefs;

    /**
     * Constructor.
     *
     * @param context       Current context
     * @param cookieManager to use
     */
    public StripInfoAuth(@NonNull final Context context,
                         @NonNull final CookieManager cookieManager) {
        this.cookieManager = cookieManager;

        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final SearchEngineConfig config = EngineId.StripInfoBe.requireConfig();

        hostUrl = config.getHostUrl(context);

        futureHttpPost = new FutureHttpPost<>(EngineId.StripInfoBe.getLabelResId());
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs(context))
                      .setReadTimeout(config.getReadTimeoutInMs(context))
                      .setThrottler(config.getThrottler());
    }


    /**
     * Get the username as configured in the settings.
     *
     * @param context Current context
     *
     * @return username
     *
     * @see #getUserId()
     */
    @AnyThread
    @NonNull
    public static Optional<String> getUsername(@NonNull final Context context) {
        final String username = PreferenceManager.getDefaultSharedPreferences(context)
                                                 .getString(PK_HOST_USER, null);
        if (username != null && !username.isEmpty()) {
            return Optional.of(username);
        }
        return Optional.empty();
    }

    /**
     * Get the user id for the <strong>current</strong> session.
     * This is not necessarily the same as the username.
     * <p>
     * In the website html sometimes referred to as "member".
     *
     * @return a valid non-empty user id if present
     *
     * @see #getUsername(Context)
     */
    @NonNull
    public Optional<String> getUserId() {
        final Optional<HttpCookie> oCookie =
                cookieManager.getCookieStore()
                             .getCookies()
                             .stream()
                             .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                                          && COOKIE_USERDATA.equals(c.getName()))
                             .findFirst();

        if (oCookie.isPresent()) {
            final HttpCookie cookie = oCookie.get();
            if (!cookie.hasExpired()) {
                final String value = cookie.getValue();
                if (value != null && !value.isEmpty()) {
                    try {
                        final String cookieValue = URLDecoder.decode(value,
                                                                     StandardCharsets.UTF_8);
                        // {"userid":"66","password":"blah","settings":{"acceptCookies":true}}
                        final JSONObject jsonCookie = new JSONObject(cookieValue);
                        final String userId = jsonCookie.optString("userid");
                        if (!userId.isEmpty()) {
                            return Optional.of(userId);
                        }
                    } catch (@NonNull final JSONException e) {
                        if (BuildConfig.DEBUG /* always */) {
                            LoggerFactory.getLogger()
                                         .e(TAG, e, "cookie.getValue()=" + value);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    @WorkerThread
    @Override
    public boolean validateConnection()
            throws IOException, CredentialsException, StorageException {
        login();
        return true;
    }

    /**
     * Performs a login using the stored credentials.
     * <p>
     * Will check the cookie to see if we're already logged in,
     * and return with success immediately.
     *
     * @return the valid user id
     *
     * @throws CredentialsException on authentication/login failures
     * @throws IOException          on generic/other IO failures
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    @NonNull
    public String login()
            throws IOException, CredentialsException, StorageException {

        // Always FIRST check the configuration for having a username/password.
        final String username = prefs.getString(PK_HOST_USER, "");
        final String password = prefs.getString(PK_HOST_PASS, "");
        if (username.isEmpty() || password.isEmpty()) {
            throw new CredentialsException(R.string.site_stripinfo_be, "missing password");
        }

        // Secondly check if we're already logged in ?
        String userId = getUserId().orElse(null);
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

        userId = getUserId().orElseThrow(
                () -> new CredentialsException(R.string.site_stripinfo_be, "login failed"));

        prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
        return userId;
    }

    public void cancel() {
        futureHttpPost.cancel();
    }
}
