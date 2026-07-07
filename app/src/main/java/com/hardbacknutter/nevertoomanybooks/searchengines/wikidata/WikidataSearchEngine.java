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
import android.content.res.Resources;

import androidx.annotation.AnyThread;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

import okhttp3.OkHttpClient;

public class WikidataSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIssn {

    private static final String TAG = "WikidataSearchEngine";

    private static final String SITE_URL = "https://www.wikidata.org";
    private static final String BOOK_URL = "https://www.wikidata.org/wiki/%s";
    private static final String AUTHOR_URL = "https://www.wikidata.org/wiki/%s";
    private static final String SERIES_URL = "https://www.wikidata.org/wiki/%s";

    private static final String PREFERENCE_KEY = "wikidata";

    /** Website character encoding. */
    private static final String CHARSET = "UTF-8";

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

    // json value element
    private static final String VALUE = "value";

    // 2026-06-21
    private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
            Map.entry("P179", Identifier.SID_WIKIDATA),

            Map.entry("P8619", Identifier.SID_BEDETHEQUE),
            Map.entry("P227", Identifier.SID_DNB),
            Map.entry("P10318", Identifier.SID_DOUBAN),
            Map.entry("P6947", Identifier.SID_GOODREADS),
            Map.entry("P1235", Identifier.SID_ISFDB),
            Map.entry("P13137", Identifier.SID_ISFDB_PUB_SERIES),
            Map.entry("P9088", Identifier.SID_KBR),
            Map.entry("P10419", Identifier.SID_LAST_DODO_NL),
            Map.entry("P244", Identifier.SID_LCCN),
            Map.entry("P8513", Identifier.SID_LIBRARY_THING),
            Map.entry("P5792", Identifier.SID_NOOSFERE),
            Map.entry("P243", Identifier.SID_OCLC),
            Map.entry("P214", Identifier.SID_VIAF)
    );

    private static final Pattern IDENT_SPLIT_PATTERN = Pattern.compile("\\|");

    @Nullable
    private HttpCall httpCall;

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
                .setPreferenceFragmentClazz(WikidataPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_WIKIDATA)
                .setIdentifierKey(Identifier.EntityType.Author, Identifier.SID_WIKIDATA)
                .setAuthorResolverSupplier(WikidataAuthorResolver::create);
    }

    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_wikidata);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_WIKIDATA,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               null),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_WIKIDATA,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P50"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Text,
                               Identifier.SID_WIKIDATA,
                               name,
                               SITE_URL,
                               SERIES_URL,
                               "P179")
        );
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws SearchException {

        final String codeStr = SearchEngineUtils.formatIssn8(context, getEngineId(), productCode);

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
            final String url = String.format(ISSN_SEARCH_URL, URLEncoder.encode(sparql, CHARSET));

            final OkHttpClient httpClient = createHttpClient();
            httpCall = HttpCallFactory.create(httpClient, getEngineId());
            final String response = httpCall.getAsString(url, getLocale(context), userLocale, null);
            document = new JSONObject(response);

            if (!isCancelled()) {
                parseIssn(context, document, productCode, book);
            }

        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        }

        return book;
    }


    @VisibleForTesting
    void parseIssn(@NonNull final Context context,
                   @NonNull final JSONObject document,
                   @NonNull final ProductCode productCode,
                   @NonNull final Book book)
            throws JSONException {

        // we don't get it from the site, just add it
        book.setRawProductCode(productCode.asText());

        // We expect one result, just throw otherwise
        final JSONObject item = document.getJSONObject("results")
                                        .getJSONArray("bindings")
                                        .getJSONObject(0);

        final List<Identifier.Value> ivs = new ArrayList<>();

        JSONObject o;

        // The title is mandatory.
        o = item.getJSONObject("title");
        book.setTitle(o.getString(VALUE));
        // and the item itself, being the SID
        o = item.getJSONObject("item");
        final String sid = getQ(o.getString(VALUE));
        // paranoia...
        if (sid != null && !sid.isEmpty()) {
            ivs.add(new Identifier.Value(Identifier.SID_WIKIDATA, sid));
        }

        // all else is optional.
        o = item.optJSONObject("langCode", null);
        if (o != null) {
            book.setLanguage(o.optString(VALUE, null));
        }
        o = item.optJSONObject("newsPaperFormatLabel", null);
        if (o != null) {
            book.setFormat(o.optString(VALUE, null));
        }
        o = item.optJSONObject("publisherLabel", null);
        if (o != null) {
            final String s = o.optString(VALUE, null);
            if (s != null && !s.isBlank()) {
                book.add(Publisher.from(s));
            }
        }

        parsePubAmountAndUnit(context, item, book);

        o = item.optJSONObject("identifiers", null);
        if (o != null) {
            parseIdentifiers(o, ivs);
        }

        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }
    }

    private void parseIdentifiers(@NonNull final JSONObject o,
                                  @NonNull final List<Identifier.Value> ivs) {
        final String s = o.optString(VALUE, null);
        if (s == null || s.isBlank()) {
            return;
        }
        // Avoid duplicates overwriting
        final Set<String> keys = new HashSet<>();

        for (final String entry : IDENT_SPLIT_PATTERN.split(s)) {
            final String[] id = entry.split(":");
            // paranoia
            if (id.length == 2) {
                final String key = IDENTIFIER_MAPPING.getOrDefault(id[0], id[0]);
                if (!keys.contains(key)) {
                    keys.add(key);
                    //noinspection DataFlowIssue
                    ivs.add(new Identifier.Value(key, id[1]));
                }
            }
        }
    }

    private void parsePubAmountAndUnit(@NonNull final Context context,
                                       @NonNull final JSONObject item,
                                       @NonNull final Book book) {

        final JSONObject pubAmount = item.optJSONObject("pubAmount", null);
        final JSONObject pubUnit = item.optJSONObject("pubUnitQ", null);
        if (pubAmount == null && pubUnit == null) {
            return;
        }


        int amount = 0;
        if (pubAmount != null) {
            final String pubAmountType = pubAmount.optString("type");
            // sanity check before we read the value as a float
            if ("literal".equals(pubAmountType)) {
                // The amount value can be a float, e.g. "0.5" to indicate twice daily.
                // Presumably, other intervals can also be floats.
                final float f = pubAmount.optFloat(VALUE, 0);
                if (f > 0.0 && f < 1.0) {
                    // Flip the fraction so we get an actual interval
                    amount = (int) (1 / f);
                } else {
                    amount = (int) f;
                }
            }
        }

        if (pubUnit != null) {
            final Resources res = context.getResources();

            final String q = getQ(pubUnit.optString(VALUE));
            if (q != null) {
                final String desc;
                switch (q) {
                    case "Q573":
                        desc = res.getQuantityString(R.plurals.publication_frequency_daily,
                                                     amount, amount);
                        break;
                    case "Q23387":
                        desc = res.getQuantityString(R.plurals.publication_frequency_weekly,
                                                     amount, amount);
                        break;
                    case "Q5151":
                        desc = res.getQuantityString(R.plurals.publication_frequency_monthly,
                                                     amount, amount);
                        break;
                    case "Q577":
                        desc = res.getQuantityString(R.plurals.publication_frequency_yearly,
                                                     amount, amount);
                        break;
                    case "Q2993680":
                        // fortnight
                        desc = res.getQuantityString(R.plurals.publication_frequency_monthly,
                                                     2, 2);
                        break;
                    case "Q1643308":
                        // quarter
                        desc = res.getQuantityString(R.plurals.publication_frequency_yearly,
                                                     4, 4);
                        break;
                    case "Q3955006":
                        // semester
                        desc = res.getQuantityString(R.plurals.publication_frequency_yearly,
                                                     2, 2);
                        break;
                    default:
                        // in theory we will never get here, as the above cases
                        // are supposed to be the full/limited list for P2896
                        return;
                }
                //URGENT: not a good idea....
                book.setDescription(desc);
            }
        }
    }

    @Nullable
    private String getQ(@Nullable final String url) {
        if (url == null || !url.contains("/Q")) {
            return null;
        }
        return url.substring(url.lastIndexOf('/') + 1);
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
