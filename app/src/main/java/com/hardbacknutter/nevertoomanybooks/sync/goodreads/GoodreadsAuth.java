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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import oauth.signpost.http.HttpParameters;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.AuthUserApiHandler;

/**
 * Handles all authentication for Goodreads access.
 */
public class GoodreadsAuth {

    /** Log tag. */
    private static final String TAG = "GoodreadsAuth";
    /** Browser url where to send the user to approve access. */
    private static final String AUTHORIZATION_WEBSITE_URL =
            GoodreadsManager.BASE_URL + "/oauth/authorize?mobile=1";
    /** OAuth url to *request* access. */
    private static final String REQUEST_TOKEN_ENDPOINT_URL =
            GoodreadsManager.BASE_URL + "/oauth/request_token";
    /** OAuth url to access. */
    private static final String ACCESS_TOKEN_ENDPOINT_URL =
            GoodreadsManager.BASE_URL + "/oauth/access_token";

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "goodreads.";
    /** Used when requesting the website for authorization. Only temporarily stored. */
    private static final String REQUEST_TOKEN = PREF_PREFIX + "RequestToken.Token";
    /** Used when requesting the website for authorization. Only temporarily stored. */
    private static final String REQUEST_SECRET = PREF_PREFIX + "RequestToken.Secret";
    /** authorization token. */
    private static final String ACCESS_TOKEN = PREF_PREFIX + "AccessToken.Token";
    /** authorization token. */
    private static final String ACCESS_SECRET = PREF_PREFIX + "AccessToken.Secret";
    /** error string. */
    private static final String INVALID_CREDENTIALS =
            "Goodreads credentials need to be validated before accessing user data";
    /** error string. */
    private static final String DEV_KEY_NOT_AVAILABLE = "Goodreads dev key not available";

    /**
     * AUTHORIZATION_CALLBACK is the call back Intent URL.
     * Must match the intent filter(s) setup in the manifest with Intent.ACTION_VIEW
     * for this activity.
     * The scheme is hardcoded to avoid confusion between java and android package names.
     * <p>
     * scheme: com.hardbacknutter.nevertoomanybooks
     * host: goodreadsauth
     *
     * <pre>
     *     {@code
     *      <activity
     *          android:name=".goodreads.GoodreadsAuthorizationActivity"
     *          android:launchMode="singleInstance">
     *          <intent-filter>
     *              <action android:name="android.intent.action.VIEW" />
     *
     *              <category android:name="android.intent.category.DEFAULT" />
     *              <category android:name="android.intent.category.BROWSABLE" />
     *              <data
     *                  android:host="goodreadsauth"
     *                  android:scheme="com.hardbacknutter.nevertoomanybooks" />
     *          </intent-filter>
     *      </activity>
     *      }
     * </pre>
     */
    private static final String AUTHORIZATION_CALLBACK =
            "com.hardbacknutter.nevertoomanybooks://goodreadsauth";

    /**
     * Set to {@code true} when the credentials have been successfully validated.
     * Can be reset to force a new validation.
     */
    private static boolean sCredentialsValidated;

    /** Cached when credentials have been verified. */
    @Nullable
    private static String sAccessToken;
    /** Cached when credentials have been verified. */
    @Nullable
    private static String sAccessSecret;
    /** Local copy of user name retrieved when the credentials were verified. */
    @Nullable
    private static String sUsername;
    /** Local copy of user id retrieved when the credentials were verified. */
    private static long sUserId;
    /** OAuth helpers. */
    @NonNull
    private final OAuthConsumer mConsumer;
    /** OAuth helpers. */
    @NonNull
    private final OAuthProvider mProvider;

    /**
     * Constructor.
     */
    @AnyThread
    public GoodreadsAuth() {

        mConsumer = new DefaultOAuthConsumer(BuildConfig.GOODREADS_PUBLIC_KEY,
                                             BuildConfig.GOODREADS_PRIVATE_KEY);
        mProvider = new DefaultOAuthProvider(REQUEST_TOKEN_ENDPOINT_URL,
                                             ACCESS_TOKEN_ENDPOINT_URL,
                                             AUTHORIZATION_WEBSITE_URL);

        // load the credentials
        hasCredentials();
    }

    public static void invalidateCredentials() {
        sCredentialsValidated = false;
    }

    /**
     * Clear all credentials related data from the preferences and local cache.
     */
    static void clearAll() {

        sAccessToken = "";
        sAccessSecret = "";
        sCredentialsValidated = false;
        ServiceLocator.getGlobalPreferences().edit()
                      .putString(ACCESS_TOKEN, "")
                      .putString(ACCESS_SECRET, "")
                      .apply();
    }

