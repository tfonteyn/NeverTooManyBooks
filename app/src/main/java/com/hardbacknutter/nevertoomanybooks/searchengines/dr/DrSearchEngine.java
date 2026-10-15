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

package com.hardbacknutter.nevertoomanybooks.searchengines.dr;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** ISBN lookup using D&R's server-rendered product pages. */
public class DrSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn {

    private static final String HOST_URL = "https://www.dr.com.tr";

    @Keep
    public DrSearchEngine(@NonNull final Context context,
                          @NonNull final SearchEngineConfig config) {
        super(context, config);
    }

    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder("dr", R.string.site_dr,
                                    List.of(R.string.site_description_turkish,
                                            R.string.site_description_shop),
                                    HOST_URL, new Locale("tr", "TR"));
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws SearchException, StorageException {
        final ProductCode isbn = ISBN.parse(criteria.requireProductCode().asText());
        if (!isbn.isIsbn() || isCancelled()) {
            return new Book();
        }
        // D&R indexes the ISBN-13 barcode, including for older ISBN-10 input.
        final Document document = loadHtml(context,
                HOST_URL + "/search?q=" + isbn.asText(ProductCodeType.Isbn13), null);
        // An exact ISBN search redirects to the product page. Never accept an
        // arbitrary recommendation when the search returns no exact product.
        final Book book = parse(document, isbn);
        if (!book.isEmpty() && !isCancelled() && criteria.getFetchCovers()[0]) {
            final Element image = document.selectFirst(".js-detail-slider-show img[src]");
            if (image != null) {
                final String url = image.absUrl("src");
                if (!url.isBlank()) {
                    httpCallFactory.saveImage(url, null, book.getRawProductCode(), 0, null)
                                   .ifPresent(
                                           file -> CoverFileSpecArray.setFileSpec(book, 0, file));
                }
            }
        }
        return isCancelled() ? new Book() : book;
    }

    /** Parse only a product whose barcode matches the requested edition. */
    @VisibleForTesting
    @NonNull
    public Book parse(@NonNull final Document document, @NonNull final ProductCode expected) {
        final Book book = new Book();
        final Element title = document.selectFirst("h1.js-text-prd-name");
        final String barcode = property(document, "Barkod:");
        final ProductCode actual = ISBN.parse(barcode);
        if (title == null || title.text().isBlank() || !actual.isIsbn()
            || !expected.isIsbn()
            || !actual.asText(ProductCodeType.Isbn13)
                      .equals(expected.asText(ProductCodeType.Isbn13))) {
            return book;
        }
        book.setTitle(title.text());
        book.setRawProductCode(actual.asText());
        for (final Element person : document.select(".js-wrapper-author h2.author")) {
            final String label = person.ownText().strip();
            final int role;
            if ("Yazar:".equals(label)) {
                role = AuthorRole.WRITER;
            } else if ("Çevirmen:".equals(label)) {
                role = AuthorRole.TRANSLATOR;
            } else {
                continue;
            }
            for (final Element link : person.select("a")) {
                if (!link.text().isBlank()) {
                    bookParserHelper.addAuthor(Author.from(link.text()), role, book, false);
                }
            }
        }
        final Element publisher = document.selectFirst("#publisherName a");
        if (publisher != null && !publisher.text().isBlank()) {
            book.add(Publisher.from(publisher.text()));
        }
        final String pages = property(document, "Sayfa Sayısı:");
        if (!pages.isBlank()) {
            book.setPages(pages);
        }
        final String language = property(document, "Dil:");
        if (!language.isBlank()) {
            book.setLanguage("Türkçe".equalsIgnoreCase(language) ? "tur" : language);
        }
        // This label describes the first printing of this publisher's edition,
        // not the original publication date of the literary work.
        final String year = property(document, "İlk Baskı Yılı:");
        if (year.matches("[0-9]{4}")) {
            book.setPublicationDate(year);
        }
        final Element description = document.selectFirst(".js-detail-product");
        if (description != null && !description.text().isBlank()) {
            book.setDescription(description.html());
        }
        return book;
    }

    @NonNull
    private static String property(@NonNull final Document document,
                                   @NonNull final String label) {
        for (final Element row : document.select(".js-list-prd-property > li")) {
            final Element key = row.selectFirst("strong");
            final Element value = row.selectFirst("span");
            if (key != null && value != null && label.equals(key.text().strip())) {
                return value.text().strip();
            }
        }
        return "";
    }
}
