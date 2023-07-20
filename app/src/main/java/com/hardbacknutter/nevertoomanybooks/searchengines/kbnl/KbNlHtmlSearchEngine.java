/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpHead;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 */
public class KbNlHtmlSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.CoverByIsbn {

    /**
     * <strong>Note:</strong> This is not the same site as the search site itself.
     * We have no indication that this site has an image we want, we just try it.
     * <p>
     * param 1: isbn, param 2: size.
     */
    private static final String BASE_URL_COVERS =
            "https://webservices.bibliotheek.be/index.php?func=cover&ISBN=%1$s&coversize=%2$s";

    /**
     * param 1: db version (part of the site session vars)
     * param 2: the set number (part of the site session vars)
     * param 3: the ISBN
     */
    private static final String SEARCH_URL = "/cbs/DB=%1$s/SET=%2$s/TTL=1/CMD?"
                                             // Action is a search
                                             + "ACT=SRCHA&"
                                             // by ISBN/ISSN
                                             + "IKT=1007&"
                                             // Results sorted by Relevance
                                             + "SRT=RLV&"
                                             // search term
                                             + "TRM=%3$s";

    /**
     * param 1: db version (part of the site session vars)
     * param 2: the set number (part of the site session params)
     * Param 3: the SHW part of the url as found in a multi-result
     */
    private static final String BOOK_URL = "/cbs/DB=%1$s/SET=%2$s/TTL=1/%3$s";

    @Nullable
    private String tmpSeriesNr;

    @Nullable
    private FutureHttpHead<Boolean> futureHttpHead;

