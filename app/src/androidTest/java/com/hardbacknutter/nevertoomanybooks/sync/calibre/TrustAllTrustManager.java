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

package com.hardbacknutter.nevertoomanybooks.sync.calibre;


import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TrustAllTrustManager
        implements X509TrustManager {

    private final X509TrustManager trustManager;

    TrustAllTrustManager()
            throws NoSuchAlgorithmException, KeyStoreException {
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        final KeyStore ks = null;
        tmf.init(ks);
        trustManager = (X509TrustManager) ((tmf.getTrustManagers())[0]);
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] x509Certificate,
                                   final String str) {
        // no checking
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] x509Certificate,
                                   final String str) {
        // no checking
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager.getAcceptedIssuers();
    }
}
