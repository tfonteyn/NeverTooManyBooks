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

package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.logging.HttpLoggingInterceptor;

public final class OkHttpLoggerFactory {

    private OkHttpLoggerFactory() {
    }

    /**
     * Create a {@link HttpLoggingInterceptor} which delegates to {@link LoggerFactory}.
     *
     * @param tag log tag
     *
     * @return interceptor
     */
    @NonNull
    public static HttpLoggingInterceptor getLogger(@NonNull final String tag) {
        final HttpLoggingInterceptor.Level level;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.OKHTTP) {
            level = HttpLoggingInterceptor.Level.BODY;
        } else {
            level = HttpLoggingInterceptor.Level.HEADERS;
        }
        return getLogger(tag, level);
    }

    /**
     * Create a {@link HttpLoggingInterceptor} which delegates to {@link LoggerFactory}.
     * <br>OkHttp NONE -> silent.
     * <br>OkHttp BASIC -> warn.
     * <br>OkHttp HEADERS -> warn
     * <br>OkHttp BODY -> debug
     *
     * @param tag   log tag
     * @param level to set
     *
     * @return interceptor
     */
    @NonNull
    public static HttpLoggingInterceptor getLogger(
            @NonNull final String tag,
            @NonNull final HttpLoggingInterceptor.Level level) {

        final HttpLoggingInterceptor interceptor =
                new HttpLoggingInterceptor(new DelegatingLogger(tag, level));
        interceptor.setLevel(level);
        return interceptor;
    }

    private static final class DelegatingLogger
            implements HttpLoggingInterceptor.Logger {

        private final Logger logger;
        @NonNull
        private final String tag;
        @NonNull
        private final HttpLoggingInterceptor.Level level;

        private DelegatingLogger(@NonNull final String tag,
                                 @NonNull final HttpLoggingInterceptor.Level level) {
            this.tag = tag;
            this.level = level;
            logger = LoggerFactory.getLogger();
        }

        @Override
        public void log(@NonNull final String s) {
            switch (level) {
                case NONE:
                    break;
                case BASIC:
                case HEADERS:
                    logger.w(tag, s);
                    break;
                case BODY:
                    logger.d(tag, s);
                    break;
            }
        }
    }
}
