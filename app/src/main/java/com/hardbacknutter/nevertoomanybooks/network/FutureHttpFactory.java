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

package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpImpl;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;

public final class FutureHttpFactory {

    private FutureHttpFactory() {
    }

    /**
     * Create a basic FutureHttp instance.
     *
     * @param siteResId string resource for the site name; used for logging/messages.
     * @param <R>       the type of the return value for the request
     *
     * @return new instance
     */
    public static <R> FutureHttp<R> create(@StringRes final int siteResId) {
        return new FutureHttpImpl<>(siteResId);
    }

    /**
     * Create a FutureHttp based on the given engine configuration.
     *
     * @param context  Current context
     * @param engineId to use
     * @param <R>      the type of the return value for the request
     *
     * @return new instance
     */
    public static <R> FutureHttp<R> create(@NonNull final Context context,
                                           @NonNull final EngineId engineId) {
        final FutureHttp<R> request = create(engineId.getLabelResId());

        final SearchEngineConfig config = Objects.requireNonNull(engineId.getConfig());
        request.setConnectTimeout(config.getConnectTimeoutInMs(context))
               .setReadTimeout(config.getReadTimeoutInMs(context))
               .setThrottler(config.getThrottler())
               .enableLogging(config.isLogHttpGetRequests());

        return request;
    }

}
