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
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.RequestFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;

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
                   SearchEngine.ByText,
                   SearchEngine.ByExternalId {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    private static final String HOST_URL = "https://services.dnb.de";
    /** {@link EngineId#getDefaultLocale()}. */
    private static final Locale HOST_LOCALE = Locale.GERMANY;
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "dnb";

    private static final String SRU_DNB = "dnb";
    private static final String SRU_ZDB = "zdb";

    /**
     * Param 1: SRU name space.
     * Param 2: SRU query.
     * <p>
     * Hardcoded to return a single result for now.
     */
    private static final String SEARCH_URL = HOST_URL + "/sru/%1$s?"
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

    /**
     * SRU: dnb.
     * Index: Normdatenidentifier.
     */
    private static final String SEARCH_INDEX_NID = "nid";

    /** Concat with the isbn. */
    private static final String COVER_URL = "https://portal.dnb.de/opac/mvb/cover?isbn=";

    private final AuthorResolverHelper authorResolverHelper;

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
    public DnbSearchEngine(@NonNull final Context context,
                           @NonNull final SearchEngineConfig config) {
        super(context, config);
        setImageRequestFactory(DnbImageRequestFactory.INSTANCE);

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
        return new EngineId.Builder(HOST_PREF_KEY,
                                    R.string.site_dnb_de,
                                    List.of(R.string.site_description_german,
                                            R.string.site_description_catalog),
                                    HOST_URL,
                                    HOST_LOCALE)
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
        final String site = "https://www.dnb.de";
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_DNB,
                               name, site,
                               "https://d-nb.info/%s",
                               "P1292"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_DNB,
                               name, site,
                               "https://d-nb.info/gnd/%s",
                               "P7902")
        );
    }


    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final String externalId = criteria.requireSid(getEngineId());
        final String url = createSearchUrl(SRU_DNB, SEARCH_INDEX_IDN + "=" + externalId);
        final Document document = loadXml(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, null, criteria.getFetchCovers(), book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws SearchException, CredentialsException, StorageException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());
        final String url = createSearchUrl(SRU_DNB, SEARCH_INDEX_NUM + "=" + codeStr);
        final Document document = loadXml(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, codeStr, criteria.getFetchCovers(), book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getDashFormattedIssn8(getEngineId());
        final String url = createSearchUrl(SRU_ZDB, SEARCH_INDEX_ISS + "=" + codeStr);
        final Document document = loadXml(context, url, null);

        final Book book = new Book();

        if (!isCancelled()) {
            parseFromIssn(context, document, codeStr, book);
        }

        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria)
            throws SearchException, CredentialsException, StorageException {

        @NonNull
        String sru = SRU_DNB;
        @Nullable
        String codeStr = null;

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner query = new StringJoiner(" and ");

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            codeStr = productCode.getFormatted(getEngineId());
            if (!codeStr.isBlank()) {
                query.add(SEARCH_INDEX_NUM + "=" + codeStr);
                if (productCode.getType() == ProductCodeType.Issn8
                    || productCode.getType() == ProductCodeType.Issn13) {
                    sru = SRU_ZDB;
                }
            } else {
                codeStr = null;
            }
        }

        addCriteria(query, SEARCH_INDEX_TIT, criteria.getTitle());
        addCriteria(query, SEARCH_INDEX_PER, criteria.getAuthor());
        addCriteria(query, SEARCH_INDEX_VLG, criteria.getPublisher());
        // criteria.getSeries() + criteria.getSeriesNr() not searchable

        final Book book = new Book();

        // Sanity check
        if (query.length() == 0) {
            return book;
        }

        final String url = createSearchUrl(sru, query.toString());
        final Document document = loadXml(context, url, null);

        if (!isCancelled()) {
            parse(context, document, codeStr, criteria.getFetchCovers(), book);
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
               @Nullable final String searchedCode,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws CredentialsException, StorageException {

        final DnbBookParser parser = new DnbBookParser(context, document, book);
        // Parse the sid FIRST
        parser.sid();
        // Parse the product-code next
        parser.isbn();
        parseCommonTags(parser);
        // all done
        parser.finish(searchedCode);

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
                       @NonNull final String searchedCode,
                       @NonNull final Book book) {

        final DnbBookParser parser = new DnbBookParser(context, document, book);
        // Parse the sid FIRST
        parser.sid();
        // Parse the product-code next
        parser.issn();
        parseCommonTags(parser);

        // Check for, and parse Periodical data.
        // This must be parsed AFTER parsing title and series as above.
        parser.periodicals();

        // all done
        parser.finish(searchedCode);
    }

    /**
     * The tags we always parse for any of the search methods.
     *
     * @param parser to use
     */
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
        // eBook flag must be parsed AFTER physicalDescription
        parser.ebook();
        parser.genreTags();
    }

    /**
     * Bypass the <a href="https://anubis.techaro.lol/docs/design/how-anubis-works/">Anubis</a>
     * filter by pretending to be {@code wget}.
     */
    private static final class DnbImageRequestFactory
            implements RequestFactory {

        private static final RequestFactory INSTANCE = new DnbImageRequestFactory();

        @NonNull
        @Override
        public Request createRequest(@NonNull final String urlStr,
                                     @Nullable final Map<String, String> /* ignored */ headers) {
            // Host, Connection, Accept-Encoding are added by OkHttp
            return new Request.Builder()
                    .url(urlStr)
                    .header(HttpConstants.USER_AGENT, "Wget/1.25.0")
                    .header(HttpConstants.ACCEPT, "*/*")
                    .build();
        }
    }
}
