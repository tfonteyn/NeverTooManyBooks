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

package com.hardbacknutter.nevertoomanybooks.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

public interface NetworkConfig {

    /** Private use only. Multiplier. */
    int SECONDS_TO_MILLIS = 1000;

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP socket connect timeout.
     * <p>
     * {@code int} in seconds
     */
    String PK_TIMEOUT_CONNECT_IN_SECONDS = "timeout.connect";

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP socket read timeout
     * <p>
     * {@code int} in seconds
     */
    String PK_TIMEOUT_READ_IN_SECONDS = "timeout.read";

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP requests/response logging.
     * <p>
     * {@code boolean}
     */
    String PK_ENABLE_HTTP_LOGGING = "logging.http.get";

    /**
     * Get the user-configured timeout value for the given key.
     *
     * @param key          to fetch
     * @param defValueInMs default to use if not found
     *
     * @return timeout value in milliseconds
     */
    static int getTimeoutValueInMs(@NonNull final String key,
                                   final int defValueInMs) {
        final int seconds = ServiceLocator.getInstance().getSharedPreferences()
                                          .getInt(key, 0);
        // The value from prefs is in SECONDS
        if (seconds > 0) {
            // convert to milliseconds
            return seconds * SECONDS_TO_MILLIS;
        } else {
            return defValueInMs;
        }
    }

    /**
     * Timeout we allow for a connection to be established.
     *
     * @return milliseconds
     */
    int getConnectTimeoutInMs();

    /**
     * Timeout we allow for getting a response from the remote server.
     *
     * @return milliseconds
     */
    int getReadTimeoutInMs();

    /**
     * Get the throttler for regulating network access.
     *
     * @return throttler to use
     */
    @Nullable
    Throttler getThrottler();

    /**
     * Whether all HTTP calls should be logged.
     * This is a configuration setting the user can change.
     *
     * @return flag
     *
     * @see #setHttpLoggingEnabled(boolean)
     */
    boolean isHttpLoggingEnabled();

    /**
     * For tests only. The configuration is set by the user from a preference screen.
     *
     * @param flag value
     */
    @VisibleForTesting
    void setHttpLoggingEnabled(boolean flag);

    /**
     * The log tag to use for immediate logging calls.
     *
     * @return tag
     */
    @NonNull
    String getLogTag();

    /**
     * The user-visible string resource representing the caller.
     * This will be used in Exception reporting.
     *
     * @return res id
     */
    @StringRes
    int getLogStringRes();
}
