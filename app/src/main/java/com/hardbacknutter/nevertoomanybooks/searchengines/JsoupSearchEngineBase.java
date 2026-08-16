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

import androidx.annotation.AnyThread;
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
    private String charSetName;
    /** Responsible for loading and parsing the web page. */
    @Nullable
    private JsoupLoader jsoupLoader;

    /**
     * Constructor.
     *
     * @param context Current context. NOT stored.
     * @param config  the search engine configuration
     *
     * @see EngineId#createSearchEngine(Context)
     */
    protected JsoupSearchEngineBase(@NonNull final Context context,
                                    @NonNull final SearchEngineConfig config) {
        super(context, config);
    }

    /**
     * Set the character set for jsoup parsing.
     * Default is {@code null} to let JSoup auto-detect it.
     * <p>
     * Only needed of the sites character set does not match the actual used one.
     *
     * @param charSetName to use; or {@code null} to auto-select.
     */
    protected void setCharSetName(@Nullable final String charSetName) {
        this.charSetName = charSetName;
    }

    @WorkerThread
    @NonNull
    public Document loadHtml(@NonNull final Context context,
                             @NonNull final String url,
                             @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {
        return loadDocument(context, Parser.htmlParser(), url, requestProperties);
    }

    @WorkerThread
    @NonNull
    public Document loadXml(@NonNull final Context context,
                            @NonNull final String url,
                            @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {
        return loadDocument(context, Parser.xmlParser(), url, requestProperties);
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
    private Document loadDocument(@NonNull final Context context,
                                 @NonNull final Parser parser,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {
        try {
            final boolean logEnabled = getConfig().isLogHttpGetRequests();
            jsoupLoader = new JsoupLoader(createGetDocumentRequest(context), logEnabled);
            jsoupLoader.setCharSetName(charSetName);
            jsoupLoader.setSslContext(getSslContext());

            return jsoupLoader.loadDocument(context, parser, url, requestProperties);

        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            jsoupLoader = null;
        }
    }

    @Override
    @AnyThread
    public void cancel() {
        super.cancel();
        synchronized (this) {
            if (jsoupLoader != null) {
                jsoupLoader.cancel();
            }
        }
    }
}
