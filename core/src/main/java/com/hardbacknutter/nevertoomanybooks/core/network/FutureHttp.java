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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

import org.xml.sax.SAXException;

public interface FutureHttp<R> {

    /**
     * Set the optional connect-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for infinite timeout
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setConnectTimeout(@IntRange(from = 0) int timeoutInMs);

    /**
     * Set the optional read-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for infinite timeout
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setReadTimeout(@IntRange(from = 0) int timeoutInMs);

    /**
     * Set whether redirects should be followed.
     * <p>
     * The default is unset, i.e. use the OS default.
     *
     * @param followRedirects flag
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setInstanceFollowRedirects(boolean followRedirects);

    /**
     * <a href="https://developer.android.com/reference/java/net/HttpURLConnection.html#response-handling">HttpURLConnection</a>
     * HttpURLConnection will follow up to five HTTP redirects. It will follow redirects
     * from one origin server to another. This implementation doesn't follow redirects
     * from HTTPS to HTTP or vice versa.
     * <p>
     * <strong>This does not always work, for some requests it responds with a 404</strong>: .
     * <p>
     * Example:
     * <br>connecting to: {@code https://covers.openlibrary.org/b/id/13414586-M.jpg?default=false}
     * <br>404
     * {@code https://archive.org/download/m_covers_0013/m_covers_0013_41.zip/0013414586-M.jpg}
     *
     * @param enable404Redirect flag
     */
    void setEnable404Redirect(boolean enable404Redirect);

    /**
     * Set the buffer size to use for the input stream.
     *
     * @param bufferSize in bytes
     */
    void setBufferSize(int bufferSize);

    /**
     * Override the default retry count.
     *
     * @param retryCount to use, must be at least {@code 1}.
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setRetryCount(@IntRange(from = 1) int retryCount);

    /**
     * For secure connections.
     *
     * @param sslContext (optional) SSL context to use instead of the system default.
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setSSLContext(@Nullable SSLContext sslContext);

    /**
     * For secure connections.
     *
     * @param verifier (optional) for custom checking of hostnames in for
     *                 example certificate handling with self-signed certificates.
     *                 {@code null} to use the system default.
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setHostnameVerifier(@Nullable HostnameVerifier verifier);

    /**
     * Is logging enabled.
     *
     * @return flag
     */
    boolean isLoggingEnabled();

    /**
     * Add a connection request property.
     *
     * @param key   to set
     * @param value to set; use {@code null} to remove instead of add the property
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    FutureHttp<R> setRequestProperty(@NonNull String key,
                                     @Nullable String value);

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
    R head(@NonNull String url,
           @NonNull ActionFunction<HttpURLConnection, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException;

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
    @Nullable
    R get(@NonNull String url,
          @NonNull ResponseProcessor<InputStream, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException;

    /**
     * Send the GET and use the given {@link ResponseProcessor} to handle the response.
     * <p>
     * This method handles gzip encoding automatically.
     *
     * @param url               to use
     * @param responseProcessor which will receive the response page as a single {@code String}
     *
     * @return the response page as a single {@code String}
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       The covers directory is not available
     */
    @NonNull
    R getAsString(@NonNull String url,
                  @NonNull ResponseProcessor<String, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException;

    /**
     * Send the POST.
     *
     * @param urlStr            to use
     * @param postBody          to send
     * @param responseProcessor which will receive the response InputStream
     *
     * @return the processed response; can be {@code null} if there was no response body.
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       The covers directory is not available
     */
    @Nullable
    R post(@NonNull String urlStr,
           @NonNull String postBody,
           @Nullable ActionFunction<InputStream, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException;

    /**
     * Request to cancel an ongoing http request.
     */
    void cancel();

    /**
     * Same as {@code java.util.function.Function} but with checked exceptions
     * thus avoiding packing/unpacking.
     *
     * @param <T> input
     *            Typically the actual {@link HttpURLConnection}
     *            or a preprocessed {@link InputStream} from that connection
     * @param <R> output
     */
    @FunctionalInterface
    interface ActionFunction<T, R> {
        /**
         * Applies this function to the given argument.
         *
         * @param t the function argument
         *
         * @return the function result
         *
         * @throws IOException      on generic/other IO failures
         * @throws StorageException The covers directory is not available
         * @throws SAXException     on parser problems if a SAX parser was used
         */
        R apply(T t)
                throws IOException,
                       StorageException,
                       SAXException;
    }
}
