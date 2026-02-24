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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

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
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Handles all authentication for openlibrary.org access.
 * <p>
 * Note that once we have been authenticated, a cookie is set
 * <strong>for the duration</strong> of our session.
 * <p>
 * TODO: add "Forget credentials" for the current session
 */
public class OpenLibraryAuth
        implements SiteAuthModule,
                   ConnectionValidator {

    /** Log tag. */
    private static final String TAG = "OpenLibraryAuth";

    /** Preferences prefix. */
    private static final String PREFERENCE_KEY = EngineId.OpenLibrary.getPreferenceKey();

    static final String PK_HOST_USER = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_USER;
    static final String PK_HOST_PASS = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_PASSWORD;

    /** the id returned in the cookie. Stored for easy access. */
    private static final String PK_HOST_USER_ID = PREFERENCE_KEY + ".host.userId";

    private static final String USER_LOGIN_URL = "/account/login";

    private static final String COOKIE_DOMAIN = "openlibrary.org";

    /**
     * Cookie with the userdata.
     * <p>
     * It contains 3 values separated by %2C (spaces for clarity)
     * <pre>
     * {@code "/people/myUserName  %2C  2024-11-06T17%3A15%3A00  %2C  hexSessionKey"}
     * </pre>
     */
    private static final String COOKIE_USERDATA = "session";
    @NonNull
    private final CookieManager cookieManager;
    @Nullable
    private FutureHttp<Void> httpPost;

    /**
     * Constructor.
     *
     * @param cookieManager previously initialised cookie manager
     */
    public OpenLibraryAuth(@NonNull final CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    /**
     * Get the username as configured in the settings.
     *
     * @return username
     *
     * @see #getUserId()
     */
    @AnyThread
    @NonNull
    public static Optional<String> getUsername() {
        final String username = ServiceLocator.getInstance().getSharedPreferences()
                                              .getString(PK_HOST_USER, null);
        if (username != null && !username.isEmpty()) {
            return Optional.of(username);
        }
        return Optional.empty();
    }

    /**
     * Get the user id for the <strong>current</strong> session.
     * <p>
     * In the website HTML code sometimes referred to as "member".
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
                    //noinspection CheckStyle
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
    public boolean validateConnection(@NonNull final Context context)
            throws IOException, CredentialsException, StorageException {
        login(context);
        return true;
    }

    @WorkerThread
    @Override
    @NonNull
    public String login(@NonNull final Context context)
            throws IOException, CredentialsException, StorageException {

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();

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

        final SearchEngineConfig config = EngineId.OpenLibrary.getConfig();

        //noinspection DataFlowIssue
        final String url = config.getHostUrl() + USER_LOGIN_URL;
        final String postBody = new StringJoiner("&")
                .add("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8))
                .add("password=" + URLEncoder.encode(password, StandardCharsets.UTF_8))
                .add("redirect=")
                .add("debug_token=")
                .toString();

        httpPost = HttpCallFactory.create(EngineId.OpenLibrary);
        httpPost.setRequestProperty(HttpConstants.CONTENT_TYPE,
                                    HttpConstants.CONTENT_TYPE_FORM_URL_ENCODED)
                .post(url, postBody, null);

        userId = getUserId().orElseThrow(
                () -> new CredentialsException(R.string.site_open_library, "login failed"));

        prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
        return userId;
    }

    @Override
    public void cancel() {
        synchronized (this) {
            if (httpPost != null) {
                httpPost.cancel();
            }
        }
    }
}
