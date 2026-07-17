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

package com.hardbacknutter.nevertoomanybooks.searchengines.bnf;

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
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * Bibliothèque nationale de France.
 * <p>
 * <a href="https://www.bnf.fr">"https://www.bnf.fr</a>
 */
public class BnfSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIssn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByBarcode,
                   SearchEngine.ByText {

    private static final String SITE_URL = "https://www.bnf.fr";
    private static final String BOOK_URL = "https://catalogue.bnf.fr/ark:/12148/cb%s";
    private static final String AUTHOR_URL = "https://catalogue.bnf.fr/ark:/12148/cb%s";

    // also used as the identifier value
    private static final String PREFERENCE_KEY = "bnf";
    private static final Locale SITE_LOCALE = Locale.FRANCE;

    /**
     * Param 1: SRU query.
     * <p>
     * Hardcoded to return a single result for now.
     */
    private static final String SEARCH_URL = "https://catalogue.bnf.fr/api/SRU?"
                                             + "version=1.2"
                                             + "&operation=searchRetrieve"
                                             + "&query=%1$s"
                                             + "&recordSchema=unimarcXchange"
                                             + "&maximumRecords=1";

    private static final String QUERY_ARK = "bib.persistentid all \"/ark:/12148/%1$s\"";
    private static final String QUERY_ISBN = "bib.isbn all \"%1$s\"";
    private static final String QUERY_ISSN = "bib.issn all \"%1$s\"";
    private static final String QUERY_EAN = "bib.ean all \"%1$s\"";
    private static final String QUERY_ANYWHERE = "bib.anywhere all \"%1$s\"";

    /**
     * Param 1: the id for the cover as read from the unimarc tag 856.
     * <p>
     * We can restrict the download size by adding:
     * + "&hauteur=400&largeur=400"
     * For now, get hires.
     */
    private static final String COVER_URL = "https://catalogue.bnf.fr/couverture?"
                                            + "appName=NE"
                                            + "&idImage=%1$s"
                                            + "&couverture=1";

    private final AuthorResolverHelper authorResolverHelper;

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
    public BnfSearchEngine(@NonNull final Context appContext,
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
                                    R.string.site_bnf_fr,
                                    List.of(R.string.site_description_french,
                                            R.string.site_description_catalog),
                                    "https://catalogue.bnf.fr",
                                    SITE_LOCALE)
                .setPreferenceFragmentClazz(BnfPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_BNF)
                .setIdentifierKey(Identifier.EntityType.Author, Identifier.SID_BNF)
                .setAuthorResolverSupplier(BnfAuthorResolver::create)
                .setConfig(cb -> cb
                        .build(SearchEngineConfig::new));
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
        final String name = context.getString(R.string.identifier_bnf);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_BNF,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P268"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_BNF,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P268")
        );
    }

    @NonNull
    private String createSearchUrl(@NonNull final String index,
                                   @NonNull final String query) {
        final String queryString = String.format(index, query);
        return String.format(SEARCH_URL,
                             URLEncoder.encode(queryString, StandardCharsets.UTF_8)
                                       .replace("+", "%20"));
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final BookSearchCriteria criteria)
            throws SearchException, CredentialsException, StorageException {

        final String externalId = criteria.requireSid(getEngineId());
        final String url = createSearchUrl(QUERY_ARK, externalId);
        return search(context, url, null, criteria.getFetchCovers());
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        return searchByProductCode(context, QUERY_ISBN, criteria);
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        return searchByProductCode(context, QUERY_ISSN, criteria);
    }

    @NonNull
    @Override
    public Book searchByBarcode(@NonNull final Context context,
                                @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        return searchByProductCode(context, QUERY_EAN, criteria);
    }

    @NonNull
    private Book searchByProductCode(@NonNull final Context context,
                                     @NonNull final String index,
                                     @NonNull final BookSearchCriteria criteria)
            throws SearchException, CredentialsException, StorageException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());

        final String url = createSearchUrl(index, codeStr);
        return search(context, url, codeStr, criteria.getFetchCovers());
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria)
            throws SearchException, CredentialsException, StorageException {
        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");

        String codeStr = null;
        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            codeStr = productCode.getFormatted(getEngineId());
            if (!codeStr.isBlank()) {
                words.add(codeStr);
            }
        }

        // Sanity check
        if (words.length() == 0) {
            return new Book();
        }

        final String url = createSearchUrl(QUERY_ANYWHERE, words.toString());
        return search(context, url, codeStr, criteria.getFetchCovers());
    }

    @NonNull
    private Book search(@NonNull final Context context,
                        @NonNull final String url,
                        @Nullable final String searchedCode,
                        @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, StorageException {

        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, searchedCode, fetchCovers, book);
        }
        return book;
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @Nullable final String searchedCode,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws CredentialsException, StorageException {

        final BnfParser parser = new BnfParser(this, document, book);

        parser.sid();
        parser.isbnFormatAndPrice(context);
        parser.languages();
        parser.title();
        parser.physicalDescription(context);
        parser.publicationDate();
        parser.publication();
        parser.description();
        parser.translation();
        parser.authors();
        parser.series();
        // This must be parsed AFTER doing title and series!
        // (a book will simply not find the tag)
        parser.magazines(context);

        final List<String> coverIds = parser.coverIds();

        parser.finish(searchedCode);

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        final int maxCovers = Math.min(DBKey.NR_OF_BOOK_COVERS, coverIds.size());
        final String codeStr = book.getRawProductCode();
        for (int c = 0; c < maxCovers; c++) {
            if (fetchCovers[c]) {
                final String url = String.format(COVER_URL, coverIds.get(c));
                final int finalC = c;
                saveImage(context, url, null, codeStr, c, null).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, finalC, fileSpec));
            }
        }
    }
}
