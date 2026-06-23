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

package com.hardbacknutter.nevertoomanybooks.searchengines.zdbkatalog;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ZdbKatalogSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIssn {

    private static final String TAG = "ZdbKatalogSearchEngine";

    private static final String SITE_URL = "https://zdb-katalog.de";
    private static final String PREFERENCE_KEY = "zdbkatalog";
    /** Website character encoding. */
    private static final String CHARSET = "UTF-8";

    private static final String ZDB_USER_AGENT =
            "NTMB/8.0 (Android; Gentle-Scraper; throttled to max 1 req/second)";

    // 2026-06-21
    private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
            Map.entry("OCLC number", Identifier.SID_OCLC),
            Map.entry("OCLC-Nr.", Identifier.SID_OCLC)
    );

    @Nullable
    private FutureHttp<Document> httpPost;

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
    public ZdbKatalogSearchEngine(@NonNull final Context appContext,
                                  @NonNull final SearchEngineConfig config) {
        super(appContext, config);
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
                                    R.string.site_zdbkatalog,
                                    List.of(R.string.site_description_german,
                                            R.string.site_description_catalog),
                                    SITE_URL,
                                    Locale.GERMANY)
                // It shared the DNB identifier!
                .setIdentifierKeys(Identifier.SID_DNB);
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String codeStr = SearchEngineUtils.formatIssn8(context, getEngineId(), productCode);

        final String viewState = getFacesViewState(context);
        if (viewState == null || viewState.isEmpty()) {
            throw new SearchException(getEngineId(),
                                      "No viewState",
                                      context.getString(R.string.error_unexpected));
        }

        final String postBody = new StringJoiner("&")
                .add("mainForm=mainForm")
                .add("mainForm:searchTermFilter:searchTerm="
                     + URLEncoder.encode(codeStr, StandardCharsets.UTF_8))
                .add("mainForm:searchTermFilter:searchKey:select=iss")
                .add("mainForm:searchTermFilter:j_idt68:searchBtn=Search")
                .add("mainForm:yearFrom=1500")
                .add("mainForm:yearTo=2026")
                .add("javax.faces.ViewState=" + viewState)
                .toString();

        final Book book = new Book();

        try {
            httpPost = HttpCallFactory.create(getEngineId());
            final Document document = httpPost
                    .setRequestProperty(HttpConstants.USER_AGENT,
                                        ZDB_USER_AGENT)
                    .setRequestProperty(HttpConstants.ACCEPT_ENCODING,
                                        HttpConstants.ACCEPT_ENCODING_GZIP)
                    .setRequestProperty(HttpConstants.CONNECTION,
                                        HttpConstants.CONNECTION_KEEP_ALIVE)
                    .post(SITE_URL + "/xindex.html", postBody,
                          is -> Jsoup.parse(is, CHARSET, SITE_URL)
                    );
            if (document != null) {
                parseIssn(context, document, productCode, book);
            }

        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }


        return book;
    }

    @VisibleForTesting
    void parseIssn(@NonNull final Context context,
                   @NonNull final Document document,
                   @NonNull final ProductCode productCode,
                   @NonNull final Book book) {

        final Element panel = document.selectFirst("div#titleDetailsPanel");
        if (panel == null) {
            return;
        }
        final List<Identifier.Value> ivs = new ArrayList<>();

        final Elements rows = panel.select("div.row");
        for (final Element row :rows) {
            final Element label = row.selectFirst("div.td-key");
            final Element value = row.selectFirst("div.td-val");
            if (label != null && value != null) {
                final String labelText = label.text();
                // The site exists in English and German, check for both labels.
                switch (labelText) {
                    // Note we ignore the ZDB-ID in favour of the IDN; the latter is used in links.
                    case "IDN": {
                        ivs.add(new Identifier.Value(Identifier.SID_DNB, value.text()));
                        break;
                    }
                    case "Title":
                    case "Titel": {
                        book.setTitle(value.text());
                        break;
                    }
                    case "Published":
                    case "Erschienen": {
                        book.add(Publisher.from(value.text()));
                        break;
                    }
                    case "Standard numbers":
                    case "Standardnummern": {
                        value.select("p")
                             .stream()
                             .map(Element::text)
                             .map(entry -> entry.split(":"))
                             // paranoia
                             .filter(id -> id.length == 2)
                                .forEach(id -> {
                                    final String idv = id[1].strip();

                                    if ("Authorised ISSN".equals(id[0])
                                        || "Autorisierte ISSN".equals(id[0])) {
                                        book.setIsbn(ISBN.cleanText(idv));
                                    } else if ("ISSN".equals(id[0]) && !book.hasIsbn()) {
                                        book.setIsbn(ISBN.cleanText(idv));
                                    } else {
                                        String key = IDENTIFIER_MAPPING.get(id[0]);
                                        if (key == null) {
                                            key = id[0];
                                        }
                                        ivs.add(new Identifier.Value(key, idv));
                                    }
                                });

                        break;
                    }
                    case "Manifestation":
                    case "Erscheinungsform": {
                        book.setFormat(value.text());
                        break;
                    }
                    case "Language":
                    case "Sprache": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            book.setLanguage(a.text());
                        }
                    }
                }
            }
        }

        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }
        // unlikely, but if we did not one from the site, use the one we searched for.
        if (!book.hasIsbn()) {
            book.setIsbn(productCode.asText());
        }
    }

    @Nullable
    private String getFacesViewState(@NonNull final Context context)
            throws SearchException, CredentialsException {

        final Document document = loadDocument(context, SITE_URL,
                                               Map.of(HttpConstants.USER_AGENT, ZDB_USER_AGENT));
        final Element vse = document.selectFirst("input[name=javax.faces.ViewState]");
        if (vse == null) {
            return null;
        }
        return vse.attr("value");
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (httpPost != null) {
                httpPost.cancel();
            }
        }
    }
}
