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

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbBookParser;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbParser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * German language magazines and newspapers.
 * <p>
 * <a href="https://zdb-katalog.de">Deutsche Nationalbibliothek (ZDB)</a>
 * <a href="https://zdb-katalog.de">Germany's National Library (ZDB)</a>
 *
 * @see <a href="https://www.dnb.de/EN/Professionell/Metadatendienste/Datenbezug/SRU/sru_node.html#doc250692bodyText1">
 *         DNB sru searches</a>
 * @see <a href="https://services.dnb.de/sru/zdb?operation=explain&version=1.1">
 *     SRU explain record</a>
 */
public class ZdbKatalogSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIssn {

    private static final String SITE_URL = "https://zdb-katalog.de";
    private static final String PREFERENCE_KEY = "zdbkatalog";

    /**
     * Param 1: SRU query
     * <p>
     * Hardcoded to return a single result for now.
     */
    private static final String SRU_URL = "https://services.dnb.de/sru/zdb?"
                                          + "version=1.1"
                                          + "&operation=searchRetrieve"
                                          + "&query=%1$s"
                                          + "&recordSchema=MARC21-xml"
                                          + "&maximumRecords=1";

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
        // We MUST bootstrap it here to ensure it's active before the first http request send
        // No further interaction with it is needed.
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
        // Shares the DNB identifier!
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_zdbkatalog,
                                    List.of(R.string.site_description_german,
                                            R.string.site_description_catalog),
                                    SITE_URL,
                                    Locale.GERMANY)
                .setPreferenceFragmentClazz(ZdbKatalogPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_DNB)
                .setIdentifierKey(Identifier.EntityType.Author, Identifier.SID_DNB);
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException {

        final String codeStr = SearchEngineUtils.formatIssn8(context, getEngineId(), productCode);

        final String query = "iss=" + codeStr;
        final String url = String.format(SRU_URL, URLEncoder
                .encode(query, StandardCharsets.UTF_8)
                .replace("+", "%20"));

        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        final Book book = new Book();

        if (!isCancelled()) {
            parse(context, document, productCode, book);
        }

        return book;
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final Document document,
               @NonNull final ProductCode productCode,
               @NonNull final Book book) {

        final DnbBookParser parser = new DnbBookParser(context, document, book);
        parser.sidDnb();

        // Specific for magazines
        // Type of continuing resource
        //    # - None of the following
        //    a - Activity report
        //    d - Updating database
        //    g - Magazine
        //    h - Blog
        //    i - Serial zine
        //    j - Journal
        //    l - Updating loose-leaf
        //    m - Monographic series
        //    n - Newspaper
        //    p - Periodical
        //    q - Serial podcast
        //    r - Repository
        //    s - Newsletter
        //    t - Directory
        //    w - Updating Web site
        //    | - No attempt to code
        // position 35-37 provides the language, but so does tag 041; we're ONLY using the latter
        final Element cf008 = document.selectFirst("controlfield[tag='008']");
        if (cf008 != null) {
            final String text = DnbParser.normalise(cf008);
            // sanity check
            if (text.length() == 40) {
                switch (text.charAt(21)) {
                    // n - Newspaper
                    case 'n': {
                        book.setFormat(context.getString(R.string.book_format_newspaper));
                        break;
                    }
                    // g - Magazine
                    // j - Journal
                    // p - Periodical
                    case 'g':
                    case 'j':
                    case 'p': {
                        book.setFormat(context.getString(R.string.book_format_periodical));
                        break;
                    }
                }
            }
        }
        parser.issn();

        parser.identifiers();
        parser.languages();

        parser.authors();
        parser.publishers();
        parser.series();

        parser.originalTitle();
        parser.title();
        parser.description();
        parser.physicalDescription();
        parser.genreTags();

        // We don't get an author for magazines, use the publisher if we have one...
        book.getPrimaryPublisher()
            .ifPresent(p -> book.add(Author.from(p.getName())));

        parser.finish(productCode);
    }
}
