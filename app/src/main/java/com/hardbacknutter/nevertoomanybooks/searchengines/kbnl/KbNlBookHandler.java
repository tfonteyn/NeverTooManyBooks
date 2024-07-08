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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;

import org.xml.sax.SAXException;

class KbNlBookHandler
        extends KbNlHandlerBase {

    private static final Pattern ISBN_BOUNDARY_PATTERN = Pattern.compile("[;)]");

    @NonNull
    private final KbNlSearchEngine searchEngine;

    @Nullable
    private String tmpSeriesNr;

    /**
     * Constructor.
     *
     * @param kbNlSearchEngine engine
     * @param data             Book to update
     */
    KbNlBookHandler(@NonNull final KbNlSearchEngine kbNlSearchEngine,
                    @NonNull final Book data) {
        super(data);
        searchEngine = kbNlSearchEngine;
    }

    @Override
    public void startDocument()
            throws SAXException {
        super.startDocument();

        tmpSeriesNr = null;
    }

    @Override
    public void endDocument()
            throws SAXException {
        super.endDocument();

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
        if (!book.isEmpty() && !book.contains(DBKey.LANGUAGE)) {
            book.putString(DBKey.LANGUAGE, "nld");
        }
    }

    /**
     * Labels for both Dutch (default) and English are listed.
     * <p>
     * Note that "Colorist" is also used in Dutch.
     *
     * @param currentLabel the current {@code labelledLabel}
     * @param currentData  content of {@code labelledData}
     */
    protected void processEntry(@NonNull final String currentLabel,
                                @NonNull final List<String> currentData) {
        switch (currentLabel) {
            case "Title":
            case "Titel":
                processTitle(currentData);
                break;

            case "Author":
            case "Auteur":
                parseAuthor(currentData, Author.TYPE_WRITER);
                break;

            case "Collaborator":
            case "Medewerker":
                parseAuthor(currentData, Author.TYPE_CONTRIBUTOR);
                break;

            case "Artist":
            case "Kunstenaar":
                // artist is for comics etc
            case "Illustrator":
                // illustrator (label is same in dutch) is for books
                // Just put them both down as artists
                parseAuthor(currentData, Author.TYPE_ARTIST);
                break;

            case "Colorist":
                parseAuthor(currentData, Author.TYPE_COLORIST);
                break;
            case "Translator":
            case "Vertaler":
                parseAuthor(currentData, Author.TYPE_TRANSLATOR);
                break;

            case "Series":
            case "Reeks":
                processSeries(currentData);
                break;

            case "Part(s)":
            case "Deel / delen":
                processSeriesNumber(currentData);
                break;

            case "Publisher":
            case "Uitgever":
                parsePublisher(currentData);
                break;

            case "Year":
            case "Jaar":
                parseDatePublished(currentData);
                break;

            case "Extent":
            case "Omvang":
                processPages(currentData);
                break;

            case "ISBN":
                parseIsbn(currentData);
                break;

            case "Illustration":
            case "Illustratie":
                processIllustration(currentData);
                break;

            case "Size":
            case "Formaat":
                // seen, but skipped for now; it's one dimension, presumably the height.
                // Formaat: 30 cm
                break;

            case "Physical information":
            case "Fysieke informatie":
                // "1 file (PDF)"
                break;

            case "Editie":
                // [2e dr.]
                break;

            case "Contains":
            case "Bevat / omvat":
                parseDescription(currentData);
                break;

            case "Note":
            case "Annotatie":
                // Note/Annotatie seems to have been replaced on newer books by
            case "Annotation edition":
            case "Annotatie editie":
                // Omslag vermeldt: K2
                //Opl. van 750 genummerde ex
                //Vert. van: Cromwell Stone. - Delcourt, cop. 1993
                break;

            //case "Note": in english used a second time for a different field. Unique in dutch.
            case "Noot":
                break;

            case "Annex":
            case "Bĳlage":
                // kleurenprent van oorspr. cover
                break;

            case "Subject heading Depot":
            case "Trefwoord Depot":
                // not used
            case "Request number":
            case "Aanvraagnummer":
                // not used
            case "Loan indication":
            case "Uitleenindicatie":
                // not used
            case "Lending information":
            case "Aanvraaginfo":
                // not used
                break;
            case "Manufacturer":
            case "Vervaardiger":
                // not used
                break;

            default:
                // ignore
                break;
        }
    }

    /**
     * <pre>{@code
     *  <psi:labelledData>
     *    <psi:line>
     *      <psi:text>De</psi:text>
     *      <psi:text href="CLK?IKT=4&amp;TRM=Foundation">Foundation</psi:text>
     *      <psi:text>/ Isaac Asimov ; [vert. uit het Engels door Jack Kröner]</psi:text>
     *    </psi:line>
     *  </psi:labelledData>
     *
     *  <psi:labelledData>
     *    <psi:line>
     *      <psi:text href="CLK?IKT=12&amp;TRM=422449059&amp;REC=*">
     *          De buitengewone reis /  Silvio Camboni
     *      </psi:text>
     *    </psi:line>
     *  </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void processTitle(@NonNull final Iterable<String> currentData) {
        final String[] cleanedData = String.join(" ", currentData).split("/");
        book.putString(DBKey.TITLE, cleanedData[0].strip());
        // It's temping to decode [1], as this is the author as it appears on the cover,
        // but the data has proven to be very unstructured and mostly unusable.
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="REL?PPN=068561504">Isaak Judovič Ozimov (1920-1992)</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="REL?PPN=068870728">Jean-Pol is Jean-Paul Van den Broeck</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="REL?PPN=070047596">Dirk Stallaert (1955-)</psi:text>
     *     <psi:text>;</psi:text>
     *     <psi:text href="REL?PPN=073286796">Leo Loedts</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="REL?PPN=068852002">Ruurd Feenstra (1904-1974)
     *       (ISNI 0000 0000 2173 3650) </psi:text>
     *     </psi:line>
     *   </psi:labelledData>
     * }</pre>
     *
     *
     *
     * <p>
     * Note that the author name in the above example can be the "actual" name, and not
     * the publicly known/used name, i.e. in this case "Isaac Asimov"
     * The 2nd sample shows how the site is creative (hum) about authors using pen-names.
     * The 3rd example has a list of authors for the same author type.
     * The 4th sample shows that dates and other info can be added
     * <p>
     * Getting author names:
     * http://opc4.kb.nl/DB=1/SET=1/TTL=1/REL?PPN=068561504
     *
     * @param currentData content of {@code labelledData}
     * @param type        the author type
     */
    private void parseAuthor(@NonNull final Iterable<String> currentData,
                             @Author.Type final int type) {
        for (final String text : currentData) {
            // remove any "(..)" parts in the name
            final String cleanedString = text.split("\\(")[0].strip();
            // reject separators as for example: <psi:text>;</psi:text>
            if (cleanedString.length() == 1) {
                return;
            }

            searchEngine.addAuthor(Author.from(cleanedString), type, book);
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=12&amp;TRM=841288933&amp;REC=*">Foundation-trilogie</psi:text>
     *     <psi:text>;</psi:text>
     *     <psi:text href="CLK?IKT=12&amp;TRM=841288933&amp;REC=*">dl. 1</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=12&amp;TRM=851863523&amp;REC=*">Discus-serie</psi:text>
     *     <psi:text>; </psi:text>
     *     <psi:text href="CLK?IKT=12&amp;TRM=821611178&amp;REC=*">Kluitman jeugdserie</psi:text>
     *     <psi:text> ; </psi:text>
     *     <psi:text href="CLK?IKT=12&amp;TRM=821611178&amp;REC=*">J 1247</psi:text>
     *   </psi:line>
     *  </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void processSeries(@NonNull final List<String> currentData) {
        book.add(Series.from(currentData.get(0)));
        // the number part is totally unstructured
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>Deel 1 / blah</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void processSeriesNumber(@NonNull final List<String> currentData) {
        // This element is listed BEFORE the Series ("reeks") itself so store it tmp.
        // Note it's often missing altogether
        final String[] nrStr = currentData.get(0).split("/")[0].split(" ");
        if (nrStr.length > 1) {
            tmpSeriesNr = nrStr[1];
        } else {
            tmpSeriesNr = nrStr[0];
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text mark="highlight">90-229-5335-1</psi:text>
     *     <psi:text>(geb.)</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text mark="highlight">978-94-6373145-4</psi:text>
     *     <psi:text> (paperback)</psi:text>
     *   </psi:line>
     *   <psi:line>
     *     <psi:text>&#xA0;</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text> (Geb.; f. 4,50)</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>2-256-90374-5 : 40.00F</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text> : 42.00F</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void parseIsbn(@NonNull final List<String> currentData) {
        for (final String text : currentData) {
            if (Character.isDigit(text.charAt(0))) {
                if (!book.contains(DBKey.BOOK_ISBN)) {
                    final String isbnText = ISBN.cleanText(text.split(":")[0]);
                    // Do a crude test on the length and hope for the best
                    // (don't do a full ISBN test here, no need)
                    if (isbnText.length() == 10 || isbnText.length() == 13) {
                        book.putString(DBKey.BOOK_ISBN, isbnText);
                    }
                }
            } else if (text.charAt(0) == '(') {
                if (!book.contains(DBKey.FORMAT)) {
                    // Skip the 1th bracket, and split either on closing or on semicolon
                    final String value = ISBN_BOUNDARY_PATTERN.split(text.substring(1))[0].strip();
                    if (!value.isBlank()) {
                        book.putString(DBKey.FORMAT, value);
                    }
                }
            }
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Breda">Breda</psi:text>
     *     <psi:text> : </psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Dark">Dark</psi:text>
     *     <psi:text> </psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Dragon">Dragon</psi:text>
     *     <psi:text> </psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Books">Books</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Utrecht">Utrecht</psi:text>
     *     <psi:text>[</psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=etc.">etc.</psi:text>
     *     <psi:text>] :</psi:text>
     *     <psi:text href="CLK?IKT=3003&amp;TRM=Bruna">Bruna</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void parsePublisher(@NonNull final List<String> currentData) {
        String publisherName = currentData.stream()
                                          .filter(name -> !name.isEmpty())
                                          .collect(Collectors.joining(" "));
        // the part before the ":" is (usually?) the city. 2nd part is the publisher
        if (publisherName.contains(":")) {
            publisherName = publisherName.split(":")[1].strip();
        }
        searchEngine.addPublisher(publisherName, book);
    }

    /**
     * Process a year field. Once again the data is not structured, but at least
     * it's guessable. Some examples:
     *
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>1983</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>[2019]</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>cop. 1986</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>c1977, cover 1978</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void parseDatePublished(@NonNull final List<String> currentData) {
        if (!book.contains(DBKey.BOOK_PUBLICATION__DATE)) {
            // Grab the first bit before a comma, and strip it for digits + hope for the best
            final String year = SearchEngineUtils.digits(currentData.get(0).split(",")[0]);
            if (!year.isEmpty()) {
                try {
                    book.setPublicationDate(Integer.parseInt(year));
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * Process the number-of-pages field. Once again the data is not structured, but at least
     * it's guessable. Some examples:
     *
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>48 pagina's</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>156 p</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void processPages(@NonNull final List<String> currentData) {
        if (!book.contains(DBKey.PAGE_COUNT)) {
            try {
                final String cleanedString = currentData.get(0).split(" ")[0];
                final int pages = Integer.parseInt(cleanedString);
                book.putString(DBKey.PAGE_COUNT, String.valueOf(pages));
            } catch (@NonNull final NumberFormatException e) {
                // use source
                book.putString(DBKey.PAGE_COUNT, currentData.get(0));
            }
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>gekleurde illustraties</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     *
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>zw. ill</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     *
     * @param currentData content of {@code labelledData}
     */
    private void processIllustration(@NonNull final List<String> currentData) {
        if (!book.contains(DBKey.COLOR)) {
            book.putString(DBKey.COLOR, currentData.get(0));
        }
    }

    private void parseDescription(@NonNull final List<String> currentData) {
        final String desc = currentData
                .stream()
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(" "));
        if (!desc.isBlank()) {
            book.putString(DBKey.DESCRIPTION, desc);
        }
    }
}
