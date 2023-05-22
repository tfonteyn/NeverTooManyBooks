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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * LibraryThing is 40% owned by AbeBooks which is owned by Amazon.
 * 2023-01-14: the website now publicly states the API is permanently down.
 * There is also a captcha for login, so switching to JSoup style html scraping is out.
 * So as far as search/sync is concerned, this site is dead.
 * <p>
 * <a href="https://www.librarything.com/services/">LibraryThing API</a>
 * <p>
 * We can still:
 * - Search for alternative editions.
 * - View books on the site for which we previously stored a native id.
 * Keep in mind that the LT id can also be gathered from other sites (e.g. OpenLibrary)
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ViewBookByExternalId,
                   SearchEngine.AlternativeEditions {

    /** Preferences - Type: {@code String}. */
    public static final String PK_HOST_URL = EngineId.LibraryThing.getPreferenceKey()
                                             + '.' + Prefs.pk_host_url;
    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public LibraryThingSearchEngine(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.
        return getLocale(context, getHostUrl());
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final String externalId) {
        return getHostUrl() + String.format("/work/%1$s", externalId);
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
            }
        }
    }

    /**
     * Search for edition data.
     * <p>
     * No dev-key needed for this call.
     * <p>
     * <strong>Note:</strong> we assume the isbn numbers retrieved from the site are valid.
     * No extra checks are made at this point.
     *
     * <br>{@inheritDoc}
     */
    @WorkerThread
    @NonNull
    @Override
    public List<String> searchAlternativeEditions(@NonNull final Context context,
                                                  @NonNull final String validIsbn)
            throws SearchException {

        futureHttpGet = createFutureGetRequest(true);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final LibraryThingEditionHandler handler = new LibraryThingEditionHandler();

        final String url = getHostUrl() + String.format("/api/thingISBN/%1$s", validIsbn);

        //noinspection OverlyBroadCatchBlock
        try {
            final SAXParser parser = factory.newSAXParser();
            futureHttpGet.get(url, response -> {
                try (BufferedInputStream bis = new BufferedInputStream(
                        response.getInputStream())) {
                    if (HttpConstants.isZipped(response)) {
                        try (GZIPInputStream gzs = new GZIPInputStream(bis)) {
                            parser.parse(gzs, handler);
                        }
                    } else {
                        parser.parse(bis, handler);
                    }
                    return true;
                } catch (@NonNull final IOException e) {
                    throw new UncheckedIOException(e);
                } catch (@NonNull final SAXException e) {
                    throw new UncheckedSAXException(e);
                }
            });

        } catch (@NonNull final StorageException | ParserConfigurationException
                | SAXException | IOException e) {
            throw new SearchException(getEngineId(), e);
        }
        return handler.getResult();
    }

    /**
     * Parser Handler to collect the edition data.
     * <p>
     * http://www.librarything.com/api/thingISBN/{ISBN}
     * <p>
     * Typical request output:
     * <pre>
     *     {@code
     *   <?xml version="1.0" encoding="utf-8"?>
     *   <idlist>
     *     <isbn>0380014300</isbn>
     *     <isbn>0839824270</isbn>
     *     <isbn>0722194390</isbn>
     *     <isbn>0783884257</isbn>
     *     ...etc...
     *     <isbn>2207301907</isbn>
     *   </idlist>
     *   }
     * </pre>
     */
    static class LibraryThingEditionHandler
            extends DefaultHandler {

        /** isbn tag in an editions xml response. */
        private static final String XML_ISBN = "isbn";

        /** XML content. */
        @SuppressWarnings("StringBufferField")
        private final StringBuilder builder = new StringBuilder();
        /** List of ISBN numbers for all found editions. */
        private final List<String> isbnList = new ArrayList<>();

        /**
         * Get the results.
         *
         * @return the list with ISBN numbers.
         */
        @NonNull
        public List<String> getResult() {
            return isbnList;
        }

        @Override
        @CallSuper
        public void endElement(@NonNull final String uri,
                               @NonNull final String localName,
                               @NonNull final String qName) {

            if (localName.equalsIgnoreCase(XML_ISBN)) {
                isbnList.add(builder.toString());
            }

            // Always reset the length. This is not entirely the right thing to do, but works
            // because we always want strings from the lowest level (leaf) XML elements.
            // To be completely correct, we should maintain a stack of builders that are pushed and
            // popped as each startElement/endElement is called. But lets not be pedantic for now.
            builder.setLength(0);
        }

        @Override
        @CallSuper
        public void characters(final char[] ch,
                               final int start,
                               final int length) {
            builder.append(ch, start, length);
        }
    }
}
