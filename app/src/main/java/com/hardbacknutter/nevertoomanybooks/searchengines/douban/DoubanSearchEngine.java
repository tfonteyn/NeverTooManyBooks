/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.douban;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class DoubanSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    /**
     * param 1: the ISBN
     */
    private static final String SEARCH_URL = "/book/subject_search?search_text=%1$s";
    private static final Pattern PATTERN_BR = Pattern.compile("<br>");

    private final RatingParser ratingParser;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public DoubanSearchEngine(@NonNull final Context appContext,
                              @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        ratingParser = new RatingParser(10);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(SEARCH_URL, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            processDocument(context, document, fetchCovers, book);
        }

        return book;
    }

    @VisibleForTesting
    public void processDocument(@NonNull final Context context,
                                @NonNull final Document document,
                                @NonNull final boolean[] fetchCovers,
                                @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // The result is always a list, even if only one book found.
        final Optional<String> oUrl = parseMultiResult(document);
        if (oUrl.isPresent()) {
            final Document d = loadDocument(context, oUrl.get(), null);
            if (!isCancelled()) {
                parse(context, d, fetchCovers, book);
            }
        } else {
            // Keep this as a fallback, but we're unlikely to get here.
            parse(context, document, fetchCovers, book);
        }
    }

    /**
     * Parse the given document for being a multi-result. If found, extract
     * the first result and return the book url.
     *
     * @param document to parse
     *
     * @return url for the book details page
     */
    @VisibleForTesting
    @NonNull
    public Optional<String> parseMultiResult(@NonNull final Document document) {
        // Look for a script
        // with the text: window.__DATA__ = {"co...
        // Grab the part after the first equal sign
        // and parse as a JSON string
        // First element of the "items" array
        // item should contain: "url": "https://book.douban.com/subject/36874304/"
        final Elements elements = document.select("script[type=\"text/javascript\"]");
        for (final Element element : elements) {
            final String s = element.html().strip();
            if (s.startsWith("window.__DATA__ =")) {
                final String[] sa = s.split("=", 2);
                if (sa.length > 1) {
                    final JSONArray items = new JSONObject(sa[1]).optJSONArray("items");
                    if (items != null) {
                        if (!items.isEmpty()) {
                            final String url = items.getJSONObject(0)
                                                    .optString("url", null);
                            if (url != null) {
                                return Optional.of(url);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parse a book-details page.
     *
     * @param context
     * @param document
     * @param fetchCovers
     * @param book
     *
     * @throws StorageException
     * @throws SearchException
     * @throws CredentialsException
     */
    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // As meta tags in the HEAD:
        //  <meta property="og:title" content="三体" />
        //  <meta property="og:description" content="军方探寻外星文明的绝秘计划“红岸工程”取得了突破性进展。但在按下发射键的那一刻，历经劫难的叶文洁没有意识到，她彻底改变了人类的命运。地球文明向宇宙发出的第一声啼鸣，以太阳为中心，以光速向宇宙深处飞驰…..." />
        //  <meta property="og:site_name" content="豆瓣" />
        //  <meta property="og:url" content="https://book.douban.com/subject/36874304/" />
        //  <meta property="og:image" content="https://img1.doubanio.com/view/subject/l/public/s34850048.jpg" />
        //  <meta property="og:type" content="book" />
        //  <meta property="book:author" content="刘慈欣" />
        //  <meta property="book:isbn" content="9787536692930" />

        int id;

        final Elements metaElements = document.head().select("meta");
        for (final Element meta : metaElements) {
            final String property = meta.attr("property");
            final String content = meta.attr("content");
            // There is also "og:image" with a cover url.
            // These can be VERY large and lead to java.net.SocketTimeoutException
            // We'll grab the thumbnail instead.
            switch (property) {
                case "og:title":
                    book.putString(DBKey.TITLE, content);
                    break;

                case "book:isbn":
                    book.putString(DBKey.BOOK_ISBN, content);
                    break;

                case "og:description":
                    // The description in the meta element is shortened.
                    // We copy it while we have it, but will overwrite when we
                    // can (should) get the full description later on.
                    book.putString(DBKey.DESCRIPTION, content);
                    break;

                case "og:url": {
                    // content="https://book.douban.com/subject/36874304/"
                    final String[] parts = content.split("/");
                    // Sanity check;
                    // Make sure it's an int as we might need it for more parsing,
                    // but store it in the book as a string as per usual with SID values
                    if (parts.length >= 5) {
                        try {
                            id = Integer.parseInt(parts[4]);
                            if (id > 0) {
                                book.putString(DBKey.SID_DOUBAN, String.valueOf(id));
                            }
                        } catch (@NonNull final NumberFormatException ignore) {
                            // ignore
                        }
                    }
                    break;
                }
            }
        }

        final Element infoTable = document.selectFirst("div#info");
        if (infoTable == null) {
            return;
        }

        final Locale siteLocale = getLocale(context);

        final Elements labels = infoTable.select("span.pl");
        for (final Element label : labels) {
            // labels include the ':' except the author, where the ':' is a sibling text element
            switch (label.text().strip()) {
                case "作者": {
                    // Author
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        // 刘慈欣 ==> Liu Cixin
                        // [法] 保罗·霍尔特   ==>  [France] Paul Holt
                        // URGENT: for the "[.] ." format, we end up with the [.] becoming the given name.
                        //  Need to decide how to handle the country prefix
                        //  Two options: drop it altogether, or move it to the end and use "(country)"
                        //  as we can handle a round-bracket section as a suffix already.
                        final String text = a.text();
                        final Author author = Author.from(text);
                        book.add(author);
                    }
                    break;
                }
                case "出版社:": {
                    // Publisher
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        book.add(Publisher.from(a.text()));
                    }
                    break;
                }
                case "出品方:": {
                    // Producer (printer?) ... not used
                    break;
                }
                case "原作名:": {
                    // Original title
                    final Node n = label.nextSibling();
                    if (n != null) {
                        book.putString(DBKey.TITLE_ORIGINAL_LANG, n.toString().strip());
                    }
                    break;
                }
                case "译者": {
                    // Translator
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        final Author author = Author.from(a.text());
                        author.setType(Author.TYPE_TRANSLATOR);
                        book.add(author);
                    }
                    break;
                }
                case "出版年:": {
                    // Year of publication
                    final Node n = label.nextSibling();
                    if (n != null) {
                        // Dates are listed as yyyy-MM; use a PartialDate parser.
                        final String dateStr = n.toString().strip();
                        final PartialDateParser parser = new PartialDateParser();
                        parser.parse(dateStr, false).ifPresent(book::setPublicationDate);
                    }
                    break;
                }
                case "页数:": {
                    // Pages
                    final Node n = label.nextSibling();
                    if (n != null) {
                        book.putString(DBKey.PAGE_COUNT, n.toString().strip());
                    }
                    break;
                }
                case "定价:": {
                    // List price
                    final Node n = label.nextSibling();
                    if (n != null) {
                        addPriceListed(context, siteLocale, n.toString().strip(), null, book);
                    }
                    break;
                }
                case "装帧:": {
                    // Format
                    final Node n = label.nextSibling();
                    if (n != null) {
                        book.putString(DBKey.FORMAT, n.toString().strip());
                    }
                    break;
                }
                case "丛书:": {
                    // Series
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        book.add(Series.from(a.text()));
                    }
                    break;
                }
            }
        }

        final Element ratingElement = document.selectFirst("div.rating_self > strong.rating_num ");
        if (ratingElement != null) {
            ratingParser.parse(ratingElement.text()).ifPresent(
                    rating -> book.putFloat(DBKey.RATING, rating));
        }

        // The meta element was shortened, overwrite if we find the full description
        final Element relInfo = document.selectFirst("div.related_info");
        if (relInfo != null) {
            // Then can be multiple "intro" blocks, as this is used for description, author, ...
            // We normally grab the first with the description only,
            // but check for an "a" element with javascript to "Expand".
            // If found, this means the text was very long, and was partially hidden.
            // In that case we grad the 2nd "intro" block which is the full description.
            final Elements introElements = relInfo.select("div.intro");
            if (!introElements.isEmpty()) {
                Element intro = introElements.get(0);
                if (intro.selectFirst("a.a_show_full") != null
                    && introElements.size() > 1) {
                    intro = introElements.get(1);
                }
                book.putString(DBKey.DESCRIPTION, intro.html().strip());
            }
        }

        // The content table - in the example we used, it's the chapter list.
        // TODO: check if there is a way of detecting chapter-list versus actual content-list
//        if (id > 0) {
//            final Element tocElement = document.selectFirst("div#dir_" + id + "_full");
//            if (tocElement != null) {
//                final String[] content = PATTERN_BR.split(tocElement.html());
//                 .... numbered lines with chapter-titles
//            }
//        }


        // There is no language listed, we're assuming Simplified Chinese
        if (!book.contains(DBKey.LANGUAGE)) {
            book.putString(DBKey.LANGUAGE, "zho");
        }

        if (fetchCovers[0]) {
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            if (!isbn.isEmpty()) {
                // "div#mainpic > a" element will have as the href a large version of the image.
                // "div#mainpic > a > img" will have "src" point to a thumbnail
                // We found the large image to result in socket-timeouts
                // (without modifying our default timeout)
                // Choosing to get the thumbnail here:
                final Element img = document.selectFirst("div#mainpic > a > img");
                if (img != null) {
                    final String src = img.attr("src");
                    if (!src.isEmpty()) {
                        saveImage(context, src, isbn, 0, null).ifPresent(
                                fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
                    }
                }
            }
        }
    }
}