    /**
     * Get the stored token values from prefs.
     * This is token availability only, we don't check if they are valid here.
     * <p>
     * No network access.
     *
     * @return {@code true} if we have credentials.
     *
     * @throws IllegalStateException if the developer key is not present
     */
    @AnyThread
    public boolean hasCredentials()
            throws IllegalStateException {

        // sanity check; see gradle.build
        if (mConsumer.getConsumerKey().isEmpty() || mConsumer.getConsumerSecret().isEmpty()) {
            // This should only happen if the developer forgot to add the Goodreads keys... (me)
            Logger.warn(TAG, "hasCredentials|" + DEV_KEY_NOT_AVAILABLE);
            if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException(DEV_KEY_NOT_AVAILABLE);
            } else {
                return false;
            }
        }

        if (sAccessToken != null && !sAccessToken.isEmpty()
            && sAccessSecret != null && !sAccessSecret.isEmpty()) {
            return true;
        }

        // Get the stored token values from prefs
        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        sAccessToken = global.getString(ACCESS_TOKEN, null);
        sAccessSecret = global.getString(ACCESS_SECRET, null);

        return sAccessToken != null && !sAccessToken.isEmpty()
               && sAccessSecret != null && !sAccessSecret.isEmpty();
    }

    /**
     * Return the public developer key, used for GET queries.
     *
     * @return developer key
     */
    @NonNull
    @AnyThread
    public String getDevKey() {
        return mConsumer.getConsumerKey();
    }

    @SuppressWarnings("unused")
    @NonNull
    public String getUserName() {
        // don't call hasCredentials(), if we don't have them at this time, blame the developer.
        if (!sCredentialsValidated) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return Objects.requireNonNull(sUsername);
    }

    public long getUserId() {
        // don't call hasCredentials(), if we don't have them at this time, blame the developer.
        if (!sCredentialsValidated) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return sUserId;
    }

    /**
     * Convenience wrapper.
     *
     * @param context Current context
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    @WorkerThread
    public void hasValidCredentialsOrThrow(@NonNull final Context context)
            throws CredentialsException {
        if (!hasValidCredentials(context)) {
            throw new CredentialsException(R.string.site_goodreads, "", null);
        }
    }

    /**
     * Check if the current credentials (either cached or in prefs) are valid.
     * If they have been previously checked and were valid, just use that result.
     * <p>
     * Network access if credentials need to be checked.
     * <p>
     * It is assumed that {@link SearchEngine#isAvailable()} has already been called.
     * Developer reminder: do NOT throw Exceptions from here; fail with returning {@code false}.
     * (unless we don't have a developer key installed)
     *
     * @param context Current context
     *
     * @return {@code true} if the credentials have been validated with the website.
     *
     * @throws IllegalStateException if the developer key is not present
     */
    @WorkerThread
    public boolean hasValidCredentials(@NonNull final Context context)
            throws IllegalStateException {
        // If credentials have already been accepted, don't re-check.
        if (sCredentialsValidated) {
            return true;
        }

        // If we don't have credentials at all, just leave
        if (!hasCredentials()) {
            return false;
        }

        mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);

        // should not be needed:
        //sCredentialsValidated = false;
        try {
            final AuthUserApiHandler authUserApi = new AuthUserApiHandler(context, this);
            if (authUserApi.getAuthUser() != 0) {
                // Cache the results to avoid network calls later
                sUsername = authUserApi.getUsername();
                sUserId = authUserApi.getUserId();

                sCredentialsValidated = true;
            }
        } catch (@NonNull final RuntimeException e) {
            // Something went wrong. Clear the access token
            sAccessToken = null;
        }

        return sCredentialsValidated;
    }

    /**
     * Request authorization for this application, for the current user,
     * by going to the OAuth web page.
     *
     * @return the authentication Uri to which the user should be send
     *
     * @throws AuthorizationException with GoodReads
     * @throws IOException            on other failures
     */
    @WorkerThread
    public Uri requestAuthorization()
            throws AuthorizationException,
                   IOException {

        String authUrl;

        // Don't do this; this is just part of OAuth and not the Goodreads API
        // THROTTLER.waitUntilRequestAllowed();

        // Get the URL to send the user to so they can authenticate.
        try {
            authUrl = mProvider.retrieveRequestToken(mConsumer, AUTHORIZATION_CALLBACK);

        } catch (@NonNull final OAuthCommunicationException e) {
            throw new IOException(e);
        } catch (@NonNull final OAuthMessageSignerException | OAuthNotAuthorizedException e) {
            throw new AuthorizationException(e);
        } catch (@NonNull final OAuthExpectationFailedException e) {
            // this would be a bug
            throw new IllegalStateException(e);
        }

        // Some urls come back without a scheme, add it to make a valid URL
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://")) {
            Logger.warn(TAG, "requestAuthorization|no scheme for authUrl=" + authUrl);
            // Assume http will auto-redirect to https if the website needs that instead.
            authUrl = "http://" + authUrl;
        }

        // Temporarily save the token; this object may be destroyed
        // before the web page returns.
        ServiceLocator.getGlobalPreferences()
                      .edit()
                      .putString(REQUEST_TOKEN, mConsumer.getToken())
                      .putString(REQUEST_SECRET, mConsumer.getTokenSecret())
                      .apply();

        return Uri.parse(authUrl);
    }

    /**
     * Called by the callback activity, @link AuthorizationResultCheckTask,
     * when a request has been authorized by the user.
     *
     * @param context Current context
     *
     * @return {@code true} on success
     *
     * @throws AuthorizationException with GoodReads
     * @throws IOException            on other failures
     */
    @WorkerThread
    boolean handleAuthenticationAfterAuthorization(@NonNull final Context context)
            throws AuthorizationException, IOException {

        // Get the temporarily saved request tokens.
        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        final String requestToken = global.getString(REQUEST_TOKEN, null);
        final String requestSecret = global.getString(REQUEST_SECRET, null);

        // The tokens are stored in #requestAuthorization
        SanityCheck.requireValue(requestToken, "requestToken");
        SanityCheck.requireValue(requestSecret, "requestSecret");

        // Update the consumer.
        mConsumer.setTokenWithSecret(requestToken, requestSecret);

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();

        // Get the access token
        try {
            mProvider.retrieveAccessToken(mConsumer, null);

        } catch (@NonNull final OAuthCommunicationException e) {
            throw new IOException(e);
        } catch (@NonNull final OAuthMessageSignerException | OAuthNotAuthorizedException e) {
            throw new AuthorizationException(e);
        } catch (@NonNull final OAuthExpectationFailedException e) {
            // this would be a bug
            throw new IllegalStateException(e);
        }

        // Cache and save the tokens
        sAccessToken = mConsumer.getToken();
        sAccessSecret = mConsumer.getTokenSecret();

        global.edit()
              .putString(ACCESS_TOKEN, sAccessToken)
              .putString(ACCESS_SECRET, sAccessSecret)
              .remove(REQUEST_TOKEN)
              .remove(REQUEST_SECRET)
              .apply();

        // and return success or failure.
        return hasValidCredentials(context);
    }

    /**
     * Sign a GET request.
     *
     * @param request Request to sign
     *
     * @throws IOException on failure
     */
    public void signGetRequest(@NonNull final HttpURLConnection request)
            throws IOException {

        mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);
        try {
            mConsumer.sign(request);
        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        }
    }

    /**
     * Sign a POST request with the post parameters.
     *
     * @param request      Request to sign
     * @param parameterMap (optional) POST parameters.
     *
     * @throws IOException on failure
     */
    public void signPostRequest(@NonNull final HttpURLConnection request,
                                @Nullable final Map<String, String> parameterMap)
            throws IOException {

        if (parameterMap != null) {
            // https://zewaren.net/oauth-java.html
            // The key to signing the POST fields is to add them as additional parameters,
            // but already percent-encoded; and also to add the realm header.
            final HttpParameters parameters = new HttpParameters();
            for (final Map.Entry<String, String> entry : parameterMap.entrySet()) {
                // note we need to encode both key and value.
                parameters.put(OAuth.percentEncode(entry.getKey()),
                               OAuth.percentEncode(entry.getValue()));
            }
            parameters.put("realm", request.getURL().toString());

            mConsumer.setAdditionalParameters(parameters);
        }

        mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);
        try {
            mConsumer.sign(request);
        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        }
    }

    /**
     * A wrapper around the OAuth exception so we can keep the use
     * of the original local to this class.
     */
    public static class AuthorizationException
            extends Exception {

        private static final long serialVersionUID = 5691917497651682323L;

        AuthorizationException(@NonNull final Throwable cause) {
            super(cause);
        }
    }
}
