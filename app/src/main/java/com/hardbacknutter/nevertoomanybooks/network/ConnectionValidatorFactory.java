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

package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.net.CookieManager;
import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbAuth;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibraryAuth;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoAuth;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;

public final class ConnectionValidatorFactory {
    private ConnectionValidatorFactory() {
    }

    /**
     * Factory constructor.
     *
     * @param context   Current context
     * @param siteResId string resource for the site name
     *
     * @return new instance
     *
     * @throws CertificateException     on failures related to a user installed CA
     * @throws IllegalArgumentException for site which do not support connection validation
     */
    @NonNull
    public static ConnectionValidator create(@NonNull final Context context,
                                             @StringRes final int siteResId)
            throws CertificateException {
        if (siteResId == R.string.site_calibre) {
            return new CalibreContentServer.Builder(context).build();
        }

        // We MUST bootstrap it here to ensure it's active before the first http request send
        final CookieManager cookieManager = ServiceLocator.getInstance().getCookieManager();

        if (siteResId == R.string.site_isfdb) {
            // The auth module login IS the validation
            return new IsfdbAuth(cookieManager);

        } else if (siteResId == R.string.site_open_library) {
            // The auth module login IS the validation
            return new OpenLibraryAuth(cookieManager);

        } else if (siteResId == R.string.site_stripinfo_be) {
            // The auth module login IS the validation
            return new StripInfoAuth(cookieManager);
        }

        // The error message is slightly misleading but will have to do.
        // We should never get here anyway as that would be bug... flw
        throw new IllegalArgumentException(context.getString(R.string.error_unknown_host,
                                                             context.getString(siteResId)));
    }
}
