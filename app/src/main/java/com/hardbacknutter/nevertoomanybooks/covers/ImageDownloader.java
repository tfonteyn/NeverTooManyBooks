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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpForbiddenException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpTooManyRequestsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpUnauthorizedException;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Given a URL and a filename, this class uses an {@link OkHttpClient} to download an image,
 * and the {@link CoverStorage} to store the image.
 */
public class ImageDownloader {

    private static final String TAG = "ImageDownloader";

    /** The prefix an embedded image url would have. */
    private static final String DATA_IMAGE_JPEG_BASE_64 = "data:image/jpeg;base64,";

    /**
     * DEBUG HACK... when running in JUnit, this variable is set to 'true'
     * by the test code.
     * <p>
     * When running as a JUnit test, the file.renameTo done during the
     * {@link CoverStorage#persist(InputStream, File)} operation will fail.
     * As that is independent of the JUnit test/purpose, we will fake success here.
     */
    @VisibleForTesting
    public static boolean IGNORE_RENAME_FAILURE;

    @NonNull
    private final OkHttpClient client;
    @Nullable
    private final Throttler throttler;

    private final boolean logEnabled;
    @StringRes
    private final int siteResId;

    /** Current cancelable call. */
    @Nullable
    private Call call;

    /**
     * Constructor.
     *
     * @param client     to use
     * @param throttler  to use
     * @param siteResId  for logging
     * @param logEnabled flag
     */
    public ImageDownloader(@NonNull final OkHttpClient client,
                           @Nullable final Throttler throttler,
                           @StringRes final int siteResId,
                           final boolean logEnabled) {

        this.client = client;
        this.throttler = throttler;
        this.siteResId = siteResId;
        this.logEnabled = logEnabled;
    }

    private static void logRequest(@NonNull final Request request) {
        final String msg = request
                .headers()
                .toMultimap()
                .entrySet()
                .stream()
                .map(es -> "Request Header: " + es.getKey() + '='
                           + String.join("|", es.getValue()))
                .collect(Collectors.joining("\n"));

        final Logger logger = LoggerFactory.getLogger();
        logger.d(TAG, "url: " + request.url().url());
        logger.d(TAG, "headers", "\n" + msg);
    }

    private static void logResponse(@NonNull final Response response) {
        final String msg = response
                .headers()
                .toMultimap()
                .entrySet()
                .stream()
                .map(es -> "Response Header: " + es.getKey() + "="
                           + String.join("|", es.getValue()))
                .collect(Collectors.joining("\n"));

        final Logger logger = LoggerFactory.getLogger();
        logger.d(TAG, "url: " + response.request().url().url());
        logger.d(TAG, "response: " + response.code(), response.message());
        logger.d(TAG, "headers", "\n" + msg);
    }

    private static boolean isZipped(@NonNull final Response response) {
        return "gzip".equalsIgnoreCase(response.header(
                HttpConstants.RESPONSE_HEADER_CONTENT_ENCODING));
    }

