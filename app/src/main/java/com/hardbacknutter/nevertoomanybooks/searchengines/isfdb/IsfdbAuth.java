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

package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.BiscuitStore;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Checks/uses the {@link BiscuitStore} if possible, see {@link #getUserId()}.
 * <p>
 * 2025-02-23: 'Advanced search' requires login. We need it for text searches; not for ISBN.
 */
public class IsfdbAuth
        implements SiteAuthModule,
                   ConnectionValidator {
    private static final String TAG = "IsfdbAuth";

    /** Preferences prefix. */
    private static final String PREFERENCE_KEY = EngineId.Isfdb.getPreferenceKey();

    static final String PK_HOST_USER = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_USER;
    static final String PK_HOST_PASS = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_PASSWORD;

    /** the id returned in the cookie. Stored for easy access. */
    private static final String PK_HOST_USER_ID = PREFERENCE_KEY + ".host.userId";

    private static final String USER_LOGIN_URL = "/cgi-bin/submitlogin.cgi";

    private static final String COOKIE_DOMAIN = "isfdb.org";

    /**
     * Cookie with the userdata. The value is the numeric user-id.
     */
    private static final String COOKIE_USERDATA = "isfdbUserID";

    @NonNull
    private final CookieManager cookieManager;
    @Nullable
    private FutureHttp<Void> httpPost;

    /**
     * Constructor.
     *
     * @param cookieManager previously initialised cookie manager
     */
    public IsfdbAuth(@NonNull final CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }


    // ISFDB CODE:   common\login.py
    //
    //Set-Cookie: isfdbUserID=1246525; path=/; domain=www.isfdb.org; expires="Fri, 08-Sep-2037 15:00:00"
    //Set-Cookie: isfdbUserName=HardbackNut; path=/; domain=www.isfdb.org; expires="Fri, 08-Sep-2037 15:00:00"
    //Set-Cookie: isfdbToken=2a9f019e4a0bf3c6b3d8760c3f4b72ef; path=/; domain=www.isfdb.org; expires="Fri, 08-Sep-2037 15:00:00"
    //Set-Cookie: isfdbUserID=1246525; path=/; domain=isfdb.org; expires="Fri, 08-Sep-2037 15:00:00"
    //Set-Cookie: isfdbUserName=HardbackNut; path=/; domain=isfdb.org; expires="Fri, 08-Sep-2037 15:00:00"
    //Set-Cookie: isfdbToken=2a9f019e4a0bf3c6b3d8760c3f4b72ef; path=/; domain=isfdb.org; expires="Fri, 08-Sep-2037 15:00:00"
    //
    // C:\d\sdk\sources\android-34\java\net\HttpCookie.java
    // line +- 1000:
    //         assignors.put("expires", new CookieAttributeAssignor(){ // Netscape only
    //                public void assign(HttpCookie cookie,
    //                                   String attrName,
    //                                   String attrValue) {
    //                    if (cookie.getMaxAge() == MAX_AGE_UNSPECIFIED) {
    //                        // BEGIN Android-changed: Use HttpDate for date parsing.
    //                        // it accepts broader set of date formats.
    //                        // cookie.setMaxAge(cookie.expiryDate2DeltaSeconds(attrValue));
    //                        // Android-changed: Altered max age calculation to avoid setting.
    //                        // it to MAX_AGE_UNSPECIFIED (-1) if "expires" is one second in past.
    //                        Date date = HttpDate.parse(attrValue);
    //
    // expires="Fri, 08-Sep-2037 15:00:00"
    // ===> Date is NULL DUE TO MISSING TIMEZONE
    // defaults is: "EEE, dd MMM yyyy HH:mm:ss zzz"
    // closest compat is:   "EEE, dd-MMM-yyyy HH:mm:ss z"   but misses 'z'
    //
    // https://android.googlesource.com/platform/libcore/+/refs/heads/main/luni/src/main/java/libcore/net/http/HttpDate.java
    //
    // in short: due to the missing timezone, we get a 'null' date...
    // which leads to max-age being set to zero... which means the cookie
    // is immediately seen as expired and it gets deleted before we have a chance
    // to access it.
    //
    // Note that we cannot modify the BiscuitStore to update/correct the max-age
    // as we cannot access the date from the expires header...
    @NonNull
    @Override
    public Optional<String> getUserId() {
        final List<HttpCookie> cookies;
        final CookieStore cookieStore = cookieManager.getCookieStore();
        if (cookieStore instanceof BiscuitStore) {
            cookies = ((BiscuitStore) cookieStore).getRawCookieList();
        } else {
            cookies = cookieStore.getCookies();
        }

        final Optional<HttpCookie> oCookie = cookies
                .stream()
                .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                             && COOKIE_USERDATA.equals(c.getName()))
                .findFirst();

        if (oCookie.isPresent()) {
            final HttpCookie cookie = oCookie.get();
            // See class docs above
            // if (!cookie.hasExpired()) {
            final String value = cookie.getValue();
            if (value != null && !value.isEmpty()) {
                //noinspection CheckStyle
                try {
                    final String userId = URLDecoder.decode(value, StandardCharsets.UTF_8);
                    if (userId != null) {
                        return Optional.of(userId);
                    }
                } catch (@NonNull final RuntimeException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger()
                                     .e(TAG, e, "cookie.getValue()=" + value);
                    }
                }
            }
            // }
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

    @NonNull
    @Override
    public String login(@NonNull final Context context)
            throws IOException, CredentialsException, StorageException {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Always FIRST check the configuration for having a username/password.
        final String username = prefs.getString(PK_HOST_USER, "");
        final String password = prefs.getString(PK_HOST_PASS, "");
        if (username.isEmpty() || password.isEmpty()) {
            throw new CredentialsException(R.string.site_isfdb, "missing password");
        }

        // Secondly check if we're already logged in ?
        String userId = getUserId().orElse(null);
        if (userId != null) {
            prefs.edit().putString(PK_HOST_USER_ID, userId).apply();
            return userId;
        }

        final SearchEngineConfig config = EngineId.Isfdb.getConfig();

        //noinspection DataFlowIssue
        final String url = config.getHostUrl(context) + USER_LOGIN_URL;
        final String postBody = new StringJoiner("&")
                .add("login=" + URLEncoder.encode(username, StandardCharsets.UTF_8))
                .add("password=" + URLEncoder.encode(password, StandardCharsets.UTF_8))
                .add("executable=0")
                .add("argument=0")
                .toString();

        httpPost = FutureHttpFactory.create(EngineId.Isfdb);
        httpPost.setRequestProperty(HttpConstants.CONTENT_TYPE,
                                    HttpConstants.CONTENT_TYPE_FORM_URL_ENCODED)
                .post(url, postBody, null);

        userId = getUserId().orElseThrow(
                () -> new CredentialsException(R.string.site_isfdb, "login failed"));

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
