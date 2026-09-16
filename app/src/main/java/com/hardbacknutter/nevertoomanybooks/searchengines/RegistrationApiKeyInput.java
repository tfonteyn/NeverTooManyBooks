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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Objects;

public class RegistrationApiKeyInput {

    private static final String TAG = "RegistrationApiKeyInput";

    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_INFO = TAG + ":info";
    private static final String BKEY_ENGINE_ID = TAG + ":engineId";
    private static final String BKEY_API_KEY_WITH_FIXED_LEN = TAG + ":len";

    @NonNull
    private final String requestKey;
    @NonNull
    private final EngineId engineId;
    private final String info;
    @IntRange(from = 0)
    private final int fixedKeyLength;

    /**
     * Constructor.
     *
     * @param requestKey     for Fragment results
     * @param engineId       for routing
     * @param info        messsage/instructions on how to register
     * @param fixedKeyLength a positive number if the key must be a fixed length,
     *                       otherwise {@code 0}
     */
    public RegistrationApiKeyInput(@NonNull final String requestKey,
                                   @NonNull final EngineId engineId,
                                   @NonNull final String info,
                                   @IntRange(from = 0) final int fixedKeyLength) {
        this.requestKey = requestKey;
        this.engineId = engineId;
        this.info = info;
        this.fixedKeyLength = fixedKeyLength;
    }

    /**
     * Constructor.
     *
     * @param args to read
     *
     * @return instance
     */
    @NonNull
    public static RegistrationApiKeyInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY));
        @SuppressWarnings("deprecation")
        final EngineId engineId = Objects.requireNonNull(args.getParcelable(BKEY_ENGINE_ID));
        final String message = Objects.requireNonNull(args.getString(BKEY_INFO));
        final int fixedKeyLength = args.getInt(BKEY_API_KEY_WITH_FIXED_LEN);

        return new RegistrationApiKeyInput(requestKey, engineId, message, fixedKeyLength);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(4);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(BKEY_ENGINE_ID, engineId);
        args.putString(BKEY_INFO, info);
        args.putInt(BKEY_API_KEY_WITH_FIXED_LEN, fixedKeyLength);
        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    /**
     * Get the engine id we're registering.
     *
     * @return id
     */
    @NonNull
    EngineId getEngineId() {
        return engineId;
    }

    @NonNull
    String getInfo() {
        return info;
    }

    /**
     * Returns a a positive number if the key must be a fixed length,
     * otherwise {@code 0}.
     *
     * @return len
     */
    @IntRange(from = 0)
    int getFixedKeyLength() {
        return fixedKeyLength;
    }
}
