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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * 2025-04-01 www.dnb.be and katalog.dnb.de installed new certificates.
 * The main site is fine, but the katalog site has a broken certificate chain.
 * Browsers will simply download the missing intermediate-ca cert,
 * but we have to do it manually.
 * <p>
 * Instead of downloading each time, we simply created the two possible chains
 * as pem-files.
 * <p>
 * A second issue is that the site only works with SNI. This is not a bad thing,
 * but due to the kludge of the certs, we will ALSO need to handle the SNI setup manually.
 * <p>
 * URGENT: current dnb certs will expire 2026-03-31 and will need replacing.
 *  Hopefully the site will fix their issues before...
 * <p>
 * It seems that they included the one for "app.dnb.de" instead?
 *
 * @see <a href="https://www.ssllabs.com/ssltest/analyze.html?d=katalog.dnb.de&latest">
 *         ssllabs.com</a>
 */
final class DnbSslContextFactory {
    /** 3-cert chain. */
    private static final String CERT_FILE_NAME = "katalog.dnb.de.pem";
    /** 4-cert chain. */
    private static final String CERT_FILE_NAME2 = "katalog.dnb.de2.pem";

    @Nullable
    private static TrustManagerFactory tmf = null;
    @Nullable
    private static SSLContext sslContext = null;

    private DnbSslContextFactory() {
    }

    @NonNull
    static X509TrustManager getTmf(@NonNull final Context context)
            throws CertificateException {
        if (sslContext == null) {
            init(context);
        }

        if (tmf != null) {
            for (final TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
        }
        throw new CertificateException("No X509TrustManager found");
    }

    @NonNull
    static SSLContext getSslContext(@NonNull final Context context)
            throws CertificateException {

        if (sslContext == null) {
            init(context);
        }
        return sslContext;
    }

    private static void init(@NonNull final Context context)
            throws CertificateException {
        try {
            final X509Certificate c1 = getCertificate(context, CERT_FILE_NAME);
            final X509Certificate c2 = getCertificate(context, CERT_FILE_NAME2);

            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry(CERT_FILE_NAME, c1);
            keyStore.setCertificateEntry(CERT_FILE_NAME2, c2);

            tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

        } catch (@NonNull final KeyManagementException e) {
            // wrap for ease of handling; it is in fact almost certain that
            // we would throw a CertificateException BEFORE we can even
            // get a KeyManagementException
            throw new CertificateException(e);

        } catch (@NonNull final IOException | KeyStoreException | NoSuchAlgorithmException ignore) {
            // All these exceptions can be ignored, and we are assuming
            // that the server does not need a cert, or that the cert is
            // loaded in the Android system keystore.
        }
    }

    @NonNull
    private static X509Certificate getCertificate(@NonNull final Context context,
                                                  @NonNull final String fileName)
            throws CertificateException, IOException {
        try (InputStream is = context.getAssets().open(fileName)) {
            return (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(is);
        }
    }
}
