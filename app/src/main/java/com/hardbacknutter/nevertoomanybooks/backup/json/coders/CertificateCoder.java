/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Handles a <strong>single</strong> certificate.
 */
public class CertificateCoder
        implements JsonCoder<X509Certificate> {

    /** JSON key. */
    private static final String CERT = "certificate";
    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----\n";
    private static final String END_CERT = "-----END CERTIFICATE-----\n";

    @NonNull
    @Override
    public JSONObject encode(@NonNull final X509Certificate certificate)
            throws JSONException {
        final JSONObject data = new JSONObject();
        try {
            data.put(CERT, BEGIN_CERT
                           + Base64.encodeToString(certificate.getEncoded(), 0)
                           + END_CERT);
        } catch (@NonNull final CertificateEncodingException e) {
            throw new JSONException(e.getMessage());
        }

        return data;
    }

    @NonNull
    @Override
    public X509Certificate decode(@NonNull final JSONObject data)
            throws JSONException {

        String dataStr = data.optString(CERT);
        if (dataStr != null && !dataStr.isEmpty()) {
            dataStr = dataStr.replace(BEGIN_CERT, "").replace(END_CERT, "");
            final byte[] b = Base64.decode(dataStr.getBytes(StandardCharsets.US_ASCII), 0);
            try (InputStream is = new ByteArrayInputStream(b)) {
                return (X509Certificate) CertificateFactory.getInstance("X.509")
                                                           .generateCertificate(is);
            } catch (@NonNull final IOException | CertificateException e) {
                throw new JSONException(e.getMessage());
            }
        }
        throw new JSONException("no input");
    }
}
