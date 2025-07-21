/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class SniSslSocketFactory
        extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

    public SniSslSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s,
                               String host,
                               int port,
                               boolean autoClose)
            throws IOException {
        SSLSocket sslSocket = (SSLSocket) delegate.createSocket(s, host, port, autoClose);
        enableSni(sslSocket, host);
        return sslSocket;
    }

    @Override
    public Socket createSocket(String host,
                               int port)
            throws IOException {
        SSLSocket sslSocket = (SSLSocket) delegate.createSocket(host, port);
        enableSni(sslSocket, host);
        return sslSocket;
    }

    @Override
    public Socket createSocket(String host,
                               int port,
                               java.net.InetAddress localHost,
                               int localPort)
            throws IOException {
        SSLSocket sslSocket = (SSLSocket) delegate.createSocket(host, port, localHost, localPort);
        enableSni(sslSocket, host);
        return sslSocket;
    }

    @Override
    public Socket createSocket(java.net.InetAddress host,
                               int port)
            throws IOException {
        SSLSocket sslSocket = (SSLSocket) delegate.createSocket(host, port);
        // no hostname here to set for SNI
        return sslSocket;
    }

    @Override
    public Socket createSocket(java.net.InetAddress address,
                               int port,
                               java.net.InetAddress localAddress,
                               int localPort)
            throws IOException {
        SSLSocket sslSocket = (SSLSocket) delegate.createSocket(address, port, localAddress,
                                                                localPort);
        // no hostname here to set for SNI
        return sslSocket;
    }

    private void enableSni(SSLSocket sslSocket,
                           String host) {
        SSLParameters params = sslSocket.getSSLParameters();
        params.setServerNames(Collections.singletonList(new SNIHostName(host)));
        sslSocket.setSSLParameters(params);
    }
}

