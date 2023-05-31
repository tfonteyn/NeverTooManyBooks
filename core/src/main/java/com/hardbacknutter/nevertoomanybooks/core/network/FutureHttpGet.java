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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;

import com.hardbacknutter.nevertoomanybooks.core.parsers.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;

import org.xml.sax.SAXException;

public final class FutureHttpGet<T>
        extends FutureHttpGetBase<T> {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Constructor.
     *
     * @param siteResId string resource for the site name
     */
    public FutureHttpGet(@StringRes final int siteResId) {
        super(siteResId);
    }

    /**
     * Send the GET and use the given {@link ResponseProcessor} to handle the response.
     * <p>
     * This method handles gzip encoding automatically.
     *
     * @param url               to use
     * @param responseProcessor which will receive the response InputStream
     *
     * @return the processed response
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       The covers directory is not available
     */
    @NonNull
    public T get(@NonNull final String url,
                 @NonNull final ResponseProcessor<T> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {
        return get(url, DEFAULT_BUFFER_SIZE, responseProcessor);
    }

    /**
     * Send the GET and use the given {@link ResponseProcessor} to handle the response.
     * <p>
     * This method handles gzip encoding automatically.
     *
     * @param url               to use
     * @param bufferSize        to use for the input stream
     * @param responseProcessor which will receive the response InputStream
     *
     * @return the processed response
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       The covers directory is not available
     */
    @NonNull
    public T get(@NonNull final String url,
                 final int bufferSize,
                 @NonNull final ResponseProcessor<T> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return Objects.requireNonNull(execute(url, "GET", false, request -> {
            try {
                connect(request);

                try (BufferedInputStream bis = new BufferedInputStream(
                        request.getInputStream(), bufferSize)) {
                    if (HttpConstants.isZipped(request)) {
                        try (GZIPInputStream gzs = new GZIPInputStream(bis)) {
                            return responseProcessor.parse(request, gzs);
                        }
                    } else {
                        return responseProcessor.parse(request, bis);
                    }
                }
            } catch (@NonNull final IOException e) {
                throw new UncheckedIOException(e);
            } catch (@NonNull final StorageException e) {
                throw new UncheckedStorageException(e);
            } catch (@NonNull final SAXException e) {
                throw new UncheckedSAXException(e);
            }
        }));
    }
}
