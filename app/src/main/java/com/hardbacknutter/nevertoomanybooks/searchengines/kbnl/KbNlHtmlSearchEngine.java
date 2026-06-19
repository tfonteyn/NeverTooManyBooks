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
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.ProductCode;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>.
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>.
 */
public class KbNlHtmlSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.CoverByEdition {

    /**
     * <strong>Note:</strong> This is not the same site as the search site itself.
     * We have no indication that this site has an image we want, we just try it.
     * <p>
     * param 1: isbn, param 2: size.
     */
    private static final String BASE_URL_COVERS =
            "https://webservices.bibliotheek.be/index.php?func=cover&ISBN=%1$s&coversize=%2$s";

    /**
     * Search by code.
     * <p>
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
     * Fetch a book.
     * <p>
     * param 1: db version (part of the site session vars).
     * param 2: the set number (part of the site session params)
     * Param 3: the SHW part of the url as found in a multi-result
     */
    private static final String BOOK_URL = "/cbs/DB=%1$s/SET=%2$s/TTL=1/%3$s";

    @Nullable
    private String tmpSeriesNr;

    @NonNull
    private String dbVersion = "2.37";
    @NonNull
    private String setNr = "1";

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
    public KbNlHtmlSearchEngine(@NonNull final Context appContext,
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
        return KbNlSearchEngine.init();
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

    /**
     * Send a HEAD request to prepare a cookie for further calls.
     *
     * @throws SearchException on any error
     */
    private void ensureCookie()
            throws SearchException {
        final FutureHttp<Boolean> httpHead = createHeadRequest();
        try {
            httpHead.head(getHostUrl() + "/cbs/", con -> true);
        } catch (@NonNull final StorageException | IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        ensureCookie();

        final String codeStr = SearchEngineUtils.formatIsbn(getEngineId(), productCode);

        final Book book = new Book();

        final String url = getHostUrl() + String.format(SEARCH_URL,
                                                        dbVersion, setNr, codeStr);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            final Element titleList = document.selectFirst("div.titlelist");
            if (titleList != null) {
                parseMultiResult(context, titleList, book);
            } else {
                parse(document, book);
            }
        }

        if (isCancelled()) {
            return book;
        }

        if (fetchCovers[0]) {
            final AltEdition edition = new AltEditionIsbn(codeStr);
            searchBestCoverByEdition(context, edition, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context   Current context
     * @param titleList to parse
     * @param book      to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @WorkerThread
    @VisibleForTesting
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Element titleList,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Element a = titleList.selectFirst("td.rec_title > div > a");
        if (a == null) {
            return;
        }
        final String show = a.attr("href");
        if (show.isEmpty()) {
            return;
        }

        final String url = getHostUrl() + String.format(BOOK_URL, dbVersion, setNr, show);
        final Document redirected = loadDocument(context, url, null);
        if (!isCancelled()) {
            parse(redirected, book);
        }
    }

    /**
     * Parse the downloaded {@link org.jsoup.nodes.Document} for a single Book.
     *
     * @param document to parse
     * @param book     to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Document document,
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
                            parseAuthor(td, AuthorRole.WRITER, book);
                            break;
                        case "Medewerker:":
                            parseAuthor(td, AuthorRole.CONTRIBUTOR, book);
                            break;
                        case "Kunstenaar:":
                            parseAuthor(td, AuthorRole.ARTIST, book);
                            break;

                        case "Colorist:":
                            parseAuthor(td, AuthorRole.COLORIST, book);
                            break;
                        case "Vertaler:":
                            parseAuthor(td, AuthorRole.TRANSLATOR, book);
                            break;

                        case "Reeks:":
                            processSeries(td, book);
                            break;

                        case "Deel / delen:":
                            processSeriesNumber(td);
                            break;

                        case "Uitgever:":
                            parsePublisher(td, book);
                            break;

                        case "Jaar:":
                            processDatePublished(td, book);
                            break;

                        case "Omvang:":
                            processPages(td, book);
                            break;

                        case "ISBN:":
                            parseIsbn(td, book);
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
                final String s = cleanName(title);
                if (!s.isBlank()) {
                    book.add(Series.from(s, tmpSeriesNr));
                }
            }
        }

        // There is no language field; e.g. French books data is the same as Dutch ones.
        // just add Dutch and hope for the best.
        if (!book.contains(DBKey.LANGUAGE)) {
            book.setLanguage("nld");
        }
    }

    private void processTitle(@NonNull final Element td,
                              @NonNull final Book book) {
        final Element a = td.selectFirst("a");
        if (a == null) {
            return;
        }
        final String[] cleanedData = a.text().split("/");
        final String s = cleanText(cleanedData[0]);
        if (!s.isBlank()) {
            book.setTitle(s);
        }
        // It's temping to decode cleanedData[1],
        // but the data has proven to be very unstructured and mostly unusable.
    }

    private void parseAuthor(@NonNull final Element td,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            return;
        }
        for (final Element a : aas) {
            // remove a year part in the name
            String s = a.text().split("\\(")[0].strip();
            // reject separators as for example: <psi:text>;</psi:text>
            if (s.length() == 1) {
                return;
            }

            s = cleanName(s);
            if (!s.isBlank()) {
                addAuthor(Author.from(s), type, book);
            }
        }
    }

    private void processSeries(@NonNull final Element td,
                               @NonNull final Book book) {
        final Element span = td.selectFirst("span");
        if (span == null) {
            return;
        }
        // Note how this is different from the psi result
        final String s = cleanName(span);
        if (!s.isBlank()) {
            book.add(Series.from(s, tmpSeriesNr));
        }
        tmpSeriesNr = null;
    }

    private void processSeriesNumber(@NonNull final Element td) {
        // This element is listed BEFORE the Series ("reeks") itself so store it tmp.
        final Element span = td.selectFirst("span");
        if (span == null) {
            return;
        }
        final String[] nrStr = span.text().split("/")[0].split(" ");
        if (nrStr.length > 1) {
            tmpSeriesNr = nrStr[1];
        } else {
            tmpSeriesNr = nrStr[0];
        }
    }

    private void parseIsbn(@NonNull final Element td,
                           @NonNull final Book book) {
        if (book.hasIsbn()) {
            return;
        }
        final Elements spans = td.select("span");
        if (spans.isEmpty()) {
            return;
        }
        // oh boy... aside of actual/valid ISBN numbers we've also seen things like
        // " : 42.00F"
        final String isbnText = ISBN.cleanText(spans.get(0).text());
        // so we do a crude test on the length and hope for the best
        // (don't do a full ISBN test here, no need)
        if (isbnText.length() == 10 || isbnText.length() == 13) {
            book.setIsbn(isbnText);
        }
        if (spans.size() > 1) {
            if (!book.contains(DBKey.FORMAT)) {
                String format = spans.get(1).text();
                if (format.startsWith("(")) {
                    format = format.substring(1, format.length() - 1);
                }
                book.setFormat(format);
            }
        }
    }

    private void parsePublisher(@NonNull final Element td,
                                @NonNull final Book book) {
        final Elements spans = td.select("span");
        if (spans.isEmpty()) {
            return;
        }
        String text = spans.stream()
                           .map(Element::text)
                           .filter(name -> !name.isEmpty())
                           .collect(Collectors.joining(" "));
        // the part before the ":" is (usually?) the city. 2nd part is the name
        if (text.contains(":")) {
            text = text.split(":")[1].strip();
        }
        text = cleanName(text);
        if (!text.isBlank()) {
            book.add(Publisher.from(text));
        }
    }

    private void processDatePublished(@NonNull final Element td,
                                      @NonNull final Book book) {
        if (book.contains(DBKey.PUBLICATION_DATE)) {
            return;
        }
        final Element span = td.selectFirst("span");
        if (span == null) {
            return;
        }
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

    private void processPages(@NonNull final Element td,
                              @NonNull final Book book) {
        if (book.contains(DBKey.PAGES)) {
            return;
        }
        final Element span = td.selectFirst("span");
        if (span == null) {
            return;
        }
        final String pagesStr = span.text().split(" ")[0];
        try {
            book.setPages(Integer.parseInt(pagesStr));
        } catch (@NonNull final NumberFormatException e) {
            // use source
            book.setPages(pagesStr);
        }
    }


    /**
     * Wrapper for {@link #searchCoverByEdition(Context, AltEdition, int, ImageWebSize)}.
     * <p>
     * Try to get an image in order of large, medium, small.
     * i.e. the 'best' image being the largest we can find.
     *
     * @param context Current context
     * @param edition to search for
     * @param cIdx    0..n image index
     *
     * @return fileSpec
     *
     * @throws CoverStorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> searchBestCoverByEdition(@NonNull final Context context,
                                                      @NonNull final AltEdition edition,
                                                      @IntRange(from = 0, to = 0) final int cIdx)
            throws CoverStorageException {

        Optional<String> oFileSpec = searchCoverByEdition(context, edition, cIdx,
                                                          ImageWebSize.Large);
        if (oFileSpec.isEmpty()) {
            oFileSpec = searchCoverByEdition(context, edition, cIdx, ImageWebSize.Medium);
            if (oFileSpec.isEmpty()) {
                oFileSpec = searchCoverByEdition(context, edition, cIdx, ImageWebSize.Small);
            }
        }
        return oFileSpec;
    }

    /**
     * The kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
     *
     * <br><br>{@inheritDoc}
     *
     * @see #searchBestCoverByEdition(Context, AltEdition, int)
     */
    @NonNull
    @Override
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 0) final int cIdx,
                                                 @Nullable final ImageWebSize size)
            throws CoverStorageException {

        if (altEdition instanceof AltEditionIsbn) {
            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

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

            final String url = String.format(BASE_URL_COVERS, isbn, sizeParam);
            return saveImage(context, url, null, isbn, cIdx, size);
        }
        return Optional.empty();
    }
}
