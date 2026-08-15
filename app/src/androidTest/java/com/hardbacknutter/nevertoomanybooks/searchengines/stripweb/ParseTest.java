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

package com.hardbacknutter.nevertoomanybooks.searchengines.stripweb;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private StripWebSearchEngine searchEngine;
    private MoneyParser moneyParser;
    private RealNumberParser ratingNumberParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.StripWebBe.getConfig().setLogHttpGetRequests(true);
        searchEngine = (StripWebSearchEngine) EngineId.StripWebBe.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        ratingNumberParser = new RealNumberParser(allLocales);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    @Test
    void parseMultiResult()
            throws IOException {
        final String locationHeader =
                "https://www.stripweb.be/nl-nl/zoeken?type=&text=Jim+Cutlass";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripweb_multi_result;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        assertEquals("https://www.stripweb.be/nl-nl/jim-cutlass-integraal",
                     searchEngine.parseMultiResult(document));
    }

    /**
     * Fairly standard single book.
     */
    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripweb.be/nl-nl/wanted-lucky-luke-2";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripweb_9782884719506;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Wanted Lucky Luke", book.getString(DBKey.TITLE, null));
        assertEquals("9782884719506", book.getString(DBKey.ISBN, null));
        assertEquals("523041", book.requireIdentifierValue(Identifier.SID_STRIPWEB));

        assertEquals("2021-10-05", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("72", book.getString(DBKey.PAGES, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));

        assertEquals("5 jaar na het album ' De Moordenaar van Lucky Luke ' neemt" +
                     " Matthieu Bonhomme opnieuw de draad op met een nieuw album van Lucky Luke." +
                     " Gezocht door een bende misdadigers, beschuldigd van moord et vergezeld" +
                     " van drie schoonheden... ja ja benieuwd hoe onze cowboy uit deze" +
                     " ' penibele ' situatie zal weten te ontkomen? met verschillende" +
                     " verwijzingen naar de cult westerns in de films zoals ' The Last Wagon'," +
                     " ' Day of the Outlaw' en ' Yellow Sky' voor de filmliefhebbers onder ons." +
                     " dit alles in het jubileum jaar van Lucky Luke 75 verschijnt midden 2021",
                     book.getString(DBKey.DESCRIPTION, null));

        assertPriceListed(book, "9.99", MoneyParser.EUR, moneyParser);

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Western"));
        assertTrue(tags.contains("western"));
        assertTrue(tags.contains("avontuur"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Lucky Comics", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("Lucky Luke door ...", series.getTitle());
        assertEquals("4", series.getNumber());

        final List<Author> allAuthors = book.getAuthors();
        assertNotNull(allAuthors);
        assertEquals(1, allAuthors.size());
        final Author author = allAuthors.get(0);
        assertEquals("Bonhomme", author.getFamilyName());
        assertEquals("Matthieu", author.getGivenNames());
        assertEquals(AuthorRole.WRITER | AuthorRole.ARTIST | AuthorRole.COLORIST,
                     author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripWebBe.getPreferenceKey()
                                          + "_9782884719506_0_.jpg"));
    }

    /**
     * Multi-author where the names are plain text.
     */
    @Test
    void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.stripweb.be/nl-nl/top-10/valstrikken-en-gevoelens";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripweb_9789085587187;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Valstrikken en emoties", book.getString(DBKey.TITLE, null));
        assertEquals("9789085587187", book.getString(DBKey.ISBN, null));
        assertEquals("548714", book.requireIdentifierValue(Identifier.SID_STRIPWEB));

        assertEquals("2024-02-13", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("64", book.getString(DBKey.PAGES, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));
        assertEquals(3.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);

        assertEquals("met tekeningen van Philippe Xavier, Iouri Jigounov, Joël Callède," +
                     " Gontran Toussaint, Mikaël, Alain Henriet. *een extra katern leveren" +
                     " de volgende tekenaars en scenaristen een hommage aan de reeks XIII:" +
                     " Enrico Marini, Éric Henninot, Alcante, François Boucq, Richard Guérineau," +
                     " Dominique Bertail, Olivier TaDuc, Olivier Grenson, Colin Wilson," +
                     " Jean-Pierre Pécau, Corentin Rouge. Na 1,5 miljoen (!) verkochte albums" +
                     " van XIII Mystery voegt Jean Van Hamme een nieuw deel toe aan de reeks," +
                     " een aflevering die op subtiele wijze het verleden van XIII blootlegt..." +
                     " Jean Van Hamme belicht in dit veertiende deel een weinig bekende episode" +
                     " uit de geschiedenis van XIII. Het boek bundelt verschillende korte" +
                     " verhalen, getekend door een uitgelezen kransje van tekenaars zoals" +
                     " Philippe Xavier, Iouri Jigounov, Mikaël en Alain Henriet. Onder welke" +
                     " omstandigheden werd Eleanor Davis-Brown - beter bekend als agent XX" +
                     " - ontmaskerd? Hoe zag Lullaby's leven eruit, tussen de Minneapolis" +
                     " Hot Dance en haar eigen zaak in Mexico? Komt XIII meer te weten over" +
                     " zijn verleden na een gesprek met een van zijn oude universiteitsvrienden?" +
                     " En is die laatste echt wie hij zegt dat hij is? In tegenstelling tot zijn" +
                     " dertien voorgangers wordt in dit nieuwe deel dus niet gefocust op één" +
                     " personage. Bovendien bevat 'Valstrikken en emoties’ een extra" +
                     " katern met hommages door o.a. Enrico Marini, Éric Henninot, Boucq," +
                     " Richard Guérineau, Dominique Bertail, TaDuc, Olivier Grenson" +
                     " en Corentin Rouge.",
                     book.getString(DBKey.DESCRIPTION, null));

        assertPriceListed(book, "9.99", MoneyParser.EUR, moneyParser);

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("detective"));
        assertTrue(tags.contains("spionage"));
        assertTrue(tags.contains("thriller"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dargaud Benelux", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("XIII Mystery", series.getTitle());
        assertEquals("14", series.getNumber());

        final List<Author> allAuthors = book.getAuthors();
        assertNotNull(allAuthors);
        assertEquals(7, allAuthors.size());
        Author author;
        author = allAuthors.get(0);
        assertEquals("Van Hamme", author.getFamilyName());
        assertEquals("Jean", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1939-01-16", author.getBirthDate().orElse(null));
        author = allAuthors.get(1);
        assertEquals("Xavier", author.getFamilyName());
        assertEquals("Philippe", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1969-05-08", author.getBirthDate().orElse(null));
        author = allAuthors.get(2);
        assertEquals("Henriet", author.getFamilyName());
        assertEquals("Alain", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1973-02-15", author.getBirthDate().orElse(null));
        author = allAuthors.get(3);
        assertEquals("Callède", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        author = allAuthors.get(4);
        assertEquals("Jigounov", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        author = allAuthors.get(5);
        assertEquals("Toussaint", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        author = allAuthors.get(6);
        assertEquals("Mikaël", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripWebBe.getPreferenceKey()
                                          + "_9789085587187_0_.jpg"));
    }

    /**
     * Multi-author where the names are "a" elements.
     */
    @Test
    void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripweb.be/nl-nl/xiii-box-delen-1-3";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripweb_3600121191341;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("XIII- box Delen 1-3", book.getString(DBKey.TITLE, null));
        assertEquals("3600121191341", book.getString(DBKey.ISBN, null));
        assertEquals("522632", book.requireIdentifierValue(Identifier.SID_STRIPWEB));

        assertEquals("2020-09-25", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("48 x 3", book.getString(DBKey.PAGES, null));
        assertEquals("hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));
        assertEquals(0.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);

        assertEquals("De albums van deze reeks verschenen enkel in softcover." +
                     " Nu verschijnt een luxe stripbox met de delen 1-3 in hardcover." +
                     " Het personage XIII is uiteraard de hoofdrolspeler van de gelijknamige" +
                     " reeks, maar waarom zouden we niet gebruikmaken van de overvloedige" +
                     " fantasie van Jean Van Hamme? Drie duo's namen intussen de uitdaging aan.",
                     book.getString(DBKey.DESCRIPTION, null));

        assertPriceListed(book, "44.99", MoneyParser.EUR, moneyParser);

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("avonturen"));
        assertTrue(tags.contains("thriller"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dargaud", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("XIII Mystery", series.getTitle());
        assertEquals("1", series.getNumber());

        final List<Author> allAuthors = book.getAuthors();
        assertNotNull(allAuthors);
        assertEquals(8, allAuthors.size());
        Author author;
        author = allAuthors.get(0);
        assertEquals("Corbeyran", author.getFamilyName());
        assertEquals("Éric", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1964-12-14", author.getBirthDate().orElse(null));
        author = allAuthors.get(1);
        assertEquals("Dorison", author.getFamilyName());
        assertEquals("Xavier", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1972-10-08", author.getBirthDate().orElse(null));
        author = allAuthors.get(2);
        assertEquals("Yann", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1954-05-25", author.getBirthDate().orElse(null));
        author = allAuthors.get(3);
        assertEquals("Berthet", author.getFamilyName());
        assertEquals("Philippe", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1956-09-22", author.getBirthDate().orElse(null));
        author = allAuthors.get(4);
        assertEquals("Henninot", author.getFamilyName());
        assertEquals("Éric", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1974-12-16", author.getBirthDate().orElse(null));
        author = allAuthors.get(5);
        assertEquals("Meyer", author.getFamilyName());
        assertEquals("Ralph", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST | AuthorRole.COLORIST, author.getRole());
        assertEquals("1971-08-11", author.getBirthDate().orElse(null));
        author = allAuthors.get(6);
        assertEquals("David", author.getFamilyName());
        assertEquals("Dominique", author.getGivenNames());
        assertEquals(AuthorRole.COLORIST, author.getRole());
        assertEquals("1960-12-12", author.getBirthDate().orElse(null));
        author = allAuthors.get(7);
        assertEquals("Delabie", author.getFamilyName());
        assertEquals("Caroline", author.getGivenNames());
        assertEquals(AuthorRole.COLORIST, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripWebBe.getPreferenceKey()
                                          + "_3600121191341_0_.jpg"));
    }
}
