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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Map;

import org.jsoup.nodes.Document;


public abstract class JsoupSearchEngineBase
        extends SearchEngineBase {

    protected JsoupSearchEngineBase(@NonNull final Context context,
                                    @NonNull final SearchEngineConfig config) {
        super(context, config);
    }

    @WorkerThread
    @NonNull
    public Document loadHtml(@NonNull final Context context,
                             @NonNull final String url,
                             @Nullable final Map<String, String> headers)
            throws SearchException {
        try {
            return httpFutureFactory.loadHtml(context, url, headers);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }

    @WorkerThread
    @NonNull
    public Document loadXml(@NonNull final Context context,
                            @NonNull final String url,
                            @Nullable final Map<String, String> headers)
            throws SearchException {
        try {
            return httpFutureFactory.loadXml(context, url, headers);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }
}
