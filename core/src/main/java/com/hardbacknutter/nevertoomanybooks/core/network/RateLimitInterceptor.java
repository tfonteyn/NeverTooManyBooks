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
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RateLimitInterceptor
        implements Interceptor {
    private static final String TAG = "RateLimitInterceptor";

    /** The default number of times we try to connect. */
    private static final int RETRY_COUNT = 3;

    /** Arbitrary maximum before we throw an exception. */
    private static final int RETRY_MAX_DELAY = 32_000;
    private static final double RETRY_RANDOMIZER = 0.5;

    @NonNull
    private final Throttler throttler;
    private final int throttlerDelay;

    private final boolean logEnabled;

    /**
     * Constructor.
     *
     * @param throttler to use
     * @param logEnabled     flag
     */
    public RateLimitInterceptor(@NonNull final Throttler throttler,
                                final boolean logEnabled) {
        this.throttler = throttler;
        throttlerDelay = 2 * throttler.getDelayInMillis();

        this.logEnabled = logEnabled;
    }

    /**
     * Parse the {@code Retry-After} response header.
     * and return the delay to wait before a new request is send.
     *
     * @param retryAfter header value
     *
     * @return in seconds; {@code 0} if none was specified
     */
    @VisibleForTesting
    @IntRange(from = 0)
    static int parseRetryAfterHeader(@Nullable final String retryAfter) {
        if (retryAfter == null) {
            return 0;
        }
        final String s = retryAfter.strip();
        if (s.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(s);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore, drop through to try parsing a Date
        }

        //noinspection CheckStyle
        try {
            final Instant future = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME)
                                                .toInstant();

            final long seconds = ChronoUnit.SECONDS.between(Instant.now(), future);
            // Sanity tests
            if (seconds <= Integer.MAX_VALUE && seconds >= 0) {
                return (int) seconds;
            }
        } catch (@NonNull final RuntimeException ignore) {
            // ignore
        }

        return 0;
    }


    int getRetryAfterInMs(final int attempt,
                          @Nullable final String retryHeader) {
        final int delaySeconds = parseRetryAfterHeader(retryHeader);
        if (delaySeconds > 0) {
            // use whatever the website told us to use.
            return delaySeconds * 1_000;
        } else {
            // Increase the time exponentially
            final int exponentialDelay = throttlerDelay * (int) (1L << attempt);
            final int cappedDelay = Math.min(exponentialDelay, RETRY_MAX_DELAY);
            // Randomize to spread any near-concurrent requests
            return (int) (cappedDelay * (RETRY_RANDOMIZER + Math.random() * RETRY_RANDOMIZER));
        }
    }

    @NonNull
    @Override
    public Response intercept(@NonNull final Chain chain)
            throws IOException {
        final Request request = chain.request();
        Response response = chain.proceed(request);

        int attempt = 0;

        while (response.code() == HttpTooManyRequestsException.HTTP_TOO_MANY_REQUESTS
               && attempt < RETRY_COUNT) {
            attempt++;

            final String retryHeader = response.header(HttpConstants.RESPONSE_HEADER_RETRY_AFTER);
            final int retryAfterMs = getRetryAfterInMs(attempt, retryHeader);

            if (logEnabled) {
                LoggerFactory.getLogger()
                             .w(TAG, "Response 429|retryHeader=" + retryHeader,
                                "retryAfterMs=" + retryAfterMs,
                                "attempt=" + attempt);
            }

            throttler.onTooManyRequests(retryAfterMs);

            // Close the previous response and try again
            response.close();
            response = chain.proceed(request);
        }

        return response;
    }
}
