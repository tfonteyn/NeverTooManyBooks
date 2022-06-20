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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedSAXException;

/**
 * 2020-03-27. Started getting "APIs Temporarily disabled" for book and cover searches.
 * <a href="https://www.librarything.com/services/">LibraryThing API</a>
 * <p>
 * 2020-05-05: removed all non-functional code.
 * We can still:
 * - Search for alternative editions.
 * - View books on the site for which we previously stored a native id.
 * Keep in mind that the LT id can also be gathered from other sites (e.g. OpenLibrary)
 * <p>
 * LibraryThing is 40% owned by AbeBooks which is owned by Amazon and the API is already shut down.
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ViewBookByExternalId,
                   SearchEngine.AlternativeEditions {

    /** Preferences prefix. */
    private static final String PREF_KEY = "librarything";
    /** Type: {@code String}. */
    public static final String PK_HOST_URL = PREF_KEY + Prefs.pk_suffix_host_url;
    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public LibraryThingSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(LibraryThingSearchEngine.class,
                                              SearchSites.LIBRARY_THING,
                                              R.string.site_library_thing,
                                              PREF_KEY,
                                              "https://www.librarything.com")
                .setSupportsMultipleCoverSizes(true)
                .setFilenameSuffix("LT")

                .setDomainKey(DBKey.SID_LIBRARY_THING)
                .setDomainViewId(R.id.site_library_thing)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING)

                .build();
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.
        return getLocale(context, getSiteUrl());
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format("/work/%1$s", externalId);
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
     *
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

        futureHttpGet = createFutureGetRequest();

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final LibraryThingEditionHandler handler = new LibraryThingEditionHandler();

        final String url = getSiteUrl() + String.format("/api/thingISBN/%1$s", validIsbn);

        try {
            final SAXParser parser = factory.newSAXParser();
            futureHttpGet.get(url, request -> {
                try (BufferedInputStream bis = new BufferedInputStream(
                        request.getInputStream())) {
                    parser.parse(bis, handler);
                    return true;

                } catch (@NonNull final IOException e) {
                    throw new UncheckedIOException(e);
                } catch (@NonNull final SAXException e) {
                    throw new UncheckedSAXException(e);
                }
            });

        } catch (@NonNull final StorageException | ParserConfigurationException
                | SAXException | IOException e) {
            throw new SearchException(getName(context), e);
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
