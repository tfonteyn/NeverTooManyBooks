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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class RegistrationApiToken {

    private static final String TAG = "RegistrationApiToken";

    /** RequestKey. */
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_MESSAGE = TAG + ":msg";
    /** Input, the updated value must be returned in the result. */
    private static final String BKEY_API_TOKEN = TAG + ":tkn";
    private static final String BKEY_TOKEN_LEN = TAG + ":len";

    @NonNull
    private final String requestKey;
    @NonNull
    private final EngineId engineId;
    private final String message;
    private final int tokenLen;
    @Nullable
    private String apiToken;

    public RegistrationApiToken(@NonNull final String requestKey,
                                @NonNull final EngineId engineId,
                                @NonNull final String message,
                                final int tokenLen,
                                @Nullable final String apiToken) {
        this.requestKey = requestKey;
        this.engineId = engineId;
        this.message = message;
        this.tokenLen = tokenLen;
        this.apiToken = apiToken;
    }

    /**
     * Constructor.
     *
     * @param args to read
     *
     * @return instance
     */
    @NonNull
    public static RegistrationApiToken fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY));
        @SuppressWarnings("deprecation")
        final EngineId engineId = Objects.requireNonNull(args.getParcelable(
                SearchEngine.UserRegistration.BKEY_ENGINE_ID));

        final String message = Objects.requireNonNull(args.getString(BKEY_MESSAGE));
        final int tokenLen = args.getInt(BKEY_TOKEN_LEN);
        final String apiToken = args.getString(BKEY_API_TOKEN);

        return new RegistrationApiToken(requestKey, engineId, message, tokenLen, apiToken);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(5);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(SearchEngine.UserRegistration.BKEY_ENGINE_ID, engineId);
        args.putString(BKEY_MESSAGE, message);
        args.putInt(BKEY_TOKEN_LEN, tokenLen);
        args.putString(BKEY_API_TOKEN, apiToken);
        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    /**
     * Or use the static {@link SearchEngine.UserRegistration#getEngineId(Bundle)}
     *
     * @return id
     */
    @SuppressWarnings("unused")
    @NonNull
    EngineId getEngineId() {
        return engineId;
    }

    @NonNull
    String getMessage() {
        return message;
    }

    int getTokenLen() {
        return tokenLen;
    }

    @Nullable
    public String getApiToken() {
        return apiToken;
    }

    void setApiToken(@Nullable final String apiToken) {
        this.apiToken = apiToken;
    }
}
