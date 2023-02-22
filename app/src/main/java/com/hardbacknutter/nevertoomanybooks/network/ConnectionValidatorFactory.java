/*
 * @Copyright 2018-2023 HardBackNutter
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

import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoAuth;

public final class ConnectionValidatorFactory {
    private ConnectionValidatorFactory() {
    }

    @NonNull
    public static ConnectionValidator create(@NonNull final Context context,
                                             @StringRes final int siteResId)
            throws CertificateException {
        if (siteResId == R.string.site_calibre) {
            return new CalibreContentServer(context);
        } else if (siteResId == R.string.site_stripinfo_be) {
            return new StripInfoAuth();
        } else {
            throw new IllegalArgumentException(String.valueOf(siteResId));
        }
    }
}
