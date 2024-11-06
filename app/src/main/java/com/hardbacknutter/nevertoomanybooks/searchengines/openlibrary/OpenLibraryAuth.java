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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

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
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * 2024-11-06 There are 2 cookies.
 * <p>
 * "pd" with no value ("") set.
 * "session": which is an array with 3 values separated by %2C:
 * "/people/myUserName  %2C  2024-11-06T17%3A15%3A00  %2C  sessionkey"
 */
public class OpenLibraryAuth
        implements ConnectionValidator {

    /** Log tag. */
    private static final String TAG = "OpenLibraryAuth";

    /** Preferences prefix. */
    private static final String PREF_KEY = EngineId.OpenLibrary.getPreferenceKey();

    static final String PK_HOST_USER = PREF_KEY + '.' + Prefs.PK_HOST_USER;
    static final String PK_HOST_PASS = PREF_KEY + '.' + Prefs.PK_HOST_PASSWORD;

    static final String PK_LOGIN_TO_SEARCH = PREF_KEY + ".login.to.search";

    /** the id returned in the cookie. Stored for easy access. */
    private static final String PK_HOST_USER_ID = PREF_KEY + ".host.userId";

    private static final String USER_LOGIN_URL = "/account/login";

    private static final String COOKIE_DOMAIN = "openlibrary.org";
    private static final String COOKIE_USERDATA = "session";

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
    public OpenLibraryAuth(@NonNull final Context context,
                           @NonNull final CookieManager cookieManager) {
        this.cookieManager = cookieManager;

        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final SearchEngineConfig config = EngineId.OpenLibrary.requireConfig();

        hostUrl = config.getHostUrl(context);

        futureHttpPost = new FutureHttpPost<>(EngineId.OpenLibrary.getLabelResId());
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs(context))
                      .setReadTimeout(config.getReadTimeoutInMs(context))
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
    static boolean isLoginToSearch(@NonNull final Context context) {
        if (BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(PK_LOGIN_TO_SEARCH, false);
        } else {
            return false;
        }
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
     * <p>
     * In the website html sometimes referred to as "member".
     *
     * @return a valid non-empty user id if present
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
                        final String[] parts = cookieValue.split(",");
                        final String userId = parts[0];
                        if (userId != null && userId.startsWith("/people/")) {
                            return Optional.of(userId.substring(8));
                        }
                    } catch (@NonNull final RuntimeException e) {
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
            throw new CredentialsException(R.string.site_open_library, "missing password");
        }

        // Secondly check if we're already logged in ?
        String userId = getUserId().orElse(null);
        if (userId != null) {
            prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
            return userId;
        }

        final String url = hostUrl + USER_LOGIN_URL;

        final String postBody = new StringJoiner("&")
                .add("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8))
                .add("password=" + URLEncoder.encode(password, StandardCharsets.UTF_8))
                .add("redirect=")
                .add("debug_token=")
                .toString();

        futureHttpPost.setRequestProperty(HttpConstants.CONTENT_TYPE,
                                          "application/x-www-form-urlencoded");
        futureHttpPost.post(url, postBody, null);

        userId = getUserId().orElseThrow(
                () -> new CredentialsException(R.string.site_open_library, "login failed"));

        prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
        return userId;
    }

    public void cancel() {
        futureHttpPost.cancel();
    }
}
