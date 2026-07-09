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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import okhttp3.Request;

/**
 * German language books & comics + multi-language magazines and newspapers.
 * <p>
 * <a href="https://www.dnb.de">Deutsche Nationalbibliothek (DNB)</a>
 * <a href="https://www.dnb.de">Germany's National Library (DNB)</a>
 * <a href="https://zdb-katalog.de">Deutsche Nationalbibliothek (ZDB)</a>
 * <a href="https://zdb-katalog.de">Germany's National Library (ZDB)</a>
 *
 * @see <a href="https://www.dnb.de/EN/Professionell/Metadatendienste/Datenbezug/SRU/sru_node.html#doc250692bodyText1">
 *         DNB sru searches</a>
 * @see <a href="https://services.dnb.de/sru/dnb?operation=explain&version=1.1">
 *         DNB explain record</a>
 * @see <a href="https://services.dnb.de/sru/zdb?operation=explain&version=1.1">
 *         ZDB explain record</a>
 */
public class DnbSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByIssn,
                   SearchEngine.ByText {

    /** Main site, but NOT the search site. */
    private static final String SITE_URL = "https://www.dnb.de";
    private static final String BOOK_URL = "https://d-nb.info/%s";
    private static final String AUTHOR_URL = "https://d-nb.info/gnd/%s";

    private static final String PREFERENCE_KEY = "dnb";

    private static final String SRU_DNB = "dnb";
    private static final String SRU_ZDB = "zdb";

    /**
     * Param 1: SRU name space.
     * Param 2: SRU query.
     * <p>
     * Hardcoded to return a single result for now.
     */
    private static final String SEARCH_URL = "https://services.dnb.de/sru/%1$s?"
                                             + "version=1.1"
                                             + "&operation=searchRetrieve"
                                             + "&query=%2$s"
                                             + "&recordSchema=MARC21-xml"
                                             + "&maximumRecords=1";

    /**
     * SRU: dnb, zdb.
     * Index: Interner DNB-Identifier.
     * <p>
     * The {@link Identifier#SID_DNB} value.
     */
    private static final String SEARCH_INDEX_IDN = "idn";
    /**
     * SRU: dnb, zdb.
     * Index: Nummer.
     * <p>
     * Generic search for a number, this includes ISBN and maybe others.
     * NOT used when searching for an ISSN.
     *
     * @see #SEARCH_INDEX_ISS
     */
    private static final String SEARCH_INDEX_NUM = "num";
    /**
     * SRU: zdb.
     * Index: ISSN [ZDB].
     * <p>
     * Specifically search for ISSN numbers in the "zdb" SRU
     */
    private static final String SEARCH_INDEX_ISS = "iss";
    /**
     * SRU: dnb, zdb.
     * Index: Titel.
     * <p>
     * Book or magazine title.
     */
    private static final String SEARCH_INDEX_TIT = "tit";
    /**
     * SRU: dnb, zdb.
     * Index: Person.
     * <p>
     * The author name.
     */
    private static final String SEARCH_INDEX_PER = "per";
    /**
     * SRU: dnb.
     * Index: Verleger/Firma, Ort
     * <p>
     * The Publisher name.
     */
    private static final String SEARCH_INDEX_VLG = "vlg";


    private static final String SEARCH_INDEX_GSR = "gsr";
    private static final String SEARCH_INDEX_ZSN = "zsn";

    /**
     * SRU: dnb.
     * Index: Normdatenidentifier.
     */
    private static final String SEARCH_INDEX_NID = "nid";

    private static final int CF_008_FREQUENCY = 18;
    private static final int CF_008_TYPE_OF_CONTINUING_RESOURCE = 21;

    /** Concat with the isbn. */
    private static final String COVER_URL = "https://portal.dnb.de/opac/mvb/cover?isbn=";

    private final AuthorResolverHelper authorResolverHelper;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     *
     * @throws IllegalStateException when we could not create the SslContext.
     */
    @Keep
    public DnbSearchEngine(@NonNull final Context appContext,
                           @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
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
                                    R.string.site_dnb_de,
                                    List.of(R.string.site_description_german,
                                            R.string.site_description_catalog),
                                    SITE_URL,
                                    Locale.GERMANY)
                .setPreferenceFragmentClazz(DnbPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_DNB)
                .setIdentifierKey(Identifier.EntityType.Author, Identifier.SID_DNB)
                .setAuthorResolverSupplier(DnbAuthorResolver::create);
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
        final String name = context.getString(R.string.identifier_dnb);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_DNB,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P1292"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_DNB,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P7902")
        );
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, StorageException {

        final String codeStr = SearchEngineUtils.formatIsbn(getEngineId(), productCode);
        final String url = createSearchUrl(SRU_DNB, SEARCH_INDEX_NUM + "=" + codeStr);
        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        final Book book = new Book();

        if (!isCancelled()) {
            parse(context, document, productCode, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException {

        final String codeStr = SearchEngineUtils.formatIssn8(context, getEngineId(), productCode);
        final String url = createSearchUrl(SRU_ZDB, SEARCH_INDEX_ISS + "=" + codeStr);
        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        final Book book = new Book();

        if (!isCancelled()) {
            parseFromIssn(context, document, productCode, book);
        }

        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, StorageException {

        String sru = SRU_DNB;

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner query = new StringJoiner(" and ");

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            final String codeStr = SearchEngineUtils.formatIsbn(getEngineId(), productCode);
            if (!codeStr.isEmpty()) {
                query.add(SEARCH_INDEX_NUM + "=" + codeStr);
                if (productCode.getType() == ProductCodeType.Issn8
                    || productCode.getType() == ProductCodeType.Issn13) {
                    sru = SRU_ZDB;
                }
            }
        }

        addCriteria(query, SEARCH_INDEX_TIT, criteria.getTitle());
        addCriteria(query, SEARCH_INDEX_PER, criteria.getAuthor());
        addCriteria(query, SEARCH_INDEX_GSR, criteria.getSeries());
        addCriteria(query, SEARCH_INDEX_ZSN, criteria.getSeriesNr());
        addCriteria(query, SEARCH_INDEX_VLG, criteria.getPublisher());

        final Book book = new Book();

        // Sanity check
        if (query.length() == 0) {
            return book;
        }

        final String url = createSearchUrl(sru, query.toString());
        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        if (!isCancelled()) {
            parse(context, document, productCode, fetchCovers, book);
        }
        return book;
    }

    private void addCriteria(@NonNull final StringJoiner query,
                             @NonNull final String index,
                             @Nullable final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        // Sanity cleaning
        final String cleaned = value.replace("\n", " ")
                                    .replace("\r", " ")
                                    .strip();
        final String escaped = cleaned.replace("\"", "\\\"");

        query.add(index + "=\"" + escaped + "\"");
    }

    @NonNull
    private String createSearchUrl(@NonNull final String sru,
                                   @NonNull final String query) {
        return String.format(SEARCH_URL, sru,
                             URLEncoder.encode(query, StandardCharsets.UTF_8)
                                       .replace("+", "%20"));
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final Document document,
               @Nullable final ProductCode productCode,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws CredentialsException, StorageException {

        final DnbBookParser parser = new DnbBookParser(context, document, book);
        parser.sidDnb();

        // Specific for books
        parser.isbn();

        parseCommonTags(parser);

        parser.finish(productCode);

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String url = COVER_URL + book.getRawProductCode();
            // No referer
            saveImage(context, url, null, book.getRawProductCode(), 0, null)
                    .ifPresent(s -> CoverFileSpecArray.setFileSpec(book, 0, s));
        }
    }

    @VisibleForTesting
    void parseFromIssn(@NonNull final Context context,
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
                parseCF008(context, text, book);
            }
        }
        parser.issn();

        parseCommonTags(parser);

        // We don't get an author for magazines, use the publisher if we have one...
        book.getPrimaryPublisher()
            .ifPresent(p -> book.add(Author.from(p.getName())));

        parser.finish(productCode);
    }

    private void parseCF008(@NonNull final Context context,
                            @NonNull final String text,
                            @NonNull final Book book) {
        switch (text.charAt(CF_008_TYPE_OF_CONTINUING_RESOURCE)) {
            // n - Newspaper
            case 'n': {
                book.setFormat(context.getString(R.string.book_format_newspaper));
                break;
            }
            case 'j': {
                book.setFormat(context.getString(R.string.book_format_journal));
                break;
            }
            // g - Magazine
            // i - Serial zine
            // p - Periodical
            case 'g':
            case 'i':
            case 'p': {
                book.setFormat(context.getString(R.string.book_format_periodical));
                break;
            }
        }
    }

    private void parseCommonTags(@NonNull final DnbBookParser parser) {
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
    }

    /**
     * Bypass the <a href="https://anubis.techaro.lol/docs/design/how-anubis-works/">Anubis</a>
     * filter by pretending to be {@code wget}.
     *
     * @param context           Current context
     * @param urlStr            to use
     * @param requestProperties (optional) extra headers to add/override
     *
     * @return request
     */
    @NonNull
    @Override
    protected Request createImageRequest(@NonNull final Context context,
                                         @NonNull final String urlStr,
                                         @Nullable final Map<String, String> requestProperties) {
        return new Request.Builder()
                .url(urlStr)
                .header(HttpConstants.USER_AGENT, "Wget/1.25.0")
                .header(HttpConstants.ACCEPT, "*/*")
                .header(HttpConstants.CONNECTION, HttpConstants.CONNECTION_KEEP_ALIVE)
                .build();
    }
}
