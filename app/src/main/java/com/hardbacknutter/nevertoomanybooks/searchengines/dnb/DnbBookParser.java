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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class DnbBookParser {

    private static final Pattern DE_PRICE_PATTERN =
            Pattern.compile("EUR\\s*(\\d+\\.\\d{2})\\s*\\(DE\\)");
    private static final Pattern AT_PRICE_PATTERN =
            Pattern.compile("EUR\\s*(\\d+\\.\\d{2})\\s*\\(AT\\)");
    private static final Pattern CH_PRICE_PATTERN =
            Pattern.compile("(CHF\\s*\\d+\\.\\d{2})");

    private static final String SUBFIELD_CODE_A = "subfield[code='a']";
    private static final String SUBFIELD_CODE_B = "subfield[code='b']";
    private static final String SUBFIELD_CODE_C = "subfield[code='c']";
    private static final String SUBFIELD_CODE_H = "subfield[code='h']";


    @NonNull
    private final Context context;
    @NonNull
    private final Document document;
    @NonNull
    private final Book book;
    private final List<Identifier.Value> ivs = new ArrayList<>();

    @NonNull
    private final Locale userLocale;
    private final DnbParser dnbParser;

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param document to parse
     * @param book     to populate
     */
    public DnbBookParser(@NonNull final Context context,
                         @NonNull final Document document,
                         @NonNull final Book book) {
        this.context = context;
        this.document = document;
        this.book = book;

        dnbParser = new DnbParser(document);

        userLocale = context.getResources().getConfiguration().getLocales().get(0);
    }

    /**
     * Provide the DNB record identifier.
     */
    public void sidDnb() {
        final Identifier.Value value = dnbParser.cf001();
        if (value != null) {
            ivs.add(value);
        }
    }

    /**
     * Books only. Get the registered ISBN.
     */
    public void isbn() {
        // 020 - International Standard Book Number (R)
        final Element tag = document.selectFirst("datafield[tag='020']");
        if (tag == null) {
            return;
        }

        // $a - International Standard Book Number (NR)
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            book.setRawProductCode(ISBN.cleanText(DnbParser.normalise(a)));
        }
        // $c - Terms of availability (NR)
        final Element c = tag.selectFirst(SUBFIELD_CODE_C);
        if (c != null) {
            // Festeinband : EUR 17.00 (DE), EUR 17.50 (AT), CHF 24.50 (freier Preis)
            final Money price = parseListPrice(c);
            if (price != null && !price.isZero()) {
                book.setPriceListed(price);
            }
        }
    }

    @Nullable
    private Money parseListPrice(@NonNull final Element element) {
        final String priceString = DnbParser.normalise(element);
        if (priceString.isBlank()) {
            return null;
        }

        final Pattern pattern;
        final Currency currency;
        switch (userLocale.getCountry()) {
            case "AT": {
                pattern = AT_PRICE_PATTERN;
                currency = Money.EURO;
                break;
            }
            case "CH": {
                pattern = CH_PRICE_PATTERN;
                currency = Currency.getInstance(userLocale);
                break;
            }
            default: {
                pattern = DE_PRICE_PATTERN;
                // hardcoded to EURO, don't use the userLocale
                currency = Money.EURO;
                break;
            }
        }

        final Matcher matcher = pattern.matcher(priceString);
        if (matcher.find()) {
            final String s = matcher.group(1);
            if (s == null) {
                return null;
            }
            try {
                // The values use a dot as decimal separator.
                // Don't use our RealNumberParser as that would use a ',' for germany etc...
                final double val = Double.parseDouble(s);
                return new Money(BigDecimal.valueOf(val), currency);
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }

        return null;
    }

    /**
     * Periodicals only. Get the registered ISSN.
     */
    public void issn() {
        // 022 - International Standard Serial Number (R)
        // $a - International Standard Serial Number (NR)
        final Element tag = document.selectFirst("datafield[tag='022'] > subfield[code='a']");
        if (tag == null) {
            return;
        }
        book.setRawProductCode(ISBN.cleanText(DnbParser.normalise(tag)));
    }

    public void ean13() {
        final String ean13 = dnbParser.ean13();
        if (ean13 != null) {
            // overwrite !
            book.setRawProductCode(ean13);
        }
    }

    public void identifiers() {
        ivs.addAll(dnbParser.identifiers());
    }

    /**
     * Language and original-language.
     */
    public void languages() {
        // 041 - Language Code (R)
        final Element tag = document.selectFirst("datafield[tag='041']");
        if (tag == null) {
            return;
        }
        // $a - Language code of text or sound track (R)
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            book.setLanguage(DnbParser.normalise(a));
        }
        // $h - Language code of original (R)
        final Element h = tag.selectFirst(SUBFIELD_CODE_H);
        if (h != null) {
            book.setTranslatedFromLanguage(DnbParser.normalise(h));
        }
    }

    public void authors() {
        dnbParser.authors().forEach(book::add);
    }

    public void originalTitle() {
        final String title = dnbParser.originalTitle();
        if (title != null) {
            book.setTranslatedFromTitle(title);
        }
    }

    public void title() {
        final String title = dnbParser.title(context);
        if (title != null) {
            book.setTitle(title);
        }
    }

    public void publishers() {
        final Pair<List<Publisher>, PartialDate> pubData = dnbParser.publishers();
        if (!pubData.first.isEmpty()) {
            book.setPublishers(pubData.first);
        }
        if (!PartialDate.NOT_SET.equals(pubData.second)) {
            book.setPublicationDate(pubData.second);
        }
    }

    public void description() {
        final String description = dnbParser.description();
        if (description != null) {
            book.setDescription(description);
        }
    }

    /**
     * Parse tag 300.
     * <p>
     * $a - Extent: Number of pages
     * $b - Other physical details
     */
    public void physicalDescription() {
        // 300 - Physical Description (R)
        final Element tag = document.selectFirst("datafield[tag='300']");
        if (tag == null) {
            return;
        }
        // $a - Extent (R)
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a == null) {
            return;
        }
        final String pages = DnbParser.normalise(a).split(" ")[0];
        try {
            final int p = NumberParser.toInt(pages);
            book.setPages(p);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        final Element b = tag.selectFirst(SUBFIELD_CODE_B);
        if (b != null) {
            // there may be more text snippets we can check for.
            final String text = DnbParser.normalise(b);

            if (text.contains("Online-Ressource")) {
                book.setFormat(context.getString(R.string.book_format_ebook));
            }

            if (text.contains("farb. Ill.")
                || text.contains("illus. in color")
                || text.contains("Farbig illustriert")
                || text.contains("Mit farbigen Zeichnungen")) {
                book.setColor(context.getString(R.string.book_color_full_color));
            }
        }
    }

    public void series() {
        final List<Series> series = dnbParser.series();
        if (!series.isEmpty()) {
            book.setSeries(series);
        }
    }

    public void genreTags() {
        final Set<Tag> genreTags = dnbParser.genreTags();
        if (!genreTags.isEmpty()) {
            book.setTags(genreTags);
        }
    }

    /**
     * Finish the parsing process.
     *
     * @param searchedCode to code which the user was searching for.
     */
    public void finish(@Nullable final ProductCode searchedCode) {
        ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }

        if (!book.hasProductCode() && searchedCode != null) {
            book.setRawProductCode(searchedCode.asText());
        }
    }
}
