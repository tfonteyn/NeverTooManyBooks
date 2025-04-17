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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.content.Context;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <a href="https://www.librarything.com/developer/documentation/thingapis">
 * LibraryThing Lightweight APIs</a>
 * <p>
 * 2025-04-04: the alternative editions api is back but requires registration.
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.AlternativeEditions<AltEditionIsbn>,
                   SearchEngine.UserRegistration {

    public static final String SITE_URL = "https://www.librarything.com";
    public static final String BOOK_URL = "https://www.librarything.com/work/%s";
    public static final String AUTHOR_URL = null;

    @VisibleForTesting
    static final String PK_API_TOKEN = EngineId.LibraryThing.getPreferenceKey()
                                       + ".api.token";
    private static final int TOKEN_LEN = 32;

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

    @Override
    public boolean isRegistrationRequired() {
        return false;
    }

    @NonNull
    @Override
    public String getRegistrationInfo(@NonNull final Context context) {
        // registration is optional
        final String reg =
                "<a href=\"https://www.librarything.com/developer/tokens\">"
                + context.getString(getEngineId().getLabelResId())
                + "</a>";
        final String help =
                "<a href=\"" + context.getString(R.string.github_help_registration_url) + "\">"
                + context.getString(R.string.action_learn_more)
                + "</a>";

        return context.getString(R.string.info_registration_benefits, reg, help);
    }

    @NonNull
    @Override
    public Class<? extends Fragment> getPreferenceFragmentClass() {
        return LibraryThingPreferencesFragment.class;
    }

    @Override
    public boolean hasRegistrationData(@NonNull final Context context) {
        final String apiToken = PreferenceManager.getDefaultSharedPreferences(context)
                                                 .getString(PK_API_TOKEN, null);
        return apiToken != null && apiToken.length() == TOKEN_LEN;
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
    public List<AltEditionIsbn> searchAlternativeEditions(@NonNull final Context context,
                                                          @NonNull final String validIsbn)
            throws SearchException, CredentialsException {

        final String apiToken = PreferenceManager.getDefaultSharedPreferences(context)
                                                 .getString(PK_API_TOKEN, null);
        // not set, quit silently
        if (apiToken == null || apiToken.isEmpty()) {
            if (BuildConfig.DEBUG /*always */) {
                Log.d(TAG, "API TOKEN NOT SET");
            }
            return List.of();
        }

        // incorrect length, abort
        if (apiToken.length() != TOKEN_LEN) {
            throw new CredentialsException(
                    getEngineId().getLabelResId(),
                    context.getString(R.string.error_http_401_authorization_failed));
        }

        final String url = getHostUrl(context) + String.format("/api/%1$s/thingISBN/%2$s",
                                                               apiToken, validIsbn);
        final SAXParser parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }

        final LibraryThingEditionHandler handler = new LibraryThingEditionHandler();
        futureHttpGet = createGetDocumentRequest(context);
        try {
            futureHttpGet.get(url, (con, is) -> {
                parser.parse(is, handler);
                return true;
            });

        } catch (@NonNull final StorageException | IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            futureHttpGet = null;
        }

        return handler.getResult();
    }

    /**
     * Parser Handler to collect the edition data.
     * <p>
     * Typical request output:
     * <pre>
     * {@code
     *   <?xml version="1.0" encoding="utf-8"?>
     *   <idlist>
     *     <isbn>0380014300</isbn>
     *     <isbn>0839824270</isbn>
     *     <isbn>0722194390</isbn>
     *     <isbn>0783884257</isbn>
     *     ...etc...
     *     <isbn>2207301907</isbn>
     *   </idlist>
     * }
     * </pre>
     */
    private static final class LibraryThingEditionHandler
            extends DefaultHandler {

        /** isbn tag in an editions xml response. */
        private static final String XML_ISBN = "isbn";

        /** XML content. */
        @SuppressWarnings("StringBufferField")
        private final StringBuilder builder = new StringBuilder();
        /** All editions we find. */
        private final List<AltEditionIsbn> editions = new ArrayList<>();

        /**
         * Get the results.
         *
         * @return the list with editions.
         */
        @NonNull
        public List<AltEditionIsbn> getResult() {
            return editions;
        }

        @Override
        @CallSuper
        public void endElement(@NonNull final String uri,
                               @NonNull final String localName,
                               @NonNull final String qName) {

            if (localName.equalsIgnoreCase(XML_ISBN)) {
                editions.add(new AltEditionIsbn(builder.toString()));
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
