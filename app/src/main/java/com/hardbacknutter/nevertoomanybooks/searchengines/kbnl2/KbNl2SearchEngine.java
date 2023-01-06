/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl2;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 */
public class KbNl2SearchEngine
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
     * Response with Dutch or English labels.
     * /LNG=NE (default)
     * /LNG=EN
     * <p>
     * 2023-01-05
     */
    private static final String SEARCH_URL =
            "/cbs" +
            "/DB=2.37" +
            // the set number normally goes up for each search; but we only go
            // through a single search/session, so we can just use '1'
            "/SET=1" +
            "/TTL=1" +
            "/CMD?"
            + "ACT=SRCHA&"
            + "IKT=1007&"
            // Year of publication
            // "SRT=LST_Ya&"
            // Relevance
            + "SRT=RLV&"
            // param 1: isbn
            + "TRM=%1$s";

    private static final String BOOK_URL = "/cbs/DB=2.37/SET=1/TTL=1/";

    @Nullable
    private String tmpSeriesNr;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public KbNl2SearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        ServiceLocator.getInstance().getCookieManager();

        final Bundle bookData = ServiceLocator.newBundle();

        final String url = getHostUrl() + String.format(SEARCH_URL, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            final Element titleList = document.selectFirst("div.titlelist");
            if (titleList != null) {
                parseMultiResult(context, titleList, fetchCovers, bookData);
            } else {
                parse(context, document, fetchCovers, bookData);
            }
        }

        if (isCancelled()) {
            return bookData;
        }

        if (fetchCovers[0]) {
            final ArrayList<String> list = searchBestCoverByIsbn(context, validIsbn, 0);
            if (!list.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
        return bookData;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param titleList   to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param bookData    Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Element titleList,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Bundle bookData)
            throws StorageException, SearchException, CredentialsException {

        final Element a = titleList.selectFirst("td.rec_title > div > a");
        if (a != null) {
            final String href = a.attr("href");
            if (!href.isEmpty()) {
                final String url = getHostUrl() + BOOK_URL + href;
                final Document document = loadDocument(context, url, null);
                if (!isCancelled()) {
                    parse(context, document, fetchCovers, bookData);
                }
            }
        }
    }

    @Override
    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Bundle bookData)
            throws StorageException, SearchException, CredentialsException {
        super.parse(context, document, fetchCovers, bookData);

        final Elements trs = document.select("table[summary='title presentation'] > tr");
        for (final Element tr : trs) {
            final Element label = tr.selectFirst("td.rec_lable > div > span");
            if (label != null) {
                final Element td = tr.selectFirst("td.rec_title");
                if (td != null) {
                    switch (label.text().trim()) {
                        case "Titel:":
                            processTitle(td, bookData);
                            break;

                        case "Auteur:":
                            processAuthor(td, Author.TYPE_WRITER);
                            break;
                        case "Medewerker:":
                            processAuthor(td, Author.TYPE_CONTRIBUTOR);
                            break;
                        case "Kunstenaar :":
                            processAuthor(td, Author.TYPE_ARTIST);
                            break;

                        case "Colorist:":
                            processAuthor(td, Author.TYPE_COLORIST);
                            break;
                        case "Vertaler:":
                            processAuthor(td, Author.TYPE_TRANSLATOR);
                            break;

                        case "Reeks:":
                            processSeries(td);
                            break;

                        case "Deel / delen:":
                            // This label can appear without there being a "Series" label.
                            // In that case, the book title is presumed to also be the Series title.
                            processSeriesNumber(td, bookData);
                            break;

                        case "Uitgever:":
                            processPublisher(td);
                            break;

                        case "Jaar:":
                            processDatePublished(td, bookData);
                            break;

                        case "Omvang:":
                            processPages(td, bookData);
                            break;

                        case "ISBN:":
                            processIsbn(td, bookData);
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
    }

    private void processSeries(@NonNull final Element td) {
        final Element span = td.selectFirst("span");
        if (span != null) {
            seriesList.add(Series.from(span.text(), tmpSeriesNr));
            tmpSeriesNr = null;
        }
    }

    private void processSeriesNumber(@NonNull final Element td,
                                     @NonNull final Bundle bookData) {
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
                             @NonNull final Bundle bookData) {
        if (!bookData.containsKey(DBKey.BOOK_ISBN)) {
            final Elements spans = td.select("span");
            if (!spans.isEmpty()) {
                bookData.putString(DBKey.BOOK_ISBN, digits(spans.get(0).text(), true));
                if (spans.size() > 1) {
                    if (!bookData.containsKey(DBKey.FORMAT)) {
                        String format = spans.get(1).text();
                        if (format.startsWith("(")) {
                            format = format.substring(1, format.length() - 1);
                        }
                        if (!format.isEmpty()) {
                            bookData.putString(DBKey.FORMAT, format);
                        }
                    }
                }
            }
        }
    }

    private void processTitle(@NonNull final Element td,
                              @NonNull final Bundle bookData) {
        final Element a = td.selectFirst("a");
        if (a != null) {
            String[] cleanedData = a.text().split("/");

            bookData.putString(DBKey.TITLE, cleanedData[0].trim());
            if (cleanedData.length > 1) {
                cleanedData = cleanedData[1].split(";");
                final Author author = Author.from(cleanedData[0]);
                // for books this is the writer, but for comics it's unknown.
                // So we do NOT set a type here
                authorList.add(author);

                // TODO: extract the translator
//            if (cleanedData.length > 1) {
//                if (cleanedData[1].startsWith(" [vert.")) {
//
//                }
//            }
            }
        }
    }

    private void processAuthor(@NonNull final Element td,
                               final int type) {
        final Elements aas = td.select("a");
        if (!aas.isEmpty()) {
            for (final Element a : aas) {
                // remove a year part in the name
                final String cleanedString = a.text().split("\\(")[0].trim();
                // reject separators as for example: <psi:text>;</psi:text>
                if (cleanedString.length() == 1) {
                    return;
                }

                final Author author = Author.from(cleanedString);
                author.setType(type);
                authorList.add(author);
            }
        }
    }

    private void processPublisher(@NonNull final Element td) {
        final Elements aas = td.select("a");
        if (!aas.isEmpty()) {
            final StringBuilder sbPublisher = new StringBuilder();
            for (final Element a : aas) {
                final String text = a.text();
                if (!text.isEmpty()) {
                    sbPublisher.append(text).append(" ");
                }
            }
            String publisherName = sbPublisher.toString();
            if (publisherName.contains(":")) {
                publisherName = publisherName.split(":")[1].trim();
            }
            publisherList.add(Publisher.from(publisherName));
        }
    }

    private void processDatePublished(@NonNull final Element td,
                                      @NonNull final Bundle bookData) {
        if (!bookData.containsKey(DBKey.BOOK_PUBLICATION__DATE)) {
            final Element span = td.selectFirst("span");
            if (span != null) {
                final String year = digits(span.text(), false);
                if (year != null && !year.isEmpty()) {
                    bookData.putString(DBKey.BOOK_PUBLICATION__DATE, year);
                }
            }
        }
    }

    private void processPages(@NonNull final Element td,
                              @NonNull final Bundle bookData) {
        if (!bookData.containsKey(DBKey.PAGE_COUNT)) {
            final Element span = td.selectFirst("span");
            if (span != null) {
                final String pagesStr = span.text().split(" ")[0];
                try {
                    final int pages = Integer.parseInt(pagesStr);
                    bookData.putString(DBKey.PAGE_COUNT, String.valueOf(pages));
                } catch (@NonNull final NumberFormatException e) {
                    // use source
                    bookData.putString(DBKey.PAGE_COUNT, pagesStr);
                }
            }
        }
    }

    /**
     * Filter a string of all non-digits. Used to clean isbn strings, years... etc.
     *
     * @param s      string to parse
     * @param isIsbn When set will also allow 'X' and 'x'
     *
     * @return stripped string
     */
    @Nullable
    private String digits(@Nullable final CharSequence s,
                          final boolean isIsbn) {
        if (s == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // allows an X anywhere instead of just at the end;
            // Doesn't really matter, we'll check for being a valid ISBN later anyhow.
            if (Character.isDigit(c) || isIsbn && (c == 'X' || c == 'x')) {
                sb.append(c);
            }
        }
        return sb.toString();
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
        return saveImage(url, validIsbn, cIdx, size);
    }
}
