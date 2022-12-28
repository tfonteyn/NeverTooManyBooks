/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.jsoup.nodes.Document;

public abstract class JsoupSearchEngineBase
        extends SearchEngineBase {

    /** accumulate all Authors for this book. */
    protected final ArrayList<Author> authorList = new ArrayList<>();
    /** accumulate all Series for this book. */
    protected final ArrayList<Series> seriesList = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    protected final ArrayList<Publisher> publisherList = new ArrayList<>();

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
     * @param context Current context
     * @param url     to load
     *
     * @return the document
     *
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final String url)
            throws SearchException, CredentialsException {
        try {
            return jsoupLoader.loadDocument(context, url);

        } catch (@NonNull final IOException e) {
            throw new SearchException(getName(context), e);
        }
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     * <p>
     * Implementations <strong>must</strong> call this super first
     * to ensure cached data is purged.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param bookData    Bundle to update
     *
     * @throws StorageException     on storage related failures
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @WorkerThread
    @CallSuper
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Bundle bookData)
            throws StorageException, SearchException, CredentialsException {
        // yes, instead of forcing child classes to call this super,
        // we could make them call a 'clear()' method instead.
        // But this way is more future oriented... maybe we'll need/can share more logic/data
        // between children... or change our mind later on.

        authorList.clear();
        seriesList.clear();
        publisherList.clear();
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (jsoupLoader) {
            jsoupLoader.cancel();
        }
    }
}
