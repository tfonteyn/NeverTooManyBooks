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

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * A thin layer between the {@link SearchEngineBase} and the actual engine implementation.
 * This class provides methods to load an html or xml document for Jsoup based engines.
 */
public abstract class JsoupSearchEngineBase
        extends SearchEngineBase {

    @Nullable
    private final String charSetName;

    /**
     * Constructor.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    protected JsoupSearchEngineBase(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        this(appContext, config, null);
    }

    /**
     * Constructor.
     *
     * @param appContext  The <strong>application</strong> context
     * @param config      the search engine configuration
     * @param charSetName to use; or {@code null} to auto-select.
     */
    protected JsoupSearchEngineBase(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config,
                                    @Nullable final String charSetName) {
        super(appContext, config);
        this.charSetName = charSetName;
    }


    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {
        return loadDocument(context, Parser.htmlParser(), url, requestProperties);
    }

    /**
     * Load the url into a parsed {@link org.jsoup.nodes.Document}.
     *
     * @param context           Current context
     * @param parser            to use
     * @param url               to load
     * @param requestProperties (optional) extra headers to add/override
     *
     * @return the document
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final Parser parser,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {
        try {
            final JsoupLoader jsoupLoader = new JsoupLoader(createGetDocumentRequest(context),
                                                            getEngineId());
            jsoupLoader.setCharSetName(charSetName);
            jsoupLoader.setSslContext(getSslContext());

            return jsoupLoader.loadDocument(context, parser, url, requestProperties);

        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }
}
