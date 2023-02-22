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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;

import org.jsoup.nodes.Document;

public abstract class JsoupSearchEngineBase
        extends SearchEngineBase {

    /** Responsible for loading and parsing the web page. */
    @NonNull
    private final JsoupLoader jsoupLoader;

    /**
     * Constructor.
     *
     * @param config the search engine configuration
     */
    protected JsoupSearchEngineBase(@NonNull final SearchEngineConfig config) {
        super(config);
        jsoupLoader = new JsoupLoader(createFutureGetRequest());
    }

    /**
     * Constructor.
     *
     * @param config      the search engine configuration
     * @param charSetName to use
     */
    protected JsoupSearchEngineBase(@NonNull final SearchEngineConfig config,
                                    @NonNull final String charSetName) {
        this(config);
        jsoupLoader.setCharSetName(charSetName);
    }

    /**
     * Load the url into a parsed {@link org.jsoup.nodes.Document}.
     *
     * @param context           Current context
     * @param url               to load
     * @param requestProperties optional
     *
     * @return the document
     *
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {
        try {
            return jsoupLoader.loadDocument(context, url, requestProperties);

        } catch (@NonNull final IOException e) {
            throw new SearchException(getName(context), e);
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (jsoupLoader) {
            jsoupLoader.cancel();
        }
    }
}
