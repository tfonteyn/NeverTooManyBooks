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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.org.json.JSONObject;

public class LoginHelper {

    public static final String PK_HOST_USER = "stripinfo.host.user";
    public static final String PK_HOST_PASS = "stripinfo.host.password";
    /** Log tag. */
    private static final String TAG = "LoginHelper";
    /** the id returned in the cookie. Stored for easy access. */
    private static final String PK_HOST_USER_ID = "stripinfo.host.userId";

    private static final String STRIPINFO_BE_USER_LOGIN = "https://www.stripinfo.be/user/login";

    /**
     * si_userdata={"userid":"66","password":"blah","settings":{"acceptCookies":true}};
     * expires=Tue, 08-Mar-2022 14:22:43 GMT; Max-Age=31536000; path=/; domain=stripinfo.be
     */
    private static final String COOKIE_SI_USERDATA = "si_userdata";
    private static final String COOKIE_DOMAIN = "stripinfo.be";

    private String mUserId;

    /**
     * Initialises the global {@link CookieManager} and performs a login
     * using the stored credentials.
     *
     * @return the Optional&lt;HttpCookie&gt;; when present indicates login was successful.
     *
     * @throws IOException on any failure
     */
    @NonNull
    public Optional<HttpCookie> login()
            throws IOException {

        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        final String username = global.getString(PK_HOST_USER, "");
        final String password = global.getString(PK_HOST_PASS, "");

        // Sanity check
        //noinspection ConstantConditions
        if (username.isEmpty() || password.isEmpty()) {
            return Optional.empty();
        }

        final CookieManager cookieManager = ServiceLocator.getInstance().getCookieManager();

        final HttpURLConnection request = (HttpURLConnection) new URL(
                STRIPINFO_BE_USER_LOGIN).openConnection();
        request.setRequestMethod(HttpConstants.POST);
        request.setDoOutput(true);

        final StringJoiner postBody = new StringJoiner("&");
        postBody.add("userName=" + URLEncoder.encode(username, "UTF-8"));
        postBody.add("passw=" + URLEncoder.encode(password, "UTF-8"));
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
        final int responseCode = request.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new HttpStatusException(R.string.site_stripinfo_be,
                                          responseCode, request.getResponseMessage(),
                                          request.getURL());
        }

        // The presence of the cookie gives us the real status.
        final Optional<HttpCookie> optional =
                cookieManager.getCookieStore().getCookies()
                             .stream()
                             .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                                          && COOKIE_SI_USERDATA.equals(c.getName()))
                             .findFirst();
        request.disconnect();

        optional.ifPresent(cookie -> {
            try {
                final String cookieValue = URLDecoder.decode(cookie.getValue(), "UTF-8");
                // {"userid":"66","password":"blah","settings":{"acceptCookies":true}}
                final JSONObject jsonCookie = new JSONObject(cookieValue);
                mUserId = jsonCookie.optString("userid");
                if (mUserId != null && !mUserId.isEmpty()) {
                    // store as String. We don't need to know it's a number.
                    global.edit().putString(PK_HOST_USER_ID, mUserId).apply();
                }

            } catch (@NonNull final UnsupportedEncodingException e) {
                mUserId = null;
                Logger.e(TAG, e, "cookie.getValue()=" + cookie.getValue());
            }
        });
        return optional;
    }

    /**
     * The user id for the <strong>current</strong> session.
     * Only valid if {@link #login} was successful.
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
