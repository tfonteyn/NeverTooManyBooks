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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import org.xml.sax.SAXException;

class KbNlBookHandler
        extends KbNlHandlerBase {

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> authorList = new ArrayList<>();
    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Series> seriesList = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    @NonNull
    private final ArrayList<Publisher> publisherList = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param data Bundle to update <em>(passed in to allow mocking)</em>
     */
    KbNlBookHandler(@NonNull final Bundle data) {
        super(data);
    }

    @Override
    public void startDocument()
            throws SAXException {
        super.startDocument();

        authorList.clear();
        seriesList.clear();
        publisherList.clear();
    }

    @Override
    public void endDocument()
            throws SAXException {
        super.endDocument();

        if (!authorList.isEmpty()) {
            data.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        }
        if (!seriesList.isEmpty()) {
            data.putParcelableArrayList(Book.BKEY_SERIES_LIST, seriesList);
        }
        if (!publisherList.isEmpty()) {
            data.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        }
        // As kb.nl is dutch, and there is no 'language' field,
        // we're going to assume that all books are in Dutch.
        if (!data.isEmpty() && !data.containsKey(DBKey.LANGUAGE)) {
            data.putString(DBKey.LANGUAGE, "nld");
        }
    }

    /**
     * Labels for both Dutch (default) and English are listed.
     * <p>
     * Note that "Colorist" is also used in Dutch.
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
                processAuthor(currentData, Author.TYPE_WRITER);
                break;
            case "Collaborator":
            case "Medewerker":
                processAuthor(currentData, Author.TYPE_CONTRIBUTOR);
                break;
            case "Artist":
            case "Kunstenaar":
                processAuthor(currentData, Author.TYPE_ARTIST);
                break;

            case "Colorist":
                processAuthor(currentData, Author.TYPE_COLORIST);
                break;
            case "Translator":
            case "Vertaler":
                processAuthor(currentData, Author.TYPE_TRANSLATOR);
                break;

            case "Series":
            case "Reeks":
                processSeries(currentData);
                break;

            case "Part(s)":
            case "Deel / delen":
                // This label can appear without there being a "Series" label.
                // In that case, the book title is presumed to also be the Series title.
                processSeriesNumber(currentData);
                break;

            case "Publisher":
            case "Uitgever":
                processPublisher(currentData);
                break;

            case "Year":
            case "Jaar":
                processDatePublished(currentData);
                break;

            case "Extent":
            case "Omvang":
                processPages(currentData);
                break;

            case "ISBN":
                processIsbn(currentData);
                break;


            case "Illustration":
            case "Illustratie":
                // seen, but skipped for now.
                // Lookup: 9789063349943
                // Illustratie:  gekleurde ill
                break;

            case "Size":
            case "Formaat":
                // seen, but skipped for now.
                // Lookup: 9789063349943
                // Formaat: 30 cm
                break;

            case "Editie":
                // [2e dr.]
                break;

            case "Note":
            case "Annotatie":
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
     */
    private void processTitle(@NonNull final Iterable<String> currentData) {
        String[] cleanedData = String.join(" ", currentData).split("/");

        data.putString(DBKey.TITLE, cleanedData[0].trim());
        if (cleanedData.length > 1) {
            cleanedData = cleanedData[1].split(";");
            final Author author = Author.from(cleanedData[0]);
            // for books this is the writer, but for comics? So we do NOT set a type here
            authorList.add(author);

            // TODO: extract the translator
//            if (cleanedData.length > 1) {
//                if (cleanedData[1].startsWith(" [vert.")) {
//
//                }
//            }
        }
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
     *     <psi:text href="REL?PPN=068852002">Ruurd Feenstra (1904-1974) (ISNI 0000 0000 2173 3650) </psi:text>
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
     */
    private void processAuthor(@NonNull final Iterable<String> currentData,
                               @Author.Type final int type) {
        for (final String name : currentData) {
            // remove a year part in the name
            final String cleanedString = name.split("\\(")[0].trim();
            // reject separators as for example: <psi:text>;</psi:text>
            if (cleanedString.length() == 1) {
                return;
            }

            final Author author = Author.from(cleanedString);
            author.setType(type);
            authorList.add(author);
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
     * }</pre>
     */
    private void processSeries(@NonNull final List<String> currentData) {
        if (currentData.size() > 2) {
            seriesList.add(Series.from(currentData.get(0), currentData.get(2)));
        } else {
            seriesList.add(Series.from(currentData.get(0)));
        }
    }

    /**
     * <pre>{@code
     * <psi:labelledData>
     *   <psi:line>
     *     <psi:text>Deel 1</psi:text>
     *   </psi:line>
     * </psi:labelledData>
     * }</pre>
     */
    private void processSeriesNumber(@NonNull final List<String> currentData) {
        final String title = data.getString(DBKey.TITLE);
        // should never happen, but paranoia...
        if (title != null) {
            final Series series = Series.from(title, currentData.get(0));
            seriesList.add(series);
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
     * }</pre>
     */
    private void processIsbn(@NonNull final List<String> currentData) {
        if (!data.containsKey(DBKey.BOOK_ISBN)) {
            data.putString(DBKey.BOOK_ISBN, digits(currentData.get(0), true));
            if (currentData.size() > 1) {
                if (!data.containsKey(DBKey.FORMAT)) {
                    String format = currentData.get(1).trim();
                    if (format.startsWith("(")) {
                        format = format.substring(1, format.length() - 1);
                    }
                    if (!format.isEmpty()) {
                        data.putString(DBKey.FORMAT, format);
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
     */
    private void processPublisher(@NonNull final Iterable<String> currentData) {
        final StringBuilder sbPublisher = new StringBuilder();
        for (final String name : currentData) {
            if (!name.isEmpty()) {
                sbPublisher.append(name).append(" ");
            }
        }
        String publisherName = sbPublisher.toString();
        if (publisherName.contains(":")) {
            publisherName = publisherName.split(":")[1].trim();
        }
        publisherList.add(Publisher.from(publisherName));
    }

    /**
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
     * }</pre>
     */
    private void processDatePublished(@NonNull final List<String> currentData) {
        if (!data.containsKey(DBKey.BOOK_PUBLICATION__DATE)) {
            final String year = digits(currentData.get(0), false);
            if (year != null && !year.isEmpty()) {
                data.putString(DBKey.BOOK_PUBLICATION__DATE, year);
            }
        }
    }

    /**
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
     */
    private void processPages(@NonNull final List<String> currentData) {
        if (!data.containsKey(DBKey.PAGE_COUNT)) {
            try {
                final String cleanedString = currentData.get(0).split(" ")[0];
                final int pages = Integer.parseInt(cleanedString);
                data.putString(DBKey.PAGE_COUNT, String.valueOf(pages));
            } catch (@NonNull final NumberFormatException e) {
                // use source
                data.putString(DBKey.PAGE_COUNT, currentData.get(0));
            }
        }
    }
}
