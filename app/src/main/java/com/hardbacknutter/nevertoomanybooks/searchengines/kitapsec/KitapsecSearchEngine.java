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

package com.hardbacknutter.nevertoomanybooks.searchengines.kitapsec;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.URI;
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

/** ISBN lookup using Kitapseç's search list and product details. */
public class KitapsecSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn {
    private static final String HOST_URL = "https://www.kitapsec.com";

    @Keep
    public KitapsecSearchEngine(@NonNull final Context context,
                               @NonNull final SearchEngineConfig config) {
        super(context, config);
    }

    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder("kitapsec", R.string.site_kitapsec,
                                    List.of(R.string.site_description_turkish,
                                            R.string.site_description_shop),
                                    HOST_URL, new Locale("tr", "TR"))
                .setCharSetName("windows-1254");
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws SearchException, StorageException {
        final ProductCode isbn = criteria.requireProductCode();
        if (!isbn.isIsbn() || isCancelled()) {
            return new Book();
        }
        final Document results = loadHtml(context,
                HOST_URL + "/Arama/index.php?a=" + isbn.asText(ProductCodeType.Isbn13), null);
        final String url = parseSearchResult(results, isbn);
        if (url == null || isCancelled()) {
            return new Book();
        }
        final Document document = loadHtml(context, url, null);
        if (isCancelled()) {
            return new Book();
        }
        final Book book = parse(document, isbn);
        if (!book.isEmpty() && !isCancelled() && criteria.getFetchCovers()[0]) {
            final Element image = document.selectFirst("#dtyResimlerIc img[src]");
            if (image != null) {
                final String coverUrl = image.absUrl("src");
                if (!coverUrl.isBlank()) {
                    httpCallFactory.saveImage(coverUrl, null, book.getRawProductCode(), 0, null)
                                   .ifPresent(
                                           file -> CoverFileSpecArray.setFileSpec(book, 0, file));
                }
            }
        }
        return isCancelled() ? new Book() : book;
    }

    /** Ignore unrelated search hits and accept only product links on Kitapseç. */
    @VisibleForTesting
    @Nullable
    public String parseSearchResult(@NonNull final Document document,
                                    @NonNull final ProductCode expected) {
        for (final Element item : document.select(".urunListeleDiv .Ks_UrunSatir")) {
            final Element sku = item.selectFirst("meta[itemprop=sku]");
            final Element link = item.selectFirst("meta[itemprop=url]");
            if (sku == null || link == null || !matches(sku.attr("content"), expected)) {
                continue;
            }
            try {
                final URI uri = URI.create(HOST_URL + "/").resolve(link.attr("content"));
                if ("https".equalsIgnoreCase(uri.getScheme())
                    && "www.kitapsec.com".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && uri.getPath().startsWith("/Products/")
                    && uri.getPath().endsWith(".html")) {
                    return uri.toString();
                }
            } catch (final IllegalArgumentException ignored) {
                // A malformed result must not stop us checking later matches.
            }
        }
        return null;
    }

    @VisibleForTesting
    @NonNull
    public Book parse(@NonNull final Document document, @NonNull final ProductCode expected) {
        final Book book = new Book();
        final Element title = document.selectFirst(".dty_SagBlok > h1");
        final String barcode = property(document, "ISBN / BARKOD");
        if (title == null || title.text().isBlank() || !matches(barcode, expected)) {
            return book;
        }
        final String publisher = property(document, "Yayınevi / Marka");
        String name = title.text().strip();
        // The shop appends the publisher to the product title.
        if (!publisher.isBlank() && name.endsWith(" " + publisher)) {
            final String withoutPublisher = name.substring(0, name.length() - publisher.length())
                                                .strip();
            if (!withoutPublisher.isBlank()) {
                name = withoutPublisher;
            }
        }
        book.setTitle(name);
        book.setRawProductCode(ISBN.parse(barcode).asText(ProductCodeType.Isbn13));
        if (!publisher.isBlank()) {
            book.add(Publisher.from(publisher));
        }
        addPeople(document, "Yazar", AuthorRole.WRITER, book);
        addPeople(document, "Çevirmen", AuthorRole.TRANSLATOR, book);
        final String pages = property(document, "Sayfa Sayısı");
        if (!pages.isBlank()) {
            book.setPages(pages);
        }
        final String language = property(document, "Dil");
        if (!language.isBlank()) {
            book.setLanguage("Türkçe".equalsIgnoreCase(language) ? "tur" : language);
        }
        final Element description = document.selectFirst(".TabDivList > #tab1");
        if (description != null && !description.text().isBlank()) {
            final Element content = description.clone();
            content.select("h2, script, style").remove();
            book.setDescription(content.html());
        }
        return book;
    }

    private void addPeople(@NonNull final Document document, @NonNull final String label,
                           @AuthorRole.Role final int role, @NonNull final Book book) {
        final Element value = propertyElement(document, label);
        if (value != null) {
            if (value.select("a").isEmpty()) {
                if (!value.text().isBlank()) {
                    bookParserHelper.addAuthor(Author.from(value.text()), role, book, false);
                }
            } else {
                for (final Element link : value.select("a")) {
                    if (!link.text().isBlank()) {
                        bookParserHelper.addAuthor(Author.from(link.text()), role, book, false);
                    }
                }
            }
        }
    }

    private static boolean matches(@NonNull final String barcode,
                                   @NonNull final ProductCode expected) {
        final ProductCode actual = ISBN.parse(barcode);
        return actual.isIsbn() && expected.isIsbn()
               && actual.asText(ProductCodeType.Isbn13)
                        .equals(expected.asText(ProductCodeType.Isbn13));
    }

    @NonNull
    private static String property(@NonNull final Document document, @NonNull final String label) {
        final Element value = propertyElement(document, label);
        return value == null ? "" : value.text().strip();
    }

    @Nullable
    private static Element propertyElement(@NonNull final Document document,
                                           @NonNull final String label) {
        for (final Element key : document.select(".dty_SagBlok .detayBilgiDivIc .baslikText")) {
            if (label.equals(key.text().strip())) {
                Element sibling = key.nextElementSibling();
                if (sibling != null && sibling.hasClass("nokta")) {
                    sibling = sibling.nextElementSibling();
                }
                return sibling != null && sibling.hasClass("sonucText") ? sibling : null;
            }
        }
        return null;
    }
}
