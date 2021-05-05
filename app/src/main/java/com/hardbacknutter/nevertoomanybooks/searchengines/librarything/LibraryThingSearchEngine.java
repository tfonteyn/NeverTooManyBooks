/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.network.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;

/**
 * 2020-03-27. Started getting "APIs Temporarily disabled" for book and cover searches.
 * Confirmed in LT forums; The entire API is currently disabled because of work on LT2.
 * <p>
 * Goodreads is owned by Amazon and is shutting their API down.
 * LibraryThing is 40% owned by AbeBooks which is owned by Amazon and the API is already shut down.
 * 2020-05-05: removed all non-functional code.
 * We can still:
 * - Search for alternative editions.
 * - View books on the site for which we previously stored a native id.
 * Keep in mind that the LT id can also be gathered from other sites (e.g. OpenLibrary)
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ViewBookByExternalId,
                   SearchEngine.AlternativeEditions {

    /** Preferences prefix. */
    private static final String PREF_KEY = "librarything";

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
    public String createBrowserUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format("/work/%1$s", externalId);
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
    public List<String> searchAlternativeEditions(@NonNull final String validIsbn)
            throws SearchException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final LibraryThingEditionHandler handler = new LibraryThingEditionHandler();

        final String url = getSiteUrl() + String.format("/api/thingISBN/%1$s", validIsbn);
        try (TerminatorConnection con = createConnection(url)) {
            final SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);

        } catch (@NonNull final ParserConfigurationException | SAXException | IOException e) {
            throw new SearchException(getName(), e);
        }
        return handler.getResult();
    }
}
