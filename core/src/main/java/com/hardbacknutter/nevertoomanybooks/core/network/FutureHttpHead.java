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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

/**
 * @param <T> the type of the return value for the request
 */
public final class FutureHttpHead<T>
        extends FutureHttpGetBase<T> {

    /**
     * Private constructor.
     *
     * @param siteResId string resource for the site name
     */
    public FutureHttpHead(@StringRes final int siteResId) {
        super(siteResId);
    }

    /**
     * Send a {@code HEAD} request.
     *
     * @param url               to connect to
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
    public T send(@NonNull final String url,
                  @NonNull final Function<HttpURLConnection, T> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return Objects.requireNonNull(execute(url, "HEAD", false, request -> {
            try {
                final HttpURLConnection connection = connect(request);
                return responseProcessor.apply(connection);

            } catch (@NonNull final IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }
}
