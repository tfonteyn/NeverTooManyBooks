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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.content.Context;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <a href="https://www.librarything.com/developer/documentation/thingapis">
 * LibraryThing Lightweight APIs</a>.
 * <p>
 * 2025-04-04: the alternative editions api is back but requires registration.
 */
public class LibraryThingSearchEngine
        extends SearchEngineBase
        implements SearchEngine.AlternativeEditions<AltEditionProductCode>,
                   SearchEngine.UserRegistration {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    private static final String HOST_URL = "https://www.librarything.com";
    /** {@link EngineId#getDefaultLocale()}. */
    private static final Locale HOST_LOCALE = Locale.US;
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "librarything";

    private static final String TAG = "LibraryThingSearchEngin";

    /** Exact length of the API token. */
    static final int TOKEN_LEN = 32;
    /** Preference key, stores the token. */
    static final String PK_API_TOKEN = HOST_PREF_KEY + ".api.token";

    /**
     * Search for alternative editions.
     * Param 1: the API token
     * Param 2: isbn
     */
    private static final String ALT_EDITIONS_URL = HOST_URL + "/api/%1$s/thingISBN/%2$s";

    @Nullable
    private FutureHttp<Boolean> httpGet;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context. NOT stored.
     * @param config  the search engine configuration
     *
     * @see EngineId#createSearchEngine(Context)
     */
    @Keep
    public LibraryThingSearchEngine(@NonNull final Context context,
                                    @NonNull final SearchEngineConfig config) {
        super(context, config);
    }

    /**
     * Called during startup to initialise the immutable/default engine configuration.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @return {@link EngineId.Builder}
     */
    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder(HOST_PREF_KEY,
                                    R.string.site_library_thing,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    HOST_URL,
                                    HOST_LOCALE)
                .setPreferenceFragmentClazz(LibraryThingPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_LIBRARY_THING);
    }

    /**
     * Called at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context
     *
     * @return list
     */
    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_library_thing);
        final String site = "https://www.librarything.com";
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_LIBRARY_THING,
                               name, site,
                               "https://www.librarything.com/work/%s",
                               "P1085"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_LIBRARY_THING,
                               name, site,
                               "https://www.librarything.com/a/%s",
                               "P7400"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Number,
                               Identifier.SID_LIBRARY_THING,
                               name, site,
                               "https://www.librarything.com/nseries/%s",
                               "P8513")
        );
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
        final String apiToken = ServiceLocator.getInstance().getSharedPreferences()
                                              .getString(PK_API_TOKEN, null);
        return apiToken != null && apiToken.length() == TOKEN_LEN;
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (httpGet != null) {
                httpGet.cancel();
            }
        }
    }

    /**
     * Search for edition data.
     *
     * @param context     Current context
     * @param productCode to search for, <strong>must</strong> be a valid ISBN.
     */
    @WorkerThread
    @NonNull
    @Override
    public List<AltEditionProductCode> searchAlternativeEditions(@NonNull final Context context,
                                                                 @NonNull final ProductCode productCode)
            throws SearchException, CredentialsException {

        if (!productCode.isIsbn()) {
            return List.of();
        }

        final String codeStr = productCode.getFormatted(getEngineId());

        final String apiToken = ServiceLocator.getInstance().getSharedPreferences()
                                              .getString(PK_API_TOKEN, null);
        // not set, quit silently
        if (apiToken == null || apiToken.isEmpty()) {
            if (BuildConfig.DEBUG /*always */) {
                Log.d(TAG, "LibraryThing API TOKEN NOT SET");
            }
            return List.of();
        }

        // incorrect length, abort
        if (apiToken.length() != TOKEN_LEN) {
            throw new CredentialsException(
                    getEngineId().getLabelResId(),
                    context.getString(R.string.error_http_401_authorization_failed));
        }

        final String url = String.format(ALT_EDITIONS_URL, apiToken, codeStr);
        final SAXParser parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }

        final LibraryThingEditionHandler handler = new LibraryThingEditionHandler();
        httpGet = createGetDocumentRequest(context);
        try {
            httpGet.get(url, (con, is) -> {
                parser.parse(is, handler);
                return true;
            });

        } catch (@NonNull final StorageException | IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            httpGet = null;
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

        /** isbn tag in an editions XML response. */
        private static final String XML_ISBN = "isbn";

        /** XML content. */
        @SuppressWarnings("StringBufferField")
        private final StringBuilder builder = new StringBuilder();
        /** All editions we find. */
        private final List<AltEditionProductCode> editions = new ArrayList<>();

        /**
         * Get the results.
         *
         * @return the list with editions.
         */
        @NonNull
        List<AltEditionProductCode> getResult() {
            return editions;
        }

        @Override
        @CallSuper
        public void endElement(@NonNull final String uri,
                               @NonNull final String localName,
                               @NonNull final String qName) {

            if (localName.equalsIgnoreCase(XML_ISBN)) {
                editions.add(new AltEditionProductCode(ISBN.parse(builder.toString())));
            }

            // Always reset the length. This is not entirely the right thing to do, but works
            // because we always want strings from the lowest level (leaf) XML elements.
            // To be completely correct, we should maintain a stack of builders that are pushed and
            // popped as each startElement/endElement is called. But let's not be pedantic for now.
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