    /**
     * Given a URL, get an image and save to the given file.
     * Must be called from a background task.
     *
     * @param request  to execute
     * @param filename filename to write to
     *
     * @return Downloaded File
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on generic/other IO failures
     */
    @NonNull
    @WorkerThread
    public Optional<File> fetch(@NonNull final Request request,
                                @NonNull final String filename)
            throws IOException, CoverStorageException {

        final CoverStorage coverStorage = ServiceLocator.getInstance().getCoverStorage();
        final File tempDir = coverStorage.getTempDir();

        final File destFile = new File(tempDir, filename);

        @Nullable
        final File savedFile;

        try {
            // Uncommon, but some sites use embedded base64 images.
            final String urlStr = request.url().url().toString();
            if (urlStr.startsWith(DATA_IMAGE_JPEG_BASE_64)) {
                try (OutputStream os = new FileOutputStream(destFile)) {
                    final byte[] image = Base64
                            .decode(urlStr.substring(DATA_IMAGE_JPEG_BASE_64.length())
                                          .getBytes(StandardCharsets.UTF_8), 0);
                    os.write(image);
                }
                savedFile = destFile;

            } else {
                call = client.newCall(request);

                if (logEnabled) {
                    logRequest(request);
                }

                if (throttler != null) {
                    throttler.waitUntilRequestAllowed();
                }
                try (Response response = call.execute()) {
                    checkResponseCode(response);

                    try (InputStream source = response.body().byteStream()) {
                        if (isZipped(response)) {
                            try (GZIPInputStream gzipInputStream = new GZIPInputStream(source)) {
                                savedFile = coverStorage.persist(gzipInputStream, destFile);
                            }
                        } else {
                            savedFile = coverStorage.persist(source, destFile);
                        }
                    }
                }
            }

            // too small ? reject
            // too big: N/A as we assume a picture from a website is already a good size
            if (coverStorage.isAcceptableSize(savedFile)) {
                return Optional.of(savedFile);
            }
            // discard
            FileUtils.backgroundDelete(savedFile);
            return Optional.empty();

        } catch (@NonNull final IOException e) {
            FileUtils.backgroundDelete(destFile);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES || IGNORE_RENAME_FAILURE) {
                LoggerFactory.getLogger().e(TAG, e, "fetch");

                if (IGNORE_RENAME_FAILURE) {
                    return Optional.of(destFile);
                }
            }

            // we swallow IOExceptions, **EXCEPT** when the disk is full.
            if (FileUtils.isDiskFull(e)) {
                throw e;
            }
            return Optional.empty();

        } finally {
            call = null;
        }
    }

    /**
     * Check the response code and throw exceptions as appropriate.
     *
     * @param response to check
     *
     * @throws HttpUnauthorizedException    401: Unauthorized.
     * @throws HttpForbiddenException       403: Forbidden
     * @throws HttpNotFoundException        404: Not Found.
     * @throws SocketTimeoutException       408: Request Time-Out.
     * @throws HttpTooManyRequestsException 429: Too Many Requests.
     * @throws HttpStatusException          on any other HTTP failures
     */
    @WorkerThread
    private void checkResponseCode(@NonNull final Response response)
            throws
            HttpUnauthorizedException,
            HttpForbiddenException,
            HttpNotFoundException,
            SocketTimeoutException,
            HttpTooManyRequestsException,
            HttpStatusException {

        final int responseCode = response.code();

        if (logEnabled) {
            logResponse(response);
        }

        if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
            return;
        }

        @Nullable
        final String location = response.header(HttpConstants.RESPONSE_HEADER_LOCATION);

        switch (responseCode) {
            case HttpURLConnection.HTTP_UNAUTHORIZED: {
                throw new HttpUnauthorizedException(siteResId,
                                                    response.message(),
                                                    response.request().url().url(),
                                                    location);
            }
            // 403 if we're 100% blocked.
            // 405 if we hit a wall like
            // https://anubis.techaro.lol/docs/design/how-anubis-works/
            case HttpURLConnection.HTTP_FORBIDDEN:
            case HttpURLConnection.HTTP_BAD_METHOD: {
                throw new HttpForbiddenException(siteResId,
                                                 response.message(),
                                                 response.request().url().url(),
                                                 location);
            }
            case HttpURLConnection.HTTP_NOT_FOUND: {
                throw new HttpNotFoundException(siteResId,
                                                response.message(),
                                                response.request().url().url(),
                                                location);
            }
            case HttpURLConnection.HTTP_CLIENT_TIMEOUT: {
                // for easier reporting issues to the user, map a 408 to an STE
                throw new SocketTimeoutException("408 " + response.message());
            }
            case HttpTooManyRequestsException.HTTP_TOO_MANY_REQUESTS: {
                throw new HttpTooManyRequestsException(
                        siteResId,
                        response.header(HttpConstants.RESPONSE_HEADER_RETRY_AFTER),
                        response.message(),
                        response.request().url().url(),
                        location);
            }
            default: {
                throw new HttpStatusException(siteResId,
                                              responseCode,
                                              response.message(),
                                              response.request().url().url(),
                                              location);
            }
        }
    }

    public void cancel() {
        synchronized (this) {
            if (call != null) {
                call.cancel();
            }
        }
    }
}
