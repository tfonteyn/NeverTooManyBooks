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

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * To access a Calibre server on the host network from the app running on the emulator, run:
 * <pre>
 *    adb reverse tcp:8443 tcp:8443
 * </pre>
 * Windows OS port forwarding from the localhost to the external address:
 * <pre>
 *     netsh interface portproxy add v4tov4 listenaddress=127.0.0.1 listenport=8443 connectaddress=<calibre-ip> connectport=8443
 * </pre>
 * Open the firewall:
 * <pre>
 *     netsh advfirewall firewall add rule name="ALLOW TCP PORT 8443" dir=out action=allow protocol=TCP localport=8443
 * </pre>
 * Close the firewall:
 * <pre>
 *     netsh advfirewall firewall delete rule name="ALLOW TCP PORT 8443"
 * </pre>
 *
 * <strong>IMPORTANT:</strong> this test is configured to accept all certificates!
 */
class CalibreContentServerTest {

    /** 10.0.2.2 is a special alias in the emulator which redirects to the host 127.0.0.1. */
    private static final String URL = "https://10.0.2.2:8443";
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    private CalibreContentServer server;
    private Context context;

    @BeforeEach
    void setup()
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException,
                   CertificateException {
        this.context = ServiceLocator.getInstance().getLocalizedAppContext();


        final X509TrustManager trustAllTrustManager = new TrustAllTrustManager();
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null,
                        new TrustManager[]{trustAllTrustManager},
                        new SecureRandom());

        server = new CalibreContentServer.Builder(context)
                .setUrl(URL)
                .setUser(USERNAME)
                .setPassword(PASSWORD)
                .setSSLContext(sslContext, trustAllTrustManager)
                .setHostnameVerifier((hostname, session) -> true)
                .build();
    }

    @Test
    void filenames()
            throws FileNotFoundException {

        final List<Author> authors = new ArrayList<>();
        authors.add(new Author("Clarke", "Arthur C. "));
        final List<Series> series = new ArrayList<>();
        series.add(new Series("Rama"));

        final Book book = new Book();
        book.setAuthors(authors);

        book.setTitle("Rama");

        final String dir = server.createAuthorDirectoryName(context, book);
        assertEquals("Clarke, Arthur C", dir);

        String fileName = server.createFilename(context, book);
        assertEquals("Rama", fileName);

        book.setTitle("Rama: the omnibus");

        // without a series
        fileName = server.createFilename(context, book);
        assertEquals("Rama_ the omnibus", fileName);

        // now with a series
        book.setSeries(series);
        fileName = server.createFilename(context, book);
        assertEquals("Rama - Rama_ the omnibus", fileName);
    }

    @Test
    void connection()
            throws IOException {

        assertTrue(server.validateConnection(context));
    }
}
