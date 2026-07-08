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
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
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
import org.jsoup.parser.Parser;

import okhttp3.Request;

/**
 * German language books & comics.
 * <p>
 * <a href="https://www.dnb.de">Deutsche Nationalbibliothek (DNB)</a>
 * <a href="https://www.dnb.de">Germany's National Library (DNB)</a>
 *
 * @see <a href="https://www.dnb.de/EN/Professionell/Metadatendienste/Datenbezug/SRU/sru_node.html#doc250692bodyText1">
 *         DNB sru searches</a>
 * @see <a href="https://services.dnb.de/sru/dnb?operation=explain&version=1.1">
 *     SRU explain record</a> */
public class DnbSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    /** Main site, but NOT the search site. */
    private static final String SITE_URL = "https://www.dnb.de";
    private static final String BOOK_URL = "https://d-nb.info/%s";
    private static final String AUTHOR_URL = "https://d-nb.info/gnd/%s";

    private static final String PREFERENCE_KEY = "dnb";

    /**
     * Param 1: SRU query.
     * <p>
     * Hardcoded to return a single result for now.
     */
    private static final String SRU_URL = "https://services.dnb.de/sru/dnb?"
                                          + "version=1.1"
                                          + "&operation=searchRetrieve"
                                          + "&query=%1$s"
                                          + "&recordSchema=MARC21-xml"
                                          + "&maximumRecords=1";

    private static final String SEARCH_TYPE_ISBN = "num";

    /** Concat with the isbn. */
    private static final String HIRES_IMAGE_SEARCH = "https://portal.dnb.de/opac/mvb/cover?isbn=";

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

        final String query = SEARCH_TYPE_ISBN + "=" + codeStr;
        final String url = String.format(SRU_URL, URLEncoder
                .encode(query, StandardCharsets.UTF_8)
                .replace("+", "%20"));

        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        final Book book = new Book();

        if (!isCancelled()) {
            parse(context, document, productCode, fetchCovers, book);
        }
        return book;
    }


    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, StorageException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner query = new StringJoiner(" and ");

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            final String codeStr = SearchEngineUtils.formatIsbn(getEngineId(), productCode);
            if (!codeStr.isEmpty()) {
                query.add(SEARCH_TYPE_ISBN + "=" + codeStr);
            }
        }

        addCriteriaIfValid(query, "tit", criteria.getTitle());
        addCriteriaIfValid(query, "per", criteria.getAuthor());
        addCriteriaIfValid(query, "gsr", criteria.getSeries());
        addCriteriaIfValid(query, "zsn", criteria.getSeriesNr());
        addCriteriaIfValid(query, "vlg", criteria.getPublisher());

        final Book book = new Book();

        // Sanity check
        if (query.length() == 0) {
            return book;
        }

        final String url = String.format(SRU_URL, URLEncoder
                .encode(query.toString(), StandardCharsets.UTF_8)
                .replace("+", "%20"));

        final Document document = loadDocument(context, Parser.xmlParser(), url, null);
        if (!isCancelled()) {
            parse(context, document, productCode, fetchCovers, book);
        }
        return book;
    }

    private void addCriteriaIfValid(@NonNull final StringJoiner query,
                                    @NonNull final String index,
                                    @Nullable final String value) {
        if (value != null && !value.isBlank()) {
            // Sanity cleaning
            final String cleaned = value.replace("\n", " ")
                                        .replace("\r", " ")
                                        .strip();
            final String escaped = cleaned.replace("\"", "\\\"");

            query.add(index + "=\"" + escaped + "\"");
        }
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final Document document,
               @Nullable final ProductCode productCode,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws CredentialsException, CoverStorageException {

        final DnbBookParser parser = new DnbBookParser(context, document, book);
        parser.sidDnb();

        // Specific for books
        parser.isbn();

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

        parser.finish(productCode);

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String url = HIRES_IMAGE_SEARCH + book.getRawProductCode();
            // No referer
            saveImage(context, url, null, book.getRawProductCode(), 0, null)
                    .ifPresent(s -> CoverFileSpecArray.setFileSpec(book, 0, s));
        }
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
