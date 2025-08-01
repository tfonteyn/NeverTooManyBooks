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

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class SniSslSocketFactory
        extends SSLSocketFactory {
    @NonNull
    private final SSLSocketFactory delegate;

    SniSslSocketFactory(@NonNull final SSLSocketFactory delegate) {
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
    public Socket createSocket(@NonNull final Socket s,
                               @NonNull final String host,
                               final int port,
                               final boolean autoClose)
            throws IOException {
        final SSLSocket sslSocket = (SSLSocket) delegate.createSocket(s, host, port, autoClose);
        enableSni(sslSocket, host);
        return sslSocket;
    }

    @Override
    public Socket createSocket(@NonNull final String host,
                               final int port)
            throws IOException {
        final SSLSocket sslSocket = (SSLSocket) delegate.createSocket(host, port);
        enableSni(sslSocket, host);
        return sslSocket;
    }

    @Override
    public Socket createSocket(@NonNull final String host,
                               final int port,
                               @NonNull final java.net.InetAddress localHost,
                               final int localPort)
            throws IOException {
        final SSLSocket sslSocket = (SSLSocket) delegate.createSocket(host, port, localHost,
                                                                      localPort);
        enableSni(sslSocket, host);
        return sslSocket;
    }

    @Override
    public Socket createSocket(@NonNull final java.net.InetAddress host,
                               final int port)
            throws IOException {
        // no hostname here to set for SNI
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(@NonNull final java.net.InetAddress address,
                               final int port,
                               @NonNull final java.net.InetAddress localAddress,
                               final int localPort)
            throws IOException {
        // no hostname here to set for SNI
        return delegate.createSocket(address, port, localAddress, localPort);
    }

    private void enableSni(@NonNull final SSLSocket sslSocket,
                           @NonNull final String host) {
        final SSLParameters params = sslSocket.getSSLParameters();
        params.setServerNames(Collections.singletonList(new SNIHostName(host)));
        sslSocket.setSSLParameters(params);
    }
}

