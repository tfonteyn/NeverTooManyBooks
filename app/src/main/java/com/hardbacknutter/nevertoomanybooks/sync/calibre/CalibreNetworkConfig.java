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

package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.network.NetworkConfig;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public class CalibreNetworkConfig
        implements NetworkConfig {

    /**
     * Whether to use a Throttler. Default is {@code true}.
     *
     * @see #THROTTLER_DELAY_IN_MILLIS
     */
    static final String PK_USE_THROTTLER = CalibreContentServer.PREFERENCE_KEY + ".throttler";

    private static final int CONNECT_TIMEOUT_IN_MS = 5_000;
    private static final int READ_TIMEOUT_IN_MS = 3_000;

    /**
     * While a Calibre server is typically a private in-house setup, we still,
     * by default, apply a Throttler to accomodate using weak hardware (e.g. raspberry-pi)
     * The 200 millis was arbitrarily chosen.
     * This can be switched off by the user.
     *
     * @see #PK_USE_THROTTLER
     */
    private static final int THROTTLER_DELAY_IN_MILLIS = 200;

    @Nullable
    private final Throttler throttler;

    CalibreNetworkConfig() {
        final Prefs prefs = ServiceLocator.getInstance().getSharedPreferences();
        final boolean useThrottler = prefs.getBoolean(PK_USE_THROTTLER, true);

        throttler = useThrottler ? new Throttler(THROTTLER_DELAY_IN_MILLIS) : null;

    }

    @Override
    public int getConnectTimeoutInMs() {
        return NetworkConfig.getTimeoutValueInMs(
                CalibreContentServer.PREFERENCE_KEY + '.' + PK_TIMEOUT_CONNECT_IN_SECONDS,
                CONNECT_TIMEOUT_IN_MS);
    }

    @Override
    public int getReadTimeoutInMs() {
        return NetworkConfig.getTimeoutValueInMs(
                CalibreContentServer.PREFERENCE_KEY + '.' + PK_TIMEOUT_READ_IN_SECONDS,
                READ_TIMEOUT_IN_MS);
    }

    @Nullable
    @Override
    public Throttler getThrottler() {
        return throttler;
    }

    @Override
    public boolean isHttpLoggingEnabled() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(CalibreContentServer.PREFERENCE_KEY
                                         + '.' + PK_ENABLE_HTTP_LOGGING,
                                         false);
    }

    @Override
    public void setHttpLoggingEnabled(final boolean flag) {
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(CalibreContentServer.PREFERENCE_KEY
                                  + '.' + PK_ENABLE_HTTP_LOGGING,
                                  flag)
                      .apply();
    }

    @NonNull
    @Override
    public String getLogTag() {
        return "CalibreContentServer";
    }

    @Override
    public int getLogStringRes() {
        return R.string.site_calibre;
    }
}
