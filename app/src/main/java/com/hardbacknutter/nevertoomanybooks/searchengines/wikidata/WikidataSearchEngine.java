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

package com.hardbacknutter.nevertoomanybooks.searchengines.wikidata;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * The structured database of Wikipedia.
 *
 * @see <a href="https://www.wikidata.org/wiki/Wikidata:Database_reports/List_of_properties/all">
 *         All Wikidata claim number - WARNING: LONG LIST</a>
 *
 * @see <a href="https://query.wikidata.org/">Try out SPARQL queries</a>
 */
public class WikidataSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIssn {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    private static final String HOST_URL = "https://www.wikidata.org";
    /** {@link EngineId#getDefaultLocale()}. */
    private static final Locale HOST_LOCALE = Locale.US;
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "wikidata";

    /**
     * Search for an ISSN and fetch standard metadata along
     * with the single highest-ranked value per external identifier property.
     * <p>
     * Param 1: the ISSN formatted as "XXXX-XXXX" for the main lookup
     * Param 2: the language code list for the automatic label service
     */
    private static final String ISSN_SPARQL =
            " SELECT "
            + " ?item ?itemLabel"
            + " ?title"
            + " ?newsPaperFormat ?newsPaperFormatLabel"
            + " ?publisher ?publisherLabel"
            + " ?pubAmount ?pubUnitQ"
            + " ?langCode"
            // CURRENT editor-in-chief + other editors
            + "(GROUP_CONCAT(DISTINCT ?editorLabel; separator=\", \") AS ?currentEditors)"
            // Concat the identifiers as a single string
            + " (GROUP_CONCAT(CONCAT(STRAFTER(STR(?propVar), \"/direct/\"),"
            + " \":\", ?idValue); separator=\"|\") AS ?identifiers)"

            + " WHERE {"
            // Match the ISSN
            + "   ?item wdt:P236 \"%1$s\" ."

            // Standard columns mapped to labels
            + "   OPTIONAL { ?item wdt:P1476 ?title . }"
            + "   OPTIONAL { ?item wdt:P123 ?publisher . }"
            // specific for newspapers, but sometimes used for magazines as well
            // There seems to be no equivalent magazine (or book) P-claim number.
            + "   OPTIONAL { ?item wdt:P3912 ?newsPaperFormat . }"

            // Fetch Editors-in-Chief (P578) an/or Editors (P98)
            + "OPTIONAL {"
            + "  {"
            + "    ?item p:P98 ?editorStmt ."
            + "    ?editorStmt ps:P98 ?editorNode ."
            + "  }"
            + "  UNION"
            + "  {"
            + "    ?item p:P578 ?editorStmt ."
            + "    ?editorStmt ps:P578 ?editorNode ."
            + "  }"
            // Exclude past editors (where end time P582 is in the past)
            + "  OPTIONAL { ?editorStmt pq:P582 ?endTime . }"
            + "  FILTER(!BOUND(?endTime) || ?endTime > NOW())"
            // Get label for active editors
            + "  ?editorNode rdfs:label ?editorLabel ."
            + "  FILTER(LANG(?editorLabel) = \"%2$s\" || LANG(?editorLabel) = \"en\")"
            + "}"

            // P2896: publication interval (Raw values, no text joins)
            + "   OPTIONAL {"
            + "     ?item p:P2896/psv:P2896 ?intervalNode ."
            + "     ?intervalNode wikibase:quantityAmount ?pubAmount ."
            + "     ?intervalNode wikibase:quantityUnit ?pubUnitQ ."
            + "   }"

            + " OPTIONAL {"
            + "  ?item wdt:P407 ?language ."
            + "  OPTIONAL { ?language wdt:P220 ?iso3 . }"
            + "  OPTIONAL { ?language wdt:P218 ?iso2 . }"
            + "  BIND(COALESCE(?iso3, ?iso2, STRAFTER(STR(?language), \"/entity/\")) AS ?langCode)"
            + " }"

            // All identifiers, which are of the type "wikibase:ExternalId"
            + " OPTIONAL {"
            + "  ?item ?propVar ?idValue ."
            + "  ?wdProp wikibase:directClaim ?propVar ."
            + "  ?wdProp wikibase:propertyType wikibase:ExternalId ."
            + " }"

            // Use the automatic labeling service
            + "   SERVICE wikibase:label { bd:serviceParam wikibase:language \"%2$s\" }"
            + " }"
            + " GROUP BY "
            + " ?item ?itemLabel"
            + " ?title"
            + " ?newsPaperFormat ?newsPaperFormatLabel"
            + " ?publisher ?publisherLabel"
            + " ?pubAmount ?pubUnitQ"
            + " ?langCode"
            + " LIMIT 1";

    /** Param 1: the encoded SPARQL. Note this is not the default host. */
    private static final String ISSN_SEARCH_URL =
            "https://query.wikidata.org/sparql?query=%s&format=json";

    @Nullable
    private HttpCall httpCall;

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
    public WikidataSearchEngine(@NonNull final Context context,
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
                                    R.string.site_wikidata,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    HOST_URL,
                                    HOST_LOCALE)
                .setPreferenceFragmentClazz(WikidataPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_WIKIDATA)
                .setIdentifierKey(Identifier.EntityType.Author, Identifier.SID_WIKIDATA)
                .setAuthorResolverSupplier(WikidataAuthorResolver::create);
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
        final String name = context.getString(R.string.identifier_wikidata);
        final String site = "https://www.wikidata.org";
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_WIKIDATA,
                               name, site,
                               "https://www.wikidata.org/wiki/%s",
                               null),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_WIKIDATA,
                               name, site,
                               "https://www.wikidata.org/wiki/%s",
                               "P50"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Text,
                               Identifier.SID_WIKIDATA,
                               name, site,
                               "https://www.wikidata.org/wiki/%s",
                               "P179")
        );
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws SearchException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getDashFormattedIssn8(getEngineId());

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        String lang = userLocale.getLanguage();
        if (lang.isEmpty()) {
            lang = "en";
        }
        final String serviceLang = "en".equals(lang) ? "en,*" : lang + ",en,*";

        final String sparql = String.format(ISSN_SPARQL, codeStr, serviceLang);

        final Book book = new Book();
        final JSONObject document;
        try {
            final String url = String.format(ISSN_SEARCH_URL,
                                             URLEncoder.encode(sparql, StandardCharsets.UTF_8));

            httpCall = httpCallFactory.createCall();
            final String response = httpCall.getAsString(url, null);
            document = new JSONObject(response);

            if (!isCancelled()) {
                parseFromIssn(context, document, productCode, book);
            }

        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        }

        return book;
    }


    @VisibleForTesting
    void parseFromIssn(@NonNull final Context context,
                       @NonNull final JSONObject document,
                       @NonNull final ProductCode productCode,
                       @NonNull final Book book)
            throws JSONException {

        final WikidataBookParser parser = new WikidataBookParser(document, productCode, book);
        parser.parse();
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (httpCall != null) {
                httpCall.cancel();
            }
        }
    }
}
