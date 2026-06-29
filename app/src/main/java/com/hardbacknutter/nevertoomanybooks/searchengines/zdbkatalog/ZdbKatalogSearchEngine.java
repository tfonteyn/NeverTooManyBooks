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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

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
 */
public class ZdbKatalogSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIssn {

    private static final String TAG = "ZdbKatalogSearchEngine";

    private static final String SITE_URL = "https://zdb-katalog.de";
    private static final String PREFERENCE_KEY = "zdbkatalog";

    // 2026-06-21
    private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
            Map.entry("OCoLC", Identifier.SID_OCLC),
            Map.entry("Be", Identifier.SID_KBR),
            Map.entry("NL-HaKB", Identifier.SID_KBNL),
            Map.entry("DLC", Identifier.SID_LCCN),
            Map.entry("US-dlc", Identifier.SID_LCCN),
            Map.entry("DE-599", Identifier.SID_ZDB_KATALOG),
            Map.entry("PoLiBN", Identifier.SID_PORBASE),
            Map.entry("SE-LIBR", Identifier.SID_LIBRIS)
    );

    private static final Pattern IDENT_PATTERN = Pattern.compile("^\\(([^)]+)\\)(.+)$");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("[^0-9]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern M21_CTRL_PATTERN = Pattern.compile("[\\u0080-\\u009F]");

    private static final String ISSN_URL = "https://services.dnb.de/sru/zdb?"
                                           + "version=1.1"
                                           + "&operation=searchRetrieve"
                                           // escape and encode the '=' as %%3D
                                           + "&query=iss%%3D%1$s"
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
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_zdbkatalog,
                                    List.of(R.string.site_description_german,
                                            R.string.site_description_catalog),
                                    SITE_URL,
                                    Locale.GERMANY)
                // It shared the DNB identifier!
                .setIdentifierKeys(Identifier.SID_DNB)
                .setPreferenceFragmentClazz(ZdbKatalogPreferencesFragment.class);
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException {

        final String codeStr = SearchEngineUtils.formatIssn8(context, getEngineId(), productCode);

        final String url = String.format(ISSN_URL, codeStr);
        final Document document = loadDocument(context, Parser.xmlParser(), url, null);

        final Book book = new Book();

        if (!isCancelled()) {
            parseIssnMARC21xml(context, document, productCode, book);
        }

        return book;
    }

    // WARNING:  USE element.wholeText() AND NOT text() ... we want all strings as-is
    // and not cleaned up by jsoup.
    @VisibleForTesting
    void parseIssnMARC21xml(@NonNull final Context context,
                            @NonNull final Document document,
                            @NonNull final ProductCode productCode,
                            @NonNull final Book book) {

        final List<Identifier.Value> ivs = new ArrayList<>();

        Element x, a, b;
        char pubType = 0;

        x = document.selectFirst("controlfield[tag='001']");
        if (x != null) {
            ivs.add(new Identifier.Value(Identifier.SID_DNB, normalize(x)));
        }

        x = document.selectFirst("controlfield[tag='008']");
        if (x != null) {
            final String text = normalize(x);
            // sanity check
            if (text.length() == 40) {
                pubType = text.charAt(21);
            }
        }

        // Issn8
        x = document.selectFirst("datafield[tag='022'] subfield[code='a']");
        if (x != null) {
            book.setRawProductCode(ISBN.cleanText(normalize(x)));
        }
        // EAN code, i.e. Issn13 - it's unlikely but we might as well try
        x = document.selectFirst("datafield[tag='024'][ind1='3'] subfield[code='a']");
        if (x != null) {
            // overwrite !
            book.setRawProductCode(ISBN.cleanText(normalize(x)));
        }

        // Identifiers
        document.select("datafield[tag='035']")
                .stream()
                .map(df -> df.selectFirst("subfield[code='a']"))
                .filter(Objects::nonNull)
                .forEach(sf -> {
                    //noinspection DataFlowIssue
                    final Matcher matcher = IDENT_PATTERN.matcher(normalize(sf));
                    if (matcher.find()) {
                        String key = matcher.group(1);
                        final String value = matcher.group(2);
                        if (key != null && value != null) {
                            final String tmp = IDENTIFIER_MAPPING.get(key);
                            if (tmp != null) {
                                key = tmp;
                            }
                            ivs.add(new Identifier.Value(key, value));
                        }
                    }
                });

        x = document.selectFirst("datafield[tag='041'] subfield[code='a']");
        if (x != null) {
            book.setLanguage(normalize(x));
        }

        x = document.selectFirst("datafield[tag='245']");
        if (x != null) {
            a = x.selectFirst("subfield[code='a']");
            b = x.selectFirst("subfield[code='b']");
            if (a != null) {
                if (b == null) {
                    book.setTitle(normalize(a));
                } else {
                    book.setTitle(context.getString(R.string.name_colon_value,
                                                    normalize(a), normalize(b)));
                }
            }
        }

        // Publisher
        x = document.selectFirst("datafield[tag='264']");
        if (x != null) {
            // place
            a = x.selectFirst("subfield[code='a']");
            // name
            b = x.selectFirst("subfield[code='b']");
            if (b != null) {
                book.add(Publisher.from(normalize(b)));
            } else if (a != null) {
                book.add(Publisher.from(normalize(a)));
            }
        }

        final Element sizeField = document.selectFirst("datafield[tag='300'] subfield[code='c']");
        switch (pubType) {
            case 'n': {
                book.setFormat(context.getString(R.string.book_format_newspaper));
                break;
            }
            case 'p': {
                int formatResId = R.string.book_format_periodical;
                if (sizeField != null && parseHeightInCm(normalize(sizeField)) <= 21) {
                    formatResId = R.string.book_format_digest;
                }
                book.setFormat(context.getString(formatResId));
                break;
            }
        }

        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }

        // We don't get an author, use the publisher if we have one...
        book.getPrimaryPublisher()
            .ifPresent(p -> book.add(Author.from(p.getName())));

        if (!book.hasProductCode()) {
            book.setRawProductCode(productCode.asText());
        }
    }

    @NonNull
    private String normalize(@NonNull final Element element) {
        // Some elements can contain MARC21 control characters.
        // e.g. <subfield code="a">&#152;Der&#156; Spiegel online Themen</subfield>
        // Strip those out.
        final String s = M21_CTRL_PATTERN.matcher(element.wholeText()).replaceAll("");
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    private int parseHeightInCm(@Nullable final String rawSizeText) {
        if (rawSizeText == null || rawSizeText.isEmpty()) {
            return 0;
        }

        @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
        final String cleanText = WHITESPACE_PATTERN.matcher(rawSizeText.toLowerCase())
                                                   .replaceAll("");

        // Extract the numeric value
        final String digits = DIGITS_PATTERN.matcher(cleanText).replaceAll("");
        if (digits.isEmpty()) {
            return 0;
        }

        final int value = Integer.parseInt(digits);

        // If it's explicitly marked as mm, normalize it to cm
        if (cleanText.contains("mm")) {
            return (int) Math.ceil(value / 10.0);
        }

        return value;
    }
}
