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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.network.NetworkConfig;
import com.hardbacknutter.prefslib.SettingsManager;

public final class CommonSettingsFactory {

    private CommonSettingsFactory() {
    }

    /**
     * Add the common username and password fields.
     *
     * @param factory to use
     * @param pk      preference prefix key (site)
     */
    public static void credentials(@NonNull final SettingsManager.Builder factory,
                                   @NonNull final String pk) {
        factory.header(R.string.lbl_credentials);
        factory.text(pk + SiteAuthModule.PK_SUFFIX_HOST_USER,
                     R.string.username, null, p -> {
                    p.setIcon(R.drawable.person_24px);
                });

        factory.password(pk + SiteAuthModule.PK_SUFFIX_HOST_PASSWORD,
                         R.string.password, null, p -> {
                    p.setIcon(R.drawable.password_24px);
                });
    }

    /**
     * Add the common connection and read timeouts.
     *
     * @param factory to use
     * @param pk      preference prefix key (site)
     */
    public static void timeouts(@NonNull final SettingsManager.Builder factory,
                                @NonNull final String pk) {
        factory.header(R.string.lbl_timeouts);
        factory.floatRange(pk + '.' + NetworkConfig.PK_TIMEOUT_CONNECT_IN_SECONDS,
                           R.string.lbl_connection_timeout,
                           3, 60, null, p -> {
                    p.setIcon(R.drawable.more_time_24px);
                    p.setValue(20);
                });

        factory.floatRange(pk + '.' + NetworkConfig.PK_TIMEOUT_READ_IN_SECONDS,
                           R.string.lbl_read_timeout,
                           3, 120, null, p -> {
                    p.setIcon(R.drawable.more_time_24px);
                    p.setValue(60);
                });
    }

    /**
     * Add the common troubleshooting options.
     *
     * @param factory to use
     * @param pk      preference prefix key (site)
     */
    public static void troubleshoot(@NonNull final SettingsManager.Builder factory,
                                    @NonNull final String pk) {
        factory.header(R.string.lbl_troubleshooting, p -> {
            p.setSummary(R.string.lbl_troubleshooting_warning);
        });
        factory.bool(pk + '.' + NetworkConfig.PK_ENABLE_HTTP_LOGGING,
                     R.string.pt_enable_extra_logging, null, p -> {
                    p.setIcon(R.drawable.troubleshoot_24px);
                });
    }
}
