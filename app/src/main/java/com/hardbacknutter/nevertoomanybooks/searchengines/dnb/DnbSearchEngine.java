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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorTypeMapper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.dnb.de">Deutsche Nationalbibliothek (DNB)</a>
 * <a href="https://www.dnb.de">Germany's National Library (DNB)</a>
 */
public class DnbSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {


    /**
     * param 1: the ISBN
     */
    private static final String SEARCH_URL = "/DE/list.html?" +
                                             "key=num&" +
                                             "key.GROUP=1&" +
                                             "t=%1$s" +
                                             "&sortD=-dat" +
                                             "&sortA=bez" +
                                             "&pr=0" +
                                             "&v=plist";

    /**
     * Suffixes we try to detect and remove from the title field.
     * Ths list of suffixes is presumed to be much larger of course.
     */
    private static final String[] TITLE_SUFFIXES = {
            ": Roman",
            ": Thriller",
            "Kriminalroman"};
    private static final Pattern PATTERN_SERIES_NR = Pattern.compile(" ; ");
    private static final Pattern PATTERN_BAR = Pattern.compile("\\|");
    private static final Pattern PATTERN_SLASH = Pattern.compile("/");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("\\d+");

    private final AuthorTypeMapper authorTypeMapper = new AuthorTypeMapper();

    private final PartialDateParser partialDateParser = new PartialDateParser();

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public DnbSearchEngine(@NonNull final Context appContext,
                           @NonNull final SearchEngineConfig config) {
        super(appContext, config);
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
            // Not verified for multi-results
            final Element table = document.selectFirst("div.l-catalog-single-content");
            if (table != null) {
                parse(context, document, fetchCovers, book);
            }
        }

        return book;
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
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

        final Locale locale = getLocale(context);

        final Element titleElement = document
                .selectFirst("h3.c-catalog-result__ueberschrift > span");
        if (titleElement != null) {
            processTitle(titleElement, book);
        }