    @NonNull
    private String dbVersion = "2.37";
    @NonNull
    private String setNr = "1";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public KbNlHtmlSearchEngine(@NonNull final Context appContext,
                                @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpHead != null) {
                futureHttpHead.cancel();
            }
        }
    }

    @NonNull
    @Override
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {

        final Document document = super.loadDocument(context, url, requestProperties);

        final Element base = document.selectFirst("head > base");
        // <base href="https://webggc.oclc.org/cbs/xslt/DB=2.37/SET=1/TTL=1/">
        if (base == null) {
            throw new SearchException(getEngineId(), "no base element?", null);
        }

        for (final String part : base.attr("href").split("/")) {
            if (part.startsWith("DB=")) {
                dbVersion = part.split("=")[1];
            } else if (part.startsWith("SET=")) {
                setNr = part.split("=")[1];
            }
        }

        return document;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        futureHttpHead = createFutureHeadRequest(context);
        try {
            futureHttpHead.send(getHostUrl(context) + "/cbs/", con -> true);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(SEARCH_URL,
                                                               dbVersion, setNr, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            final Element titleList = document.selectFirst("div.titlelist");
            if (titleList != null) {
                parseMultiResult(context, titleList, fetchCovers, book);
            } else {
                parse(context, document, fetchCovers, book);
            }
        }

        if (isCancelled()) {
            return book;
        }

        if (fetchCovers[0]) {
            book.setCoverFileSpecList(0, searchBestCoverByIsbn(context, validIsbn, 0));
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param titleList   to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    @VisibleForTesting
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Element titleList,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Element a = titleList.selectFirst("td.rec_title > div > a");
        if (a != null) {
            final String show = a.attr("href");
            if (!show.isEmpty()) {
                final String url = getHostUrl(context) + String.format(BOOK_URL, dbVersion, setNr,
                                                                       show);
                final Document document = loadDocument(context, url, null);
                if (!isCancelled()) {
                    parse(context, document, fetchCovers, book);
                }
            }
        }
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

        tmpSeriesNr = null;

        final Elements trs = document.select("table[summary='title presentation'] tr");
        for (final Element tr : trs) {
            final Element label = tr.selectFirst("td.rec_lable > div > span");
            if (label != null) {
                final Element td = tr.selectFirst("td.rec_title");
                if (td != null) {
                    final String s = label.text().strip();
                    switch (s) {
                        case "Titel:":
                            processTitle(td, book);
                            break;

                        case "Auteur:":
                            processAuthor(td, Author.TYPE_WRITER, book);
                            break;
                        case "Medewerker:":
                            processAuthor(td, Author.TYPE_CONTRIBUTOR, book);
                            break;
                        case "Kunstenaar:":
                            processAuthor(td, Author.TYPE_ARTIST, book);
                            break;

                        case "Colorist:":
                            processAuthor(td, Author.TYPE_COLORIST, book);
                            break;
                        case "Vertaler:":
                            processAuthor(td, Author.TYPE_TRANSLATOR, book);
                            break;

                        case "Reeks:":
                            processSeries(td, book);
                            break;

                        case "Deel / delen:":
                            processSeriesNumber(td);
                            break;

                        case "Uitgever:":
                            processPublisher(td, book);
                            break;

                        case "Jaar:":
                            processDatePublished(td, book);
                            break;

                        case "Omvang:":
                            processPages(td, book);
                            break;

                        case "ISBN:":
                            processIsbn(td, book);
                            break;

                        case "Illustratie:":
                            // e.g.: gekleurde illustraties
                            //TODO: extract color
                            break;

                        case "Formaat:":
                            // e.g.: Formaat: 30 cm
                            // instead we get the format from the ISBN line
                            break;

                        case "Editie:":
                            // e.g.: Eerste druk
                            // e.g.: [2e dr.]
                            break;

                        case "Annotatie editie:":
                            // e.g.: Omslag vermeldt: K2
                            // e.g.: Opl. van 750 genummerde ex
                            // e.g.: Vert. van: Cromwell Stone. - Delcourt, cop. 1993
                            break;

                        case "Noot:":
                            break;

                        case "Bĳlage:":
                            // e.g.: kleurenprent van oorspr. cover
                            break;

                        case "Trefwoord Depot:":
                            // not used
                        case "Aanvraagnummer:":
                            // not used
                        case "Uitleenindicatie:":
                            // not used
                        case "Aanvraaginfo:":
                            // not used
                            break;

                        default:
                            // ignore
                            break;
                    }
                }
            }
        }

        if (tmpSeriesNr != null) {
            final String title = book.getString(DBKey.TITLE, null);
            // should never happen, but paranoia...
            if (title != null && !title.isBlank()) {
                final Series series = Series.from(title, tmpSeriesNr);
                book.add(series);
            }
        }

        // There is no language field; e.g. french books data is the same as dutch ones.
        // just add Dutch and hope for the best.
        if (!book.contains(DBKey.LANGUAGE)) {
            book.putString(DBKey.LANGUAGE, "nld");
        }
    }

    private void processTitle(@NonNull final Element td,
                              @NonNull final Book book) {
        final Element a = td.selectFirst("a");
        if (a != null) {
            final String[] cleanedData = a.text().split("/");
            book.putString(DBKey.TITLE, cleanedData[0].trim());
            // It's temping to decode [1,
            // but the data has proven to be very unstructured and mostly unusable.
        }
    }

    private void processAuthor(@NonNull final Element td,
                               final int type,
                               @NonNull final Book book) {
        final Elements aas = td.select("a");
        if (!aas.isEmpty()) {
            for (final Element a : aas) {
                // remove a year part in the name
                final String cleanedString = a.text().split("\\(")[0].trim();
                // reject separators as for example: <psi:text>;</psi:text>
                if (cleanedString.length() == 1) {
                    return;
                }

                processAuthor(Author.from(cleanedString), type, book);
            }
        }
    }

    private void processSeries(@NonNull final Element td,
                               @NonNull final Book book) {
        final Element span = td.selectFirst("span");
        if (span != null) {
            // Note how this is different from the psi result
            book.add(Series.from(span.text(), tmpSeriesNr));
            tmpSeriesNr = null;
        }
    }

    private void processSeriesNumber(@NonNull final Element td) {
        // This element is listed BEFORE the Series ("reeks") itself so store it tmp.
        final Element span = td.selectFirst("span");
        if (span != null) {
            final String[] nrStr = span.text().split("/")[0].split(" ");
            if (nrStr.length > 1) {
                tmpSeriesNr = nrStr[1];
            } else {
                tmpSeriesNr = nrStr[0];
            }
        }
    }

    private void processIsbn(@NonNull final Element td,
                             @NonNull final Book book) {
        if (!book.contains(DBKey.BOOK_ISBN)) {
            final Elements spans = td.select("span");
            if (!spans.isEmpty()) {
                // oh boy... aside of actual/valid ISBN numbers we've also seen things like
                // " : 42.00F"
                final String digits = SearchEngineUtils.isbn(spans.get(0).text());
                // so we do a crude test on the length and hope for the best
                // (don't do a full ISBN test here, no need)
                if (digits != null && (digits.length() == 10 || digits.length() == 13)) {
                    book.putString(DBKey.BOOK_ISBN, digits);
                }
                if (spans.size() > 1) {
                    if (!book.contains(DBKey.FORMAT)) {
                        String format = spans.get(1).text();
                        if (format.startsWith("(")) {
                            format = format.substring(1, format.length() - 1);
                        }
                        if (!format.isEmpty()) {
                            book.putString(DBKey.FORMAT, format);
                        }
                    }
                }
            }
        }
    }

    private void processPublisher(@NonNull final Element td,
                                  @NonNull final Book book) {
        final Elements spans = td.select("span");
        if (!spans.isEmpty()) {
            String publisherName = spans.stream()
                                        .map(Element::text)
                                        .filter(text -> !text.isEmpty())
                                        .collect(Collectors.joining(" "));
            if (publisherName.contains(":")) {
                publisherName = publisherName.split(":")[1].trim();
            }
            book.add(Publisher.from(publisherName));
        }
    }

    private void processDatePublished(@NonNull final Element td,
                                      @NonNull final Book book) {
        if (!book.contains(DBKey.BOOK_PUBLICATION__DATE)) {
            final Element span = td.selectFirst("span");
            if (span != null) {
                // It's not good... we've seen some different notations.
                // e.g.:  [2019]
                // e.g.:  c1977, cover 1978
                // Grab the first bit before a comma, and strip it for digits + hope for the best
                final String year = SearchEngineUtils.digits(span.text().split(",")[0]);
                if (!year.isEmpty()) {
                    try {
                        book.setPublicationDate(Integer.parseInt(year));
                    } catch (@NonNull final NumberFormatException ignore) {
                        // ignore
                    }
                }
            }
        }
    }

    private void processPages(@NonNull final Element td,
                              @NonNull final Book book) {
        if (!book.contains(DBKey.PAGE_COUNT)) {
            final Element span = td.selectFirst("span");
            if (span != null) {
                final String pagesStr = span.text().split(" ")[0];
                try {
                    final int pages = Integer.parseInt(pagesStr);
                    book.putString(DBKey.PAGE_COUNT, String.valueOf(pages));
                } catch (@NonNull final NumberFormatException e) {
                    // use source
                    book.putString(DBKey.PAGE_COUNT, pagesStr);
                }
            }
        }
    }

    /**
     * Ths kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
     *
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String searchCoverByIsbn(@NonNull final Context context,
                                    @NonNull final String validIsbn,
                                    @IntRange(from = 0, to = 1) final int cIdx,
                                    @Nullable final Size size)
            throws StorageException {
        final String sizeParam;
        if (size == null) {
            sizeParam = "large";
        } else {
            switch (size) {
                case Small:
                    sizeParam = "small";
                    break;
                case Medium:
                    sizeParam = "medium";
                    break;
                case Large:
                default:
                    sizeParam = "large";
                    break;
            }
        }

        final String url = String.format(BASE_URL_COVERS, validIsbn, sizeParam);
        return saveImage(context, url, validIsbn, cIdx, size);
    }
}
