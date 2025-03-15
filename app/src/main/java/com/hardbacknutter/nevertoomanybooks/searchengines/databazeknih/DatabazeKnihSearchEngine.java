/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih;

import android.content.Context;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class DatabazeKnihSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    private static final String TAG = "DatabazeKnihSearchEngin";

    private static final String BY_ISBN = "/search?in=books&q=%1$s";

    private final RatingParser ratingParser;
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();
    private final DatabazeKnihAuthorResolver resolver;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public DatabazeKnihSearchEngine(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        ratingParser = new RatingParser(5);

        resolver = new DatabazeKnihAuthorResolver(appContext, this);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final String url = getHostUrl(context) + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    /**
     * Parses the downloaded {@link Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // id and title
        final String sid = parseMetaTags(document, book);

        final Elements itemProps = document.select("[itemprop]");
        for (final Element itemProp : itemProps) {
            final String prop = itemProp.attr("itemprop");
            switch (prop) {
                case "author": {
                    parseAuthor(itemProp, Author.TYPE_WRITER, book);
                    break;
                }
                case "description": {
                    final Element desc = itemProp.nextElementSibling();
                    if (desc != null) {
                        String text = desc.wholeText();
                        // remove the "click to see more" if present
                        if (text.endsWith("... celý text")) {
                            text = text.substring(0, text.length() - 13);
                        }
                        book.putString(DBKey.DESCRIPTION, text);
                    }
                    break;
                }
                case "ratingValue": {
                    ratingParser.parse(itemProp.text()).ifPresent(rating ->
                                                                          book.putFloat(
                                                                                  DBKey.RATING,
                                                                                  rating));
                    break;
                }
                case "genre": {
                    final List<Tag> tags = itemProp
                            .select("a.genre")
                            .stream().map(Element::text)
                            .map(Tag::new)
                            .collect(Collectors.toList());
                    if (!tags.isEmpty()) {
                        book.setTags(tags);
                    }
                    break;
                }
                case "datePublished": {
                    final String text = itemProp.text();
                    if (!text.isEmpty()) {
                        partialDateParser.parse(text).ifPresent(book::setPublicationDate);
                    }
                    break;
                }
                case "publisher": {
                    final String text = itemProp.text();
                    if (!text.isEmpty()) {
                        book.add(Publisher.from(text));
                    }
                    break;
                }
                default:
                    Log.d(TAG, "prop=" + prop);
            }
        }

        Element element;
        element = document.selectFirst("h3 > a[href^=/serie/]");
        if (element != null) {
            final String seriesName = SearchEngineUtils.cleanName(element.text());
            if (!seriesName.isEmpty()) {
                final Series series = Series.from(seriesName);
                element = document.selectFirst(
                        "span.nowrap > span.odright_pet, span.nowrap > span.odleft_pet ");
                if (element != null) {
                    final String nr = element.text();
                    if (!nr.isEmpty()) {
                        series.setNumber(nr);
                    }
                }
                book.add(series);
            }
        }

        element = document.selectFirst("span:contains(Originální název:)");
        if (element != null) {
            element = element.nextElementSibling();
            if (element != null) {
                // <h4>The Case of the Left-Handed Lady<span class="gray">,</span> 2007</h4>
                if ("h4".equals(element.tag().getName())) {
                    if (element.childNodeSize() == 3) {
                        String text;
                        text = SearchEngineUtils.cleanName(element.childNode(0).toString());
                        if (!text.isEmpty()) {
                            book.putString(DBKey.TRANSLATION_ORIGINAL_TITLE, text);
                        }
                        text = SearchEngineUtils.cleanText(element.childNode(2).toString());
                        partialDateParser.parse(text).ifPresent(
                                book::setFirstPublicationDate);
                    }
                }
            }
        }

        // in addition to the "genre" tags parsed above
        book.addTags(document.select("a.tag").stream()
                             .map(Element::text)
                             .map(Tag::new)
                             .collect(Collectors.toList()));

        // Sanity check
        if (sid != null && !sid.isEmpty()) {
            // fetch the "more details" and parse
            final Document additional = loadDocument(
                    context, getHostUrl(context) + "/book-detail-more-info/" + sid, null);
            parseAdditional(context, additional, book);
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = book.getString(DBKey.ISBN);
            parseCover(context, document, isbn, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void parseAdditional(@NonNull final Context context,
                                 @NonNull final Document root,
                                 @NonNull final Book book)
            throws SearchException, CredentialsException {
        Element element;

        element = root.selectFirst("[itemprop='numberOfPages']");
        if (element != null) {
            final String text = element.text();
            if (!text.isEmpty()) {
                book.putString(DBKey.PAGES, text);
            }
        }

        element = root.selectFirst("[itemprop='language']");
        if (element != null) {
            final String text = element.text();
            if (!text.isEmpty()) {
                book.putString(DBKey.LANGUAGE, text);
            }
        }

        // there can be more than one isbn. First one "wins"
        if (book.getIsbn().isEmpty()) {
            element = root.selectFirst("[itemprop='isbn']");
            if (element != null) {
                final String text = ISBN.cleanText(element.text());
                if (!text.isEmpty()) {
                    book.setIsbn(text);
                }
            }
        }

        element = root.selectFirst("[itemprop='ilustrator']");
        if (element != null) {
            parseAuthor(element, Author.TYPE_ARTIST, book);
        }
        element = root.selectFirst("[itemprop='cover']");
        if (element != null) {
            parseAuthor(element, Author.TYPE_COVER_ARTIST, book);
        }
        element = root.selectFirst("[itemprop='translator']");
        if (element != null) {
            parseAuthor(element, Author.TYPE_TRANSLATOR, book);
        }

        // for translations, we keep that date if already set.
        if (!book.getFirstPublicationDate().isPresent()) {
            // 1. vydání originálu:
            element = root.selectFirst("span:contains(1. vydání originálu:)");
            if (element != null) {
                final Node textNode = element.nextSibling();
                if (textNode != null) {
                    final String text = textNode.toString().trim();
                    if (!text.isEmpty()) {
                        partialDateParser.parse(text).ifPresent(book::setFirstPublicationDate);
                    }
                }
            }
        }

        // Vazba knihy == binding
        element = root.selectFirst("span:contains(Vazba knihy:)");
        if (element != null) {
            final Node textNode = element.nextSibling();
            if (textNode != null) {
                final String text = textNode.toString().trim();
                if (!text.isEmpty()) {
                    book.putString(DBKey.FORMAT, text);
                }
            }
        }

        // Náklad == circulation
        element = root.selectFirst("span:contains(Náklad:)");
        if (element != null) {
            final Node textNode = element.nextSibling();
            if (textNode != null) {
                final String text = textNode.toString().trim();
                if (!text.isEmpty()) {
                    book.putString(DBKey.PRINT_RUN, text);
                }
            }
        }

        // Forma == form
        element = root.selectFirst("span:contains(Forma:)");
        if (element != null) {
            final Node textNode = element.nextSibling();
            if (textNode != null) {
                final String text = textNode.toString().trim();
                if (!text.isEmpty()) {
                    // eBook
                    if ("ekniha".equals(text)) {
                        book.putString(DBKey.FORMAT, "ekniha");
                    } else {
                        book.putString(SiteField.FORMA, text);
                    }
                }
            }
        }
    }

    private void parseAuthor(@NonNull final Element element,
                             @Author.Type final int type,
                             @NonNull final Book book)
            throws SearchException, CredentialsException {
        for (final Element a : element.select("a")) {
            final String text = a.text();
            if (!text.isEmpty()) {
                final Author author = Author.from(text);

                final String url = a.attr("href");
                final int index = url.lastIndexOf('-');
                if (index > 0 && (index + 1) < url.length()) {
                    final String id = url.substring(index + 1);
                    if (!id.isEmpty()) {
                        author.setIdentifierValue(Identifier.SID_DATABAZE_KNIH, id);
                    }
                }

                // an adjacent "(p)" indicates this is a Pseudonym.
                final Element maybePseudonym = a.nextElementSibling();
                if (maybePseudonym != null
                    && "span".equals(maybePseudonym.tag().getName())
                    && "(p)".equals(maybePseudonym.text())) {
                    resolver.resolve(author);
                }

                addAuthor(author, type, book);
            }
        }
    }

    @Nullable
    private String parseMetaTags(@NonNull final Document document,
                                 @NonNull final Book book) {
        String id = null;

        final Elements metaElements = document.head().select("meta");
        for (final Element meta : metaElements) {
            final String property = meta.attr("property");
            final String content = meta.attr("content");
            switch (property) {
                case "og:title": {
                    book.setTitle(content);
                    break;
                }
                case "og:url": {
                    // https://www.databazeknih.cz/prehled-knihy/pripad-levoruke-damy-546691
                    final int index = content.lastIndexOf('-');
                    if (index > 0 && (index + 1) < content.length()) {
                        id = content.substring(index + 1);
                        book.setIdentifierValue(Identifier.SID_DATABAZE_KNIH, id);
                    }
                    break;
                }
                // case "og:image": this is a small thumbnail only
            }
        }

        return id;
    }

    /**
     * Parses the given {@link Document} for the cover and fetches it when present.
     *
     * @param context  Current context
     * @param document to parse
     * @param bookId   (optional) isbn or native id of the book,
     *                 will only be used for the temporary cover filename
     * @param cIdx     0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                        @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        String url = null;
        final Element img = document.selectFirst("img.kniha_img, img.kniha_img_audiobook");
        if (img != null) {
            url = img.attr("src");
        }
        if (url == null) {
            return Optional.empty();
        }

        return saveImage(context, url, bookId, cIdx, null);
    }

    /**
     * specific field names we add to the bundle based on parsed XML data.
     */
    static final class SiteField {
        static final String FORMA = "__DATABAZE_KNIH_FORMA";

        private SiteField() {
        }
    }
}