        final Element tableElement = document.selectFirst("table.c-catalog-table__table");
        // We could collapse the above select with the select for the tr's
        // But there is a tbody in between, let's take the safe route for now
        if (tableElement != null) {
            final Elements trs = tableElement.select("tr.c-catalog-table__row");
            for (final Element tr : trs) {
                final Element label = tr.selectFirst("th.c-catalog-table__head > p");
                if (label != null) {
                    final Element td = tr.selectFirst("td.c-catalog-table__content > p");
                    if (td != null) {
                        final String s = label.text();
                        // We should get the german labels, but it seems we might get the
                        // english ones despite the "DE" in the url. So... check on both!
                        switch (s) {
                            case "Titel":
                            case "Title":
                                if (!book.contains(DBKey.TITLE)) {
                                    processTitle(td, book);
                                }
                                break;
                            case "Beteiligt":
                            case "Involved":
                                processAuthor(context, td, book);
                                break;
                            case "Erschienen":
                            case "Published":
                                processPublisher(td, book);
                                break;
                            case "Umfang":
                            case "Extent":
                                processPageNumber(td, book);
                                break;
                            case "ISBN":
                                processIsbn(td, book);
                                break;
                            case "Sprache":
                            case "Language":
                                final String lang = ServiceLocator
                                        .getInstance().getLanguages()
                                        .getISO3FromDisplayLanguage(context, locale, td.text());
                                book.putString(DBKey.LANGUAGE, lang);
                                break;
                            case "Genre":
                                // there is also:
                                // Themen­gebiet / Topic
                                // Thema / Subject
                                if (!book.contains(DBKey.GENRE)) {
                                    book.putString(DBKey.GENRE, td.text());
                                }
                                break;
                            case "Werk":
                            case "Work":
                                // This looks like it's the original title for a translated book
                                book.putString(DBKey.TITLE_ORIGINAL_LANG, td.text());
                                break;

                            case "Reihe":
                            case "Series":
                                processSeries(td, book);
                                break;
                        }
                    }
                }
            }

            if (isCancelled()) {
                return;
            }

            if (fetchCovers[0]) {
                final String isbn = book.getString(DBKey.BOOK_ISBN);
                parseCovers(context, document, isbn, 0).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
            }
        }
    }

    /**
     * The title data from the site is very bad... as a library site they should be ashamed :(
     * <p>
     * 978-3-453-32189-2
     * Nemesis : Roman / Isaac Asimov ; deutsche Übersetzung von Irene Holicki
     * 978-3-426-22668-1
     * Totholz : Was vergraben ist, ist nicht vergessen. Kriminalroman | Nummer 1 SPIEGEL Bestseller-Autor / Andreas Föhr
     * 978-3-453-44215-3
     * Wenn sie wüsste : Thriller / Freida McFadden ; aus dem Amerikanischen von Astrid Gravert und Renate Weitbrecht
     * 978-3-551-03302-4
     * Das kleine Wir im Kindergarten / Daniela Kunkel
     * 978-3-499-01410-9
     * Brüssel sehen und sterben : wie ich im Europaparlament meinen Glauben an (fast) alles verloren habe / Nico Semsrott
     * 978-3-96584-423-0
     * Der Glukose-Masterplan / Ernährungs-Doc Dr. med. Matthias Riedl ; mit Texten von Franziska Pfeiffer und Rezepten von Inga Pfannebecker
     */
    private void processTitle(@NonNull final Element element,
                              @NonNull final Book book) {

        String text = element.text();
        if (text.contains("/")) {
            text = PATTERN_SLASH.split(text)[0];
        }
        if (text.contains("|")) {
            text = PATTERN_BAR.split(text)[0];
        }

        text = text.strip();
        for (final String suffix : TITLE_SUFFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length() - 1).strip();
            }
        }

        book.putString(DBKey.TITLE, text);
    }

    private void processIsbn(@NonNull final Element td,
                             @NonNull final Book book) {
        if (!book.contains(DBKey.BOOK_ISBN)) {
            final String digits = SearchEngineUtils.isbn(td.text());
            // (don't do a full ISBN test here, no need)
            if (digits != null && (digits.length() == 10 || digits.length() == 13)) {
                book.putString(DBKey.BOOK_ISBN, digits);
            }
        }
    }

    private void processAuthor(final Context context,
                               @NonNull final Element td,
                               @NonNull final Book book) {
        final Iterator<Element> it = td.children().iterator();
        while (it.hasNext()) {
            @Nullable
            Element e = it.next();

            while (e != null && e.nameIs("a")) {
                final String name = e.text();
                if (it.hasNext()) {
                    // The tag AFTER the "a" can be a "<small>" or a "<br>"
                    e = it.next();
                    if (e.nameIs("small")) {
                        String authorTypeText = e.text();
                        // Sanity check, this is always true ... flw
                        if (authorTypeText.startsWith("(") && authorTypeText.endsWith(")")
                            && authorTypeText.length() > 3) {
                            authorTypeText = authorTypeText
                                    .substring(1, authorTypeText.length() - 1);
                        }
                        @Author.Type
                        final int authorType = authorTypeMapper
                                .map(getLocale(context), authorTypeText);
                        processAuthor(Author.from(name), authorType, book);
                        if (it.hasNext()) {
                            // The tag AFTER the "small" can be a "br" or an "a"
                            e = it.next();
                            continue;
                        }
                    }
                    if (e.nameIs("br")) {
                        processAuthor(Author.from(name), Author.TYPE_UNKNOWN, book);
                        if (it.hasNext()) {
                            e = it.next();
                            continue;
                        }
                    }
                }
                // unknown or no more tags,
                e = null;
            }
        }
    }

    private void processSeries(@NonNull final Element td,
                               @NonNull final Book book) {
        final String text = td.text();
        if (text.contains(" ; ")) {
            final String[] split = PATTERN_SERIES_NR.split(text);
            final Series series = Series.from(split[0]);
            series.setNumber(split[1].strip());
            book.add(series);
        } else {
            book.add(Series.from(text));
        }
    }

    /**
     * <pre>
     *     "München: Knaur
     *     2024"
     *   ==> "2024"
     *     "München: Wilhelm Heyne Verlag
     *     01/2023"
     *   ==> "2023-01"
     *     "Schwarzenbek: newart medien & design GbR
     *     [2019]"
     *   ==> "2019"
     *     "Bamberg: Karl-May-Verlag
     *     to be released in November 2024"
     *   ==> "2024"
     *     "Hamburg: Rowohlt Polaris
     *     Mai 2024"
     *   ==> "2024"
     * </pre>
     */
    private void processPublisher(@NonNull final Element td,
                                  @NonNull final Book book) {
        final List<Node> nodes = td.childNodes();
        if (!nodes.isEmpty()) {
            final String publisherName = nodes.get(0).toString().strip();
            if (publisherName.contains(":")) {
                final String[] parts = publisherName.split(":", 2);
                if (parts.length == 2) {
                    book.add(Publisher.from(parts[1]));
                } else {
                    book.add(Publisher.from(parts[0]));
                }
            } else {
                book.add(Publisher.from(publisherName));
            }

            // node.get(1) should be the br tag which we skip.

            if (nodes.size() > 2) {
                final String dateStr = nodes.get(2).toString().strip();
                partialDateParser.parse(dateStr, false).ifPresent(book::setPublicationDate);
            }
        }
    }

    /**
     * Patterns seen:
     * <pre>
     * "123 Seiten"
     * "182 Seiten : Illustrationen"
     * "Online-Ressource, 224 Seiten"
     * </pre>
     * We grab the first set of digit's we find.
     */
    private void processPageNumber(@NonNull final Element td,
                                   @NonNull final Book book) {

        final Matcher matcher = PAGE_NUMBER_PATTERN.matcher(td.text());
        if (matcher.find()) {
            book.putString(DBKey.PAGE_COUNT, matcher.group());
        }
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
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
    @VisibleForTesting
    @NonNull
    private Optional<String> parseCovers(@NonNull final Context context,
                                         @NonNull final Document document,
                                         @Nullable final String bookId,
                                         @SuppressWarnings("SameParameterValue")
                                         @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        final Element image = document.selectFirst(
                "div.c-catalog-result__image-wrapper > div > span > img");
        if (image != null) {
            String url = image.attr("src");
            if (!url.isBlank()) {
                // sanity check
                if (!url.startsWith("https")) {
                    url = getHostUrl(context) + url;
                }
                return saveImage(context, url, bookId, cIdx, null);
            }
        }

        return Optional.empty();
    }
}
