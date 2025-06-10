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

package com.hardbacknutter.nevertoomanybooks.searchengines.wikidata;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class WikidataSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn {

    public static final String SITE_URL = "https://www.wikidata.org";
    public static final String BOOK_URL = "https://www.wikidata.org/wiki/%s";
    public static final String AUTHOR_URL = "https://www.wikidata.org/wiki/%s";

    private static final String PREFERENCE_KEY = "wikidata";

    private static final Map<String, String> SIDS = Map.ofEntries(
            Map.entry("P5749", Identifier.SID_ASIN),
            Map.entry("P5199", Identifier.SID_BRITISH_LIBRARY),
            Map.entry("P10386", Identifier.SID_DATABAZE_KNIH),
            Map.entry("P1292", Identifier.SID_DNB),
            Map.entry("P6442", Identifier.SID_DOUBAN),
            Map.entry("P7439", Identifier.SID_FANTLAB),
            Map.entry("P8383", Identifier.SID_GOODREADS),
            Map.entry("P675", Identifier.SID_GOOGLE),
            Map.entry("P1234", Identifier.SID_ISFDB),
            Map.entry("P9088", Identifier.SID_KBR),
            Map.entry("P1144", Identifier.SID_LCCN),
            Map.entry("P1085", Identifier.SID_LIBRARY_THING),
            Map.entry("P2191", Identifier.SID_NILF),
            Map.entry("P5571", Identifier.SID_NOOSFERE),
            Map.entry("P10832", Identifier.SID_OCLC),
            Map.entry("P648", Identifier.SID_OPEN_LIBRARY),
            Map.entry("P6373", Identifier.SID_PORBASE)
    );

    private static final String SEARCH_BY_ISBN =
            "https://www.wikidata.org/wiki/Special:Search?search=ISBN+%1$s";
    @Nullable
    private FutureHttpGet<String> futureHttpGet;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public WikidataSearchEngine(@NonNull final Context appContext,
                                @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        // We MUST bootstrap it here to ensure it's active before the first http request send
        ServiceLocator.getInstance().getCookieManager();
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
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_wikidata,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    "https://www.wikidata.org",
                                    Locale.US)
                .setIdentifierKey(Identifier.SID_WIKIDATA);
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

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = String.format(SEARCH_BY_ISBN, validIsbn);
        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    @NonNull
    private String loadDocument(@NonNull final Context context,
                                @NonNull final String url)
            throws StorageException, SearchException {
        futureHttpGet = createGetDocumentRequest(context);
        try {
            return futureHttpGet.getAsString(url, (con, s) -> s);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            futureHttpGet = null;
        }
    }

    /**
     * Fetch and parse.
     *
     * @param context     Current context
     * @param url         to fetch
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    private void fetchBook(@NonNull final Context context,
                           @NonNull final String url,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        String response = loadDocument(context, url);

        try {
            final JSONObject root = new JSONObject(response);


        } catch (@NonNull final JSONException e) {
            throw new SearchException(getEngineId(), e);
        }

    }
}
